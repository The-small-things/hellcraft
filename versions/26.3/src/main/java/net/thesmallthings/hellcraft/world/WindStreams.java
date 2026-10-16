package net.thesmallthings.hellcraft.world;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Lust's rule (L-II): the wind is a road. The infernal hurricane blows in thirty-six streams (three between each pair
 * of ways down) that spiral in across the Storm Canyons, each from a pad somewhere in the circle's outer half, up over
 * the spires, and down onto a pad by the next way down. Step into one and it carries you (client/layer/LayerFx moves you; LayerRules keeps the server happy);
 * sneak to let go and you glide down. Both sides compute the same streams from the geometry, so nothing is sent.
 */
public final class WindStreams {
	private WindStreams() {
	}

	/** How fast a stream carries you, in blocks a tick. */
	public static final double SPEED = 0.8;
	/** How close to a stream's heart you must be for it to take you. */
	public static final double RADIUS = 1.8;
	/** How high over the ground a stream cruises (clear of the spires). */
	private static final double CRUISE = 30.0;
	/** Over how many blocks of its path a stream climbs from its pad to its cruise (an updraft), and comes down at the end. */
	private static final double RISE = 14.0;
	private static final double FALL = 26.0;
	private static final double STEP = 2.0;
	public static final int COUNT = 36;
	/** Streams between each pair of ways down. */
	private static final int PER_WAY = 3;
	/** The ground around each pad is level (InfernoGeometry.roughness): a pad, then a fade. */
	private static final double PAD = 7.0;
	private static final double PAD_FADE = 12.0;

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

	private static List<Stream> streams;

	public static synchronized List<Stream> all() {
		if (streams == null) {
			List<Stream> out = new ArrayList<>();
			for (int k = 0; k < COUNT; k++) {
				out.add(build(k));
			}
			streams = List.copyOf(out);
		}
		return streams;
	}

	/** The outermost a stream starts. */
	private static double startRadius() {
		return InfernoGeometry.outerRadius(Zone.LUST) - 20;
	}

	/** Where stream k starts: the first of each three at the circle's outer edge, the others further in. */
	private static double startRadius(int k) {
		return startRadius() - 55.0 * (k % PER_WAY);
	}

	private static double endRadius() {
		return InfernoGeometry.outerRadius(Zone.GLUTTONY) + 25;
	}

	/** The three streams after way down w start spread across the gap and curl round to land by way down w + 1. */
	private static double startAngle(int k) {
		return InfernoGeometry.wayDownAngle(k / PER_WAY) + InfernoGeometry.WAY_DOWN_STEP * (0.3 + 0.2 * (k % PER_WAY));
	}

	private static double endAngle(int k) {
		return InfernoGeometry.wayDownAngle(k / PER_WAY) + InfernoGeometry.WAY_DOWN_STEP * (0.88 + 0.12 * (k % PER_WAY));
	}

	private static Stream build(int k) {
		double r0 = startRadius(k);
		double r1 = endRadius();
		double a0 = startAngle(k);
		double a1 = endAngle(k);
		// a fine pass to measure it, then points STEP apart along it
		int fine = 2000;
		double[] fx = new double[fine + 1];
		double[] fz = new double[fine + 1];
		double[] fs = new double[fine + 1];
		for (int i = 0; i <= fine; i++) {
			double t = i / (double) fine;
			double r = r0 + (r1 - r0) * t;
			double a = a0 + (a1 - a0) * t;
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
			y[i] = InfernoGeometry.baseHeight(x[i], z[i]) + 1.5 + (CRUISE - 1.5) * up;
		}
		return new Stream(k, x, y, z, a0, a1);
	}

	private static double smooth(double t) {
		t = Math.max(0, Math.min(1, t));
		return t * t * (3 - 2 * t);
	}

	/** The stream point within RADIUS of a position, or null. */
	@Nullable
	public static Hit find(double x, double y, double z) {
		double r = Math.sqrt(x * x + z * z);
		if (r > startRadius() + RADIUS + 2 || r < endRadius() - RADIUS - 2) {
			return null;
		}
		double theta = Math.atan2(z, x);
		Hit best = null;
		for (Stream s : all()) {
			double mid = (s.fromAngle() + s.toAngle()) / 2;
			double half = (s.toAngle() - s.fromAngle()) / 2 + 0.01;
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

	/** The pads' xz positions: the start and end of every stream. */
	public static double[][] pads() {
		double[][] out = new double[COUNT * 2][];
		for (int k = 0; k < COUNT; k++) {
			out[2 * k] = new double[]{Math.cos(startAngle(k)) * startRadius(k), Math.sin(startAngle(k)) * startRadius(k)};
			out[2 * k + 1] = new double[]{Math.cos(endAngle(k)) * endRadius(), Math.sin(endAngle(k)) * endRadius()};
		}
		return out;
	}

	private static final double[][] PAD_CACHE = pads();

	/** 1 on a pad, fading to 0 around it: the ground where streams start and land is level. */
	static double padLevelling(double x, double z) {
		double r = Math.sqrt(x * x + z * z);
		if (r > InfernoGeometry.outerRadius(Zone.LUST) || r < InfernoGeometry.outerRadius(Zone.GLUTTONY)) {
			return 0;
		}
		double best = 0;
		for (double[] p : PAD_CACHE) {
			double d = Math.hypot(x - p[0], z - p[1]);
			if (d < PAD + PAD_FADE) {
				best = Math.max(best, smooth(1 - (d - PAD) / PAD_FADE));
			}
		}
		return best;
	}
}
