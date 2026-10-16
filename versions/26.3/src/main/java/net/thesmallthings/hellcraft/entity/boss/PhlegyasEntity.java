package net.thesmallthings.hellcraft.entity.boss;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.thesmallthings.hellcraft.entity.boss.gen.PhlegyasAnim;

/**
 * Phlegyas, the Ferryman of the Styx: the body of his fight (PhlegyasFight), modelled in tools/art/phlegyas.py. He
 * stands waist-deep in the pool at the heart of his landing; the fight turns him toward his targets and tells him
 * what to play.
 */
public class PhlegyasEntity extends BossEntity {
	/** One state per animation (client side), in PhlegyasAnim's order. */
	public final AnimationState[] states = new AnimationState[PhlegyasAnim.values().length];
	private final int[] startedAt = new int[PhlegyasAnim.values().length];

	public PhlegyasEntity(EntityType<? extends Monster> type, Level level) {
		super(type, level);
		for (int i = 0; i < states.length; i++) {
			states[i] = new AnimationState();
		}
		setNoAi(true);
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 320.0)
				.add(Attributes.ARMOR, 10.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.MOVEMENT_SPEED, 0.0)
				.add(Attributes.FOLLOW_RANGE, 48.0);
	}

	public void play(PhlegyasAnim anim) {
		playAnimation(anim.ordinal());
	}

	@Override
	protected void onAnimation(int id) {
		PhlegyasAnim anim = PhlegyasAnim.values()[id];
		// one at a time, over the idle sway
		for (PhlegyasAnim other : PhlegyasAnim.values()) {
			if (other != PhlegyasAnim.IDLE && other != PhlegyasAnim.HURT) {
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
		states[PhlegyasAnim.HURT.ordinal()].start(tickCount);
		startedAt[PhlegyasAnim.HURT.ordinal()] = tickCount;
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide()) {
			return;
		}
		boolean dying = states[PhlegyasAnim.DEATH.ordinal()].isStarted();
		states[PhlegyasAnim.IDLE.ordinal()].animateWhen(!dying, tickCount);
		// a finished animation lets go, so the next starts from his resting pose (death holds its last frame)
		for (PhlegyasAnim anim : PhlegyasAnim.values()) {
			AnimationState state = states[anim.ordinal()];
			if (!anim.looping && anim != PhlegyasAnim.DEATH && state.isStarted() && tickCount - startedAt[anim.ordinal()] > lengthOf(anim)) {
				state.stop();
			}
		}
	}

	@Override
	protected int deathTicks() {
		return PhlegyasAnim.DEATH.ticks + 10;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!level().isClientSide()) {
			play(PhlegyasAnim.DEATH);
		}
	}
}
