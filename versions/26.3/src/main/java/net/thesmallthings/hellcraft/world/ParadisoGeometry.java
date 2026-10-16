package net.thesmallthings.hellcraft.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Paradiso's shape (Phase 7): its own dimension, a climb upward. Nine spheres are stacked one above the other, each a
 * cluster of floating islands with its own look, and the Empyrean with the Celestial Rose is on top. A column of light
 * lifts you from each sphere's departure isle to the next sphere's arrival isle, straight above it; you can always see
 * the next sphere over you.
 *
 * <p>The spheres' centres go round a circle (three tenths of a turn each), so no sphere sits right over the one below
 * it and each column of light rises halfway between two centres. Pure geometry, shared by the island feature
 * (ParadisoIslandFeature), the trials (ParadisoTrials) and the client (the columns of light and the compass); it can
 * be checked offline (java ... ParadisoGeometry).
 */
public final class ParadisoGeometry {
	private ParadisoGeometry() {
	}

	public enum Sphere {
		MOON("moon", "I. The Moon", "The inconstant, who left their vows unkept", 0xE8EEFF,
				"Gather the five pale lights on its islands."),
		MERCURY("mercury", "II. Mercury", "The ambitious, who did good for glory", 0x9FD8FF,
				"Gather the six quick lights within a minute of the first."),
		VENUS("venus", "III. Venus", "The lovers", 0xFF9AD0,
				"Catch the four lights that circle the island."),
		SUN("sun", "IV. The Sun", "The wise, in their rings of light", 0xFFD860,
				"Gather the twelve lights in the ring of the wise."),
		MARS("mars", "V. Mars", "The warriors of the faith", 0xFF6A50,
				"Stand at the heart of the cross and beat the Angelic Guard."),
		JUPITER("jupiter", "VI. Jupiter", "The just rulers, who make the Eagle", 0xF0F4FF,
				"Touch the Eagle's five lights in order: tail, wings, heart, eye."),
		SATURN("saturn", "VII. Saturn", "The contemplatives, and the golden ladder", 0xC8B0FF,
				"Climb Jacob's Ladder to the light at its top."),
		FIXED_STARS("fixed_stars", "VIII. The Fixed Stars", "The Church Triumphant", 0x7A9CFF,
				"Gather the seven lights on top of the star spires."),
		PRIMUM_MOBILE("primum_mobile", "IX. The Primum Mobile", "The angels, turning all the heavens", 0x9FF6FF,
				"Face the Seraph."),
		EMPYREAN("empyrean", "The Empyrean", "The love that moves the sun and the other stars", 0xFFF6D8,
				"Ring the bell of the Celestial Rose.");

		public final String id;
		public final String title;
		public final String tagline;
		/** The colour of the column of light that leaves this sphere (RGB). */
		public final int colour;
		/** The trial, in one plain sentence. */
		public final String trial;

		Sphere(String id, String title, String tagline, int colour, String trial) {
			this.id = id;
			this.title = title;
			this.tagline = tagline;
			this.colour = colour;
			this.trial = trial;
		}

		public String biome() {
			return "hellcraft:paradiso_" + id;
		}

		public boolean last() {
			return this == EMPYREAN;
		}
	}

	public static final Sphere[] SPHERES = Sphere.values();
	/** The Moon's islands' top, and the climb from one sphere to the next. */
	public static final int BASE_Y = 64;
	public static final int STEP = 96;
	/** The spheres' centres go round this circle, three tenths of a turn apart. */
	private static final double RING = 40.0 / Math.sin(Math.toRadians(54));
	private static final double TURN = Math.toRadians(108);
	public static final double CENTRE_RADIUS = 20;
	public static final double ISLE_RADIUS = 7;
	/** Half the side of a column of light (the arrival isle's hole is three blocks wide). */
	public static final double COLUMN_HALF = 1.15;

	public static int topY(Sphere s) {
		return BASE_Y + s.ordinal() * STEP;
	}

	/** The sphere whose band of sky a height is in (from half a step below its islands to half a step above). */
	public static Sphere sphereAtY(double y) {
		int k = (int) Math.floor((y - BASE_Y + STEP / 2.0) / STEP);
		return SPHERES[Math.max(0, Math.min(SPHERES.length - 1, k))];
	}

	/** The centre of a sphere's great island, {x, z}. */
	public static double[] centre(Sphere s) {
		double a = s.ordinal() * TURN;
		return new double[]{RING * Math.cos(a), RING * Math.sin(a)};
	}

	/** Where the column of light leaving a sphere rises (none from the Empyrean): halfway to the next sphere's centre. */
	public static double[] departure(Sphere s) {
		if (s.last()) {
			return null;
		}
		double[] a = centre(s);
		double[] b = centre(SPHERES[s.ordinal() + 1]);
		return new double[]{Math.floor((a[0] + b[0]) / 2) + 0.5, Math.floor((a[1] + b[1]) / 2) + 0.5};
	}

	/** Where you arrive in a sphere: over the column from the one below (the Moon: the Ascent from Purgatory lands here). */
	public static double[] arrival(Sphere s) {
		if (s.ordinal() > 0) {
			return departure(SPHERES[s.ordinal() - 1]);
		}
		double[] c = centre(s);
		double[] d = departure(s);
		return new double[]{Math.floor(2 * c[0] - d[0]) + 0.5, Math.floor(2 * c[1] - d[1]) + 0.5};
	}

	/** Where a player is set down on arriving: on the arrival isle, beside its column, towards the centre. */
	public static double[] landing(Sphere s) {
		double[] a = arrival(s);
		double[] c = centre(s);
		double dx = c[0] - a[0];
		double dz = c[1] - a[1];
		double len = Math.sqrt(dx * dx + dz * dz);
		return new double[]{a[0] + dx / len * 3, topY(s) + 1, a[1] + dz / len * 3};
	}

	/** The sphere whose column of light a position is in, or null. */
	public static Sphere columnAt(double x, double y, double z) {
		Sphere s = sphereAtY(y - STEP / 2.0);
		for (Sphere t : new Sphere[]{s, sphereAtY(y)}) {
			double[] d = departure(t);
			if (d != null && Math.abs(x - d[0]) < COLUMN_HALF && Math.abs(z - d[1]) < COLUMN_HALF
					&& y >= topY(t) + 1 && y < topY(SPHERES[t.ordinal() + 1]) + 2) {
				return t;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------------------------------ islands

	public enum Kind {CENTRE, ARRIVAL, DEPARTURE, SATELLITE}

	/** A floating island: its centre, its top (flat), its radius and a seed for its ragged edge. */
	public record Island(Kind kind, double x, double z, int top, double radius, int seed) {
		/** The island's edge in a direction: a little ragged. */
		public double edge(double angle) {
			return radius * (1 + 0.09 * Math.sin(3 * angle + seed) + 0.05 * Math.sin(5 * angle + seed * 1.7));
		}

		/** How deep the island hangs below its top at a column, or -1 outside it. */
		public int depth(double px, double pz) {
			double dx = px - x;
			double dz = pz - z;
			double d = Math.sqrt(dx * dx + dz * dz);
			double e = edge(Math.atan2(dz, dx));
			if (d > e) {
				return -1;
			}
			double f = 1 - (d / e) * (d / e);
			return 1 + (int) Math.round(Math.pow(f, 0.8) * radius * 0.85 + 1.5 * InfernoGeometry.hash01((int) px, (int) pz, seed) * f);
		}
	}

	/** The great island's radius: larger for the Seraph's arena and the Rose. */
	public static double centreRadius(Sphere s) {
		return s == Sphere.PRIMUM_MOBILE ? 24 : s == Sphere.EMPYREAN ? 34 : CENTRE_RADIUS;
	}

	/** A sphere's islands: the great one, the arrival and departure isles, and four small ones round about. */
	public static List<Island> islands(Sphere s) {
		List<Island> out = new ArrayList<>();
		int y = topY(s);
		double[] c = centre(s);
		out.add(new Island(Kind.CENTRE, c[0], c[1], y, centreRadius(s), 11 + s.ordinal()));
		double[] a = arrival(s);
		out.add(new Island(Kind.ARRIVAL, a[0], a[1], y, ISLE_RADIUS, 23 + s.ordinal()));
		double[] d = departure(s);
		if (d != null) {
			out.add(new Island(Kind.DEPARTURE, d[0], d[1], y, ISLE_RADIUS, 37 + s.ordinal()));
		}
		for (double[] sat : satellites(s)) {
			out.add(new Island(Kind.SATELLITE, sat[0], sat[1], (int) sat[2], 5, 51 + s.ordinal() * 7 + (int) sat[3]));
		}
		return out;
	}

	/** The small islands, {x, z, top, index}: four of eight directions round the centre, away from the two columns. */
	public static List<double[]> satellites(Sphere s) {
		List<double[]> out = new ArrayList<>();
		if (s.last()) {
			return out;
		}
		double[] c = centre(s);
		double toArrival = direction(c, arrival(s));
		double toDeparture = direction(c, departure(s));
		int[] lift = {3, -2, 5, 1, -3, 4, 2, -1};
		for (int i = 0; i < 8 && out.size() < 4; i++) {
			double a = i * Math.PI / 4 + s.ordinal() * 0.37;
			if (gap(a, toArrival) < 0.75 || gap(a, toDeparture) < 0.75) {
				continue;
			}
			double r = 36 + 4 * InfernoGeometry.hash01(i, s.ordinal(), 71);
			out.add(new double[]{c[0] + Math.cos(a) * r, c[1] + Math.sin(a) * r, topY(s) + lift[(i + s.ordinal()) % 8], out.size()});
		}
		return out;
	}

	private static double direction(double[] from, double[] to) {
		return Math.atan2(to[1] - from[1], to[0] - from[0]);
	}

	private static double gap(double a, double b) {
		double d = Math.abs(a - b) % (2 * Math.PI);
		return Math.min(d, 2 * Math.PI - d);
	}

	/** The bridges, {x0, z0, x1, z1}: from the arrival isle to the great island, and from it to the departure isle. */
	public static List<double[]> bridges(Sphere s) {
		List<double[]> out = new ArrayList<>();
		double[] c = centre(s);
		double[] a = arrival(s);
		out.add(new double[]{a[0], a[1], c[0], c[1]});
		double[] d = departure(s);
		if (d != null) {
			out.add(new double[]{c[0], c[1], d[0], d[1]});
		}
		return out;
	}

	/** Stepping stones out to the small islands, {x, y, z}: one every three blocks, climbing or dropping a block at a time. */
	public static List<int[]> stones(Sphere s) {
		List<int[]> out = new ArrayList<>();
		double[] c = centre(s);
		double from = centreRadius(s);
		for (double[] sat : satellites(s)) {
			double dx = sat[0] - c[0];
			double dz = sat[1] - c[1];
			double len = Math.sqrt(dx * dx + dz * dz);
			double span = len - 5 - from;
			int n = (int) Math.floor(span / 3);
			for (int i = 1; i <= n; i++) {
				double t = (from + span * i / (n + 1.0)) / len;
				int y = (int) Math.round(topY(s) + (sat[2] - topY(s)) * (double) i / (n + 1));
				out.add(new int[]{(int) Math.floor(c[0] + dx * t), y, (int) Math.floor(c[1] + dz * t)});
			}
		}
		return out;
	}

	// ------------------------------------------------------------------------------------------ the trials

	/**
	 * The lights a sphere's trial asks you to gather, {x, y, z} (block centres, one block over the island), or none
	 * for the trials that aren't gathering (Venus's lights move: {@link #venusLight}).
	 */
	public static List<double[]> lights(Sphere s) {
		List<double[]> out = new ArrayList<>();
		double[] c = centre(s);
		int y = topY(s);
		List<double[]> sats = satellites(s);
		switch (s) {
			case MOON -> {
				for (double[] sat : sats) {
					out.add(new double[]{Math.floor(sat[0]) + 0.5, sat[2] + 2, Math.floor(sat[1]) + 0.5});
				}
				out.add(new double[]{Math.floor(c[0]) + 0.5, y + 2, Math.floor(c[1]) + 0.5});
			}
			case MERCURY -> {
				for (double[] sat : sats) {
					out.add(new double[]{Math.floor(sat[0]) + 0.5, sat[2] + 2, Math.floor(sat[1]) + 0.5});
				}
				out.add(new double[]{Math.floor(c[0]) + 12.5, y + 2, Math.floor(c[1]) + 0.5});
				out.add(new double[]{Math.floor(c[0]) - 11.5, y + 2, Math.floor(c[1]) + 0.5});
			}
			case SUN -> {
				for (int i = 0; i < 12; i++) {
					double a = i * Math.PI / 6;
					out.add(new double[]{Math.floor(c[0] + Math.cos(a) * 13) + 0.5, y + 2, Math.floor(c[1] + Math.sin(a) * 13) + 0.5});
				}
			}
			case JUPITER -> {
				// the Eagle in the floor, its head to the north (-z): tail, left wing, right wing, heart, eye
				int[][] at = {{0, 12}, {-13, -1}, {13, -1}, {0, 0}, {0, -12}};
				for (int[] p : at) {
					out.add(new double[]{Math.floor(c[0]) + p[0] + 0.5, y + 2, Math.floor(c[1]) + p[1] + 0.5});
				}
			}
			case SATURN -> out.add(new double[]{Math.floor(c[0]) + 0.5, y + LADDER_HEIGHT + 2, Math.floor(c[1]) + 0.5});
			case FIXED_STARS -> {
				for (int[] spire : spires(s)) {
					out.add(new double[]{spire[0] + 0.5, spire[1] + spire[3] + 1, spire[2] + 0.5});
				}
			}
			default -> {
			}
		}
		return out;
	}

	/** Saturn's golden ladder: how high it climbs over the island. */
	public static final int LADDER_HEIGHT = 40;
	/** Its steps: a helix round a pillar, one block up for each step. */
	public static final double LADDER_TURN = 0.3;

	/** The Fixed Stars' spires, {x, base y (the island top), z, height}: one on each small island and three on the great one. */
	public static List<int[]> spires(Sphere s) {
		List<int[]> out = new ArrayList<>();
		if (s != Sphere.FIXED_STARS) {
			return out;
		}
		for (double[] sat : satellites(s)) {
			out.add(new int[]{(int) Math.floor(sat[0]), (int) sat[2], (int) Math.floor(sat[1]), 5 + (int) sat[3] % 3});
		}
		double[] c = centre(s);
		for (int i = 0; i < 3; i++) {
			double a = i * 2 * Math.PI / 3 + 0.5;
			out.add(new int[]{(int) Math.floor(c[0] + Math.cos(a) * 11), topY(s), (int) Math.floor(c[1] + Math.sin(a) * 11), 6 + 2 * i});
		}
		return out;
	}

	/** One of Venus's four lights at a moment: they circle the great island, a block and a half over it. */
	public static double[] venusLight(int i, double ticks) {
		double[] c = centre(Sphere.VENUS);
		double a = i * Math.PI / 2 + ticks * 2 * Math.PI / 900;
		return new double[]{c[0] + Math.cos(a) * 14, topY(Sphere.VENUS) + 1.5, c[1] + Math.sin(a) * 14};
	}

	public static void main(String[] args) {
		for (Sphere s : SPHERES) {
			double[] c = centre(s);
			double[] a = arrival(s);
			double[] d = departure(s);
			System.out.printf(Locale.ROOT, "%-14s y %4d centre %6.1f %6.1f  arrive %6.1f %6.1f  leave %s  lights %d%n", s.id, topY(s), c[0], c[1],
					a[0], a[1], d == null ? "-" : String.format(Locale.ROOT, "%6.1f %6.1f", d[0], d[1]), lights(s).size());
		}
		// no island may stand in another sphere's column of light, nor two of a sphere's islands overlap
		for (Sphere s : SPHERES) {
			List<Island> isles = islands(s);
			for (Island i : isles) {
				for (Island j : isles) {
					double gap = Math.hypot(i.x() - j.x(), i.z() - j.z()) - i.radius() * 1.14 - j.radius() * 1.14;
					if (i != j && gap < 2 && s != Sphere.EMPYREAN) {
						System.out.println("OVERLAP in " + s.id + ": " + i.kind() + " and " + j.kind() + " (gap " + gap + ")");
					}
				}
			}
		}
	}
}
