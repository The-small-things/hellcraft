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

/** Biome source {@code hellcraft:forge}: the Forge of Dis's region at a position (ForgeGeometry), as a biome. */
public class ForgeBiomeSource extends BiomeSource {
	public static final MapCodec<ForgeBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			RegistryOps.retrieveGetter(Registries.BIOME)
	).apply(instance, ForgeBiomeSource::new));

	private final Map<ForgeGeometry.Region, Holder<Biome>> biomes = new EnumMap<>(ForgeGeometry.Region.class);

	public ForgeBiomeSource(HolderGetter<Biome> getter) {
		for (ForgeGeometry.Region region : ForgeGeometry.Region.values()) {
			biomes.put(region, getter.getOrThrow(ResourceKey.create(Registries.BIOME, HellcraftMod.id(region.id()))));
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
		return (quartX, quartY, quartZ) -> biomes.get(ForgeGeometry.regionAt((quartX << 2) + 2, (quartZ << 2) + 2));
	}
}
