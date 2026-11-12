package net.thesmallthings.hellcraft.world;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lust's wind streams: worlds made before 3.4 keep their 36; every new world draws 72 of its own, clear of everything. */
class WindStreamsTest {
	private static final long[] SEEDS = {1L, 2L, 42L, -7L, 123456789L, 0x5EEDL, Long.MAX_VALUE, Long.MIN_VALUE + 1, 987654321987L, 31337L};

	@AfterEach
	void backToTheFirstLayout() {
		WindStreams.use(WindStreams.LEGACY);
	}

	@Test
	void oldWorldsKeepTheirStreams() {
		WindStreams.use(WindStreams.LEGACY);
		assertEquals(WindStreams.LEGACY_COUNT, WindStreams.all().size());
		assertArrayEquals(WindStreams.legacyPads(), WindStreams.pads());
		// the first stream of the first layout, as it always was: from the rim of Lust to the cliff above Gluttony
		double[] start = WindStreams.pads()[0];
		assertEquals(InfernoGeometry.outerRadius(Zone.LUST) - 20, Math.hypot(start[0], start[1]), 1e-6);
		double[] end = WindStreams.pads()[1];
		assertEquals(InfernoGeometry.outerRadius(Zone.GLUTTONY) + 25, Math.hypot(end[0], end[1]), 1e-6);
	}

	@Test
	void everyNewWorldHasItsOwnDenserStreams() {
		double[][] first = null;
		for (long worldSeed : SEEDS) {
			long seed = WindStreams.seedFor(worldSeed);
			assertTrue(seed != WindStreams.LEGACY);
			WindStreams.use(seed);
			int n = WindStreams.all().size();
			assertTrue(n >= WindStreams.COUNT - 2, "seed " + worldSeed + ": only " + n + " streams");
			double[][] pads = WindStreams.pads();
			if (first == null) {
				first = pads;
			} else {
				assertFalse(Arrays.deepEquals(first, pads), "two worlds have the same streams");
			}
			// the same world always gets the same streams
			WindStreams.use(WindStreams.LEGACY);
			WindStreams.use(seed);
			assertArrayEquals(pads, WindStreams.pads());
		}
	}

	@Test
	void padsKeepClearOfEverything() {
		for (long worldSeed : SEEDS) {
			WindStreams.use(WindStreams.seedFor(worldSeed));
			double[][] pads = WindStreams.pads();
			for (int i = 0; i < pads.length; i++) {
				double x = pads[i][0];
				double z = pads[i][1];
				String pad = "seed " + worldSeed + ", pad " + i + " at " + (int) x + ", " + (int) z;
				assertEquals(Zone.LUST, InfernoGeometry.zoneAt(x, z), pad + " is outside Lust");
				assertEquals(0.0, InfernoGeometry.roadLevel(x, z), pad + " is on a road");
				assertFalse(InfernoGeometry.nearLair(x, z, 40), pad + " is in a lair");
				assertFalse(SecretWays.near(x, z, 16), pad + " is on a secret way");
				for (ShrineSites.Site s : ShrineSites.all()) {
					assertTrue(Math.hypot(s.x() - x, s.z() - z) > 30, pad + " is on a Rest");
				}
				for (int j = i + 1; j < pads.length; j++) {
					assertTrue(Math.hypot(pads[j][0] - x, pads[j][1] - z) >= 20, pad + " crowds pad " + j);
				}
			}
		}
	}

	@Test
	void streamsStayInLustAndCanBeRidden() {
		WindStreams.use(WindStreams.seedFor(42L));
		for (WindStreams.Stream s : WindStreams.all()) {
			for (int i = 0; i < s.size(); i++) {
				double x = s.x()[i];
				double z = s.z()[i];
				assertEquals(Zone.LUST, InfernoGeometry.zoneAt(x, z), "stream " + s.index() + " leaves Lust");
				assertTrue(s.y()[i] >= InfernoGeometry.baseHeight(x, z) + 1.4, "stream " + s.index() + " runs underground");
			}
			// standing in its middle, the wind takes you, and blows the way the stream runs
			int mid = s.size() / 2;
			WindStreams.Hit hit = WindStreams.find(s.x()[mid], s.y()[mid], s.z()[mid]);
			assertTrue(hit != null && hit.distance() < 1e-6, "stream " + s.index() + " can't be found from its own middle");
		}
	}
}
