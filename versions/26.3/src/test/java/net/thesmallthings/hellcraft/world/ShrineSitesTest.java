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
	void everyCircleHasARestBesideEachRoadAndFourBetween() {
		List<ShrineSites.Site> sites = ShrineSites.all();
		// eight in every circle but Malebolge, whose bridges off the roads have Fraud's fake Rests instead
		assertEquals((10 * 2 - 1) * InfernoGeometry.ROAD_ANGLES.length, sites.size());
		assertEquals(sites.size(), sites.stream().map(ShrineSites.Site::id).distinct().count(), "two Rests share an id");
		for (ShrineSites.Site s : sites) {
			assertEquals(s.zone(), InfernoGeometry.zoneAt(s.x(), s.z()), s.id() + " is not in its circle");
			// beside the road, never on it: the platform (11 x 11) stays off the paving
			for (int dx = -5; dx <= 5; dx++) {
				for (int dz = -5; dz <= 5; dz++) {
					assertFalse(InfernoGeometry.onRoad(s.x() + dx, s.z() + dz), s.id() + " is on the road");
				}
			}
			if (s.id().endsWith("east") && !s.id().endsWith("_east") || s.id().endsWith("west") && !s.id().endsWith("_west")) {
				// between the roads: on its way down, well away from the roads
				double theta = Math.atan2(s.z(), s.x());
				double toWay = Double.MAX_VALUE;
				for (int k = 0; k < 12; k++) {
					toWay = Math.min(toWay, InfernoGeometry.angularDistance(theta, InfernoGeometry.wayDownAngle(k)));
				}
				assertTrue(toWay < 0.02, s.id() + " is off its way down");
				continue;
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
			for (int k = 0; k < g.lairs(); k++) {
				BlockPos lair = g.lairColumn(k);
				assertEquals(lair.getX(), ShrineSites.LAIRS[i][0], g.id());
				assertEquals(lair.getZ(), ShrineSites.LAIRS[i][1], g.id());
				for (int dx = -24; dx <= 24; dx += 4) {
					for (int dz = -24; dz <= 24; dz += 4) {
						if (dx * dx + dz * dz <= 24 * 24) {
							assertFalse(InfernoGeometry.onRoad(lair.getX() + dx, lair.getZ() + dz), g.id() + "'s lair " + (k + 1) + " reaches the road");
						}
					}
				}
				i++;
			}
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
