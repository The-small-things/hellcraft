package net.thesmallthings.hellcraft.hazard;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.hazard.guardian.GuardianManager;
import net.thesmallthings.hellcraft.hazard.lucifer.LuciferManager;
import net.thesmallthings.hellcraft.world.Circle;
import net.thesmallthings.hellcraft.world.HellWorldgen;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.Zone;

/** The deeper you go, the worse the damned: each circle toughens and twists its monsters. */
public final class MobEmpowerment {
	private MobEmpowerment() {
	}

	private static final String TOUCHED = "hellcraft_touched";

	public static void onLoad(Entity entity, ServerLevel level) {
		if (!(entity instanceof Monster monster) || entity.entityTags().contains(TOUCHED) || LuciferManager.isLucifer(entity)
				|| GuardianManager.isGuardian(entity)
				|| !HellWorldgen.isInferno(level)) {
			return;
		}
		monster.addTag(TOUCHED);
		Zone zone = InfernoGeometry.zoneAt(monster.getX(), monster.getZ());
		Circle circle = zone.circle();

		double bonus = circle.depth() * HellConfig.get().mobHealthPerDepth;
		AttributeInstance health = monster.getAttribute(Attributes.MAX_HEALTH);
		if (health != null && bonus > 0) {
			health.addPermanentModifier(new AttributeModifier(HellcraftMod.id("depth"), bonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			monster.setHealth(monster.getMaxHealth());
		}
		if (circle == Circle.WRATH && monster.getType() == EntityTypes.DROWNED) {
			// the wrathful, fighting beneath the Styx
			monster.addEffect(new MobEffectInstance(MobEffects.STRENGTH, MobEffectInstance.INFINITE_DURATION, 0, false, false));
		}
		if (circle == Circle.FRAUD && monster.getType() != EntityTypes.CREEPER && level.getRandom().nextFloat() < 0.2f) {
			// the frauds hide, but their swirl of particles gives them away
			monster.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, MobEffectInstance.INFINITE_DURATION, 0, false, true));
		}
	}
}
