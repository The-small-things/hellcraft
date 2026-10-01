package net.thesmallthings.hellcraft.blood;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.world.ParadisoGeometry;
import net.thesmallthings.hellcraft.world.Spine;

import java.util.ArrayList;
import java.util.List;

/**
 * "The Pilgrim's Guide", by Virgil: everything a new soul needs to know, in a written book. The
 * numbers come from the config, so the book never goes stale. New players get one on their first
 * join; {@code /guide} hands out another.
 */
public final class GuideBook {
	private GuideBook() {
	}

	public static ItemStack create(MinecraftServer server) {
		HellConfig c = HellConfig.get();
		HellState.GlobalSpot altar = HellState.get(server).starterAltar;
		String altarAt = altar != null ? " (" + altar.pos().getX() + ", " + altar.pos().getY() + ", " + altar.pos().getZ() + ")" : "";
		BlockPos east = Spine.start(Spine.Way.EAST);

		List<Component> pages = new ArrayList<>();
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
		pages.add(page("THE CIRCLES",
				"Each has a hazard. To avoid it:",
				"Lust: wind (sneak)",
				"Gluttony: hunger (a roof)",
				"Greed: gold slows you (store it)",
				"Wrath: the river Styx (use a boat)",
				"Heresy: darkness (a roof)",
				"Sands: falling fire (roof, water)",
				"Fraud: invisible foes (watch for swirls)",
				"Treachery: cold (leather, campfire)"));
		pages.add(page("VIRGIL'S RESTS",
				"A safe camp in each circle, where the ramp comes down: soul fire, a supply chest and a Blood Altar.",
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
				"Five bosses wait on the road from the Gate to the centre (z = 0), each in a ring of soul fire:",
				"Minos (x 4050)",
				"Cerberus (3575)",
				"Plutus (3125)",
				"Minotaur (1600)",
				"Geryon (1480)",
				"First win against each: " + c.guardianHearts + " Blood Hearts, a Soul Anchor and enchanted books."));
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
				"/hearts",
				"/withdraw [n]",
				"/circle",
				"/revive [name]",
				"/guide",
				"",
				"Go with God. He is not down here."));

		List<Filterable<Component>> filtered = new ArrayList<>();
		for (Component p : pages) {
			filtered.add(Filterable.passThrough(p));
		}
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("The Pilgrim's Guide"), "Virgil", 0, filtered, true));
		return book;
	}

	/** A page: a dark red title line, then plain lines. */
	private static Component page(String title, String... lines) {
		MutableComponent page = Component.literal(title).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
		for (String line : lines) {
			page.append(Component.literal("\n" + line).withStyle(s -> s.withBold(false).withColor(ChatFormatting.BLACK)));
		}
		return page;
	}
}
