package net.thesmallthings.hellcraft.world;


/**
 * Limbo's Noble Castle (L-I), the hub of Upper Hell: "a noble castle, seven times encircled by high walls, defended
 * round about by a fair rivulet" (Inferno IV). It stands on the east road, which runs through its seven gates; inside
 * is the only green meadow in Hell, and on a luminous mound at its heart the shades of the poets and philosophers
 * (Shades) talk, trade and hand out Codex pages.
 * <p>
 * The geometry lives here so the worldgen (NobleCastleFeature), the map (MapPainter) and the tests agree.
 */
public final class NobleCastle {
	private NobleCastle() {
	}

	/** The castle's heart, on the east road in the middle of Limbo. */
	public static final int X = (int) Math.round(InfernoGeometry.scaled(2760));
	public static final int Z = 0;
	public static final int WALLS = 7;
	/** Wall k (0 innermost) stands at radius INNER + SPACING * k. */
	private static final int INNER = 20;
	private static final int SPACING = 4;
	/** The fair rivulet runs round the outside, between these radii. */
	public static final double STREAM_INNER = 48.5;
	public static final double STREAM_OUTER = 50.5;
	/** The green: the castle's own biome reaches this far. */
	public static final double GREEN = 53;
	/** The ground is level this far out, fading to the meadow's own over LEVEL_FADE. */
	private static final double LEVEL = 58;
	private static final double LEVEL_FADE = 16;
	/** The luminous mound at the heart. */
	public static final double MOUND = 7;
	/** Gates (and paths) run along the four axes, this many blocks either side. */
	public static final double GATE_HALF_WIDTH = 2.5;
	public static final int GATE_HEIGHT = 4;

	public enum Part {
		NONE,
		/** Grass inside the castle. */
		MEADOW,
		/** A path from gate to gate along an axis (the east road does its own paving). */
		PATH,
		WALL,
		/** A gate through a wall: open below GATE_HEIGHT. */
		GATE,
		/** A round tower on the innermost and outermost walls. */
		TOWER,
		STREAM,
		/** The raised mound at the heart. */
		MOUND
	}

	public static double distance(double x, double z) {
		return Math.hypot(x - X, z - Z);
	}

	public static double radius(int wall) {
		return INNER + SPACING * wall;
	}

	/** The outermost wall is the lowest; each one inward stands a block higher, like tiers. */
	public static int wallHeight(int wall) {
		return 6 + (WALLS - 1 - wall);
	}

	/** Which wall a column is on (or -1). */
	public static int wallAt(double x, double z) {
		double d = distance(x, z);
		for (int k = 0; k < WALLS; k++) {
			if (Math.abs(d - radius(k)) < 0.75) {
				return k;
			}
		}
		return -1;
	}

	/** True on the four axes through the heart, where the gates and paths are. */
	private static boolean onAxis(double x, double z) {
		return Math.abs(x - X) <= GATE_HALF_WIDTH || Math.abs(z - Z) <= GATE_HALF_WIDTH;
	}

	/** True in a tower: on the innermost and outermost walls, at the four diagonals. */
	private static boolean tower(double x, double z) {
		double d = distance(x, z);
		for (int k : new int[]{0, WALLS - 1}) {
			double tr = radius(k);
			for (int q = 0; q < 4; q++) {
				double a = Math.PI / 4 + q * Math.PI / 2;
				double tx = X + Math.cos(a) * tr;
				double tz = Z + Math.sin(a) * tr;
				if (Math.hypot(x - tx, z - tz) < 2.2 && Math.abs(d - tr) < 2.5) {
					return true;
				}
			}
		}
		return false;
	}

	public static Part partAt(double x, double z) {
		double d = distance(x, z);
		if (d >= STREAM_OUTER + 1) {
			return Part.NONE;
		}
		if (d >= STREAM_INNER) {
			// the rivulet, crossed as if on hard ground where the paths go over
			return d < STREAM_OUTER && !onAxis(x, z) ? Part.STREAM : Part.NONE;
		}
		if (tower(x, z)) {
			return Part.TOWER;
		}
		if (wallAt(x, z) >= 0) {
			return onAxis(x, z) ? Part.GATE : Part.WALL;
		}
		if (d < MOUND) {
			return Part.MOUND;
		}
		return onAxis(x, z) ? Part.PATH : Part.MEADOW;
	}

	/** True inside the castle's green (its biome). */
	public static boolean green(double x, double z) {
		return distance(x, z) < GREEN;
	}

	/** True within the outermost wall. */
	public static boolean inside(double x, double z) {
		return distance(x, z) < radius(WALLS - 1);
	}

	/** 1 on the castle's level ground, fading to 0 around it (InfernoGeometry.roughness). */
	static double levelling(double x, double z) {
		double dx = x - X;
		double dz = z - Z;
		if (Math.abs(dx) > LEVEL + LEVEL_FADE || Math.abs(dz) > LEVEL + LEVEL_FADE) {
			return 0;
		}
		double d = Math.sqrt(dx * dx + dz * dz);
		double t = Math.max(0, Math.min(1, 1 - (d - LEVEL) / LEVEL_FADE));
		return t * t * (3 - 2 * t);
	}
}
