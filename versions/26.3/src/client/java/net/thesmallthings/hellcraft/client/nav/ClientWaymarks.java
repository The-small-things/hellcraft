package net.thesmallthings.hellcraft.client.nav;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.thesmallthings.hellcraft.net.WaymarkEditPayload;
import net.thesmallthings.hellcraft.net.WaymarksPayload;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The waymarks this player can see (world/Waymarks sends them): their own and everyone's shared ones. One of them can be
 * followed: the compass then points to it (CompassHud). Which one is followed stays on this computer.
 */
public final class ClientWaymarks {
	private ClientWaymarks() {
	}

	/** The colours a waymark can be (WaymarkEditPayload.colour indexes these). */
	public static final int[] COLOURS = {0xFFE04040, 0xFFF09030, 0xFFF0D040, 0xFF60D060, 0xFF40C8E0, 0xFF5070F0, 0xFFC060E0, 0xFFF0F0F0};
	public static final String[] COLOUR_NAMES = {"Red", "Orange", "Yellow", "Green", "Cyan", "Blue", "Purple", "White"};

	private static volatile List<WaymarksPayload.Entry> marks = List.of();
	private static int followed = -1;

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(WaymarksPayload.TYPE, (payload, context) -> {
			marks = List.copyOf(payload.marks());
			if (followed >= 0 && byId(followed) == null) {
				followed = -1;
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			marks = List.of();
			followed = -1;
		});
	}

	public static List<WaymarksPayload.Entry> all() {
		return marks;
	}

	public static @Nullable WaymarksPayload.Entry byId(int id) {
		for (WaymarksPayload.Entry e : marks) {
			if (e.id() == id) {
				return e;
			}
		}
		return null;
	}

	/** The waymark the compass points to, or null. */
	public static @Nullable WaymarksPayload.Entry followed() {
		return followed < 0 ? null : byId(followed);
	}

	public static void follow(int id) {
		followed = followed == id ? -1 : id;
	}

	public static boolean following(int id) {
		return followed == id;
	}

	public static int colour(WaymarksPayload.Entry e) {
		return COLOURS[Math.floorMod(e.colour(), COLOURS.length)];
	}

	/** Places a new waymark (id 0) or changes one of this player's own. */
	public static void save(int id, String name, int x, int z, int colour, boolean shared) {
		ClientPlayNetworking.send(new WaymarkEditPayload(id, name, x, z, colour, shared, false));
	}

	public static void remove(int id) {
		if (followed == id) {
			followed = -1;
		}
		ClientPlayNetworking.send(new WaymarkEditPayload(id, "", 0, 0, 0, false, true));
	}

	public static boolean canSend() {
		return ClientPlayNetworking.canSend(WaymarkEditPayload.TYPE);
	}
}
