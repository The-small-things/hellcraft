package net.thesmallthings.hellcraft.world;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.thesmallthings.hellcraft.HellcraftMod;

import java.util.Optional;

/**
 * Villagers under Hell's stopped clock. Their routine comes from data/hellcraft/timeline/villager_schedule.json,
 * which runs on their own clock (hellcraft:villager_day) since the overworld's is stopped at dusk: a ten-minute
 * day of work, a meeting at the bell and idle time (when they breed). Their trades restock here every ten minutes.
 */
public final class Villages {
	private Villages() {
	}

	private static final int RESTOCK_TICKS = 20 * 60 * 10;
	private static int ticks;

	/** Makes sure the villagers' clock runs (nothing should pause it, but a /time command could). */
	public static void setUp(MinecraftServer server) {
		Optional<Holder.Reference<WorldClock>> clock = server.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK)
				.get(ResourceKey.create(Registries.WORLD_CLOCK, Identifier.fromNamespaceAndPath(HellcraftMod.MOD_ID, "villager_day")));
		if (clock.isEmpty()) {
			HellcraftMod.LOGGER.error("The villagers' clock hellcraft:villager_day is missing: villagers will not breed");
			return;
		}
		server.clockManager().setPaused(clock.get(), false);
		HellcraftMod.LOGGER.info("Villager clock running: work, meet, idle (breeding) every 10 minutes");
	}

	public static void tick(MinecraftServer server) {
		if (++ticks % RESTOCK_TICKS != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		if (!HellWorldgen.isInferno(level)) {
			return;
		}
		for (Villager villager : level.getEntities(EntityTypes.VILLAGER, v -> v.isAlive() && !v.isBaby())) {
			villager.restock();
		}
	}
}
