package net.thesmallthings.hellcraft.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.thesmallthings.hellcraft.blood.Guide;
import net.thesmallthings.hellcraft.blood.Hearts;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.codex.Codex;
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
		PayloadTypeRegistry.clientboundPlay().register(OpenJournalPayload.TYPE, OpenJournalPayload.CODEC);
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

	/** Opens a Codex entry for a player who has just met its boss, and tells them where to read it. */
	public static void unlockCodex(ServerPlayer player, String id) {
		Codex.Entry entry = Codex.byId(id);
		if (entry == null || !Hearts.soul(player).codex.add(id)) {
			return;
		}
		HellState.get(player.level().getServer()).setDirty();
		Dialogue.notice(player, Component.literal("New in your Journal's Codex: " + entry.title().charAt(0) + entry.title().substring(1).toLowerCase(java.util.Locale.ROOT)
				+ ", " + entry.subtitle() + ". Press ").withStyle(ChatFormatting.GOLD)
				.append(Component.keybind("key.hellcraft.journal").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
				.append(Component.literal(" to read how to fight it.").withStyle(ChatFormatting.GOLD)));
		send(player);
	}

	/** Opens the Journal on the player's screen (/guide). */
	public static void openJournal(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, OpenJournalPayload.TYPE)) {
			ServerPlayNetworking.send(player, OpenJournalPayload.INSTANCE);
		} else {
			player.sendSystemMessage(Component.literal("The Journal needs Hellcraft on your side too: install it, then press J."));
		}
	}

	private static void sendJournal(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, JournalPayload.TYPE)) {
			ServerPlayNetworking.send(player, new JournalPayload(Guide.pages(player.level().getServer())));
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
		List<String> codex = soul == null ? List.of() : List.copyOf(soul.codex);
		boolean inferno = HellWorldgen.isInferno(player.level().getServer().overworld());
		ServerPlayNetworking.send(player, new MapPayload(inferno, HellConfig.get().circleTitles, visited, beaten, codex,
				soul == null ? 0 : soul.spheres));
	}
}
