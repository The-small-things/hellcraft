package net.thesmallthings.hellcraft.world;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.world.feature.AltarRuinFeature;
import net.thesmallthings.hellcraft.world.feature.BoulderFeature;
import net.thesmallthings.hellcraft.world.feature.BurningTombFeature;
import net.thesmallthings.hellcraft.world.feature.ChainPillarFeature;
import net.thesmallthings.hellcraft.world.feature.DisWallFeature;
import net.thesmallthings.hellcraft.world.feature.ForgeRuinFeature;
import net.thesmallthings.hellcraft.world.feature.GateWallFeature;
import net.thesmallthings.hellcraft.world.feature.RingFluidFeature;
import net.thesmallthings.hellcraft.world.feature.SlagHeapFeature;
import net.thesmallthings.hellcraft.world.feature.StarClusterFeature;

/**
 * Registers the handful of worldgen codecs the data pack refers to. None of these registries are
 * synced to clients, so vanilla clients can still join.
 */
public final class HellWorldgen {
	private HellWorldgen() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, HellcraftMod.id("inferno"), InfernoDensity.MAP_CODEC);
		Registry.register(BuiltInRegistries.BIOME_SOURCE, HellcraftMod.id("circles"), InfernoBiomeSource.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, HellcraftMod.id("ring_fluid"), RingFluidFeature.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, HellcraftMod.id("dis_wall"), DisWallFeature.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, HellcraftMod.id("gate_wall"), GateWallFeature.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, HellcraftMod.id("burning_tomb"), BurningTombFeature.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, HellcraftMod.id("altar_ruin"), AltarRuinFeature.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, HellcraftMod.id("boulder"), BoulderFeature.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, HellcraftMod.id("forge_ruin"), ForgeRuinFeature.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, HellcraftMod.id("chain_pillar"), ChainPillarFeature.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, HellcraftMod.id("slag_heap"), SlagHeapFeature.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, HellcraftMod.id("star_cluster"), StarClusterFeature.CODEC);
	}

	/** True when this level was generated as the Inferno (so gameplay only applies there). */
	public static boolean isInferno(ServerLevel level) {
		return level.getChunkSource().getGenerator().getBiomeSource() instanceof InfernoBiomeSource;
	}
}
