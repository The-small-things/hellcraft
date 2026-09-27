package net.thesmallthings.hellcraft.hazard;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.blood.BloodAltar;
import net.thesmallthings.hellcraft.blood.Ghosts;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.util.Feedback;
import net.thesmallthings.hellcraft.world.Circle;
import net.thesmallthings.hellcraft.world.HellWorldgen;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
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
				continue;
			}
			Zone zone = InfernoGeometry.zoneAt(player.getX(), player.getZ());
			if (config.circleTitles) {
				announce(player, zone);
			}
			if (!config.circleHazards || player.isSpectator() || player.isCreative() || warded(player) || Spine.shelters(player)) {
				continue;
			}
			torment(player, level, zone);
		}
	}

	public static boolean warded(ServerPlayer player) {
		HellState.Soul soul = HellState.get(player.level().getServer()).existing(player.getUUID());
		return soul != null && soul.wardUntil > player.level().getServer().overworld().getGameTime();
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
		}
		String region = InfernoGeometry.regionName(player.getX(), player.getZ());
		if (!region.equals(LAST_REGION.put(id, region)) && !region.equals(circle.title())) {
			player.sendOverlayMessage(Component.literal(region).withStyle(ChatFormatting.RED));
		}
	}

	private static void torment(ServerPlayer player, ServerLevel level, Zone zone) {
		BlockPos pos = player.blockPosition();
		boolean open = level.canSeeSky(pos.above());
		switch (zone.circle()) {
			case LUST -> {
				// the infernal hurricane that never rests
				if (open && level.getRandom().nextFloat() < 0.35f) {
					double r = Math.max(1.0, Math.sqrt(player.getX() * player.getX() + player.getZ() * player.getZ()));
					Vec3 tangent = new Vec3(-player.getZ() / r, 0, player.getX() / r);
					double strength = 0.6 + level.getRandom().nextDouble() * 0.8;
					player.push(tangent.x * strength, 0.25 + level.getRandom().nextDouble() * 0.3, tangent.z * strength);
					Feedback.syncMotion(player);
					level.playSound(null, pos, SoundEvents.ELYTRA_FLYING, SoundSource.WEATHER, 0.4f, 1.6f);
				}
			}
			case GLUTTONY -> player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 60, 0, true, false, true));
			case GREED -> {
				int weight = greedWeight(player);
				if (weight >= 32) {
					int amp = Math.min(2, weight / 64);
					player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, amp, true, false, true));
				}
			}
			case WRATH -> {
				if (player.isInWater()) {
					player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, true, false, true));
					player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 60, 1, true, false, true));
				}
			}
			case HERESY -> {
				if (level.getRandom().nextFloat() < 0.06f) {
					player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0, true, false, true));
				}
			}
			case VIOLENCE -> {
				if (zone == Zone.BURNING_SANDS && open && !player.hasEffect(MobEffects.FIRE_RESISTANCE)
						&& level.getRandom().nextFloat() < 0.3f) {
					// dilated flakes of fire, falling slowly
					player.igniteForSeconds(3.0f);
				}
			}
			case TREACHERY -> {
				if (player.canFreeze()) {
					double depth = InfernoGeometry.treacheryDepth(player.getX(), player.getZ());
					int add = 44 + (int) (depth * 60);
					int cap = player.getTicksRequiredToFreeze() + 40;
					player.setTicksFrozen(Math.min(cap, player.getTicksFrozen() + add));
				}
			}
			default -> {
			}
		}
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
	}
}
