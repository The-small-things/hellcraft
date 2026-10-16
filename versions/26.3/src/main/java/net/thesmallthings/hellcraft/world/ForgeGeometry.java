package net.thesmallthings.hellcraft.world;

/**
 * The shape of the Forge of Dis (the Nether of an Inferno world, Phase 6): one source for the terrain
 * ({@link ForgeDensity}), the biomes ({@link ForgeBiomeSource}), the features and the compass, like
 * {@link InfernoGeometry} is for the overworld. No Minecraft classes, so it can be tested and previewed offline.
 *
 * <pre>
 *   the Great Forge   a flat-topped mesa (r &lt; 40) in a vast dome; Vulcan's smithy stands on it (GreatForge)
 *   the Slag Sea      a ring of molten slag round the mesa, crossed on its cooling crust (SlagCrustBlock)
 *   then four regions, one to each quarter (their borders wander):
 *     north  the Foundry Halls       tall halls, rivers of lava, pillars hung with chains
 *     east   the Chainlands          an abyss over the lava sea, islands of rock hung on giant chains
 *     south  the Ash Wastes          dunes of ash under a low roof
 *     west   the Cyclops Quarries    terraced pits cut into the rock, the cyclopes' camps
 * </pre>
 *
 * The Nether is 1:8, so the overworld's border (3,500) puts the Nether's at {@link #BORDER} blocks.
 */
public final class ForgeGeometry {
	private ForgeGeometry() {
	}

	/** The world border, scaled down by the Nether's 1:8. */
	public static final double BORDER = InfernoGeometry.BORDER_RADIUS / 8.0;
	/** The lava sea: every open space below this fills with lava. */
	public static final int LAVA = 32;
	/** The Great Forge's mesa: its flat top is the floor Vulcan's hall stands on (GreatForge.FLOOR_Y). */
	public static final double MESA_RADIUS = 40.0;
	public static final int MESA_TOP = 70;
	/** Where the Slag Sea ends and the four regions begin (it wanders a little round this). */
	public static final double SEA_OUTER = 112.0;

	public enum Region {
		GREAT_FORGE("great_forge", "The Great Forge"),
		SLAG_SEA("slag_sea", "The Slag Sea"),
		FOUNDRY_HALLS("foundry_halls", "The Foundry Halls"),
		CHAINLANDS("chainlands", "The Chainlands"),
		ASH_WASTES("ash_wastes", "The Ash Wastes"),
		CYCLOPS_QUARRIES("cyclops_quarries", "The Cyclops Quarries");

		private final String id;
		private final String title;

		Region(String id, String title) {
			this.id = id;
			this.title = title;
		}

		public String id() {
			return id;
		}

		public String title() {
			return title;
		}
	}

	/** The four outer regions in angle order from east (+X), going through south (+Z). */
	private static final Region[] QUARTERS = {Region.CHAINLANDS, Region.ASH_WASTES, Region.CYCLOPS_QUARRIES, Region.FOUNDRY_HALLS};
	/** How wide the blend between two outer regions is, in radians of (wandering) angle. */
	private static final double QUARTER_BLEND = 0.14;

	// ------------------------------------------------------------------ where

	/** The region a column belongs to (the one with the most weight). */
	public static Region regionAt(double x, double z) {
		double[] w = weights(x, z);
		int best = 0;
		for (int i = 1; i < w.length; i++) {
			if (w[i] > w[best]) {
				best = i;
			}
		}
		return Region.values()[best];
	}

	/** Each region's share of a column, summing to 1: regions blend into each other over a few blocks. */
	public static double[] weights(double x, double z) {
		double[] w = new double[Region.values().length];
		double r = Math.sqrt(x * x + z * z);
		double mesa = 1.0 - InfernoGeometry.smoothstep((r - MESA_RADIUS) / 6.0);
		double outer = InfernoGeometry.smoothstep((r - seaOuter(x, z)) / 18.0 + 0.5);
		w[Region.GREAT_FORGE.ordinal()] = mesa;
		w[Region.SLAG_SEA.ordinal()] = (1.0 - mesa) * (1.0 - outer);
		if (outer > 0) {
			double a = wanderingAngle(x, z);
			double sum = 0;
			double[] q = new double[4];
			for (int k = 0; k < 4; k++) {
				double d = Math.abs(angleDiff(a, k * Math.PI / 2));
				q[k] = InfernoGeometry.smoothstep((Math.PI / 4 - d) / QUARTER_BLEND + 0.5);
				sum += q[k];
			}
			for (int k = 0; k < 4; k++) {
				w[QUARTERS[k].ordinal()] = (1.0 - mesa) * outer * q[k] / Math.max(1.0e-9, sum);
			}
		}
		return w;
	}

	/** The Slag Sea's outer shore, wandering 10 blocks either way. */
	static double seaOuter(double x, double z) {
		double a = Math.atan2(z, x);
		return SEA_OUTER + 10.0 * InfernoGeometry.fbm(Math.cos(a) * 3.0 + 40.0, Math.sin(a) * 3.0 + 40.0, 71);
	}

	/** The column's angle, bent by noise so the quarters' borders wander instead of running straight out. */
	static double wanderingAngle(double x, double z) {
		return Math.atan2(z, x) + 0.35 * InfernoGeometry.fbm(x / 90.0, z / 90.0, 72);
	}

	static double angleDiff(double a, double b) {
		double d = (a - b) % (2 * Math.PI);
		if (d > Math.PI) {
			d -= 2 * Math.PI;
		} else if (d < -Math.PI) {
			d += 2 * Math.PI;
		}
		return d;
	}

	// ------------------------------------------------------------------ the terrain

	/**
	 * The column's terrain: {floor, ceiling, roughness}. Below the floor and above the ceiling is rock (the floor is
	 * the y of the first open block); roughness (in density units, 8 blocks each) scales the Nether's own 3D noise
	 * round both surfaces.
	 */
	public static double[] column(double x, double z) {
		double[] w = weights(x, z);
		double floor = 0;
		double ceiling = 0;
		double rough = 0;
		for (Region region : Region.values()) {
			double weight = w[region.ordinal()];
			if (weight <= 1.0e-6) {
				continue;
			}
			double[] c = regionColumn(region, x, z);
			floor += weight * c[0];
			ceiling += weight * c[1];
			rough += weight * c[2];
		}
		return new double[]{floor, ceiling, rough};
	}

	static double[] regionColumn(Region region, double x, double z) {
		double n = InfernoGeometry.fbm(x / 50.0, z / 50.0, 73);
		double fine = InfernoGeometry.fbm(x / 13.0, z / 13.0, 74);
		return switch (region) {
			// the mesa's top is dead flat: the smithy and its yard stand on it
			case GREAT_FORGE -> new double[]{MESA_TOP, 118 + 4 * n, 0.05};
			case SLAG_SEA -> {
				// mostly molten, with a few rock islets
				double islet = InfernoGeometry.fbm(x / 22.0, z / 22.0, 75);
				double floor = islet > 0.5 ? LAVA + 2 + 10 * (islet - 0.5) : 24 + 3 * fine;
				yield new double[]{floor, 100 + 10 * n, 0.3};
			}
			case FOUNDRY_HALLS -> {
				// tall halls, cut by rivers of lava where a ridge noise is near zero
				double river = Math.abs(InfernoGeometry.fbm(x / 70.0, z / 70.0, 76));
				double bed = InfernoGeometry.smoothstep((0.09 - river) / 0.05);
				yield new double[]{InfernoGeometry.lerp(44 + 4 * n, 27, bed), 116 + 4 * fine, 0.35};
			}
			// an abyss: its floor is under the lava sea, and its rock hangs in islands (island())
			case CHAINLANDS -> new double[]{20, 121, 0.25};
			case ASH_WASTES -> new double[]{50 + 7 * n + 3 * fine, 104 + 8 * n, 0.45};
			case CYCLOPS_QUARRIES -> {
				// terraces seven blocks high, sunk deeper in the quarry pits; ramps wind through them where a ridge
				// noise is near zero, smooth enough to walk up
				double base = 50 + 12 * n - 14 * quarryPit(x, z);
				double ramp = InfernoGeometry.smoothstep((0.07 - Math.abs(InfernoGeometry.fbm(x / 45.0, z / 45.0, 95))) / 0.04);
				yield new double[]{InfernoGeometry.lerp(Math.floor(base / 7.0) * 7.0 + 1.0, base, ramp), 112 + 4 * n, 0.1};
			}
		};
	}

	/** How deep in a quarry pit a column is: 0 outside, 1 at a pit's bottom (pits are about 50 blocks across). */
	static double quarryPit(double x, double z) {
		double cell = 72.0;
		int cx = (int) Math.floor(x / cell);
		int cz = (int) Math.floor(z / cell);
		double best = 0;
		for (int i = cx - 1; i <= cx + 1; i++) {
			for (int j = cz - 1; j <= cz + 1; j++) {
				double px = (i + 0.25 + 0.5 * InfernoGeometry.hash01(i, j, 77)) * cell;
				double pz = (j + 0.25 + 0.5 * InfernoGeometry.hash01(i, j, 78)) * cell;
				double radius = 20 + 8 * InfernoGeometry.hash01(i, j, 79);
				double d = Math.hypot(x - px, z - pz) / radius;
				best = Math.max(best, 1.0 - InfernoGeometry.smoothstep(d));
			}
		}
		return best;
	}

	/** The Chainlands' islands: a cell grid, one island to a cell, each hung on its chains (ChainedIslandFeature). */
	public static final double ISLAND_CELL = 44.0;

	/** The island of cell (i, j): {x, z, top y, radius}, or null (about a third of the cells are empty). */
	public static double[] island(int i, int j) {
		if (InfernoGeometry.hash01(i, j, 84) < 0.35) {
			return null;
		}
		double x = (i + 0.15 + 0.7 * InfernoGeometry.hash01(i, j, 80)) * ISLAND_CELL;
		double z = (j + 0.15 + 0.7 * InfernoGeometry.hash01(i, j, 81)) * ISLAND_CELL;
		double top = 62 + 22 * InfernoGeometry.hash01(i, j, 82);
		double radius = 9 + 7 * InfernoGeometry.hash01(i, j, 83);
		return new double[]{x, z, Math.floor(top), radius};
	}

	/**
	 * How solid the Chainlands' islands make a point: positive inside one (in density units). An island is a flat
	 * top over a rough cone of rock that tapers to a point 2.5 radii below.
	 */
	static double islands(double x, double y, double z) {
		int ci = (int) Math.floor(x / ISLAND_CELL);
		int cj = (int) Math.floor(z / ISLAND_CELL);
		double best = -1.0;
		for (int i = ci - 1; i <= ci + 1; i++) {
			for (int j = cj - 1; j <= cj + 1; j++) {
				double[] is = island(i, j);
				if (is == null) {
					continue;
				}
				double d = Math.hypot(x - is[0], z - is[1]) / is[3];
				if (d >= 1.2) {
					continue;
				}
				double depth = 2.5 * is[3] * (1.0 - d);
				double inside = Math.min(Math.min(is[2] - y, y - (is[2] - depth)), (1.0 - d) * is[3]);
				best = Math.max(best, inside / 8.0);
			}
		}
		return best;
	}

	/**
	 * The terrain's density before the Nether's noise: positive is rock. Rock below the floor and above the ceiling,
	 * open between, and in the Chainlands the islands.
	 */
	public static double shape(double x, double y, double z, double[] column) {
		double open = Math.max(column[0] - y, y - column[1]) / 8.0;
		double chain = weights(x, z)[Region.CHAINLANDS.ordinal()];
		if (chain > 0.05) {
			open = Math.max(open, islands(x, y, z) * chain - (1.0 - chain) * 0.5);
		}
		return open;
	}

	/** Whether this block is rock, by the shape alone (the noise roughens it in game): for tests and previews. */
	public static boolean solid(double x, double y, double z) {
		return shape(x, y, z, column(x, z)) > 0;
	}

	/** The top of the ground under an open column at this height, by the shape alone (for previews and tests). */
	public static int groundBelow(int x, int y, int z) {
		double[] c = column(x, z);
		for (int yy = y; yy > 0; yy--) {
			if (shape(x, yy, z, c) > 0) {
				return yy + 1;
			}
		}
		return 0;
	}
}
