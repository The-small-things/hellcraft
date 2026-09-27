package net.thesmallthings.hellcraft.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.thesmallthings.hellcraft.HellcraftMod;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Stream;

/** Biome source {@code hellcraft:circles}: picks the biome of whichever ring of Hell a position falls in. */
public class InfernoBiomeSource extends BiomeSource {
	public static final MapCodec<InfernoBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			RegistryOps.retrieveGetter(Registries.BIOME)
	).apply(instance, InfernoBiomeSource::new));

	private final Map<Zone, Holder<Biome>> biomes = new EnumMap<>(Zone.class);

	public InfernoBiomeSource(HolderGetter<Biome> getter) {
		for (Zone zone : Zone.values()) {
			biomes.put(zone, getter.getOrThrow(ResourceKey.create(Registries.BIOME, HellcraftMod.id(zone.id()))));
		}
	}

	@Override
	protected MapCodec<? extends BiomeSource> codec() {
		return CODEC;
	}

	@Override
	protected Stream<Holder<Biome>> collectPossibleBiomes() {
		return biomes.values().stream();
	}

	@Override
	public BiomeResolver createResolver(Climate.Sampler sampler) {
		return (quartX, quartY, quartZ) -> biomes.get(InfernoGeometry.zoneAt((quartX << 2) + 2, (quartZ << 2) + 2));
	}
}
