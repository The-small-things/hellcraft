package net.thesmallthings.hellcraft.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

class SecretWaysTest {
	@Test
	void threeInEveryCircleFromLimboToMalebolge() {
		List<SecretWays.Site> sites = SecretWays.all();
		assertEquals(9 * SecretWays.PER_CIRCLE, sites.size());
		Set<String> ids = new HashSet<>();
		for (SecretWays.Site s : sites) {
			assertTrue(ids.add(s.id()), "two secrets share the id " + s.id());
			assertTrue(s.zone() != Zone.COCYTUS && s.zone() != Zone.JUDECCA, s.id() + " is in Cocytus");
		}
	}

	@Test
	void eachLeadsDownToTheNextCircleAwayFromEverythingElse() {
		assertNull(SecretWays.problems());
		for (SecretWays.Site s : SecretWays.all()) {
			// the next circle down, at the same angle round Hell
			double from = Math.atan2(s.z(), s.x());
			double to = Math.atan2(s.landZ(), s.landX());
			assertTrue(InfernoGeometry.angularDistance(from, to) < 0.01, s.id() + " lands at another angle");
			assertTrue(s.to().circle().depth() >= s.zone().circle().depth(), s.id() + " leads up");
			// the ground round its mouth is level
			assertEquals(1.0, SecretWays.levelling(s.x(), s.z()), 1e-9);
			// and its mouth is where the site's frame says
			int mx = s.worldX(s.kind().mouthF(), 0);
			int mz = s.worldZ(s.kind().mouthF(), 0);
			assertTrue(s.mouthAt(mx, mz), s.id() + "'s mouth is not at its middle");
			assertEquals(s.kind().mouthF(), s.frameF(mx, mz));
		}
	}

	@Test
	void heresysTombsKeepClear() {
		for (SecretWays.Site s : SecretWays.all()) {
			for (Tombs.Tomb t : Tombs.within(s.x() - 12, s.z() - 12, s.x() + 12, s.z() + 12)) {
				assertTrue(Math.hypot(t.centreX() - s.x(), t.centreZ() - s.z()) > s.kind().radius + 2, "a burning tomb stands in " + s.id());
			}
		}
	}
}
