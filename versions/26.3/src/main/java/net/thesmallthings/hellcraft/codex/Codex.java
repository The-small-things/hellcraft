package net.thesmallthings.hellcraft.codex;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Codex (B2): one entry per boss and per monster of note, opened the first time you meet it (or when a shade of
 * Limbo hands you its page: Shades) and read in the Journal (J). Each says who it is, in two or three plain sentences
 * after Dante, how to fight it, and quotes the poem. The lore is optional: the fight's own tips say everything you
 * need.
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

	public static final Entry CERBERUS = new Entry("cerberus", "CERBERUS", "the Great Worm",
			List.of("The three-headed hound of the old stories, grown into a worm of the mire. He guards the gluttons in the "
							+ "endless rain of the third circle, and barks over them until they wish they were deaf.",
					"Virgil quieted him once with a fistful of earth thrown into his throats. You will need more than that."),
			List.of("Each head fights on its own: watch the one that is about to strike.",
					"White flash: a head bites. Block it with your shield and that head reels.",
					"Red cone: all three maws at once, in front of him. Get behind him.",
					"Red puddles: his filth. Keep off them: they slow and sicken you.",
					"Purple ring: his howl. It can't be blocked: be out of its reach or be stunned."),
			"Cerberus, a monster fierce and strange, with three gullets doglike barketh over the people that are there submerged.",
			"Inferno VI");

	public static final Entry PLUTUS = new Entry("plutus", "PLUTUS", "the Great Enemy",
			List.of("The old god of riches, swollen with all the gold of the fourth circle. He keeps the Mint where the "
							+ "misers and the spendthrifts roll their weights at each other forever.",
					"He shrieks words no one understands. Virgil's answer was to tell him to be silent, and he fell like a sail when the mast breaks."),
			List.of("Leave your gold behind before you face him: his gold rain hits harder the more you carry.",
					"Red circles: gold falls there. Step out.",
					"White flash: his fist. Block it and he reels.",
					"Red line: he hurls himself along it. Step aside.",
					"Purple: \"Pape Satàn\", a shriek that can't be blocked. Get away from him before it sounds."),
			"\"Pape Satàn, Pape Satàn aleppe!\" thus Plutus with his clucking voice began.",
			"Inferno VII");

	public static final Entry PHLEGYAS = new Entry("phlegyas", "PHLEGYAS", "the Ferryman of the Styx",
			List.of("A king who burned a god's temple in his rage, now the boatman of the fifth circle. A beacon on the "
							+ "watchtower calls him, and his ferry crosses the marsh to the burning walls of Dis.",
					"He thinks every soul he carries is one more of the wrathful, and treats them so."),
			List.of("Light the watchtower's beacon to call him.",
					"White flash: his oar. Block it and he reels.",
					"Red: the water churns where his oar will come down. Get out of it.",
					"Beat him and his ferry is yours: it carries you across to the Walls of Dis."),
			"\"Phlegyas, Phlegyas, thou criest out in vain for this once,\" my Lord said; \"longer thou shalt not have us than in passing o'er the mire.\"",
			"Inferno VIII");

	public static final Entry HOARDER = new Entry("hoarder", "THE HOARDERS", "the Misers of the Fourth Circle",
			List.of("The misers of Greed, bent under sacks of coin. In the poem they roll great weights against the "
							+ "spendthrifts forever; these have found something lighter to carry: other people's gold.",
					"They can smell gold through a pack, and they come for whoever carries the most."),
			List.of("In Greed, carrying gold (or diamonds, emeralds, netherite) draws them: the more you carry, the more come.",
					"Each blow steals some of your treasure. Kill the thief and it drops everything it took.",
					"Stash your gold (an ender chest keeps it anywhere) and they lose interest."),
			"Here saw I people, more than elsewhere, many, on one side and the other, with great howls, rolling weights forward by main force of chest.",
			"Inferno VII");

	public static final List<Entry> ALL = List.of(MINOS, CERBERUS, PLUTUS, PHLEGYAS, HOARDER);

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
