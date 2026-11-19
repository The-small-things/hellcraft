package net.thesmallthings.hellcraft.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.thesmallthings.hellcraft.hazard.guardian.Guardian;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrath's Fen of Styx (L-V): where its watchtowers stand and where its plank roads wind, shared by the worldgen
 * (WatchtowerFeature, BoardwalkFeature) and the ferry. A watchtower stands on the Fen's outer shore beside each of the
 * four roads, its two beacons on top; light them and the ferryman comes. Others stand here and there across the Fen by
 * chance (outposts, Outposts: a rare-ish structure, about two dozen in a world, placed by the world seed), the same
 * towers with the same cache at the foot of the ladder but no beacons: so a pilgrim exploring the Fen finds more than
 * one of its caches (and its Sidestep books).
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

	/** Beacon tower k's centre column (x, z), k by road (east, south, west, north). */
	public static int[] tower(int k) {
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

	/**
	 * The outposts: watchtowers without beacons, scattered across the Fen by chance. The Fen is cut into cells CELL
	 * across; the world seed gives each cell one chance in ONE_IN of an outpost, at a spot inside it at least MARGIN from
	 * its edges (so two never crowd each other), and the spot is kept only where the whole tower stands in the Fen, clear
	 * of the roads, the ways down from Greed, the lairs (and Phlegyas's jetties), the Rests, the secret ways and the
	 * beacon towers. Worked out from the seed and the geometry alone, so every chunk agrees on where they are.
	 */
	public static final class Outposts {
		private Outposts() {
		}

		static final int CELL = 128;
		static final int ONE_IN = 12;
		static final int MARGIN = 20;

		/** The outpost in cell (cx, cz) of a world with this seed, as its centre column {x, z}, or null. */
		@Nullable
		public static int[] inCell(long seed, int cx, int cz) {
			long h = hash(seed, cx, cz);
			if (Math.floorMod(h, ONE_IN) != 0) {
				return null;
			}
			int span = CELL - 2 * MARGIN;
			int[] t = {cx * CELL + MARGIN + (int) Math.floorMod(h >>> 12, span), cz * CELL + MARGIN + (int) Math.floorMod(h >>> 32, span)};
			if (InfernoGeometry.zoneAt(t[0] + 0.5, t[1] + 0.5) != Zone.STYX || !clear(t)) {
				return null;
			}
			return t;
		}

		/** Every outpost whose centre lies in the box from (x0, z0) to (x1, z1), for a world with this seed. */
		public static List<int[]> in(long seed, int x0, int z0, int x1, int z1) {
			List<int[]> out = new ArrayList<>();
			for (int cx = Math.floorDiv(x0, CELL); cx <= Math.floorDiv(x1, CELL); cx++) {
				for (int cz = Math.floorDiv(z0, CELL); cz <= Math.floorDiv(z1, CELL); cz++) {
					int[] t = inCell(seed, cx, cz);
					if (t != null && t[0] >= x0 && t[0] <= x1 && t[1] >= z0 && t[1] <= z1) {
						out.add(t);
					}
				}
			}
			return out;
		}

		private static long hash(long seed, int cx, int cz) {
			long h = seed ^ cx * 0x9E3779B97F4A7C15L ^ cz * 0xC2B2AE3D27D4EB4FL ^ 0x7A11E5L;
			h ^= h >>> 31;
			h *= 0xBF58476D1CE4E5B9L;
			h ^= h >>> 29;
			h *= 0x94D049BB133111EBL;
			return h ^ (h >>> 32);
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
			for (int k = 0; k < towers(); k++) {
				int[] t2 = tower(k);
				if (Math.hypot(t2[0] - x, t2[1] - z) < 64) {
					return false;
				}
			}
			for (ShrineSites.Site s : ShrineSites.all()) {
				if (Math.hypot(s.x() - x, s.z() - z) < 32) {
					return false;
				}
			}
			return true;
		}
	}

	/** The beacon tower whose footprint (with its platform's overhang) holds this column, or -1. */
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
