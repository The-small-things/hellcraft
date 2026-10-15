package net.thesmallthings.hellcraft.entity.monster;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.entity.boss.gen.LoversAnim;
import net.thesmallthings.hellcraft.util.Feedback;

/**
 * The lovers (Lust's monster, tools/art/lovers.py): two souls turning round each other on the hurricane. They circle
 * over their prey, then dive through it and throw it about with the wind (a hard shove, up and away: mind the cliffs).
 * They fly, and never touch the ground.
 */
public class Lovers extends SoulMonster {
	/** Ticks between dives. */
	private static final int DIVE_EVERY = 70;
	private int nextDive = 40;
	private int diving;

	public Lovers(EntityType<? extends Monster> type, Level level) {
		super(type, level);
		setNoGravity(true);
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 16.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FLYING_SPEED, 0.6)
				.add(Attributes.ATTACK_DAMAGE, 3.0)
				.add(Attributes.FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void registerGoals() {
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		LivingEntity target = getTarget();
		Vec3 v = getDeltaMovement();
		if (target == null || !target.isAlive()) {
			// drifting on the wind
			setDeltaMovement(v.x * 0.9 + Math.cos(tickCount * 0.05) * 0.02, Math.sin(tickCount * 0.07) * 0.03, v.z * 0.9 + Math.sin(tickCount * 0.05) * 0.02);
			return;
		}
		lookAt(target, 30, 30);
		if (diving > 0) {
			diving--;
			Vec3 to = target.getEyePosition().subtract(position()).normalize().scale(0.9);
			setDeltaMovement(to);
			if (distanceTo(target) < 1.8) {
				doHurtTarget(level, target);
				diving = 0;
				nextDive = tickCount + DIVE_EVERY;
				setDeltaMovement(to.scale(-0.5).add(0, 0.6, 0));
			}
			return;
		}
		if (tickCount >= nextDive) {
			diving = 30;
			playSound(SoundEvents.PHANTOM_SWOOP, 1.5f, 0.8f);
			return;
		}
		// circling over the prey
		double a = tickCount * 0.06 + getId();
		Vec3 want = target.position().add(Math.cos(a) * 5, 4.5, Math.sin(a) * 5);
		setDeltaMovement(want.subtract(position()).scale(0.06).add(v.scale(0.6)));
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit) {
			// thrown about by the wind
			Vec3 push = target.position().subtract(position()).multiply(1, 0, 1);
			push = push.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : push.normalize();
			target.setDeltaMovement(push.x * 1.5, 0.7, push.z * 1.5);
			Feedback.syncMotion(target);
			playSound(SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.0f, 0.8f);
		}
		return hit;
	}

	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.VEX_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.VEX_DEATH;
	}

	@Override
	protected int deathTicks() {
		return LoversAnim.DEATH.ticks + 4;
	}
}
