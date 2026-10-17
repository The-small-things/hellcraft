package net.thesmallthings.hellcraft.blood;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.thesmallthings.hellcraft.HellcraftMod;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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

	// ------------------------------------------------------------------ blood gear (real items since 3.0)

	/** Blood armour: diamond's protection, its own look (equipment asset hellcraft:blood). */
	public static final ArmorMaterial BLOOD_ARMOUR = new ArmorMaterial(33, defense(3, 6, 8, 3, 11), 10, SoundEvents.ARMOR_EQUIP_DIAMOND, 2.0f, 0.0f,
			ItemTags.REPAIRS_DIAMOND_ARMOR, BloodArmour.ASSET);
	/** Infernal blood armour (the Hellforge): netherite's. */
	public static final ArmorMaterial INFERNAL_ARMOUR = new ArmorMaterial(37, defense(3, 6, 8, 3, 19), 15, SoundEvents.ARMOR_EQUIP_NETHERITE, 3.0f, 0.1f,
			ItemTags.REPAIRS_NETHERITE_ARMOR, BloodArmour.ASSET);

	/** The Halo: worn as a helmet, a gold circlet (equipment asset hellcraft:halo); gold's protection. */
	public static final ArmorMaterial HALO_MATERIAL = new ArmorMaterial(7, defense(1, 3, 5, 2, 7), 25, SoundEvents.ARMOR_EQUIP_GOLD, 0.0f, 0.0f,
			ItemTags.REPAIRS_GOLD_ARMOR, ResourceKey.create(EquipmentAssets.ROOT_ID, HellcraftMod.id("halo")));

	private static final Map<BloodArmour.Piece, Item> ARMOUR = new EnumMap<>(BloodArmour.Piece.class);
	private static final Map<BloodArmour.Piece, Item> INFERNAL_ARMOUR_ITEMS = new EnumMap<>(BloodArmour.Piece.class);
	private static final Map<HellWeapons.Weapon, Item> WEAPONS = new EnumMap<>(HellWeapons.Weapon.class);
	private static final Map<HellWeapons.Weapon, Item> INFERNAL_WEAPONS = new EnumMap<>(HellWeapons.Weapon.class);
	private static final Map<Relics.Relic, Item> RELICS = new EnumMap<>(Relics.Relic.class);

	static {
		for (BloodArmour.Piece piece : BloodArmour.Piece.values()) {
			ARMOUR.put(piece, register(piece.id, new Item.Properties().humanoidArmor(BLOOD_ARMOUR, piece.type)
					.component(DataComponents.LORE, BloodArmour.lore(false)).rarity(Rarity.EPIC)));
			INFERNAL_ARMOUR_ITEMS.put(piece, register("infernal_" + piece.id, new Item.Properties().humanoidArmor(INFERNAL_ARMOUR, piece.type)
					.component(DataComponents.LORE, BloodArmour.lore(true)).rarity(Rarity.EPIC).fireResistant()));
		}
		for (HellWeapons.Weapon weapon : HellWeapons.Weapon.values()) {
			WEAPONS.put(weapon, register(weapon.id, weaponProperties(weapon, false)));
			INFERNAL_WEAPONS.put(weapon, register("infernal_" + weapon.id, weaponProperties(weapon, true).fireResistant()));
		}
		for (Relics.Relic relic : Relics.Relic.values()) {
			RELICS.put(relic, register(relic.id, relicProperties(relic)));
		}
	}

	/** Paradiso's relics (real items since 3.0): what they do is in {@link Relics}. */
	private static Item.Properties relicProperties(Relics.Relic relic) {
		Item.Properties props = switch (relic) {
			case HALO -> new Item.Properties().humanoidArmor(HALO_MATERIAL, ArmorType.HELMET)
					.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			// elytra that never wear out, with their own wings (equipment asset hellcraft:seraph_wings)
			case SERAPH_WINGS -> new Item.Properties().component(DataComponents.GLIDER, Unit.INSTANCE)
					.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST).setEquipSound(SoundEvents.ARMOR_EQUIP_ELYTRA)
							.setAsset(ResourceKey.create(EquipmentAssets.ROOT_ID, HellcraftMod.id("seraph_wings"))).setDamageOnHurt(false).build());
			case BEATRICES_ROSE -> new Item.Properties();
		};
		if (relic != Relics.Relic.BEATRICES_ROSE) {
			props.component(DataComponents.UNBREAKABLE, Unit.INSTANCE);
		}
		return props.stacksTo(1).component(DataComponents.ITEM_NAME, Relics.name(relic)).component(DataComponents.LORE, Relics.lore(relic))
				.rarity(Rarity.EPIC);
	}

	private static Item.Properties weaponProperties(HellWeapons.Weapon weapon, boolean infernal) {
		ToolMaterial diamond = infernal ? ToolMaterial.NETHERITE : ToolMaterial.DIAMOND;
		Item.Properties props = switch (weapon) {
			case BLOODLETTER -> new Item.Properties().sword(infernal ? ToolMaterial.NETHERITE : ToolMaterial.IRON, 3.0f, -2.4f);
			// a scythe, not a garden tool: heavy and slow
			case REAPER -> new Item.Properties().hoe(diamond, 4.0f, -3.0f);
			case TITHE_AXE -> new Item.Properties().axe(diamond, 5.0f, -3.0f);
			case BLOOD_PICKAXE -> new Item.Properties().pickaxe(diamond, 1.0f, -2.8f);
		};
		return props.component(DataComponents.LORE, HellWeapons.lore(weapon, infernal)).rarity(infernal ? Rarity.EPIC : weapon.rarity);
	}

	private static Map<ArmorType, Integer> defense(int boots, int legs, int chest, int helm, int body) {
		Map<ArmorType, Integer> map = new EnumMap<>(ArmorType.class);
		map.put(ArmorType.BOOTS, boots);
		map.put(ArmorType.LEGGINGS, legs);
		map.put(ArmorType.CHESTPLATE, chest);
		map.put(ArmorType.HELMET, helm);
		map.put(ArmorType.BODY, body);
		return map;
	}

	public static Item armour(BloodArmour.Piece piece, boolean infernal) {
		return (infernal ? INFERNAL_ARMOUR_ITEMS : ARMOUR).get(piece);
	}

	public static Item weapon(HellWeapons.Weapon weapon, boolean infernal) {
		return (infernal ? INFERNAL_WEAPONS : WEAPONS).get(weapon);
	}

	public static Item relic(Relics.Relic relic) {
		return RELICS.get(relic);
	}

	/** The relic this item is, if it's one. */
	@Nullable
	static Relics.Relic relicOf(Item item) {
		for (Map.Entry<Relics.Relic, Item> e : RELICS.entrySet()) {
			if (e.getValue() == item) {
				return e.getKey();
			}
		}
		return null;
	}

	/** The piece this item is, if it's blood armour (either form). */
	@Nullable
	static BloodArmour.Piece pieceOf(Item item) {
		for (BloodArmour.Piece piece : BloodArmour.Piece.values()) {
			if (ARMOUR.get(piece) == item || INFERNAL_ARMOUR_ITEMS.get(piece) == item) {
				return piece;
			}
		}
		return null;
	}

	/** The weapon this item is, if it's a hell weapon (either form). */
	@Nullable
	static HellWeapons.Weapon weaponOf(Item item) {
		for (HellWeapons.Weapon weapon : HellWeapons.Weapon.values()) {
			if (WEAPONS.get(weapon) == item || INFERNAL_WEAPONS.get(weapon) == item) {
				return weapon;
			}
		}
		return null;
	}

	/** Whether this item is the infernal form of a piece of blood gear. */
	static boolean infernalItem(Item item) {
		return INFERNAL_ARMOUR_ITEMS.containsValue(item) || INFERNAL_WEAPONS.containsValue(item);
	}

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
					for (HellWeapons.Weapon weapon : HellWeapons.Weapon.values()) {
						output.accept(new ItemStack(weapon(weapon, false)));
					}
					for (BloodArmour.Piece piece : BloodArmour.Piece.values()) {
						output.accept(new ItemStack(armour(piece, false)));
					}
					for (HellWeapons.Weapon weapon : HellWeapons.Weapon.values()) {
						output.accept(new ItemStack(weapon(weapon, true)));
					}
					for (BloodArmour.Piece piece : BloodArmour.Piece.values()) {
						output.accept(new ItemStack(armour(piece, true)));
					}
					for (Relics.Relic relic : Relics.Relic.values()) {
						output.accept(new ItemStack(relic(relic)));
					}
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
