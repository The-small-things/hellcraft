package net.thesmallthings.hellcraft.blood;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.util.Feedback;
import net.thesmallthings.hellcraft.world.HellWorldgen;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

/**
 * The bounty keeps the mighty in check: every so often the soul holding the most blood (and at least
 * bountyMinHearts) is marked. The whole server is told roughly where they are, they glow for a while,
 * and whoever kills them is paid in Blood Hearts. Players police each other.
 */
public final class Bounty {
	private Bounty() {
	}

	@Nullable
	private static UUID target;
	private static String targetName = "";
	private static long nextMark;

	public static String status(MinecraftServer server) {
		HellConfig config = HellConfig.get();
		if (!config.bounty) {
			return "The bounty is off (set bounty to true).";
		}
		long minutes = Math.max(0, (nextMark - server.overworld().getGameTime() + 1199) / 1200);
		return (target == null ? "Nobody carries a bounty." : targetName + " carries the bounty.")
				+ " The next mark in " + minutes + " min (it needs " + config.bountyMinHearts + "+ hearts).";
	}

	public static void clear() {
		target = null;
		targetName = "";
	}

	/** Marks the strongest soul now (an operator's command) or on the timer. Returns who, or null. */
	@Nullable
	public static String markNow(MinecraftServer server) {
		HellConfig config = HellConfig.get();
		HellState state = HellState.get(server);
		ServerPlayer best = null;
		int bestHearts = config.bountyMinHearts - 1;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			HellState.Soul soul = state.existing(p.getUUID());
			if (soul == null || soul.ghost || p.isSpectator() || p.isCreative() || state.captives.containsKey(p.getUUID())) {
				continue;
			}
			if (soul.hearts > bestHearts || (best != null && soul.hearts == bestHearts && soul.prestige > Hearts.soul(best).prestige)) {
				best = p;
				bestHearts = soul.hearts;
			}
		}
		nextMark = server.overworld().getGameTime() + config.bountyIntervalMinutes * 1200L;
		if (best == null) {
			clear();
			return null;
		}
		target = best.getUUID();
		targetName = best.getGameProfile().name();
		best.addEffect(new MobEffectInstance(MobEffects.GLOWING, 45 * 20, 0, false, false, true));
		server.getPlayerList().broadcastSystemMessage(Component.literal("☠ A bounty on " + targetName + " (" + bestHearts + " ❤): ")
				.withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
				.append(Component.literal("last seen " + where(best) + ". Kill them to get " + config.bountyRewardHearts
						+ " Blood Hearts.").withStyle(ChatFormatting.RED)), false);
		best.sendSystemMessage(Component.literal("You have the most hearts, so everyone has been told where you are.")
				.withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
		Feedback.sound(best, SoundEvents.WITHER_AMBIENT, SoundSource.MASTER, 0.8f, 0.6f);
		HellcraftMod.LOGGER.info("Bounty on {} ({} hearts)", targetName, bestHearts);
		return targetName;
	}

	/** Roughly where a soul is: the circle (or realm) and coordinates rounded to 50 blocks. */
	private static String where(ServerPlayer p) {
		ServerLevel level = p.level();
		String place;
		if (level.dimension() == Level.NETHER) {
			place = "in the Forge of Dis";
		} else if (level.dimension() == Level.END) {
			place = "in Paradiso";
		} else if (HellWorldgen.isInferno(level)) {
			place = "in " + InfernoGeometry.regionName(p.getX(), p.getZ());
		} else {
			place = "in the overworld";
		}
		return String.format(Locale.ROOT, "%s, near x %d, z %d", place, Math.round(p.getX() / 50.0) * 50, Math.round(p.getZ() / 50.0) * 50);
	}

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		HellConfig config = HellConfig.get();
		if (!config.bounty) {
			clear();
			return;
		}
		long now = server.overworld().getGameTime();
		if (nextMark == 0) {
			nextMark = now + config.bountyIntervalMinutes * 1200L;
		}
		if (target != null) {
			ServerPlayer marked = server.getPlayerList().getPlayer(target);
			if (marked == null) {
				clear(); // logged off: the mark fades (and may come back at the next one)
			}
		}
		if (now >= nextMark) {
			markNow(server);
		}
	}

	/** A player died: if they carried the bounty, whoever killed them is paid. */
	public static void onDeath(ServerPlayer dead, @Nullable ServerPlayer killer) {
		if (target == null || !target.equals(dead.getUUID())) {
			return;
		}
		String name = targetName;
		clear();
		if (killer == null || killer == dead) {
			dead.level().getServer().getPlayerList().broadcastSystemMessage(Component.literal("The bounty on " + name + " is over: they died, but not to a player.")
					.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
			return;
		}
		int reward = HellConfig.get().bountyRewardHearts;
		BloodItems.give(killer, BloodItems.heart(reward));
		dead.level().getServer().getPlayerList().broadcastSystemMessage(Component.literal(killer.getGameProfile().name() + " collected the bounty on "
				+ name + " (" + reward + " Blood Hearts).").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		HellcraftMod.LOGGER.info("Bounty on {} collected by {}", name, killer.getGameProfile().name());
	}
}
