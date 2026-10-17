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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /hellcraft loadout: a full kit for testing (op only, works in single player with cheats on and on a server). Enchanted
 * armour, put on where the slot is empty (netherite, or Hellcraft's blood armour), every weapon (netherite, a bow,
 * a crossbow, a shield and the three hell weapons), ender pearls, enchanted golden apples, totems, food, potions,
 * blocks and torches, and a few Blood Fragments, Vigil Candles and a Soul Anchor.
 */
public final class Loadout {
	private Loadout() {
	}

	public enum Armour {
		NETHERITE, BLOOD;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** Gives one player the kit; returns how many stacks it handed over. */
	public static int give(ServerPlayer player, Armour armour) {
		HolderLookup.RegistryLookup<Enchantment> e = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		List<ItemStack> kit = new ArrayList<>();
		for (ItemStack piece : armour(armour, e)) {
			EquipmentSlot slot = slotOf(piece);
			if (slot != null && player.getItemBySlot(slot).isEmpty()) {
				player.setItemSlot(slot, piece);
			} else {
				kit.add(piece);
			}
		}
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
		for (ItemStack stack : kit) {
			BloodItems.give(player, stack);
		}
		player.setHealth(player.getMaxHealth());
		player.getFoodData().setFoodLevel(20);
		player.getFoodData().setSaturation(20f);
		return kit.size();
	}

	private static List<ItemStack> armour(Armour armour, HolderLookup.RegistryLookup<Enchantment> e) {
		List<ItemStack> out = new ArrayList<>();
		if (armour == Armour.BLOOD) {
			for (BloodArmour.Piece piece : BloodArmour.Piece.values()) {
				out.add(enchanted(BloodArmour.create(piece), e, Enchantments.PROTECTION, 4, Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));
			}
			return out;
		}
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
