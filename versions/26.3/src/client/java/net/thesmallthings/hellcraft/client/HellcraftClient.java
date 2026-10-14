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
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.client.nav.ClientNav;
import net.thesmallthings.hellcraft.client.nav.CompassHud;
import net.thesmallthings.hellcraft.client.nav.JournalScreen;
import net.thesmallthings.hellcraft.client.nav.LayerCard;
import net.thesmallthings.hellcraft.client.nav.MapTexture;
import net.thesmallthings.hellcraft.client.nav.NavKeys;
import net.thesmallthings.hellcraft.client.render.TestRigModel;
import net.thesmallthings.hellcraft.client.render.TestRigRenderer;
import net.thesmallthings.hellcraft.client.render.boss.MinosModel;
import net.thesmallthings.hellcraft.client.render.boss.MinosRenderer;
import net.thesmallthings.hellcraft.client.render.gen.MinosMesh;
import net.thesmallthings.hellcraft.entity.HellEntities;
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
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			BloodHud.reset();
			ClientNav.reset();
			LayerCard.reset();
		});

		// finding the way: the circle cards, the compass, and the keys for the Map of Hell and the Journal
		NavKeys.register();
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			LayerCard.tick(client);
			NavKeys.tick(client);
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, CompassHud.ID, new CompassHud());
		HudElementRegistry.attachElementBefore(VanillaHudElements.TITLE_AND_SUBTITLE, LayerCard.ID, new LayerCard());

		ModelLayerRegistry.registerModelLayer(TestRigModel.LAYER, TestRigModel::createLayer);
		EntityRendererRegistry.register(HellEntities.TEST_RIG, TestRigRenderer::new);
		ModelLayerRegistry.registerModelLayer(MinosModel.LAYER, MinosMesh::createLayer);
		EntityRendererRegistry.register(HellEntities.MINOS, MinosRenderer::new);
	}
}
