package net.thesmallthings.hellcraft.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Where the Virgil's Rests stand: at the foot of every ramp that leads down into a new circle, on the
 * first dry ground past the river banks. Pure geometry (no world access), so the list is the same on
 * every server and can be worked out offline.
 */
public final class ShrineSites {
	private ShrineSites() {
	}

	/**
	 * A Rest: {@code id} is unique ("limbo_1"), {@code tier} picks its supply chest (0 upper, 1 middle,
	 * 2 lower), {@code dry} is false when no dry ground was found and the Rest stands on a raised platform.
	 */
	public record Site(String id, Zone zone, String title, int tier, int x, int z, boolean dry) {
	}

	/** Each circle's way in: the zone the Rest stands in, the band whose ramps lead down to it, and its chest tier. */
	private record Entry(Zone into, Zone rampsOf, String title, int tier) {
	}

	private static final Entry[] ENTRIES = {
			new Entry(Zone.LIMBO, Zone.ACHERON, "Circle I — Limbo", 0),
			new Entry(Zone.LUST, Zone.LIMBO, "Circle II — Lust", 0),
			new Entry(Zone.GLUTTONY, Zone.LUST, "Circle III — Gluttony", 0),
			new Entry(Zone.GREED, Zone.GLUTTONY, "Circle IV — Greed", 0),
			new Entry(Zone.STYX, Zone.GREED, "Circle V — Wrath", 1),
			new Entry(Zone.HERESY, Zone.WALLS_OF_DIS, "Circle VI — Heresy", 1),
			new Entry(Zone.WOOD_OF_SUICIDES, Zone.PHLEGETHON, "The Wood of Suicides", 2),
			// the Wood's inner edge is a gentle slope all round (no ramps): follow the Phlegethon's ramps on
			new Entry(Zone.BURNING_SANDS, Zone.PHLEGETHON, "The Burning Sands", 2),
			new Entry(Zone.MALEBOLGE, Zone.BURNING_SANDS, "Circle VIII — Malebolge", 2),
	};

	/** Rests keep this far from a guardian's lair (they slide along the ring). */
	private static final double LAIR_CLEARANCE = 56.0;
	/** Lair radii along +X (see Guardian). */
	private static final int[] LAIRS = {4050, 3575, 3125, 1600, 1480};

	private static List<Site> sites;

	public static synchronized List<Site> all() {
		if (sites == null) {
			sites = compute();
		}
		return sites;
	}

	private static List<Site> compute() {
		List<Site> out = new ArrayList<>();
		for (Entry e : ENTRIES) {
			double[] angles = InfernoGeometry.rampAngles(e.rampsOf);
			double outer = InfernoGeometry.outerRadius(e.into);
			for (int k = 0; k < angles.length; k++) {
				double a = angles[k];
				int[] spot = null;
				// scan inward from the foot of the ramp for dry ground inside the circle
				for (double r = outer - 10; r > outer - 110; r -= 3) {
					double x = Math.cos(a) * r;
					double z = Math.sin(a) * r;
					if (InfernoGeometry.zoneAt(x, z) == e.into && InfernoGeometry.fluidAt(x, z) == InfernoGeometry.Fluid.NONE
							&& InfernoGeometry.fluidAt(x + 6, z + 6) == InfernoGeometry.Fluid.NONE
							&& InfernoGeometry.fluidAt(x - 6, z - 6) == InfernoGeometry.Fluid.NONE) {
						spot = clearOfLairs(a, r);
						break;
					}
				}
				boolean dry = spot != null;
				if (!dry) {
					spot = clearOfLairs(a, outer - 20);
				}
				String id = e.into.name().toLowerCase(Locale.ROOT) + "_" + (k + 1);
				out.add(new Site(id, e.into, e.title, e.tier, spot[0], spot[1], dry));
			}
		}
		return List.copyOf(out);
	}

	private static int[] clearOfLairs(double a, double r) {
		for (int tries = 0; tries < 8; tries++) {
			double x = Math.cos(a) * r;
			double z = Math.sin(a) * r;
			boolean clear = true;
			for (int lair : LAIRS) {
				double dx = x - lair;
				if (dx * dx + z * z < LAIR_CLEARANCE * LAIR_CLEARANCE) {
					clear = false;
					break;
				}
			}
			if (clear) {
				return new int[]{(int) Math.round(x), (int) Math.round(z)};
			}
			a += LAIR_CLEARANCE / r;
		}
		return new int[]{(int) Math.round(Math.cos(a) * r), (int) Math.round(Math.sin(a) * r)};
	}

	/** The Rest with this id, or null. */
	public static Site byId(String id) {
		for (Site s : all()) {
			if (s.id().equals(id)) {
				return s;
			}
		}
		return null;
	}

	/** The Rest nearest to a point, or null if there are none. */
	public static Site nearest(double x, double z) {
		Site best = null;
		double bestD = Double.MAX_VALUE;
		for (Site s : all()) {
			double d = (s.x() - x) * (s.x() - x) + (s.z() - z) * (s.z() - z);
			if (d < bestD) {
				bestD = d;
				best = s;
			}
		}
		return best;
	}

	/** Prints the sites (for checking them offline: java ... ShrineSites). */
	public static void main(String[] args) {
		for (Site s : all()) {
			System.out.println(s);
		}
	}
}
