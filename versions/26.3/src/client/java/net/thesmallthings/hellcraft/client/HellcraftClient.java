package net.thesmallthings.hellcraft.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.client.render.TestRigModel;
import net.thesmallthings.hellcraft.client.render.TestRigRenderer;
import net.thesmallthings.hellcraft.entity.HellEntities;
import net.thesmallthings.hellcraft.net.HudPayload;
import net.thesmallthings.hellcraft.net.VersionPayload;

/** Hellcraft's client side. */
public class HellcraftClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// the server asks which version we have while we join (JoinCheck)
		ClientConfigurationNetworking.registerGlobalReceiver(VersionPayload.TYPE,
				(payload, context) -> context.responseSender().sendPacket(new VersionPayload(HellcraftMod.VERSION)));
		ClientPlayNetworking.registerGlobalReceiver(HudPayload.TYPE, (payload, context) -> BloodHud.set(payload.bloodHearts(), payload.rank()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BloodHud.reset());

		ModelLayerRegistry.registerModelLayer(TestRigModel.LAYER, TestRigModel::createLayer);
		EntityRendererRegistry.register(HellEntities.TEST_RIG, TestRigRenderer::new);
	}
}
