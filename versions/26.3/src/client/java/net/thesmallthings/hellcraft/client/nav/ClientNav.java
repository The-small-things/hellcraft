package net.thesmallthings.hellcraft.client.nav;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.thesmallthings.hellcraft.blood.Guide;
import net.thesmallthings.hellcraft.codex.Codex;
import net.thesmallthings.hellcraft.net.JournalPayload;
import net.thesmallthings.hellcraft.net.MapPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * What the client knows for finding the way: whether this world is the Inferno, the Rests this player has
 * found, the bosses they have beaten (MapPayload) and the Guide's pages (JournalPayload). Everything else
 * (circles, roads, ways down) comes from the same geometry the server builds the terrain from.
 */
public final class ClientNav {
	private ClientNav() {
	}

	private static volatile boolean inferno;
	private static volatile boolean cards;
	private static volatile Set<String> visited = Set.of();
	private static volatile Set<String> beaten = Set.of();
	private static volatile Set<String> codex = Set.of();
	private static volatile List<Guide.Page> pages = List.of();
	private static volatile int spheres;

	public static void map(MapPayload payload) {
		inferno = payload.inferno();
		cards = payload.cards();
		visited = Set.copyOf(payload.visited());
		beaten = Set.copyOf(payload.beaten());
		codex = Set.copyOf(payload.codex());
		spheres = payload.spheres();
	}

	public static void journal(JournalPayload payload) {
		pages = List.copyOf(payload.pages());
	}

	public static void reset() {
		inferno = false;
		cards = false;
		visited = Set.of();
		beaten = Set.of();
		codex = Set.of();
		pages = List.of();
	}

	/** True in the overworld of an Inferno world: the compass, cards and map apply. */
	public static boolean inHell(Minecraft mc) {
		return inferno && mc.level != null && mc.player != null && mc.level.dimension() == Level.OVERWORLD;
	}

	/** In the Forge of Dis: the Nether of an Inferno world (Phase 6), where the compass points to the Great Forge. */
	/** In Paradiso (its own dimension). */
	public static boolean inParadiso(Minecraft mc) {
		return mc.level != null && mc.player != null && mc.level.dimension() == net.thesmallthings.hellcraft.world.Paradiso.LEVEL;
	}

	/** Has this player passed a sphere's trial (MapSync sends them)? */
	public static boolean passed(net.thesmallthings.hellcraft.world.ParadisoGeometry.Sphere s) {
		return (spheres >> s.ordinal() & 1) != 0;
	}

	public static boolean inForge(Minecraft mc) {
		return inferno && mc.level != null && mc.player != null && mc.level.dimension() == Level.NETHER;
	}

	public static boolean inferno() {
		return inferno;
	}

	public static boolean cards() {
		return cards;
	}

	public static boolean found(String restId) {
		return visited.contains(restId);
	}

	public static int foundCount() {
		return visited.size();
	}

	public static boolean beaten(String boss) {
		return beaten.contains(boss);
	}

	/** The Codex entries this player has opened, in the Codex's order. */
	public static List<Codex.Entry> codex() {
		List<Codex.Entry> out = new ArrayList<>();
		for (Codex.Entry e : Codex.ALL) {
			if (codex.contains(e.id())) {
				out.add(e);
			}
		}
		return out;
	}

	public static List<Guide.Page> pages() {
		return pages;
	}
}
