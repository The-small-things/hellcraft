package net.thesmallthings.hellcraft.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

class NavigationTest {
	private static int band(double x, double z) {
		return InfernoGeometry.bandIndex(InfernoGeometry.effectiveRadius(x, z));
	}

	@Test
	void followingTheCompassLeadsToThePit() {
		// from all round the Dark Wood, do what the compass says: walk to the way down it points at, go down it
		// (60 blocks further in), and look again
		for (int k = 0; k < 24; k++) {
			double a = k * Math.PI / 12 + 0.05;
			double x = Math.cos(a) * 3420;
			double z = Math.sin(a) * 3420;
			int steps = 0;
			int lastBand = band(x, z);
			Navigation.Target next;
			while (!(next = Navigation.wayDown(x, z)).label().equals("Lucifer's pit")) {
				double r = Math.sqrt(x * x + z * z);
				double walk = Math.hypot(next.x() - x, next.z() - z);
				// never sent the long way round: at most an eighth of the way around, plus a little
				assertTrue(walk <= r * Math.PI / 4 + 150, next.label() + " is " + (int) walk + " blocks from " + (int) x + ", " + (int) z);
				double tr = Math.hypot(next.x(), next.z());
				double f = Math.max(0, tr - 60) / tr;
				x = next.x() * f;
				z = next.z() * f;
				int now = band(x, z);
				assertTrue(now >= lastBand, "the compass led back up at " + (int) x + ", " + (int) z);
				lastBand = now;
				assertTrue(++steps < 40, "the compass goes round in circles from angle " + k);
			}
			assertEquals(Zone.JUDECCA, InfernoGeometry.zoneAt(x, z));
		}
	}

	@Test
	void theMapShowsEveryWayDown() {
		List<Navigation.Target> all = Navigation.all();
		assertEquals(InfernoGeometry.GATE_COUNT, all.stream().filter(t -> t.label().equals("Gate of Hell")).count());
		assertEquals(4, all.stream().filter(t -> t.label().equals("Gate of Dis")).count());
		assertEquals(4, all.stream().filter(t -> t.label().equals("The Spine")).count());
		assertEquals(1, all.stream().filter(t -> t.label().equals("Lucifer's pit")).count());
		long ramps = 0;
		for (InfernoGeometry.Band b : InfernoGeometry.BANDS) {
			if (b.zone() != Zone.STYX && b.zone() != Zone.WALLS_OF_DIS) {
				ramps += InfernoGeometry.rampAngles(b.zone()).length;
			}
		}
		assertEquals(ramps, all.stream().filter(t -> t.label().equals("Way down")).count());
	}

	@Test
	void theCompassPointsAtTheGateFromOutsideTheWall() {
		Navigation.Target t = Navigation.wayDown(3400, 30);
		assertEquals("Gate of Hell", t.label());
		assertTrue(t.x() > 3200 && Math.abs(t.z()) < 10, "the east gate, not " + t);
	}
}
