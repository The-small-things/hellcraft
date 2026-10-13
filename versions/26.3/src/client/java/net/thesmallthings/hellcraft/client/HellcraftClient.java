package net.thesmallthings.hellcraft.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.net.VersionPayload;

/** Hellcraft's client side. */
public class HellcraftClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// the server asks which version we have while we join (JoinCheck)
		ClientConfigurationNetworking.registerGlobalReceiver(VersionPayload.TYPE,
				(payload, context) -> context.responseSender().sendPacket(new VersionPayload(HellcraftMod.VERSION)));
	}
}
