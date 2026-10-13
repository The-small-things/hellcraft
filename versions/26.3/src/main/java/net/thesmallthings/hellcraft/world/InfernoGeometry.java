package net.thesmallthings.hellcraft.world;

/**
 * The shape of Hell. Pure math with no Minecraft dependencies, so terrain, biomes, features and
 * gameplay hazards all agree on where everything is (and so it can be unit-tested and rendered
 * offline).
 *
 * <p>The world is Dante's funnel: concentric rings centred on 0,0. The outer rim (the Dark Wood)
 * sits high; every circle is a terrace lower than the one outside it, joined by cliffs with a few
 * "ruined slopes" (ramps) cut into them. The frozen lake Cocytus lies at the bottom.
 */
public final class InfernoGeometry {
	private InfernoGeometry() {
	}

	/** Radius of the world border; the Dark Wood climbs into mountains just before it. */
	public static final int BORDER_RADIUS = 6000;

	private static final double WOBBLE_AMPLITUDE = 28.0;
	private static final double WOBBLE_SCALE = 1.0 / 240.0;
	private static final double DEFAULT_CLIFF_WIDTH = 10.0;
	private static final double RAMP_WIDTH = 90.0;
	private static final double RAMP_HALF_ARC = 12.0;
	private static final double RAMP_FADE_ARC = 24.0;

	/** Four gates through the Walls of Dis; the ramps down into Heresy line up with them. */
	public static final double[] DIS_GATE_ANGLES = {Math.PI / 4, 3 * Math.PI / 4, 5 * Math.PI / 4, 7 * Math.PI / 4};
	/** Radial stone bridges ("spokes") crossing the ten ditches of Malebolge. */
	public static final int MALEBOLGE_SPOKES = 12;
	public static final int MALEBOLGE_DITCHES = 10;
	private static final double BRIDGE_HALF_WIDTH = 3.0;
	private static final double DITCH_DEPTH = 14.0;
	/** Radius of the pit at the very centre where Lucifer is frozen. */
	public static final double PIT_RADIUS = 30.0;
	/** Kept above y=-54, below which Minecraft floods open air with lava. */
	public static final int PIT_FLOOR_Y = -52;

	public static final double DIS_WALL_RADIUS = 2425.0;
	public static final double DIS_WALL_HALF_THICKNESS = 3.5;
	public static final int DIS_WALL_HEIGHT = 24;
	public static final int DIS_TOWER_HEIGHT = 34;
	private static final double DIS_TOWER_RADIUS = 5.0;
	private static final int DIS_TOWER_COUNT = 72;
	private static final double DIS_GATE_HALF_WIDTH = 5.0;
	public static final int DIS_GATE_HEIGHT = 10;

	/**
	 * @param zone      zone occupying this band
	 * @param outer     outer radius of the band
	 * @param inner     inner radius of the band
	 * @param hOut      surface height at the outer edge
	 * @param hIn       surface height at the inner edge (the floor slopes gently inward)
	 * @param rough     amplitude (blocks) of smooth detail noise
	 * @param ridged    amplitude (blocks) of ridged, spiky detail noise
	 * @param jagged    strength of 3D overhang noise
	 * @param ramps     number of ramps cut into the cliff on this band's inner edge
	 * @param cliff     width of the cliff on this band's inner edge
	 */
	record Band(Zone zone, double outer, double inner, double hOut, double hIn,
				double rough, double ridged, double jagged, int ramps, double cliff) {
	}

	static final Band[] BANDS = {
			new Band(Zone.DARK_WOOD, 6000, 5400, 172, 164, 7, 0, 0.12, InfernoGeometry.GATE_COUNT, 12),
			new Band(Zone.VESTIBULE, 5400, 5000, 150, 142, 3, 0, 0.05, 5, 12),
			new Band(Zone.ACHERON, 5000, 4900, 118, 118, 1.5, 0, 0.0, 3, 10),
			new Band(Zone.LIMBO, 4900, 4300, 134, 126, 5, 0, 0.08, 3, 10),
			new Band(Zone.LUST, 4300, 3800, 108, 100, 4, 16, 0.9, 3, 10),
			new Band(Zone.GLUTTONY, 3800, 3350, 88, 84, 2.5, 0, 0.05, 3, 10),
			new Band(Zone.GREED, 3350, 2900, 72, 66, 6, 3, 0.15, 3, 10),
			new Band(Zone.STYX, 2900, 2450, 53, 52, 4, 0, 0.0, 4, 10),
			new Band(Zone.WALLS_OF_DIS, 2450, 2400, 58, 58, 1, 0, 0.0, 4, 10),
			new Band(Zone.HERESY, 2400, 2000, 44, 38, 4, 2, 0.25, 3, 10),
			new Band(Zone.PHLEGETHON, 2000, 1880, 18, 18, 1.5, 0, 0.0, 3, 10),
			new Band(Zone.WOOD_OF_SUICIDES, 1880, 1640, 34, 33, 3, 0, 0.1, 0, 30),
			new Band(Zone.BURNING_SANDS, 1640, 1450, 33, 30, 6, 0, 0.0, 1, 10),
			new Band(Zone.MALEBOLGE, 1450, 700, -2, -12, 2, 0, 0.1, 2, 10),
			new Band(Zone.WELL_OF_GIANTS, 700, 600, -13, -14, 2, 0, 0.6, 2, 10),
			new Band(Zone.COCYTUS, 600, 150, -38, -41, 0.8, 0, 0.0, 0, 30),
			new Band(Zone.JUDECCA, 150, 0, -42, -43, 0.6, 0, 0.0, 0, 10),
	};

	public enum Fluid {NONE, WATER, LAVA}

	/** A ring of fluid. Margins extend it over the banks so the pool is bounded by terrain, not by zone edges. */
	private record River(Zone zone, int level, Fluid fluid, double outerMargin, double innerMargin) {
		boolean contains(double re) {
			Band b = bandFor(zone);
			return re <= b.outer + outerMargin && re >= b.inner - innerMargin;
		}
	}

	private static final River[] RIVERS = {
			new River(Zone.ACHERON, 125, Fluid.WATER, 100, 40),
			new River(Zone.STYX, 55, Fluid.WATER, 100, 0),
			new River(Zone.PHLEGETHON, 26, Fluid.LAVA, 100, 30),
	};

	// ------------------------------------------------------------------ radius & zones

	/** Radius with a gentle noise wobble so the circles look natural rather than compass-drawn. */
	public static double effectiveRadius(double x, double z) {
		double r = Math.sqrt(x * x + z * z);
		double fade = Math.min(1.0, r / 300.0);
		return r + WOBBLE_AMPLITUDE * fade * fbm(x * WOBBLE_SCALE, z * WOBBLE_SCALE, 7717);
	}

	static int bandIndex(double re) {
		for (int i = 0; i < BANDS.length; i++) {
			if (re >= BANDS[i].inner) {
				return i;
			}
		}
		return BANDS.length - 1;
	}

	static Band band(double re) {
		return BANDS[bandIndex(re)];
	}

	public static Zone zoneAt(double x, double z) {
		double re = effectiveRadius(x, z);
		Band b = band(re);
		if (b.zone == Zone.MALEBOLGE) {
			double theta = Math.atan2(z, x);
			if (ditchTrough(re, theta) > 0.5) {
				int ditch = ditchIndex(re);
				if (ditch % 3 == 1) {
					return Zone.MALEBOLGE_PITCH;
				}
				if (ditch % 3 == 2) {
					return Zone.MALEBOLGE_BLIGHT;
				}
			}
		}
		return b.zone;
	}

	public static Circle circleAt(double x, double z) {
		return zoneAt(x, z).circle();
	}

	/** Human-friendly name of the specific region (sub-ring) at a position. */
	public static String regionName(double x, double z) {
		double re = effectiveRadius(x, z);
		Zone zone = zoneAt(x, z);
		return switch (zone) {
			case ACHERON -> "The River Acheron";
			case STYX -> "The Marsh of Styx";
			case WALLS_OF_DIS -> "The Walls of Dis";
			case PHLEGETHON -> "Phlegethon, the River of Blood";
			case WOOD_OF_SUICIDES -> "The Wood of the Suicides";
			case BURNING_SANDS -> "The Burning Sands";
			case MALEBOLGE, MALEBOLGE_PITCH, MALEBOLGE_BLIGHT -> "Malebolge — Bolgia " + roman(ditchIndex(re) + 1);
			case WELL_OF_GIANTS -> "The Well of the Giants";
			case COCYTUS -> re > 450 ? "Caina" : re > 300 ? "Antenora" : "Ptolomea";
			case JUDECCA -> "Judecca";
			default -> zone.circle().title();
		};
	}

	/** 0 at the edge of Cocytus, 1 at the very centre. Used to scale the cold. */
	public static double treacheryDepth(double x, double z) {
		double re = effectiveRadius(x, z);
		return clamp(1.0 - re / 600.0, 0.0, 1.0);
	}

	// ------------------------------------------------------------------ height

	/** Target surface height (before detail noise) at a column. */
	public static double baseHeight(double x, double z) {
		double re = effectiveRadius(x, z);
		double theta = Math.atan2(z, x);
		return baseHeight(re, theta, Math.sqrt(x * x + z * z));
	}

	static double baseHeight(double re, double theta, double r) {
		int i = bandIndex(re);
		Band b = BANDS[i];
		double width = b.outer - b.inner;
		double t = clamp((b.outer - re) / width, 0.0, 1.0);
		double h = lerp(b.hOut, b.hIn, t);

		if (b.zone == Zone.DARK_WOOD && re > 5700) {
			double m = (re - 5700) / 300.0;
			h += Math.min(m * m, 1.6) * 75.0;
		}

		if (i < BANDS.length - 1) {
			Band next = BANDS[i + 1];
			double d = re - b.inner;
			double cw = lerp(b.cliff, Math.max(b.cliff, Math.min(RAMP_WIDTH, width * 0.8)), rampFactor(i, theta, re));
			if (d < cw) {
				double s = smoothstep(1.0 - d / cw);
				h = lerp(h, next.hOut, s);
			}
		}

		if (b.zone == Zone.MALEBOLGE) {
			h -= DITCH_DEPTH * ditchTrough(re, theta);
		}

		if (b.zone == Zone.JUDECCA && r < PIT_RADIUS) {
			double s = smoothstep(1.0 - r / PIT_RADIUS);
			h = lerp(h, PIT_FLOOR_Y, s);
		}
		return h;
	}

	/**
	 * Amplitudes of detail noise at a column: {smooth, ridged, jagged3d}. Averaged over a small
	 * radial window so roughness changes smoothly across zone boundaries.
	 */
	public static double[] roughness(double x, double z) {
		double re = effectiveRadius(x, z);
		double smooth = 0, ridged = 0, jagged = 0;
		for (int k = -2; k <= 2; k++) {
			Band b = band(re + k * 8.0);
			smooth += b.rough;
			ridged += b.ridged;
			jagged += b.jagged;
		}
		double r = Math.sqrt(x * x + z * z);
		double[] out = {smooth / 5.0, ridged / 5.0, jagged / 5.0};
		if (r < PIT_RADIUS + 10) {
			// keep Lucifer's pit clean
			out[0] *= 0.3;
		}
		return out;
	}

	/**
	 * Final surface height given a detail noise sample in roughly [-1, 1]. This is what the density
	 * function uses; offline tools can pass 0 to see the undecorated shape.
	 */
	public static double surfaceHeight(double x, double z, double detail) {
		double[] a = roughness(x, z);
		double ridge = 1.0 - Math.abs(detail);
		ridge = ridge * ridge * ridge;
		return baseHeight(x, z) + detail * a[0] + ridge * a[1];
	}

	public static double jaggedAmplitude(double x, double z) {
		return roughness(x, z)[2];
	}

	// ------------------------------------------------------------------ ramps

	static double rampFactor(int boundary, double theta, double re) {
		Band b = BANDS[boundary];
		if (b.ramps <= 0) {
			return 0.0;
		}
		double best = 0.0;
		for (int k = 0; k < b.ramps; k++) {
			double angle = rampAngle(boundary, k);
			double arc = angularDistance(theta, angle) * re;
			double f = clamp(1.0 - (arc - RAMP_HALF_ARC) / RAMP_FADE_ARC, 0.0, 1.0);
			best = Math.max(best, smoothstep(f));
		}
		return best;
	}

	static double rampAngle(int boundary, int k) {
		Zone zone = BANDS[boundary].zone;
		if (zone == Zone.DARK_WOOD) {
			// every gate in the Gate of Hell leads onto a slope down into the Vestibule
			return gateAngle(k);
		}
		if (zone == Zone.WALLS_OF_DIS || zone == Zone.STYX) {
			return DIS_GATE_ANGLES[k % DIS_GATE_ANGLES.length];
		}
		if (zone == Zone.BURNING_SANDS || zone == Zone.MALEBOLGE || zone == Zone.WELL_OF_GIANTS) {
			// the descent through the lower circles follows Malebolge's bridges
			int spoke = (int) Math.floor(hash01(boundary, k, 991) * MALEBOLGE_SPOKES);
			return spokeAngle(spoke);
		}
		return hash01(boundary, k, 313) * 2.0 * Math.PI - Math.PI;
	}

	// ------------------------------------------------------------------ malebolge

	public static double spokeAngle(int spoke) {
		return -Math.PI + (spoke + 0.5) * (2.0 * Math.PI / MALEBOLGE_SPOKES);
	}

	static int ditchIndex(double re) {
		Band b = bandFor(Zone.MALEBOLGE);
		double width = (b.outer - b.inner) / MALEBOLGE_DITCHES;
		int k = (int) Math.floor((b.outer - re) / width);
		return Math.max(0, Math.min(MALEBOLGE_DITCHES - 1, k));
	}

	/** 0 on ridges and bridges, 1 on ditch floors. */
	static double ditchTrough(double re, double theta) {
		Band b = bandFor(Zone.MALEBOLGE);
		if (re > b.outer || re < b.inner) {
			return 0.0;
		}
		double width = (b.outer - b.inner) / MALEBOLGE_DITCHES;
		double u = (b.outer - re) / width;
		double f = u - Math.floor(u);
		double edge = Math.min(f, 1.0 - f);
		double trough = smoothstep(clamp((edge - 0.08) / 0.14, 0.0, 1.0));
		double bridge = bridgeFactor(theta, re);
		return trough * (1.0 - bridge);
	}

	/** 1 on a Malebolge bridge, fading to 0 beside it. */
	public static double bridgeFactor(double theta, double re) {
		double best = 0.0;
		for (int s = 0; s < MALEBOLGE_SPOKES; s++) {
			double arc = angularDistance(theta, spokeAngle(s)) * re;
			double f = clamp(1.0 - (arc - BRIDGE_HALF_WIDTH) / 3.0, 0.0, 1.0);
			best = Math.max(best, f);
		}
		return best;
	}

	// ------------------------------------------------------------------ rivers

	/** Fluid that should fill air below {@link #fluidLevel} at this column. */
	public static Fluid fluidAt(double x, double z) {
		double re = effectiveRadius(x, z);
		for (River river : RIVERS) {
			if (river.contains(re)) {
				return river.fluid;
			}
		}
		return Fluid.NONE;
	}

	/** Surface level of the river at this column, or {@link Integer#MIN_VALUE} if none. */
	public static int fluidLevel(double x, double z) {
		double re = effectiveRadius(x, z);
		for (River river : RIVERS) {
			if (river.contains(re)) {
				return river.level;
			}
		}
		return Integer.MIN_VALUE;
	}

	/** Cheap check: can any column within {@code slack} blocks of this radius hold a river? */
	public static boolean nearRiver(double r, double slack) {
		for (River river : RIVERS) {
			Band b = bandFor(river.zone);
			if (r <= b.outer + river.outerMargin + WOBBLE_AMPLITUDE + slack
					&& r >= b.inner - river.innerMargin - WOBBLE_AMPLITUDE - slack) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ walls of dis

	public enum WallPart {NONE, WALL, TOWER, GATE}

	public static boolean nearDisWall(double r, double slack) {
		return Math.abs(r - DIS_WALL_RADIUS) < DIS_TOWER_RADIUS + 1 + WOBBLE_AMPLITUDE + slack;
	}

	/** What part of the Walls of Dis (if any) stands on this column. The wall follows the wobbling ring of Dis. */
	public static WallPart disWallAt(double x, double z) {
		double r = Math.sqrt(x * x + z * z);
		double re = effectiveRadius(x, z);
		double theta = Math.atan2(z, x);
		double radial = re - DIS_WALL_RADIUS;
		if (Math.abs(radial) > DIS_TOWER_RADIUS + 1) {
			return WallPart.NONE;
		}
		double towerStep = 2.0 * Math.PI / DIS_TOWER_COUNT;
		double towerAngle = Math.round(theta / towerStep) * towerStep;
		double arc = angularDistance(theta, towerAngle) * r;
		boolean inGate = false;
		for (double gate : DIS_GATE_ANGLES) {
			if (angularDistance(theta, gate) * r < DIS_GATE_HALF_WIDTH) {
				inGate = true;
			}
		}
		if (!inGate && Math.sqrt(arc * arc + radial * radial) <= DIS_TOWER_RADIUS) {
			return WallPart.TOWER;
		}
		if (Math.abs(radial) <= DIS_WALL_HALF_THICKNESS) {
			return inGate ? WallPart.GATE : WallPart.WALL;
		}
		return WallPart.NONE;
	}

	// ------------------------------------------------------------------ landmarks

	// ------------------------------------------------------------------ the gate of hell

	/** The Gate of Hell is a ring wall enclosing the Vestibule and Acheron, pierced by these gates. */
	public static final int GATE_COUNT = 12;
	/** Effective radius of the wall: just outside the Dark Wood's cliff and the ramps cut into it. */
	public static final double GATE_WALL_RADIUS = 5495.0;
	public static final double GATE_WALL_HALF_THICKNESS = 1.5;
	public static final int GATE_WALL_HEIGHT = 18;
	public static final int GATE_TOWER_HEIGHT = 26;
	public static final int GATE_OPENING_HEIGHT = 10;
	private static final double GATE_HALF_WIDTH = 4.5;
	private static final double GATE_TOWER_RADIUS = 2.5;
	private static final int GATE_TOWER_COUNT = 180;

	public static double gateAngle(int k) {
		return k * 2.0 * Math.PI / GATE_COUNT;
	}

	public static boolean nearGateWall(double r, double slack) {
		return Math.abs(r - GATE_WALL_RADIUS) < GATE_TOWER_RADIUS + 1 + WOBBLE_AMPLITUDE + slack;
	}

	/** What part of the Gate of Hell (if any) stands on this column. */
	public static WallPart gateWallAt(double x, double z) {
		double re = effectiveRadius(x, z);
		double radial = re - GATE_WALL_RADIUS;
		if (Math.abs(radial) > GATE_TOWER_RADIUS + 1) {
			return WallPart.NONE;
		}
		double r = Math.sqrt(x * x + z * z);
		double theta = Math.atan2(z, x);
		double nearestGate = Double.MAX_VALUE;
		for (int k = 0; k < GATE_COUNT; k++) {
			nearestGate = Math.min(nearestGate, angularDistance(theta, gateAngle(k)) * r);
		}
		if (nearestGate < GATE_HALF_WIDTH) {
			return Math.abs(radial) <= GATE_WALL_HALF_THICKNESS ? WallPart.GATE : WallPart.NONE;
		}
		double towerStep = 2.0 * Math.PI / GATE_TOWER_COUNT;
		double towerAngle = Math.round(theta / towerStep) * towerStep;
		double arc = angularDistance(theta, towerAngle) * r;
		// flanking towers either side of every gate, and a tower every 2 degrees along the wall
		boolean flank = Math.abs(nearestGate - (GATE_HALF_WIDTH + GATE_TOWER_RADIUS)) <= GATE_TOWER_RADIUS;
		if (flank || (nearestGate > 20 && Math.sqrt(arc * arc + radial * radial) <= GATE_TOWER_RADIUS)) {
			if (Math.abs(radial) <= GATE_TOWER_RADIUS) {
				return WallPart.TOWER;
			}
		}
		if (Math.abs(radial) <= GATE_WALL_HALF_THICKNESS) {
			return WallPart.WALL;
		}
		return WallPart.NONE;
	}

	/** Column just outside gate {@code k}, where its inscription stands. */
	public static int[] gateSignSpot(int k) {
		double a = gateAngle(k);
		double target = GATE_WALL_RADIUS + GATE_WALL_HALF_THICKNESS + 3.0;
		double lo = 5300, hi = 5700;
		for (int i = 0; i < 40; i++) {
			double mid = (lo + hi) / 2;
			if (effectiveRadius(Math.cos(a) * mid, Math.sin(a) * mid) < target) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		return new int[]{(int) Math.floor(Math.cos(a) * lo), (int) Math.floor(Math.sin(a) * lo)};
	}

	/** X coordinate (on the +X axis, z = 0) where the Gate of Hell's wall crosses: the spawn gate. */
	public static int gateX() {
		for (int x = 5300; x < 5800; x++) {
			if (effectiveRadius(x, 0) >= GATE_WALL_RADIUS) {
				return x;
			}
		}
		return (int) GATE_WALL_RADIUS;
	}

	/** Radius at which the chained giants stand. */
	public static final double GIANT_RADIUS = 660.0;

	// ------------------------------------------------------------------ helpers

	/** Angles (radians) of the ramps cut into a band's inner edge: the ways down into the band below it. */
	public static double[] rampAngles(Zone zone) {
		for (int i = 0; i < BANDS.length; i++) {
			if (BANDS[i].zone == zone) {
				double[] angles = new double[BANDS[i].ramps];
				for (int k = 0; k < angles.length; k++) {
					angles[k] = rampAngle(i, k);
				}
				return angles;
			}
		}
		return new double[0];
	}

	/** Radius of a zone's band where it begins (its outer edge). */
	public static double outerRadius(Zone zone) {
		return bandFor(zone).outer;
	}

	static Band bandFor(Zone zone) {
		for (Band b : BANDS) {
			if (b.zone == zone) {
				return b;
			}
		}
		throw new IllegalArgumentException(zone.name());
	}

	public static double angularDistance(double a, double b) {
		double d = Math.abs(a - b) % (2.0 * Math.PI);
		return d > Math.PI ? 2.0 * Math.PI - d : d;
	}

	static double clamp(double v, double lo, double hi) {
		return v < lo ? lo : Math.min(v, hi);
	}

	static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}

	static double smoothstep(double t) {
		t = clamp(t, 0.0, 1.0);
		return t * t * (3.0 - 2.0 * t);
	}

	static double hash01(int a, int b, int seed) {
		long h = seed * 0x9E3779B97F4A7C15L;
		h ^= a * 0xC2B2AE3D27D4EB4FL;
		h = Long.rotateLeft(h, 31) * 0x165667B19E3779F9L;
		h ^= b * 0x94D049BB133111EBL;
		h ^= h >>> 29;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 32;
		return (h >>> 11) * 0x1.0p-53;
	}

	private static double valueNoise(double x, double z, int seed) {
		int x0 = (int) Math.floor(x);
		int z0 = (int) Math.floor(z);
		double fx = smoothstep(x - x0);
		double fz = smoothstep(z - z0);
		double a = hash01(x0, z0, seed);
		double b = hash01(x0 + 1, z0, seed);
		double c = hash01(x0, z0 + 1, seed);
		double d = hash01(x0 + 1, z0 + 1, seed);
		return lerp(lerp(a, b, fx), lerp(c, d, fx), fz) * 2.0 - 1.0;
	}

	/** Two-octave value noise in roughly [-1, 1]. */
	static double fbm(double x, double z, int seed) {
		return valueNoise(x, z, seed) * 0.7 + valueNoise(x * 2.3, z * 2.3, seed + 1) * 0.3;
	}

	static String roman(int n) {
		String[] r = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
		return n >= 1 && n <= r.length ? r[n - 1] : Integer.toString(n);
	}
}
