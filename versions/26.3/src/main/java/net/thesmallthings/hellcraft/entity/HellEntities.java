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
import net.thesmallthings.hellcraft.entity.boss.AntaeusEntity;
import net.thesmallthings.hellcraft.entity.boss.CerberusEntity;
import net.thesmallthings.hellcraft.entity.boss.CyclopsEntity;
import net.thesmallthings.hellcraft.entity.boss.EmperorEntity;
import net.thesmallthings.hellcraft.entity.boss.EmperorWeakPoint;
import net.thesmallthings.hellcraft.entity.boss.FallenSeraphEntity;
import net.thesmallthings.hellcraft.entity.boss.FarinataEntity;
import net.thesmallthings.hellcraft.entity.boss.FuryEntity;
import net.thesmallthings.hellcraft.entity.boss.GeryonCarrier;
import net.thesmallthings.hellcraft.entity.boss.GeryonEntity;
import net.thesmallthings.hellcraft.entity.boss.MedusaEntity;
import net.thesmallthings.hellcraft.entity.boss.MinosEntity;
import net.thesmallthings.hellcraft.entity.boss.MinotaurEntity;
import net.thesmallthings.hellcraft.entity.boss.PhlegyasEntity;
import net.thesmallthings.hellcraft.entity.boss.PlutusEntity;
import net.thesmallthings.hellcraft.entity.boss.RollingCoin;
import net.thesmallthings.hellcraft.entity.boss.SeraphEntity;
import net.thesmallthings.hellcraft.entity.boss.VulcanEntity;
import net.thesmallthings.hellcraft.entity.monster.Centaur;
import net.thesmallthings.hellcraft.entity.monster.Glutton;
import net.thesmallthings.hellcraft.entity.monster.Harpy;
import net.thesmallthings.hellcraft.entity.monster.Heretic;
import net.thesmallthings.hellcraft.entity.monster.Hoarder;
import net.thesmallthings.hellcraft.entity.monster.Lovers;
import net.thesmallthings.hellcraft.entity.monster.Malacoda;
import net.thesmallthings.hellcraft.entity.monster.Malebranche;
import net.thesmallthings.hellcraft.entity.monster.Mimic;
import net.thesmallthings.hellcraft.entity.monster.Thief;
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

	/** The harpies of the Wood of the Suicides, called by the trees' scream (LowerRules). */
	public static final EntityType<Harpy> HARPY = register("harpy",
			EntityType.Builder.of(Harpy::new, MobCategory.MONSTER).sized(0.9f, 1.6f).clientTrackingRange(10));

	/** The centaurs posted by the fords of the river of blood (LowerMonsters). */
	public static final EntityType<Centaur> CENTAUR = register("centaur",
			EntityType.Builder.of(Centaur::new, MobCategory.MONSTER).sized(1.4f, 2.4f).eyeHeight(2.1f).clientTrackingRange(10));

	/** The heretics, who climb out of their burning tombs (LowerRules). */
	public static final EntityType<Heretic> HERETIC = register("heretic",
			EntityType.Builder.of(Heretic::new, MobCategory.MONSTER).sized(0.6f, 1.95f).fireImmune().clientTrackingRange(8));

	/** Malebolge's mimics, chests that aren't (Frauds). */
	public static final EntityType<Mimic> MIMIC = register("mimic",
			EntityType.Builder.of(Mimic::new, MobCategory.MONSTER).sized(0.9f, 0.9f).clientTrackingRange(8));

	/** Malebolge's thieves, who snatch from your pack and run (Frauds). */
	public static final EntityType<Thief> THIEF = register("thief",
			EntityType.Builder.of(Thief::new, MobCategory.MONSTER).sized(0.6f, 1.8f).clientTrackingRange(8));

	/** The Malebranche, the devils with hooks who keep Malebolge's ditches of pitch (Frauds). */
	public static final EntityType<Malebranche> MALEBRANCHE = register("malebranche",
			EntityType.Builder.of(Malebranche::new, MobCategory.MONSTER).sized(0.8f, 2.0f).fireImmune().clientTrackingRange(10));

	/** Malacoda, their captain, who leads souls along the fifth ridge to a bridge that isn't there (Frauds). */
	public static final EntityType<Malacoda> MALACODA = register("malacoda",
			EntityType.Builder.of(Malacoda::new, MobCategory.MISC).sized(0.9f, 2.4f).fireImmune().clientTrackingRange(10));

	/** The Furies at the Walls of Dis (FuriesFight): the leader is the fight's body, her sisters fly with her. */
	public static final EntityType<FuryEntity> FURY = register("fury",
			EntityType.Builder.of(FuryEntity::new, MobCategory.MONSTER).sized(1.4f, 3.0f).eyeHeight(2.6f).fireImmune().clientTrackingRange(16));

	/** Medusa, called up over the battlements of Dis by the Furies (FuriesFight). */
	public static final EntityType<MedusaEntity> MEDUSA = register("medusa",
			EntityType.Builder.of(MedusaEntity::new, MobCategory.MONSTER).sized(1.5f, 5.5f).eyeHeight(2.8f).fireImmune().clientTrackingRange(16));

	/** Farinata, the Proud Heretic, in his burning tomb (FarinataFight). */
	public static final EntityType<FarinataEntity> FARINATA = register("farinata",
			EntityType.Builder.of(FarinataEntity::new, MobCategory.MONSTER).sized(1.6f, 3.8f).eyeHeight(3.3f).fireImmune().clientTrackingRange(16));

	/** Vulcan, smith of the gods, at the Great Forge of Dis in the Nether (VulcanFight). */
	public static final EntityType<VulcanEntity> VULCAN = register("vulcan",
			EntityType.Builder.of(VulcanEntity::new, MobCategory.MONSTER).sized(2.6f, 5.8f).eyeHeight(5.0f).fireImmune().clientTrackingRange(16));
	/** The Seraph of the Primum Mobile, Paradiso's guardian (SeraphFight). */
	public static final EntityType<SeraphEntity> SERAPH = register("seraph",
			EntityType.Builder.of(SeraphEntity::new, MobCategory.MONSTER).sized(2.4f, 6.4f).eyeHeight(5.4f).fireImmune().clientTrackingRange(16));
	/** A Cyclops of the quarries, a mini-boss at a camp in the Cyclops Quarries of the Forge of Dis (CyclopsCampFeature). */
	public static final EntityType<CyclopsEntity> CYCLOPS = register("cyclops",
			EntityType.Builder.of(CyclopsEntity::new, MobCategory.MONSTER).sized(2.0f, 4.4f).eyeHeight(3.8f).fireImmune().clientTrackingRange(12));
	/** The Minotaur, the Infamy of Crete, on the ruined slope down to the river of blood (MinotaurFight). */
	public static final EntityType<MinotaurEntity> MINOTAUR = register("minotaur",
			EntityType.Builder.of(MinotaurEntity::new, MobCategory.MONSTER).sized(2.4f, 5.6f).eyeHeight(4.9f).fireImmune().clientTrackingRange(16));

	/** Geryon, the Image of Fraud, at the brink of the great cliff (GeryonFight). */
	public static final EntityType<GeryonEntity> GERYON = register("geryon",
			EntityType.Builder.of(GeryonEntity::new, MobCategory.MONSTER).sized(3.0f, 3.0f).eyeHeight(2.4f).fireImmune().clientTrackingRange(16)
					.updateInterval(1));

	/** Geryon tamed, who flies souls down the cliff once he's beaten (GeryonCarrier). */
	public static final EntityType<GeryonCarrier> GERYON_CARRIER = register("geryon_carrier",
			EntityType.Builder.of(GeryonCarrier::new, MobCategory.MISC).sized(3.0f, 3.0f).eyeHeight(2.4f).fireImmune().clientTrackingRange(16)
					.updateInterval(1));

	/** Antaeus, the unchained giant who sets souls down on the ice of Cocytus (AntaeusCleft). */
	public static final EntityType<AntaeusEntity> ANTAEUS = register("antaeus",
			EntityType.Builder.of(AntaeusEntity::new, MobCategory.MISC).sized(6.0f, 28.0f).eyeHeight(25.0f).fireImmune().clientTrackingRange(24));

	/** Lucifer as he first shows himself, the Fallen Seraph (LuciferFight's duel). */
	public static final EntityType<FallenSeraphEntity> FALLEN_SERAPH = register("fallen_seraph",
			EntityType.Builder.of(FallenSeraphEntity::new, MobCategory.MONSTER).sized(1.4f, 5.0f).eyeHeight(4.3f).fireImmune().clientTrackingRange(16)
					.updateInterval(1));

	/** Lucifer's true form, the Emperor frozen to the chest in the ice of the pit (LuciferFight). */
	public static final EntityType<EmperorEntity> EMPEROR = register("emperor",
			EntityType.Builder.of(EmperorEntity::new, MobCategory.MISC).sized(8.0f, 14.0f).eyeHeight(20.0f).fireImmune().clientTrackingRange(32)
					.updateInterval(2));

	/** Where the Emperor lays himself open: a hand on the ice, a face down low (EmperorAttacks). */
	public static final EntityType<EmperorWeakPoint> EMPEROR_WEAK_POINT = register("emperor_weak_point",
			EntityType.Builder.<EmperorWeakPoint>of(EmperorWeakPoint::new, MobCategory.MISC).sized(7.0f, 7.0f).fireImmune().noSummon()
					.clientTrackingRange(16).updateInterval(5));

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
		FabricDefaultAttributeRegistry.register(HARPY, Harpy.attributes());
		FabricDefaultAttributeRegistry.register(CENTAUR, Centaur.attributes());
		FabricDefaultAttributeRegistry.register(HERETIC, Heretic.attributes());
		FabricDefaultAttributeRegistry.register(MIMIC, Mimic.attributes());
		FabricDefaultAttributeRegistry.register(THIEF, Thief.attributes());
		FabricDefaultAttributeRegistry.register(MALEBRANCHE, Malebranche.attributes());
		FabricDefaultAttributeRegistry.register(MALACODA, Malacoda.attributes());
		FabricDefaultAttributeRegistry.register(FURY, FuryEntity.attributes());
		FabricDefaultAttributeRegistry.register(MEDUSA, MedusaEntity.attributes());
		FabricDefaultAttributeRegistry.register(FARINATA, FarinataEntity.attributes());
		FabricDefaultAttributeRegistry.register(MINOTAUR, MinotaurEntity.attributes());
		FabricDefaultAttributeRegistry.register(VULCAN, VulcanEntity.attributes());
		FabricDefaultAttributeRegistry.register(SERAPH, SeraphEntity.attributes());
		FabricDefaultAttributeRegistry.register(CYCLOPS, CyclopsEntity.attributes());
		FabricDefaultAttributeRegistry.register(GERYON, GeryonEntity.attributes());
		FabricDefaultAttributeRegistry.register(GERYON_CARRIER, GeryonCarrier.attributes());
		FabricDefaultAttributeRegistry.register(ANTAEUS, AntaeusEntity.attributes());
		FabricDefaultAttributeRegistry.register(FALLEN_SERAPH, FallenSeraphEntity.attributes());
		FabricDefaultAttributeRegistry.register(EMPEROR, EmperorEntity.attributes());
		Carrier.register();
	}

	private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, HellcraftMod.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}
}
