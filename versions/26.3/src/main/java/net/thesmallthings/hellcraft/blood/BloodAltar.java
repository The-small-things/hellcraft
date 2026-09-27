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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.thesmallthings.hellcraft.config.HellConfig;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * A blood altar is a respawn anchor standing on a 3x3 of crying obsidian. Blood is its fuel:
 * Right-clicking it opens a menu ({@link AltarMenu}):
 * <ul>
 *     <li><b>Revive</b> a ghost here for Blood Hearts (also: use a Name Tag renamed to their name)</li>
 *     <li>a <b>Ward</b>, immunity to the hazards of the circles for a while (also: use a Blood Heart)</li>
 *     <li><b>bind</b> your respawn to this altar (also: sneak and use a Blood Heart)</li>
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
		if (player.isShiftKeyDown() && held.getItem() instanceof BlockItem) {
			// sneaking with a block: build against the altar like against any other block
			return InteractionResult.PASS;
		}
		HellState state = HellState.get(level.getServer());

		// shortcut: a Name Tag renamed to a ghost's name
		if (held.is(Items.NAME_TAG) && held.has(DataComponents.CUSTOM_NAME)) {
			String name = held.getHoverName().getString();
			Map.Entry<UUID, HellState.Soul> target = state.findByName(name);
			if (target == null || !target.getValue().ghost) {
				player.sendSystemMessage(Component.literal("No damned soul named \"" + name + "\" awaits here.").withStyle(ChatFormatting.GRAY));
				return InteractionResult.SUCCESS;
			}
			if (revive(player, level, pos, target.getKey())) {
				held.shrink(1);
			}
			return InteractionResult.SUCCESS;
		}

		// shortcuts: offer the Blood Heart in your hand
		if (BloodItems.isHeart(held)) {
			if (player.isShiftKeyDown()) {
				bind(player, level, pos);
			} else {
				ward(player, level, pos);
			}
			return InteractionResult.SUCCESS;
		}

		AltarMenu.open(player, level, pos);
		return InteractionResult.SUCCESS;
	}

	/** Spends a Blood Heart for a Ward against the circles' hazards. */
	public static boolean ward(ServerPlayer player, ServerLevel level, BlockPos pos) {
		HellConfig config = HellConfig.get();
		if (BloodItems.takeHearts(player, 1) < 1) {
			player.sendSystemMessage(Component.literal("The altar wants a Blood Heart for a Ward.").withStyle(ChatFormatting.RED));
			return false;
		}
		HellState state = HellState.get(level.getServer());
		HellState.Soul soul = Hearts.soul(player);
		long now = level.getServer().overworld().getGameTime();
		soul.wardUntil = Math.max(soul.wardUntil, now) + config.wardMinutes * 1200L;
		state.setDirty();
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 400, 1));
		player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2400, 1));
		player.sendSystemMessage(Component.literal("A Blood Ward shields you from the torments of the circles for "
				+ config.wardMinutes + " minutes.").withStyle(ChatFormatting.DARK_RED));
		level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1.0f, 0.6f);
		ritual(level, pos, 30);
		return true;
	}

	/** Spends a Blood Heart to make this altar the player's respawn point. */
	public static boolean bind(ServerPlayer player, ServerLevel level, BlockPos pos) {
		if (BloodItems.takeHearts(player, 1) < 1) {
			player.sendSystemMessage(Component.literal("The altar wants a Blood Heart to bind your respawn.").withStyle(ChatFormatting.RED));
			return false;
		}
		HellState.Soul soul = Hearts.soul(player);
		soul.altar = new HellState.GlobalSpot(level.dimension(), pos.immutable());
		HellState.get(level.getServer()).setDirty();
		player.sendSystemMessage(Component.literal("Your blood is bound to this altar. You will return here when you die.")
				.withStyle(ChatFormatting.DARK_RED));
		level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.BLOCKS, 1.0f, 0.8f);
		ritual(level, pos, 30);
		return true;
	}

	/** Pays the blood price to bring a ghost back to life on top of this altar. */
	public static boolean revive(ServerPlayer player, ServerLevel level, BlockPos pos, UUID ghost) {
		HellState.Soul target = HellState.get(level.getServer()).existing(ghost);
		if (target == null || !target.ghost) {
			player.sendSystemMessage(Component.literal("That soul is no longer among the dead.").withStyle(ChatFormatting.GRAY));
			return false;
		}
		int cost = HellConfig.get().reviveCostHearts;
		int have = BloodItems.countHearts(player);
		if (have < cost) {
			player.sendSystemMessage(Component.literal("The altar demands " + cost + " Blood Hearts to return " + target.name
					+ ". You carry " + have + ".").withStyle(ChatFormatting.RED));
			player.sendSystemMessage(Component.literal("Get Blood Hearts with /withdraw, by clotting "
					+ HellConfig.get().fragmentsPerHeart + " Blood Fragments, or from kills.")
					.withStyle(ChatFormatting.GRAY));
			return false;
		}
		BloodItems.takeHearts(player, cost);
		Ghosts.revive(level.getServer(), ghost, new HellState.GlobalSpot(level.dimension(), pos.above()));
		ritual(level, pos, 60);
		level.playSound(null, pos, SoundEvents.TOTEM_USE, SoundSource.BLOCKS, 1.0f, 0.7f);
		return true;
	}

	/** The nearest Blood Altar within a few blocks of the player, if any. */
	@Nullable
	public static BlockPos near(ServerPlayer player, int radius) {
		BlockPos center = player.blockPosition();
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-radius, -3, -radius), center.offset(radius, 3, radius))) {
			if (isAltar(player.level(), p)) {
				double d = p.distSqr(center);
				if (d < bestDist) {
					bestDist = d;
					best = p.immutable();
				}
			}
		}
		return best;
	}

	public static void ritual(ServerLevel level, BlockPos pos, int count) {
		level.sendParticles(BLOOD, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, count, 0.6, 0.6, 0.6, 0.0);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, count / 3, 0.4, 0.4, 0.4, 0.02);
	}
}
