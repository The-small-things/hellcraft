package net.thesmallthings.hellcraft.blood;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.thesmallthings.hellcraft.config.HellConfig;

import java.util.Map;
import java.util.UUID;

/**
 * A blood altar is a respawn anchor standing on a 3x3 of crying obsidian. Blood is its fuel:
 * <ul>
 *     <li>Blood Heart: a <b>Ward</b>, immunity to the hazards of the circles for a while</li>
 *     <li>Blood Heart while sneaking: <b>bind</b> your respawn to this altar</li>
 *     <li>Name Tag bearing a ghost's name + Blood Hearts: <b>revive</b> them here</li>
 * </ul>
 * Respawn anchors don't work in the Inferno (beds explode too), so altars are how you choose where
 * you come back.
 */
public final class BloodAltar {
	private BloodAltar() {
	}

	public static final DustParticleOptions BLOOD = new DustParticleOptions(0x8C0000, 1.6f);

	public static boolean isAltar(BlockGetter level, BlockPos pos) {
		if (!level.getBlockState(pos).is(Blocks.RESPAWN_ANCHOR)) {
			return false;
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (!level.getBlockState(pos.offset(dx, -1, dz)).is(Blocks.CRYING_OBSIDIAN)) {
					return false;
				}
			}
		}
		return true;
	}

	public static InteractionResult use(ServerPlayer player, ServerLevel level, InteractionHand hand, BlockPos pos) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.FAIL;
		}
		ItemStack held = player.getItemInHand(hand);
		HellConfig config = HellConfig.get();
		HellState state = HellState.get(level.getServer());
		HellState.Soul soul = Hearts.soul(player);

		if (held.is(Items.NAME_TAG) && held.has(DataComponents.CUSTOM_NAME)) {
			String name = held.getHoverName().getString();
			Map.Entry<UUID, HellState.Soul> target = state.findByName(name);
			if (target == null || !target.getValue().ghost) {
				player.sendSystemMessage(Component.literal("No damned soul named \"" + name + "\" awaits here.").withStyle(ChatFormatting.GRAY));
				return InteractionResult.SUCCESS;
			}
			int cost = config.reviveCostHearts;
			if (BloodItems.countHearts(player) < cost) {
				player.sendSystemMessage(Component.literal("The altar demands " + cost + " Blood Hearts to return " + target.getValue().name + ".")
						.withStyle(ChatFormatting.RED));
				return InteractionResult.SUCCESS;
			}
			BloodItems.takeHearts(player, cost);
			held.shrink(1);
			Ghosts.revive(level.getServer(), target.getKey(), new HellState.GlobalSpot(level.dimension(), pos.above()));
			ritual(level, pos, 60);
			level.playSound(null, pos, SoundEvents.TOTEM_USE, SoundSource.BLOCKS, 1.0f, 0.7f);
			return InteractionResult.SUCCESS;
		}

		if (BloodItems.isHeart(held)) {
			held.shrink(1);
			if (player.isShiftKeyDown()) {
				soul.altar = new HellState.GlobalSpot(level.dimension(), pos.immutable());
				state.setDirty();
				player.sendSystemMessage(Component.literal("Your blood is bound to this altar. You will return here when you die.")
						.withStyle(ChatFormatting.DARK_RED));
				level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.BLOCKS, 1.0f, 0.8f);
			} else {
				long now = level.getServer().overworld().getGameTime();
				soul.wardUntil = Math.max(soul.wardUntil, now) + config.wardMinutes * 1200L;
				state.setDirty();
				player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 400, 1));
				player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2400, 1));
				player.sendSystemMessage(Component.literal("A Blood Ward shields you from the torments of the circles for "
						+ config.wardMinutes + " minutes.").withStyle(ChatFormatting.DARK_RED));
				level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1.0f, 0.6f);
			}
			ritual(level, pos, 30);
			return InteractionResult.SUCCESS;
		}

		player.sendSystemMessage(Component.literal("† Blood Altar †").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
		player.sendSystemMessage(Component.literal(" Offer a Blood Heart: a Ward against the circles (" + config.wardMinutes + " min)").withStyle(ChatFormatting.GRAY));
		player.sendSystemMessage(Component.literal(" Sneak + offer a Blood Heart: bind your respawn here").withStyle(ChatFormatting.GRAY));
		player.sendSystemMessage(Component.literal(" Name Tag with a ghost's name + " + config.reviveCostHearts + " Blood Hearts: revive them").withStyle(ChatFormatting.GRAY));
		return InteractionResult.SUCCESS;
	}

	public static void ritual(ServerLevel level, BlockPos pos, int count) {
		level.sendParticles(BLOOD, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, count, 0.6, 0.6, 0.6, 0.0);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, count / 3, 0.4, 0.4, 0.4, 0.02);
	}
}
