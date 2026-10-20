package net.thesmallthings.hellcraft.world;


import java.util.EnumMap;
import java.util.Map;

/**
 * Paints the Inferno from above, straight from the geometry: each circle in its own colour, shaded by height and
 * slope, with the rivers, the walls and the roads. Used by the Map of Hell (in game) and tools/RenderMap.java
 * (offline previews), so both show the same thing.
 */
public final class MapPainter {
	private MapPainter() {
	}

	private static final Map<Zone, Integer> COLOURS = new EnumMap<>(Zone.class);

	static {
		COLOURS.put(Zone.DARK_WOOD, 0x2E4A26);
		COLOURS.put(Zone.VESTIBULE, 0x8A8A80);
		COLOURS.put(Zone.ACHERON, 0x3A4A5E);
		COLOURS.put(Zone.LIMBO, 0xB8B8A8);
		COLOURS.put(Zone.LUST, 0x7A4A9A);
		COLOURS.put(Zone.GLUTTONY, 0x5A4430);
		COLOURS.put(Zone.GREED, 0xC8A030);
		COLOURS.put(Zone.STYX, 0x2A3A2A);
		COLOURS.put(Zone.WALLS_OF_DIS, 0x3A2A2A);
		COLOURS.put(Zone.HERESY, 0x5A7A8A);
		COLOURS.put(Zone.PHLEGETHON, 0xB01010);
		COLOURS.put(Zone.WOOD_OF_SUICIDES, 0x4A3020);
		COLOURS.put(Zone.BURNING_SANDS, 0xD06030);
		COLOURS.put(Zone.MALEBOLGE, 0x404048);
		COLOURS.put(Zone.MALEBOLGE_PITCH, 0x201010);
		COLOURS.put(Zone.MALEBOLGE_BLIGHT, 0x0A4A55);
		COLOURS.put(Zone.WELL_OF_GIANTS, 0x606060);
		COLOURS.put(Zone.COCYTUS, 0xA0C8E8);
		COLOURS.put(Zone.JUDECCA, 0x70A0F0);
	}

	public static final int ROAD = 0xD8C8A0;
	private static final int WATER = 0x203A70;
	private static final int LAVA = 0xFF5A00;
	private static final int WALL = 0x111111;
	private static final int GATE = 0xFF3020;
	private static final int CASTLE_GREEN = 0x5E9A3C;
	private static final int CASTLE_WALL = 0xD8D8CC;

	/** The zone's flat colour (0xRRGGBB). */
	public static int colour(Zone zone) {
		return COLOURS.get(zone);
	}

	/**
	 * The colour (0xRRGGBB) of the column at x, z, seen from above, for a map whose pixels are {@code scale}
	 * blocks wide: thin things (roads, walls) are widened to at least a pixel so they never break up.
	 */
	public static int colour(double x, double z, double scale) {
		// every colour a little toward old parchment, so the map reads as one drawing
		return mix(paint(x, z, scale), PARCHMENT, 0.12);
	}

	/** Old parchment, round the funnel on the Map of Hell. */
	public static final int PARCHMENT = 0xCBB894;

	/** Blends a colour toward another by t (0: all a, 1: all b). */
	public static int mix(int a, int b, double t) {
		int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
		int g = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
		int bl = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return (r << 16) | (g << 8) | bl;
	}

	private static int paint(double x, double z, double scale) {
		double h = InfernoGeometry.baseHeight(x, z);
		double hx = InfernoGeometry.baseHeight(x + scale, z);
		double hz = InfernoGeometry.baseHeight(x, z + scale);
		int c = COLOURS.get(InfernoGeometry.zoneAt(x, z));
		InfernoGeometry.Fluid fluid = InfernoGeometry.fluidAt(x, z);
		boolean wet = fluid != InfernoGeometry.Fluid.NONE && h < InfernoGeometry.fluidLevel(x, z);
		if (wet) {
			c = fluid == InfernoGeometry.Fluid.WATER ? WATER : LAVA;
		}
		double reach = Math.max(0, scale / 2 - 2);
		double re = InfernoGeometry.effectiveRadius(x, z);
		boolean built = false;
		if (Math.abs(re - InfernoGeometry.DIS_WALL_RADIUS) <= InfernoGeometry.DIS_WALL_HALF_THICKNESS + reach) {
			c = WALL;
			built = true;
		}
		if (Math.abs(re - InfernoGeometry.GATE_WALL_RADIUS) <= InfernoGeometry.GATE_WALL_HALF_THICKNESS + reach) {
			c = gateNear(x, z, scale) ? GATE : WALL;
			built = true;
		}
		int castle = castle(x, z, scale, reach, c);
		built |= castle != c;
		c = castle;
		if (roadNear(x, z, InfernoGeometry.ROAD_HALF_WIDTH + reach)) {
			return tint(ROAD, 0.95 + 0.1 * InfernoGeometry.fbm(x / 6, z / 6, 41));
		}
		if (built) {
			return c;
		}
		if (wet) {
			// water ripples and lava's hot veins
			double ripple = InfernoGeometry.fbm(x / 9, z / 9, 43);
			return fluid == InfernoGeometry.Fluid.WATER ? tint(c, 0.9 + 0.25 * ripple) : tint(c, 0.85 + 0.3 * Math.abs(ripple));
		}
		// the ground's own grain: broad patches and a finer speckle
		double grain = 1.0 + 0.09 * InfernoGeometry.fbm(x / 34, z / 34, 37) + 0.05 * InfernoGeometry.fbm(x / 7, z / 7, 39);
		// light from the north-west, so slopes facing it are lit and the far sides fall into shadow
		double d = Math.max(scale, 6);
		double lit = (InfernoGeometry.baseHeight(x - d, z - d) - InfernoGeometry.baseHeight(x + d, z + d)) / (2 * d);
		double shade = Math.max(0.55, Math.min(1.35, 1.0 + lit * 1.1));
		double height = 0.82 + (h + 60) / 320.0 * 0.36;
		int out = tint(c, shade * height * grain);
		// every cliff edge inked in, like a drawn map's contour
		double drop = Math.max(Math.abs(h - hx), Math.abs(h - hz));
		if (drop > Math.max(5, scale * 0.6)) {
			out = tint(out, 0.42);
		}
		return out;
	}

	/** Limbo's Noble Castle: its green meadow ringed by pale walls (only the outermost when zoomed out) and its stream. */
	private static int castle(double x, double z, double scale, double reach, int c) {
		double d = NobleCastle.distance(x, z);
		if (d >= NobleCastle.STREAM_OUTER + reach) {
			return c;
		}
		if (d >= NobleCastle.STREAM_INNER) {
			return WATER;
		}
		int out = CASTLE_GREEN;
		for (int k = scale > 3 ? NobleCastle.WALLS - 1 : 0; k < NobleCastle.WALLS; k++) {
			if (Math.abs(d - NobleCastle.radius(k)) <= 0.75 + reach) {
				out = CASTLE_WALL;
			}
		}
		return out;
	}

	/** On (or, at this half width, beside) one of the four roads. */
	private static boolean roadNear(double x, double z, double halfWidth) {
		for (double a : InfernoGeometry.ROAD_ANGLES) {
			double ax = Math.round(Math.cos(a));
			double az = Math.round(Math.sin(a));
			double along = x * ax + z * az;
			double side = -x * az + z * ax;
			if (Math.abs(side) <= halfWidth && along >= InfernoGeometry.roadInnerRadius() && along <= InfernoGeometry.roadOuterRadius()) {
				return true;
			}
		}
		return false;
	}

	private static boolean gateNear(double x, double z, double scale) {
		double theta = Math.atan2(z, x);
		double r = Math.sqrt(x * x + z * z);
		for (int k = 0; k < InfernoGeometry.GATE_COUNT; k++) {
			if (InfernoGeometry.angularDistance(theta, InfernoGeometry.gateAngle(k)) * r < 4.5 + scale / 2) {
				return true;
			}
		}
		return false;
	}

	public static int tint(int rgb, double f) {
		int r = (int) Math.max(0, Math.min(255, ((rgb >> 16) & 0xFF) * f));
		int g = (int) Math.max(0, Math.min(255, ((rgb >> 8) & 0xFF) * f));
		int b = (int) Math.max(0, Math.min(255, (rgb & 0xFF) * f));
		return (r << 16) | (g << 8) | b;
	}
}
