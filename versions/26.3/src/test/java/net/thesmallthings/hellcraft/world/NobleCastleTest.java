package net.thesmallthings.hellcraft.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NobleCastleTest {
	@Test
	void theCastleStandsInLimboOnTheEastRoad() {
		for (int k = 0; k < 72; k++) {
			double a = k * Math.PI / 36;
			double x = NobleCastle.X + Math.cos(a) * (NobleCastle.STREAM_OUTER + 4);
			double z = NobleCastle.Z + Math.sin(a) * (NobleCastle.STREAM_OUTER + 4);
			assertEquals(Zone.LIMBO, InfernoGeometry.zoneAt(x, z), "the castle spills out of Limbo at " + (int) x + ", " + (int) z);
			assertEquals(InfernoGeometry.Fluid.NONE, InfernoGeometry.fluidAt(x, z), "a river runs into the castle at " + (int) x + ", " + (int) z);
		}
		assertTrue(InfernoGeometry.onRoad(NobleCastle.X + 0.5, NobleCastle.Z + 0.5), "the east road doesn't run through the castle");
		// the road passes through a gate in every wall
		for (int wall = 0; wall < NobleCastle.WALLS; wall++) {
			double r = NobleCastle.radius(wall);
			assertEquals(NobleCastle.Part.GATE, NobleCastle.partAt(NobleCastle.X + r + 0.1, NobleCastle.Z + 0.5));
			assertEquals(NobleCastle.Part.GATE, NobleCastle.partAt(NobleCastle.X - r - 0.1, NobleCastle.Z + 0.5));
		}
	}

	@Test
	void theRestsStandClearOfTheCastle() {
		for (ShrineSites.Site site : ShrineSites.all()) {
			assertTrue(NobleCastle.distance(site.x(), site.z()) > NobleCastle.STREAM_OUTER + 12, site.id() + " is inside the Noble Castle");
		}
	}
}
