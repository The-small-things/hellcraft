package net.thesmallthings.hellcraft.entity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.entity.boss.MinosEntity;

/** Hellcraft's own mobs. Each has a renderer and a model in the client sources (client/render). */
public final class HellEntities {
	private HellEntities() {
	}

	/** A test dummy for the animated models; see {@link TestRig}. */
	public static final EntityType<TestRig> TEST_RIG = register("test_rig",
			EntityType.Builder.of(TestRig::new, MobCategory.MISC).sized(0.6f, 1.95f).clientTrackingRange(10));

	/** Minos, Judge of the Damned: the first boss with a real body (MinosFight). */
	public static final EntityType<MinosEntity> MINOS = register("minos",
			EntityType.Builder.of(MinosEntity::new, MobCategory.MONSTER).sized(3.0f, 6.4f).eyeHeight(5.6f).fireImmune().clientTrackingRange(16));

	public static void register() {
		FabricDefaultAttributeRegistry.register(TEST_RIG, TestRig.attributes());
		FabricDefaultAttributeRegistry.register(MINOS, MinosEntity.attributes());
	}

	private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, HellcraftMod.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}
}
