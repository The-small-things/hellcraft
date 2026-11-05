package net.thesmallthings.hellcraft.client.layer;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.thesmallthings.hellcraft.net.GlowPayload;

import java.util.Set;

/**
 * A faint glow (hazard/Glow): the server says which entities have one (the onlookers, the Keys of Peter's holder), and the
 * renderer lights them as if a dim lamp stood by them (mixin/EntityRendererMixin), so they show against Hell's dark
 * without an outline.
 */
public final class Glow {
	private Glow() {
	}

	/** The block light a glowing entity is drawn with, at least (of 15): a faint glow, not a lantern. */
	public static final int LIGHT = 9;

	private static volatile Set<Integer> ids = Set.of();

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(GlowPayload.TYPE, (payload, context) -> ids = Set.copyOf(payload.ids()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ids = Set.of());
	}

	public static boolean glows(int entityId) {
		return ids.contains(entityId);
	}
}
