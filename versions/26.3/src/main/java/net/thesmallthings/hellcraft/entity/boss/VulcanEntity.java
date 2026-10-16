package net.thesmallthings.hellcraft.entity.boss;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.thesmallthings.hellcraft.entity.boss.gen.VulcanAnim;

/**
 * Vulcan, smith of the gods: the body of his fight at the Great Forge of Dis (VulcanFight), modelled in
 * tools/art/vulcan.py. He limps after his prey between blows; the fight moves him.
 */
public class VulcanEntity extends AnimatedBoss {
	public VulcanEntity(EntityType<? extends Monster> type, Level level) {
		super(type, level, VulcanAnim.values());
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 600.0)
				.add(Attributes.ARMOR, 12.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.MOVEMENT_SPEED, 0.24)
				.add(Attributes.STEP_HEIGHT, 1.5)
				.add(Attributes.FOLLOW_RANGE, 48.0);
	}

	@Override
	protected void registerGoals() {
		// none: the fight moves him
	}
}
