package net.thesmallthings.hellcraft.combat;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.blood.LayerEnchants;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.entity.boss.gen.PlayerMovesAnim;
import net.thesmallthings.hellcraft.hazard.Danger;
import net.thesmallthings.hellcraft.net.FeelPayload;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The dodge roll (the Dash key, for everyone while Hellcraft's combat is on): a tumble the way you're moving that costs
 * stamina, and for a few ticks in the middle of it blows pass through you. The client moves its own player (a player's
 * movement is the client's to decide) and plays the roll at once; the server takes the stamina, keeps the time between
 * rolls and decides which blows miss. Sidestep leggings make it cheaper, longer and safer (blood/LayerEnchants).
 */
public final class Dodge {
	private Dodge() {
	}

	/** The roll's length in ticks, as animated. */
	public static final int TICKS = PlayerMovesAnim.ROLL.ticks;
	/** The tick into the roll at which blows start to miss. */
	private static final int EVADE_FROM = 1;

	private static final Map<UUID, Long> STARTED = new HashMap<>();
	/** The last blow that passed through each roller, so one blow counts once. */
	private static final Map<UUID, Long> PRAISED = new HashMap<>();

	static void register() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> RollShape.tick(false));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player && evading(player) && dodgeable(source)) {
				passedThrough(player);
				return false;
			}
			return true;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			STARTED.remove(entity.getUUID());
			PRAISED.remove(entity.getUUID());
		});
	}

	static void forget(ServerPlayer player) {
		STARTED.remove(player.getUUID());
		PRAISED.remove(player.getUUID());
	}

	/** What a roll costs this player. */
	public static float cost(ServerPlayer player) {
		return cost(HellConfig.get().dodgeCost, LayerEnchants.level(player, LayerEnchants.SIDESTEP));
	}

	static float cost(float base, int sidestep) {
		return base * Math.max(0.4f, 1.0f - 0.2f * sidestep);
	}

	public static int invulnerableTicks(int base, int sidestep) {
		return Math.min(TICKS - EVADE_FROM, base + 2 * sidestep);
	}

	/**
	 * The client rolled (it has already begun to move). Returns false if the server won't have it: then the roll is only a
	 * stumble, with no blows missing.
	 */
	public static boolean roll(ServerPlayer player, float x, float z) {
		if (!player.isAlive() || player.isSpectator() || player.isPassenger() || player.isFallFlying() || player.isSwimming()
				|| player.isUsingItem() && !Guard.guarding(player)) {
			return false;
		}
		long now = player.level().getGameTime();
		Long last = STARTED.get(player.getUUID());
		if (last != null && now - last < HellConfig.get().dodgeCooldownTicks) {
			return false;
		}
		if (!Stamina.spend(player, cost(player))) {
			return false;
		}
		if (player.isUsingItem()) {
			player.stopUsingItem();
		}
		Swing.cancel(player);
		STARTED.put(player.getUUID(), now);
		RollShape.start(player);
		Vec3 dir = new Vec3(x, 0, z);
		if (dir.lengthSqr() < 1.0e-4) {
			double yaw = Math.toRadians(player.getYRot());
			dir = new Vec3(Math.sin(yaw), 0, -Math.cos(yaw));
		}
		dir = dir.normalize();
		Combat.show(player, PlayerMovesAnim.ROLL, (float) dir.x, (float) dir.z, 1.0f, false);
		ServerLevel level = player.level();
		level.playSound(null, player.blockPosition(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.5f, 1.5f);
		level.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.8f, 0.8f);
		level.sendParticles(ParticleTypes.POOF, player.getX(), player.getY() + 0.15, player.getZ(), 6, 0.25, 0.05, 0.25, 0.01);
		return true;
	}

	/** Mid-roll at all (stamina waits, guards drop). */
	public static boolean rolling(ServerPlayer player) {
		Long started = STARTED.get(player.getUUID());
		return started != null && player.level().getGameTime() - started < TICKS;
	}

	/** The earliest tick into a roll from which a guard may cut it short: its "recover", up off the ground. */
	public static final int GUARD_FROM = PlayerMovesAnim.ROLL.at("recover");

	/**
	 * The tick into a roll from which a guard may cut it short: its "recover", and never while blows still pass through
	 * (Sidestep and the dodgeInvulnerableTicks setting make that part longer). The client asks the same (RollControl), with
	 * the safe ticks the server sent, so it never raises a guard the server won't.
	 */
	public static int guardFrom(int invulnerableBase, int sidestep) {
		return Math.max(GUARD_FROM, EVADE_FROM + invulnerableTicks(invulnerableBase, sidestep));
	}

	/** Whether a guard may go up now: not rolling, or past the part of the roll where blows pass through. */
	public static boolean mayGuard(ServerPlayer player) {
		Long started = STARTED.get(player.getUUID());
		if (started == null || !rolling(player)) {
			return true;
		}
		int from = guardFrom(HellConfig.get().dodgeInvulnerableTicks, LayerEnchants.level(player, LayerEnchants.SIDESTEP));
		return player.level().getGameTime() - started >= from && !evading(player);
	}

	/** In the part of a roll where blows pass through. */
	public static boolean evading(ServerPlayer player) {
		Long started = STARTED.get(player.getUUID());
		if (started == null) {
			return false;
		}
		long into = player.level().getGameTime() - started;
		int ticks = invulnerableTicks(HellConfig.get().dodgeInvulnerableTicks, LayerEnchants.level(player, LayerEnchants.SIDESTEP));
		return into >= EVADE_FROM && into < EVADE_FROM + ticks;
	}

	/** What a roll can slip: blows and shots from someone or something, a boss's marks, blasts. Not fire, falls, drowning or magic. */
	static boolean dodgeable(DamageSource source) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		return source.getEntity() != null || source.getDirectEntity() != null || source.is(Danger.JUDGEMENT) || source.is(DamageTypeTags.IS_EXPLOSION);
	}

	private static void passedThrough(ServerPlayer player) {
		long now = player.level().getGameTime();
		Long last = PRAISED.get(player.getUUID());
		if (last != null && now - last < TICKS) {
			return;
		}
		PRAISED.put(player.getUUID(), now);
		// a blow slipped: a breath of stamina back, and the player hears and feels it
		Stamina.restore(player, 8);
		Combat.feel(player, FeelPayload.Kind.DODGED, 1.0f);
		ServerLevel level = player.level();
		net.thesmallthings.hellcraft.hazard.Cue.DODGE.play(level, player.getX(), player.getY() + 1, player.getZ(), 1.0f, 1.0f);
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.9, player.getZ(), 5, 0.3, 0.4, 0.3, 0.01);
		net.thesmallthings.hellcraft.hazard.guardian.GuardianManager.dodged(player);
		net.thesmallthings.hellcraft.hazard.lucifer.LuciferManager.dodged(player);
	}

	static String selfTest() {
		if (cost(30, 0) != 30 || cost(30, 2) >= cost(30, 1) || cost(30, 9) <= 0) {
			return "a roll's cost should fall with Sidestep and never reach nothing";
		}
		if (invulnerableTicks(8, 0) != 8 || invulnerableTicks(8, 3) > TICKS) {
			return "a roll's safe ticks should fit inside the roll";
		}
		if (guardFrom(8, 0) != GUARD_FROM || guardFrom(8, 1) <= GUARD_FROM || guardFrom(8, 1) < EVADE_FROM + invulnerableTicks(8, 1)) {
			return "a guard should cut a roll short at its recover, and never while blows still pass through";
		}
		return "";
	}
}
