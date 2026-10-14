package net.thesmallthings.hellcraft.world;


import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Where the Virgil's Rests stand: beside each Pilgrim Road where it comes down into a new circle, on the
 * first dry ground past the river banks, {@link #ROAD_OFFSET} blocks to the road's side so they never block
 * it. Pure geometry (no world access), so the list is the same on every server and can be worked out offline.
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

	/** Each circle's way in: the zone the Rest stands in, and its chest tier. */
	private record Entry(Zone into, String title, int tier) {
	}

	private static final Entry[] ENTRIES = {
			new Entry(Zone.LIMBO, "Circle I — Limbo", 0),
			new Entry(Zone.LUST, "Circle II — Lust", 0),
			new Entry(Zone.GLUTTONY, "Circle III — Gluttony", 0),
			new Entry(Zone.GREED, "Circle IV — Greed", 0),
			new Entry(Zone.STYX, "Circle V — Wrath", 1),
			new Entry(Zone.HERESY, "Circle VI — Heresy", 1),
			new Entry(Zone.WOOD_OF_SUICIDES, "The Wood of Suicides", 2),
			new Entry(Zone.BURNING_SANDS, "The Burning Sands", 2),
			new Entry(Zone.MALEBOLGE, "Circle VIII — Malebolge", 2),
			new Entry(Zone.WELL_OF_GIANTS, "The Well of Giants", 2),
	};

	/** How far beside the road (to its left, walking in) a Rest's altar stands. */
	public static final int ROAD_OFFSET = 10;
	/** Rests keep this far from a guardian's lair (they slide inward along the road). */
	private static final double LAIR_CLEARANCE = 56.0;
	/** The guardians' lairs beside the east road (x, z). */
	static final int[][] LAIRS = InfernoGeometry.LAIRS;
	/** The four roads, in the order of ROAD_ANGLES. */
	private static final String[] ROADS = {"east", "south", "west", "north"};

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
			double outer = InfernoGeometry.outerRadius(e.into);
			for (int k = 0; k < InfernoGeometry.ROAD_ANGLES.length; k++) {
				double a = InfernoGeometry.ROAD_ANGLES[k];
				// to the left of the road, walking toward the centre
				double sideX = Math.sin(a) * ROAD_OFFSET;
				double sideZ = -Math.cos(a) * ROAD_OFFSET;
				int[] spot = null;
				// walk in from the foot of the ramp to the first dry ground inside the circle, clear of the lairs
				for (double r = outer - 10; r > outer - 120; r -= 3) {
					double x = Math.cos(a) * r + sideX;
					double z = Math.sin(a) * r + sideZ;
					if (InfernoGeometry.zoneAt(x, z) == e.into && dry(x, z) && clearOfLairs(x, z)) {
						spot = new int[]{(int) Math.round(x), (int) Math.round(z)};
						break;
					}
				}
				boolean dry = spot != null;
				if (!dry) {
					double r = outer - 20;
					spot = new int[]{(int) Math.round(Math.cos(a) * r + sideX), (int) Math.round(Math.sin(a) * r + sideZ)};
				}
				String id = e.into.name().toLowerCase(Locale.ROOT) + "_" + ROADS[k];
				out.add(new Site(id, e.into, e.title, e.tier, spot[0], spot[1], dry));
			}
		}
		return List.copyOf(out);
	}

	private static boolean dry(double x, double z) {
		return InfernoGeometry.fluidAt(x, z) == InfernoGeometry.Fluid.NONE
				&& InfernoGeometry.fluidAt(x + 6, z + 6) == InfernoGeometry.Fluid.NONE
				&& InfernoGeometry.fluidAt(x - 6, z - 6) == InfernoGeometry.Fluid.NONE;
	}

	private static boolean clearOfLairs(double x, double z) {
		for (int[] lair : LAIRS) {
			double dx = x - lair[0];
			double dz = z - lair[1];
			if (dx * dx + dz * dz < LAIR_CLEARANCE * LAIR_CLEARANCE) {
				return false;
			}
		}
		return true;
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
