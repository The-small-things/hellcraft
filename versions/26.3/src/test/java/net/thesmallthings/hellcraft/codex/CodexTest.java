package net.thesmallthings.hellcraft.codex;

import net.thesmallthings.hellcraft.hazard.guardian.Guardian;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodexTest {
	/** Every guardian of Hell (not the Seraph, who comes after him) says something about Lucifer, once (L1). */
	@Test
	void everyGuardianOfHellMentionsLucifer() {
		Set<String> said = new HashSet<>();
		for (Codex.Rumour r : Codex.RUMOURS) {
			assertNotNull(Guardian.byId(r.guardian()), r.guardian() + " is not a guardian");
			assertTrue(said.add(r.guardian()), r.guardian() + " has two rumours");
		}
		for (Guardian g : Guardian.values()) {
			if (g != Guardian.SERAPH) {
				assertNotNull(Codex.rumour(g.id()), g.id() + " says nothing about Lucifer");
			}
		}
		assertEquals(Codex.EMPEROR, Codex.byId("emperor"));
	}
}
