package net.thesmallthings.hellcraft;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.thesmallthings.hellcraft.block.HellBlocks;
import net.thesmallthings.hellcraft.blood.BloodArmour;
import net.thesmallthings.hellcraft.blood.BloodEvents;
import net.thesmallthings.hellcraft.blood.Bounty;
import net.thesmallthings.hellcraft.blood.Fairness;
import net.thesmallthings.hellcraft.blood.GhostPowers;
import net.thesmallthings.hellcraft.blood.HellItems;
import net.thesmallthings.hellcraft.blood.HellWeapons;
import net.thesmallthings.hellcraft.blood.Hellforge;
import net.thesmallthings.hellcraft.blood.LayerEnchants;
import net.thesmallthings.hellcraft.blood.Prestige;
import net.thesmallthings.hellcraft.blood.RecipeCheck;
import net.thesmallthings.hellcraft.blood.Relics;
import net.thesmallthings.hellcraft.blood.Scoreboards;
import net.thesmallthings.hellcraft.command.HellCommands;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.cutscene.CutsceneManager;
import net.thesmallthings.hellcraft.entity.HellEntities;
import net.thesmallthings.hellcraft.hazard.Ambience;
import net.thesmallthings.hellcraft.hazard.BossModel;
import net.thesmallthings.hellcraft.hazard.CircleHazards;
import net.thesmallthings.hellcraft.hazard.Danger;
import net.thesmallthings.hellcraft.hazard.Frauds;
import net.thesmallthings.hellcraft.hazard.Hoarders;
import net.thesmallthings.hellcraft.hazard.LayerMonsters;
import net.thesmallthings.hellcraft.hazard.LayerRules;
import net.thesmallthings.hellcraft.hazard.LowerMonsters;
import net.thesmallthings.hellcraft.hazard.LowerRules;
import net.thesmallthings.hellcraft.hazard.MobEmpowerment;
import net.thesmallthings.hellcraft.hazard.Wingbeat;
import net.thesmallthings.hellcraft.hazard.guardian.GuardianManager;
import net.thesmallthings.hellcraft.hazard.lucifer.LuciferManager;
import net.thesmallthings.hellcraft.hazard.lucifer.LuciferRewards;
import net.thesmallthings.hellcraft.music.BossMusic;
import net.thesmallthings.hellcraft.net.Dialogue;
import net.thesmallthings.hellcraft.net.HudSync;
import net.thesmallthings.hellcraft.net.JoinCheck;
import net.thesmallthings.hellcraft.net.MapSync;
import net.thesmallthings.hellcraft.util.Journey;
import net.thesmallthings.hellcraft.world.Ferries;
import net.thesmallthings.hellcraft.world.GreatForge;
import net.thesmallthings.hellcraft.world.HallOfTheDamned;
import net.thesmallthings.hellcraft.world.Heaven;
import net.thesmallthings.hellcraft.world.HellWorldgen;
import net.thesmallthings.hellcraft.world.Landmarks;
import net.thesmallthings.hellcraft.world.Paradiso;
import net.thesmallthings.hellcraft.world.Purgatory;
import net.thesmallthings.hellcraft.world.Shades;
import net.thesmallthings.hellcraft.world.Shrines;
import net.thesmallthings.hellcraft.world.Spine;
import net.thesmallthings.hellcraft.world.Villages;
import net.thesmallthings.hellcraft.world.WhiteRoom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hellcraft: the overworld becomes Dante's Inferno, and blood is fuel. Players need the mod too (see JoinCheck);
 * its client side is HellcraftClient.
 */
public class HellcraftMod implements ModInitializer {
	public static final String MOD_ID = "hellcraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	/** This build's version, for example "3.0.0+26.3". Players must have exactly the server's. */
	public static final String VERSION = FabricLoader.getInstance().getModContainer(MOD_ID)
			.map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("unknown");

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		HellConfig.load();
		JoinCheck.register();
		HellBlocks.register();
		HellItems.register();
		HellEntities.register();
		net.thesmallthings.hellcraft.entity.boss.CyclopsEntity.register();
		HellWorldgen.register();
		BloodEvents.register();
		Fairness.register();
		HellWeapons.register();
		Hellforge.register();
		Relics.register();
		Heaven.register();
		Paradiso.register();
		BloodArmour.register();
		LayerEnchants.register();
		Wingbeat.register();
		CircleHazards.register();
		LuciferManager.register();
		GuardianManager.register();
		BossModel.register();
		LuciferRewards.register();
		HudSync.register();
		Dialogue.register();
		MapSync.register();
		Danger.register();
		CutsceneManager.register();

		ServerLifecycleEvents.SERVER_STARTED.register(Landmarks::buildOnce);
		ServerLifecycleEvents.SERVER_STARTED.register(HallOfTheDamned::buildOnce);
		ServerLifecycleEvents.SERVER_STARTED.register(Spine::buildOnce);
		ServerLifecycleEvents.SERVER_STARTED.register(Purgatory::buildOnce);
		ServerLifecycleEvents.SERVER_STARTED.register(Heaven::buildAscent);
		ServerLifecycleEvents.SERVER_STARTED.register(Prestige::setUp);
		ServerLifecycleEvents.SERVER_STARTED.register(Scoreboards::setUp);
		ServerLifecycleEvents.SERVER_STARTED.register(Journey::check);
		ServerLifecycleEvents.SERVER_STARTED.register(RecipeCheck::run);
		ServerLifecycleEvents.SERVER_STARTED.register(Villages::setUp);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> BossMusic.log());
		ServerEntityEvents.ENTITY_LOAD.register(MobEmpowerment::onLoad);
		ServerEntityEvents.ENTITY_LOAD.register(Spine::onLoad);
		ServerEntityEvents.ENTITY_LOAD.register(Shrines::onLoad);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			CircleHazards.tick(server);
			LayerRules.tick(server);
			LowerRules.tick(server);
			LowerMonsters.tick(server);
			Frauds.tick(server);
			Hoarders.tick(server);
			LayerMonsters.tick(server);
			Shades.tick(server);
			Ferries.tick(server);
			Wingbeat.tick(server);
			LuciferManager.tick(server);
			GuardianManager.tick(server);
			BossModel.tickFalling(server);
			Spine.tick(server);
			Shrines.tick(server);
			Villages.tick(server);
			Paradiso.tick(server);
			GreatForge.tick(server);
			net.thesmallthings.hellcraft.world.ForgeOfDis.tick(server);
			Relics.tick(server);
			Ambience.tick(server);
			GhostPowers.tick(server);
			Purgatory.tick(server);
			Prestige.tick(server);
			HallOfTheDamned.tick(server);
			WhiteRoom.tick(server);
			Bounty.tick(server);
			Fairness.tick(server);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			CircleHazards.forget(handler.getPlayer());
			Spine.forget(handler.getPlayer());
			Ambience.forget(handler.getPlayer());
			Purgatory.forget(handler.getPlayer());
			Paradiso.forget(handler.getPlayer());
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> HellCommands.register(dispatcher));

		LOGGER.info("Hellcraft loaded. Lasciate ogne speranza, voi ch'intrate.");
	}
}
