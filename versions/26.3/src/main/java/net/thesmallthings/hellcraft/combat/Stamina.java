package net.thesmallthings.hellcraft.combat;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.net.StaminaPayload;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stamina: what a dodge roll, a heavy attack and a blow taken on a weapon's guard cost. It comes back by itself a moment
 * after the last time it was spent (slower while guarding, not at all mid-roll). Kept in memory only: everyone starts a
 * session full. Creative players never run out.
 */
public final class Stamina {
	private Stamina() {
	}

	private static final class State {
		float value;
		long spentAt;
		float sent = -1;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	static void register() {
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			State s = state(newPlayer);
			s.value = max();
			s.sent = -1;
		});
	}

	public static float max() {
		return HellConfig.get().staminaMax;
	}

	private static State state(ServerPlayer player) {
		return STATES.computeIfAbsent(player.getUUID(), id -> {
			State s = new State();
			s.value = max();
			return s;
		});
	}

	static void join(ServerPlayer player) {
		state(player).sent = -1;
	}

	static void forget(ServerPlayer player) {
		STATES.remove(player.getUUID());
	}

	public static float get(ServerPlayer player) {
		return player.hasInfiniteMaterials() ? max() : state(player).value;
	}

	/** Spends stamina if there is enough. Returns whether it was spent. */
	public static boolean spend(ServerPlayer player, float amount) {
		if (amount <= 0 || player.hasInfiniteMaterials()) {
			return true;
		}
		State s = state(player);
		if (s.value < amount) {
			return false;
		}
		s.value -= amount;
		s.spentAt = player.level().getGameTime();
		return true;
	}

	/** Takes stamina, as much as there is. Returns true if it ran out. */
	public static boolean drain(ServerPlayer player, float amount) {
		if (amount <= 0 || player.hasInfiniteMaterials()) {
			return false;
		}
		State s = state(player);
		s.value = Math.max(0, s.value - amount);
		s.spentAt = player.level().getGameTime();
		return s.value <= 0;
	}

	public static void restore(ServerPlayer player, float amount) {
		State s = state(player);
		s.value = Math.min(max(), s.value + amount);
	}

	static void tick(MinecraftServer server) {
		HellConfig config = HellConfig.get();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			State s = state(player);
			float max = max();
			if (s.value > max) {
				s.value = max;
			}
			long now = player.level().getGameTime();
			if (s.value < max && now - s.spentAt >= config.staminaRegenDelay && !Dodge.rolling(player)) {
				s.value = Math.min(max, s.value + regenPerTick((float) config.staminaRegen, Guard.guarding(player)));
			}
			// the bar: every change while it moves fast would be a packet a tick, so send steps of 2 and the ends
			boolean edge = (s.value >= max || s.value <= 0) && s.sent != s.value;
			if (edge || Math.abs(s.value - s.sent) >= 2.0f) {
				s.sent = s.value;
				if (ServerPlayNetworking.canSend(player, StaminaPayload.TYPE)) {
					ServerPlayNetworking.send(player, new StaminaPayload(get(player)));
				}
			}
		}
	}

	static float regenPerTick(float base, boolean guarding) {
		return guarding ? base * 0.35f : base;
	}

	static String selfTest() {
		if (regenPerTick(2.0f, true) >= regenPerTick(2.0f, false)) {
			return "stamina should come back slower on guard";
		}
		return "";
	}
}
