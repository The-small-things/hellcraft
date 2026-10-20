package net.thesmallthings.hellcraft.world;


import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The secret ways down: three hidden shortcuts in each circle from Limbo to Malebolge, each a landmark of its circle
 * (a ruined well, a ring of standing stones, a sinkhole, a vault's trapdoor, a whirlpool, an open tomb, a hollow tree, a
 * sand funnel, the old man of Crete's head) well away from the roads, the ramps, the lairs, the Rests and the other
 * set-pieces. Walk into one's mouth (or right-click it) and it carries you down to the next circle, to a small cairn at the
 * same angle round Hell (SecretWayLift). Pure geometry (no world access), so worldgen (SecretWayFeature), the server and
 * the client's Map of Hell all agree on where they are; the ground round each mouth is level
 * ({@link #levelling}).
 */
public final class SecretWays {
	private SecretWays() {
	}

	/** How each circle's secret looks. {@code radius}: how far from the mouth's middle it is built (and the ground levelled). */
	public enum Kind {
		WELL("A ruined well", "Jump into the well.", 5),
		STONES("A ring of standing stones", "Step into the hole in the middle of the ring.", 8),
		SINKHOLE("A sinkhole in the mud", "Slide down into the hole at its bottom.", 6),
		VAULT("A vault's open trapdoor", "Drop through the open trapdoor.", 6),
		WHIRLPOOL("A whirlpool in the marsh", "Swim into the middle and let it pull you under.", 7),
		TOMB("An open tomb with steps going down", "Walk down its steps.", 5),
		HOLLOW_TREE("A hollow tree", "Step inside the trunk.", 6),
		FUNNEL("A funnel in the sand", "Slide down into the hole at its bottom.", 7),
		HEAD("The old man of Crete's head", "Walk into his open mouth.", 7);

		public final String title;
		public final String how;
		public final int radius;

		Kind(String title, String how, int radius) {
			this.title = title;
			this.how = how;
			this.radius = radius;
		}

		/** True on the columns of its mouth, in the site's own frame (f toward its front, s to its right). */
		public boolean mouth(int f, int s) {
			return switch (this) {
				case TOMB -> f >= -2 && f <= -1 && Math.abs(s) <= 1;
				case HEAD -> f >= -2 && f <= 0 && Math.abs(s) <= 1;
				default -> Math.abs(f) <= 1 && Math.abs(s) <= 1;
			};
		}

		/** The middle of its mouth, as f in the site's frame. */
		public int mouthF() {
			return this == TOMB || this == HEAD ? -1 : 0;
		}
	}

	/**
	 * A secret way: from (x, z) in {@code zone} to (landX, landZ) in {@code to}. {@code y} is the top of the level ground
	 * at its mouth; (fx, fz) is the way it faces (outward, toward where pilgrims come from: the tomb's steps, the tree's
	 * door, the old man's face).
	 */
	public record Site(String id, int circle, Zone zone, Zone to, Kind kind, int x, int z, int y, int landX, int landZ, int fx, int fz) {
		/** The circle it is in, as players know it ("Greed"). */
		public String from() {
			return NAMES[circle][0];
		}

		/** Where it leads ("the Marsh of Styx (Wrath)"). */
		public String place() {
			return NAMES[circle][1];
		}

		/** World column of the site-frame offset (f forward, s to the right). */
		public int worldX(int f, int s) {
			return x + f * fx - s * fz;
		}

		public int worldZ(int f, int s) {
			return z + f * fz + s * fx;
		}

		/** The site-frame f of a world column. */
		public int frameF(int wx, int wz) {
			return (wx - x) * fx + (wz - z) * fz;
		}

		public int frameS(int wx, int wz) {
			return -(wx - x) * fz + (wz - z) * fx;
		}

		/** True on a column of its mouth. */
		public boolean mouthAt(int wx, int wz) {
			return kind.mouth(frameF(wx, wz), frameS(wx, wz));
		}
	}

	/** Each circle with secrets: where they are, where they lead, what they look like. */
	private record Entry(Zone zone, Zone to, Kind kind) {
	}

	private static final Entry[] ENTRIES = {
			new Entry(Zone.LIMBO, Zone.LUST, Kind.WELL),
			new Entry(Zone.LUST, Zone.GLUTTONY, Kind.STONES),
			new Entry(Zone.GLUTTONY, Zone.GREED, Kind.SINKHOLE),
			new Entry(Zone.GREED, Zone.STYX, Kind.VAULT),
			new Entry(Zone.STYX, Zone.HERESY, Kind.WHIRLPOOL),
			new Entry(Zone.HERESY, Zone.WOOD_OF_SUICIDES, Kind.TOMB),
			new Entry(Zone.WOOD_OF_SUICIDES, Zone.BURNING_SANDS, Kind.HOLLOW_TREE),
			new Entry(Zone.BURNING_SANDS, Zone.MALEBOLGE, Kind.FUNNEL),
			new Entry(Zone.MALEBOLGE, Zone.COCYTUS, Kind.HEAD),
	};

	/** {from, to} as players know them, by entry. */
	private static final String[][] NAMES = {
			{"Limbo", "Lust"}, {"Lust", "Gluttony"}, {"Gluttony", "Greed"}, {"Greed", "the Marsh of Styx (Wrath)"},
			{"the Marsh of Styx", "Heresy, inside the Walls of Dis"}, {"Heresy", "the Wood of the Suicides"},
			{"the Wood of the Suicides", "the Burning Sands"}, {"the Burning Sands", "Malebolge"}, {"Malebolge", "Cocytus, the frozen lake"},
	};

	/** Secrets in each circle. */
	public static final int PER_CIRCLE = 3;
	/** A landing's cairn is 5 across; it keeps this far (plus a margin) from everything else. */
	public static final int LANDING_RADIUS = 3;
	private static final double FADE = 8.0;

	/** Worked out once, the first time they are needed (after the rest of this class is set up). */
	private static final class Holder {
		static final List<Site> SITES = compute();
	}

	public static List<Site> all() {
		return Holder.SITES;
	}

	public static int count() {
		return all().size();
	}

	public static Site byId(String id) {
		for (Site s : all()) {
			if (s.id().equals(id)) {
				return s;
			}
		}
		return null;
	}

	/** True within {@code within} blocks of a secret's mouth or its landing (Heresy's tombs keep clear of both). */
	public static boolean near(double x, double z, double within) {
		for (Site s : all()) {
			double reach = within + s.kind().radius;
			if (Math.abs(s.x() - x) < reach && Math.abs(s.z() - z) < reach && Math.hypot(s.x() - x, s.z() - z) < reach) {
				return true;
			}
			double landReach = within + LANDING_RADIUS;
			if (Math.abs(s.landX() - x) < landReach && Math.abs(s.landZ() - z) < landReach && Math.hypot(s.landX() - x, s.landZ() - z) < landReach) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 1 at a secret's mouth, fading to 0 round it: the ground there is level (InfernoGeometry.roughness). Landings aren't
	 * levelled: their cairns follow the ground.
	 */
	static double levelling(double x, double z) {
		double best = 0;
		for (Site s : all()) {
			best = Math.max(best, disc(x - s.x(), z - s.z(), s.kind().radius + 1));
		}
		return best;
	}

	private static double disc(double dx, double dz, double flat) {
		if (Math.abs(dx) > flat + FADE || Math.abs(dz) > flat + FADE) {
			return 0;
		}
		double d = Math.sqrt(dx * dx + dz * dz);
		return InfernoGeometry.smoothstep(InfernoGeometry.clamp(1.0 - (d - flat) / FADE, 0.0, 1.0));
	}

	// ------------------------------------------------------------------ placing them

	private static List<Site> compute() {
		List<Site> out = new ArrayList<>();
		for (int n = 0; n < ENTRIES.length; n++) {
			Entry e = ENTRIES[n];
			int first = (int) Math.floorMod(Math.round(InfernoGeometry.hash01(n, 17, 9241) * 1000), 12);
			for (int k = 0; k < PER_CIRCLE; k++) {
				Site site = null;
				// one in each third of Hell, between two ways down; if that gap has no room, the next gap round
				for (int shift = 0; shift < 4 && site == null; shift++) {
					int slot = Math.floorMod(first + k * 4 + shift, 12);
					site = place(n, k, e, slot, out);
				}
				if (site == null) {
					throw new IllegalStateException("No room for secret way " + k + " of " + e.zone().id());
				}
				out.add(site);
			}
		}
		return List.copyOf(out);
	}

	private static final double[] JITTER = {0, 3, -3, 6, -6};
	private static final double[] FRACTIONS = {0.5, 0.42, 0.58, 0.35, 0.65, 0.3, 0.7};

	private static Site place(int n, int k, Entry e, int slot, List<Site> taken) {
		double outer = InfernoGeometry.outerRadius(e.zone());
		double inner = InfernoGeometry.bandFor(e.zone()).inner();
		int radius = e.kind().radius;
		for (double jitter : JITTER) {
			double a = InfernoGeometry.normalizeAngle(Math.toRadians(slot * 30 + 15 + jitter));
			for (double[] at : mouths(e, a, outer, inner)) {
				double x = at[0];
				double z = at[1];
				int ix = (int) Math.round(x);
				int iz = (int) Math.round(z);
				if (!mouthOk(e, ix, iz, radius, taken)) {
					continue;
				}
				int[] land = landing(e.to(), a);
				if (land == null) {
					continue;
				}
				// it faces outward, toward the pilgrims coming down: along whichever axis is nearer
				int fx = Math.abs(Math.cos(a)) >= Math.abs(Math.sin(a)) ? (int) Math.signum(Math.cos(a)) : 0;
				int fz = fx == 0 ? (int) Math.signum(Math.sin(a)) : 0;
				int y = (int) Math.floor(InfernoGeometry.baseHeight(ix, iz));
				String id = e.zone().id() + "_" + (k + 1);
				return new Site(id, n, e.zone(), e.to(), e.kind(), ix, iz, y, land[0], land[1], fx, fz);
			}
		}
		return null;
	}

	/** Candidate mouths along an angle: across the middle of the circle; in Malebolge, the middle of a ditch's floor. */
	private static List<double[]> mouths(Entry e, double a, double outer, double inner) {
		List<double[]> out = new ArrayList<>();
		if (e.zone() == Zone.MALEBOLGE) {
			double width = (outer - inner) / InfernoGeometry.MALEBOLGE_DITCHES;
			for (int ditch : new int[]{4, 5, 3, 6, 2, 7, 1, 8}) {
				double r = FraudSites.onRidge(a, outer - (ditch + 0.5) * width);
				out.add(new double[]{Math.cos(a) * r, Math.sin(a) * r});
			}
			return out;
		}
		for (double f : FRACTIONS) {
			double r = FraudSites.onRidge(a, inner + (outer - inner) * f);
			out.add(new double[]{Math.cos(a) * r, Math.sin(a) * r});
		}
		return out;
	}

	private static boolean mouthOk(Entry e, int x, int z, int radius, List<Site> taken) {
		double re = InfernoGeometry.effectiveRadius(x, z);
		double outer = InfernoGeometry.outerRadius(e.zone());
		double inner = InfernoGeometry.bandFor(e.zone()).inner();
		if (re > outer - 40 || re < inner + 50) {
			return false;
		}
		double theta = Math.atan2(z, x);
		double[][] probes = {{0, 0}, {radius, 0}, {-radius, 0}, {0, radius}, {0, -radius}};
		for (double[] p : probes) {
			double px = x + p[0];
			double pz = z + p[1];
			Zone zone = InfernoGeometry.zoneAt(px, pz);
			if (e.zone() == Zone.MALEBOLGE) {
				// on a ditch's floor, all of it
				double pr = InfernoGeometry.effectiveRadius(px, pz);
				if (zone.circle() != Circle.FRAUD || InfernoGeometry.ditchTrough(pr, Math.atan2(pz, px)) < 0.95) {
					return false;
				}
			} else if (zone != e.zone()) {
				return false;
			}
			boolean wet = InfernoGeometry.fluidAt(px, pz) != InfernoGeometry.Fluid.NONE;
			if (wet != (e.kind() == Kind.WHIRLPOOL)) {
				return false;
			}
		}
		return clear(x, z, radius, 50, theta) && !tooClose(x, z, taken);
	}

	/** Clear of the roads, the ramps, the lairs, the Rests and the other set-pieces worked out from the geometry. */
	private static boolean clear(double x, double z, int radius, double rampClearance, double theta) {
		double r = Math.hypot(x, z);
		for (int k = 0; k < 12; k++) {
			if (InfernoGeometry.angularDistance(theta, InfernoGeometry.wayDownAngle(k)) * r < radius + rampClearance) {
				return false;
			}
		}
		int reach = radius + 8;
		for (int dx = -reach; dx <= reach; dx += 3) {
			for (int dz = -reach; dz <= reach; dz += 3) {
				if (InfernoGeometry.roadFactor(x + dx, z + dz) > 0) {
					return false;
				}
			}
		}
		if (InfernoGeometry.nearLair(x, z, radius + 60)) {
			return false;
		}
		for (ShrineSites.Site s : ShrineSites.all()) {
			if (Math.hypot(s.x() - x, s.z() - z) < radius + 40) {
				return false;
			}
		}
		if (NobleCastle.distance(x, z) < NobleCastle.GREEN + radius + 30) {
			return false;
		}
		for (double[] pad : WindStreams.pads()) {
			if (Math.hypot(pad[0] - x, pad[1] - z) < radius + 24) {
				return false;
			}
		}
		for (FraudSites.Cache c : FraudSites.caches()) {
			if (Math.hypot(c.x() - x, c.z() - z) < radius + 16) {
				return false;
			}
		}
		for (FraudSites.FakeRest f : FraudSites.fakeRests()) {
			if (Math.hypot(f.x() - x, f.z() - z) < radius + 24) {
				return false;
			}
		}
		// the chained giants in the Well, and Antaeus's cleft at its foot
		for (double a : new double[]{InfernoGeometry.wayDownAngle(2) + InfernoGeometry.WAY_DOWN_STEP / 2,
				InfernoGeometry.wayDownAngle(6) + InfernoGeometry.WAY_DOWN_STEP / 2}) {
			double gx = Math.cos(a) * InfernoGeometry.GIANT_RADIUS;
			double gz = Math.sin(a) * InfernoGeometry.GIANT_RADIUS;
			if (Math.hypot(gx - x, gz - z) < radius + 40) {
				return false;
			}
		}
		double antaeus = InfernoGeometry.wayDownAngle(10) + InfernoGeometry.WAY_DOWN_STEP / 2;
		return InfernoGeometry.angularDistance(theta, antaeus) * r > radius + 60 || r > InfernoGeometry.outerRadius(Zone.WELL_OF_GIANTS) + 40;
	}

	private static boolean tooClose(double x, double z, List<Site> taken) {
		for (Site s : taken) {
			if (Math.hypot(s.x() - x, s.z() - z) < 200) {
				return true;
			}
		}
		return false;
	}

	/** Where a secret leading into {@code to} at this angle sets you down: the first good ground out from the circle's middle. */
	private static int[] landing(Zone to, double a) {
		double outer = InfernoGeometry.outerRadius(to);
		double inner = InfernoGeometry.bandFor(to).inner();
		double lo = inner + 30;
		double hi = outer - 30;
		if (to == Zone.COCYTUS) {
			// on the ice of Caina, below the cliff, before the braziers begin
			lo = outer - 22;
			hi = outer - 14;
		}
		if (to == Zone.MALEBOLGE) {
			// on a ridge between two ditches
			for (int ridge : new int[]{5, 4, 6, 3, 7, 2, 8}) {
				double r = FraudSites.onRidge(a, FraudSites.ridgeRadius(ridge));
				int x = (int) Math.round(Math.cos(a) * r);
				int z = (int) Math.round(Math.sin(a) * r);
				if (landingOk(to, x, z)) {
					return new int[]{x, z};
				}
			}
			return null;
		}
		double mid = (lo + hi) / 2;
		for (int step = 0; step * 3 <= (hi - lo) / 2; step++) {
			for (int sign : new int[]{1, -1}) {
				double r = FraudSites.onRidge(a, mid + sign * step * 3);
				int x = (int) Math.round(Math.cos(a) * r);
				int z = (int) Math.round(Math.sin(a) * r);
				if (landingOk(to, x, z)) {
					return new int[]{x, z};
				}
			}
		}
		return null;
	}

	private static boolean landingOk(Zone to, int x, int z) {
		int reach = LANDING_RADIUS + 1;
		for (int dx = -reach; dx <= reach; dx += reach) {
			for (int dz = -reach; dz <= reach; dz += reach) {
				Zone zone = InfernoGeometry.zoneAt(x + dx, z + dz);
				if (zone != to) {
					return false;
				}
				if (to == Zone.MALEBOLGE && InfernoGeometry.ditchTrough(InfernoGeometry.effectiveRadius(x + dx, z + dz), Math.atan2(z + dz, x + dx)) > 0.02) {
					return false;
				}
				if (to != Zone.STYX && InfernoGeometry.fluidAt(x + dx, z + dz) != InfernoGeometry.Fluid.NONE) {
					return false;
				}
			}
		}
		return clear(x, z, LANDING_RADIUS, 30, Math.atan2(z, x));
	}

	/** Checks every site (for /hellcraft secrets test and the unit test): null if all is well, else what is wrong. */
	public static String problems() {
		StringBuilder bad = new StringBuilder();
		for (Site s : all()) {
			Zone lands = InfernoGeometry.zoneAt(s.landX(), s.landZ());
			if (lands != s.to()) {
				bad.append(String.format(Locale.ROOT, " %s lands in %s;", s.id(), lands.id()));
			}
			if (s.zone() != Zone.MALEBOLGE && InfernoGeometry.zoneAt(s.x(), s.z()) != s.zone()) {
				bad.append(" ").append(s.id()).append(" is out of its circle;");
			}
			if (InfernoGeometry.onRoad(s.x(), s.z()) || InfernoGeometry.onRoad(s.landX(), s.landZ())) {
				bad.append(" ").append(s.id()).append(" is on a road;");
			}
			if (InfernoGeometry.nearLair(s.x(), s.z(), InfernoGeometry.LAIR_CLEARANCE + s.kind().radius)
					|| InfernoGeometry.nearLair(s.landX(), s.landZ(), InfernoGeometry.LAIR_CLEARANCE)) {
				bad.append(" ").append(s.id()).append(" is in a lair;");
			}
			for (ShrineSites.Site rest : ShrineSites.all()) {
				if (Math.hypot(rest.x() - s.x(), rest.z() - s.z()) < 30 || Math.hypot(rest.x() - s.landX(), rest.z() - s.landZ()) < 20) {
					bad.append(" ").append(s.id()).append(" is at the Rest ").append(rest.id()).append(";");
				}
			}
			if (s.to() != Zone.STYX && InfernoGeometry.fluidAt(s.landX(), s.landZ()) != InfernoGeometry.Fluid.NONE) {
				bad.append(" ").append(s.id()).append(" lands in water;");
			}
			double depthFrom = InfernoGeometry.effectiveRadius(s.x(), s.z());
			double depthTo = InfernoGeometry.effectiveRadius(s.landX(), s.landZ());
			if (depthTo >= depthFrom) {
				bad.append(" ").append(s.id()).append(" leads up;");
			}
		}
		return bad.isEmpty() ? null : bad.toString().trim();
	}

	/** Prints the sites (for checking them offline). */
	public static void main(String[] args) {
		for (Site s : all()) {
			System.out.println(s);
		}
		System.out.println(problems());
	}
}
