package net.thesmallthings.hellcraft.client.nav;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.thesmallthings.hellcraft.net.SecretsPayload;
import net.thesmallthings.hellcraft.world.SecretWays;

import java.util.Set;

/** The secret ways down this player has found (SecretsPayload), for the Map of Hell and the Journal. */
public final class SecretMarks {
	private SecretMarks() {
	}

	private static volatile Set<String> found = Set.of();

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(SecretsPayload.TYPE, (payload, context) -> found = Set.copyOf(payload.found()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> found = Set.of());
	}

	public static boolean found(String id) {
		return found.contains(id);
	}

	/** How many of the secret ways this player has found. */
	public static int count() {
		int n = 0;
		for (SecretWays.Site s : SecretWays.all()) {
			if (found.contains(s.id())) {
				n++;
			}
		}
		return n;
	}
}
