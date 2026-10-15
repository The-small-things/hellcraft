package net.thesmallthings.hellcraft.entity.boss;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.thesmallthings.hellcraft.entity.boss.gen.PlutusAnim;

/**
 * Plutus, the Great Enemy: the body of his fight (PlutusFight), modelled in tools/art/plutus.py. He waddles after his
 * prey between blows (his walk plays from his speed); the fight tells him what to play.
 */
public class PlutusEntity extends BossEntity {
	/** One state per animation (client side), in PlutusAnim's order. */
	public final AnimationState[] states = new AnimationState[PlutusAnim.values().length];
	private final int[] startedAt = new int[PlutusAnim.values().length];

	public PlutusEntity(EntityType<? extends Monster> type, Level level) {
		super(type, level);
		for (int i = 0; i < states.length; i++) {
			states[i] = new AnimationState();
		}
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 300.0)
				.add(Attributes.ARMOR, 10.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.MOVEMENT_SPEED, 0.22)
				.add(Attributes.STEP_HEIGHT, 1.5)
				.add(Attributes.FOLLOW_RANGE, 48.0);
	}

	public void play(PlutusAnim anim) {
		playAnimation(anim.ordinal());
	}

	@Override
	protected void registerGoals() {
		// none: the fight moves him
	}

	@Override
	protected void onAnimation(int id) {
		for (PlutusAnim other : PlutusAnim.values()) {
			if (other != PlutusAnim.IDLE && other != PlutusAnim.HURT && other != PlutusAnim.WALK) {
				states[other.ordinal()].stop();
			}
		}
		if (id != PlutusAnim.IDLE.ordinal()) {
			states[id].start(tickCount);
			startedAt[id] = tickCount;
		}
	}

	@Override
	public void handleDamageEvent(DamageSource source) {
		super.handleDamageEvent(source);
		states[PlutusAnim.HURT.ordinal()].start(tickCount);
		startedAt[PlutusAnim.HURT.ordinal()] = tickCount;
	}

	/** Whether he is playing something of his own (a blow, his fury): then he doesn't walk or idle under it. */
	public boolean acting() {
		for (PlutusAnim anim : PlutusAnim.values()) {
			if (anim != PlutusAnim.IDLE && anim != PlutusAnim.HURT && anim != PlutusAnim.WALK && states[anim.ordinal()].isStarted()) {
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
		boolean dying = states[PlutusAnim.DEATH.ordinal()].isStarted();
		states[PlutusAnim.IDLE.ordinal()].animateWhen(!dying && walkAnimation.speed() < 0.1f, tickCount);
		for (PlutusAnim anim : PlutusAnim.values()) {
			AnimationState state = states[anim.ordinal()];
			if (!anim.looping && anim != PlutusAnim.DEATH && state.isStarted() && tickCount - startedAt[anim.ordinal()] > anim.ticks) {
				state.stop();
			}
		}
	}

	@Override
	protected int deathTicks() {
		return PlutusAnim.DEATH.ticks + 10;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!level().isClientSide()) {
			play(PlutusAnim.DEATH);
		}
	}
}
