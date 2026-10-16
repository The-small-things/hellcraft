package net.thesmallthings.hellcraft.entity.boss;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.thesmallthings.hellcraft.entity.boss.gen.FallenSeraphAnim;

/**
 * Lucifer as he first shows himself, the Fallen Seraph (hazard/lucifer/SeraphDuel), modelled in
 * tools/art/fallen_seraph.py: about five blocks tall, he glides over the ice instead of walking (the fight moves him),
 * and his seraph form breaks at the end of the duel (DEATH, played by the fight, not by dying).
 */
public class FallenSeraphEntity extends AnimatedBoss {
	/** He is drawn this many times his Blockbench size. */
	public static final float SCALE = 1.6f;

	public FallenSeraphEntity(EntityType<? extends Monster> type, Level level) {
		super(type, level, FallenSeraphAnim.values());
		setNoAi(true);
		setNoGravity(true);
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 500.0)
				.add(Attributes.ARMOR, 10.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.MOVEMENT_SPEED, 0.0)
				.add(Attributes.FOLLOW_RANGE, 64.0);
	}

	@Override
	protected void registerGoals() {
		// none: the fight moves him
	}
}
