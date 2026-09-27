package net.thesmallthings.hellcraft.world.feature;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/** A rough half-buried boulder of any block (the "great weights" of Greed, ice in Cocytus, ...). */
public record BoulderFeature(BlockState state) implements Feature {
	public static final MapCodec<BoulderFeature> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			BlockState.CODEC.fieldOf("state").forGetter(BoulderFeature::state)
	).apply(instance, BoulderFeature::new));

	@Override
	public MapCodec<BoulderFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		BlockPos center = origin.below(1);
		if (!level.getBlockState(center.below()).isSolid()) {
			return false;
		}
		float radius = 1.2f + random.nextFloat() * 1.8f;
		int r = (int) Math.ceil(radius);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					float d = (float) Math.sqrt(dx * dx + dy * dy * 1.3f + dz * dz);
					if (d <= radius + random.nextFloat() * 0.4f) {
						pos.setWithOffset(center, dx, dy, dz);
						level.setBlock(pos, state, 2);
					}
				}
			}
		}
		return true;
	}
}
