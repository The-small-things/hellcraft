package net.thesmallthings.hellcraft.hazard;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.blood.LayerEnchants;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.entity.monster.SoulMonster;
import net.thesmallthings.hellcraft.hazard.lucifer.LuciferManager;
import org.jetbrains.annotations.Nullable;

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

	/**
	 * The shield a player is still raising against a boss's or one of Hell's monsters' blow, or null: vanilla only
	 * counts a shield a quarter of a second after it goes up, so a block timed to a white flash came too late. Against
	 * them, a shield counts the moment it is raised (it must still face the blow).
	 */
	@Nullable
	public static ItemStack raisingShieldAgainst(LivingEntity victim, DamageSource source) {
		if (!(victim instanceof Player) || !victim.isUsingItem() || source.getEntity() == null) {
			return null;
		}
		Entity from = source.getEntity();
		if (!fromBoss(from) && !(from instanceof SoulMonster)) {
			return null;
		}
		ItemStack using = victim.getUseItem();
		return using.has(DataComponents.BLOCKS_ATTACKS) ? using : null;
	}

	/** Whether a player has a shield up (or going up) facing a point, as vanilla judges a block: within 90 degrees. */
	public static boolean shieldFacing(Player player, Vec3 from) {
		if (!player.isUsingItem() || !player.getUseItem().has(DataComponents.BLOCKS_ATTACKS)) {
			return false;
		}
		Vec3 to = from.subtract(player.position());
		to = new Vec3(to.x, 0, to.z);
		if (to.lengthSqr() < 1.0e-4) {
			return true;
		}
		Vec3 view = player.getViewVector(1.0f);
		return to.normalize().dot(new Vec3(view.x, 0, view.z).normalize()) > 0;
	}

	/** How much harder a boss hits this victim: 1 at 10 hearts, 4 at 40 (with bossHeartScaling 1). */
	public static float heartScale(LivingEntity victim) {
		if (!(victim instanceof Player)) {
			return 1.0f;
		}
		double scaling = HellConfig.get().bossHeartScaling;
		return (float) Math.max(1.0, 1.0 + scaling * (victim.getMaxHealth() / 20.0 - 1.0));
	}

	/** For Lucifer's blows that no mob deals (the frost, the wind): the heart scaling and his damage share. */
	public static float luciferScale(LivingEntity victim) {
		return heartScale(victim) * (float) HellConfig.get().luciferDamage * LayerEnchants.bossDamageTaken(victim);
	}

	public static float adjust(LivingEntity victim, DamageSource source, float amount) {
		Entity attacker = source.getEntity();
		if (victim instanceof Player && attacker != null && attacker != victim && fromBoss(attacker)) {
			float lucifer = attacker.entityTags().contains(LUCIFER_TAG) ? (float) HellConfig.get().luciferDamage : 1.0f;
			return amount * heartScale(victim) * lucifer * LayerEnchants.bossDamageTaken(victim);
		}
		if (attacker instanceof Player player && attacker == source.getDirectEntity() && fromBoss(victim)) {
			// Judgement: melee blows at a boss hit harder
			amount += LayerEnchants.bossDamageDealt(player);
		}
		if (attacker instanceof Player player) {
			// the Fallen Seraph's guard (LuciferFight)
			amount = LuciferManager.bossDamageTaken(victim, player, amount);
		}
		if (attacker instanceof Player && victim.entityTags().contains(BODY_TAG) && source.is(DamageTypeTags.IS_PROJECTILE)) {
			return amount * (float) HellConfig.get().bossProjectileDamage;
		}
		return amount;
	}
}
