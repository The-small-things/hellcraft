package net.thesmallthings.hellcraft.hazard;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.thesmallthings.hellcraft.net.GlowPayload;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Which entities have a faint glow of their own, drawn brighter than the dark round them (client layer/Glow): the
 * onlookers (Onlookers) and whoever carries the Keys of Peter (KeysOfPeter). Players are told whenever it changes, and
 * when they join.
 */
public final class Glow {
	private Glow() {
	}

	private static final Map<UUID, Integer> GLOWING = new HashMap<>();
	@Nullable
	private static UUID keys;

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(GlowPayload.TYPE, GlowPayload.CODEC);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.getPlayer()));
	}

	public static void add(MinecraftServer server, Entity entity) {
		if (!Integer.valueOf(entity.getId()).equals(GLOWING.put(entity.getUUID(), entity.getId()))) {
			broadcast(server);
		}
	}

	public static void remove(MinecraftServer server, UUID id) {
		if (GLOWING.remove(id) != null) {
			broadcast(server);
		}
	}

	/** Whoever carries the Keys of Peter now (null: no-one). */
	public static void keys(MinecraftServer server, @Nullable ServerPlayer holder) {
		UUID now = holder == null ? null : holder.getUUID();
		if (java.util.Objects.equals(now, keys) && (holder == null || Integer.valueOf(holder.getId()).equals(GLOWING.get(now)))) {
			return;
		}
		if (keys != null) {
			GLOWING.remove(keys);
		}
		keys = now;
		if (holder != null) {
			GLOWING.put(holder.getUUID(), holder.getId());
		}
		broadcast(server);
	}

	static void send(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, GlowPayload.TYPE)) {
			ServerPlayNetworking.send(player, new GlowPayload(new ArrayList<>(GLOWING.values())));
		}
	}

	private static void broadcast(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			send(player);
		}
	}
}
