package net.thesmallthings.hellcraft.world;


import java.util.ArrayList;
import java.util.List;

/**
 * Where to go next, worked out from the geometry alone (so the client can show it without asking the server):
 * the next way down from wherever you stand, for the compass and the Map of Hell.
 */
public final class Navigation {
	private Navigation() {
	}

	/** A place to head for, and what to call it. */
	public record Target(double x, double z, String label) {
	}

	/** The Spines leave the Well of Giants this far from the centre (Spine.START). */
	private static final double SPINE_START = Spine.START;

	/**
	 * The nearest way down into the next circle: a ramp (one every 30 degrees on most cliffs), a gate (the Gate
	 * of Hell, the Walls of Dis), a Spine (from the Well of Giants), or the pit itself.
	 */
	public static Target wayDown(double x, double z) {
		double re = InfernoGeometry.effectiveRadius(x, z);
		double theta = Math.atan2(z, x);
		int i = InfernoGeometry.bandIndex(re);
		InfernoGeometry.Band band = InfernoGeometry.BANDS[i];
		Zone zone = band.zone();
		if (zone == Zone.JUDECCA) {
			return new Target(0, 0, "Lucifer's pit");
		}
		if (zone == Zone.WELL_OF_GIANTS) {
			return nearest(x, z, InfernoGeometry.ROAD_ANGLES, SPINE_START, "The Spine");
		}
		if (zone == Zone.DARK_WOOD && re > InfernoGeometry.GATE_WALL_RADIUS) {
			double[] gates = new double[InfernoGeometry.GATE_COUNT];
			for (int k = 0; k < gates.length; k++) {
				gates[k] = InfernoGeometry.gateAngle(k);
			}
			return nearest(x, z, gates, InfernoGeometry.GATE_WALL_RADIUS - 6, "Gate of Hell");
		}
		if (zone == Zone.STYX || zone == Zone.WALLS_OF_DIS) {
			return nearest(x, z, InfernoGeometry.ROAD_ANGLES, InfernoGeometry.DIS_WALL_RADIUS - 6, "Gate of Dis");
		}
		double target = band.inner() + 20;
		if (band.ways() == InfernoGeometry.Ways.NONE) {
			// a gentle slope all round: straight in
			return new Target(Math.cos(theta) * (band.inner() - 10), Math.sin(theta) * (band.inner() - 10), "Way down");
		}
		return nearest(x, z, InfernoGeometry.rampAngles(zone), target, "Way down");
	}

	/** Every way down, for the Map of Hell: the ramps of each cliff, the gates, the Spines and the pit. */
	public static List<Target> all() {
		List<Target> out = new ArrayList<>();
		for (int k = 0; k < InfernoGeometry.GATE_COUNT; k++) {
			double a = InfernoGeometry.gateAngle(k);
			out.add(at(a, InfernoGeometry.GATE_WALL_RADIUS, "Gate of Hell"));
		}
		for (double a : InfernoGeometry.ROAD_ANGLES) {
			out.add(at(a, InfernoGeometry.DIS_WALL_RADIUS, "Gate of Dis"));
			out.add(at(a, SPINE_START, "The Spine"));
		}
		for (InfernoGeometry.Band band : InfernoGeometry.BANDS) {
			Zone zone = band.zone();
			if (zone == Zone.STYX || zone == Zone.WALLS_OF_DIS || band.ways() == InfernoGeometry.Ways.NONE) {
				continue;
			}
			for (double a : InfernoGeometry.rampAngles(zone)) {
				out.add(at(a, band.inner(), "Way down"));
			}
		}
		out.add(new Target(0, 0, "Lucifer's pit"));
		return out;
	}

	private static Target at(double angle, double radius, String label) {
		return new Target(Math.cos(angle) * radius, Math.sin(angle) * radius, label);
	}

	private static Target nearest(double x, double z, double[] angles, double radius, String label) {
		Target best = null;
		double bestD = Double.MAX_VALUE;
		for (double a : angles) {
			double tx = Math.cos(a) * radius;
			double tz = Math.sin(a) * radius;
			double d = (tx - x) * (tx - x) + (tz - z) * (tz - z);
			if (d < bestD) {
				bestD = d;
				best = new Target(tx, tz, label);
			}
		}
		return best;
	}
}
