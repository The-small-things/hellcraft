package net.thesmallthings.hellcraft.combat;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.blood.HellWeapons;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.entity.boss.gen.PlayerMovesAnim;
import net.thesmallthings.hellcraft.hazard.BossRules;
import net.thesmallthings.hellcraft.net.FeelPayload;
import net.thesmallthings.hellcraft.net.GuardPayload;
import net.thesmallthings.hellcraft.util.Feedback;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Guarding with a weapon: hold right-click with a sword, an axe, a mace or a hell weapon in the main hand (and nothing to
 * use in the other) and it turns aside part of every blow from the front, at a cost in stamina. A guard raised just
 * before the blow is a <b>parry</b>: nothing gets through, and the attacker reels (a boss counts it as a blocked white
 * flash). Out of stamina, the guard breaks. Shields work as ever and come first.
 *
 * <p>A guard is vanilla's "using an item" (so it is slow, everyone sees it and letting go ends it), started by hand
 * since a sword has no use of its own: the client raises it on a right-click that nothing else used (a chest, a door,
 * food or a shield in the other hand all come first) and says so (net/GuardPayload). The blocking is worked out here,
 * from mixin/LivingEntityMixin.
 */
public final class Guard {
	private Guard() {
	}

	/** A guard begun sooner than this after the last one can't parry: no fluttering the button to parry everything. */
	private static final int PARRY_LOCK = 14;

	/** When each guard now up was raised. */
	private static final Map<UUID, Long> RAISED = new HashMap<>();
	/** When each player's guard before that was raised. */
	private static final Map<UUID, Long> BEFORE = new HashMap<>();
	/** Until when a parry's opening lasts: the next blow struck is a riposte (combat/Swing). */
	private static final Map<UUID, Long> RIPOSTE = new HashMap<>();

	private static int wholeId = -1;
	private static long wholeAt = -1;

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(GuardPayload.TYPE, GuardPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(GuardPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (Combat.on() && mayRaise(player) && !Dodge.rolling(player)) {
				player.startUsingItem(InteractionHand.MAIN_HAND);
			}
		});
	}

	/**
	 * Whether a right-click that nothing else used may raise this player's guard (either side; the client asks before it
	 * tells the server): a weapon that guards in the main hand, not on cooldown, and not a sneaking hell weapon (that
	 * swears its Blood Oath).
	 */
	public static boolean mayRaise(Player player) {
		if (player.isSpectator() || player.isUsingItem() || !player.isAlive()) {
			return false;
		}
		ItemStack stack = player.getMainHandItem();
		if (!guards(stack) || player.getCooldowns().isOnCooldown(stack)) {
			return false;
		}
		return !(player.isShiftKeyDown() && HellWeapons.of(stack) != null);
	}

	static void forget(ServerPlayer player) {
		RAISED.remove(player.getUUID());
		BEFORE.remove(player.getUUID());
		RIPOSTE.remove(player.getUUID());
	}

	/** Whether this is a weapon one can guard with. */
	public static boolean guards(ItemStack stack) {
		return WeaponClass.of(stack) != null;
	}

	/** Holding a weapon's guard up (either side). */
	public static boolean guarding(Player player) {
		return player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND && guards(player.getUseItem());
	}

	/** In the opening a parry leaves. */
	public static boolean riposting(ServerPlayer player) {
		Long until = RIPOSTE.get(player.getUUID());
		return until != null && player.level().getGameTime() <= until;
	}

	public static void spendRiposte(ServerPlayer player) {
		RIPOSTE.remove(player.getUUID());
	}

	static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			long now = player.level().getGameTime();
			if (guarding(player)) {
				if (!Combat.on()) {
					player.stopUsingItem();
					continue;
				}
				RAISED.putIfAbsent(id, now);
			} else {
				Long raised = RAISED.remove(id);
				if (raised != null) {
					BEFORE.put(id, raised);
				}
			}
		}
	}

	/**
	 * A blow reaches someone (LivingEntityMixin, in place of vanilla's shield check): how much of it their weapon's guard
	 * turns aside, or -1 if they aren't guarding with a weapon (then vanilla's shields have their say).
	 */
	public static float block(LivingEntity victim, ServerLevel level, DamageSource source, float damage) {
		if (!(victim instanceof ServerPlayer player) || !guarding(player) || !Combat.on()) {
			return -1;
		}
		if (damage <= 0 || !blockable(source) || !facing(player, source.getSourcePosition())) {
			return 0;
		}
		HellConfig config = HellConfig.get();
		WeaponClass kind = WeaponClass.of(player.getUseItem());
		Entity attacker = source.getEntity();
		long now = level.getGameTime();
		// the guard may have gone up this very tick, before tick() saw it
		long raised = RAISED.getOrDefault(player.getUUID(), now);
		long since = now - raised;
		Long before = BEFORE.get(player.getUUID());
		boolean fresh = before == null || raised - before >= PARRY_LOCK;
		if (fresh && since <= config.parryTicks) {
			parried(player, level, attacker, damage);
			wholeId = player.getId();
			wholeAt = now;
			return damage;
		}
		float share = (float) Math.min(0.95, config.guardBlock * kind.guard);
		float blocked = damage * share;
		// an axe's blow beats a guard down, as it does a shield
		boolean beaten = attacker instanceof LivingEntity living && living.getSecondsToDisableBlocking() > 0;
		boolean spent = Stamina.drain(player, (float) Math.min(Stamina.max() * 0.6, damage * config.guardStaminaPerDamage));
		player.getUseItem().hurtAndBreak(1, player, InteractionHand.MAIN_HAND.asEquipmentSlot());
		Vec3 at = clash(player, attacker);
		if (spent || beaten) {
			broken(player, level, at);
		} else {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 0.9f, 1.25f + level.getRandom().nextFloat() * 0.2f);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.15f, 1.9f);
			level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.15, 0.15, 0.15, 0.25);
			Combat.feel(player, FeelPayload.Kind.GUARDED, Math.min(2.0f, 0.6f + damage / 10.0f));
		}
		return blocked;
	}

	private static void parried(ServerPlayer player, ServerLevel level, Entity attacker, float damage) {
		HellConfig config = HellConfig.get();
		Vec3 at = clash(player, attacker);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 1.0f, 1.7f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.35f, 1.9f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_HIT_GROUND, SoundSource.PLAYERS, 0.7f, 1.5f);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 14, 0.2, 0.2, 0.2, 0.6);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.4);
		Stamina.restore(player, 12);
		RIPOSTE.put(player.getUUID(), level.getGameTime() + 30);
		Combat.show(player, PlayerMovesAnim.PARRY, 0, 0, 1.0f, true);
		Combat.feel(player, FeelPayload.Kind.PARRY, Math.min(2.0f, 1.0f + damage / 20.0f));
		if (attacker instanceof LivingEntity living && attacker != player && !BossRules.fromBoss(attacker)) {
			stagger(living, player, attacker instanceof Player ? config.parryStaggerTicks / 2 : config.parryStaggerTicks);
		}
	}

	/**
	 * Whether this entity's weapon guard has just turned a whole blow (a parry), this tick: vanilla, finding no shield,
	 * would flash them red, tilt their view and play their hurt sound all the same (LivingEntityMixin asks).
	 */
	public static boolean turnedWhole(LivingEntity entity) {
		return entity.getId() == wholeId && entity.level().getGameTime() == wholeAt;
	}

	/** A parried attacker reels: thrown back, slowed to a crawl and unable to strike for a moment. */
	public static void stagger(LivingEntity attacker, Entity from, int ticks) {
		if (ticks <= 0) {
			return;
		}
		attacker.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 5, false, false));
		attacker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 9, false, false));
		Feedback.pushAway(attacker, from.getX(), from.getZ(), 0.55);
		if (attacker.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.ENCHANTED_HIT, attacker.getX(), attacker.getEyeY() + 0.3, attacker.getZ(), 10, 0.25, 0.15, 0.25, 0.0);
		}
	}

	private static void broken(ServerPlayer player, ServerLevel level, Vec3 at) {
		HellConfig config = HellConfig.get();
		ItemStack weapon = player.getUseItem().copy();
		player.stopUsingItem();
		if (config.guardBreakTicks > 0 && !weapon.isEmpty()) {
			player.getCooldowns().addCooldown(weapon, config.guardBreakTicks);
		}
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 2, false, false));
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BREAK.value(), SoundSource.PLAYERS, 0.9f, 0.9f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.3f, 0.7f);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 16, 0.25, 0.25, 0.25, 0.5);
		Combat.show(player, PlayerMovesAnim.GUARD_BREAK, 0, 0, 1.0f, true);
		Combat.feel(player, FeelPayload.Kind.GUARD_BREAK, 1.5f);
		player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("Guard broken! Out of stamina.")
				.withStyle(net.minecraft.ChatFormatting.RED));
	}

	/** Where blade meets blow: between the two, at chest height. */
	private static Vec3 clash(ServerPlayer player, Entity attacker) {
		Vec3 chest = player.position().add(0, player.getBbHeight() * 0.7, 0);
		if (attacker == null) {
			return chest.add(player.getLookAngle().multiply(1, 0, 1).scale(0.6));
		}
		Vec3 to = attacker.position().add(0, attacker.getBbHeight() * 0.6, 0).subtract(chest);
		return chest.add(to.normalize().scale(Math.min(0.7, to.length() * 0.5)));
	}

	/** A weapon's guard turns blows, not arrows, blasts, magic or what no shield stops. */
	static boolean blockable(DamageSource source) {
		return !source.is(DamageTypeTags.BYPASSES_SHIELD) && !source.is(DamageTypeTags.IS_PROJECTILE) && !source.is(DamageTypeTags.IS_EXPLOSION)
				&& source.getSourcePosition() != null;
	}

	/** Within a quarter turn of where the player looks (flat), as vanilla judges a shield. */
	static boolean facing(Player player, Vec3 from) {
		if (from == null) {
			return false;
		}
		return facing(player.getYHeadRot(), from.x - player.getX(), from.z - player.getZ());
	}

	static boolean facing(float yaw, double dx, double dz) {
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1.0e-4) {
			return true;
		}
		double rad = Math.toRadians(yaw);
		double vx = -Math.sin(rad);
		double vz = Math.cos(rad);
		return (dx * vx + dz * vz) / len > 0;
	}

	static String selfTest() {
		// looking south (yaw 0 is +z): a blow from the south is faced, one from the north is not
		if (!facing(0, 0, 3) || facing(0, 0, -3) || !facing(90, -3, 0) || facing(90, 3, 0)) {
			return "the guard's facing check is turned round";
		}
		for (WeaponClass kind : WeaponClass.values()) {
			if (kind.guard <= 0) {
				return kind + " can't guard";
			}
		}
		return "";
	}
}
