package net.thesmallthings.hellcraft.blood;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.util.Feedback;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * What a ghost can still do while the living gather blood for them:
 * <ul>
 *     <li>{@code /ghost mark}: whatever you look at glows for everyone, for 10 s;</li>
 *     <li>{@code /ghost haunt}: whoever killed you, if near, is chilled with darkness and dread;</li>
 *     <li>{@code /ghost beacon}: a pillar of soul fire over you for 30 s, so the living can find you.</li>
 * </ul>
 */
public final class GhostPowers {
	private GhostPowers() {
	}

	private static final int MARK_COOLDOWN = 30 * 20;
	private static final int HAUNT_COOLDOWN = 120 * 20;
	private static final int BEACON_COOLDOWN = 60 * 20;
	private static final int BEACON_TICKS = 30 * 20;

	public static final String HELP = "Ghost powers: /ghost mark (make what you look at glow), /ghost haunt (slow your killer), "
			+ "/ghost beacon (a pillar of fire so friends can find you).";

	/** Ghost UUID -> the entity that killed them. */
	private static final Map<UUID, UUID> KILLERS = new HashMap<>();
	private static final Map<String, Long> READY = new HashMap<>();
	/** Ghost UUID -> game time its beacon burns until. */
	private static final Map<UUID, Long> BEACONS = new HashMap<>();

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("ghost")
				.executes(ctx -> {
					ctx.getSource().sendSuccess(() -> Component.literal(HELP).withStyle(ChatFormatting.GRAY), false);
					return 1;
				})
				.then(Commands.literal("mark").executes(ctx -> mark(ctx.getSource().getPlayerOrException())))
				.then(Commands.literal("haunt").executes(ctx -> haunt(ctx.getSource().getPlayerOrException())))
				.then(Commands.literal("beacon").executes(ctx -> beacon(ctx.getSource().getPlayerOrException()))));
	}

	/** Remembers who (or what) took a player's last heart. */
	public static void rememberKiller(ServerPlayer ghost, @Nullable Entity killer) {
		if (killer != null && killer != ghost) {
			KILLERS.put(ghost.getUUID(), killer.getUUID());
		}
	}

	private static boolean isGhost(ServerPlayer player) {
		HellState.Soul soul = HellState.get(player.level().getServer()).existing(player.getUUID());
		if (soul == null || !soul.ghost) {
			player.sendSystemMessage(Component.literal("Only ghosts can use these.").withStyle(ChatFormatting.GRAY));
			return false;
		}
		return true;
	}

	/** Starts a cooldown; returns false (and says how long is left) if it's still running. */
	private static boolean ready(ServerPlayer player, String power, int cooldown) {
		String key = player.getUUID() + ":" + power;
		long now = player.level().getGameTime();
		long at = READY.getOrDefault(key, 0L);
		if (now < at) {
			player.sendOverlayMessage(Component.literal("On cooldown: " + power + " again in " + (at - now + 19) / 20 + " s.")
					.withStyle(ChatFormatting.GRAY));
			return false;
		}
		READY.put(key, now + cooldown);
		return true;
	}

	private static int mark(ServerPlayer ghost) {
		if (!isGhost(ghost)) {
			return 0;
		}
		LivingEntity target = lookedAt(ghost, 32.0);
		if (target == null) {
			ghost.sendOverlayMessage(Component.literal("Look at a creature to mark it.").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		if (!ready(ghost, "mark", MARK_COOLDOWN)) {
			return 0;
		}
		target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
		ServerLevel level = ghost.level();
		level.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + target.getBbHeight(), target.getZ(), 20, 0.3, 0.3, 0.3, 0.02);
		Component msg = Component.literal("☠ " + ghost.getGameProfile().name() + " (a ghost) marked ").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
				.append(target.getDisplayName().copy().withStyle(ChatFormatting.WHITE));
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(ghost) < 96 * 96) {
				p.sendSystemMessage(msg);
			}
		}
		return 1;
	}

	private static int haunt(ServerPlayer ghost) {
		if (!isGhost(ghost)) {
			return 0;
		}
		UUID killerId = KILLERS.get(ghost.getUUID());
		Entity found = killerId == null ? null : ghost.level().getEntity(killerId);
		if (!(found instanceof LivingEntity killer) || !killer.isAlive() || killer.distanceToSqr(ghost) > 48 * 48) {
			ghost.sendOverlayMessage(Component.literal("Your killer isn't close enough.").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		if (!ready(ghost, "haunt", HAUNT_COOLDOWN)) {
			return 0;
		}
		killer.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0));
		killer.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 120, 1));
		ghost.level().sendParticles(ParticleTypes.SCULK_SOUL, killer.getX(), killer.getY() + 1, killer.getZ(), 30, 0.5, 0.8, 0.5, 0.02);
		if (killer instanceof ServerPlayer p) {
			p.sendSystemMessage(Component.literal("You feel " + ghost.getGameProfile().name() + "'s ghost haunting you.")
					.withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
			Feedback.sound(p, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 0.6f, 1.6f);
		}
		ghost.sendOverlayMessage(Component.literal("You haunt your killer.").withStyle(ChatFormatting.DARK_AQUA));
		return 1;
	}

	private static int beacon(ServerPlayer ghost) {
		if (!isGhost(ghost) || !ready(ghost, "beacon", BEACON_COOLDOWN)) {
			return 0;
		}
		BEACONS.put(ghost.getUUID(), ghost.level().getGameTime() + BEACON_TICKS);
		Component msg = Component.literal("☠ " + ghost.getGameProfile().name() + "'s ghost lit a beacon at ("
				+ ghost.getBlockX() + ", " + ghost.getBlockY() + ", " + ghost.getBlockZ() + ").").withStyle(ChatFormatting.AQUA);
		ghost.level().getServer().getPlayerList().broadcastSystemMessage(msg, false);
		return 1;
	}

	/** Draws the burning beacons. */
	public static void tick(MinecraftServer server) {
		if (BEACONS.isEmpty() || server.getTickCount() % 5 != 0) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Long>> it = BEACONS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Long> e = it.next();
			ServerPlayer ghost = server.getPlayerList().getPlayer(e.getKey());
			if (ghost == null || ghost.level().getGameTime() > e.getValue()) {
				it.remove();
				continue;
			}
			ServerLevel level = ghost.level();
			for (int y = 0; y < 24; y += 2) {
				level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, ghost.getX(), ghost.getY() + y, ghost.getZ(), 3, 0.15, 0.5, 0.15, 0.0);
			}
		}
	}

	/** The living thing nearest the centre of the player's view, within range. */
	@Nullable
	private static LivingEntity lookedAt(ServerPlayer player, double range) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		LivingEntity best = null;
		double bestDot = 0.97;
		for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
				e -> e != player && e.isAlive() && !e.isSpectator())) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			double dist = to.length();
			if (dist > range || dist < 1.0e-3) {
				continue;
			}
			double dot = to.scale(1.0 / dist).dot(look);
			if (dot > bestDot) {
				bestDot = dot;
				best = e;
			}
		}
		return best;
	}
}
