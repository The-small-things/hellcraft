package net.thesmallthings.hellcraft.world;

import net.minecraft.core.BlockPos;
import net.thesmallthings.hellcraft.hazard.guardian.Guardian;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShrineSitesTest {
	@Test
	void everyCircleHasARestBesideEachRoad() {
		List<ShrineSites.Site> sites = ShrineSites.all();
		assertEquals(10 * InfernoGeometry.ROAD_ANGLES.length, sites.size());
		for (ShrineSites.Site s : sites) {
			assertEquals(s.zone(), InfernoGeometry.zoneAt(s.x(), s.z()), s.id() + " is not in its circle");
			// beside the road, never on it: the platform (11 x 11) stays off the paving
			for (int dx = -5; dx <= 5; dx++) {
				for (int dz = -5; dz <= 5; dz++) {
					assertFalse(InfernoGeometry.onRoad(s.x() + dx, s.z() + dz), s.id() + " is on the road");
				}
			}
			double nearestRoad = Double.MAX_VALUE;
			for (double a : InfernoGeometry.ROAD_ANGLES) {
				nearestRoad = Math.min(nearestRoad, Math.abs(-Math.sin(a) * s.x() + Math.cos(a) * s.z()));
			}
			assertTrue(nearestRoad <= ShrineSites.ROAD_OFFSET + 1, s.id() + " is " + nearestRoad + " blocks from the road");
		}
	}

	@Test
	void theRestsKnowWhereTheLairsAre() {
		int i = 0;
		for (Guardian g : Guardian.values()) {
			if (g.offMap()) {
				continue;
			}
			BlockPos lair = g.lairColumn();
			assertEquals(lair.getX(), ShrineSites.LAIRS[i][0], g.id());
			assertEquals(lair.getZ(), ShrineSites.LAIRS[i][1], g.id());
			assertFalse(InfernoGeometry.onRoad(lair.getX(), lair.getZ() - 24), g.id() + "'s lair reaches the road");
			i++;
		}
		assertEquals(ShrineSites.LAIRS.length, i);
		for (ShrineSites.Site s : ShrineSites.all()) {
			for (int[] lair : ShrineSites.LAIRS) {
				double d = Math.hypot(s.x() - lair[0], s.z() - lair[1]);
				assertTrue(d > 40, s.id() + " is inside a lair");
			}
		}
	}
}
