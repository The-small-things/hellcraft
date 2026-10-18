package net.thesmallthings.hellcraft.entity.monster;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.thesmallthings.hellcraft.hazard.BossRules;
import net.thesmallthings.hellcraft.util.Feedback;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The lovers (Lust's monster, tools/art/lovers.py): two souls turning round each other on the hurricane. They circle
 * over their prey, then dive through it and throw it about with the wind (a hard shove, up and away: mind the cliffs).
 * They fly, and never touch the ground, except stunned: block a dive with a shield and they tumble to the ground for a
 * few seconds, open to your blows. Only one pair dives at a time at the same soul; the others wait their turn.
 */
public class Lovers extends SoulMonster {
	/** Ticks between dives. */
	private static final int DIVE_EVERY = 70;
	/** How long a blocked pair lies stunned. */
	private static final int STUN = 60;
	/** Who is diving at whom: the target's id to the diver's id and when its turn ends. */
	private static final Map<UUID, int[]> TURN = new HashMap<>();
	private int nextDive = 40;
	private int diving;
	private int stunned;

	public Lovers(EntityType<? extends Monster> type, Level level) {
		super(type, level);
		setNoGravity(true);
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 12.0)
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
		if (stunned > 0) {
			// tumbling on the ground, dazed
			if (--stunned == 0) {
				setNoGravity(true);
				nextDive = tickCount + DIVE_EVERY;
			} else if (stunned % 10 == 0) {
				level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 1.6, getZ(), 6, 0.3, 0.2, 0.3, 0.05);
			}
			setDeltaMovement(v.x * 0.5, v.y, v.z * 0.5);
			return;
		}
		lookAt(target, 30, 30);
		if (diving > 0) {
			diving--;
			Vec3 to = target.getEyePosition().subtract(position()).normalize().scale(0.9);
			setDeltaMovement(to);
			if (distanceTo(target) < 1.8) {
				diving = 0;
				nextDive = tickCount + DIVE_EVERY;
				if (target instanceof Player player && BossRules.shieldFacing(player, position())) {
					stun(level, player);
				} else {
					doHurtTarget(level, target);
					setDeltaMovement(to.scale(-0.5).add(0, 0.6, 0));
				}
				TURN.remove(target.getUUID());
			}
			return;
		}
		if (tickCount >= nextDive && takeTurn(target)) {
			diving = 30;
			playSound(SoundEvents.PHANTOM_SWOOP, 1.5f, 0.8f);
			return;
		}
		// circling over the prey
		double a = tickCount * 0.06 + getId();
		Vec3 want = target.position().add(Math.cos(a) * 5, 4.5, Math.sin(a) * 5);
		setDeltaMovement(want.subtract(position()).scale(0.06).add(v.scale(0.6)));
	}

	/** Whether this pair may dive at the target now: one pair at a time, the next a second after the last. */
	private boolean takeTurn(LivingEntity target) {
		int[] turn = TURN.get(target.getUUID());
		if (turn != null && turn[0] != getId() && tickCount < turn[1] && level().getEntity(turn[0]) instanceof Lovers other && other.isAlive()) {
			return false;
		}
		// the turn lasts the dive (30 ticks) and a second more before the next pair may go
		TURN.put(target.getUUID(), new int[]{getId(), tickCount + 50});
		return true;
	}

	/** A dive met a raised shield: the pair is knocked out of the air and lies dazed. */
	private void stun(ServerLevel level, Player by) {
		stunned = STUN;
		setNoGravity(false);
		Vec3 back = position().subtract(by.position()).multiply(1, 0, 1);
		back = back.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : back.normalize();
		setDeltaMovement(back.x * 0.6, 0.1, back.z * 0.6);
		level.playSound(null, by.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 1.0f, 1.0f);
		level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 1, getZ(), 20, 0.4, 0.4, 0.4, 0.2);
		by.sendOverlayMessage(Component.literal("Blocked! The lovers fall stunned: hit them now.").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
	}

	/** True while a blocked dive has left the pair dazed on the ground. */
	public boolean stunned() {
		return stunned > 0;
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
