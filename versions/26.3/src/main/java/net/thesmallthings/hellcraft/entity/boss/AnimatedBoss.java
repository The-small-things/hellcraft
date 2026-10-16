package net.thesmallthings.hellcraft.entity.boss;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * A boss with a real body and generated animations (tools/art, tools/gen_bosses.py), done once for all of them: one
 * animation state per animation, the fight names what to play (one at a time, over its idle), it flinches when hurt,
 * walks by its speed if it has a WALK animation, and plays its own death. The client side is
 * client/render/boss/AnimatedBossRenderer. (Minos, Cerberus, Plutus and Phlegyas came first and have their own.)
 */
public abstract class AnimatedBoss extends BossEntity {
	private final BossAnim[] anims;
	/** One state per animation (client side), in the anim enum's order. */
	public final AnimationState[] states;
	private final int[] startedAt;
	private final int idle;
	private final int hurt;
	private final int death;
	private final int walk;

	protected AnimatedBoss(EntityType<? extends Monster> type, Level level, BossAnim[] anims) {
		super(type, level);
		this.anims = anims;
		this.states = new AnimationState[anims.length];
		this.startedAt = new int[anims.length];
		for (int i = 0; i < states.length; i++) {
			states[i] = new AnimationState();
		}
		this.idle = index("IDLE");
		this.hurt = index("HURT");
		this.death = index("DEATH");
		this.walk = index("WALK");
	}

	private int index(String name) {
		for (BossAnim a : anims) {
			if (a.name().equals(name)) {
				return a.ordinal();
			}
		}
		return -1;
	}

	public void play(BossAnim anim) {
		playAnimation(anim.ordinal());
	}

	/** How fast animation i plays (AnimatedBossRenderer). */
	public float speed(int i) {
		return speedOf(anims[i]);
	}

	/** Whether its hideable part is hidden now (the Emperor's eyes while he sleeps): AnimatedBossModel.hideable. */
	public boolean partHidden() {
		return false;
	}

	/** The WALK animation's index, or -1 if it has none (it never walks). */
	public int walkIndex() {
		return walk;
	}

	private boolean background(int i) {
		return i == idle || i == hurt || i == walk;
	}

	@Override
	protected void onAnimation(int id) {
		for (int i = 0; i < states.length; i++) {
			if (!background(i)) {
				states[i].stop();
			}
		}
		if (id != idle) {
			states[id].start(tickCount);
			startedAt[id] = tickCount;
		}
	}

	@Override
	public void handleDamageEvent(DamageSource source) {
		super.handleDamageEvent(source);
		if (hurt >= 0) {
			states[hurt].start(tickCount);
			startedAt[hurt] = tickCount;
		}
	}

	/** Whether it is playing something of its own (a blow, its fury): then it doesn't walk or idle under it. */
	public boolean acting() {
		for (int i = 0; i < states.length; i++) {
			if (!background(i) && states[i].isStarted()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide()) {
			return;
		}
		boolean dying = death >= 0 && states[death].isStarted();
		if (idle >= 0) {
			states[idle].animateWhen(!dying && (walk < 0 || walkAnimation.speed() < 0.1f), tickCount);
		}
		// a finished animation lets go, so the next starts from its resting pose (death holds its last frame)
		for (BossAnim a : anims) {
			AnimationState state = states[a.ordinal()];
			if (!a.looping() && a.ordinal() != death && state.isStarted() && tickCount - startedAt[a.ordinal()] > lengthOf(a)) {
				state.stop();
			}
		}
	}

	@Override
	protected int deathTicks() {
		return death < 0 ? 20 : anims[death].ticks() + 10;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!level().isClientSide() && death >= 0) {
			playAnimation(death);
		}
	}
}
