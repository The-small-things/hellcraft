package net.thesmallthings.hellcraft.entity.boss;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.thesmallthings.hellcraft.entity.boss.gen.MinosAnim;

/**
 * Minos, Judge of the Damned: the body of his fight (MinosFight), modelled in tools/art/minos.py. He never leaves his
 * coils; the fight turns him toward his targets and tells him what to play.
 */
public class MinosEntity extends BossEntity {
	/** One state per animation (client side), in MinosAnim's order. */
	public final AnimationState[] states = new AnimationState[MinosAnim.values().length];
	private final int[] startedAt = new int[MinosAnim.values().length];

	public MinosEntity(EntityType<? extends Monster> type, Level level) {
		super(type, level);
		for (int i = 0; i < states.length; i++) {
			states[i] = new AnimationState();
		}
		setNoAi(true);
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 300.0)
				.add(Attributes.ARMOR, 10.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.MOVEMENT_SPEED, 0.0)
				.add(Attributes.FOLLOW_RANGE, 48.0);
	}

	public void play(MinosAnim anim) {
		playAnimation(anim.ordinal());
	}

	@Override
	protected void onAnimation(int id) {
		MinosAnim anim = MinosAnim.values()[id];
		// one at a time, over the idle sway
		for (MinosAnim other : MinosAnim.values()) {
			if (other != MinosAnim.IDLE && other != MinosAnim.HURT) {
				states[other.ordinal()].stop();
			}
		}
		states[id].start(tickCount);
		startedAt[id] = tickCount;
	}

	@Override
	public void handleDamageEvent(DamageSource source) {
		super.handleDamageEvent(source);
		// a flinch, over whatever he is doing
		states[MinosAnim.HURT.ordinal()].start(tickCount);
		startedAt[MinosAnim.HURT.ordinal()] = tickCount;
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide()) {
			return;
		}
		boolean dying = states[MinosAnim.DEATH.ordinal()].isStarted();
		states[MinosAnim.IDLE.ordinal()].animateWhen(!dying, tickCount);
		// a finished animation lets go, so the next starts from his resting pose (death holds its last frame)
		for (MinosAnim anim : MinosAnim.values()) {
			AnimationState state = states[anim.ordinal()];
			if (!anim.looping && anim != MinosAnim.DEATH && state.isStarted() && tickCount - startedAt[anim.ordinal()] > lengthOf(anim)) {
				state.stop();
			}
		}
	}

	@Override
	protected int deathTicks() {
		return MinosAnim.DEATH.ticks + 10;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!level().isClientSide()) {
			play(MinosAnim.DEATH);
		}
	}
}
