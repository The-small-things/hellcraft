package net.thesmallthings.hellcraft.hazard;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.blood.BloodAltar;
import net.thesmallthings.hellcraft.blood.Ghosts;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.blood.Prestige;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.util.Feedback;
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
	/** Lust: game time of each player's next gust. */
	private static final Map<UUID, Long> NEXT_GUST = new HashMap<>();
	private static int ticks;

	public static void register() {
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer sp && state.is(BlockTags.LOGS)
					&& HellWorldgen.isInferno(serverLevel)
					&& InfernoGeometry.zoneAt(pos.getX(), pos.getZ()) == Zone.WOOD_OF_SUICIDES && !warded(sp)) {
				// "Why dost thou rend me?" -- the trees of the suicides bleed when broken
				sp.hurt(sp.damageSources().magic(), 3.0f);
				serverLevel.sendParticles(BloodAltar.BLOOD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 25, 0.4, 0.4, 0.4, 0.0);
				sp.sendOverlayMessage(Component.literal("\"Why dost thou rend me?\"").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
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
			player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 25));
			player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(circle.title()).withStyle(ChatFormatting.DARK_RED)));
			player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(circle.tagline()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
			player.sendSystemMessage(Component.literal("\"" + circle.quote() + "\"").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			Component rest = circle.depth() > 0 ? Shrines.nearestLine(player) : null;
			if (rest != null) {
				player.sendSystemMessage(rest);
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
		boolean open = level.canSeeSky(pos.above());
		Circle circle = zone.circle();
		switch (circle) {
			case LUST -> {
				// the infernal hurricane that never rests: a gust every 5-9 s shoves you sideways along the circle. It only
				// catches you on your feet (never mid-air, so it can't stack with a breeze's wind charge or a fall), barely
				// lifts you, and sneaking or a roof braces you against it
				long now = level.getGameTime();
				if (now < NEXT_GUST.getOrDefault(player.getUUID(), 0L)) {
					break;
				}
				NEXT_GUST.put(player.getUUID(), now + 100 + level.getRandom().nextInt(80));
				if (open && !player.isShiftKeyDown() && player.onGround() && player.hurtTime == 0 && !player.isFallFlying()) {
					double r = Math.max(1.0, Math.sqrt(player.getX() * player.getX() + player.getZ() * player.getZ()));
					Vec3 tangent = new Vec3(-player.getZ() / r, 0, player.getX() / r);
					double strength = 0.35 + level.getRandom().nextDouble() * 0.25;
					player.push(tangent.x * strength, 0.12, tangent.z * strength);
					Feedback.syncMotion(player);
					level.playSound(null, pos, SoundEvents.ELYTRA_FLYING, SoundSource.WEATHER, 0.4f, 1.6f);
					level.sendParticles(ParticleTypes.CLOUD, player.getX() - tangent.x, player.getY() + 1.0, player.getZ() - tangent.z,
							6, 0.3, 0.4, 0.3, 0.05);
					hint(player, circle, "The wind can't move you while you sneak, or under a roof.");
				}
			}
			case GLUTTONY -> {
				// the cold, heavy rain: a roof keeps it off
				if (open) {
					player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 60, 0, true, false, true));
					hint(player, circle, "Gluttony's rain makes you hungry. Stand under a roof.");
				}
			}
			case GREED -> {
				int weight = greedWeight(player);
				if (weight >= 32) {
					int amp = Math.min(2, weight / 64);
					player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, amp, true, false, true));
					hint(player, circle, "Carrying gold slows you down here. Put it in an ender chest or leave it behind.");
				}
			}
			case WRATH -> {
				if (player.isInWater()) {
					player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, true, false, true));
					player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 60, 1, true, false, true));
					hint(player, circle, "The river Styx hurts you while you're in it. Use a boat or stay on land.");
				}
			}
			case HERESY -> {
				if (open && level.getRandom().nextFloat() < 0.02f) {
					player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0, true, false, true));
					hint(player, circle, "Smoke from the tombs darkens your view under open sky. Stand under a roof.");
				}
			}
			case VIOLENCE -> {
				if (zone == Zone.BURNING_SANDS) {
					burningSands(player, level, pos, open);
				}
			}
			case TREACHERY -> {
				if (nearHeat(level, pos)) {
					// a fire keeps the cold of Cocytus away
					player.setTicksFrozen(Math.max(0, player.getTicksFrozen() - 40));
				} else if (player.canFreeze()) {
					double depth = InfernoGeometry.treacheryDepth(player.getX(), player.getZ());
					int add = 44 + (int) (depth * 60);
					int cap = player.getTicksRequiredToFreeze() + 40;
					player.setTicksFrozen(Math.min(cap, player.getTicksFrozen() + add));
					hint(player, circle, "The ice freezes you. Wear leather armour or stay near a campfire.");
				}
			}
			default -> {
			}
		}
	}

	/** "Dilated flakes of fire, falling slowly": only on the open sand, never in water or under a roof. */
	private static void burningSands(ServerPlayer player, ServerLevel level, BlockPos pos, boolean open) {
		if (!open || player.isInWater() || player.hasEffect(MobEffects.FIRE_RESISTANCE) || !onSand(level, pos)) {
			return;
		}
		// the flakes are seen falling before they land
		level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 3.5, player.getZ(), 6, 1.2, 0.6, 1.2, 0.0);
		level.sendParticles(ParticleTypes.FALLING_LAVA, player.getX(), player.getY() + 4.0, player.getZ(), 4, 1.5, 0.3, 1.5, 0.0);
		if (level.getRandom().nextFloat() < 0.2f) {
			player.igniteForSeconds(3.0f);
			hint(player, Circle.VIOLENCE, "Fire falls on the open sand. A roof, water or Fire Resistance protects you.");
		}
	}

	/** Standing on (or just above) the sand of the Burning Sands. */
	private static boolean onSand(ServerLevel level, BlockPos pos) {
		for (int dy = 1; dy <= 3; dy++) {
			BlockState below = level.getBlockState(pos.below(dy));
			if (below.isAir()) {
				continue;
			}
			return below.is(BlockTags.SAND) || below.is(Blocks.SANDSTONE) || below.is(Blocks.RED_SANDSTONE) || below.is(Blocks.NETHERRACK);
		}
		return false;
	}

	/** A campfire, fire, lava or magma within 4 blocks. */
	private static boolean nearHeat(ServerLevel level, BlockPos pos) {
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-4, -2, -4), pos.offset(4, 2, 4))) {
			BlockState b = level.getBlockState(p);
			if (b.is(BlockTags.CAMPFIRES) || b.is(BlockTags.FIRE) || b.is(Blocks.LAVA) || b.is(Blocks.MAGMA_BLOCK)) {
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
				hintBit(player, 20, "The heat near lava hurts you here. Fire Resistance or standing in water protects you.");
				return;
			}
		}
	}

	/** Tells a player, once per circle, how to escape its torment. */
	private static void hint(ServerPlayer player, Circle circle, String text) {
		hintBit(player, circle.ordinal(), text);
	}

	private static void hintBit(ServerPlayer player, int index, String text) {
		HellState.Soul soul = HellState.get(player.level().getServer()).existing(player.getUUID());
		int bit = 1 << index;
		if (soul == null || (soul.hints & bit) != 0) {
			return;
		}
		soul.hints |= bit;
		HellState.get(player.level().getServer()).setDirty();
		player.sendSystemMessage(Component.literal("Virgil: ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal(text).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
	}

	/** How much treasure a player is hauling, in "gold ingot equivalents". */
	private static int greedWeight(ServerPlayer player) {
		int weight = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack s = player.getInventory().getItem(i);
			int c = s.getCount();
			if (s.is(Items.GOLD_INGOT) || s.is(Items.RAW_GOLD) || s.is(Items.DIAMOND) || s.is(Items.EMERALD) || s.is(Items.GILDED_BLACKSTONE)) {
				weight += c;
			} else if (s.is(Items.GOLD_BLOCK) || s.is(Items.RAW_GOLD_BLOCK) || s.is(Items.DIAMOND_BLOCK) || s.is(Items.EMERALD_BLOCK)) {
				weight += c * 9;
			} else if (s.is(Items.NETHERITE_INGOT)) {
				weight += c * 4;
			} else if (s.is(Items.NETHERITE_BLOCK)) {
				weight += c * 36;
			} else if (s.is(Items.GOLD_NUGGET)) {
				weight += c / 9;
			}
		}
		return weight;
	}

	public static void forget(ServerPlayer player) {
		LAST_CIRCLE.remove(player.getUUID());
		LAST_REGION.remove(player.getUUID());
		NEXT_GUST.remove(player.getUUID());
	}
}
