package net.thesmallthings.hellcraft.world.feature;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/** Another feature (Cocytus's ice spikes), placed only where it can't bury one of the braziers ({@link BrazierFeature#near}). */
public record ClearOfBraziersFeature(Holder<Feature> feature) implements Feature {
	public static final MapCodec<ClearOfBraziersFeature> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Feature.CODEC.fieldOf("feature").forGetter(ClearOfBraziersFeature::feature)
	).apply(instance, ClearOfBraziersFeature::new));

	/** An ice spike is up to about 6 wide: keep its foot this far from a brazier. */
	private static final int CLEARANCE = 10;

	@Override
	public MapCodec<ClearOfBraziersFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		if (BrazierFeature.near(origin.getX(), origin.getZ(), CLEARANCE)) {
			return false;
		}
		return feature.value().place(level, generator, random, origin);
	}
}
