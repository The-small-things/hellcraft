package net.thesmallthings.hellcraft.blood;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /hellcraft loadout: a kit for testing (op only, works in single player with cheats on and on a server). The journey kits
 * give about what a player who played their way there would have, so a boss can be tested fairly without the grind:
 * <ul>
 *     <li>{@code upper}, upper Hell (Minos to Phlegyas): iron armour and tools, a bow, a shield, two golden apples;</li>
 *     <li>{@code lower}, lower Hell (the Furies to Geryon): part diamond armour, the Bloodletter, a few pearls and apples;</li>
 *     <li>{@code lucifer}, Cocytus, Lucifer and the Forge of Dis: enchanted diamond armour, the three hell weapons, a totem;</li>
 *     <li>{@code paradiso}, Purgatory and Paradiso (the Seraph): enchanted blood armour, more apples and pearls.</li>
 * </ul>
 * {@code full} (the default) and {@code blood} are everything at once, for trying things out rather than testing balance:
 * maxed netherite or blood armour, every weapon, 16 ender pearls and 16 enchanted golden apples, totems, potions, blocks.
 * Armour is put on where the slot is empty; the rest goes to the inventory.
 */
public final class Loadout {
	private Loadout() {
	}

	public enum Kit {
		UPPER("upper Hell (Minos to Phlegyas)"),
		LOWER("lower Hell (the Furies to Geryon)"),
		LUCIFER("Cocytus, Lucifer and the Forge of Dis"),
		PARADISO("Purgatory and Paradiso (the Seraph)"),
		FULL("everything, netherite"),
		BLOOD("everything, blood armour");

		public final String title;

		Kit(String title) {
			this.title = title;
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** Whether it's a kit for one stretch of the journey (not everything at once). */
		public boolean journey() {
			return this != FULL && this != BLOOD;
		}

		@Nullable
		public static Kit byId(String id) {
			if (id.equals("netherite")) {
				return FULL;
			}
			for (Kit kit : values()) {
				if (kit.id().equals(id)) {
					return kit;
				}
			}
			return null;
		}
	}

	/** Gives one player the kit; returns how many stacks it handed over. */
	public static int give(ServerPlayer player, Kit kit) {
		HolderLookup.RegistryLookup<Enchantment> e = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		List<ItemStack> items = new ArrayList<>();
		for (ItemStack piece : armour(kit, e)) {
			EquipmentSlot slot = slotOf(piece);
			if (slot != null && player.getItemBySlot(slot).isEmpty()) {
				player.setItemSlot(slot, piece);
			} else {
				items.add(piece);
			}
		}
		items.addAll(switch (kit) {
			case UPPER -> upper(e);
			case LOWER -> lower(e);
			case LUCIFER -> lucifer(e);
			case PARADISO -> paradiso(e);
			case FULL, BLOOD -> full(e);
		});
		for (ItemStack stack : items) {
			BloodItems.give(player, stack);
		}
		player.setHealth(player.getMaxHealth());
		player.getFoodData().setFoodLevel(20);
		player.getFoodData().setSaturation(20f);
		return items.size();
	}

	private static List<ItemStack> upper(HolderLookup.RegistryLookup<Enchantment> e) {
		return new ArrayList<>(List.of(
				enchanted(new ItemStack(Items.IRON_SWORD), e, Enchantments.SHARPNESS, 1),
				new ItemStack(Items.IRON_AXE), new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.BOW), new ItemStack(Items.SHIELD),
				new ItemStack(Items.ARROW, 32), new ItemStack(Items.GOLDEN_APPLE, 2), new ItemStack(Items.BREAD, 24),
				PotionContents.createItemStack(Items.POTION, Potions.HEALING),
				new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.TORCH, 32), new ItemStack(Items.COBBLESTONE, 64),
				BloodItems.vigil(1)));
	}

	private static List<ItemStack> lower(HolderLookup.RegistryLookup<Enchantment> e) {
		return new ArrayList<>(List.of(
				HellWeapons.create(HellWeapons.Weapon.BLOODLETTER),
				enchanted(new ItemStack(Items.DIAMOND_PICKAXE), e, Enchantments.EFFICIENCY, 2),
				new ItemStack(Items.IRON_AXE),
				enchanted(new ItemStack(Items.BOW), e, Enchantments.POWER, 2),
				enchanted(new ItemStack(Items.SHIELD), e, Enchantments.UNBREAKING, 1),
				new ItemStack(Items.ARROW, 48), new ItemStack(Items.GOLDEN_APPLE, 4), new ItemStack(Items.ENDER_PEARL, 4),
				new ItemStack(Items.COOKED_BEEF, 32),
				PotionContents.createItemStack(Items.POTION, Potions.HEALING),
				PotionContents.createItemStack(Items.POTION, Potions.HEALING),
				PotionContents.createItemStack(Items.POTION, Potions.FIRE_RESISTANCE),
				new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.TORCH, 32), new ItemStack(Items.COBBLESTONE, 64),
				BloodItems.fragment(4), BloodItems.vigil(1)));
	}

	private static List<ItemStack> lucifer(HolderLookup.RegistryLookup<Enchantment> e) {
		List<ItemStack> kit = new ArrayList<>();
		for (HellWeapons.Weapon weapon : HellWeapons.Weapon.values()) {
			kit.add(HellWeapons.create(weapon));
		}
		kit.addAll(List.of(
				enchanted(new ItemStack(Items.BOW), e, Enchantments.POWER, 3, Enchantments.UNBREAKING, 2),
				enchanted(new ItemStack(Items.SHIELD), e, Enchantments.UNBREAKING, 2),
				new ItemStack(Items.ARROW, 64), new ItemStack(Items.GOLDEN_APPLE, 8), new ItemStack(Items.ENCHANTED_GOLDEN_APPLE),
				new ItemStack(Items.TOTEM_OF_UNDYING), new ItemStack(Items.ENDER_PEARL, 8), new ItemStack(Items.COOKED_BEEF, 48),
				PotionContents.createItemStack(Items.POTION, Potions.LONG_FIRE_RESISTANCE),
				PotionContents.createItemStack(Items.POTION, Potions.LONG_FIRE_RESISTANCE)));
		for (int i = 0; i < 3; i++) {
			kit.add(PotionContents.createItemStack(Items.SPLASH_POTION, Potions.STRONG_HEALING));
		}
		// (flint and steel lights Cocytus's braziers)
		kit.addAll(List.of(new ItemStack(Items.FLINT_AND_STEEL), new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.TORCH, 64),
				new ItemStack(Items.COBBLESTONE, 64), BloodItems.fragment(8), BloodItems.vigil(2), BloodItems.anchor(1)));
		return kit;
	}

	private static List<ItemStack> paradiso(HolderLookup.RegistryLookup<Enchantment> e) {
		List<ItemStack> kit = new ArrayList<>();
		for (HellWeapons.Weapon weapon : HellWeapons.Weapon.values()) {
			kit.add(HellWeapons.create(weapon));
		}
		kit.addAll(List.of(
				enchanted(new ItemStack(Items.BOW), e, Enchantments.POWER, 4, Enchantments.UNBREAKING, 3),
				enchanted(new ItemStack(Items.SHIELD), e, Enchantments.UNBREAKING, 3),
				new ItemStack(Items.ARROW, 64), new ItemStack(Items.GOLDEN_APPLE, 12), new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 2),
				new ItemStack(Items.TOTEM_OF_UNDYING), new ItemStack(Items.ENDER_PEARL, 12), new ItemStack(Items.GOLDEN_CARROT, 64)));
		for (int i = 0; i < 4; i++) {
			kit.add(PotionContents.createItemStack(Items.SPLASH_POTION, Potions.STRONG_HEALING));
		}
		kit.addAll(List.of(new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.TORCH, 64), new ItemStack(Items.COBBLESTONE, 64),
				BloodItems.fragment(12), BloodItems.vigil(2), BloodItems.anchor(1)));
		return kit;
	}

	private static List<ItemStack> full(HolderLookup.RegistryLookup<Enchantment> e) {
		List<ItemStack> kit = new ArrayList<>();
		kit.add(enchanted(new ItemStack(Items.NETHERITE_SWORD), e, Enchantments.SHARPNESS, 5, Enchantments.SWEEPING_EDGE, 3,
				Enchantments.FIRE_ASPECT, 2, Enchantments.LOOTING, 3, Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));
		kit.add(enchanted(new ItemStack(Items.NETHERITE_AXE), e, Enchantments.SHARPNESS, 5, Enchantments.EFFICIENCY, 5,
				Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));
		kit.add(enchanted(new ItemStack(Items.BOW), e, Enchantments.POWER, 5, Enchantments.PUNCH, 2, Enchantments.FLAME, 1,
				Enchantments.INFINITY, 1, Enchantments.UNBREAKING, 3));
		kit.add(enchanted(new ItemStack(Items.CROSSBOW), e, Enchantments.QUICK_CHARGE, 3, Enchantments.MULTISHOT, 1,
				Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));
		kit.add(enchanted(new ItemStack(Items.SHIELD), e, Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));
		kit.add(enchanted(new ItemStack(Items.NETHERITE_PICKAXE), e, Enchantments.EFFICIENCY, 5, Enchantments.FORTUNE, 3,
				Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));
		for (HellWeapons.Weapon weapon : HellWeapons.Weapon.values()) {
			kit.add(HellWeapons.create(weapon));
		}
		kit.add(new ItemStack(Items.ARROW, 64));
		kit.add(new ItemStack(Items.ENDER_PEARL, 16));
		kit.add(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 16));
		kit.add(new ItemStack(Items.GOLDEN_APPLE, 32));
		kit.add(new ItemStack(Items.TOTEM_OF_UNDYING));
		kit.add(new ItemStack(Items.TOTEM_OF_UNDYING));
		kit.add(new ItemStack(Items.GOLDEN_CARROT, 64));
		kit.add(PotionContents.createItemStack(Items.POTION, Potions.LONG_FIRE_RESISTANCE));
		kit.add(PotionContents.createItemStack(Items.POTION, Potions.LONG_FIRE_RESISTANCE));
		kit.add(PotionContents.createItemStack(Items.POTION, Potions.LONG_NIGHT_VISION));
		for (int i = 0; i < 4; i++) {
			kit.add(PotionContents.createItemStack(Items.SPLASH_POTION, Potions.STRONG_HEALING));
		}
		kit.add(new ItemStack(Items.WATER_BUCKET));
		kit.add(new ItemStack(Items.FLINT_AND_STEEL));
		kit.add(new ItemStack(Items.TORCH, 64));
		kit.add(new ItemStack(Items.COBBLESTONE, 64));
		kit.add(new ItemStack(Items.COBBLESTONE, 64));
		kit.add(new ItemStack(Items.LADDER, 64));
		kit.add(BloodItems.fragment(16));
		kit.add(BloodItems.vigil(2));
		kit.add(BloodItems.anchor(1));
		return kit;
	}

	private static List<ItemStack> armour(Kit kit, HolderLookup.RegistryLookup<Enchantment> e) {
		List<ItemStack> out = new ArrayList<>();
		switch (kit) {
			case UPPER -> {
				out.add(new ItemStack(Items.IRON_HELMET));
				out.add(enchanted(new ItemStack(Items.IRON_CHESTPLATE), e, Enchantments.PROTECTION, 1));
				out.add(enchanted(new ItemStack(Items.IRON_LEGGINGS), e, Enchantments.PROTECTION, 1));
				out.add(new ItemStack(Items.IRON_BOOTS));
			}
			case LOWER -> {
				out.add(enchanted(new ItemStack(Items.DIAMOND_HELMET), e, Enchantments.PROTECTION, 2));
				out.add(enchanted(new ItemStack(Items.DIAMOND_CHESTPLATE), e, Enchantments.PROTECTION, 2));
				out.add(enchanted(new ItemStack(Items.IRON_LEGGINGS), e, Enchantments.PROTECTION, 1));
				out.add(enchanted(new ItemStack(Items.IRON_BOOTS), e, Enchantments.FEATHER_FALLING, 2));
			}
			case LUCIFER -> {
				for (Item item : new Item[]{Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS}) {
					out.add(enchanted(new ItemStack(item), e, Enchantments.PROTECTION, 3, Enchantments.UNBREAKING, 2));
				}
			}
			case PARADISO -> {
				for (BloodArmour.Piece piece : BloodArmour.Piece.values()) {
					out.add(enchanted(BloodArmour.create(piece), e, Enchantments.PROTECTION, 3, Enchantments.UNBREAKING, 3));
				}
			}
			case BLOOD -> {
				for (BloodArmour.Piece piece : BloodArmour.Piece.values()) {
					out.add(enchanted(BloodArmour.create(piece), e, Enchantments.PROTECTION, 4, Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));
				}
			}
			case FULL -> {
				Item[] pieces = {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS};
				for (Item item : pieces) {
					ItemStack stack = enchanted(new ItemStack(item), e, Enchantments.PROTECTION, 4, Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1);
					if (item == Items.NETHERITE_HELMET) {
						enchanted(stack, e, Enchantments.RESPIRATION, 3, Enchantments.AQUA_AFFINITY, 1);
					} else if (item == Items.NETHERITE_LEGGINGS) {
						enchanted(stack, e, Enchantments.SWIFT_SNEAK, 3);
					} else if (item == Items.NETHERITE_BOOTS) {
						enchanted(stack, e, Enchantments.FEATHER_FALLING, 4, Enchantments.DEPTH_STRIDER, 3, Enchantments.SOUL_SPEED, 3);
					}
					out.add(stack);
				}
			}
		}
		return out;
	}

	private static EquipmentSlot slotOf(ItemStack stack) {
		var equippable = stack.get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
		return equippable == null ? null : equippable.slot();
	}

	private static ItemStack enchanted(ItemStack stack, HolderLookup.RegistryLookup<Enchantment> lookup, Object... enchants) {
		for (int i = 0; i + 1 < enchants.length; i += 2) {
			@SuppressWarnings("unchecked")
			ResourceKey<Enchantment> key = (ResourceKey<Enchantment>) enchants[i];
			Holder<Enchantment> holder = lookup.getOrThrow(key);
			stack.enchant(holder, (Integer) enchants[i + 1]);
		}
		return stack;
	}
}
