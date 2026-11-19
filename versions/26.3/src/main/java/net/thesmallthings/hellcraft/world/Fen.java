package net.thesmallthings.hellcraft.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.thesmallthings.hellcraft.hazard.guardian.Guardian;

/**
 * Wrath's Fen of Styx (L-V): where its watchtowers stand and where its plank roads wind, shared by the worldgen
 * (WatchtowerFeature, BoardwalkFeature) and the ferry. A watchtower stands on the Fen's outer shore beside each of the
 * four roads, its two beacons on top; light them and the ferryman comes. Between the roads stand eight more (outposts,
 * since 3.5.1), the same towers with the same cache at the foot of the ladder but no beacons: so a pilgrim can find
 * more than one of the Fen's caches (and its Sidestep books) without walking every road.
 */
public final class Fen {
	private Fen() {
	}

	/** The watchtowers stand this far inside the Styx's outer edge, to the right of each road (walking in). */
	private static final double TOWER_INSET = 14;
	private static final double TOWER_SIDE = 11;
	/** Half the tower's width (it is 2 * HALF + 1 square) and its height to the platform. */
	public static final int HALF = 3;
	public static final int HEIGHT = 20;

	/**
	 * Tower k's centre column (x, z): k below towers() by road (east, south, west, north), the beacon towers; from
	 * towers() to allTowers() the outposts between the roads.
	 */
	public static int[] tower(int k) {
		if (k >= towers()) {
			return Outposts.SITES[k - towers()].clone();
		}
		return roadTower(k);
	}

	private static int[] roadTower(int k) {
		double a = InfernoGeometry.ROAD_ANGLES[k];
		return standIn(a, -Math.sin(a) * TOWER_SIDE, Math.cos(a) * TOWER_SIDE);
	}

	/** The tower's column at angle a (offset sideways by side), stepped in from the Fen's outer edge until it all stands in the Fen. */
	private static int[] standIn(double a, double sideX, double sideZ) {
		// the circles' edges wobble: step in until the whole tower stands in the Fen
		double r = InfernoGeometry.outerRadius(Zone.STYX) - TOWER_INSET;
		while (r > InfernoGeometry.outerRadius(Zone.STYX) - 120 && !inFen(Math.cos(a) * r + sideX, Math.sin(a) * r + sideZ)) {
			r -= 2;
		}
		return new int[]{(int) Math.floor(Math.cos(a) * r + sideX), (int) Math.floor(Math.sin(a) * r + sideZ)};
	}

	private static boolean inFen(double x, double z) {
		for (int dx = -HALF - 2; dx <= HALF + 2; dx += HALF + 2) {
			for (int dz = -HALF - 2; dz <= HALF + 2; dz += HALF + 2) {
				if (InfernoGeometry.zoneAt(x + dx, z + dz) != Zone.STYX) {
					return false;
				}
			}
		}
		return true;
	}

	/** The beacon towers, one by each road (their index is the road's, and the ferry's). */
	public static int towers() {
		return InfernoGeometry.ROAD_ANGLES.length;
	}

	/** Every watchtower: the beacon towers, then the outposts. */
	public static int allTowers() {
		return towers() + Outposts.SITES.length;
	}

	/** True for the beacon towers by the roads; the outposts have none. */
	public static boolean hasBeacons(int k) {
		return k < towers();
	}

	/** The angle round Hell that tower k stands at (radians). */
	public static double angle(int k) {
		int[] t = tower(k);
		return Math.atan2(t[1] + 0.5, t[0] + 0.5);
	}

	/**
	 * The outposts: two between each pair of roads, a quarter and three quarters of the way round, each nudged along the
	 * shore until it is clear of the roads, the ways down from Greed, the lairs (and Phlegyas's jetties), the Rests and
	 * the secret ways. Worked out once, from geometry alone.
	 */
	static final class Outposts {
		private Outposts() {
		}

		static final double[] BASE_DEGREES = {22.5, 67.5, 112.5, 157.5, 202.5, 247.5, 292.5, 337.5};
		/** How far either way an outpost may be nudged (degrees), in steps of one. */
		private static final int NUDGE = 10;
		static final int[][] SITES = sites();

		private static int[][] sites() {
			int[][] out = new int[BASE_DEGREES.length][];
			for (int i = 0; i < BASE_DEGREES.length; i++) {
				int[] best = null;
				for (int n = 0; n <= NUDGE && best == null; n++) {
					for (int sign : n == 0 ? new int[]{1} : new int[]{1, -1}) {
						double a = Math.toRadians(BASE_DEGREES[i] + sign * n);
						int[] t = standIn(a, 0, 0);
						if (clear(t)) {
							best = t;
							break;
						}
					}
				}
				out[i] = best != null ? best : standIn(Math.toRadians(BASE_DEGREES[i]), 0, 0);
			}
			return out;
		}

		static boolean clear(int[] t) {
			double x = t[0] + 0.5;
			double z = t[1] + 0.5;
			for (int dx = -HALF - 2; dx <= HALF + 2; dx++) {
				for (int dz = -HALF - 2; dz <= HALF + 2; dz++) {
					if (InfernoGeometry.roadLevel(x + dx, z + dz) > 0 || InfernoGeometry.zoneAt(x + dx, z + dz) != Zone.STYX) {
						return false;
					}
				}
			}
			// Phlegyas's landings (with their jetties out to the shore) and every other lair
			if (InfernoGeometry.nearLair(x, z, LANDING + JETTY + 12)) {
				return false;
			}
			double r = Math.hypot(x, z);
			double a = Math.atan2(z, x);
			for (int k = 0; k < 12; k++) {
				if (InfernoGeometry.angularDistance(a, InfernoGeometry.wayDownAngle(k)) * r < 80) {
					return false;
				}
			}
			if (SecretWays.near(x, z, 16)) {
				return false;
			}
			for (ShrineSites.Site s : ShrineSites.all()) {
				if (Math.hypot(s.x() - x, s.z() - z) < 32) {
					return false;
				}
			}
			return true;
		}
	}

	/** The tower whose footprint (with its platform's overhang) holds this column, or -1. */
	public static int towerAt(int x, int z) {
		for (int k = 0; k < allTowers(); k++) {
			int[] t = tower(k);
			if (Math.abs(x - t[0]) <= HALF + 1 && Math.abs(z - t[1]) <= HALF + 1) {
				return k;
			}
		}
		return -1;
	}

	/** The two beacons on top of tower k, at the platform's corners away from the road, given its platform height. */
	public static BlockPos[] beacons(int k, int platformY) {
		int[] t = tower(k);
		return new BlockPos[]{new BlockPos(t[0] - HALF + 1, platformY + 1, t[1] - HALF + 1), new BlockPos(t[0] + HALF - 1, platformY + 1, t[1] + HALF - 1)};
	}

	/** The tower's door step: the shore, or a block above the water. */
	public static int base(int[] t) {
		return Math.max((int) Math.round(InfernoGeometry.baseHeight(t[0], t[1])) + 1, InfernoGeometry.fluidLevel(t[0], t[1]) + 1);
	}

	/** The height of tower k's platform (its beacons stand on it). */
	public static int platform(int k) {
		return base(tower(k)) + HEIGHT;
	}

	/** True if both of tower k's beacons burn (and the tower is loaded). */
	public static boolean beaconsLit(ServerLevel level, int k) {
		for (BlockPos b : beacons(k, platform(k))) {
			if (!level.isLoaded(b)) {
				return false;
			}
			BlockState state = level.getBlockState(b);
			if (!state.hasProperty(CampfireBlock.LIT) || !state.getValue(CampfireBlock.LIT)) {
				return false;
			}
		}
		return true;
	}

	/** Puts tower k's beacons out (so they can be lit again). */
	public static void douse(ServerLevel level, int k) {
		for (BlockPos b : beacons(k, platform(k))) {
			BlockState state = level.getBlockState(b);
			if (level.isLoaded(b) && state.hasProperty(CampfireBlock.LIT)) {
				level.setBlock(b, state.setValue(CampfireBlock.LIT, false), 3);
			}
		}
	}

	// ------------------------------------------------------------------ Phlegyas's landing

	/**
	 * Phlegyas's landings, one on each diagonal between the roads (his lairs, Guardian; k as the towers, landing k a
	 * quarter turn on from tower k): a ring of planks on the water round the deep pool he rises from, with a jetty
	 * running straight out toward the Fen's outer shore.
	 */
	public static int[] landing(int k) {
		BlockPos c = Guardian.PHLEGYAS.lairColumn(k);
		return new int[]{c.getX(), c.getZ()};
	}

	public static final double POOL = 3.5;
	public static final double LANDING = 21;
	/** How far the jetty runs on past the deck's rail, outward (it stops being built where the ground rises dry). */
	public static final double JETTY = 44;

	public enum Landing {
		NONE,
		/** The deep water he stands in. */
		POOL,
		/** Planks on the water. */
		DECK,
		/** The rail round the deck's edge (posts with lanterns here and there). */
		RAIL,
		/** The jetty out toward the outer shore. */
		JETTY
	}

	public static Landing landingAt(double x, double z) {
		for (int k = 0; k < towers(); k++) {
			Landing part = landingAt(k, x, z);
			if (part != Landing.NONE) {
				return part;
			}
		}
		return Landing.NONE;
	}

	/** Which part of landing k this is: measured across its radius (u) and outward from the pit (v). */
	private static Landing landingAt(int k, double x, double z) {
		int[] c = landing(k);
		double a = Math.atan2(c[1], c[0]);
		double rx = x - c[0] - 0.5;
		double rz = z - c[1] - 0.5;
		double v = rx * Math.cos(a) + rz * Math.sin(a);
		double u = -rx * Math.sin(a) + rz * Math.cos(a);
		double d = Math.hypot(rx, rz);
		if (d < POOL) {
			return Landing.POOL;
		}
		// the jetty runs from the deck straight outward, toward the circle's outer shore
		boolean jetty = Math.abs(u) <= 2 && v > 0 && v < LANDING + JETTY;
		if (d < LANDING - 1) {
			return Landing.DECK;
		}
		if (d < LANDING) {
			return jetty ? Landing.DECK : Landing.RAIL;
		}
		return jetty ? Landing.JETTY : Landing.NONE;
	}

	/** True where a plank road crosses the Fen's water: thin winding lines along a noise's contours. */
	public static boolean boardwalk(int x, int z) {
		double n = InfernoGeometry.fbm(x / 46.0, z / 46.0, 5309);
		double m = InfernoGeometry.fbm(x / 61.0, z / 61.0, 7411);
		return Math.abs(n) < 0.045 || Math.abs(m - 0.25) < 0.035;
	}
}
