package net.thesmallthings.hellcraft.entity.boss;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.thesmallthings.hellcraft.entity.boss.gen.GeryonAnim;

/**
 * Geryon, the Image of Fraud: the body of his fight (GeryonFight), modelled in tools/art/geryon.py. He swims in the
 * air; the fight moves him. Beaten, he yields, and a tamed Geryon (GeryonCarrier) stays at the cliff to fly souls down.
 */
public class GeryonEntity extends AnimatedBoss {
	private static final String FALSE = "hellcraft_false_geryon";

	public GeryonEntity(EntityType<? extends Monster> type, Level level) {
		super(type, level, GeryonAnim.values());
		setNoGravity(true);
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 320.0)
				.add(Attributes.ARMOR, 8.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.MOVEMENT_SPEED, 0.0)
				.add(Attributes.FLYING_SPEED, 0.0)
				.add(Attributes.FOLLOW_RANGE, 48.0);
	}

	@Override
	protected void registerGoals() {
	}

	/** A false Geryon (his False Face): one blow and it pops; it's never saved. */
	public void makeFalse() {
		addTag(FALSE);
	}

	public boolean isFalse() {
		return entityTags().contains(FALSE);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (isFalse()) {
			level.sendParticles(ParticleTypes.WITCH, getX(), getY() + 1, getZ(), 30, 1.2, 0.8, 1.2, 0.05);
			level.playSound(null, blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 1.5f, 1.4f);
			discard();
			return false;
		}
		return super.hurtServer(level, source, amount);
	}

	@Override
	public boolean shouldBeSaved() {
		return !isFalse() && super.shouldBeSaved();
	}
}
