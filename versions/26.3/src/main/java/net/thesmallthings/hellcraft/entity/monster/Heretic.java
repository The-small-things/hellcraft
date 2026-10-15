package net.thesmallthings.hellcraft.entity.monster;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.thesmallthings.hellcraft.entity.boss.gen.HereticAnim;

/**
 * The heretics (Heresy's monster, tools/art/heretic.py): souls burnt black in their open tombs. When a tomb blasts
 * near you one may climb out of it (LowerRules); it shambles after you, and its grip sets you alight. Fire can't hurt
 * it; water and a good blade can.
 */
public class Heretic extends SoulMonster {
	private int risingUntil;

	public Heretic(EntityType<? extends Monster> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 24.0)
				.add(Attributes.MOVEMENT_SPEED, 0.24)
				.add(Attributes.ATTACK_DAMAGE, 5.0)
				.add(Attributes.FOLLOW_RANGE, 24.0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
		goalSelector.addGoal(6, new RandomStrollGoal(this, 0.7));
		goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0f));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	/** Climbs out of its tomb (the special animation); it can't act until it's out. */
	public void rise() {
		risingUntil = tickCount + HereticAnim.RISE.ticks;
		playSpecial();
		playSound(SoundEvents.BLAZE_AMBIENT, 1.2f, 0.5f);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		if (tickCount < risingUntil) {
			getNavigation().stop();
			if (tickCount % 4 == 0) {
				level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 0.5, getZ(), 4, 0.4, 0.4, 0.4, 0.02);
			}
			return;
		}
		super.customServerAiStep(level);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (level().isClientSide() && random.nextInt(4) == 0) {
			level().addParticle(ParticleTypes.FLAME, getRandomX(0.4), getY() + 1.9 + random.nextDouble() * 0.4, getRandomZ(0.4), 0, 0.03, 0);
		}
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit) {
			target.igniteForSeconds(4.0f);
		}
		return hit;
	}

	@Override
	public boolean isSensitiveToWater() {
		return true;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.HUSK_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.HUSK_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.BLAZE_DEATH;
	}

	@Override
	protected int deathTicks() {
		return HereticAnim.DEATH.ticks + 4;
	}
}
