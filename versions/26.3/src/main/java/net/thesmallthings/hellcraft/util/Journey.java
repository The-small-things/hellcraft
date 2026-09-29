package net.thesmallthings.hellcraft.util;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.thesmallthings.hellcraft.HellcraftMod;

/**
 * Dante's Journey, the Hellcraft advancement tab (data/hellcraft/advancement/journey). Places are
 * reached through vanilla location triggers; deeds (a guardian slain, a P burned) are awarded here,
 * through each advancement's single "done" criterion.
 */
public final class Journey {
	private Journey() {
	}

	public static void award(ServerPlayer player, String path) {
		MinecraftServer server = player.level().getServer();
		AdvancementHolder holder = server.getAdvancements().get(HellcraftMod.id(path));
		if (holder == null) {
			return;
		}
		player.getAdvancements().award(holder, "done");
	}

	/** Logs how many Journey advancements loaded (a datapack error drops them silently otherwise). */
	public static int check(MinecraftServer server) {
		int n = 0;
		for (AdvancementHolder holder : server.getAdvancements().getAllAdvancements()) {
			if (holder.id().getNamespace().equals(HellcraftMod.MOD_ID)) {
				n++;
			}
		}
		HellcraftMod.LOGGER.info("Hellcraft advancements: {}", n);
		return n;
	}
}
