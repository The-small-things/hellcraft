package net.thesmallthings.hellcraft.world;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Villagers under Hell's stopped clock. Their routine comes from data/hellcraft/timeline/villager_schedule.json
 * (they work around the clock, so they take jobs); and since the day never turns, their trades restock here
 * instead, every ten minutes.
 */
public final class Villages {
	private Villages() {
	}

	private static final int RESTOCK_TICKS = 20 * 60 * 10;
	private static int ticks;

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
