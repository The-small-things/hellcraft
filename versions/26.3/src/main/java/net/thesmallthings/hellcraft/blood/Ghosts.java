package net.thesmallthings.hellcraft.blood;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.thesmallthings.hellcraft.config.HellConfig;

import java.util.UUID;

/**
 * Hell is full. Players who run out of hearts are not banned: they linger as ghosts (spectators)
 * tethered to where they fell until someone pays blood to bring them back.
 */
public final class Ghosts {
	private Ghosts() {
	}

	public static void makeGhost(ServerPlayer player) {
		HellState.Soul soul = Hearts.soul(player);
		if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
			player.setGameMode(GameType.SPECTATOR);
		}
		if (soul.deathSpot != null) {
			teleport(player, soul.deathSpot);
		}
		player.sendSystemMessage(Component.literal("Hell is full. You wander as a ghost until the living pay blood for your return.")
				.withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
	}

	/** Keeps ghosts near their grave. Called about once a second. */
	public static void tether(ServerPlayer player, HellState.Soul soul) {
		if (soul.deathSpot == null) {
			return;
		}
		if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR && !player.hasPermissions(2)) {
			player.setGameMode(GameType.SPECTATOR);
		}
		if (player.getCamera() != player) {
			player.setCamera(player);
		}
		double r = HellConfig.get().ghostTetherRadius;
		BlockPos p = soul.deathSpot.pos();
		boolean wrongWorld = !player.level().dimension().equals(soul.deathSpot.dimension());
		if (wrongWorld || player.distanceToSqr(p.getX() + 0.5, p.getY(), p.getZ() + 0.5) > r * r) {
			teleport(player, soul.deathSpot);
		}
	}

	/** Revives a ghost at an altar. Works for offline players too (applied when they next join). */
	public static void revive(MinecraftServer server, UUID id, HellState.GlobalSpot at) {
		HellState state = HellState.get(server);
		HellState.Soul soul = state.existing(id);
		if (soul == null) {
			return;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(id);
		if (player == null) {
			soul.reviveAt = at;
			state.setDirty();
			server.getPlayerList().broadcastSystemMessage(Component.literal(soul.name + " has been bought back from the dead. They will rise when next they wake.")
					.withStyle(ChatFormatting.GOLD), false);
			return;
		}
		finishRevive(player, soul, at);
	}

	public static void finishRevive(ServerPlayer player, HellState.Soul soul, HellState.GlobalSpot at) {
		HellState state = HellState.get(player.server);
		soul.ghost = false;
		soul.reviveAt = null;
		soul.hearts = HellConfig.get().reviveHearts;
		state.setDirty();
		player.setGameMode(GameType.SURVIVAL);
		teleport(player, at);
		Hearts.apply(player);
		player.setHealth(player.getMaxHealth());
		player.server.getPlayerList().broadcastSystemMessage(Component.literal(player.getGameProfile().getName()
				+ " has been bought back from the dead with blood.").withStyle(ChatFormatting.GOLD), false);
	}

	public static void teleport(ServerPlayer player, HellState.GlobalSpot spot) {
		ServerLevel level = player.server.getLevel(spot.dimension());
		if (level == null) {
			level = player.server.overworld();
		}
		BlockPos p = spot.pos();
		player.teleportTo(level, p.getX() + 0.5, p.getY(), p.getZ() + 0.5, player.getYRot(), player.getXRot());
	}
}
