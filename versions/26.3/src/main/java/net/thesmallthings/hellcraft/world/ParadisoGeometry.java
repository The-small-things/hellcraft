package net.thesmallthings.hellcraft.world;

import java.util.Locale;

/**
 * Paradiso: the End as Dante's heaven. The dragon's island in the middle is the Threshold; the outer
 * islands are the nine spheres, ring by ring outward from the centre, and past them the Empyrean, where
 * the Celestial Rose blooms. Pure geometry, so it can be checked offline (java ... ParadisoGeometry).
 */
public final class ParadisoGeometry {
	private ParadisoGeometry() {
	}

	public enum Sphere {
		THRESHOLD("threshold", "The Threshold of Heaven", "Here must all distrust be left behind", 0),
		MOON("moon", "I — The Moon", "The inconstant: vows left unfulfilled", 1000),
		MERCURY("mercury", "II — Mercury", "The ambitious, who did good for glory", 1600),
		VENUS("venus", "III — Venus", "The lovers", 2200),
		SUN("sun", "IV — The Sun", "The wise", 2800),
		MARS("mars", "V — Mars", "The warriors of the faith", 3400),
		JUPITER("jupiter", "VI — Jupiter", "The just rulers", 4000),
		SATURN("saturn", "VII — Saturn", "The contemplatives", 4600),
		FIXED_STARS("fixed_stars", "VIII — The Fixed Stars", "The Church Triumphant", 5200),
		PRIMUM_MOBILE("primum_mobile", "IX — The Primum Mobile", "The angels, turning all the heavens", 5800),
		EMPYREAN("empyrean", "The Empyrean", "The love that moves the sun and the other stars", 6400);

		public final String id;
		public final String title;
		public final String tagline;
		/** Distance from the centre where this sphere begins. */
		public final int from;

		Sphere(String id, String title, String tagline, int from) {
			this.id = id;
			this.title = title;
			this.tagline = tagline;
			this.from = from;
		}

		public String biome() {
			return "hellcraft:paradiso_" + id;
		}
	}

	/** The Celestial Rose's centre, in the Empyrean on the +X side (built once, see Paradiso). */
	public static final int ROSE_X = 7000;
	public static final int ROSE_Z = 0;

	/** Which sphere a column of the End belongs to (the ring edges wobble a little so they don't look drawn). */
	public static Sphere sphereAt(double x, double z) {
		double r = Math.sqrt(x * x + z * z);
		if (r < 800) {
			return Sphere.THRESHOLD;
		}
		double wobble = 40.0 * Math.sin(Math.atan2(z, x) * 7.0) + 25.0 * Math.sin(r / 173.0);
		double re = r + wobble;
		Sphere best = Sphere.MOON;
		for (Sphere s : Sphere.values()) {
			if (s != Sphere.THRESHOLD && re >= s.from) {
				best = s;
			}
		}
		return best;
	}

	public static void main(String[] args) {
		for (Sphere s : Sphere.values()) {
			System.out.printf(Locale.ROOT, "%-14s from %5d  %s%n", s.id, s.from, s.biome());
		}
		for (int r : new int[]{0, 500, 900, 1200, 2000, 3000, 4500, 6000, 6500, 7000, 12000}) {
			System.out.printf(Locale.ROOT, "r=%5d -> %s%n", r, sphereAt(r, 0).id);
		}
	}
}
