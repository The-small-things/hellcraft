package net.thesmallthings.hellcraft.entity.boss;

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
}
