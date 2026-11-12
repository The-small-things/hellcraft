package net.thesmallthings.hellcraft.world;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Lust's rule (L-II): the wind is a road. The infernal hurricane blows in streams that spiral in across the Storm
 * Canyons, each from a pad somewhere in the circle's outer part, up over the spires, and down onto a pad by a way down.
 * Step into one and it carries you (client/layer/LayerFx moves you; LayerRules keeps the server happy); sneak to let go
 * and you glide down.
 *
 * <p>Every world made since 3.4 has its own streams, drawn from its seed (six between each pair of ways down, each
 * starting, bending and landing where its own dice say); worlds made before keep the thirty-six streams they were
 * generated with ({@link #LEGACY}: three between each pair of ways down). The server picks the layout when the
 * overworld loads ({@code HellState.windSeed}, before any of Lust is generated, since the pads level the ground) and
 * tells each client ({@code net/WindPayload}); both then compute the same streams from the geometry.
 */
public final class WindStreams {
	private WindStreams() {
	}

	/** How fast a stream carries you, in blocks a tick. */
	public static final double SPEED = 0.8;
	/** How close to a stream's heart you must be for it to take you. */
	public static final double RADIUS = 1.8;
	/** The layout of worlds made before 3.4: thirty-six fixed streams. */
	public static final long LEGACY = 0L;
	/** Streams in a world made since 3.4 (some may be dropped where no pad fits; in practice none are). */
	public static final int COUNT = 72;
	/** Streams in a world made before. */
	public static final int LEGACY_COUNT = 36;
	/** Over how many blocks of its path a stream climbs from its pad to its cruise (an updraft), and comes down at the end. */
	private static final double RISE = 14.0;
	private static final double FALL = 26.0;
	private static final double STEP = 2.0;
	/** Streams between each pair of ways down. */
	private static final int PER_WAY = COUNT / 12;
	private static final int LEGACY_PER_WAY = LEGACY_COUNT / 12;
	/** The ground around each pad is level (InfernoGeometry.roughness): a pad, then a fade. */
	private static final double PAD = 7.0;
	private static final double PAD_FADE = 12.0;
	/** How far apart two pads keep (their level ground doesn't run together). */
	private static final double PAD_SPACING = 2 * PAD + 6;

	/** A stream as points along its heart, STEP blocks apart, from its start pad to its end pad. */
	public record Stream(int index, double[] x, double[] y, double[] z, double fromAngle, double toAngle) {
		public int size() {
			return x.length;
		}
	}

	/** The point of a stream nearest to someone, and how far they are from it. */
	public record Hit(Stream stream, int index, double distance) {
		public double x() {
			return stream.x()[index];
		}

		public double y() {
			return stream.y()[index];
		}

		public double z() {
			return stream.z()[index];
		}

		/** The way the stream blows here (a unit vector). */
		public double[] direction() {
			int a = Math.max(0, index - 1);
			int b = Math.min(stream.size() - 1, index + 1);
			double dx = stream.x()[b] - stream.x()[a];
			double dy = stream.y()[b] - stream.y()[a];
			double dz = stream.z()[b] - stream.z()[a];
			double len = Math.max(1e-6, Math.sqrt(dx * dx + dy * dy + dz * dz));
			return new double[]{dx / len, dy / len, dz / len};
		}

		/** True at the stream's last stretch: it sets you down here. */
		public boolean atEnd() {
			return index >= stream.size() - 2;
		}
	}

	/**
	 * A stream's plan: it starts at (r0, a0) and lands at (r1, a1) (radius, angle), swinging out and in by {@code bend}
	 * blocks over {@code waves} half-waves, turning at a pace eased by {@code ease} (1 = even), and cruising
	 * {@code cruise} blocks over the ground.
	 */
	record Plan(double r0, double a0, double r1, double a1, double bend, int waves, double ease, double cruise) {
		double radius(double t) {
			return r0 + (r1 - r0) * t + bend * Math.sin(Math.PI * t * waves);
		}

		double angle(double t) {
			return a0 + (a1 - a0) * Math.pow(t, ease);
		}
	}

	/** One world's streams: the plans and pads at once (cheap), the streams' points the first time they're needed. */
	private static final class Layout {
		final long seed;
		final List<Plan> plans;
		final double[][] pads;
		final double minRadius;
		final double maxRadius;
		private volatile List<Stream> streams;

		Layout(long seed) {
			this.seed = seed;
			this.plans = seed == LEGACY ? legacyPlans() : plans(seed);
			this.pads = padsOf(plans);
			double lo = Double.MAX_VALUE;
			double hi = 0;
			for (Plan p : plans) {
				for (int i = 0; i <= 64; i++) {
					double r = p.radius(i / 64.0);
					lo = Math.min(lo, r);
					hi = Math.max(hi, r);
				}
			}
			this.minRadius = lo;
			this.maxRadius = hi;
		}

		List<Stream> streams() {
			List<Stream> s = streams;
			if (s == null) {
				synchronized (this) {
					s = streams;
					if (s == null) {
						List<Stream> out = new ArrayList<>();
						for (int k = 0; k < plans.size(); k++) {
							out.add(build(k, plans.get(k)));
						}
						s = streams = List.copyOf(out);
					}
				}
			}
			return s;
		}
	}

	private static volatile Layout layout;

	private static Layout layout() {
		Layout l = layout;
		if (l == null) {
			synchronized (WindStreams.class) {
				l = layout;
				if (l == null) {
					l = layout = new Layout(LEGACY);
				}
			}
		}
		return l;
	}

	/** Switches to the streams of the world with this wind seed (the server on loading a world, a client on joining). */
	public static synchronized void use(long seed) {
		if (layout().seed != seed) {
			layout = new Layout(seed);
		}
	}

	/** The wind seed in use. */
	public static long seed() {
		return layout().seed;
	}

	/** A new world's wind seed, from its world seed (never {@link #LEGACY}). */
	public static long seedFor(long worldSeed) {
		long h = worldSeed * 0x9E3779B97F4A7C15L + 0x5851F42D4C957F2DL;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return h == LEGACY ? 1L : h;
	}

	public static List<Stream> all() {
		return layout().streams();
	}

	/** The pads' xz positions: the start and end of every stream. */
	public static double[][] pads() {
		return layout().pads;
	}

	/** Where the pads of worlds made before 3.4 lie (the secret ways were placed clear of them, and stay put). */
	public static double[][] legacyPads() {
		return LegacyPads.PADS;
	}

	private static final class LegacyPads {
		static final double[][] PADS = padsOf(legacyPlans());
	}

	private static double[][] padsOf(List<Plan> plans) {
		double[][] out = new double[plans.size() * 2][];
		for (int k = 0; k < plans.size(); k++) {
			Plan p = plans.get(k);
			out[2 * k] = new double[]{Math.cos(p.a0()) * p.r0(), Math.sin(p.a0()) * p.r0()};
			out[2 * k + 1] = new double[]{Math.cos(p.a1()) * p.r1(), Math.sin(p.a1()) * p.r1()};
		}
		return out;
	}

	// ------------------------------------------------------------------ the layouts

	private static double lustOuter() {
		return InfernoGeometry.outerRadius(Zone.LUST);
	}

	private static double lustInner() {
		return InfernoGeometry.outerRadius(Zone.GLUTTONY);
	}

	/**
	 * The thirty-six streams of worlds made before 3.4: the three after way down w start spread across the gap (the
	 * first at the circle's outer edge, the others further in) and curl round to land by way down w + 1.
	 */
	private static List<Plan> legacyPlans() {
		List<Plan> out = new ArrayList<>();
		double step = InfernoGeometry.WAY_DOWN_STEP;
		for (int k = 0; k < LEGACY_COUNT; k++) {
			double way = InfernoGeometry.wayDownAngle(k / LEGACY_PER_WAY);
			int i = k % LEGACY_PER_WAY;
			out.add(new Plan(lustOuter() - 20 - 55.0 * i, way + step * (0.3 + 0.2 * i),
					lustInner() + 25, way + step * (0.88 + 0.12 * i), 0, 1, 1, 30.0));
		}
		return out;
	}

	/**
	 * A world's own streams: six in each gap between ways down, each from a pad anywhere in the outer three fifths of the
	 * circle to a pad by one of the gap's two ways down, bending and climbing as its dice fall. A pad keeps off the
	 * roads, the lairs, the Rests, the secret ways and the other pads; a stream whose pads find no room in 40 throws is
	 * left out.
	 */
	static List<Plan> plans(long seed) {
		List<Plan> out = new ArrayList<>();
		List<double[]> taken = new ArrayList<>();
		double step = InfernoGeometry.WAY_DOWN_STEP;
		double width = lustOuter() - lustInner();
		for (int w = 0; w < 12; w++) {
			double way = InfernoGeometry.wayDownAngle(w);
			for (int i = 0; i < PER_WAY; i++) {
				Random rng = new Random(seed ^ (0x51ED2705L * (w * PER_WAY + i + 1)));
				// half of each gap's streams land by its first way down, half by its second
				boolean forward = i % 2 == 0;
				for (int attempt = 0; attempt < 40; attempt++) {
					double r0 = lustOuter() - 20 - rng.nextDouble() * width * 0.6;
					double a0 = way + step * (0.12 + 0.76 * rng.nextDouble());
					double r1 = lustInner() + 22 + rng.nextDouble() * 26;
					double a1 = (forward ? way + step : way) + step * (forward ? -1 : 1) * (0.02 + 0.16 * rng.nextDouble());
					double bend = (rng.nextDouble() * 2 - 1) * 40;
					int waves = 1 + rng.nextInt(2);
					// it sweeps round first and spirals in toward its pad (an ease over 1 would hug the cliff)
					double ease = 0.6 + rng.nextDouble() * 0.5;
					double cruise = 28 + rng.nextDouble() * 12;
					Plan p = new Plan(r0, a0, r1, a1, bend, waves, ease, cruise);
					if (!fits(p, width) || !padFree(p.r0(), p.a0(), taken) || !padFree(p.r1(), p.a1(), taken)) {
						continue;
					}
					taken.add(new double[]{Math.cos(a0) * r0, Math.sin(a0) * r0});
					taken.add(new double[]{Math.cos(a1) * r1, Math.sin(a1) * r1});
					out.add(p);
					break;
				}
			}
		}
		return out;
	}

	/** The stream stays inside the circle all the way (its bends don't carry it over the rim or the cliff). */
	private static boolean fits(Plan p, double width) {
		for (int i = 0; i <= 32; i++) {
			double r = p.radius(i / 32.0);
			if (r > lustOuter() - 12 || r < lustInner() + 18) {
				return false;
			}
		}
		// long enough to be worth riding
		return Math.abs(p.a1() - p.a0()) * lustInner() > width * 0.5;
	}

	private static boolean padFree(double r, double a, List<double[]> taken) {
		double x = Math.cos(a) * r;
		double z = Math.sin(a) * r;
		if (InfernoGeometry.roadLevel(x, z) > 0 || InfernoGeometry.nearLair(x, z, InfernoGeometry.LAIR_CLEARANCE + PAD + PAD_FADE)) {
			return false;
		}
		if (SecretWays.near(x, z, PAD + PAD_FADE + 4)) {
			return false;
		}
		for (ShrineSites.Site s : ShrineSites.all()) {
			if (Math.hypot(s.x() - x, s.z() - z) < PAD + PAD_FADE + 16) {
				return false;
			}
		}
		for (double[] t : taken) {
			if (Math.hypot(t[0] - x, t[1] - z) < PAD_SPACING) {
				return false;
			}
		}
		return true;
	}

	private static Stream build(int k, Plan p) {
		// a fine pass to measure it, then points STEP apart along it
		int fine = 2000;
		double[] fx = new double[fine + 1];
		double[] fz = new double[fine + 1];
		double[] fs = new double[fine + 1];
		for (int i = 0; i <= fine; i++) {
			double t = i / (double) fine;
			double r = p.radius(t);
			double a = p.angle(t);
			fx[i] = Math.cos(a) * r;
			fz[i] = Math.sin(a) * r;
			if (i > 0) {
				fs[i] = fs[i - 1] + Math.hypot(fx[i] - fx[i - 1], fz[i] - fz[i - 1]);
			}
		}
		double length = fs[fine];
		int n = (int) Math.ceil(length / STEP) + 1;
		double[] x = new double[n];
		double[] y = new double[n];
		double[] z = new double[n];
		int j = 0;
		for (int i = 0; i < n; i++) {
			double s = Math.min(length, i * STEP);
			while (j < fine && fs[j + 1] < s) {
				j++;
			}
			double u = fs[j + 1] - fs[j] < 1e-9 ? 0 : (s - fs[j]) / (fs[j + 1] - fs[j]);
			x[i] = fx[j] + (fx[j + 1] - fx[j]) * u;
			z[i] = fz[j] + (fz[j + 1] - fz[j]) * u;
			double up = Math.min(smooth(s / RISE), smooth((length - s) / FALL));
			y[i] = InfernoGeometry.baseHeight(x[i], z[i]) + 1.5 + (p.cruise() - 1.5) * up;
		}
		return new Stream(k, x, y, z, p.a0(), p.a1());
	}

	private static double smooth(double t) {
		t = Math.max(0, Math.min(1, t));
		return t * t * (3 - 2 * t);
	}

	/** The stream point within RADIUS of a position, or null. */
	@Nullable
	public static Hit find(double x, double y, double z) {
		Layout l = layout();
		double r = Math.sqrt(x * x + z * z);
		if (r > l.maxRadius + RADIUS + 2 || r < l.minRadius - RADIUS - 2) {
			return null;
		}
		double theta = Math.atan2(z, x);
		Hit best = null;
		for (Stream s : l.streams()) {
			double mid = (s.fromAngle() + s.toAngle()) / 2;
			double half = Math.abs(s.toAngle() - s.fromAngle()) / 2 + 0.01;
			if (InfernoGeometry.angularDistance(theta, mid) > half) {
				continue;
			}
			for (int i = 0; i < s.size(); i++) {
				double dx = s.x()[i] - x;
				double dz = s.z()[i] - z;
				if (Math.abs(dx) > RADIUS + STEP || Math.abs(dz) > RADIUS + STEP) {
					continue;
				}
				double dy = s.y()[i] - y;
				double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
				if (d <= RADIUS && (best == null || d < best.distance())) {
					best = new Hit(s, i, d);
				}
			}
		}
		return best;
	}

	/** 1 on a pad, fading to 0 around it: the ground where streams start and land is level. */
	static double padLevelling(double x, double z) {
		double r = Math.sqrt(x * x + z * z);
		if (r > lustOuter() || r < lustInner()) {
			return 0;
		}
		double best = 0;
		for (double[] p : layout().pads) {
			double dx = x - p[0];
			double dz = z - p[1];
			if (Math.abs(dx) > PAD + PAD_FADE || Math.abs(dz) > PAD + PAD_FADE) {
				continue;
			}
			double d = Math.sqrt(dx * dx + dz * dz);
			if (d < PAD + PAD_FADE) {
				best = Math.max(best, smooth(1 - (d - PAD) / PAD_FADE));
			}
		}
		return best;
	}
}
