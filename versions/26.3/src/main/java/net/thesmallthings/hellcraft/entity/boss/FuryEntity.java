package net.thesmallthings.hellcraft.entity.boss;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.thesmallthings.hellcraft.entity.boss.gen.FuryAnim;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * One of the three Furies (FuriesFight), modelled in tools/art/fury.py. The leader (Tisiphone) is the fight's body;
 * her sisters fly with her, and a blow to a sister wounds the leader, so the three share one life: the boss bar.
 */
public class FuryEntity extends AnimatedBoss {
	@Nullable
	private UUID leader;

	public FuryEntity(EntityType<? extends Monster> type, Level level) {
		super(type, level, FuryAnim.values());
		setNoGravity(true);
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 420.0)
				.add(Attributes.ARMOR, 6.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.MOVEMENT_SPEED, 0.0)
				.add(Attributes.FLYING_SPEED, 0.0)
				.add(Attributes.FOLLOW_RANGE, 48.0);
	}

	/** Makes this Fury a sister of the leader: her wounds are the leader's. */
	public void sisterOf(LivingEntity leader) {
		this.leader = leader.getUUID();
	}

	public boolean sister() {
		return leader != null;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (leader == null) {
			return super.hurtServer(level, source, amount);
		}
		Entity l = level.getEntity(leader);
		if (l instanceof LivingEntity lead && lead.isAlive()) {
			// the flinch is hers, the wound the leader's
			level.broadcastDamageEvent(this, source);
			return lead.hurtServer(level, source, amount);
		}
		return false;
	}

	@Override
	protected void registerGoals() {
	}

	@Override
	public boolean shouldBeSaved() {
		// the sisters come and go with the fight
		return leader == null && super.shouldBeSaved();
	}
}
