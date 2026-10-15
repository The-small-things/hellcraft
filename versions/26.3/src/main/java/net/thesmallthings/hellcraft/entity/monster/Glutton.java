package net.thesmallthings.hellcraft.entity.monster;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.thesmallthings.hellcraft.entity.boss.gen.GluttonAnim;
import net.thesmallthings.hellcraft.util.Feedback;

/**
 * The glutton (Gluttony's monster, tools/art/glutton.py): a swollen soul that lies sunk in the mud until someone comes
 * near, then heaves itself up and lumbers after them. Its belly slam leaves you starving and sick, and it bursts into
 * a cloud of filth when it dies (keep out of it).
 */
public class Glutton extends SoulMonster {
	private static final EntityDataAccessor<Boolean> SUNK = SynchedEntityData.defineId(Glutton.class, EntityDataSerializers.BOOLEAN);
	/** How close someone must come before it rises. */
	private static final double WAKE = 7.0;
	private int risingUntil;

	public Glutton(EntityType<? extends Monster> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 30.0)
				.add(Attributes.MOVEMENT_SPEED, 0.18)
				.add(Attributes.ATTACK_DAMAGE, 5.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
				.add(Attributes.FOLLOW_RANGE, 20.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(SUNK, false);
	}

	/** Sunk in the mud, waiting (it doesn't move, and the client draws only a hump of mud). */
	public boolean sunk() {
		return entityData.get(SUNK);
	}

	public void sink() {
		entityData.set(SUNK, true);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, false));
		goalSelector.addGoal(6, new RandomStrollGoal(this, 0.7));
		goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0f));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		if (sunk()) {
			getNavigation().stop();
			setTarget(null);
			Player near = level.getNearestPlayer(this, WAKE);
			if (near != null && !near.isSpectator() && !near.isCreative()) {
				rise(level);
			}
			return;
		}
		if (tickCount < risingUntil) {
			getNavigation().stop();
			return;
		}
		super.customServerAiStep(level);
	}

	private void rise(ServerLevel level) {
		entityData.set(SUNK, false);
		risingUntil = tickCount + GluttonAnim.EMERGE.ticks;
		playSpecial();
		playSound(SoundEvents.MUD_BREAK, 1.5f, 0.5f);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MUD.defaultBlockState()), getX(), getY() + 0.5, getZ(), 60, 0.8, 0.4, 0.8, 0.1);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (sunk()) {
			rise(level);
		}
		return super.hurtServer(level, source, amount);
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit && target instanceof LivingEntity victim) {
			victim.addEffect(new MobEffectInstance(MobEffects.HUNGER, 200, 1));
			victim.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0));
			victim.push(getLookAngle().x * 0.8, 0.3, getLookAngle().z * 0.8);
			Feedback.syncMotion(victim);
		}
		return hit;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel level) {
			// it bursts: a cloud of filth that sickens whoever stays in it
			AreaEffectCloud cloud = new AreaEffectCloud(level, getX(), getY(), getZ());
			cloud.setRadius(2.5f);
			cloud.setDuration(120);
			cloud.setRadiusPerTick(-0.01f);
			cloud.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 120, 0));
			cloud.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
			cloud.setCustomParticle(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MUD.defaultBlockState()));
			level.addFreshEntity(cloud);
			playSound(SoundEvents.SLIME_SQUISH, 1.5f, 0.4f);
		}
	}

	@Override
	public boolean isInvisible() {
		return sunk() || super.isInvisible();
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return sunk() ? null : SoundEvents.HOGLIN_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.HOGLIN_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.HOGLIN_DEATH;
	}

	@Override
	protected int deathTicks() {
		return GluttonAnim.DEATH.ticks + 4;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("sunk", sunk());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		entityData.set(SUNK, input.getBooleanOr("sunk", false));
	}
}
