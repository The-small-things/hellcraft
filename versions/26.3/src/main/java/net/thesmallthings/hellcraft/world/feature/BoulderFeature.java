package net.thesmallthings.hellcraft.world.feature;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration;

/** A rough half-buried boulder of any block (the "great weights" of Greed, ice in Cocytus, ...). */
public class BoulderFeature extends Feature<BlockStateConfiguration> {
	public BoulderFeature() {
		super(BlockStateConfiguration.CODEC);
	}

	@Override
	public boolean place(FeaturePlaceContext<BlockStateConfiguration> context) {
		WorldGenLevel level = context.level();
		RandomSource random = context.random();
		BlockPos center = context.origin().below(1);
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
						level.setBlock(pos, context.config().state, 2);
					}
				}
			}
		}
		return true;
	}
}
