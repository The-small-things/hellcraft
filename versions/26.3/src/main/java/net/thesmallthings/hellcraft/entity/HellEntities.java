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
import net.thesmallthings.hellcraft.entity.boss.CerberusEntity;
import net.thesmallthings.hellcraft.entity.boss.MinosEntity;
import net.thesmallthings.hellcraft.entity.boss.PhlegyasEntity;
import net.thesmallthings.hellcraft.entity.boss.PlutusEntity;
import net.thesmallthings.hellcraft.entity.boss.RollingCoin;
import net.thesmallthings.hellcraft.entity.monster.Glutton;
import net.thesmallthings.hellcraft.entity.monster.Hoarder;
import net.thesmallthings.hellcraft.entity.monster.Lovers;
import net.thesmallthings.hellcraft.entity.monster.Wrathful;
import net.thesmallthings.hellcraft.entity.npc.Shade;

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

	/** Cerberus, the Great Worm, guardian of Gluttony (CerberusFight). */
	public static final EntityType<CerberusEntity> CERBERUS = register("cerberus",
			EntityType.Builder.of(CerberusEntity::new, MobCategory.MONSTER).sized(4.2f, 4.4f).eyeHeight(3.8f).fireImmune().clientTrackingRange(16));

	/** Plutus, the Great Enemy, guardian of Greed (PlutusFight). */
	public static final EntityType<PlutusEntity> PLUTUS = register("plutus",
			EntityType.Builder.of(PlutusEntity::new, MobCategory.MONSTER).sized(3.0f, 5.6f).eyeHeight(4.8f).fireImmune().clientTrackingRange(16));

	/** A great coin of the Mint, rolling (Plutus's fury). */
	public static final EntityType<RollingCoin> ROLLING_COIN = register("rolling_coin",
			EntityType.Builder.<RollingCoin>of(RollingCoin::new, MobCategory.MISC).sized(1.0f, 5.0f).clientTrackingRange(10).updateInterval(1));

	/** Phlegyas, the Ferryman of the Styx, guardian of Wrath (PhlegyasFight). */
	public static final EntityType<PhlegyasEntity> PHLEGYAS = register("phlegyas",
			EntityType.Builder.of(PhlegyasEntity::new, MobCategory.MONSTER).sized(3.0f, 7.4f).eyeHeight(6.6f).fireImmune().clientTrackingRange(16));

	/** Phlegyas's ferry across the Styx (world/Ferries). */
	public static final EntityType<Ferry> FERRY = register("ferry",
			EntityType.Builder.<Ferry>of(Ferry::new, MobCategory.MISC).sized(3.0f, 1.0f).clientTrackingRange(10).updateInterval(1));

	/** Greed's hoarders, drawn to whoever carries gold (Hoarders). */
	public static final EntityType<Hoarder> HOARDER = register("hoarder",
			EntityType.Builder.of(Hoarder::new, MobCategory.MONSTER).sized(0.8f, 1.7f).clientTrackingRange(8));

	/** Limbo's shades, the great souls of the Noble Castle (Shades). */
	public static final EntityType<Shade> SHADE = register("shade",
			EntityType.Builder.of(Shade::new, MobCategory.CREATURE).sized(0.6f, 1.95f).clientTrackingRange(10));

	/** Lust's lovers, two souls blown together on the wind (LayerMonsters). */
	public static final EntityType<Lovers> LOVERS = register("lovers",
			EntityType.Builder.of(Lovers::new, MobCategory.MONSTER).sized(1.0f, 1.8f).clientTrackingRange(10));

	/** Gluttony's gluttons, sunk in the mud until someone comes near (LayerMonsters). */
	public static final EntityType<Glutton> GLUTTON = register("glutton",
			EntityType.Builder.of(Glutton::new, MobCategory.MONSTER).sized(1.3f, 1.7f).clientTrackingRange(8));

	/** Wrath's brawlers (LayerMonsters). */
	public static final EntityType<Wrathful> WRATHFUL = register("wrathful",
			EntityType.Builder.of(Wrathful::new, MobCategory.MONSTER).sized(0.7f, 1.9f).clientTrackingRange(8));

	public static void register() {
		FabricDefaultAttributeRegistry.register(TEST_RIG, TestRig.attributes());
		FabricDefaultAttributeRegistry.register(MINOS, MinosEntity.attributes());
		FabricDefaultAttributeRegistry.register(CERBERUS, CerberusEntity.attributes());
		FabricDefaultAttributeRegistry.register(PLUTUS, PlutusEntity.attributes());
		FabricDefaultAttributeRegistry.register(PHLEGYAS, PhlegyasEntity.attributes());
		FabricDefaultAttributeRegistry.register(HOARDER, Hoarder.attributes());
		FabricDefaultAttributeRegistry.register(LOVERS, Lovers.attributes());
		FabricDefaultAttributeRegistry.register(GLUTTON, Glutton.attributes());
		FabricDefaultAttributeRegistry.register(WRATHFUL, Wrathful.attributes());
		FabricDefaultAttributeRegistry.register(SHADE, Shade.attributes());
	}

	private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, HellcraftMod.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}
}
