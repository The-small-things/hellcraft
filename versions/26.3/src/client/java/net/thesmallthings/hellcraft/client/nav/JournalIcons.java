package net.thesmallthings.hellcraft.client.nav;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.thesmallthings.hellcraft.blood.BloodArmour;
import net.thesmallthings.hellcraft.blood.HellItems;
import net.thesmallthings.hellcraft.blood.HellWeapons;
import net.thesmallthings.hellcraft.blood.Relics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The pictures in the Pilgrim's Journal: the items a page is about (the Blood Heart on HEARTS, the hell weapons on HELL
 * WEAPONS), or something to know a Codex entry by (Cerberus's bone, Plutus's gold). The first is the icon beside the page in
 * the table of contents; the right-hand page shows them all, large, under its title (hover one for its tooltip).
 */
final class JournalIcons {
	private JournalIcons() {
	}

	private static final Map<String, List<ItemLike>> ICONS = new HashMap<>();

	static {
		// the Guide's pages, by title (blood/Guide)
		put("WHERE YOU ARE", Items.COMPASS, Items.FILLED_MAP);
		put("THE PILGRIM'S GUIDE", Items.WRITABLE_BOOK, Items.COMPASS);
		put("THE JOURNEY", Items.LANTERN, Items.IRON_SWORD, Items.BREAD);
		put("HEARTS", HellItems.BLOOD_HEART);
		put("BLOOD FRAGMENTS", HellItems.BLOOD_FRAGMENT, HellItems.BLOOD_HEART);
		put("OUT OF HEARTS", Items.SKELETON_SKULL, HellItems.VIGIL_CANDLE);
		put("FAIR PLAY", Items.SHIELD, Items.TOTEM_OF_UNDYING);
		put("BLOOD ALTARS", Items.RESPAWN_ANCHOR, Items.CRYING_OBSIDIAN, HellItems.BLOOD_FRAGMENT);
		put("PRESTIGE", Items.NETHER_STAR, HellItems.BLOOD_HEART);
		put("PRESTIGE BONUSES", Items.NETHER_STAR, Items.GOLDEN_APPLE);
		put("COMING BACK", HellItems.VIGIL_CANDLE, HellItems.SOUL_ANCHOR);
		put("FINDING THE WAY", Items.COMPASS, Items.FILLED_MAP, Items.LANTERN);
		put("THE CIRCLES", Items.SOUL_LANTERN, Items.MAGMA_CREAM, Items.BLUE_ICE);
		put("THE NOBLE CASTLE", Items.CHISELED_STONE_BRICKS, Items.WRITABLE_BOOK);
		put("THE FERRY", Items.OAK_BOAT, Items.SOUL_CAMPFIRE);
		put("COMMANDS", Items.WRITABLE_BOOK);
		put("LUCIFER", HellItems.LUCIFERS_BANE, Items.BLUE_ICE, Items.WITHER_SKELETON_SKULL);
		put("RIDES DOWN", Items.SADDLE, Items.LEAD);
		put("SECRET WAYS", Items.ENDER_EYE, Items.LADDER);
		put("STYLE", Items.DIAMOND_SWORD, Items.GOLD_INGOT);
		put("SUPPLIES", Items.BREAD, Items.TORCH, Items.ARROW, Items.GOLDEN_APPLE);
		put("THE CIRCLES' ENCHANTMENTS", Items.ENCHANTED_BOOK);
		put("THE CLIMB OUT", Items.LADDER, Items.SCAFFOLDING);
		put("THE FORGE OF DIS", Items.ANVIL, Items.LAVA_BUCKET, Items.MAGMA_BLOCK);
		put("THE GUARDIANS", Items.GOAT_HORN, Items.BONE);
		put("THE PILGRIMAGE", HellItems.PILGRIM_SHELL);
		put("THE SPINES", Items.BONE, Items.BONE_BLOCK);
		put("VIRGIL'S RESTS", Items.CAMPFIRE, Items.LANTERN);
		put("MINOS'S JUDGEMENT", Items.WRITABLE_BOOK, Items.NAME_TAG);
		List<ItemLike> armour = new ArrayList<>();
		for (BloodArmour.Piece piece : BloodArmour.Piece.values()) {
			armour.add(HellItems.armour(piece, false));
		}
		ICONS.put("BLOOD ARMOUR", armour);
		List<ItemLike> weapons = new ArrayList<>();
		for (HellWeapons.Weapon weapon : HellWeapons.Weapon.values()) {
			weapons.add(HellItems.weapon(weapon, false));
		}
		ICONS.put("HELL WEAPONS", weapons);
		List<ItemLike> relics = new ArrayList<>();
		for (Relics.Relic relic : Relics.Relic.values()) {
			relics.add(HellItems.relic(relic));
		}
		ICONS.put("PARADISO", relics);

		// the Codex, by id (codex/Codex): something to know each by
		put("codex:minos", HellItems.weapon(HellWeapons.Weapon.REAPER, false), Items.WRITABLE_BOOK);
		put("codex:cerberus", Items.BONE, Items.ROTTEN_FLESH);
		put("codex:plutus", Items.GOLD_BLOCK, Items.GOLD_NUGGET);
		put("codex:phlegyas", Items.OAK_BOAT, Items.FIRE_CHARGE);
		put("codex:furies", Items.WITHER_ROSE, Items.SPIDER_EYE);
		put("codex:farinata", Items.FIRE_CHARGE, Items.BLAZE_POWDER);
		put("codex:minotaur", Items.GOAT_HORN, Items.LEATHER);
		put("codex:geryon", Items.SADDLE, Items.FERMENTED_SPIDER_EYE);
		put("codex:vulcan", Items.ANVIL, Items.NETHERITE_INGOT);
		put("codex:seraph", Items.FEATHER, HellItems.relic(Relics.Relic.HALO));
		put("codex:emperor", Items.BLUE_ICE, HellItems.LUCIFERS_BANE);
		put("codex:lucifer", HellItems.LUCIFERS_BANE, Items.WITHER_SKELETON_SKULL);
		put("codex:hoarder", Items.GOLD_NUGGET, Items.CHEST);
		put("codex:lovers", Items.POPPY, Items.PHANTOM_MEMBRANE);
		put("codex:glutton", Items.ROTTEN_FLESH, Items.COOKED_PORKCHOP);
		put("codex:wrathful", Items.MUD, Items.IRON_SWORD);
		put("codex:heretic", Items.FIRE_CHARGE, Items.SOUL_TORCH);
		put("codex:centaur", Items.BOW, Items.ARROW);
		put("codex:harpy", Items.FEATHER, Items.DEAD_BUSH);
		put("codex:mimic", Items.CHEST, Items.NAME_TAG);
		put("codex:thief", Items.EMERALD, Items.STRING);
		put("codex:malebranche", Items.TRIDENT, Items.IRON_HOE);
		put("codex:cyclops", Items.ENDER_EYE, Items.IRON_INGOT);
	}

	private static void put(String key, ItemLike... items) {
		ICONS.put(key, List.of(items));
	}

	/** A Guide page's pictures, by its title; none if it has none. */
	static List<ItemStack> page(String title) {
		return stacks(ICONS.get(title));
	}

	/** A Codex entry's pictures, by its id. */
	static List<ItemStack> codex(String id) {
		return stacks(ICONS.get("codex:" + id));
	}

	private static List<ItemStack> stacks(List<ItemLike> items) {
		List<ItemStack> out = new ArrayList<>();
		if (items != null) {
			for (ItemLike item : items) {
				Item it = item.asItem();
				if (it != Items.AIR) {
					out.add(new ItemStack(it));
				}
			}
		}
		return out;
	}
}
