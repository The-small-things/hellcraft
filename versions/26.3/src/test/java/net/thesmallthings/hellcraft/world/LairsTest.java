package net.thesmallthings.hellcraft.world;

import net.minecraft.core.BlockPos;
import net.thesmallthings.hellcraft.hazard.guardian.Guardian;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

class LairsTest {
	/** Each guardian's circle: its whole arena must lie in it, clear of its edges. */
	private static final Map<Guardian, Zone> HOME = Map.of(Guardian.MINOS, Zone.LUST, Guardian.CERBERUS, Zone.GLUTTONY, Guardian.PLUTUS, Zone.GREED,
			Guardian.PHLEGYAS, Zone.STYX, Guardian.FURIES, Zone.HERESY, Guardian.FARINATA, Zone.HERESY, Guardian.MINOTAUR, Zone.WOOD_OF_SUICIDES,
			Guardian.GERYON, Zone.BURNING_SANDS);

	@Test
	void eachLairIsWellInsideItsOwnCircle() {
		for (Map.Entry<Guardian, Zone> e : HOME.entrySet()) {
			BlockPos c = e.getKey().lairColumn();
			for (int k = 0; k < 16; k++) {
				double a = k * Math.PI / 8;
				// the brazier ring is 14 across: its edge, and a little beyond, is in the circle
				double x = c.getX() + Math.cos(a) * 18;
				double z = c.getZ() + Math.sin(a) * 18;
				Zone at = InfernoGeometry.zoneAt(x, z);
				// (the Furies' lair is against the wall of Dis on purpose: Medusa rises over its battlements)
				boolean wall = e.getKey() == Guardian.FURIES && at == Zone.WALLS_OF_DIS;
				assertTrue(at == e.getValue() || wall, e.getKey().id() + "'s lair spills out of " + e.getValue() + " into " + at);
			}
		}
	}

	@Test
	void lairsAreSpreadRoundHellAndOffTheWays() {
		for (Guardian g : HOME.keySet()) {
			BlockPos c = g.lairColumn();
			double theta = Math.atan2(c.getZ(), c.getX());
			double r = Math.hypot(c.getX(), c.getZ());
			for (int k = 0; k < 12; k++) {
				double arc = InfernoGeometry.angularDistance(theta, InfernoGeometry.wayDownAngle(k)) * r;
				assertTrue(arc > 34, g.id() + "'s lair is on a way down");
			}
			for (double[] pad : WindStreams.pads()) {
				assertTrue(Math.hypot(pad[0] - c.getX(), pad[1] - c.getZ()) > 40, g.id() + "'s lair is on a wind pad");
			}
			for (Guardian other : HOME.keySet()) {
				if (other != g) {
					BlockPos o = other.lairColumn();
					assertTrue(Math.abs(Math.atan2(o.getZ(), o.getX()) - theta) > 0.1 || other == Guardian.PHLEGYAS || g == Guardian.PHLEGYAS,
							g.id() + " and " + other.id() + " stand in a line");
				}
			}
		}
	}
}
