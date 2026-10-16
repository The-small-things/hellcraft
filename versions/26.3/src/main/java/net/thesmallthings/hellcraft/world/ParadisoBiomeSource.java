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

/** Biome source {@code hellcraft:paradiso}: Paradiso's sphere at a height (ParadisoGeometry.sphereAtY), as a biome; each has its own sky. */
public class ParadisoBiomeSource extends BiomeSource {
	public static final MapCodec<ParadisoBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			RegistryOps.retrieveGetter(Registries.BIOME)
	).apply(instance, ParadisoBiomeSource::new));

	private final Map<ParadisoGeometry.Sphere, Holder<Biome>> biomes = new EnumMap<>(ParadisoGeometry.Sphere.class);

	public ParadisoBiomeSource(HolderGetter<Biome> getter) {
		for (ParadisoGeometry.Sphere s : ParadisoGeometry.SPHERES) {
			biomes.put(s, getter.getOrThrow(ResourceKey.create(Registries.BIOME, HellcraftMod.id("paradiso_" + s.id))));
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
		return (quartX, quartY, quartZ) -> biomes.get(ParadisoGeometry.sphereAtY((quartY << 2) + 2));
	}
}
