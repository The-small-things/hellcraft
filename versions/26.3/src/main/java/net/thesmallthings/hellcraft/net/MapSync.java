package net.thesmallthings.hellcraft.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.thesmallthings.hellcraft.blood.GuideBook;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.world.HellWorldgen;

import java.util.List;

/**
 * Keeps each player's compass, circle cards, Map of Hell and Journal up to date: on join, whenever they find a
 * Virgil's Rest, and whenever they beat a boss.
 */
public final class MapSync {
	private MapSync() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(MapPayload.TYPE, MapPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(JournalPayload.TYPE, JournalPayload.CODEC);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			send(handler.getPlayer());
			sendJournal(handler.getPlayer());
		});
	}

	/** Everything, to everyone: after the settings change (the cards can be turned off, the Guide's numbers move). */
	public static void sendAll(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			send(player);
			sendJournal(player);
		}
	}

	private static void sendJournal(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, JournalPayload.TYPE)) {
			ServerPlayNetworking.send(player, new JournalPayload(GuideBook.pages(player.level().getServer())));
		}
	}

	/** The map state: call when the player finds a Rest or beats a boss. */
	public static void send(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, MapPayload.TYPE)) {
			return;
		}
		HellState.Soul soul = HellState.get(player.level().getServer()).existing(player.getUUID());
		List<String> visited = soul == null ? List.of() : List.copyOf(soul.visited);
		List<String> beaten = soul == null ? List.of() : List.copyOf(soul.guardianSpoils.keySet());
		boolean inferno = HellWorldgen.isInferno(player.level().getServer().overworld());
		ServerPlayNetworking.send(player, new MapPayload(inferno, HellConfig.get().circleTitles, visited, beaten));
	}
}
