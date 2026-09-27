package net.thesmallthings.hellcraft.hazard.lucifer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.util.Feedback;

import java.util.ArrayList;
import java.util.List;

/**
 * Lucifer's scripted attacks. Every one is telegraphed (particles and a sound first, the hit a moment
 * later) so a quick player can read and dodge it.
 */
final class LuciferAttacks {
	private LuciferAttacks() {
	}

	enum Attack {SLASH, FANGS, WINGS, HELLFIRE}

	static void perform(LuciferFight fight, Attack attack, Mob avatar, LivingEntity target) {
		switch (attack) {
			case SLASH -> slash(fight, avatar, target);
			case FANGS -> fangs(fight, avatar, target);
			case WINGS -> wings(fight, avatar);
			case HELLFIRE -> hellfire(fight, avatar);
		}
	}

	/** Portal particles gather behind the target... then he is there. */
	private static void slash(LuciferFight fight, Mob avatar, LivingEntity target) {
		ServerLevel level = fight.level();
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.SLASH));
		Vec3 facing = target.getLookAngle().multiply(1, 0, 1);
		if (facing.lengthSqr() < 1.0e-4) {
			facing = new Vec3(1, 0, 0);
		}
		Vec3 spot = target.position().subtract(facing.normalize().scale(2.5));
		double spotY = floorAt(level, spot.x, target.getY(), spot.z);
		level.playSound(null, spot.x, spotY, spot.z, SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.HOSTILE, 1.5f, 0.6f);
		for (int t = 0; t < 16; t += 2) {
			fight.schedule(t, () -> level.sendParticles(ParticleTypes.REVERSE_PORTAL, spot.x, spotY + 1.5, spot.z, 25, 0.4, 1.2, 0.4, 0.02));
		}
		fight.schedule(16, () -> {
			if (!avatar.isAlive()) {
				return;
			}
			level.sendParticles(ParticleTypes.PORTAL, avatar.getX(), avatar.getY() + 2, avatar.getZ(), 60, 0.6, 1.5, 0.6, 0.5);
			avatar.teleportTo(spot.x, spotY, spot.z);
			avatar.getLookControl().setLookAt(target);
			level.playSound(null, spot.x, spotY, spot.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 2.0f, 0.5f);
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, spot.x, spotY + 2, spot.z, 3, 0.8, 0.5, 0.8, 0.0);
			avatar.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			if (target.isAlive() && avatar.distanceTo(target) < 4.5) {
				target.hurt(level.damageSources().mobAttack(avatar), (fight.enraged() ? 12.0f : 9.0f) * fight.damageMultiplier());
				target.knockback(1.4, avatar.getX() - target.getX(), avatar.getZ() - target.getZ(), level.damageSources().mobAttack(avatar), 0.0f);
				Feedback.syncMotion(target);
			}
		});
	}

	/** Three lines of judgement race across the ice toward the target. */
	private static void fangs(LuciferFight fight, Mob avatar, LivingEntity target) {
		ServerLevel level = fight.level();
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.FANGS));
		level.playSound(null, avatar.blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 2.0f, 0.6f);
		avatar.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		double base = Math.atan2(target.getZ() - avatar.getZ(), target.getX() - avatar.getX());
		int lines = (fight.enraged() ? 5 : 3) + fight.extraLines();
		for (int line = 0; line < lines; line++) {
			double angle = base + (line - (lines - 1) / 2.0) * 0.4;
			for (int i = 1; i <= 18; i++) {
				double d = 1.3 * i;
				double x = avatar.getX() + Math.cos(angle) * d;
				double z = avatar.getZ() + Math.sin(angle) * d;
				double y = floorAt(level, x, avatar.getY(), z);
				level.addFreshEntity(new EvokerFangs(level, x, y, z, (float) angle, 6 + i, avatar));
			}
		}
	}

	/** He spreads the wings whose beating froze Cocytus: everyone near is blasted away and frozen. */
	private static void wings(LuciferFight fight, Mob avatar) {
		ServerLevel level = fight.level();
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.WINGS));
		level.playSound(null, avatar.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 3.0f, 0.5f);
		for (int t = 0; t < 20; t += 2) {
			int step = t;
			fight.schedule(t, () -> {
				double r = 6.0 - step * 0.25;
				for (int k = 0; k < 12; k++) {
					double a = k * Math.PI / 6 + step * 0.3;
					level.sendParticles(ParticleTypes.SNOWFLAKE, avatar.getX() + Math.cos(a) * r, avatar.getY() + 2.5, avatar.getZ() + Math.sin(a) * r,
							2, 0.1, 0.4, 0.1, 0.0);
				}
			});
		}
		fight.schedule(20, () -> {
			if (!avatar.isAlive()) {
				return;
			}
			level.playSound(null, avatar.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 4.0f, 0.3f);
			level.playSound(null, avatar.blockPosition(), SoundEvents.POWDER_SNOW_BREAK, SoundSource.HOSTILE, 4.0f, 0.5f);
			level.sendParticles(ParticleTypes.SNOWFLAKE, avatar.getX(), avatar.getY() + 2, avatar.getZ(), 400, 8, 1.5, 8, 0.3);
			level.sendParticles(ParticleTypes.CLOUD, avatar.getX(), avatar.getY() + 1, avatar.getZ(), 120, 6, 0.5, 6, 0.2);
			for (LivingEntity e : victims(fight, avatar.position(), 20.0)) {
				Vec3 away = e.position().subtract(avatar.position()).multiply(1, 0, 1);
				away = away.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : away.normalize();
				e.push(away.x * 2.2, 0.7, away.z * 2.2);
				Feedback.syncMotion(e);
				e.setTicksFrozen(Math.min(e.getTicksFrozen() + 200, e.getTicksRequiredToFreeze() + 200));
				e.hurt(level.damageSources().freeze(), (fight.enraged() ? 6.0f : 4.0f) * fight.damageMultiplier());
			}
		});
	}

	/** Pillars of hellfire fall wherever the flames mark the ice. */
	private static void hellfire(LuciferFight fight, Mob avatar) {
		ServerLevel level = fight.level();
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.HELLFIRE));
		level.playSound(null, avatar.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 2.0f, 0.5f);
		List<Vec3> marks = new ArrayList<>();
		int perTarget = (fight.enraged() ? 7 : 5) + fight.extraMarks();
		for (LivingEntity target : fight.targets()) {
			for (int k = 0; k < perTarget; k++) {
				double ox = k == 0 ? 0 : (level.getRandom().nextDouble() - 0.5) * 9;
				double oz = k == 0 ? 0 : (level.getRandom().nextDouble() - 0.5) * 9;
				double x = target.getX() + ox;
				double z = target.getZ() + oz;
				marks.add(new Vec3(x, floorAt(level, x, target.getY(), z), z));
			}
		}
		for (int t = 0; t < 30; t += 3) {
			fight.schedule(t, () -> {
				for (Vec3 m : marks) {
					level.sendParticles(ParticleTypes.FLAME, m.x, m.y + 0.1, m.z, 6, 0.5, 0.02, 0.5, 0.0);
					level.sendParticles(ParticleTypes.SMOKE, m.x, m.y + 0.1, m.z, 2, 0.3, 0.02, 0.3, 0.0);
				}
			});
		}
		fight.schedule(30, () -> {
			for (Vec3 m : marks) {
				level.explode(avatar, m.x, m.y + 0.5, m.z, 1.8f + 0.2f * fight.tier(), false, Level.ExplosionInteraction.NONE);
				level.sendParticles(ParticleTypes.LAVA, m.x, m.y + 0.5, m.z, 8, 0.3, 0.3, 0.3, 0.0);
				for (LivingEntity e : victims(fight, m, 2.5)) {
					e.igniteForSeconds(4.0f);
				}
			}
		});
	}

	/** Everything that can be hurt near a point, except Lucifer and his servants. */
	static List<LivingEntity> victims(LuciferFight fight, Vec3 center, double radius) {
		AABB box = new AABB(center, center).inflate(radius);
		return fight.level().getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive()
				&& !LuciferManager.isLucifer(e)
				&& !e.isSpectator()
				&& !(e instanceof net.minecraft.world.entity.player.Player p && p.isCreative())
				&& e.position().distanceToSqr(center) <= radius * radius);
	}

	/** Top of the first solid block at or below {@code y + 4}. */
	static double floorAt(ServerLevel level, double x, double y, double z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		for (int by = (int) Math.floor(y) + 4; by > (int) Math.floor(y) - 12; by--) {
			pos.set(bx, by, bz);
			if (level.getBlockState(pos).isSolid()) {
				return by + 1;
			}
		}
		return y;
	}
}
