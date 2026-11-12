package net.thesmallthings.hellcraft.net;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.world.WindStreams;

/**
 * Picks this world's wind streams as the overworld loads (before any of Lust is generated, since their pads level the
 * ground): a new world draws its own from its seed and keeps them in HellState; a world made before 3.4 keeps the
 * first layout. Each player is told on joining (WindPayload), so their game blows the same streams.
 */
public final class WindSync {
	private WindSync() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(WindPayload.TYPE, WindPayload.CODEC);
		ServerLevelEvents.LOAD.register((server, level) -> {
			if (level.dimension() == Level.OVERWORLD) {
				choose(level);
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.getPlayer()));
	}

	private static void choose(ServerLevel overworld) {
		HellState state = HellState.get(overworld.getServer());
		if (!state.windChosen) {
			state.windSeed = WindStreams.seedFor(overworld.getSeed());
			state.windChosen = true;
			state.setDirty();
		}
		WindStreams.use(state.windSeed);
		HellcraftMod.LOGGER.info("Lust's wind: {} streams ({})", WindStreams.all().size(),
				state.windSeed == WindStreams.LEGACY ? "the first layout, from before 3.4" : "this world's own");
	}

	public static void send(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, WindPayload.TYPE)) {
			ServerPlayNetworking.send(player, new WindPayload(WindStreams.seed()));
		}
	}
}
