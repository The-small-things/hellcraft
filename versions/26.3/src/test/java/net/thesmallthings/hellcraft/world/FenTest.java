package net.thesmallthings.hellcraft.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Fen's watchtowers: four with beacons by the roads, eight outposts between them, all in the Fen and clear of the rest. */
class FenTest {
	@Test
	void twelveTowersStandApartInTheFen() {
		assertEquals(4, Fen.towers());
		assertEquals(12, Fen.allTowers());
		for (int k = 0; k < Fen.allTowers(); k++) {
			int[] t = Fen.tower(k);
			assertEquals(Zone.STYX, InfernoGeometry.zoneAt(t[0] + 0.5, t[1] + 0.5), "tower " + k + " stands in the Fen");
			assertEquals(k, Fen.towerAt(t[0], t[1]), "tower " + k + " is found at its own column");
			assertEquals(k < 4, Fen.hasBeacons(k));
			for (int j = 0; j < k; j++) {
				int[] o = Fen.tower(j);
				assertTrue(Math.hypot(t[0] - o[0], t[1] - o[1]) > 400, "towers " + j + " and " + k + " stand far apart");
			}
		}
	}

	@Test
	void outpostsKeepClear() {
		for (int i = 0; i < Fen.Outposts.SITES.length; i++) {
			int[] t = Fen.Outposts.SITES[i];
			assertTrue(Fen.Outposts.clear(t), "outpost " + i + " at " + t[0] + ", " + t[1] + " is clear of roads, lairs, ways down, Rests and secret ways");
		}
	}
}
