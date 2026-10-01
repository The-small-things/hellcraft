package net.thesmallthings.hellcraft.blood;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.thesmallthings.hellcraft.config.HellConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Hellforge: any anvil standing on magma in the Nether (one waits in the Great Forge of Dis). Right-click
 * it with a hell weapon or a piece of blood armour, carrying a netherite ingot and some Blood Fragments, and it
 * is forged anew as infernal gear: netherite, fire-proof, and (weapons) a good deal stronger.
 */
public final class Hellforge {
	private Hellforge() {
	}

	private static final Map<Item, Item> NETHERITE = Map.of(
			Items.IRON_SWORD, Items.NETHERITE_SWORD,
			Items.DIAMOND_HOE, Items.NETHERITE_HOE,
			Items.DIAMOND_AXE, Items.NETHERITE_AXE,
			Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE,
			Items.DIAMOND_HELMET, Items.NETHERITE_HELMET,
			Items.DIAMOND_CHESTPLATE, Items.NETHERITE_CHESTPLATE,
			Items.DIAMOND_LEGGINGS, Items.NETHERITE_LEGGINGS,
			Items.DIAMOND_BOOTS, Items.NETHERITE_BOOTS);

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown() || !(player instanceof ServerPlayer sp)
					|| level.dimension() != Level.NETHER || !isHellforge(level, hit.getBlockPos())) {
				return InteractionResult.PASS;
			}
			ItemStack held = player.getMainHandItem();
			if (HellWeapons.of(held) == null && BloodArmour.of(held) == null) {
				return InteractionResult.PASS;
			}
			forge(sp, (ServerLevel) level, hit.getBlockPos(), held);
			return InteractionResult.SUCCESS;
		});
	}

	public static boolean isHellforge(Level level, BlockPos pos) {
		return level.getBlockState(pos).is(BlockTags.ANVIL) && level.getBlockState(pos.below()).is(Blocks.MAGMA_BLOCK);
	}

	private static void forge(ServerPlayer player, ServerLevel level, BlockPos at, ItemStack held) {
		if (HellWeapons.infernal(held)) {
			player.sendOverlayMessage(Component.literal("Already infernal: it can't be upgraded further.").withStyle(ChatFormatting.GOLD));
			return;
		}
		Item base = NETHERITE.get(held.getItem());
		if (base == null) {
			return;
		}
		int fragments = HellConfig.get().hellforgeCostFragments;
		int ingots = player.getInventory().countItem(Items.NETHERITE_INGOT);
		if (ingots < 1 || BloodItems.countFragments(player) < fragments) {
			player.sendSystemMessage(Component.literal("The Hellforge needs a netherite ingot and " + fragments + " Blood Fragments (you have "
					+ ingots + " and " + BloodItems.countFragments(player) + ").").withStyle(ChatFormatting.RED));
			return;
		}
		takeOne(player, Items.NETHERITE_INGOT);
		BloodItems.takeFragments(player, fragments);

		ItemStack forged = new ItemStack(base, 1);
		forged.applyComponents(held.getComponentsPatch());
		CompoundTag tag = forged.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		tag.putBoolean(HellWeapons.INFERNAL, true);
		forged.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		Component name = forged.getOrDefault(DataComponents.ITEM_NAME, Component.empty());
		forged.set(DataComponents.ITEM_NAME, Component.literal("Infernal ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD).append(name));
		HellWeapons.Weapon weapon = HellWeapons.of(forged);
		if (weapon != null) {
			forged.set(DataComponents.LORE, HellWeapons.lore(weapon, true));
		} else {
			List<Component> lore = new ArrayList<>();
			lore.add(Component.literal("Infernal (upgraded at a Hellforge): now netherite.")
					.withStyle(s -> s.withColor(ChatFormatting.GOLD).withItalic(false)));
			lore.addAll(forged.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
			forged.set(DataComponents.LORE, new ItemLore(lore));
		}
		player.setItemInHand(InteractionHand.MAIN_HAND, forged);

		level.playSound(null, at, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.2f, 0.6f);
		level.playSound(null, at, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1.0f, 0.8f);
		level.sendParticles(ParticleTypes.LAVA, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0);
		level.sendParticles(ParticleTypes.FLAME, at.getX() + 0.5, at.getY() + 1.2, at.getZ() + 0.5, 40, 0.4, 0.4, 0.4, 0.05);
		player.sendSystemMessage(Component.literal("The Hellforge upgraded your ").withStyle(ChatFormatting.GOLD)
				.append(name).append(Component.literal(" to infernal.").withStyle(ChatFormatting.GOLD)));
	}

	private static void takeOne(ServerPlayer player, Item item) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack s = player.getInventory().getItem(i);
			if (s.is(item)) {
				s.shrink(1);
				return;
			}
		}
	}
}
