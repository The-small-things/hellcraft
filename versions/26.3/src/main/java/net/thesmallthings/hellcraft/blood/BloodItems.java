package net.thesmallthings.hellcraft.blood;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

/**
 * Blood Hearts and Blood Fragments. Vanilla clients can't see new items, so these are vanilla items
 * marked with {@code custom_data {hellcraft: "heart" | "fragment"}} plus a name and lore. Only
 * items carrying that marker count, so ordinary red dye or spider eyes are never blood.
 */
public final class BloodItems {
	private BloodItems() {
	}

	public static final String KEY = "hellcraft";
	public static final String HEART = "heart";
	public static final String FRAGMENT = "fragment";
	public static final String BANE = "bane";

	/** Base items. Change these two lines to re-skin the blood items. */
	public static final Item HEART_BASE = Items.FERMENTED_SPIDER_EYE;
	public static final Item FRAGMENT_BASE = Items.DYE.red();
	/** The Morning Star's bane: a nether star. */
	public static final Item BANE_BASE = Items.NETHER_STAR;

	public static ItemStack heart(int count) {
		ItemStack stack = new ItemStack(HEART_BASE, count);
		mark(stack, HEART);
		stack.set(DataComponents.ITEM_NAME, Component.literal("Blood Heart").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
				Component.literal("A heart torn from the living.").withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(false)),
				Component.literal("Right-click to take it as your own.").withStyle(s -> s.withColor(ChatFormatting.RED).withItalic(false)),
				Component.literal("Offer it at a blood altar for its rites.").withStyle(s -> s.withColor(ChatFormatting.DARK_GRAY).withItalic(false)))));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		stack.set(DataComponents.RARITY, Rarity.EPIC);
		return stack;
	}

	public static ItemStack fragment(int count) {
		ItemStack stack = new ItemStack(FRAGMENT_BASE, count);
		mark(stack, FRAGMENT);
		stack.set(DataComponents.ITEM_NAME, Component.literal("Blood Fragment").withStyle(ChatFormatting.RED));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
				Component.literal("Right-click 8 together to clot them into a Blood Heart").withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(false)))));
		stack.set(DataComponents.RARITY, Rarity.UNCOMMON);
		return stack;
	}

	/** Lucifer's Bane: consumed to permanently raise your heart cap. Tradeable and stackable. */
	public static ItemStack bane(int count) {
		ItemStack stack = new ItemStack(BANE_BASE, count);
		mark(stack, BANE);
		stack.set(DataComponents.ITEM_NAME, Component.literal("Lucifer's Bane").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
				Component.literal("Torn from the Emperor at the bottom of the world.").withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(true)),
				Component.literal("Right-click: your veins hold more hearts, forever.").withStyle(s -> s.withColor(ChatFormatting.GOLD).withItalic(false)),
				Component.literal("Tradeable. Each one stacks.").withStyle(s -> s.withColor(ChatFormatting.DARK_GRAY).withItalic(false)))));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		stack.set(DataComponents.RARITY, Rarity.EPIC);
		return stack;
	}

	private static void mark(ItemStack stack, String kind) {
		CompoundTag tag = new CompoundTag();
		tag.putString(KEY, kind);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	private static String kind(ItemStack stack) {
		if (stack.isEmpty()) {
			return "";
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return "";
		}
		return data.copyTag().getStringOr(KEY, "");
	}

	public static boolean isHeart(ItemStack stack) {
		return HEART.equals(kind(stack));
	}

	public static boolean isFragment(ItemStack stack) {
		return FRAGMENT.equals(kind(stack));
	}

	public static boolean isBane(ItemStack stack) {
		return BANE.equals(kind(stack));
	}

	/** Any Hellcraft item that must not be used as its vanilla base (dyeing, planting, beacons...). */
	public static boolean isBlood(ItemStack stack) {
		String k = kind(stack);
		return HEART.equals(k) || FRAGMENT.equals(k) || BANE.equals(k);
	}

	/** Counts Blood Hearts across a player's inventory. */
	public static int countHearts(Player player) {
		int total = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack s = player.getInventory().getItem(i);
			if (isHeart(s)) {
				total += s.getCount();
			}
		}
		return total;
	}

	/** Removes up to {@code amount} Blood Hearts from a player's inventory; returns how many were taken. */
	public static int takeHearts(Player player, int amount) {
		int left = amount;
		for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
			ItemStack s = player.getInventory().getItem(i);
			if (isHeart(s)) {
				int take = Math.min(left, s.getCount());
				s.shrink(take);
				left -= take;
			}
		}
		return amount - left;
	}

	/** Gives items to a player, dropping whatever doesn't fit at their feet. */
	public static void give(Player player, ItemStack stack) {
		if (!player.getInventory().add(stack) && !stack.isEmpty()) {
			if (player.level() instanceof ServerLevel level) {
				player.spawnAtLocation(level, stack);
			}
		}
	}
}
