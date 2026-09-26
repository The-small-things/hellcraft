package net.thesmallthings.hellcraft.blood;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.thesmallthings.hellcraft.config.HellConfig;

/** Wires lifesteal into Fabric's events: deaths, respawns, joins and right-clicks. */
public final class BloodEvents {
	private BloodEvents() {
	}

	public static void register() {
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> DeathHandler.beforeDeath(entity, source));
		ServerLivingEntityEvents.AFTER_DEATH.register(DeathHandler::afterDeath);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> onRespawn(newPlayer, alive));
		ServerPlayerEvents.JOIN.register(BloodEvents::onJoin);

		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (level.isClientSide() || !(player instanceof ServerPlayer sp) || !BloodItems.isBlood(stack)) {
				return InteractionResultHolder.pass(stack);
			}
			return consume(sp, stack) ? InteractionResultHolder.success(stack) : InteractionResultHolder.fail(stack);
		});

		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer sp) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			if (BloodAltar.isAltar(level, hit.getBlockPos())) {
				return BloodAltar.use(sp, (ServerLevel) level, hand, hit.getBlockPos());
			}
			ItemStack stack = player.getItemInHand(hand);
			if (BloodItems.isBlood(stack)) {
				// never let blood be used as dye or planted; treat it like using it in the air
				return consume(sp, stack) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});

		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
				!level.isClientSide() && BloodItems.isBlood(player.getItemInHand(hand)) ? InteractionResult.FAIL : InteractionResult.PASS);
	}

	/** Right-clicking blood: hearts are drunk, fragments clot. Returns true if something happened. */
	private static boolean consume(ServerPlayer player, ItemStack stack) {
		HellConfig config = HellConfig.get();
		if (BloodItems.isHeart(stack)) {
			if (Hearts.soul(player).hearts >= Hearts.cap(Hearts.soul(player))) {
				player.displayClientMessage(Component.literal("Your veins can hold no more blood.").withStyle(ChatFormatting.RED), true);
				return false;
			}
			stack.shrink(1);
			Hearts.add(player, 1);
			player.heal(2.0f);
			player.level().playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1.0f, 0.6f);
			player.displayClientMessage(Component.literal("+1 ❤  (" + Hearts.soul(player).hearts + ")").withStyle(ChatFormatting.DARK_RED), true);
			return true;
		}
		if (BloodItems.isFragment(stack)) {
			int needed = config.fragmentsPerHeart;
			if (stack.getCount() < needed) {
				player.displayClientMessage(Component.literal("Gather " + needed + " Blood Fragments to clot a heart.").withStyle(ChatFormatting.RED), true);
				return false;
			}
			stack.shrink(needed);
			BloodItems.give(player, BloodItems.heart(1));
			player.level().playSound(null, player.blockPosition(), SoundEvents.HONEY_BLOCK_PLACE, SoundSource.PLAYERS, 1.0f, 0.5f);
			player.displayClientMessage(Component.literal("The blood clots into a heart.").withStyle(ChatFormatting.DARK_RED), true);
			return true;
		}
		return false;
	}

	private static void onJoin(ServerPlayer player) {
		HellState.Soul soul = Hearts.soul(player);
		Hearts.apply(player);
		if (soul.reviveAt != null) {
			Ghosts.finishRevive(player, soul, soul.reviveAt);
			return;
		}
		if (soul.ghost) {
			Ghosts.makeGhost(player);
		} else if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR && soul.deathSpot != null && !player.hasPermissions(2)) {
			// was a ghost, got revived by an admin command while offline
			player.setGameMode(GameType.SURVIVAL);
		}
	}

	private static void onRespawn(ServerPlayer player, boolean alive) {
		HellState.Soul soul = Hearts.soul(player);
		Hearts.apply(player);
		if (alive) {
			return;
		}
		player.setHealth(player.getMaxHealth());
		if (soul.ghost) {
			Ghosts.makeGhost(player);
			return;
		}
		if (soul.altar != null) {
			ServerLevel level = player.server.getLevel(soul.altar.dimension());
			if (level != null && BloodAltar.isAltar(level, soul.altar.pos())) {
				Ghosts.teleport(player, new HellState.GlobalSpot(soul.altar.dimension(), soul.altar.pos().above()));
				return;
			}
			soul.altar = null;
			HellState.get(player.server).setDirty();
			player.sendSystemMessage(Component.literal("Your blood altar has been destroyed.").withStyle(ChatFormatting.RED));
		}
	}
}
