package net.thesmallthings.hellcraft.client.danger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.hazard.Danger;
import net.thesmallthings.hellcraft.net.DangerPayload;

/**
 * The moment a boss's blow lands (a danger mark fills, DangerMarks): the whole mark erupts in its colour, so a blow
 * reads from across the arena. Red bursts into fire, smoke and flying earth; white cracks with light and sparks;
 * purple tears open in violet. Its edge throws out a shockwave, a circle sends a column up from its heart, a line
 * blasts along its length, and the view shakes for whoever stands close. Drawn on the client, from the mark alone.
 */
public final class Impacts {
	private Impacts() {
	}

	private static final int[][] DUST = {{0xFF2A10, 0xFF8A1E, 0x5A0A05}, {0xFFFFFF, 0xFFF2B0, 0xBFE8FF}, {0xB040FF, 0xE070FF, 0x4A1080}};
	/** At most this many points of a mark erupt (big marks are sampled more thinly). */
	private static final int MAX_POINTS = 220;
	private static final int SHAKE = 14;

	private static float shake;
	private static int shakeAge = -1;

	/** A mark's blow has landed: make it erupt. */
	static void land(DangerPayload p, double x, double z) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null) {
			return;
		}
		RandomSource random = level.getRandom();
		int colour = Math.min(DUST.length - 1, p.colour());
		Danger.Shape shape = Danger.Shape.values()[p.shape()];
		double size = Math.max(1, p.size());
		// the inside: points spread over the mark, each a burst of dust, the colour's own fire and the ground's crumbs
		double area = switch (shape) {
			case CIRCLE -> Math.PI * size * size;
			case RING -> Math.PI * (size * size - p.width() * p.width());
			case LINE -> size * p.width() * 2;
			case CONE -> size * size * p.width();
			case CLEAR -> 0;
		};
		int points = Mth.clamp((int) (area * 1.2), 16, MAX_POINTS);
		for (int i = 0; i < points; i++) {
			Vec3 at = inside(p, shape, x, z, size, random);
			if (at == null) {
				continue;
			}
			burst(level, colour, at, random, 1.0);
		}
		// the edge's shockwave and the heart's column
		switch (shape) {
			case CIRCLE, RING -> {
				shockwave(level, colour, x, p.y(), z, size, random);
				if (shape == Danger.Shape.CIRCLE) {
					column(level, colour, x, ground(level, x, p.y(), z), z, Math.min(10, 3 + size), random);
				}
			}
			case LINE -> {
				for (double d = 0; d <= size; d += 2.5) {
					double lx = x + Math.cos(p.angle()) * d;
					double lz = z + Math.sin(p.angle()) * d;
					big(level, colour, lx, ground(level, lx, p.y(), lz) + 0.6, lz, random);
				}
			}
			case CONE -> {
				for (double a = -p.width(); a <= p.width(); a += 0.15) {
					double lx = x + Math.cos(p.angle() + a) * size;
					double lz = z + Math.sin(p.angle() + a) * size;
					big(level, colour, lx, ground(level, lx, p.y(), lz) + 0.6, lz, random);
				}
			}
			case CLEAR -> {
			}
		}
		sound(level, colour, x, p.y(), z, size, random);
		// close by, the ground jumps under you
		if (mc.player != null) {
			double near = Math.sqrt(mc.player.distanceToSqr(x, p.y(), z)) - size;
			float strength = (float) Mth.clamp(1.0 - near / 14.0, 0, 1) * (float) Mth.clamp(size / 6.0, 0.35, 1.0);
			if (strength > shake * (1f - Math.max(0, shakeAge) / (float) SHAKE)) {
				shake = strength;
				shakeAge = 0;
			}
		}
	}

	/** A random point inside the mark at ground height, or null. */
	private static Vec3 inside(DangerPayload p, Danger.Shape shape, double x, double z, double size, RandomSource random) {
		ClientLevel level = Minecraft.getInstance().level;
		double px;
		double pz;
		switch (shape) {
			case CIRCLE -> {
				double r = size * Math.sqrt(random.nextDouble());
				double a = random.nextDouble() * Math.PI * 2;
				px = x + Math.cos(a) * r;
				pz = z + Math.sin(a) * r;
			}
			case RING -> {
				double r = Mth.lerp(Math.sqrt(random.nextDouble()), p.width(), size);
				double a = random.nextDouble() * Math.PI * 2;
				px = x + Math.cos(a) * r;
				pz = z + Math.sin(a) * r;
			}
			case LINE -> {
				double along = random.nextDouble() * size;
				double across = (random.nextDouble() * 2 - 1) * p.width();
				px = x + Math.cos(p.angle()) * along - Math.sin(p.angle()) * across;
				pz = z + Math.sin(p.angle()) * along + Math.cos(p.angle()) * across;
			}
			case CONE -> {
				double r = size * Math.sqrt(random.nextDouble());
				double a = p.angle() + (random.nextDouble() * 2 - 1) * p.width();
				px = x + Math.cos(a) * r;
				pz = z + Math.sin(a) * r;
			}
			default -> {
				return null;
			}
		}
		return new Vec3(px, ground(level, px, p.y(), pz), pz);
	}

	/** One point of the eruption: big dust thrown up, the colour's own fire or light, and crumbs of the ground. */
	private static void burst(ClientLevel level, int colour, Vec3 at, RandomSource random, double force) {
		int[] dust = DUST[colour];
		add(level, new DustParticleOptions(dust[random.nextInt(dust.length)], 2.2f + random.nextFloat() * 1.8f), at.x, at.y + 0.2, at.z,
				(random.nextDouble() - 0.5) * 0.2, 0.15 + random.nextDouble() * 0.35 * force, (random.nextDouble() - 0.5) * 0.2);
		ParticleOptions own = switch (colour) {
			case 0 -> random.nextInt(3) == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME;
			case 1 -> random.nextBoolean() ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK;
			default -> random.nextBoolean() ? ParticleTypes.WITCH : ParticleTypes.REVERSE_PORTAL;
		};
		add(level, own, at.x, at.y + 0.3, at.z, (random.nextDouble() - 0.5) * 0.25, 0.25 + random.nextDouble() * 0.5 * force, (random.nextDouble() - 0.5) * 0.25);
		if (random.nextInt(3) == 0) {
			BlockState ground = level.getBlockState(BlockPos.containing(at.x, at.y - 0.5, at.z));
			if (!ground.isAir()) {
				for (int k = 0; k < 3; k++) {
					add(level, new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.2, at.z,
							(random.nextDouble() - 0.5) * 0.5, 0.4 + random.nextDouble() * 0.5 * force, (random.nextDouble() - 0.5) * 0.5);
				}
			}
		}
		if (colour == 0 && random.nextInt(4) == 0) {
			add(level, ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.5, at.z, 0, 0.12, 0);
		}
	}

	/** A great puff (an explosion's cloud) in the mark's colour. */
	private static void big(ClientLevel level, int colour, double x, double y, double z, RandomSource random) {
		add(level, ParticleTypes.EXPLOSION, x, y, z, 0, 0, 0);
		add(level, ColorParticleOption.create(ParticleTypes.FLASH, DUST[colour][0]), x, y + 0.5, z, 0, 0, 0);
		for (int k = 0; k < 6; k++) {
			burst(level, colour, new Vec3(x + random.nextDouble() - 0.5, y - 0.5, z + random.nextDouble() - 0.5), random, 1.6);
		}
	}

	/** The edge of a circle or ring throws dust outward, with great puffs all round it. */
	private static void shockwave(ClientLevel level, int colour, double x, double y, double z, double radius, RandomSource random) {
		int[] dust = DUST[colour];
		int around = Mth.clamp((int) (radius * Math.PI * 2 / 0.7), 12, 160);
		for (int i = 0; i < around; i++) {
			double a = i * Math.PI * 2 / around;
			double ex = x + Math.cos(a) * radius;
			double ez = z + Math.sin(a) * radius;
			double ey = ground(level, ex, y, ez) + 0.3;
			add(level, new DustParticleOptions(dust[0], 3.0f + random.nextFloat()), ex, ey, ez, Math.cos(a) * 0.35, 0.08, Math.sin(a) * 0.35);
			if (i % Math.max(1, around / 12) == 0) {
				add(level, ParticleTypes.EXPLOSION, ex, ey + 0.4, ez, 0, 0, 0);
			}
			if (colour != 0 && random.nextInt(3) == 0) {
				add(level, ParticleTypes.SWEEP_ATTACK, ex, ey + 0.4, ez, 0, 0, 0);
			}
		}
	}

	/** A column of the colour's fire or light rising from the heart of a circle. */
	private static void column(ClientLevel level, int colour, double x, double y, double z, double height, RandomSource random) {
		add(level, ColorParticleOption.create(ParticleTypes.FLASH, DUST[colour][0]), x, y + 1, z, 0, 0, 0);
		add(level, ParticleTypes.EXPLOSION_EMITTER, x, y + 0.5, z, 0, 0, 0);
		for (int i = 0; i < 40; i++) {
			double h = random.nextDouble() * height;
			ParticleOptions own = switch (colour) {
				case 0 -> ParticleTypes.FLAME;
				case 1 -> ParticleTypes.END_ROD;
				default -> ParticleTypes.WITCH;
			};
			add(level, own, x + (random.nextDouble() - 0.5) * 0.8, y + h, z + (random.nextDouble() - 0.5) * 0.8, 0, 0.15 + random.nextDouble() * 0.3, 0);
			add(level, new DustParticleOptions(DUST[colour][random.nextInt(3)], 3.5f), x + (random.nextDouble() - 0.5), y + h, z + (random.nextDouble() - 0.5),
					0, 0.1, 0);
		}
		if (colour == 0) {
			for (int i = 0; i < 6; i++) {
				add(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x + (random.nextDouble() - 0.5) * 2, y + 0.5, z + (random.nextDouble() - 0.5) * 2, 0, 0.08, 0);
			}
		}
	}

	private static void sound(ClientLevel level, int colour, double x, double y, double z, double size, RandomSource random) {
		SoundEvent sound = switch (colour) {
			case 0 -> SoundEvents.GENERIC_EXPLODE.value();
			case 1 -> SoundEvents.MACE_SMASH_GROUND_HEAVY;
			default -> SoundEvents.BREEZE_WIND_CHARGE_BURST.value();
		};
		float volume = (float) Mth.clamp(0.5 + size / 10, 0.6, 1.6);
		level.playLocalSound(x, y, z, sound, SoundSource.HOSTILE, volume, 0.75f + random.nextFloat() * 0.2f, false);
	}

	private static void add(ClientLevel level, ParticleOptions particle, double x, double y, double z, double vx, double vy, double vz) {
		level.addAlwaysVisibleParticle(particle, true, x, y, z, vx, vy, vz);
	}

	private static double ground(ClientLevel level, double x, double y, double z) {
		return DangerMarks.ground(level, Mth.floor(x), y, Mth.floor(z));
	}

	public static void tick() {
		if (shakeAge >= 0 && ++shakeAge > SHAKE) {
			shakeAge = -1;
		}
	}

	/** How far a nearby impact throws the view this frame (degrees of yaw and pitch), or zeros. */
	public static float[] shake(float partialTicks) {
		if (shakeAge < 0) {
			return new float[]{0, 0};
		}
		float t = shakeAge + partialTicks;
		float k = shake * 1.4f * (1f - t / SHAKE);
		return new float[]{k * (Mth.sin(t * 3.1f) * 1.1f + Mth.sin(t * 7.3f) * 0.4f), k * (Mth.cos(t * 3.7f) * 1.2f + Mth.sin(t * 8.9f) * 0.3f)};
	}
}
