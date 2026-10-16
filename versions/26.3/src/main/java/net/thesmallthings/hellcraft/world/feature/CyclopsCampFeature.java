package net.thesmallthings.hellcraft.world.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/** The Cyclops Quarries' camps (Phase 6): built with the Cyclops. */
public record CyclopsCampFeature() implements Feature {
	public static final CyclopsCampFeature INSTANCE = new CyclopsCampFeature();
	public static final MapCodec<CyclopsCampFeature> CODEC = MapCodec.unit(INSTANCE);

	@Override
	public MapCodec<CyclopsCampFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		return false;
	}
}
