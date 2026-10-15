package net.thesmallthings.hellcraft.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Wrath's Fen of Styx (L-V): where its watchtowers stand and where its plank roads wind, shared by the worldgen
 * (WatchtowerFeature, BoardwalkFeature) and the ferry. A watchtower stands on the Fen's outer shore beside each of the
 * four roads, its two beacons on top; light them and the ferryman comes.
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

	/** Tower k's centre column (x, z), k by road (east, south, west, north). */
	public static int[] tower(int k) {
		double a = InfernoGeometry.ROAD_ANGLES[k];
		double sideX = -Math.sin(a) * TOWER_SIDE;
		double sideZ = Math.cos(a) * TOWER_SIDE;
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

	public static int towers() {
		return InfernoGeometry.ROAD_ANGLES.length;
	}

	/** The tower whose footprint (with its platform's overhang) holds this column, or -1. */
	public static int towerAt(int x, int z) {
		for (int k = 0; k < towers(); k++) {
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

	/** Phlegyas's landing: a ring of planks on the water round the deep pool he rises from (his lair, Guardian). */
	public static final int LANDING_X = 1605;
	public static final int LANDING_Z = 40;
	public static final double POOL = 3.5;
	public static final double LANDING = 21;

	public enum Landing {
		NONE,
		/** The deep water he stands in. */
		POOL,
		/** Planks on the water. */
		DECK,
		/** The rail round the deck's edge (posts with lanterns here and there). */
		RAIL,
		/** The jetty out from the east road. */
		JETTY
	}

	public static Landing landingAt(double x, double z) {
		double d = Math.hypot(x - LANDING_X - 0.5, z - LANDING_Z - 0.5);
		if (d < POOL) {
			return Landing.POOL;
		}
		boolean jetty = Math.abs(x - LANDING_X - 0.5) <= 2 && z >= 3 && z < LANDING_Z;
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
