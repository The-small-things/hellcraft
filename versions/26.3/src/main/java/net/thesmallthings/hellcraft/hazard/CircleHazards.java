package net.thesmallthings.hellcraft.hazard;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.thesmallthings.hellcraft.blood.BloodAltar;
import net.thesmallthings.hellcraft.blood.Ghosts;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.blood.Prestige;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.hazard.lucifer.LuciferManager;
import net.thesmallthings.hellcraft.net.Dialogue;
import net.thesmallthings.hellcraft.world.Circle;
import net.thesmallthings.hellcraft.world.HellWorldgen;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.Shrines;
import net.thesmallthings.hellcraft.world.Spine;
import net.thesmallthings.hellcraft.world.Zone;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The torments of each circle, applied about once a second to living players in the Inferno.
 * A Blood Ward (from an altar) suspends them all.
 */
public final class CircleHazards {
	private CircleHazards() {
	}

	private static final Map<UUID, Circle> LAST_CIRCLE = new HashMap<>();
	private static final Map<UUID, String> LAST_REGION = new HashMap<>();
	private static int ticks;
	/** Hint bit: the first unlit brazier someone tries. */
	private static final int HINT_BRAZIER = 40;

	public static void register() {
		// Cocytus's braziers (and any campfire on the ice) light with a torch as well as flint and steel, and stay lit
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer sp) || !HellWorldgen.isInferno(serverLevel)) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			BlockState state = level.getBlockState(pos);
			if (!state.is(BlockTags.CAMPFIRES) || state.getValue(CampfireBlock.LIT) || state.getValue(CampfireBlock.WATERLOGGED)
					|| InfernoGeometry.zoneAt(pos.getX(), pos.getZ()).circle() != Circle.TREACHERY) {
				return InteractionResult.PASS;
			}
			ItemStack held = sp.getItemInHand(hand);
			boolean torch = held.is(Items.TORCH) || held.is(Items.SOUL_TORCH);
			if (!torch && !held.is(Items.FLINT_AND_STEEL) && !held.is(Items.FIRE_CHARGE)) {
				hintOnce(sp, HINT_BRAZIER, "This brazier is out. Light it with a torch or flint and steel, and it stays lit for everyone.");
				return InteractionResult.PASS;
			}
			if (torch) {
				level.setBlock(pos, state.setValue(CampfireBlock.LIT, true), 11);
				level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0f, 1.2f);
			}
			Dialogue.notice(sp, Component.literal("The brazier burns, and will burn for everyone who crosses the ice after you. Stand by it to warm up.")
					.withStyle(ChatFormatting.GOLD));
			return torch ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer sp && state.is(BlockTags.LOGS)
					&& HellWorldgen.isInferno(serverLevel)
					&& InfernoGeometry.zoneAt(pos.getX(), pos.getZ()) == Zone.WOOD_OF_SUICIDES && !warded(sp)) {
				// "Why dost thou rend me?" -- the trees of the suicides bleed when broken, and the noise carries (LowerRules)
				serverLevel.sendParticles(BloodAltar.BLOOD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 25, 0.4, 0.4, 0.4, 0.0);
				sp.sendOverlayMessage(Component.literal("\"Why dost thou rend me?\"").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
				LowerRules.branchBroken(sp);
			}
		});
	}

	public static void tick(MinecraftServer server) {
		if (++ticks % 20 != 0) {
			return;
		}
		HellConfig config = HellConfig.get();
		HellState state = HellState.get(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			HellState.Soul soul = state.existing(player.getUUID());
			if (soul != null && soul.ghost) {
				Ghosts.tether(player, soul);
				continue;
			}
			ServerLevel level = player.level();
			if (!HellWorldgen.isInferno(level)) {
				LAST_CIRCLE.remove(player.getUUID());
				if (level.dimension() == Level.NETHER && config.circleHazards && !player.isSpectator() && !player.isCreative() && !warded(player)) {
					forgeHeat(player, level);
				}
				continue;
			}
			Zone zone = InfernoGeometry.zoneAt(player.getX(), player.getZ());
			if (config.circleTitles) {
				announce(player, zone);
			}
			if (!config.circleHazards || player.isSpectator() || player.isCreative() || warded(player) || Spine.shelters(player) || Shrines.shelters(player)) {
				continue;
			}
			torment(player, level, zone);
		}
	}

	public static boolean warded(ServerPlayer player) {
		HellState.Soul soul = HellState.get(player.level().getServer()).existing(player.getUUID());
		// the purified (all seven P's burned) are beyond the torments for good
		return soul != null && (soul.wardUntil > player.level().getServer().overworld().getGameTime()
				|| Prestige.has(soul, Prestige.Terrace.LUST));
	}

	private static void announce(ServerPlayer player, Zone zone) {
		Circle circle = zone.circle();
		UUID id = player.getUUID();
		if (LAST_CIRCLE.get(id) != circle) {
			LAST_CIRCLE.put(id, circle);
			// the circle's card (name, what it does, what to bring) is drawn by the client (LayerCard)
			Dialogue.say(player, "Dante", ChatFormatting.GRAY, Component.literal(circle.quote()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
			Component rest = circle.depth() > 0 ? Shrines.nearestLine(player) : null;
			if (rest != null) {
				Dialogue.notice(player, rest);
			}
		}
		String region = InfernoGeometry.regionName(player.getX(), player.getZ());
		if (!region.equals(LAST_REGION.put(id, region)) && !region.equals(circle.title())) {
			player.sendOverlayMessage(Component.literal(region).withStyle(ChatFormatting.RED));
		}
	}

	/**
	 * Every torment has a counter (cover, water, sneaking, heat...), and the first time one touches a
	 * player they are told what it is.
	 */
	private static void torment(ServerPlayer player, ServerLevel level, Zone zone) {
		BlockPos pos = player.blockPosition();
		Circle circle = zone.circle();
		switch (circle) {
			// Lust to Violence have rules to play with instead (LayerRules, Hoarders, LowerRules)
			case TREACHERY -> {
				if (nearHeat(level, pos)) {
					// a fire keeps the cold of Cocytus away
					player.setTicksFrozen(Math.max(0, player.getTicksFrozen() - 40));
				} else if (player.canFreeze() && !(LuciferManager.fighting() && LuciferManager.inPit(level, pos))) {
					// the cold builds slowly while you're away from fire: frozen through in about 45 seconds at the lake's
					// edge, 25 in Judecca (the game itself thaws 40 a second, so this is 3 to 6 a second more than that)
					double depth = InfernoGeometry.treacheryDepth(player.getX(), player.getZ());
					int add = 43 + (int) Math.round(depth * 4);
					int cap = player.getTicksRequiredToFreeze() + 40;
					player.setTicksFrozen(Math.min(cap, player.getTicksFrozen() + add));
					hint(player, circle, "The cold of Cocytus builds while you're away from fire (the frost at the screen's edges). Stand by a "
							+ "lit campfire to warm up: light the braziers on the ice, they stay lit for everyone. Leather armour keeps the cold out.");
				}
			}
			default -> {
			}
		}
	}

	/** A lit campfire, fire, lava or magma within 5 blocks. */
	public static boolean nearHeat(ServerLevel level, BlockPos pos) {
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-5, -2, -5), pos.offset(5, 2, 5))) {
			BlockState b = level.getBlockState(p);
			if (b.is(BlockTags.CAMPFIRES) && b.getValue(net.minecraft.world.level.block.CampfireBlock.LIT) || b.is(BlockTags.FIRE) || b.is(Blocks.LAVA)
					|| b.is(Blocks.MAGMA_BLOCK)) {
				return true;
			}
		}
		return false;
	}

	/** The Forge of Dis: close to lava, the heat drains you (Fire Resistance or water keeps it off). */
	private static void forgeHeat(ServerPlayer player, ServerLevel level) {
		if (player.hasEffect(MobEffects.FIRE_RESISTANCE) || player.isInWater()) {
			return;
		}
		BlockPos pos = player.blockPosition();
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-3, -2, -3), pos.offset(3, 2, 3))) {
			if (level.getBlockState(p).is(Blocks.LAVA)) {
				player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 60, 0, true, false, true));
				hintOnce(player, 20, "The heat near lava hurts you here. Fire Resistance or standing in water protects you.");
				return;
			}
		}
	}

	/** Tells a player, once per circle, how to escape its torment. */
	private static void hint(ServerPlayer player, Circle circle, String text) {
		hintOnce(player, circle.ordinal(), text);
	}

	/**
	 * Virgil tells a player something once (soul.hints is a bit set of 64: 0-10 the circles' torments, 11 the Noble
	 * Castle, 20 the Forge's heat, 24-26 the danger language's colours, 27-31 the upper circles' rules, 32 on the lower
	 * circles' (LowerRules and their monsters) from 32 on, 36 Malacoda's trick, 37 Antaeus, 38 the
	 * Malebranche's hooks).
	 */
	/** Whether Virgil has told this player hint `index` (or it was spent some other way, see spendHint). */
	public static boolean hinted(ServerPlayer player, int index) {
		HellState.Soul soul = HellState.get(player.level().getServer()).existing(player.getUUID());
		return soul == null || (soul.hints & (1L << index)) != 0;
	}

	/** Marks hint `index` as told without saying anything (for one-time events). */
	public static void spendHint(ServerPlayer player, int index) {
		HellState.Soul soul = HellState.get(player.level().getServer()).existing(player.getUUID());
		if (soul != null) {
			soul.hints |= 1L << index;
			HellState.get(player.level().getServer()).setDirty();
		}
	}

	public static void hintOnce(ServerPlayer player, int index, String text) {
		HellState.Soul soul = HellState.get(player.level().getServer()).existing(player.getUUID());
		long bit = 1L << index;
		if (soul == null || (soul.hints & bit) != 0) {
			return;
		}
		soul.hints |= bit;
		HellState.get(player.level().getServer()).setDirty();
		Dialogue.say(player, "Virgil", ChatFormatting.GOLD, Component.literal(text).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
	}

	public static void forget(ServerPlayer player) {
		LAST_CIRCLE.remove(player.getUUID());
		LAST_REGION.remove(player.getUUID());
	}
}
