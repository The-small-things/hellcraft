package net.thesmallthings.hellcraft.entity.boss;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.thesmallthings.hellcraft.entity.boss.gen.SeraphAnim;

/**
 * The Seraph of the Primum Mobile, Paradiso's guardian and the last trial: the body of its fight (SeraphFight), modelled
 * in tools/art/seraph.py. It hovers over the crystal floor (its model floats; its feet are on the ground), drifting after
 * you between its blows; the fight moves it.
 */
public class SeraphEntity extends AnimatedBoss {
	public SeraphEntity(EntityType<? extends Monster> type, Level level) {
		super(type, level, SeraphAnim.values());
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 700.0)
				.add(Attributes.ARMOR, 10.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.MOVEMENT_SPEED, 0.26)
				.add(Attributes.STEP_HEIGHT, 1.5)
				.add(Attributes.FOLLOW_RANGE, 48.0);
	}

	@Override
	protected void registerGoals() {
		// none: the fight moves it
	}
}
