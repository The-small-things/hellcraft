package net.thesmallthings.hellcraft.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfernoGeometryTest {
	private static final int ANGLES = 48;

	@Test
	void circlesGetDeeperTowardTheCentre() {
		for (int a = 0; a < ANGLES; a++) {
			double theta = a * 2 * Math.PI / ANGLES;
			int lastDepth = Integer.MAX_VALUE;
			for (int r = 5900; r >= 0; r -= 5) {
				int depth = InfernoGeometry.circleAt(Math.cos(theta) * r, Math.sin(theta) * r).depth();
				assertTrue(depth >= 0 && depth <= 9);
				if (lastDepth != Integer.MAX_VALUE) {
					assertTrue(depth >= lastDepth, "depth decreased going inward at r=" + r + " theta=" + theta);
				}
				lastDepth = depth;
			}
			assertEquals(Circle.TREACHERY, InfernoGeometry.circleAt(Math.cos(theta) * 10, Math.sin(theta) * 10));
		}
	}

	@Test
	void everyZoneExists() {
		java.util.EnumSet<Zone> seen = java.util.EnumSet.noneOf(Zone.class);
		for (int a = 0; a < ANGLES; a++) {
			double theta = a * 2 * Math.PI / ANGLES;
			for (int r = 0; r < 6000; r += 2) {
				seen.add(InfernoGeometry.zoneAt(Math.cos(theta) * r, Math.sin(theta) * r));
			}
		}
		assertEquals(java.util.EnumSet.allOf(Zone.class), seen);
	}

	@Test
	void heightsStayInsideTheWorldAndAboveTheLavaLine() {
		for (int x = -6200; x <= 6200; x += 37) {
			for (int z = -6200; z <= 6200; z += 37) {
				for (double detail : new double[]{-1.5, 0, 1.5}) {
					double h = InfernoGeometry.surfaceHeight(x, z, detail);
					assertTrue(h > -54 && h < 310, "height " + h + " at " + x + "," + z);
				}
			}
		}
	}

	@Test
	void terrainIsContinuous() {
		// cliffs are steep but never a discontinuity; big jumps would mean a bug in the band blending
		for (int a = 0; a < ANGLES; a++) {
			double theta = a * 2 * Math.PI / ANGLES + 0.013;
			double prev = Double.NaN;
			for (int r = 0; r < 6000; r++) {
				double h = InfernoGeometry.baseHeight(Math.cos(theta) * r, Math.sin(theta) * r);
				if (!Double.isNaN(prev)) {
					assertTrue(Math.abs(h - prev) < 12, "jump of " + (h - prev) + " at r=" + r);
				}
				prev = h;
			}
		}
	}

	@Test
	void riversAreContainedByTheirBanks() {
		for (int a = 0; a < 360; a++) {
			double theta = a * 2 * Math.PI / 360;
			for (int r = 1400; r < 5200; r++) {
				double x = Math.cos(theta) * r;
				double z = Math.sin(theta) * r;
				if (InfernoGeometry.fluidAt(x, z) != InfernoGeometry.Fluid.NONE) {
					continue;
				}
				// a dry column right next to a river must stand above that river's surface
				for (int d : new int[]{-1, 1}) {
					double nx = Math.cos(theta) * (r + d);
					double nz = Math.sin(theta) * (r + d);
					if (InfernoGeometry.fluidAt(nx, nz) != InfernoGeometry.Fluid.NONE) {
						int level = InfernoGeometry.fluidLevel(nx, nz);
						assertTrue(InfernoGeometry.baseHeight(x, z) >= level,
								"river at level " + level + " would spill at r=" + r + " theta=" + theta);
					}
				}
			}
		}
	}

	@Test
	void theWallsOfDisHaveFourGates() {
		for (double gate : InfernoGeometry.DIS_GATE_ANGLES) {
			boolean found = false;
			for (double r = InfernoGeometry.DIS_WALL_RADIUS - 40; r < InfernoGeometry.DIS_WALL_RADIUS + 40; r += 0.5) {
				if (InfernoGeometry.disWallAt(Math.cos(gate) * r, Math.sin(gate) * r) == InfernoGeometry.WallPart.GATE) {
					found = true;
				}
			}
			assertTrue(found, "no gate at angle " + gate);
		}
	}

	@Test
	void theGateOfHellStandsInTheDarkWood() {
		int gx = InfernoGeometry.gateX();
		assertEquals(Zone.DARK_WOOD, InfernoGeometry.zoneAt(gx, 0));
		assertEquals(Zone.DARK_WOOD, InfernoGeometry.zoneAt(gx + 24, 0));
		assertTrue(gx < InfernoGeometry.BORDER_RADIUS - 200);
	}

	@Test
	void theGateOfHellRingsTheVestibuleWithTwelveGates() {
		for (int k = 0; k < InfernoGeometry.GATE_COUNT; k++) {
			double a = InfernoGeometry.gateAngle(k);
			boolean gate = false;
			for (double r = 5400; r < 5600; r += 0.25) {
				if (InfernoGeometry.gateWallAt(Math.cos(a) * r, Math.sin(a) * r) == InfernoGeometry.WallPart.GATE) {
					gate = true;
				}
			}
			assertTrue(gate, "no gate at " + k);
			// every gate leads onto a ramp down into the Vestibule
			assertTrue(InfernoGeometry.rampFactor(0, a, 5400) > 0.99, "no ramp below gate " + k);
			int[] sign = InfernoGeometry.gateSignSpot(k);
			assertEquals(Zone.DARK_WOOD, InfernoGeometry.zoneAt(sign[0], sign[1]));
			assertEquals(InfernoGeometry.WallPart.NONE, InfernoGeometry.gateWallAt(sign[0], sign[1]));
		}
		// the wall is unbroken between gates: walk the ring and count openings
		int openings = 0;
		boolean inGate = false;
		for (int i = 0; i < 72000; i++) {
			double a = Math.PI / 12 + i * 2 * Math.PI / 72000; // start between two gates
			boolean found = false;
			boolean anyWall = false;
			for (double r = 5440; r < 5560; r += 0.5) {
				InfernoGeometry.WallPart part = InfernoGeometry.gateWallAt(Math.cos(a) * r, Math.sin(a) * r);
				found |= part == InfernoGeometry.WallPart.GATE;
				anyWall |= part != InfernoGeometry.WallPart.NONE;
			}
			assertTrue(anyWall, "gap in the wall at angle " + a);
			if (found && !inGate) {
				openings++;
			}
			inGate = found;
		}
		assertEquals(InfernoGeometry.GATE_COUNT, openings);
	}

	@Test
	void spawnIsOutsideTheWall() {
		int gx = InfernoGeometry.gateX();
		assertTrue(InfernoGeometry.effectiveRadius(gx + 24, 0) > InfernoGeometry.GATE_WALL_RADIUS + 5);
		assertEquals(InfernoGeometry.WallPart.NONE, InfernoGeometry.gateWallAt(gx + 24, 0));
	}
}
