package net.thesmallthings.hellcraft.hazard;

import net.thesmallthings.hellcraft.hazard.guardian.LairLifts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LairLiftsTest {
	/** Every guardian's lift (REVAMP Q2) lands in the circle below its lair, on dry ground. */
	@Test
	void everyLiftLandsInTheNextCircleOnDryGround() {
		String test = LairLifts.selfTest();
		assertTrue(test.startsWith("Lifts test OK"), test);
	}
}
