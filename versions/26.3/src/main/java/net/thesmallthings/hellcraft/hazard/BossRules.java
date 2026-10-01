package net.thesmallthings.hellcraft.hazard;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.thesmallthings.hellcraft.config.HellConfig;

/**
 * Rules every Hellcraft boss (the guardians and Lucifer) fights by, so that hearts and bows don't make
 * a fight trivial:
 * <ul>
 *     <li>A boss's hits grow with the victim's heart capacity: a hit takes the same share of your health
 *     at 40 hearts as at 10 (bossHeartScaling).</li>
 *     <li>Arrows and other projectiles do only part of their damage to a boss (bossProjectileDamage).</li>
 * </ul>
 * Applied to every hit from LivingEntityMixin, at the start of hurtServer.
 */
public final class BossRules {
	private BossRules() {
	}

	/** Carried by every boss body (not by the monsters they summon). */
	public static final String BODY_TAG = "hellcraft_boss";
	private static final String GUARDIAN_TAG = "hellcraft_guardian";
	private static final String LUCIFER_TAG = "hellcraft_lucifer";

	/** A boss, or one of the monsters it brought. */
	public static boolean fromBoss(Entity entity) {
		return entity.entityTags().contains(GUARDIAN_TAG) || entity.entityTags().contains(LUCIFER_TAG);
	}

	/** How much harder a boss hits this victim: 1 at 10 hearts, 4 at 40 (with bossHeartScaling 1). */
	public static float heartScale(LivingEntity victim) {
		if (!(victim instanceof Player)) {
			return 1.0f;
		}
		double scaling = HellConfig.get().bossHeartScaling;
		return (float) Math.max(1.0, 1.0 + scaling * (victim.getMaxHealth() / 20.0 - 1.0));
	}

	public static float adjust(LivingEntity victim, DamageSource source, float amount) {
		Entity attacker = source.getEntity();
		if (victim instanceof Player && attacker != null && attacker != victim && fromBoss(attacker)) {
			return amount * heartScale(victim);
		}
		if (attacker instanceof Player && victim.entityTags().contains(BODY_TAG) && source.is(DamageTypeTags.IS_PROJECTILE)) {
			return amount * (float) HellConfig.get().bossProjectileDamage;
		}
		return amount;
	}
}
