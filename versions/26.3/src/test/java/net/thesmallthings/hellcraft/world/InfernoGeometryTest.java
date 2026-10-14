package net.thesmallthings.hellcraft.world;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfernoGeometryTest {
	private static final int ANGLES = 48;
	private static final int BORDER = InfernoGeometry.BORDER_RADIUS;

	@Test
	void circlesGetDeeperTowardTheCentre() {
		for (int a = 0; a < ANGLES; a++) {
			double theta = a * 2 * Math.PI / ANGLES;
			int lastDepth = Integer.MAX_VALUE;
			for (int r = BORDER - 100; r >= 0; r -= 5) {
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
		EnumSet<Zone> seen = EnumSet.noneOf(Zone.class);
		for (int a = 0; a < ANGLES; a++) {
			double theta = a * 2 * Math.PI / ANGLES + 0.07;
			for (int r = 0; r < BORDER; r += 2) {
				seen.add(InfernoGeometry.zoneAt(Math.cos(theta) * r, Math.sin(theta) * r));
			}
		}
		assertEquals(EnumSet.allOf(Zone.class), seen);
	}

	@Test
	void heightsStayInsideTheWorldAndAboveTheLavaLine() {
		for (int x = -BORDER - 200; x <= BORDER + 200; x += 23) {
			for (int z = -BORDER - 200; z <= BORDER + 200; z += 23) {
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
			for (int r = 0; r < BORDER; r++) {
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
		for (int a = 0; a < 720; a++) {
			double theta = a * 2 * Math.PI / 720;
			for (int r = 900; r < InfernoGeometry.GATE_WALL_RADIUS; r++) {
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

	/** Every Pilgrim Road can be walked from the Gate of Hell to the Well of Giants: no step over one block, no water, no wall. */
	@Test
	void theRoadsCanBeWalkedFromTheGateToTheWell() {
		for (double angle : InfernoGeometry.ROAD_ANGLES) {
			long prev = Long.MIN_VALUE;
			for (double r = InfernoGeometry.roadOuterRadius(); r >= InfernoGeometry.roadInnerRadius(); r -= 0.5) {
				double x = Math.cos(angle) * r;
				double z = Math.sin(angle) * r;
				assertTrue(InfernoGeometry.onRoad(x, z), "off the road at r=" + r + " angle=" + angle);
				assertEquals(InfernoGeometry.Fluid.NONE, InfernoGeometry.fluidAt(x, z), "water on the road at r=" + r + " angle=" + angle);
				InfernoGeometry.WallPart dis = InfernoGeometry.disWallAt(x, z);
				assertTrue(dis == InfernoGeometry.WallPart.NONE || dis == InfernoGeometry.WallPart.GATE, "the Walls of Dis block the road at r=" + r);
				InfernoGeometry.WallPart gate = InfernoGeometry.gateWallAt(x, z);
				assertTrue(gate == InfernoGeometry.WallPart.NONE || gate == InfernoGeometry.WallPart.GATE, "the Gate of Hell blocks the road at r=" + r);
				// the road's ground has no detail noise, so its height is the base height
				long h = Math.round(InfernoGeometry.surfaceHeight(x, z, 1.0));
				assertEquals(Math.round(InfernoGeometry.baseHeight(x, z)), h, "the road is not level at r=" + r);
				if (prev != Long.MIN_VALUE) {
					assertTrue(Math.abs(h - prev) <= 1, "a step of " + (h - prev) + " on the road at r=" + r + " angle=" + angle);
				}
				prev = h;
			}
		}
	}

	/** Every cliff with ways down has twelve, on the clock; each can be walked (water aside). */
	@Test
	void everyClockCliffHasTwelveWalkableWaysDown() {
		for (int i = 0; i < InfernoGeometry.BANDS.length - 1; i++) {
			InfernoGeometry.Band band = InfernoGeometry.BANDS[i];
			if (band.ways() != InfernoGeometry.Ways.CLOCK) {
				continue;
			}
			assertEquals(12, InfernoGeometry.rampAngles(band.zone()).length);
			for (int k = 0; k < 12; k++) {
				double angle = InfernoGeometry.wayDownAngle(k);
				assertTrue(InfernoGeometry.rampFactor(i, angle, band.inner()) > 0.99, band.zone() + " has no way down at " + k);
				long prev = Long.MIN_VALUE;
				for (double r = Math.min(band.outer() - 5, band.inner() + 120); r >= band.inner() - 10; r -= 0.5) {
					long h = Math.round(InfernoGeometry.baseHeight(Math.cos(angle) * r, Math.sin(angle) * r));
					if (prev != Long.MIN_VALUE) {
						assertTrue(Math.abs(h - prev) <= 1, "a step of " + (h - prev) + " down from " + band.zone() + " at r=" + r + ", way " + k);
					}
					prev = h;
				}
			}
		}
	}

	@Test
	void theWallsOfDisHaveAGateOnEveryRoad() {
		for (double gate : InfernoGeometry.ROAD_ANGLES) {
			boolean found = false;
			for (double r = InfernoGeometry.DIS_WALL_RADIUS - 40; r < InfernoGeometry.DIS_WALL_RADIUS + 40; r += 0.5) {
				if (InfernoGeometry.disWallAt(Math.cos(gate) * r, Math.sin(gate) * r) == InfernoGeometry.WallPart.GATE) {
					found = true;
				}
			}
			assertTrue(found, "no gate at angle " + gate);
		}
		// and nowhere else: between the roads the wall is whole
		double between = Math.PI / 4;
		boolean wall = false;
		for (double r = InfernoGeometry.DIS_WALL_RADIUS - 40; r < InfernoGeometry.DIS_WALL_RADIUS + 40; r += 0.5) {
			InfernoGeometry.WallPart part = InfernoGeometry.disWallAt(Math.cos(between) * r, Math.sin(between) * r);
			assertNotEquals(InfernoGeometry.WallPart.GATE, part);
			wall |= part == InfernoGeometry.WallPart.WALL || part == InfernoGeometry.WallPart.TOWER;
		}
		assertTrue(wall);
	}

	@Test
	void theRiversAreCrossedByCauseways() {
		int crossings = 0;
		for (double angle : InfernoGeometry.ROAD_ANGLES) {
			boolean wetBeside = false;
			for (double r = InfernoGeometry.roadOuterRadius(); r >= InfernoGeometry.roadInnerRadius(); r -= 1) {
				double sx = Math.cos(angle) * r - Math.sin(angle) * 20;
				double sz = Math.sin(angle) * r + Math.cos(angle) * 20;
				wetBeside |= InfernoGeometry.fluidAt(sx, sz) != InfernoGeometry.Fluid.NONE;
			}
			if (wetBeside) {
				crossings++;
			}
		}
		assertEquals(InfernoGeometry.ROAD_ANGLES.length, crossings, "every road should pass rivers (and stay dry)");
	}

	@Test
	void theGateOfHellStandsInTheDarkWood() {
		int gx = InfernoGeometry.gateX();
		assertEquals(Zone.DARK_WOOD, InfernoGeometry.zoneAt(gx, 0));
		assertEquals(Zone.DARK_WOOD, InfernoGeometry.zoneAt(gx + 24, 0));
		assertTrue(gx < BORDER - 150);
	}

	@Test
	void theGateOfHellRingsTheVestibuleWithTwelveGates() {
		double wall = InfernoGeometry.GATE_WALL_RADIUS;
		for (int k = 0; k < InfernoGeometry.GATE_COUNT; k++) {
			double a = InfernoGeometry.gateAngle(k);
			boolean gate = false;
			for (double r = wall - 100; r < wall + 100; r += 0.25) {
				if (InfernoGeometry.gateWallAt(Math.cos(a) * r, Math.sin(a) * r) == InfernoGeometry.WallPart.GATE) {
					gate = true;
				}
			}
			assertTrue(gate, "no gate at " + k);
			// every gate leads onto a ramp down into the Vestibule
			assertTrue(InfernoGeometry.rampFactor(0, a, InfernoGeometry.BANDS[0].inner()) > 0.99, "no ramp below gate " + k);
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
			for (double r = wall - 60; r < wall + 60; r += 0.5) {
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
		assertTrue(InfernoGeometry.onRoad(gx + 24, 0), "spawn should be on the east road");
	}
}
