package net.thesmallthings.hellcraft.entity.monster;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * One of Hell's own monsters with a real, keyframe-animated body (tools/art): it idles, walks by its speed, swings
 * when it attacks, flinches when hurt, plays its own death, and has one special animation of its own (a glutton
 * heaving itself out of the mud, a wrathful soul beating its chest). The client model is client/render/monster.
 */
public abstract class SoulMonster extends Monster {
	/** The entity event that starts the special animation (vanilla's own events stop well below this). */
	private static final byte SPECIAL = 100;

	public final AnimationState idle = new AnimationState();
	public final AnimationState attack = new AnimationState();
	public final AnimationState hurt = new AnimationState();
	public final AnimationState death = new AnimationState();
	public final AnimationState special = new AnimationState();

	protected SoulMonster(EntityType<? extends Monster> type, Level level) {
		super(type, level);
	}

	/** How long its death animation runs before the body goes. */
	protected abstract int deathTicks();

	/** Plays the special animation on every client watching. */
	public void playSpecial() {
		level().broadcastEntityEvent(this, SPECIAL);
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		level.broadcastEntityEvent(this, EntityEvent.START_ATTACKING);
		return super.doHurtTarget(level, target);
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == EntityEvent.START_ATTACKING) {
			attack.start(tickCount);
		} else if (id == SPECIAL) {
			special.start(tickCount);
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	public void handleDamageEvent(DamageSource source) {
		super.handleDamageEvent(source);
		hurt.start(tickCount);
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			idle.animateWhen(isAlive() && walkAnimation.speed() < 0.1f, tickCount);
			if (deathTime > 0) {
				death.startIfStopped(tickCount);
			}
		}
	}

	@Override
	protected void tickDeath() {
		deathTime++;
		if (deathTime >= deathTicks() && !level().isClientSide() && !isRemoved()) {
			level().broadcastEntityEvent(this, EntityEvent.POOF);
			remove(RemovalReason.KILLED);
		}
	}
}
