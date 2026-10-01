package net.thesmallthings.hellcraft.blood;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.thesmallthings.hellcraft.config.HellConfig;

import java.util.ArrayList;
import java.util.List;

/** Wires lifesteal into Fabric's events: deaths, respawns, joins and right-clicks. */
public final class BloodEvents {
	private BloodEvents() {
	}

	public static void register() {
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> DeathHandler.beforeDeath(entity, source));
		ServerLivingEntityEvents.AFTER_DEATH.register(DeathHandler::afterDeath);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> onRespawn(newPlayer, alive));
		ServerPlayerEvents.JOIN.register(BloodEvents::onJoin);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Respawns.tick(server);
			if (server.getTickCount() % 100 == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					BloodItems.refresh(player);
				}
			}
		});

		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (!level.isClientSide() && player instanceof ServerPlayer sp && hand == InteractionHand.MAIN_HAND) {
				InteractionResult weapon = HellWeapons.use(sp, stack);
				if (weapon != InteractionResult.PASS) {
					return weapon;
				}
			}
			if (level.isClientSide() || !(player instanceof ServerPlayer sp) || !BloodItems.isBlood(stack)) {
				return InteractionResult.PASS;
			}
			return consume(sp, stack) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
		});

		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer sp) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			if (BloodAltar.isAltar(level, hit.getBlockPos())) {
				return BloodAltar.use(sp, (ServerLevel) level, hand, hit.getBlockPos());
			}
			ItemStack stack = player.getItemInHand(hand);
			if (hand == InteractionHand.MAIN_HAND && player.isShiftKeyDown() && HellWeapons.of(stack) != null) {
				// a Blood Oath can be sworn at the ground too (sneaking only, so axes still strip logs)
				return HellWeapons.use(sp, stack);
			}
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
				player.sendOverlayMessage(Component.literal("You're already at max hearts.").withStyle(ChatFormatting.RED));
				return false;
			}
			stack.shrink(1);
			Hearts.add(player, 1);
			player.heal(2.0f);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1.0f, 0.6f);
			player.sendOverlayMessage(Component.literal("+1 ❤  (" + Hearts.soul(player).hearts + ")").withStyle(ChatFormatting.DARK_RED));
			return true;
		}
		if (BloodItems.isBane(stack)) {
			HellState.Soul soul = Hearts.soul(player);
			if (Hearts.atCeiling(soul)) {
				player.sendOverlayMessage(Component.literal("Your max hearts is already as high as it can go (" + Hearts.cap(soul) + " hearts).")
						.withStyle(ChatFormatting.GOLD));
				return false;
			}
			stack.shrink(1);
			soul.maxBonus += config.luciferMaxHeartBonus;
			HellState.get(player.level().getServer()).setDirty();
			Hearts.apply(player);
			player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 0.6f);
			player.sendSystemMessage(Component.literal("Lucifer's Bane: your max hearts is now " + Hearts.cap(soul) + ".")
					.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
			return true;
		}
		if (BloodItems.isVigil(stack)) {
			return Respawns.lightVigil(player, stack);
		}
		if (BloodItems.isAnchor(stack)) {
			player.sendOverlayMessage(Component.literal("Keep it in your inventory: if you die, you come back where you died.").withStyle(ChatFormatting.AQUA));
			return false;
		}
		if (BloodItems.isFragment(stack)) {
			int needed = config.fragmentsPerHeart;
			if (stack.getCount() < needed) {
				player.sendOverlayMessage(Component.literal("You need " + needed + " Blood Fragments in one stack to make a Blood Heart.").withStyle(ChatFormatting.RED));
				return false;
			}
			stack.shrink(needed);
			BloodItems.give(player, BloodItems.heart(1));
			player.level().playSound(null, player.blockPosition(), SoundEvents.HONEY_BLOCK_PLACE, SoundSource.PLAYERS, 1.0f, 0.5f);
			player.sendOverlayMessage(Component.literal("Made a Blood Heart.").withStyle(ChatFormatting.DARK_RED));
			return true;
		}
		return false;
	}

	private static void onJoin(ServerPlayer player) {
		boolean firstJoin = HellState.get(player.level().getServer()).existing(player.getUUID()) == null;
		HellState.Soul soul = Hearts.soul(player);
		Hearts.apply(player);
		BloodItems.refresh(player);
		if (firstJoin) {
			// something to eat on the long walk down, and something to read
			BloodItems.give(player, new ItemStack(Items.BREAD, 5));
			if (HellConfig.get().guideBook) {
				BloodItems.give(player, GuideBook.create(player.level().getServer()));
			}
		}
		if (soul.kit < 1 && !soul.ghost) {
			// a way back for the first falls (players from older versions get theirs once, too)
			BloodItems.give(player, BloodItems.vigil(2));
			soul.kit = 1;
			HellState.get(player.level().getServer()).setDirty();
		}
		// the hell weapons' and blood armour's recipes, in everyone's recipe book
		MinecraftServer server = player.level().getServer();
		List<String> recipes = new ArrayList<>();
		for (HellWeapons.Weapon weapon : HellWeapons.Weapon.values()) {
			recipes.add(weapon.id);
		}
		for (BloodArmour.Piece piece : BloodArmour.Piece.values()) {
			recipes.add(piece.id);
		}
		for (String recipe : recipes) {
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
					"recipe give " + player.getGameProfile().name() + " hellcraft:" + recipe);
		}
		if (soul.reviveAt != null) {
			Ghosts.finishRevive(player, soul, soul.reviveAt);
			return;
		}
		if (soul.ghost) {
			Ghosts.makeGhost(player);
		} else if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR && soul.deathSpot != null && !player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
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
		if (Respawns.respawn(player, soul)) {
			return;
		}
		if (soul.altar != null) {
			ServerLevel level = player.level().getServer().getLevel(soul.altar.dimension());
			if (level != null && BloodAltar.isAltar(level, soul.altar.pos())) {
				Ghosts.teleport(player, new HellState.GlobalSpot(soul.altar.dimension(), soul.altar.pos().above()));
				return;
			}
			soul.altar = null;
			HellState.get(player.level().getServer()).setDirty();
			player.sendSystemMessage(Component.literal("Your Blood Altar was destroyed, so your respawn point is gone.").withStyle(ChatFormatting.RED));
		}
	}
}
