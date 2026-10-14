package net.thesmallthings.hellcraft.world;

/**
 * Wrath's rule (L-V): the sullen lurk under the Styx. A spot every few blocks of the marsh's water hides one, the
 * same for server and client (the client shows its bubbles). Swim over one and hands drag you down; plank roads and
 * boats are safe.
 */
public final class Sullen {
	private Sullen() {
	}

	/** The sullen sit on a grid of cells this many blocks across, one in every few cells. */
	private static final int CELL = 4;
	private static final int ONE_IN = 9;
	/** How close to a spot you must swim for the hands to reach you. */
	public static final double REACH = 1.6;

	/** The spot in the cell holding (x, z), as {x, z} block coordinates, or null if the cell has none. */
	public static int[] spot(int x, int z) {
		int cx = Math.floorDiv(x, CELL);
		int cz = Math.floorDiv(z, CELL);
		long h = hash(cx, cz);
		if (Math.floorMod(h, ONE_IN) != 0) {
			return null;
		}
		int sx = cx * CELL + (int) Math.floorMod(h >> 8, CELL);
		int sz = cz * CELL + (int) Math.floorMod(h >> 16, CELL);
		if (InfernoGeometry.zoneAt(sx + 0.5, sz + 0.5) != Zone.STYX || InfernoGeometry.fluidAt(sx + 0.5, sz + 0.5) != InfernoGeometry.Fluid.WATER) {
			return null;
		}
		return new int[]{sx, sz};
	}

	/** A spot within REACH of (x, z), or null. */
	public static int[] near(double x, double z) {
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		for (int dx = -CELL; dx <= CELL; dx += CELL) {
			for (int dz = -CELL; dz <= CELL; dz += CELL) {
				int[] s = spot(bx + dx, bz + dz);
				if (s != null && Math.hypot(s[0] + 0.5 - x, s[1] + 0.5 - z) <= REACH) {
					return s;
				}
			}
		}
		return null;
	}

	private static long hash(int x, int z) {
		long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ 0x5111E7L;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return h & Long.MAX_VALUE;
	}
}
