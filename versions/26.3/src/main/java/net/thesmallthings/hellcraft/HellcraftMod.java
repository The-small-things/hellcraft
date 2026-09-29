package net.thesmallthings.hellcraft;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.thesmallthings.hellcraft.blood.BloodArmour;
import net.thesmallthings.hellcraft.blood.BloodEvents;
import net.thesmallthings.hellcraft.blood.GhostPowers;
import net.thesmallthings.hellcraft.blood.HellWeapons;
import net.thesmallthings.hellcraft.blood.RecipeCheck;
import net.thesmallthings.hellcraft.blood.Scoreboards;
import net.thesmallthings.hellcraft.command.HellCommands;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.hazard.Ambience;
import net.thesmallthings.hellcraft.hazard.CircleHazards;
import net.thesmallthings.hellcraft.hazard.MobEmpowerment;
import net.thesmallthings.hellcraft.hazard.guardian.GuardianManager;
import net.thesmallthings.hellcraft.hazard.lucifer.LuciferManager;
import net.thesmallthings.hellcraft.hazard.lucifer.LuciferRewards;
import net.thesmallthings.hellcraft.music.MusicPack;
import net.thesmallthings.hellcraft.world.HellWorldgen;
import net.thesmallthings.hellcraft.world.Landmarks;
import net.thesmallthings.hellcraft.world.Purgatory;
import net.thesmallthings.hellcraft.world.Shrines;
import net.thesmallthings.hellcraft.world.Paradiso;
import net.thesmallthings.hellcraft.world.Spine;
import net.thesmallthings.hellcraft.world.Villages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hellcraft: the overworld becomes Dante's Inferno, and blood is fuel.
 * Server-side only; vanilla clients can join.
 */
public class HellcraftMod implements ModInitializer {
	public static final String MOD_ID = "hellcraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		HellConfig.load();
		HellWorldgen.register();
		BloodEvents.register();
		HellWeapons.register();
		BloodArmour.register();
		CircleHazards.register();
		LuciferManager.register();
		GuardianManager.register();
		LuciferRewards.register();
		MusicPack.register();

		ServerLifecycleEvents.SERVER_STARTING.register(Paradiso::init);
		ServerLifecycleEvents.SERVER_STARTED.register(Landmarks::buildOnce);
		ServerLifecycleEvents.SERVER_STARTED.register(Spine::buildOnce);
		ServerLifecycleEvents.SERVER_STARTED.register(Purgatory::buildOnce);
		ServerLifecycleEvents.SERVER_STARTED.register(Scoreboards::setUp);
		ServerLifecycleEvents.SERVER_STARTED.register(RecipeCheck::run);
		ServerEntityEvents.ENTITY_LOAD.register(MobEmpowerment::onLoad);
		ServerEntityEvents.ENTITY_LOAD.register(Spine::onLoad);
		ServerEntityEvents.ENTITY_LOAD.register(Shrines::onLoad);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			CircleHazards.tick(server);
			LuciferManager.tick(server);
			GuardianManager.tick(server);
			Spine.tick(server);
			Shrines.tick(server);
			Villages.tick(server);
			Paradiso.tick(server);
			Ambience.tick(server);
			GhostPowers.tick(server);
			Purgatory.tick(server);
			MobEmpowerment.tick();
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			CircleHazards.forget(handler.getPlayer());
			Spine.forget(handler.getPlayer());
			Ambience.forget(handler.getPlayer());
			Purgatory.forget(handler.getPlayer());
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> HellCommands.register(dispatcher));

		LOGGER.info("Hellcraft loaded. Lasciate ogne speranza, voi ch'intrate.");
	}
}
