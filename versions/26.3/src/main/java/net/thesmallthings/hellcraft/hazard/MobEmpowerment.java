package net.thesmallthings.hellcraft.hazard;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.hazard.lucifer.LuciferManager;
import net.thesmallthings.hellcraft.world.Circle;
import net.thesmallthings.hellcraft.world.HellWorldgen;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.Zone;

import java.util.ArrayDeque;
import java.util.Deque;

/** The deeper you go, the worse the damned: each circle toughens and twists its monsters. */
public final class MobEmpowerment {
	private MobEmpowerment() {
	}

	private static final String TOUCHED = "hellcraft_touched";
	private static final Deque<Skeleton> CENTAURS = new ArrayDeque<>();

	public static void onLoad(Entity entity, ServerLevel level) {
		if (!(entity instanceof Monster monster) || entity.entityTags().contains(TOUCHED) || LuciferManager.isLucifer(entity)
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
		if (circle == Circle.WRATH) {
			monster.addEffect(new MobEffectInstance(MobEffects.STRENGTH, MobEffectInstance.INFINITE_DURATION, 0, false, false));
		}
		if (circle == Circle.FRAUD && level.getRandom().nextFloat() < 0.25f) {
			monster.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, MobEffectInstance.INFINITE_DURATION, 0, false, false));
		}
		if (zone == Zone.PHLEGETHON && monster instanceof Skeleton skeleton && !skeleton.isPassenger() && level.getRandom().nextFloat() < 0.4f) {
			// centaurs patrol the river of blood; mount them next tick, not while the chunk is loading
			CENTAURS.add(skeleton);
		}
	}

	public static void tick() {
		while (!CENTAURS.isEmpty()) {
			Skeleton skeleton = CENTAURS.poll();
			if (!skeleton.isAlive() || skeleton.isPassenger() || !(skeleton.level() instanceof ServerLevel level)) {
				continue;
			}
			SkeletonHorse horse = EntityTypes.SKELETON_HORSE.create(level, EntitySpawnReason.JOCKEY);
			if (horse == null) {
				continue;
			}
			horse.snapTo(skeleton.getX(), skeleton.getY(), skeleton.getZ(), skeleton.getYRot(), 0.0f);
			horse.setTamed(true);
			level.addFreshEntity(horse);
			skeleton.startRiding(horse, true, true);
		}
	}
}
