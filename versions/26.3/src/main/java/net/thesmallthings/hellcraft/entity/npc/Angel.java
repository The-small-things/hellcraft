package net.thesmallthings.hellcraft.entity.npc;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.entity.boss.gen.AngelAnim;
import net.thesmallthings.hellcraft.entity.monster.SoulMonster;

/**
 * The angels of Paradiso (tools/art/angel.py). Most are the heavens' own host: they wheel round each sphere's great
 * island, and round the Celestial Rose they fly in and out "like a swarm of bees" (Paradiso XXXI), now and then lifting
 * their arms in song. They can't be hurt and pay you no mind (Heaven.host keeps them in the sky). The few that are the
 * Angelic Guard (Heaven.angels: Mars's trial, the Seraph's fight) come down to test you: they wheel over you, then
 * stoop and strike with a hand of light, and after a minute they go back up.
 */
public class Angel extends SoulMonster {
	/** How long a guard stays to test you (ticks). */
	private static final int GUARD_LIFE = 1200;
	private static final int STOOP_EVERY = 50;

	private boolean guard;
	/** The host's circle: its centre, radius and the height it rises and falls by, how fast it goes and where it starts. */
	private double cx;
	private double cy;
	private double cz;
	private double radius;
	private double rise;
	private double speed;
	private double phase;
	/** Round the Rose: the circle breathes in and out, so the angels fly into the flower and back. */
	private boolean swarm;
	private int nextStoop = 30;
	private int stooping;

	public Angel(EntityType<? extends Monster> type, Level level) {
		super(type, level);
		setNoGravity(true);
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 16.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FLYING_SPEED, 0.6)
				.add(Attributes.ATTACK_DAMAGE, 4.0)
				.add(Attributes.FOLLOW_RANGE, 40.0);
	}

	/** One of the host, flying round (cx, cy, cz) at this radius; swarm: in and out of the Rose. */
	public void circle(double cx, double cy, double cz, double radius, double rise, double speed, double phase, boolean swarm) {
		this.guard = false;
		this.cx = cx;
		this.cy = cy;
		this.cz = cz;
		this.radius = radius;
		this.rise = rise;
		this.speed = speed;
		this.phase = phase;
		this.swarm = swarm;
		Vec3 at = orbit(0);
		snapTo(at.x, at.y, at.z, 0, 0);
	}

	/** One of the Angelic Guard, come down to test whoever it targets. */
	public void guard() {
		this.guard = true;
	}

	public boolean isGuard() {
		return guard;
	}

	@Override
	protected void registerGoals() {
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
	}

	private Vec3 orbit(int t) {
		double a = phase + t * speed;
		double r = swarm ? radius * (0.45 + 0.55 * (0.5 + 0.5 * Math.sin(a * 2.3 + phase * 3))) : radius;
		return new Vec3(cx + Math.cos(a) * r, cy + Math.sin(a * 0.7 + phase) * rise, cz + Math.sin(a) * r);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (!guard) {
			host();
			return;
		}
		if (tickCount > GUARD_LIFE) {
			discard();
			return;
		}
		LivingEntity target = getTarget();
		Vec3 v = getDeltaMovement();
		if (target == null || !target.isAlive()) {
			setDeltaMovement(v.x * 0.9, 0.15, v.z * 0.9);
			return;
		}
		lookAt(target, 30, 30);
		if (stooping > 0) {
			stooping--;
			Vec3 to = target.getEyePosition().subtract(position()).normalize().scale(0.8);
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
			playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5f, 1.4f);
			return;
		}
		// wheeling over the one it tests
		double a = tickCount * 0.08 + getId();
		Vec3 want = target.position().add(Math.cos(a) * 4, 3.5, Math.sin(a) * 4);
		setDeltaMovement(want.subtract(position()).scale(0.07).add(v.scale(0.6)));
	}

	/** The host: round and round its circle, facing the way it flies, through the islands as if they weren't there. */
	private void host() {
		setTarget(null);
		noPhysics = true;
		Vec3 want = orbit(tickCount);
		Vec3 step = want.subtract(position());
		setDeltaMovement(step.scale(0.25));
		Vec3 ahead = orbit(tickCount + 4).subtract(want);
		if (ahead.horizontalDistanceSqr() > 1e-4) {
			float yaw = (float) Math.toDegrees(Math.atan2(-ahead.x, ahead.z));
			setYRot(yaw);
			yBodyRot = yaw;
			yHeadRot = yaw;
		}
		if (swarm && getRandom().nextInt(240) == 0) {
			playSpecial();
		}
	}

	/** The host can't be hurt (only /kill moves it); the Guard can. */
	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		return !guard && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || super.isInvulnerableTo(level, source);
	}

	@Override
	public boolean isPushable() {
		return guard;
	}

	@Override
	protected void doPush(Entity entity) {
		if (guard) {
			super.doPush(entity);
		}
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
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("guard", guard);
		output.putDouble("cx", cx);
		output.putDouble("cy", cy);
		output.putDouble("cz", cz);
		output.putDouble("radius", radius);
		output.putDouble("rise", rise);
		output.putDouble("speed", speed);
		output.putDouble("phase", phase);
		output.putBoolean("swarm", swarm);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		guard = input.getBooleanOr("guard", false);
		cx = input.getDoubleOr("cx", getX());
		cy = input.getDoubleOr("cy", getY());
		cz = input.getDoubleOr("cz", getZ());
		radius = input.getDoubleOr("radius", 20);
		rise = input.getDoubleOr("rise", 4);
		speed = input.getDoubleOr("speed", 0.01);
		phase = input.getDoubleOr("phase", 0);
		swarm = input.getBooleanOr("swarm", false);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.AMETHYST_BLOCK_CHIME;
	}

	@Override
	protected float getSoundVolume() {
		return guard ? 1.0f : 0.4f;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ALLAY_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ALLAY_DEATH;
	}

	@Override
	protected int deathTicks() {
		return AngelAnim.DEATH.ticks + 2;
	}
}
