package net.thesmallthings.hellcraft.codex;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Codex (B2): one entry per boss, opened the first time you meet it and read in the Journal (J). Each says who it
 * is, in two or three plain sentences after Dante, how to fight it, and quotes the poem. The lore is optional: the
 * fight's own tips say everything you need.
 */
public final class Codex {
	private Codex() {
	}

	/** An entry: its title and subtitle, who it is, how to fight it, and a quote (with where it is from). */
	public record Entry(String id, String title, String subtitle, List<String> who, List<String> fight, String quote, String source) {
	}

	public static final Entry MINOS = new Entry("minos", "MINOS", "Judge of the Damned",
			List.of("Once a king of Crete, now the judge at the gate of the second circle. Every soul that comes down "
							+ "confesses to him, and he wraps his tail around himself once for each circle it must sink.",
					"He has judged since before Rome. He never asks whose law he keeps; the verdicts come up from below."),
			List.of("White flash, white ground: his claw. Raise your shield as it lands and he reels, his armour gone, for two seconds.",
					"Purple ring: his tail sweeps all the way round. Jump as it reaches you, or stand off past its tip.",
					"Red circle: a sentence. It follows you, stops, then the storm falls there. Keep moving.",
					"Red line: his coil. Step out of the line. If a friend is caught, hit him hard to free them.",
					"At half his health he sentences everyone at once, and attacks faster."),
			"There standeth Minos horribly, and snarls; examines the transgressions at the entrance; judges, and sends "
					+ "according as he girds him.",
			"Inferno V");

	public static final List<Entry> ALL = List.of(MINOS);

	@Nullable
	public static Entry byId(String id) {
		for (Entry e : ALL) {
			if (e.id().equals(id)) {
				return e;
			}
		}
		return null;
	}
}
