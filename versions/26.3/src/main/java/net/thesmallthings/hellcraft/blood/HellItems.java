package net.thesmallthings.hellcraft.blood;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.thesmallthings.hellcraft.HellcraftMod;

import java.util.List;

/**
 * Hellcraft's own items. Their look comes from assets/hellcraft/items/&lt;id&gt;.json (tools/gen_models.py);
 * what they do when used is in {@link BloodEvents} and {@link BloodItems}.
 */
public final class HellItems {
	private HellItems() {
	}

	public static final Item BLOOD_HEART = register("blood_heart", new Item.Properties()
			.component(DataComponents.ITEM_NAME, Component.literal("Blood Heart").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
			.component(DataComponents.LORE, lore(
					line("A heart you can carry, trade or spend.", ChatFormatting.GRAY),
					line("Right-click to gain a heart.", ChatFormatting.RED),
					line("Blood Altars spend them to revive, ward and more.", ChatFormatting.DARK_GRAY)))
			.rarity(Rarity.EPIC));
	/** Its lore names the number of fragments a heart takes, which is a setting: see {@link BloodItems#fragment}. */
	public static final Item BLOOD_FRAGMENT = register("blood_fragment", new Item.Properties()
			.component(DataComponents.ITEM_NAME, Component.literal("Blood Fragment").withStyle(ChatFormatting.RED))
			.component(DataComponents.LORE, BloodItems.fragmentLore())
			.rarity(Rarity.UNCOMMON));
	public static final Item LUCIFERS_BANE = register("lucifers_bane", new Item.Properties()
			.component(DataComponents.ITEM_NAME, Component.literal("Lucifer's Bane").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
			.component(DataComponents.LORE, lore(
					Component.literal("Lucifer's reward.").withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(true)),
					line("Right-click: your max hearts go up by 1, for good.", ChatFormatting.GOLD),
					line("Can be traded. Use as many as you like.", ChatFormatting.DARK_GRAY)))
			.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
			.rarity(Rarity.EPIC));
	public static final Item VIGIL_CANDLE = register("vigil_candle", new Item.Properties()
			.component(DataComponents.ITEM_NAME, Component.literal("Vigil Candle").withStyle(ChatFormatting.AQUA))
			.component(DataComponents.LORE, lore(
					line("Right-click to light it where you stand.", ChatFormatting.GRAY),
					line("Next time you die, you respawn there (once).", ChatFormatting.GRAY)))
			.rarity(Rarity.UNCOMMON));
	public static final Item SOUL_ANCHOR = register("soul_anchor", new Item.Properties()
			.component(DataComponents.ITEM_NAME, Component.literal("Soul Anchor").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
			.component(DataComponents.LORE, lore(
					line("Keep it in your inventory. If you die, you", ChatFormatting.GRAY),
					line("come back where you died, and it breaks.", ChatFormatting.GRAY)))
			.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
			.rarity(Rarity.RARE));

	/** Loads this class, registering the items, and adds the Hellcraft tab to the creative inventory. */
	public static void register() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, HellcraftMod.id("hellcraft"), FabricCreativeModeTab.builder()
				.title(Component.literal("Hellcraft"))
				.icon(() -> new ItemStack(BLOOD_HEART))
				.displayItems((parameters, output) -> {
					output.accept(BloodItems.heart(1));
					output.accept(BloodItems.fragment(1));
					output.accept(BloodItems.bane(1));
					output.accept(BloodItems.vigil(1));
					output.accept(BloodItems.anchor(1));
				})
				.build());
	}

	private static Item register(String name, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, HellcraftMod.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(properties.setId(key)));
	}

	private static Component line(String text, ChatFormatting colour) {
		return Component.literal(text).withStyle(s -> s.withColor(colour).withItalic(false));
	}

	private static ItemLore lore(Component... lines) {
		return new ItemLore(List.of(lines));
	}
}
