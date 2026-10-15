package net.thesmallthings.hellcraft.entity.monster;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import net.thesmallthings.hellcraft.entity.boss.gen.HarpyAnim;

/**
 * The harpies (the Wood of the Suicides' monster, tools/art/harpy.py): called by the trees' scream when someone
 * blunders through the wood (LowerRules). They wheel overhead, then stoop and rake with their talons, which leaves you
 * slowed for a moment. With no one left to hunt they fly off back to their roosts.
 */
public class Harpy extends SoulMonster {
	private static final int STOOP_EVERY = 60;
	/** Ticks without prey before it flies away. */
	private static final int GIVE_UP = 600;
	private int nextStoop = 30;
	private int stooping;
	private int idleTicks;

	public Harpy(EntityType<? extends Monster> type, Level level) {
		super(type, level);
		setNoGravity(true);
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 18.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FLYING_SPEED, 0.6)
				.add(Attributes.ATTACK_DAMAGE, 4.0)
				.add(Attributes.FOLLOW_RANGE, 40.0);
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
			// back up to the roosts, and away
			setDeltaMovement(v.x * 0.9, 0.12, v.z * 0.9);
			if (++idleTicks > GIVE_UP) {
				discard();
			}
			return;
		}
		idleTicks = 0;
		lookAt(target, 30, 30);
		if (stooping > 0) {
			stooping--;
			Vec3 to = target.getEyePosition().subtract(position()).normalize().scale(0.85);
			setDeltaMovement(to);
			if (distanceTo(target) < 1.8) {
				doHurtTarget(level, target);
				stooping = 0;
				nextStoop = tickCount + STOOP_EVERY;
				setDeltaMovement(to.scale(-0.4).add(0, 0.7, 0));
			}
			return;
		}
		if (tickCount >= nextStoop) {
			stooping = 30;
			playSound(SoundEvents.PHANTOM_SWOOP, 1.5f, 1.2f);
			return;
		}
		// wheeling over the prey, lower than the lovers: under the dead branches
		double a = tickCount * 0.07 + getId();
		Vec3 want = target.position().add(Math.cos(a) * 4.5, 3.5, Math.sin(a) * 4.5);
		setDeltaMovement(want.subtract(position()).scale(0.07).add(v.scale(0.6)));
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit && target instanceof LivingEntity living) {
			// torn by the talons
			living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1), this);
		}
		return hit;
	}

	/** Comes out of the trees with a lament (when the trees scream). */
	public void shriek() {
		playSpecial();
		playSound(SoundEvents.GHAST_SCREAM, 1.5f, 1.6f);
	}

	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return true;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.PHANTOM_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.PHANTOM_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.PHANTOM_DEATH;
	}

	@Override
	protected int deathTicks() {
		return HarpyAnim.DEATH.ticks + 4;
	}
}
