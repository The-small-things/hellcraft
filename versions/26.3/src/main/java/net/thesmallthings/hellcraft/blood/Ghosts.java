package net.thesmallthings.hellcraft.blood;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.GameType;
import net.thesmallthings.hellcraft.config.HellConfig;

import java.util.List;
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
		player.sendSystemMessage(Component.literal("You lost your last heart and are now a ghost. A friend can revive you.")
				.withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
		player.sendSystemMessage(Component.literal(GhostPowers.HELP).withStyle(ChatFormatting.DARK_AQUA));
		player.sendSystemMessage(Component.literal("Tell your friends:").withStyle(ChatFormatting.GRAY));
		howToRevive(player.level().getServer()).forEach(player::sendSystemMessage);
	}

	/** Step-by-step instructions for bringing a ghost back, for chat. */
	public static List<Component> howToRevive(MinecraftServer server) {
		int cost = HellConfig.get().reviveCostHearts;
		HellState.GlobalSpot altar = HellState.get(server).starterAltar;
		String where = altar != null
				? " There is one beside the Gate of Hell at " + altar.pos().getX() + " " + altar.pos().getY() + " " + altar.pos().getZ() + "."
				: " There is one beside the Gate of Hell.";
		return List.of(
				Component.literal("☠ How to revive a ghost").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				Component.literal(" 1. Carry " + cost + " Blood Hearts. /withdraw turns your own hearts into Blood Hearts;"
						+ " you can also make one from " + HellConfig.get().fragmentsPerHeart + " Blood Fragments.").withStyle(ChatFormatting.GRAY),
				Component.literal(" 2. Go to a Blood Altar: a respawn anchor on 3x3 crying obsidian." + where).withStyle(ChatFormatting.GRAY),
				Component.literal(" 3. Right-click the altar with an empty hand and click the ghost's head"
						+ " (or stand next to it and type /revive <name>).").withStyle(ChatFormatting.GRAY),
				Component.literal(" The ghost comes back on top of the altar with " + HellConfig.get().reviveHearts + " hearts.")
						.withStyle(ChatFormatting.GRAY));
	}

	/** Keeps ghosts near their grave. Called about once a second. */
	public static void tether(ServerPlayer player, HellState.Soul soul) {
		if (soul.deathSpot == null) {
			return;
		}
		if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR && !player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
			player.setGameMode(GameType.SPECTATOR);
		}
		if (player.getCamera() != player) {
			player.setCamera(player);
		}
		if (player.level().getGameTime() / 20 % 10 == 0) {
			player.sendOverlayMessage(Component.literal("☠ You are a ghost. The living can revive you at a Blood Altar for "
					+ HellConfig.get().reviveCostHearts + " Blood Hearts (/revive). Your powers: /ghost").withStyle(ChatFormatting.GRAY));
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
			server.getPlayerList().broadcastSystemMessage(Component.literal(soul.name + " was revived, and will come back when they next log in.")
					.withStyle(ChatFormatting.GOLD), false);
			return;
		}
		finishRevive(player, soul, at);
	}

	public static void finishRevive(ServerPlayer player, HellState.Soul soul, HellState.GlobalSpot at) {
		HellState state = HellState.get(player.level().getServer());
		soul.ghost = false;
		soul.reviveAt = null;
		soul.hearts = HellConfig.get().reviveHearts;
		state.setDirty();
		player.setGameMode(GameType.SURVIVAL);
		teleport(player, at);
		Hearts.apply(player);
		player.setHealth(player.getMaxHealth());
		player.level().getServer().getPlayerList().broadcastSystemMessage(Component.literal(player.getGameProfile().name()
				+ " was revived.").withStyle(ChatFormatting.GOLD), false);
	}

	public static void teleport(ServerPlayer player, HellState.GlobalSpot spot) {
		ServerLevel level = player.level().getServer().getLevel(spot.dimension());
		if (level == null) {
			level = player.level().getServer().overworld();
		}
		BlockPos p = spot.pos();
		player.teleportTo(level, p.getX() + 0.5, p.getY(), p.getZ() + 0.5, java.util.Set.of(), player.getYRot(), player.getXRot(), true);
	}
}
