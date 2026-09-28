package net.thesmallthings.hellcraft.blood;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.thesmallthings.hellcraft.HellcraftMod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Blood armour: diamond armour reforged with blood (recipes in data/hellcraft/recipe, written by
 * tools/gen_models.py). The pieces work as a set:
 * <ul>
 *     <li>2 or more: your melee hits heal you 5% of the damage they deal;</li>
 *     <li>all 4: 10%, a <b>Blood Rush</b> (Regeneration II) when you drop below 3 hearts of health, once a
 *     minute, and Resistance while under a Blood Oath.</li>
 * </ul>
 */
public final class BloodArmour {
	private BloodArmour() {
	}

	public static final String KIND = "armour";
	private static final ResourceKey<EquipmentAsset> ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, HellcraftMod.id("blood"));
	private static final int RUSH_COOLDOWN = 60 * 20;
	private static final List<String> LORE = List.of(
			"Blood armour: 2 pieces heal you 5% of the damage you deal,",
			"4 pieces 10%, plus a Blood Rush when near death",
			"and Resistance under a Blood Oath.");

	public enum Piece {
		HELMET("blood_helmet", "Blood Helm", Items.DIAMOND_HELMET, EquipmentSlot.HEAD),
		CHESTPLATE("blood_chestplate", "Blood Cuirass", Items.DIAMOND_CHESTPLATE, EquipmentSlot.CHEST),
		LEGGINGS("blood_leggings", "Blood Greaves", Items.DIAMOND_LEGGINGS, EquipmentSlot.LEGS),
		BOOTS("blood_boots", "Blood Sabatons", Items.DIAMOND_BOOTS, EquipmentSlot.FEET);

		public final String id;
		final String title;
		final Item base;
		final EquipmentSlot slot;

		Piece(String id, String title, Item base, EquipmentSlot slot) {
			this.id = id;
			this.title = title;
			this.base = base;
			this.slot = slot;
		}
	}

	private static final Map<UUID, Long> RUSH_READY = new HashMap<>();

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(BloodArmour::afterDamage);
	}

	/** Must match the results in data/hellcraft/recipe/blood_*.json. */
	public static ItemStack create(Piece piece) {
		ItemStack stack = new ItemStack(piece.base);
		CompoundTag tag = new CompoundTag();
		tag.putString(BloodItems.KEY, KIND);
		tag.putString(KIND, piece.id);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ITEM_MODEL, HellcraftMod.id(piece.id));
		stack.set(DataComponents.ITEM_NAME, Component.literal(piece.title).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
		List<Component> lore = new ArrayList<>();
		for (String line : LORE) {
			lore.add(Component.literal(line).withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(false)));
		}
		stack.set(DataComponents.LORE, new ItemLore(lore));
		stack.set(DataComponents.RARITY, Rarity.EPIC);
		stack.set(DataComponents.EQUIPPABLE, Equippable.builder(piece.slot).setEquipSound(SoundEvents.ARMOR_EQUIP_DIAMOND).setAsset(ASSET).build());
		return stack;
	}

	@Nullable
	public static Piece of(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		CompoundTag tag = data.copyTag();
		if (!KIND.equals(tag.getStringOr(BloodItems.KEY, ""))) {
			return null;
		}
		String id = tag.getStringOr(KIND, "");
		for (Piece p : Piece.values()) {
			if (p.id.equals(id)) {
				return p;
			}
		}
		return null;
	}

	/** How many blood pieces the entity wears (each in its own slot). */
	public static int worn(LivingEntity entity) {
		int n = 0;
		for (Piece p : Piece.values()) {
			if (of(entity.getItemBySlot(p.slot)) == p) {
				n++;
			}
		}
		return n;
	}

	private static void afterDamage(LivingEntity target, DamageSource source, float base, float taken, boolean blocked) {
		// the wearer strikes: drink a little of what was spilled
		if (source.getEntity() instanceof ServerPlayer attacker && source.getDirectEntity() == attacker && attacker != target && taken > 0) {
			int pieces = worn(attacker);
			if (pieces >= 2 && attacker.getHealth() < attacker.getMaxHealth()) {
				attacker.heal(taken * (pieces >= 4 ? 0.10f : 0.05f));
			}
		}
		// the wearer bleeds: at the edge of death, the blood rushes back
		if (target instanceof ServerPlayer wearer && wearer.isAlive() && wearer.getHealth() < 6.0f && worn(wearer) >= 4) {
			long now = wearer.level().getGameTime();
			if (now >= RUSH_READY.getOrDefault(wearer.getUUID(), 0L)) {
				RUSH_READY.put(wearer.getUUID(), now + RUSH_COOLDOWN);
				wearer.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
				wearer.level().sendParticles(BloodAltar.BLOOD, wearer.getX(), wearer.getY() + 1, wearer.getZ(), 40, 0.4, 0.8, 0.4, 0.0);
				wearer.level().sendParticles(ParticleTypes.HEART, wearer.getX(), wearer.getY() + 2, wearer.getZ(), 4, 0.4, 0.2, 0.4, 0.0);
				wearer.sendOverlayMessage(Component.literal("Blood Rush!").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
			}
		}
	}
}
