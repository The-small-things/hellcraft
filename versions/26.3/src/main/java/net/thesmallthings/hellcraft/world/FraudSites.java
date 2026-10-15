package net.thesmallthings.hellcraft.world;


import java.util.ArrayList;
import java.util.List;

/**
 * Where Malebolge keeps its lies (L-VIII, "nothing is what it seems"): caches on the ridges between the ditches (a
 * chest each, built by MalebolgeCacheFeature; one in three is a mimic instead, which the server brings out), and a
 * fake Virgil's Rest beside each of the bridges the roads don't take (Frauds builds it and springs its trap). The
 * same for worldgen and the server.
 */
public final class FraudSites {
	private FraudSites() {
	}

	/** Caches stand about this far apart along each ridge (blocks of arc). */
	private static final double CACHE_SPACING = 56;

	/** A cache on a ridge: a chest, or a mimic waiting where a chest should be. */
	public record Cache(String id, double x, double z, boolean mimic, int facing) {
	}

	/** A fake Rest beside a bridge. */
	public record FakeRest(String id, int spoke, int x, int z) {
	}

	private static List<Cache> caches;
	private static List<FakeRest> fakes;

	/** The middle of ridge k (1..9): the walls between the ten ditches, outermost first. */
	public static double ridgeRadius(int k) {
		double outer = InfernoGeometry.outerRadius(Zone.MALEBOLGE);
		double inner = InfernoGeometry.outerRadius(Zone.WELL_OF_GIANTS);
		return outer - k * (outer - inner) / InfernoGeometry.MALEBOLGE_DITCHES;
	}

	/** The real radius at this angle whose wobbled radius is `wanted` (so a site sits on the ridge as drawn). */
	static double onRidge(double angle, double wanted) {
		double r = wanted;
		for (int i = 0; i < 4; i++) {
			r += wanted - InfernoGeometry.effectiveRadius(Math.cos(angle) * r, Math.sin(angle) * r);
		}
		return r;
	}

	public static synchronized List<Cache> caches() {
		if (caches != null) {
			return caches;
		}
		List<Cache> out = new ArrayList<>();
		for (int k = 1; k < InfernoGeometry.MALEBOLGE_DITCHES; k++) {
			double ridge = ridgeRadius(k);
			int n = (int) Math.floor(2 * Math.PI * ridge / CACHE_SPACING);
			for (int i = 0; i < n; i++) {
				long h = hash(k, i);
				double a = InfernoGeometry.normalizeAngle((i + 0.15 + 0.7 * ((h & 0xFFFF) / 65535.0)) * 2 * Math.PI / n);
				double r = onRidge(a, ridge);
				double x = Math.cos(a) * r;
				double z = Math.sin(a) * r;
				if (nearBridge(a, r) || InfernoGeometry.roadFactor(x, z) > 0 || nearRoad(x, z)) {
					continue;
				}
				out.add(new Cache("c" + k + "_" + i, x, z, Math.floorMod(h >> 20, 3) == 0, (int) Math.floorMod(h >> 24, 4)));
			}
		}
		caches = out;
		return caches;
	}

	public static synchronized List<FakeRest> fakeRests() {
		if (fakes != null) {
			return fakes;
		}
		List<FakeRest> out = new ArrayList<>();
		for (int s = 0; s < InfernoGeometry.MALEBOLGE_SPOKES; s++) {
			if (s % 3 == 0) {
				continue;
			}
			// beside the bridge, on a ridge part of the way down: a welcome sight for a weary soul
			double ridge = ridgeRadius(s % 2 == 0 ? 3 : 6);
			double a = InfernoGeometry.spokeAngle(s) + 14.0 / ridge;
			double r = onRidge(a, ridge);
			out.add(new FakeRest("f" + s, s, (int) Math.floor(Math.cos(a) * r), (int) Math.floor(Math.sin(a) * r)));
		}
		fakes = out;
		return fakes;
	}

	public static List<Cache> cachesNear(double x, double z, double range) {
		List<Cache> out = new ArrayList<>();
		for (Cache c : caches()) {
			if (Math.abs(c.x() - x) <= range && Math.abs(c.z() - z) <= range) {
				out.add(c);
			}
		}
		return out;
	}

	private static boolean nearBridge(double a, double r) {
		for (int s = 0; s < InfernoGeometry.MALEBOLGE_SPOKES; s++) {
			if (InfernoGeometry.angularDistance(a, InfernoGeometry.spokeAngle(s)) * r < 24) {
				return true;
			}
		}
		return false;
	}

	private static boolean nearRoad(double x, double z) {
		for (int dx = -8; dx <= 8; dx += 4) {
			for (int dz = -8; dz <= 8; dz += 4) {
				if (InfernoGeometry.onRoad(x + dx, z + dz)) {
					return true;
				}
			}
		}
		return false;
	}

	private static long hash(int a, int b) {
		long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ 0xF4A0DL;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return h & Long.MAX_VALUE;
	}
}
