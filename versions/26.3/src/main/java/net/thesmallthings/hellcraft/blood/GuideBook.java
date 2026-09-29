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
				"This world is Dante's Inferno. Walk inward, circle by circle, to the Emperor frozen at its heart.",
				"",
				"  — Virgil"));
		pages.add(page("BLOOD IS FUEL",
				"You begin with " + c.startHearts + " hearts and can hold " + c.maxHearts + ".",
				"",
				"Kill a player: take a heart. Die to one: lose a heart.",
				c.pveDeathsCostHearts ? "Monsters and the circles take hearts too." : "Monsters and the circles can't take your hearts.",
				"",
				"/withdraw bleeds your hearts into Blood Hearts. Right-click one to drink it back."));
		pages.add(page("BLOOD FRAGMENTS",
				"Monsters killed by players drop Blood Fragments, more the deeper you go.",
				"",
				"Right-click " + c.fragmentsPerHeart + " in one stack to clot them into a Blood Heart.",
				"",
				"Hell weapons spend fragments for power."));
		pages.add(page("HELL IS FULL",
				"At 0 hearts you become a ghost, bound to where you fell.",
				"",
				"Your friends revive you at a Blood Altar for " + c.reviveCostHearts + " Blood Hearts; you rise with "
						+ c.reviveHearts + " hearts.",
				"",
				"Type /revive to learn how. Ghosts: /ghost"));
		pages.add(page("BLOOD ALTARS",
				"A respawn anchor on 3x3 crying obsidian. One stands by the Gate of Hell" + altarAt + ".",
				"",
				"Right-click it: revive the dead, buy a Ward against the circles, or bind your respawn to it"
						+ (c.bindCostHearts == 0 ? " (free)." : " (" + c.bindCostHearts + " Blood Hearts).")));
		pages.add(page("A WAY BACK",
				"Vigil Candle (torch + bone + string): right-click to light it. Your next death wakes you beside it.",
				"",
				"Soul Anchor (from guardians and deep loot): carry it, and you rise where you fell."));
		pages.add(page("THE CIRCLES",
				"Each torments the living, and each has a way out:",
				"Lust: wind (sneak)",
				"Gluttony: hunger (a roof)",
				"Greed: gold weighs (stash it)",
				"Wrath: the Styx (a boat)",
				"Heresy: darkness (a roof)",
				"Sands: fire (roof, water)",
				"Fraud: hidden foes (look for their swirl)",
				"Treachery: cold (leather, a campfire)"));
		pages.add(page("VIRGIL'S RESTS",
				"Where the ramps come down into each circle stands a Rest: soul fire, a supply chest and a Blood Altar.",
				"",
				"Nothing torments you there and no monster follows you in. Bind your respawn at its altar.",
				"",
				"Any altar's Travel button takes you back to a Rest you've reached.",
				"",
				"/circle shows the nearest."));
		pages.add(page("HELL WEAPONS",
				"Forge the Bloodletter, the Reaper of Minos and the Tithe Axe from Blood Fragments.",
				"",
				"Hits and kills fill them with blood. Right-click when full: its Blood Art.",
				"",
				"Sneak + right-click: a Blood Oath, 3 hearts of health for 30 s of full power."));
		pages.add(page("BLOOD ARMOUR",
				"Reforge diamond armour with 4 Blood Fragments a piece.",
				"",
				"Two pieces: your blows heal you. All four: more, a Blood Rush near death, and Resistance under an oath."));
		pages.add(page("THE GUARDIANS",
				"On the road in from the Gate (along x, z = 0) five guardians wait in rings of soul fire:",
				"Minos (x 4050)",
				"Cerberus (3575)",
				"Plutus (3125)",
				"Minotaur (1600)",
				"Geryon (1480)",
				"Each is worth " + c.guardianHearts + " Blood Hearts, a Soul Anchor and enchanted books."));
		pages.add(page("SUPPLIES",
				"Villages and traders wait in the Dark Wood and Limbo. Sugar cane grows by the water.",
				"",
				"Ruined altars, Virgil's Rests and the heretics' tombs hide arrows, books and enchantments."));
		pages.add(page("THE SPINES",
				"On the rim of the Well of Giants, four great spines reach down to Lucifer's pit: north, east, south and west.",
				"",
				"East: " + east.getX() + ", " + east.getY() + ", " + east.getZ(),
				"",
				"Nothing hunts you there."));
		pages.add(page("THE FORGE OF DIS",
				"The Nether is Hell's workshop. At its centre (0, 70, 0) stands the Great Forge, where Vulcan works.",
				"",
				"Its Hellforge (any anvil on magma, in the Nether) makes blood gear infernal: netherite + " + c.hellforgeCostFragments + " Blood Fragments."));
		pages.add(page("PARADISO",
				"The End is heaven. Past the Seraph's island, nine spheres ring the void: the Moon to the Primum Mobile.",
				"",
				"Beyond them all, at 6400 blocks out, the Empyrean."));
		pages.add(page("LUCIFER",
				"He waits in the pit at the very centre (0, 0). Enter it and the ice seals you in.",
				"",
				"The Seraph, the Morning Star, then the Emperor frozen in the ice.",
				"",
				"Victors choose a reward: /lucifer reward"));
		pages.add(page("THE CLIMB OUT",
				"When Lucifer falls, a burrow opens where he was frozen.",
				"",
				"It leads up to Purgatory: seven terraces, the Earthly Paradise, and its two streams, Lethe and Eunoë."));
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
