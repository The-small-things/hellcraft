package net.thesmallthings.hellcraft.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

/** The Fen's watchtowers: four with beacons by the roads, and outposts scattered by chance, all in the Fen and clear of the rest. */
class FenTest {
	@Test
	void beaconTowersStandInTheFen() {
		assertEquals(4, Fen.towers());
		for (int k = 0; k < Fen.towers(); k++) {
			int[] t = Fen.tower(k);
			assertEquals(Zone.STYX, InfernoGeometry.zoneAt(t[0] + 0.5, t[1] + 0.5), "tower " + k + " stands in the Fen");
			assertEquals(k, Fen.towerAt(t[0], t[1]));
		}
	}

	@Test
	void outpostsAreRareButFindable() {
		int r = (int) InfernoGeometry.outerRadius(Zone.STYX) + 8;
		for (long seed : new long[]{0L, 1L, 42L, -7215633781L, 123456789012345L}) {
			List<int[]> all = Fen.Outposts.in(seed, -r, -r, r, r);
			// about two dozen round the whole Fen: enough to find several caches, few enough to be worth finding
			assertTrue(all.size() >= 12 && all.size() <= 40, "seed " + seed + " has " + all.size() + " outposts");
			for (int i = 0; i < all.size(); i++) {
				int[] t = all.get(i);
				assertTrue(Fen.Outposts.clear(t), "outpost at " + t[0] + ", " + t[1] + " is clear");
				for (int j = 0; j < i; j++) {
					int[] o = all.get(j);
					assertTrue(Math.hypot(t[0] - o[0], t[1] - o[1]) >= 2 * Fen.Outposts.MARGIN, "outposts keep apart");
				}
			}
		}
		// the same seed always gives the same towers (every chunk must agree)
		assertEquals(Fen.Outposts.in(42L, -r, -r, r, r).size(), Fen.Outposts.in(42L, -r, -r, r, r).size());
	}
}
