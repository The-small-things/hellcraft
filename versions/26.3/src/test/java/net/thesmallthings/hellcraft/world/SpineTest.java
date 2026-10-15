package net.thesmallthings.hellcraft.world;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpineTest {
	@Test
	void bendLeavesTheEndsInPlace() {
		for (Spine.Way way : Spine.Way.values()) {
			assertEquals(0, Spine.bend(way, Spine.START), way.id + " start");
			assertEquals(0, Spine.bend(way, Spine.END), way.id + " end");
			assertEquals(0, Spine.bend(way, Spine.START + 5), way.id + " sign");
		}
	}

	@Test
	void walkwayStaysWhole() {
		for (Spine.Way way : Spine.Way.values()) {
			int most = 0;
			for (int u = Spine.END; u < Spine.START; u++) {
				int step = Math.abs(Spine.bend(way, u + 1) - Spine.bend(way, u));
				assertTrue(step <= 1, way.id + " jumps " + step + " at u=" + u);
				most = Math.max(most, Math.abs(Spine.bend(way, u)));
			}
			// it really does wander, but stays inside its sanctuary
			assertTrue(most >= 6 && most <= 16, way.id + " bends at most " + most);
		}
	}

	@Test
	void spinesAreNotAlike() {
		Set<List<Integer>> shapes = new HashSet<>();
		for (Spine.Way way : Spine.Way.values()) {
			shapes.add(List.of(Spine.bend(way, 150), Spine.bend(way, 250), Spine.bend(way, 350)));
		}
		assertEquals(Spine.Way.values().length, shapes.size());
	}
}
