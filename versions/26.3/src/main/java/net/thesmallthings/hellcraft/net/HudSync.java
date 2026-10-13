package net.thesmallthings.hellcraft.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.config.HellConfig;

/**
 * Tells each player's game how to draw their health bar: on join, when their prestige rank changes, and
 * when an operator changes the settings. The client draws it (BloodHud in the client sources).
 */
public final class HudSync {
	private HudSync() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(HudPayload.TYPE, HudPayload.CODEC);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.getPlayer()));
	}

	public static void send(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, HudPayload.TYPE)) {
			return;
		}
		HellState.Soul soul = HellState.get(player.level().getServer()).existing(player.getUUID());
		ServerPlayNetworking.send(player, new HudPayload(HellConfig.get().heartHud, soul == null ? 0 : soul.prestige));
	}

	public static void sendAll(MinecraftServer server) {
		server.getPlayerList().getPlayers().forEach(HudSync::send);
	}
}
