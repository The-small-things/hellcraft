package net.thesmallthings.hellcraft;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.ResourceLocation;
import net.thesmallthings.hellcraft.blood.BloodEvents;
import net.thesmallthings.hellcraft.command.HellCommands;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.hazard.CircleHazards;
import net.thesmallthings.hellcraft.hazard.MobEmpowerment;
import net.thesmallthings.hellcraft.hazard.lucifer.LuciferManager;
import net.thesmallthings.hellcraft.world.HellWorldgen;
import net.thesmallthings.hellcraft.world.Landmarks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hellcraft: the overworld becomes Dante's Inferno, and blood is fuel.
 * Server-side only; vanilla clients can join.
 */
public class HellcraftMod implements ModInitializer {
	public static final String MOD_ID = "hellcraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		HellConfig.load();
		HellWorldgen.register();
		BloodEvents.register();
		CircleHazards.register();
		LuciferManager.register();

		ServerLifecycleEvents.SERVER_STARTED.register(Landmarks::buildOnce);
		ServerEntityEvents.ENTITY_LOAD.register(MobEmpowerment::onLoad);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			CircleHazards.tick(server);
			LuciferManager.tick(server);
			MobEmpowerment.tick();
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> CircleHazards.forget(handler.getPlayer()));
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> HellCommands.register(dispatcher));

		LOGGER.info("Hellcraft loaded. Lasciate ogne speranza, voi ch'intrate.");
	}
}
