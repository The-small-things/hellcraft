package net.thesmallthings.hellcraft.entity.boss;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.thesmallthings.hellcraft.entity.boss.gen.CerberusAnim;

/**
 * Cerberus, the Great Worm: the body of his fight (CerberusFight), modelled in tools/art/cerberus.py. Unlike Minos he
 * hunts: the fight walks him after his prey between blows (his walk plays from his speed) and tells him what to play.
 */
public class CerberusEntity extends BossEntity {
	/** One state per animation (client side), in CerberusAnim's order. */
	public final AnimationState[] states = new AnimationState[CerberusAnim.values().length];
	private final int[] startedAt = new int[CerberusAnim.values().length];

	public CerberusEntity(EntityType<? extends Monster> type, Level level) {
		super(type, level);
		for (int i = 0; i < states.length; i++) {
			states[i] = new AnimationState();
		}
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 350.0)
				.add(Attributes.ARMOR, 8.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.STEP_HEIGHT, 1.5)
				.add(Attributes.FOLLOW_RANGE, 48.0);
	}

	public void play(CerberusAnim anim) {
		playAnimation(anim.ordinal());
	}

	@Override
	protected void registerGoals() {
		// none: the fight moves him
	}

	@Override
	protected void onAnimation(int id) {
		for (CerberusAnim other : CerberusAnim.values()) {
			if (other != CerberusAnim.IDLE && other != CerberusAnim.HURT && other != CerberusAnim.WALK) {
				states[other.ordinal()].stop();
			}
		}
		if (id != CerberusAnim.IDLE.ordinal()) {
			states[id].start(tickCount);
			startedAt[id] = tickCount;
		}
	}

	@Override
	public void handleDamageEvent(DamageSource source) {
		super.handleDamageEvent(source);
		states[CerberusAnim.HURT.ordinal()].start(tickCount);
		startedAt[CerberusAnim.HURT.ordinal()] = tickCount;
	}

	/** Whether he is playing something of his own (a blow, his fury): then he doesn't walk or idle under it. */
	public boolean acting() {
		for (CerberusAnim anim : CerberusAnim.values()) {
			if (anim != CerberusAnim.IDLE && anim != CerberusAnim.HURT && anim != CerberusAnim.WALK && states[anim.ordinal()].isStarted()) {
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
		boolean dying = states[CerberusAnim.DEATH.ordinal()].isStarted();
		states[CerberusAnim.IDLE.ordinal()].animateWhen(!dying && walkAnimation.speed() < 0.1f, tickCount);
		for (CerberusAnim anim : CerberusAnim.values()) {
			AnimationState state = states[anim.ordinal()];
			if (!anim.looping && anim != CerberusAnim.DEATH && state.isStarted() && tickCount - startedAt[anim.ordinal()] > anim.ticks) {
				state.stop();
			}
		}
	}

	@Override
	protected int deathTicks() {
		return CerberusAnim.DEATH.ticks + 10;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!level().isClientSide()) {
			play(CerberusAnim.DEATH);
		}
	}
}
