package net.thesmallthings.hellcraft.hazard;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.util.Feedback;
import net.thesmallthings.hellcraft.world.Circle;
import net.thesmallthings.hellcraft.world.HellWorldgen;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.Shrines;
import net.thesmallthings.hellcraft.world.Spine;
import net.thesmallthings.hellcraft.world.Sullen;
import net.thesmallthings.hellcraft.world.WindStreams;
import net.thesmallthings.hellcraft.world.Zone;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The upper circles' rules (P3: each punishment turned into something you play with), on the server side:
 * <ul>
 *     <li><b>Lust: the wind is a road.</b> While the wind carries a player (client/layer/LayerFx moves them along
 *     WindStreams) they weigh nothing, so the server doesn't take them for a flyer and they take no fall damage;
 *     letting go (sneaking) lets them glide down (Slow Falling).</li>
 *     <li><b>Gluttony: the mud swallows you if you stand still.</b> Standing still on soft ground sinks you, in
 *     stages: slowed, held, then choking. Moving, or standing on roots, stone or planks, is safe.</li>
 *     <li><b>Wrath: the sullen lurk under the water.</b> Swim over a spot where bubbles rise (Sullen) and hands drag
 *     you down for two seconds. Plank roads and boats are safe.</li>
 * </ul>
 * Greed's rule (gold draws the hoarders) is Hoarders. A Blood Ward, a Virgil's Rest and the Spines shelter you from
 * the mud and the hands, as from every torment.
 */
public final class LayerRules {
	private LayerRules() {
	}

	private static final Identifier WIND = HellcraftMod.id("wind");
	private static final int HINT_WIND = 27;
	private static final int HINT_SULLEN = 28;
	private static final int HINT_MUD = 29;
	/** Ticks before the same soul can be grabbed again (a grip lasts sullenGripTicks). */
	private static final int GRIP_COOLDOWN = 60;

	private static final Set<UUID> RIDING = new HashSet<>();
	private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();
	private static final Map<UUID, Integer> STILL = new HashMap<>();
	private static final Map<UUID, Integer> GRIPPED = new HashMap<>();
	private static final Map<UUID, Long> GRIP_FREE_AT = new HashMap<>();

	public static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (!HellWorldgen.isInferno(level)) {
			return;
		}
		boolean second = server.getTickCount() % 20 == 0;
		Set<UUID> here = new HashSet<>();
		for (ServerPlayer p : level.players()) {
			here.add(p.getUUID());
			if (p.isSpectator()) {
				stopRiding(p);
				continue;
			}
			Zone zone = InfernoGeometry.zoneAt(p.getX(), p.getZ());
			wind(p, zone);
			if (p.isCreative() || !HellConfig.get().circleHazards || CircleHazards.warded(p) || Spine.shelters(p) || Shrines.shelters(p)) {
				GRIPPED.remove(p.getUUID());
				STILL.remove(p.getUUID());
				continue;
			}
			sullen(p, zone, level);
			if (second) {
				mud(p, zone, level);
			}
		}
		// whoever left the overworld (or the server) lets go of the wind
		RIDING.removeIf(id -> !here.contains(id));
	}

	// ------------------------------------------------------------------ Lust: the wind is a road

	private static void wind(ServerPlayer p, Zone zone) {
		WindStreams.Hit hit = zone.circle() == Circle.LUST ? WindStreams.find(p.getX(), p.getY() + 0.9, p.getZ()) : null;
		boolean ride = hit != null && !hit.atEnd() && !p.isShiftKeyDown() && !p.isPassenger() && !p.isFallFlying();
		if (ride) {
			if (RIDING.add(p.getUUID())) {
				AttributeInstance gravity = p.getAttribute(Attributes.GRAVITY);
				if (gravity != null && !gravity.hasModifier(WIND)) {
					gravity.addTransientModifier(new AttributeModifier(WIND, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
				}
				CircleHazards.hintOnce(p, HINT_WIND, "The wind is a road: it carries you down the circle to the next way down. Sneak to let go, and you'll glide.");
			}
			p.resetFallDistance();
		} else if (RIDING.contains(p.getUUID())) {
			stopRiding(p);
		}
	}

	private static void stopRiding(ServerPlayer p) {
		if (!RIDING.remove(p.getUUID())) {
			return;
		}
		AttributeInstance gravity = p.getAttribute(Attributes.GRAVITY);
		if (gravity != null) {
			gravity.removeModifier(WIND);
		}
		p.resetFallDistance();
		if (!p.onGround()) {
			p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 120, 0, true, false, true));
		}
	}

	public static boolean riding(ServerPlayer p) {
		return RIDING.contains(p.getUUID());
	}

	// ------------------------------------------------------------------ Gluttony: the mud

	/** Soft ground the mud can take you down through; roots, stone, planks and the like hold you up. */
	static boolean soft(BlockState state) {
		if (state.is(Blocks.MUDDY_MANGROVE_ROOTS) || state.is(Blocks.MANGROVE_ROOTS)) {
			return false;
		}
		return state.is(BlockTags.DIRT) || state.is(Blocks.MUD) || state.is(Blocks.CLAY) || state.is(Blocks.SOUL_SOIL) || state.is(Blocks.SOUL_SAND);
	}

	private static void mud(ServerPlayer p, Zone zone, ServerLevel level) {
		Vec3 last = LAST_POS.put(p.getUUID(), p.position());
		if (zone.circle() != Circle.GLUTTONY || !p.onGround() || p.isPassenger()) {
			STILL.remove(p.getUUID());
			return;
		}
		BlockPos under = p.getOnPos();
		boolean soft = soft(level.getBlockState(under));
		double moved = last == null ? 1 : Math.hypot(p.getX() - last.x, p.getZ() - last.z);
		int still = STILL.getOrDefault(p.getUUID(), 0);
		still = !soft || moved > 0.6 ? Math.max(0, still - 2) : still + 1;
		STILL.put(p.getUUID(), still);
		int stage = HellConfig.get().mudSeconds;
		if (still < stage) {
			return;
		}
		BlockParticleOption mud = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MUD.defaultBlockState());
		level.sendParticles(mud, p.getX(), p.getY() + 0.1, p.getZ(), 12, 0.3, 0.05, 0.3, 0.05);
		level.playSound(null, p.blockPosition(), SoundEvents.MUD_STEP, SoundSource.BLOCKS, 1.0f, 0.6f);
		CircleHazards.hintOnce(p, HINT_MUD, "Gluttony's mud swallows whoever stands still. Keep moving, or stand on roots, stone or planks.");
		if (still < 2 * stage) {
			p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 0, true, false, true));
			p.sendOverlayMessage(Component.literal("The mud is pulling you down: keep moving!").withStyle(ChatFormatting.GOLD));
		} else if (still < 3 * stage) {
			p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 2, true, false, true));
			p.sendOverlayMessage(Component.literal("The mud has you: move, or get onto roots, stone or planks!").withStyle(ChatFormatting.RED));
		} else {
			p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 3, true, false, true));
			p.hurtServer(level, level.damageSources().drown(), 1.0f);
			p.sendOverlayMessage(Component.literal("The mud is swallowing you!").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
		}
	}

	// ------------------------------------------------------------------ Wrath: the sullen

	private static void sullen(ServerPlayer p, Zone zone, ServerLevel level) {
		UUID id = p.getUUID();
		Integer left = GRIPPED.get(id);
		if (left != null) {
			if (left <= 0 || !p.isInWater() || p.isPassenger()) {
				GRIPPED.remove(id);
				GRIP_FREE_AT.put(id, level.getGameTime() + GRIP_COOLDOWN);
				return;
			}
			Vec3 v = p.getDeltaMovement();
			p.setDeltaMovement(v.x * 0.5, -0.22, v.z * 0.5);
			Feedback.syncMotion(p);
			if (left % 10 == 0) {
				p.hurtServer(level, level.damageSources().drown(), 1.5f);
				level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, p.getX(), p.getY() + 0.5, p.getZ(), 20, 0.4, 0.4, 0.4, 0.05);
			}
			GRIPPED.put(id, left - 1);
			return;
		}
		if (zone != Zone.STYX || !p.isInWater() || p.isPassenger() || level.getGameTime() < GRIP_FREE_AT.getOrDefault(id, 0L)) {
			return;
		}
		if (Sullen.near(p.getX(), p.getZ()) == null) {
			return;
		}
		if (HellConfig.get().sullenGripTicks <= 0) {
			return;
		}
		GRIPPED.put(id, HellConfig.get().sullenGripTicks);
		level.playSound(null, p.blockPosition(), SoundEvents.DROWNED_AMBIENT_WATER, SoundSource.HOSTILE, 1.5f, 0.6f);
		p.sendOverlayMessage(Component.literal("Hands drag you down! Swim clear of the bubbles.").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD));
		CircleHazards.hintOnce(p, HINT_SULLEN, "The sullen lurk under the Styx where bubbles rise. Keep to the plank roads, or take a boat.");
	}

	/** For the smoke test: what counts as soft ground, and that the wind and the sullen are where they should be. */
	public static String selfTest() {
		boolean mud = soft(Blocks.MUD.defaultBlockState());
		boolean roots = soft(Blocks.MUDDY_MANGROVE_ROOTS.defaultBlockState());
		boolean stone = soft(Blocks.STONE.defaultBlockState());
		WindStreams.Stream s = WindStreams.all().getFirst();
		boolean inLust = InfernoGeometry.zoneAt(s.x()[s.size() / 2], s.z()[s.size() / 2]) == Zone.LUST;
		int sullen = 0;
		for (int x = (int) InfernoGeometry.scaled(1450); x < InfernoGeometry.scaled(1640); x++) {
			if (Sullen.spot(x, 60) != null) {
				sullen++;
			}
		}
		return "Layer rules test: mud soft=" + mud + " roots soft=" + roots + " stone soft=" + stone + "; " + WindStreams.all().size()
				+ " wind streams, in Lust=" + inLust + "; sullen spots on a line across the Styx=" + (sullen > 0);
	}
}
