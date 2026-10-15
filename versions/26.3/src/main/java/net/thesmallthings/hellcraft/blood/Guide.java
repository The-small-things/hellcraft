package net.thesmallthings.hellcraft.blood;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.world.ParadisoGeometry;
import net.thesmallthings.hellcraft.world.Spine;

import java.util.ArrayList;
import java.util.List;

/**
 * "The Pilgrim's Guide", by Virgil: everything a new soul needs to know, page by page. The pages fill the Journal
 * screen (J), which the server sends them to (JournalPayload). The numbers come from the config, so they never go
 * stale.
 */
public final class Guide {
	private Guide() {
	}

	/** A page: a title and its lines (an empty line is a gap). */
	public record Page(String title, List<String> lines) {
	}

	public static List<Page> pages(MinecraftServer server) {
		HellConfig c = HellConfig.get();
		HellState.GlobalSpot altar = HellState.get(server).starterAltar;
		String altarAt = altar != null ? " (" + altar.pos().getX() + ", " + altar.pos().getY() + ", " + altar.pos().getZ() + ")" : "";
		BlockPos east = Spine.start(Spine.Way.EAST);

		List<Page> pages = new ArrayList<>();
		pages.add(page("THE PILGRIM'S GUIDE",
				"",
				"Abandon all hope, ye who enter here.",
				"",
				"This world is Dante's Inferno: nine rings, called circles, around a pit. Lucifer waits at the centre (0, 0).",
				"",
				"  — Virgil"));
		pages.add(page("HEARTS",
				"You begin with " + c.startHearts + " hearts and can hold " + c.maxHearts + ".",
				"",
				"Kill a player: take a heart. Die to one: lose a heart.",
				c.pveDeathsCostHearts ? "Monsters and the circles take hearts too." : "Monsters and the circles can't take your hearts.",
				"",
				"/withdraw turns your hearts into Blood Heart items. Right-click one to get the heart back."));
		pages.add(page("BLOOD FRAGMENTS",
				"Monsters killed by players drop Blood Fragments, more the deeper you go.",
				"",
				"Hold " + c.fragmentsPerHeart + " in one stack and right-click to turn them into a Blood Heart.",
				"",
				"They also craft hell weapons and blood armour."));
		pages.add(page("OUT OF HEARTS",
				"At 0 hearts you become a ghost and can't leave the place you died.",
				"",
				"A friend can revive you at a Blood Altar for " + c.reviveCostHearts + " Blood Hearts. You come back with "
						+ c.reviveHearts + " hearts.",
				"",
				"/revive explains how. Ghosts can use /ghost."));
		pages.add(page("FAIR PLAY",
				c.sanctuaryNoPvp ? "No fighting other players in the Dark Wood, on the Shore or in Limbo." : "Players can fight anywhere.",
				"",
				c.newcomerGraceMinutes > 0 ? "New souls: for your first " + c.newcomerGraceMinutes + " minutes of play, players can't take your hearts. "
						+ "\"(new)\" shows after your name. Attack a player and it ends." : "",
				"",
				"Killing someone with less than half your hearts takes nothing, and gives you Cain's Mark: you glow for "
						+ c.cainMarkMinutes + " minutes, and whoever kills you gets " + c.cainBountyHearts + " Blood Hearts."));
		pages.add(page("BLOOD ALTARS",
				"Build one: a respawn anchor on a 3x3 of crying obsidian. There is one by the Gate of Hell" + altarAt + ".",
				"",
				"Right-click it to revive a ghost, buy a Ward (protection from circle hazards), or set your respawn there"
						+ (c.bindCostHearts == 0 ? " (free)." : " (" + c.bindCostHearts + " Blood Hearts).")));
		if (c.prestige) {
			pages.add(page("PRESTIGE",
					"There are 7 prestige ranks, called the Seven P's.",
					"",
					"At max hearts, press Ascend at a Blood Altar. Your hearts drop to " + c.prestigeResetHearts
							+ ", but your max goes up by " + c.prestigeHeartBonus + " for good.",
					"",
					"Each rank also gives a bonus that lasts."));
			pages.add(page("PRESTIGE BONUSES",
					"1: less fall damage",
					"2: more Blood Fragments",
					"3: less knockback",
					"4: faster walking",
					"5: better loot (luck)",
					"6: slower hunger",
					"7: immune to circle hazards"));
		}
		pages.add(page("COMING BACK",
				"Vigil Candle (craft: torch, bone, string): right-click to light it where you stand. Next time you die, you respawn there.",
				"",
				"Soul Anchor (from bosses and loot): keep it in your inventory. Next time you die, you come back where you died."));
		pages.add(page("FINDING THE WAY",
				"Four roads (east, south, west and north) run from the Gate of Hell down to the Well of Giants. Follow one and you can't get lost.",
				"",
				"Off the road, every cliff has a way down every 30 degrees, like the hours on a clock.",
				"",
				"The compass at the top of the screen points to the next way down (red arrow) and the nearest Rest (gold diamond).",
				"",
				"M opens the Map of Hell. J opens the Pilgrim's Journal: its first page says where you are and where to go next."));
		pages.add(page("THE CIRCLES",
				"Each has a rule. What to do:",
				"Lust: the white wind streams carry you down the circle (step in; sneak to let go)",
				"Gluttony: the mud sinks you if you stand still (keep moving, or stand on roots, stone, planks)",
				"Greed: gold draws hoarders that steal it (stash it, or kill them to get it back)",
				"Wrath: hands drag you under where the Styx bubbles (keep to the roads, or take a boat)",
				"Heresy: darkness (a roof)",
				"Sands: falling fire (roof, water)",
				"Fraud: invisible foes (watch for swirls)",
				"Treachery: cold (leather, campfire)"));
		pages.add(page("THE NOBLE CASTLE",
				"Halfway across Limbo, the east road runs through a castle of seven walls. Inside is the only green meadow in Hell.",
				"",
				"The shades of the great poets and thinkers live there. Right-click one to talk: a tip, a page for your Codex, and trades for emeralds.",
				"",
				"Ovid sells potions for Lust's wind and the Styx; Aristotle an ender chest to keep gold from the hoarders."));
		pages.add(page("THE FERRY",
				"A watchtower stands on the Fen's shore beside each road, with two beacons on top.",
				"",
				"Light both (flint and steel) and a ferry comes out of the fog. Right-click it to board: it carries you across the Styx to the gate of Dis.",
				"",
				"Phlegyas, the ferryman, waits at his landing south of the east road: walk into his ring of braziers, or light the east tower's beacons, and he rises. Beat him and his ferry is yours."));
		pages.add(page("VIRGIL'S RESTS",
				"A safe camp beside each road in every circle: soul fire, a supply chest and a Blood Altar.",
				"",
				"No hazards or monsters there. Set your respawn at its altar.",
				"",
				"Travel (at any altar) teleports you to a Rest you've found.",
				"",
				"/circle shows the nearest one."));
		pages.add(page("HELL WEAPONS",
				"Craft the Bloodletter with Blood Fragments. The Reaper of Minos, Tithe Axe and Blood Pickaxe also cost Blood Hearts.",
				"",
				"Hits and kills charge them. When full, right-click for a special attack.",
				"",
				"Sneak + right-click (Blood Oath): pay 3 hearts of health for 30 s of full power."));
		pages.add(page("BLOOD ARMOUR",
				"Craft diamond armour with 4 Blood Fragments per piece.",
				"",
				"2 pieces: your hits heal you. 4 pieces: more healing, Regeneration when nearly dead, and Resistance during a Blood Oath."));
		pages.add(page("THE GUARDIANS",
				"Eight bosses wait just south of the east road (the one from spawn), each in a ring of soul fire, with a sign by the road:",
				"Minos (Lust), Cerberus (Gluttony), Plutus (Greed), Phlegyas (the Fen of Styx)",
				"The Furies (inside the Walls of Dis), Farinata (Heresy)",
				"The Minotaur (above the river of blood), Geryon (the edge of the great cliff)",
				"None blocks the way down. First win against each: " + c.guardianHearts + " Blood Hearts, a Soul Anchor and enchanted books."));
		pages.add(page("RIDES DOWN",
				"Geryon: beat him, then right-click him at the brink of the great cliff. He flies you down to Malebolge, and back up.",
				"",
				"Antaeus: below the Well of Giants. Wait on the crying obsidian by his hand and he sets you down on the ice; wait on the one "
						+ "below to go back up.",
				"",
				"Don't sneak while riding: you'd let go."));
		pages.add(page("STYLE",
				"Bosses rank your STYLE: D C B A S SS SSS.",
				"Hits fill the bar. Swap weapons: one used again and again goes stale. Crits, smashes, parries score extra.",
				"Getting hit or holding back drains it.",
				"Spoils follow your average rank. First S and first SSS on a boss: a Blood Heart each."));
		pages.add(page("SUPPLIES",
				"Villages and traders: in the Dark Wood and Limbo. Sugar cane grows by water.",
				"",
				"Loot chests: ruined altars, Virgil's Rests and the tombs in Heresy."));
		pages.add(page("THE SPINES",
				"Four giant spines lead from the edge of the Well of Giants down to Lucifer's pit (north, east, south, west).",
				"",
				"East: " + east.getX() + ", " + east.getY() + ", " + east.getZ(),
				"",
				"No monsters spawn on them."));
		pages.add(page("THE FORGE OF DIS",
				"The Nether's Great Forge is at 0, 70, 0. Vulcan, a boss, waits there.",
				"",
				"Hellforge: any anvil on a magma block in the Nether. It upgrades blood gear for a netherite ingot + " + c.hellforgeCostFragments + " Blood Fragments."));
		pages.add(page("PARADISO (THE END)",
				"The Ender Dragon is the Seraph: beat it for a Halo.",
				"",
				"Then pearl into a gateway portal to the outer islands: nine rings, each with End cities (Seraph Wings) and chorus.",
				"",
				"There you get a Rose Compass to the Celestial Rose (x " + ParadisoGeometry.ROSE_X + ")."));
		pages.add(page("LUCIFER",
				"The final boss, in the pit at the centre (0, 0). Once you go in, the way out is sealed until the fight ends.",
				"",
				"He fights in three phases: the Morning Star, the Morning Star enraged, then the Emperor (a Wither).",
				"",
				"Win and you choose a reward (/lucifer reward)."));
		pages.add(page("THE CLIMB OUT",
				"When Lucifer dies, a tunnel opens in the middle of the pit.",
				"",
				"It leads up to Purgatory: a mountain of seven ledges with a garden on top."));
		pages.add(page("COMMANDS",
				"/hearts: your hearts",
				"/withdraw [n]: turn hearts into Blood Hearts",
				"/circle: where you are, and the nearest Rest",
				"/revive [name]: bring a ghost back",
				"/guide: open this Journal",
				"",
				"Go with God. He is not down here."));

		return pages;
	}

	private static Page page(String title, String... lines) {
		return new Page(title, List.of(lines));
	}
}
