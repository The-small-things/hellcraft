package net.thesmallthings.hellcraft.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

class LowerHellTest {
	@Test
	void tombsFillTheIronCityButKeepOffTheRoads() {
		List<Tombs.Tomb> tombs = Tombs.within((int) InfernoGeometry.scaled(1100), -300, (int) InfernoGeometry.scaled(1400), 300);
		assertTrue(tombs.size() > 100, "only " + tombs.size() + " tombs");
		int[] slots = new int[Tombs.SLOTS];
		for (Tombs.Tomb t : tombs) {
			assertEquals(Zone.HERESY, InfernoGeometry.zoneAt(t.centreX(), t.centreZ()));
			assertFalse(InfernoGeometry.onRoad(t.centreX(), t.centreZ()), "a tomb on the road at " + t);
			for (int[] lair : InfernoGeometry.LAIRS) {
				assertTrue(Math.hypot(t.centreX() - lair[0], t.centreZ() - lair[1]) > 40, "a tomb in a lair at " + t);
			}
			slots[t.slot()]++;
		}
		for (int n : slots) {
			assertTrue(n > 10);
		}
		// the same site from any block of its cell
		Tombs.Tomb t = tombs.getFirst();
		assertEquals(t, Tombs.inCell(t.x() + 1, t.z() + 1));
	}

	@Test
	void tombsTakeTurns() {
		Tombs.Tomb a = new Tombs.Tomb(0, 0, true, 0);
		Tombs.Tomb b = new Tombs.Tomb(0, 0, true, 1);
		int both = 0;
		int quiet = 0;
		for (long t = 0; t < Tombs.PERIOD; t++) {
			if (a.blasting(t) && b.blasting(t)) {
				both++;
			}
			if (!a.blasting(t) && !a.warning(t)) {
				quiet++;
			}
		}
		assertEquals(0, both, "neighbouring slots blast at once");
		assertEquals(Tombs.PERIOD - Tombs.WARN - Tombs.BLAST, quiet);
	}

	@Test
	void fordsCrossTheRiverOfBlood() {
		for (int k = 0; k < 12; k++) {
			double a = InfernoGeometry.wayDownAngle(k);
			boolean dry = true;
			for (int r = (int) InfernoGeometry.scaled(1000); r <= InfernoGeometry.scaled(1110); r++) {
				if (InfernoGeometry.fluidAt(Math.cos(a) * r, Math.sin(a) * r) != InfernoGeometry.Fluid.NONE) {
					dry = false;
				}
			}
			// every way down crosses dry: the roads on their causeways, the rest at fords
			assertTrue(dry, "way " + k + " is under the river");
		}
		// between the ways down, the river is blood all across
		double between = InfernoGeometry.wayDownAngle(1) + InfernoGeometry.WAY_DOWN_STEP / 2;
		assertFalse(InfernoGeometry.onFord(Math.cos(between) * InfernoGeometry.scaled(1065), Math.sin(between) * InfernoGeometry.scaled(1065)));
		assertTrue(InfernoGeometry.onFord(Math.cos(InfernoGeometry.wayDownAngle(1)) * InfernoGeometry.scaled(1065), Math.sin(InfernoGeometry.wayDownAngle(1)) * InfernoGeometry.scaled(1065)));
		assertNull(Tombs.blastingAt(0, 0, 0));
	}

	@Test
	void malebolgeKeepsItsLiesOnTheRidges() {
		List<FraudSites.Cache> caches = FraudSites.caches();
		assertTrue(caches.size() > 200, "only " + caches.size() + " caches");
		long mimics = caches.stream().filter(FraudSites.Cache::mimic).count();
		assertTrue(mimics > caches.size() / 5 && mimics < caches.size() / 2, mimics + " mimics");
		for (FraudSites.Cache c : caches) {
			assertEquals(Zone.MALEBOLGE, InfernoGeometry.zoneAt(c.x(), c.z()), c.id() + " is off its ridge");
			assertFalse(InfernoGeometry.onRoad(c.x(), c.z()), c.id() + " is on a road");
		}
		assertEquals(8, FraudSites.fakeRests().size());
		for (FraudSites.FakeRest f : FraudSites.fakeRests()) {
			assertEquals(Zone.MALEBOLGE, InfernoGeometry.zoneAt(f.x(), f.z()), f.id() + " is off its ridge");
		}
	}
}
