package net.thesmallthings.hellcraft.world;


import java.util.ArrayList;
import java.util.List;

/**
 * Heresy's rule (L-VI): the open tombs of the heretics blast fire in turn. Each tomb stands on a site worked out
 * here (one in most cells of a grid over the Iron City, never on a road, a cliff, a Rest or a lair), so worldgen
 * (BurningTombFeature) and the server (LayerRules) agree on where they are. Tombs fire in diagonal waves: a tomb's
 * {@link #slot} says which quarter of the rhythm is its own, so a gap always opens a moment after a blast.
 */
public final class Tombs {
	private Tombs() {
	}

	/** Tombs sit on a grid of cells this many blocks across, one in most cells. */
	public static final int CELL = 13;
	/** A tomb is LENGTH blocks long and WIDTH wide (walls included). */
	public static final int LENGTH = 5;
	public static final int WIDTH = 3;
	/** One full rhythm, in ticks: a warning, a blast, then quiet while the other slots take their turn. */
	public static final int PERIOD = 120;
	public static final int SLOTS = 4;
	public static final int WARN = 30;
	public static final int BLAST = 30;
	/** How far from a tomb's middle its blast reaches (blocks, sideways). */
	public static final double REACH = 3.2;

	/** A tomb: its corner (smallest x and z), whether it runs along X, and its slot in the rhythm. */
	public record Tomb(int x, int z, boolean alongX, int slot) {
		public double centreX() {
			return x + (alongX ? LENGTH : WIDTH) / 2.0;
		}

		public double centreZ() {
			return z + (alongX ? WIDTH : LENGTH) / 2.0;
		}

		/** Where in the rhythm this tomb is at this game time: 0..PERIOD-1, warning first, then the blast. */
		public int phase(long gameTime) {
			return (int) Math.floorMod(gameTime - (long) slot * (PERIOD / SLOTS), (long) PERIOD);
		}

		public boolean warning(long gameTime) {
			return phase(gameTime) < WARN;
		}

		public boolean blasting(long gameTime) {
			int p = phase(gameTime);
			return p >= WARN && p < WARN + BLAST;
		}
	}

	/** The tomb in the cell holding (x, z), or null if the cell has none. */
	public static Tomb inCell(int x, int z) {
		int cx = Math.floorDiv(x, CELL);
		int cz = Math.floorDiv(z, CELL);
		long h = hash(cx, cz);
		if (Math.floorMod(h, 10) < 3) {
			return null;
		}
		boolean alongX = ((h >> 4) & 1) == 0;
		int spanX = alongX ? LENGTH : WIDTH;
		int spanZ = alongX ? WIDTH : LENGTH;
		int tx = cx * CELL + 1 + (int) Math.floorMod(h >> 8, CELL - spanX - 1);
		int tz = cz * CELL + 1 + (int) Math.floorMod(h >> 16, CELL - spanZ - 1);
		if (!allowed(tx, tz, spanX, spanZ)) {
			return null;
		}
		// diagonal waves sweep across the city
		return new Tomb(tx, tz, alongX, Math.floorMod(cx + cz, SLOTS));
	}

	private static boolean allowed(int x, int z, int spanX, int spanZ) {
		for (int[] c : new int[][]{{x, z}, {x + spanX, z}, {x, z + spanZ}, {x + spanX, z + spanZ}}) {
			if (InfernoGeometry.zoneAt(c[0], c[1]) != Zone.HERESY) {
				return false;
			}
		}
		double mx = x + spanX / 2.0;
		double mz = z + spanZ / 2.0;
		double re = InfernoGeometry.effectiveRadius(mx, mz);
		// not under the Walls of Dis, nor on the cliff down to the river of blood
		if (re > InfernoGeometry.outerRadius(Zone.HERESY) - 14 || re < InfernoGeometry.outerRadius(Zone.PHLEGETHON) + 16) {
			return false;
		}
		for (int dx = -6; dx <= 6; dx += 3) {
			for (int dz = -6; dz <= 6; dz += 3) {
				if (InfernoGeometry.roadFactor(mx + dx, mz + dz) > 0) {
					return false;
				}
			}
		}
		for (int[] lair : InfernoGeometry.LAIRS) {
			if (Math.hypot(mx - lair[0], mz - lair[1]) < 44) {
				return false;
			}
		}
		for (ShrineSites.Site s : ShrineSites.all()) {
			if (Math.hypot(mx - s.x(), mz - s.z()) < 24) {
				return false;
			}
		}
		return true;
	}

	/** Every tomb with a corner in [x0, x1] x [z0, z1]. */
	public static List<Tomb> within(int x0, int z0, int x1, int z1) {
		List<Tomb> out = new ArrayList<>();
		for (int cx = Math.floorDiv(x0, CELL) - 1; cx <= Math.floorDiv(x1, CELL); cx++) {
			for (int cz = Math.floorDiv(z0, CELL) - 1; cz <= Math.floorDiv(z1, CELL); cz++) {
				Tomb t = inCell(cx * CELL, cz * CELL);
				if (t != null && t.x() >= x0 - LENGTH && t.x() <= x1 && t.z() >= z0 - LENGTH && t.z() <= z1) {
					out.add(t);
				}
			}
		}
		return out;
	}

	/** True when (x, z) is in a blasting tomb's reach at this time. */
	public static Tomb blastingAt(double x, double z, long gameTime) {
		for (Tomb t : within((int) Math.floor(x) - 4, (int) Math.floor(z) - 4, (int) Math.floor(x) + 4, (int) Math.floor(z) + 4)) {
			if (t.blasting(gameTime) && Math.hypot(t.centreX() - x, t.centreZ() - z) <= REACH) {
				return t;
			}
		}
		return null;
	}

	private static long hash(int x, int z) {
		long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ 0x7E3B5L;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return h & Long.MAX_VALUE;
	}
}
