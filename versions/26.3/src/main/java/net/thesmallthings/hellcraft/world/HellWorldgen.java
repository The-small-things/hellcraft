package net.thesmallthings.hellcraft.world;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.world.feature.AltarRuinFeature;
import net.thesmallthings.hellcraft.world.feature.BloatedFungusFeature;
import net.thesmallthings.hellcraft.world.feature.BoardwalkFeature;
import net.thesmallthings.hellcraft.world.feature.BoulderFeature;
import net.thesmallthings.hellcraft.world.feature.BurningTombFeature;
import net.thesmallthings.hellcraft.world.feature.ChainPillarFeature;
import net.thesmallthings.hellcraft.world.feature.CoinHillFeature;
import net.thesmallthings.hellcraft.world.feature.DisWallFeature;
import net.thesmallthings.hellcraft.world.feature.FeastTableFeature;
import net.thesmallthings.hellcraft.world.feature.ForgeRuinFeature;
import net.thesmallthings.hellcraft.world.feature.GateWallFeature;
import net.thesmallthings.hellcraft.world.feature.GiantScalesFeature;
import net.thesmallthings.hellcraft.world.feature.NobleCastleFeature;
import net.thesmallthings.hellcraft.world.feature.PhlegyasLandingFeature;
import net.thesmallthings.hellcraft.world.feature.PilgrimRoadFeature;
import net.thesmallthings.hellcraft.world.feature.ReedBedFeature;
import net.thesmallthings.hellcraft.world.feature.RingFluidFeature;
import net.thesmallthings.hellcraft.world.feature.RottingTreeFeature;
import net.thesmallthings.hellcraft.world.feature.SlagHeapFeature;
import net.thesmallthings.hellcraft.world.feature.StarClusterFeature;
import net.thesmallthings.hellcraft.world.feature.VaultFeature;
import net.thesmallthings.hellcraft.world.feature.WatchtowerFeature;

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
		Registry.register(BuiltInRegistries.FEATURE_TYPE, HellcraftMod.id("pilgrim_road"), PilgrimRoadFeature.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, HellcraftMod.id("noble_castle"), NobleCastleFeature.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, HellcraftMod.id("rotting_tree"), RottingTreeFeature.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, HellcraftMod.id("bloated_fungus"), BloatedFungusFeature.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, HellcraftMod.id("feast_table"), FeastTableFeature.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, HellcraftMod.id("coin_hill"), CoinHillFeature.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, HellcraftMod.id("vault"), VaultFeature.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, HellcraftMod.id("giant_scales"), GiantScalesFeature.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, HellcraftMod.id("watchtower"), WatchtowerFeature.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, HellcraftMod.id("boardwalk"), BoardwalkFeature.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, HellcraftMod.id("reed_bed"), ReedBedFeature.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, HellcraftMod.id("phlegyas_landing"), PhlegyasLandingFeature.CODEC);
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
