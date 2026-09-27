package net.thesmallthings.hellcraft.world;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.world.feature.AltarRuinFeature;
import net.thesmallthings.hellcraft.world.feature.BoulderFeature;
import net.thesmallthings.hellcraft.world.feature.BurningTombFeature;
import net.thesmallthings.hellcraft.world.feature.DisWallFeature;
import net.thesmallthings.hellcraft.world.feature.GateWallFeature;
import net.thesmallthings.hellcraft.world.feature.RingFluidFeature;

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
		Registry.register(BuiltInRegistries.FEATURE, HellcraftMod.id("ring_fluid"), new RingFluidFeature());
		Registry.register(BuiltInRegistries.FEATURE, HellcraftMod.id("dis_wall"), new DisWallFeature());
		Registry.register(BuiltInRegistries.FEATURE, HellcraftMod.id("gate_wall"), new GateWallFeature());
		Registry.register(BuiltInRegistries.FEATURE, HellcraftMod.id("burning_tomb"), new BurningTombFeature());
		Registry.register(BuiltInRegistries.FEATURE, HellcraftMod.id("altar_ruin"), new AltarRuinFeature());
		Registry.register(BuiltInRegistries.FEATURE, HellcraftMod.id("boulder"), new BoulderFeature());
	}

	/** True when this level was generated as the Inferno (so gameplay only applies there). */
	public static boolean isInferno(ServerLevel level) {
		return level.getChunkSource().getGenerator().getBiomeSource() instanceof InfernoBiomeSource;
	}
}
