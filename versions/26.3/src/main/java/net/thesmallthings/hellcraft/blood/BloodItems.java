package net.thesmallthings.hellcraft.blood;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.config.HellConfig;

import java.util.List;

/**
 * Blood Hearts, Blood Fragments, Lucifer's Bane, Vigil Candles and Soul Anchors: Hellcraft's own items
 * ({@link HellItems}). Before 3.0 they were vanilla items marked with {@code custom_data {hellcraft: "heart" | ...}};
 * {@link #refresh} turns any of those still around into the real thing.
 */
public final class BloodItems {
	private BloodItems() {
	}

	/** The custom_data key that marked the old vanilla-based items (and still marks weapons, armour and relics). */
	public static final String KEY = "hellcraft";
	public static final String HEART = "heart";
	public static final String FRAGMENT = "fragment";
	public static final String BANE = "bane";
	public static final String VIGIL = "vigil";
	public static final String ANCHOR = "anchor";

	public static ItemStack heart(int count) {
		return new ItemStack(HellItems.BLOOD_HEART, count);
	}

	public static ItemStack fragment(int count) {
		ItemStack stack = new ItemStack(HellItems.BLOOD_FRAGMENT, count);
		stack.set(DataComponents.LORE, fragmentLore());
		return stack;
	}

	/** A fragment's lore names how many make a heart, which can be changed while the server runs. */
	static ItemLore fragmentLore() {
		return new ItemLore(List.of(Component.literal("Right-click with " + HellConfig.get().fragmentsPerHeart + " in a stack to make a Blood Heart")
				.withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(false))));
	}

	/** Lucifer's Bane: consumed to permanently raise your heart cap. Tradeable and stackable. */
	public static ItemStack bane(int count) {
		return new ItemStack(HellItems.LUCIFERS_BANE, count);
	}

	/** A one-time respawn point: light it, and the next death wakes you beside it. Recipe: vigil_candle.json. */
	public static ItemStack vigil(int count) {
		return new ItemStack(HellItems.VIGIL_CANDLE, count);
	}

	/** Carried: if you die, you rise again where you fell, and it breaks. */
	public static ItemStack anchor(int count) {
		return new ItemStack(HellItems.SOUL_ANCHOR, count);
	}

	/** What an old (before 3.0) vanilla-based blood item was marked as, or "" for anything else. */
	private static String legacyKind(ItemStack stack) {
		if (stack.isEmpty() || stack.getItem().builtInRegistryHolder().key().identifier().getNamespace().equals(HellcraftMod.MOD_ID)) {
			return "";
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return "";
		}
		return data.copyTag().getStringOr(KEY, "");
	}

	public static boolean isHeart(ItemStack stack) {
		return stack.is(HellItems.BLOOD_HEART);
	}

	public static boolean isFragment(ItemStack stack) {
		return stack.is(HellItems.BLOOD_FRAGMENT);
	}

	public static boolean isBane(ItemStack stack) {
		return stack.is(HellItems.LUCIFERS_BANE);
	}

	public static boolean isVigil(ItemStack stack) {
		return stack.is(HellItems.VIGIL_CANDLE);
	}

	public static boolean isAnchor(ItemStack stack) {
		return stack.is(HellItems.SOUL_ANCHOR);
	}

	/** Any of these five Hellcraft items. */
	public static boolean isBlood(ItemStack stack) {
		return isHeart(stack) || isFragment(stack) || isBane(stack) || isVigil(stack) || isAnchor(stack);
	}

	/**
	 * Re-stamps every blood item a player carries (inventory and ender chest) in its current form: items from
	 * before 3.0 become the real items, and a fragment's lore follows the fragmentsPerHeart setting.
	 */
	public static void refresh(Player player) {
		refresh(player.getInventory());
		refresh(player.getEnderChestInventory());
	}

	private static void refresh(Container container) {
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			HellWeapons.refresh(stack);
			int n = stack.getCount();
			ItemStack fresh = switch (legacyKind(stack)) {
				case HEART -> heart(n);
				case FRAGMENT -> fragment(n);
				case BANE -> bane(n);
				case VIGIL -> vigil(n);
				case ANCHOR -> anchor(n);
				default -> isFragment(stack) ? fragment(n) : null;
			};
			if (fresh != null && !ItemStack.isSameItemSameComponents(stack, fresh)) {
				container.setItem(i, fresh);
			}
		}
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

	/** Counts Blood Fragments across a player's inventory. */
	public static int countFragments(Player player) {
		int total = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack s = player.getInventory().getItem(i);
			if (isFragment(s)) {
				total += s.getCount();
			}
		}
		return total;
	}

	/** Takes exactly {@code amount} Blood Fragments, or none at all if the player has fewer. */
	public static boolean takeFragments(Player player, int amount) {
		if (countFragments(player) < amount) {
			return false;
		}
		int left = amount;
		for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
			ItemStack s = player.getInventory().getItem(i);
			if (isFragment(s)) {
				int take = Math.min(left, s.getCount());
				s.shrink(take);
				left -= take;
			}
		}
		return true;
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
