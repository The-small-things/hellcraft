package net.thesmallthings.hellcraft.blood;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.thesmallthings.hellcraft.hazard.lucifer.LuciferManager;
import net.thesmallthings.hellcraft.util.Feedback;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The two one-time respawn items:
 * <ul>
 *     <li>a <b>Vigil Candle</b>, lit where you stand: your next death wakes you beside it;</li>
 *     <li>a <b>Soul Anchor</b>, carried: if you die, you rise again where you fell and it breaks.</li>
 * </ul>
 * Neither holds in Lucifer's pit. On respawn the anchor goes first, then the candle, then a bound altar.
 */
public final class Respawns {
	private Respawns() {
	}

	/** How far from where you died an anchor looks for the last solid ground you stood on. */
	private static final int ANCHOR_REACH = 32;
	/** The last safe ground each player stood on (in memory; an anchor falls back to the death spot). */
	private static final Map<UUID, HellState.GlobalSpot> SAFE = new HashMap<>();
	private static int ticks;

	public static void tick(MinecraftServer server) {
		if (++ticks % 20 != 0) {
			return;
		}
		HellState state = HellState.get(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isAlive() && !player.isSpectator() && player.onGround() && !player.isInLava() && !player.isOnFire()) {
				SAFE.put(player.getUUID(), new HellState.GlobalSpot(player.level().dimension(), player.blockPosition()));
			}
			HellState.Soul soul = state.existing(player.getUUID());
			if (ticks % 60 == 0 && soul != null && soul.vigil != null && soul.vigil.dimension() == player.level().dimension()
					&& soul.vigil.pos().distSqr(player.blockPosition()) < 48 * 48) {
				// your candle, still burning
				BlockPos p = soul.vigil.pos();
				player.level().sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX() + 0.5, p.getY() + 0.6, p.getZ() + 0.5, 4, 0.08, 0.15, 0.08, 0.005);
			}
		}
	}

	public static void forget(ServerPlayer player) {
		SAFE.remove(player.getUUID());
	}

	// ------------------------------------------------------------------ the Vigil Candle

	/** Right-click with a Vigil Candle: lights it here. Returns true if the candle was used. */
	public static boolean lightVigil(ServerPlayer player, ItemStack stack) {
		ServerLevel level = player.level();
		if (LuciferManager.inPit(level, player.blockPosition())) {
			player.sendOverlayMessage(Component.literal("No flame lives in the Emperor's pit.").withStyle(ChatFormatting.AQUA));
			return false;
		}
		if (!player.onGround()) {
			player.sendOverlayMessage(Component.literal("Stand on solid ground to light your vigil.").withStyle(ChatFormatting.GRAY));
			return false;
		}
		HellState.Soul soul = Hearts.soul(player);
		boolean replaced = soul.vigil != null;
		soul.vigil = new HellState.GlobalSpot(level.dimension(), player.blockPosition());
		HellState.get(level.getServer()).setDirty();
		stack.shrink(1);
		BlockPos p = player.blockPosition();
		level.playSound(null, p, SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 1.0f, 0.7f);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX() + 0.5, p.getY() + 0.6, p.getZ() + 0.5, 20, 0.2, 0.3, 0.2, 0.01);
		player.sendSystemMessage(Component.literal((replaced ? "Your old candle gutters out. " : "")
				+ "A Vigil Candle burns here (" + p.getX() + ", " + p.getY() + ", " + p.getZ() + "): your next death wakes you beside it.")
				.withStyle(ChatFormatting.AQUA));
		return true;
	}

	// ------------------------------------------------------------------ the Soul Anchor

	/** As a player dies (before their items drop): takes a Soul Anchor from them and remembers where to bring them back. */
	public static void takeAnchor(ServerPlayer player) {
		ServerLevel level = player.level();
		if (LuciferManager.inPit(level, player.blockPosition())) {
			return;
		}
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack s = player.getInventory().getItem(i);
			if (BloodItems.isAnchor(s)) {
				s.shrink(1);
				Hearts.soul(player).anchorAt = anchorSpot(player);
				HellState.get(level.getServer()).setDirty();
				return;
			}
		}
	}

	private static HellState.GlobalSpot anchorSpot(ServerPlayer player) {
		BlockPos died = player.blockPosition();
		HellState.GlobalSpot safe = SAFE.get(player.getUUID());
		if (safe != null && safe.dimension() == player.level().dimension() && safe.pos().distSqr(died) <= ANCHOR_REACH * ANCHOR_REACH) {
			return safe;
		}
		int y = Math.max(died.getY(), player.level().getMinY() + 1);
		return new HellState.GlobalSpot(player.level().dimension(), new BlockPos(died.getX(), y, died.getZ()));
	}

	// ------------------------------------------------------------------ respawning

	/** Called after a (non-ghost) respawn. Returns true if an anchor or candle brought the player back. */
	public static boolean respawn(ServerPlayer player, HellState.Soul soul) {
		HellState state = HellState.get(player.level().getServer());
		if (soul.anchorAt != null) {
			HellState.GlobalSpot at = soul.anchorAt;
			soul.anchorAt = null;
			state.setDirty();
			Ghosts.teleport(player, at);
			player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100, 3));
			player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0));
			blast(player);
			player.sendSystemMessage(Component.literal("Your Soul Anchor drags you back to where you fell, and shatters.")
					.withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
			return true;
		}
		if (soul.vigil != null) {
			HellState.GlobalSpot at = soul.vigil;
			soul.vigil = null;
			state.setDirty();
			Ghosts.teleport(player, at);
			player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 1));
			player.sendSystemMessage(Component.literal("You wake beside your Vigil Candle. It has burned out.").withStyle(ChatFormatting.AQUA));
			return true;
		}
		return false;
	}

	/** The anchor lands with a burst of blood that throws back whatever waits nearby. */
	private static void blast(ServerPlayer player) {
		ServerLevel level = player.level();
		level.sendParticles(BloodAltar.BLOOD, player.getX(), player.getY() + 1, player.getZ(), 60, 2.0, 0.6, 2.0, 0.0);
		level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1, player.getZ(), 30, 1.5, 0.8, 1.5, 0.05);
		level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8f, 1.3f);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(6.0), e -> e instanceof Enemy && e.isAlive())) {
			Feedback.pushAway(e, player.getX(), player.getZ(), 1.6);
		}
	}
}
