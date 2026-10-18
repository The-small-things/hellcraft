package net.thesmallthings.hellcraft.hazard;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.blood.BloodAltar;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.world.Circle;
import net.thesmallthings.hellcraft.world.HellWorldgen;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.Shrines;
import net.thesmallthings.hellcraft.world.Spine;
import net.thesmallthings.hellcraft.world.Tombs;
import net.thesmallthings.hellcraft.world.Zone;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The lower circles' rules (P3: each punishment turned into something you play with), on the server side:
 * <ul>
 *     <li><b>Heresy: the tombs blast fire in turn.</b> Every open tomb (Tombs) warns with a red circle, then blasts a
 *     column of fire for a moment; they go in diagonal waves, so watch the rhythm and cross between blasts.</li>
 *     <li><b>Violence, the Wood of the Suicides: sneak.</b> Pushing through the dead branches without sneaking, or
 *     breaking them, makes noise; too much and the trees scream, and the harpies come.</li>
 *     <li><b>Violence, the Burning Sands: fire falls in waves.</b> The sky reddens and a countdown warns, then fire
 *     rains on the open sand for a few seconds. Get under one of the rock arches (or into water) until it passes.</li>
 * </ul>
 * A Blood Ward, a Virgil's Rest and the Spines shelter you, as from every torment.
 */
public final class LowerRules {
	private LowerRules() {
	}

	static final int HINT_TOMBS = 32;
	static final int HINT_SANDS = 33;
	static final int HINT_WOOD = 34;

	/** How near a player a tomb must be for its rhythm to run (blocks). */
	private static final int TOMB_RANGE = 28;

	/** The Burning Sands' rhythm (ticks of game time): calm, then a warning, then the fire falls. */
	public static final int SANDS_PERIOD = 480;
	public static final int SANDS_WARN_AT = 280;
	public static final int SANDS_FIRE_AT = 360;

	/** The Wood's noise: a warning at WOOD_WARN, the scream at WOOD_SCREAM; quiet for WOOD_CALM ticks after. */
	public static final int WOOD_WARN = 4;
	public static final int WOOD_SCREAM = 8;
	private static final int WOOD_CALM = 600;
	private static final Map<UUID, Integer> NOISE = new HashMap<>();
	private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();
	private static final Map<UUID, Long> CALM_UNTIL = new HashMap<>();

	public static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (!HellWorldgen.isInferno(level) || !HellConfig.get().circleHazards) {
			return;
		}
		long now = level.getGameTime();
		Set<Tombs.Tomb> tombs = new HashSet<>();
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator()) {
				continue;
			}
			Zone zone = InfernoGeometry.zoneAt(p.getX(), p.getZ());
			if (zone == Zone.HERESY) {
				int x = (int) Math.floor(p.getX());
				int z = (int) Math.floor(p.getZ());
				tombs.addAll(Tombs.within(x - TOMB_RANGE, z - TOMB_RANGE, x + TOMB_RANGE, z + TOMB_RANGE));
			}
			if (zone == Zone.BURNING_SANDS) {
				sands(p, level, now);
			}
			if (now % 10 == 0) {
				wood(p, level, zone, now);
			}
		}
		for (Tombs.Tomb t : tombs) {
			tomb(level, t, now);
		}
	}

	static boolean exempt(ServerPlayer p) {
		return p.isSpectator() || p.isCreative() || CircleHazards.warded(p) || Spine.shelters(p) || Shrines.shelters(p);
	}

	// ------------------------------------------------------------------ Heresy: the tombs

	/** The top of a tomb's walls (its fire burns one block below). */
	private static double tombTop(ServerLevel level, Tombs.Tomb t) {
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(t.centreX()), (int) Math.floor(t.centreZ()));
	}

	private static void tomb(ServerLevel level, Tombs.Tomb t, long now) {
		if (!level.isLoaded(BlockPos.containing(t.centreX(), 64, t.centreZ()))) {
			return;
		}
		int phase = t.phase(now);
		double y = tombTop(level, t);
		Vec3 centre = new Vec3(t.centreX(), y, t.centreZ());
		if (phase == 0) {
			Danger.circle(level, Danger.Colour.RED, centre, Tombs.REACH, Tombs.WARN);
			level.playSound(null, BlockPos.containing(centre), SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 1.5f, 0.6f);
		} else if (phase < Tombs.WARN) {
			if (phase % 4 == 0) {
				level.sendParticles(ParticleTypes.LARGE_SMOKE, centre.x, y, centre.z, 3, 0.6, 0.1, 0.6, 0.02);
			}
		} else if (phase < Tombs.WARN + Tombs.BLAST) {
			if (phase == Tombs.WARN) {
				level.playSound(null, BlockPos.containing(centre), SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 2.0f, 0.5f);
				ServerPlayer witness = level.getNearestPlayer(centre.x, centre.y, centre.z, 20, false) instanceof ServerPlayer sp ? sp : null;
				if (witness != null && !exempt(witness)) {
					LowerMonsters.heretic(level, centre, witness);
				}
			}
			// a roaring column of fire: jets shot straight up out of the tomb, smoke and sparks over them
			for (int i = 0; i < 14; i++) {
				double ox = (level.getRandom().nextDouble() - 0.5) * 1.6;
				double oz = (level.getRandom().nextDouble() - 0.5) * 1.6;
				level.sendParticles(i % 3 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, true, false, centre.x + ox, y + 0.2, centre.z + oz, 0,
						ox * 0.05, 0.55 + level.getRandom().nextDouble() * 0.35, oz * 0.05, 1.0);
			}
			if (phase % 3 == 0) {
				level.sendParticles(ParticleTypes.LARGE_SMOKE, true, false, centre.x, y + 7, centre.z, 4, 0.6, 0.6, 0.6, 0.03);
				level.sendParticles(ParticleTypes.LAVA, centre.x, y + 1, centre.z, 2, 0.5, 0.2, 0.5, 0.0);
			}
			if (phase % 5 == 0) {
				burn(level, t, centre);
			}
		}
	}

	private static void burn(ServerLevel level, Tombs.Tomb t, Vec3 centre) {
		for (ServerPlayer p : level.players()) {
			if (exempt(p)) {
				continue;
			}
			double dy = p.getY() - centre.y;
			if (dy < -2 || dy > 8 || Math.hypot(p.getX() - centre.x, p.getZ() - centre.z) > Tombs.REACH) {
				continue;
			}
			p.igniteForSeconds(4.0f);
			p.hurtServer(level, level.damageSources().inFire(), 3.0f);
			CircleHazards.hintOnce(p, HINT_TOMBS, "The tombs blast fire in turn: a red circle warns which goes next. Watch the rhythm and cross between blasts.");
		}
	}

	// ------------------------------------------------------------------ Violence: the Wood of the Suicides

	private static void wood(ServerPlayer p, ServerLevel level, Zone zone, long now) {
		UUID id = p.getUUID();
		Vec3 last = LAST_POS.put(id, p.position());
		if (zone != Zone.WOOD_OF_SUICIDES || exempt(p)) {
			NOISE.remove(id);
			return;
		}
		double moved = last == null ? 0 : Math.hypot(p.getX() - last.x, p.getZ() - last.z);
		int noise = NOISE.getOrDefault(id, 0);
		if (moved > 0.8 && !p.isShiftKeyDown() && amongBranches(level, p)) {
			noise += p.isSprinting() ? 2 : 1;
		} else if (noise > 0 && (p.isShiftKeyDown() || moved < 0.2)) {
			noise--;
		}
		noise(p, level, noise, now);
	}

	/** Breaking a branch in the Wood is loud (CircleHazards hears it). */
	public static void branchBroken(ServerPlayer p) {
		if (exempt(p)) {
			return;
		}
		noise(p, p.level(), NOISE.getOrDefault(p.getUUID(), 0) + 4, p.level().getGameTime());
	}

	private static void noise(ServerPlayer p, ServerLevel level, int noise, long now) {
		UUID id = p.getUUID();
		if (now < CALM_UNTIL.getOrDefault(id, 0L)) {
			NOISE.remove(id);
			return;
		}
		int before = NOISE.getOrDefault(id, 0);
		NOISE.put(id, noise);
		if (noise >= WOOD_SCREAM) {
			scream(p, level, now);
		} else if (noise >= WOOD_WARN && before < WOOD_WARN) {
			level.playSound(null, p.blockPosition(), SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.0f, 0.6f);
			p.sendOverlayMessage(Component.literal("Branches crack and the trees groan... sneak, or they'll scream!").withStyle(ChatFormatting.GOLD));
			CircleHazards.hintOnce(p, HINT_WOOD, "In the Wood of the Suicides, pushing through the branches or breaking them makes the trees scream, "
					+ "and the harpies come. Sneak through it (hold sneak), and leave the branches be.");
		}
	}

	/** Dead branches close by: brushing past them is what makes the noise. */
	private static boolean amongBranches(ServerLevel level, ServerPlayer p) {
		BlockPos feet = p.blockPosition();
		for (BlockPos b : BlockPos.betweenClosed(feet.offset(-1, 0, -1), feet.offset(1, 2, 1))) {
			if (level.getBlockState(b).is(BlockTags.LOGS)) {
				return true;
			}
		}
		return false;
	}

	private static void scream(ServerPlayer p, ServerLevel level, long now) {
		NOISE.remove(p.getUUID());
		CALM_UNTIL.put(p.getUUID(), now + WOOD_CALM);
		level.playSound(null, p.blockPosition(), SoundEvents.GHAST_SCREAM, SoundSource.HOSTILE, 2.0f, 0.5f);
		level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.0f, 0.6f);
		BlockPos feet = p.blockPosition();
		for (BlockPos b : BlockPos.betweenClosed(feet.offset(-5, -1, -5), feet.offset(5, 6, 5))) {
			if (level.getBlockState(b).is(BlockTags.LOGS) && level.getRandom().nextInt(6) == 0) {
				level.sendParticles(BloodAltar.BLOOD, b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.0);
			}
		}
		p.sendOverlayMessage(Component.literal("The trees scream! The harpies are coming.").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
		LowerMonsters.harpies(level, p, 2 + level.getRandom().nextInt(2));
	}

	// ------------------------------------------------------------------ Violence: the Burning Sands

	/** Where the Sands are in their rhythm (0..SANDS_PERIOD-1), the same for everyone. */
	public static int sandsPhase(long gameTime) {
		return (int) Math.floorMod(gameTime, (long) SANDS_PERIOD);
	}

	public static boolean fireFalling(long gameTime) {
		return sandsPhase(gameTime) >= SANDS_FIRE_AT;
	}

	/** Under a roof of any kind (a rock arch, a tunnel), in water, or proof against fire. */
	static boolean sheltered(ServerPlayer p, ServerLevel level) {
		return !level.canSeeSky(BlockPos.containing(p.getX(), p.getEyeY(), p.getZ())) || p.isInWater()
				|| p.hasEffect(MobEffects.FIRE_RESISTANCE);
	}

	private static void sands(ServerPlayer p, ServerLevel level, long now) {
		int phase = sandsPhase(now);
		if (phase < SANDS_WARN_AT) {
			return;
		}
		boolean safe = sheltered(p, level);
		if (phase < SANDS_FIRE_AT) {
			// the warning: the sky reddens, embers drift high above, and a countdown
			if (phase == SANDS_WARN_AT) {
				level.playSound(null, p.blockPosition(), SoundEvents.BLAZE_AMBIENT, SoundSource.AMBIENT, 1.5f, 0.4f);
			}
			if (phase % 4 == 0) {
				level.sendParticles(ParticleTypes.LAVA, true, false, p.getX(), p.getY() + 18, p.getZ(), 4, 10, 2, 10, 0.0);
			}
			if (phase % 20 == 0 && !exempt(p)) {
				int seconds = (SANDS_FIRE_AT - phase + 19) / 20;
				p.sendOverlayMessage(Component.literal(safe ? "Fire will fall in " + seconds + "... you're under cover. Stay there."
						: "Fire will fall in " + seconds + "... get under a rock!").withStyle(safe ? ChatFormatting.GOLD : ChatFormatting.RED, ChatFormatting.BOLD));
			}
			return;
		}
		// the fire falls: "dilated flakes of fire, falling slowly"
		if (phase % 2 == 0) {
			level.sendParticles(ParticleTypes.FLAME, true, false, p.getX(), p.getY() + 8, p.getZ(), 14, 9, 3, 9, 0.02);
			level.sendParticles(ParticleTypes.FALLING_LAVA, true, false, p.getX(), p.getY() + 10, p.getZ(), 10, 9, 2, 9, 0.0);
		}
		if (phase == SANDS_FIRE_AT) {
			level.playSound(null, p.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.AMBIENT, 1.5f, 0.6f);
		}
		if (phase % 10 != 0 || exempt(p) || safe) {
			return;
		}
		p.igniteForSeconds(4.0f);
		p.hurtServer(level, level.damageSources().inFire(), 1.5f);
		p.sendOverlayMessage(Component.literal("Fire is falling: get under a rock!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
		CircleHazards.hintOnce(p, HINT_SANDS, "On the Burning Sands fire falls in waves. When the countdown starts, get under a rock arch (or into water) until it passes.");
	}

	/** For the smoke test: the tombs and the Sands' rhythm. */
	public static String selfTest() {
		List<Tombs.Tomb> tombs = Tombs.within((int) InfernoGeometry.scaled(1150), -150, (int) InfernoGeometry.scaled(1350), 150);
		boolean inHeresy = !tombs.isEmpty() && tombs.stream().allMatch(t -> InfernoGeometry.zoneAt(t.centreX(), t.centreZ()) == Zone.HERESY);
		int[] slots = new int[Tombs.SLOTS];
		tombs.forEach(t -> slots[t.slot()]++);
		boolean everySlot = java.util.Arrays.stream(slots).allMatch(n -> n > 0);
		boolean noneOnRoad = tombs.stream().noneMatch(t -> InfernoGeometry.onRoad(t.centreX(), t.centreZ()));
		boolean rhythm = !fireFalling(SANDS_FIRE_AT - 1) && fireFalling(SANDS_FIRE_AT) && !fireFalling(SANDS_PERIOD);
		return "Lower rules test: " + tombs.size() + " tombs near the east road, in Heresy=" + inHeresy + ", every slot used=" + everySlot
				+ ", none on the road=" + noneOnRoad + "; sands rhythm=" + rhythm + "; circle " + Circle.HERESY.depth();
	}
}
