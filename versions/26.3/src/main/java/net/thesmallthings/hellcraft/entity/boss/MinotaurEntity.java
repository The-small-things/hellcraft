package net.thesmallthings.hellcraft.entity.boss;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.thesmallthings.hellcraft.entity.boss.gen.MinotaurAnim;

/**
 * The Minotaur, the Infamy of Crete: the body of his fight (MinotaurFight), modelled in tools/art/minotaur.py. He hunts
 * like Cerberus: the fight walks him after his prey between blows and sends him charging.
 */
public class MinotaurEntity extends AnimatedBoss {
	public MinotaurEntity(EntityType<? extends Monster> type, Level level) {
		super(type, level, MinotaurAnim.values());
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 400.0)
				.add(Attributes.ARMOR, 10.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.STEP_HEIGHT, 1.5)
				.add(Attributes.FOLLOW_RANGE, 48.0);
	}

	@Override
	protected void registerGoals() {
		// none: the fight moves him
	}
}
