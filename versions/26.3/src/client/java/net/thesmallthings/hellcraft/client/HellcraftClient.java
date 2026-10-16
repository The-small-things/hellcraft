package net.thesmallthings.hellcraft.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.world.phys.AABB;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.client.cutscene.CutscenePlayer;
import net.thesmallthings.hellcraft.client.danger.DangerMarks;
import net.thesmallthings.hellcraft.client.dialogue.DialogueHud;
import net.thesmallthings.hellcraft.client.layer.GazeOverlay;
import net.thesmallthings.hellcraft.client.layer.LayerFx;
import net.thesmallthings.hellcraft.client.layer.SoulFlocks;
import net.thesmallthings.hellcraft.client.nav.ClientNav;
import net.thesmallthings.hellcraft.client.nav.CompassHud;
import net.thesmallthings.hellcraft.client.nav.JournalScreen;
import net.thesmallthings.hellcraft.client.nav.LayerCard;
import net.thesmallthings.hellcraft.client.nav.MapTexture;
import net.thesmallthings.hellcraft.client.nav.NavKeys;
import net.thesmallthings.hellcraft.client.render.TestRigModel;
import net.thesmallthings.hellcraft.client.render.TestRigRenderer;
import net.thesmallthings.hellcraft.client.render.boss.AnimatedBossRenderer;
import net.thesmallthings.hellcraft.client.render.boss.CerberusModel;
import net.thesmallthings.hellcraft.client.render.boss.CerberusRenderer;
import net.thesmallthings.hellcraft.client.render.boss.CoinModel;
import net.thesmallthings.hellcraft.client.render.boss.CoinRenderer;
import net.thesmallthings.hellcraft.client.render.boss.FerryModel;
import net.thesmallthings.hellcraft.client.render.boss.FerryRenderer;
import net.thesmallthings.hellcraft.client.render.boss.MinosModel;
import net.thesmallthings.hellcraft.client.render.boss.MinosRenderer;
import net.thesmallthings.hellcraft.client.render.boss.PhlegyasModel;
import net.thesmallthings.hellcraft.client.render.boss.PhlegyasRenderer;
import net.thesmallthings.hellcraft.client.render.boss.PlutusModel;
import net.thesmallthings.hellcraft.client.render.boss.PlutusRenderer;
import net.thesmallthings.hellcraft.client.render.gen.AntaeusAnimations;
import net.thesmallthings.hellcraft.client.render.gen.AntaeusMesh;
import net.thesmallthings.hellcraft.client.render.gen.CentaurAnimations;
import net.thesmallthings.hellcraft.client.render.gen.CentaurMesh;
import net.thesmallthings.hellcraft.client.render.gen.CerberusMesh;
import net.thesmallthings.hellcraft.client.render.gen.CoinMesh;
import net.thesmallthings.hellcraft.client.render.gen.FarinataAnimations;
import net.thesmallthings.hellcraft.client.render.gen.FarinataMesh;
import net.thesmallthings.hellcraft.client.render.gen.FerryMesh;
import net.thesmallthings.hellcraft.client.render.gen.FuryAnimations;
import net.thesmallthings.hellcraft.client.render.gen.FuryMesh;
import net.thesmallthings.hellcraft.client.render.gen.GeryonAnimations;
import net.thesmallthings.hellcraft.client.render.gen.GeryonMesh;
import net.thesmallthings.hellcraft.client.render.gen.GluttonAnimations;
import net.thesmallthings.hellcraft.client.render.gen.GluttonMesh;
import net.thesmallthings.hellcraft.client.render.gen.HarpyAnimations;
import net.thesmallthings.hellcraft.client.render.gen.HarpyMesh;
import net.thesmallthings.hellcraft.client.render.gen.HereticAnimations;
import net.thesmallthings.hellcraft.client.render.gen.HereticMesh;
import net.thesmallthings.hellcraft.client.render.gen.HoarderMesh;
import net.thesmallthings.hellcraft.client.render.gen.LoversAnimations;
import net.thesmallthings.hellcraft.client.render.gen.LoversMesh;
import net.thesmallthings.hellcraft.client.render.gen.MalacodaAnimations;
import net.thesmallthings.hellcraft.client.render.gen.MalacodaMesh;
import net.thesmallthings.hellcraft.client.render.gen.MalebrancheAnimations;
import net.thesmallthings.hellcraft.client.render.gen.MalebrancheMesh;
import net.thesmallthings.hellcraft.client.render.gen.MedusaAnimations;
import net.thesmallthings.hellcraft.client.render.gen.MedusaMesh;
import net.thesmallthings.hellcraft.client.render.gen.MimicAnimations;
import net.thesmallthings.hellcraft.client.render.gen.MimicMesh;
import net.thesmallthings.hellcraft.client.render.gen.MinosMesh;
import net.thesmallthings.hellcraft.client.render.gen.MinotaurAnimations;
import net.thesmallthings.hellcraft.client.render.gen.MinotaurMesh;
import net.thesmallthings.hellcraft.client.render.gen.PhlegyasMesh;
import net.thesmallthings.hellcraft.client.render.gen.PlutusMesh;
import net.thesmallthings.hellcraft.client.render.gen.ShadeMesh;
import net.thesmallthings.hellcraft.client.render.gen.ThiefAnimations;
import net.thesmallthings.hellcraft.client.render.gen.ThiefMesh;
import net.thesmallthings.hellcraft.client.render.gen.WrathfulAnimations;
import net.thesmallthings.hellcraft.client.render.gen.WrathfulMesh;
import net.thesmallthings.hellcraft.client.render.monster.HoarderModel;
import net.thesmallthings.hellcraft.client.render.monster.HoarderRenderer;
import net.thesmallthings.hellcraft.client.render.monster.SoulMonsterModel;
import net.thesmallthings.hellcraft.client.render.monster.SoulMonsterRenderer;
import net.thesmallthings.hellcraft.client.render.npc.ShadeModel;
import net.thesmallthings.hellcraft.client.render.npc.ShadeRenderer;
import net.thesmallthings.hellcraft.entity.HellEntities;
import net.thesmallthings.hellcraft.entity.boss.AntaeusEntity;
import net.thesmallthings.hellcraft.entity.boss.MedusaEntity;
import net.thesmallthings.hellcraft.entity.boss.gen.MedusaAnim;
import net.thesmallthings.hellcraft.entity.boss.gen.MinotaurAnim;
import net.thesmallthings.hellcraft.net.CutscenePayload;
import net.thesmallthings.hellcraft.net.DangerPayload;
import net.thesmallthings.hellcraft.net.DialoguePayload;
import net.thesmallthings.hellcraft.net.HudPayload;
import net.thesmallthings.hellcraft.net.JournalPayload;
import net.thesmallthings.hellcraft.net.MapPayload;
import net.thesmallthings.hellcraft.net.OpenJournalPayload;
import net.thesmallthings.hellcraft.net.VersionPayload;

/** Hellcraft's client side. */
public class HellcraftClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// the server asks which version we have while we join (JoinCheck)
		ClientConfigurationNetworking.registerGlobalReceiver(VersionPayload.TYPE,
				(payload, context) -> context.responseSender().sendPacket(new VersionPayload(HellcraftMod.VERSION)));
		ClientPlayNetworking.registerGlobalReceiver(HudPayload.TYPE, (payload, context) -> BloodHud.set(payload.bloodHearts(), payload.rank()));
		ClientPlayNetworking.registerGlobalReceiver(MapPayload.TYPE, (payload, context) -> {
			ClientNav.map(payload);
			if (payload.inferno()) {
				MapTexture.prepare();
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(JournalPayload.TYPE, (payload, context) -> ClientNav.journal(payload));
		ClientPlayNetworking.registerGlobalReceiver(OpenJournalPayload.TYPE, (payload, context) -> context.client().gui.setScreen(new JournalScreen()));
		ClientPlayNetworking.registerGlobalReceiver(DangerPayload.TYPE, (payload, context) -> DangerMarks.receive(payload));
		ClientPlayNetworking.registerGlobalReceiver(CutscenePayload.TYPE, (payload, context) -> CutscenePlayer.start(payload));
		ClientPlayNetworking.registerGlobalReceiver(DialoguePayload.TYPE, (payload, context) -> DialogueHud.receive(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			DangerMarks.clear();
			CutscenePlayer.reset();
			BloodHud.reset();
			ClientNav.reset();
			LayerCard.reset();
			DialogueHud.reset();
			GazeOverlay.reset();
		});

		// finding the way: the circle cards, the compass, and the keys for the Map of Hell and the Journal
		NavKeys.register();
		DashKey.register();
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			LayerCard.tick(client);
			NavKeys.tick(client);
			DashKey.tick(client);
			DangerMarks.tick(client);
			CutscenePlayer.tick(client);
			LayerFx.tick(client);
			DialogueHud.tick(client);
			GazeOverlay.tick(client);
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, CompassHud.ID, new CompassHud());
		HudElementRegistry.attachElementBefore(VanillaHudElements.TITLE_AND_SUBTITLE, LayerCard.ID, new LayerCard());
		// what the souls of Hell say, in a box above the hotbar instead of the chat
		HudElementRegistry.attachElementAfter(VanillaHudElements.OVERLAY_MESSAGE, DialogueHud.ID, new DialogueHud());
		// Medusa's gaze greys the screen of whoever looks at her
		HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR, GazeOverlay.ID, new GazeOverlay());

		// the danger language's marks on the ground
		LevelRenderEvents.COLLECT_SUBMITS.register(DangerMarks::render);
		// Lust's wind streams, and the flocks of souls in its sky
		LevelRenderEvents.COLLECT_SUBMITS.register(LayerFx::render);
		LevelRenderEvents.COLLECT_SUBMITS.register(SoulFlocks::render);

		ModelLayerRegistry.registerModelLayer(TestRigModel.LAYER, TestRigModel::createLayer);
		EntityRendererRegistry.register(HellEntities.TEST_RIG, TestRigRenderer::new);
		ModelLayerRegistry.registerModelLayer(MinosModel.LAYER, MinosMesh::createLayer);
		EntityRendererRegistry.register(HellEntities.MINOS, MinosRenderer::new);
		ModelLayerRegistry.registerModelLayer(CerberusModel.LAYER, CerberusMesh::createLayer);
		EntityRendererRegistry.register(HellEntities.CERBERUS, CerberusRenderer::new);
		ModelLayerRegistry.registerModelLayer(PlutusModel.LAYER, PlutusMesh::createLayer);
		EntityRendererRegistry.register(HellEntities.PLUTUS, PlutusRenderer::new);
		ModelLayerRegistry.registerModelLayer(CoinModel.LAYER, CoinMesh::createLayer);
		EntityRendererRegistry.register(HellEntities.ROLLING_COIN, CoinRenderer::new);
		ModelLayerRegistry.registerModelLayer(PhlegyasModel.LAYER, PhlegyasMesh::createLayer);
		EntityRendererRegistry.register(HellEntities.PHLEGYAS, PhlegyasRenderer::new);
		ModelLayerRegistry.registerModelLayer(FerryModel.LAYER, FerryMesh::createLayer);
		EntityRendererRegistry.register(HellEntities.FERRY, FerryRenderer::new);
		// the upper circles' monsters: the lovers of Lust (see-through), the gluttons, the wrathful
		ModelLayerRegistry.registerModelLayer(SoulMonsterRenderer.layer("lovers"), LoversMesh::createLayer);
		EntityRendererRegistry.register(HellEntities.LOVERS, context -> new SoulMonsterRenderer<>(context, SoulMonsterRenderer.layer("lovers"),
				new SoulMonsterModel.Anims(LoversAnimations.IDLE, null, LoversAnimations.ATTACK, LoversAnimations.HURT, LoversAnimations.DEATH, null),
				"lovers", 0.0f, 175));
		ModelLayerRegistry.registerModelLayer(SoulMonsterRenderer.layer("glutton"), GluttonMesh::createLayer);
		EntityRendererRegistry.register(HellEntities.GLUTTON, context -> new SoulMonsterRenderer<>(context, SoulMonsterRenderer.layer("glutton"),
				new SoulMonsterModel.Anims(GluttonAnimations.IDLE, GluttonAnimations.WALK, GluttonAnimations.ATTACK, GluttonAnimations.HURT,
						GluttonAnimations.DEATH, GluttonAnimations.EMERGE), "glutton", 0.8f, 255, "body", "head"));
		ModelLayerRegistry.registerModelLayer(SoulMonsterRenderer.layer("wrathful"), WrathfulMesh::createLayer);
		EntityRendererRegistry.register(HellEntities.WRATHFUL, context -> new SoulMonsterRenderer<>(context, SoulMonsterRenderer.layer("wrathful"),
				new SoulMonsterModel.Anims(WrathfulAnimations.IDLE, WrathfulAnimations.WALK, WrathfulAnimations.ATTACK, WrathfulAnimations.HURT,
						WrathfulAnimations.DEATH, WrathfulAnimations.RAGE), "wrathful", 0.5f, 255, "body", "head"));
		// Lower Hell's: the heretics out of their tombs, the centaurs of the river of blood, the harpies of the Wood
		ModelLayerRegistry.registerModelLayer(SoulMonsterRenderer.layer("heretic"), HereticMesh::createLayer);
		EntityRendererRegistry.register(HellEntities.HERETIC, context -> new SoulMonsterRenderer<>(context, SoulMonsterRenderer.layer("heretic"),
				new SoulMonsterModel.Anims(HereticAnimations.IDLE, HereticAnimations.WALK, HereticAnimations.ATTACK, HereticAnimations.HURT,
						HereticAnimations.DEATH, HereticAnimations.RISE), "heretic", 0.5f, 255, "body", "head"));
		ModelLayerRegistry.registerModelLayer(SoulMonsterRenderer.layer("centaur"), CentaurMesh::createLayer);
		EntityRendererRegistry.register(HellEntities.CENTAUR, context -> new SoulMonsterRenderer<>(context, SoulMonsterRenderer.layer("centaur"),
				new SoulMonsterModel.Anims(CentaurAnimations.IDLE, CentaurAnimations.WALK, CentaurAnimations.ATTACK, CentaurAnimations.HURT,
						CentaurAnimations.DEATH, CentaurAnimations.LOOK), "centaur", 1.0f, 255, "horse", "torso", "head"));
		ModelLayerRegistry.registerModelLayer(SoulMonsterRenderer.layer("harpy"), HarpyMesh::createLayer);
		EntityRendererRegistry.register(HellEntities.HARPY, context -> new SoulMonsterRenderer<>(context, SoulMonsterRenderer.layer("harpy"),
				new SoulMonsterModel.Anims(HarpyAnimations.IDLE, null, HarpyAnimations.ATTACK, HarpyAnimations.HURT, HarpyAnimations.DEATH,
						HarpyAnimations.SHRIEK), "harpy", 0.4f, 255, "body", "head"));
		// Malebolge's: mimics (no glow: nothing gives them away but their breathing) and thieves
		ModelLayerRegistry.registerModelLayer(SoulMonsterRenderer.layer("mimic"), MimicMesh::createLayer);
		EntityRendererRegistry.register(HellEntities.MIMIC, context -> new SoulMonsterRenderer<>(context, SoulMonsterRenderer.layer("mimic"),
				new SoulMonsterModel.Anims(MimicAnimations.IDLE, MimicAnimations.WALK, MimicAnimations.ATTACK, MimicAnimations.HURT,
						MimicAnimations.DEATH, MimicAnimations.BREATHE), "mimic", 0.4f, 255));
		ModelLayerRegistry.registerModelLayer(SoulMonsterRenderer.layer("thief"), ThiefMesh::createLayer);
		EntityRendererRegistry.register(HellEntities.THIEF, context -> new SoulMonsterRenderer<>(context, SoulMonsterRenderer.layer("thief"),
				new SoulMonsterModel.Anims(ThiefAnimations.IDLE, ThiefAnimations.WALK, ThiefAnimations.ATTACK, ThiefAnimations.HURT,
						ThiefAnimations.DEATH, ThiefAnimations.FLEE), "thief", 0.5f, 255, "body", "head"));
		// Lower Hell's guardians (AnimatedBoss: one renderer for all), Medusa, the tamed Geryon and Antaeus
		ModelLayerRegistry.registerModelLayer(AnimatedBossRenderer.layer("fury"), FuryMesh::createLayer);
		EntityRendererRegistry.register(HellEntities.FURY, context -> new AnimatedBossRenderer<>(context, "fury", FuryAnimations.all(), -1, 1.4f, 1.0f,
				0.5f, "body", "torso", "head"));
		ModelLayerRegistry.registerModelLayer(AnimatedBossRenderer.layer("medusa"), MedusaMesh::createLayer);
		EntityRendererRegistry.register(HellEntities.MEDUSA, context -> {
			AnimatedBossRenderer<MedusaEntity> r = new AnimatedBossRenderer<>(context, "medusa", MedusaAnimations.all(), -1, MedusaEntity.SCALE, 0.0f,
					0.6f, "bust", "head");
			// her open eyes show only while she gazes
			r.getModel().onlyDuring(MedusaAnim.GAZE.ordinal(), "bust", "head", "eyes");
			return r;
		});
		ModelLayerRegistry.registerModelLayer(AnimatedBossRenderer.layer("farinata"), FarinataMesh::createLayer);
		EntityRendererRegistry.register(HellEntities.FARINATA, context -> new AnimatedBossRenderer<>(context, "farinata", FarinataAnimations.all(), -1,
				1.8f, 1.0f, 0.6f, "body", "torso", "head"));
		ModelLayerRegistry.registerModelLayer(AnimatedBossRenderer.layer("minotaur"), MinotaurMesh::createLayer);
		EntityRendererRegistry.register(HellEntities.MINOTAUR, context -> new AnimatedBossRenderer<>(context, "minotaur", MinotaurAnimations.all(),
				MinotaurAnim.WALK.ordinal(), 1.4f, 2.0f, 0.5f, "hips", "torso", "head"));
		ModelLayerRegistry.registerModelLayer(AnimatedBossRenderer.layer("geryon"), GeryonMesh::createLayer);
		EntityRendererRegistry.register(HellEntities.GERYON, context -> new AnimatedBossRenderer<>(context, "geryon", GeryonAnimations.all(), -1, 2.0f,
				2.0f, 0.4f, "root", "chest", "neck", "head"));
		EntityRendererRegistry.register(HellEntities.GERYON_CARRIER, context -> new AnimatedBossRenderer<>(context, "geryon", GeryonAnimations.all(), -1,
				2.0f, 2.0f, 0.0f));
		ModelLayerRegistry.registerModelLayer(AnimatedBossRenderer.layer("antaeus"), AntaeusMesh::createLayer);
		EntityRendererRegistry.register(HellEntities.ANTAEUS, context -> new AnimatedBossRenderer<>(context, "antaeus", AntaeusAnimations.all(), -1,
				AntaeusEntity.SCALE, 0.0f, 0.0f) {
			// his arm reaches far past his feet: don't hide him when only his hand is in view
			@Override
			protected AABB getBoundingBoxForCulling(AntaeusEntity entity, float partialTicks) {
				return super.getBoundingBoxForCulling(entity, partialTicks).inflate(16);
			}
		});
		// the Malebranche (special: the jeer) and their captain Malacoda (attack: pointing the way; special: his laugh)
		ModelLayerRegistry.registerModelLayer(SoulMonsterRenderer.layer("malebranche"), MalebrancheMesh::createLayer);
		EntityRendererRegistry.register(HellEntities.MALEBRANCHE, context -> new SoulMonsterRenderer<>(context, SoulMonsterRenderer.layer("malebranche"),
				new SoulMonsterModel.Anims(MalebrancheAnimations.IDLE, MalebrancheAnimations.WALK, MalebrancheAnimations.ATTACK,
						MalebrancheAnimations.HURT, MalebrancheAnimations.DEATH, MalebrancheAnimations.JEER), "malebranche", 0.6f, 255, "body", "head"));
		ModelLayerRegistry.registerModelLayer(SoulMonsterRenderer.layer("malacoda"), MalacodaMesh::createLayer);
		EntityRendererRegistry.register(HellEntities.MALACODA, context -> new SoulMonsterRenderer<>(context, SoulMonsterRenderer.layer("malacoda"),
				new SoulMonsterModel.Anims(MalacodaAnimations.IDLE, MalacodaAnimations.WALK, MalacodaAnimations.POINT, MalacodaAnimations.HURT,
						MalacodaAnimations.DEATH, MalacodaAnimations.LAUGH), "malacoda", 0.6f, 255, "body", "head"));
		ModelLayerRegistry.registerModelLayer(HoarderModel.LAYER, HoarderMesh::createLayer);
		EntityRendererRegistry.register(HellEntities.HOARDER, HoarderRenderer::new);
		ModelLayerRegistry.registerModelLayer(ShadeModel.LAYER, ShadeMesh::createLayer);
		EntityRendererRegistry.register(HellEntities.SHADE, ShadeRenderer::new);
	}
}
