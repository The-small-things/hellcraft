package net.thesmallthings.hellcraft.entity.boss;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.thesmallthings.hellcraft.entity.boss.gen.FarinataAnim;

/**
 * Farinata degli Uberti: the body of his fight (FarinataFight), modelled in tools/art/farinata.py. He stands in his
 * burning tomb and never leaves it; the fight turns him toward whoever he scorns.
 */
public class FarinataEntity extends AnimatedBoss {
	public FarinataEntity(EntityType<? extends Monster> type, Level level) {
		super(type, level, FarinataAnim.values());
		setNoAi(true);
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 360.0)
				.add(Attributes.ARMOR, 12.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.MOVEMENT_SPEED, 0.0)
				.add(Attributes.FOLLOW_RANGE, 48.0);
	}
}
