package net.thesmallthings.hellcraft.world.feature;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** The open, burning sepulchres of the heretics (Inferno, Canto IX-X). */
public class BurningTombFeature extends Feature<NoneFeatureConfiguration> {
	public BurningTombFeature() {
		super(NoneFeatureConfiguration.CODEC);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		WorldGenLevel level = context.level();
		RandomSource random = context.random();
		BlockPos origin = context.origin();
		if (!level.getBlockState(origin.below()).isSolid()) {
			return false;
		}
		boolean alongX = random.nextBoolean();
		boolean soul = random.nextInt(3) == 0;
		int length = 5;
		int width = 3;
		BlockState wall = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		BlockState cracked = Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		BlockState fuel = soul ? Blocks.SOUL_SOIL.defaultBlockState() : Blocks.NETHERRACK.defaultBlockState();
		BlockState flame = soul ? Blocks.SOUL_FIRE.defaultBlockState() : Blocks.FIRE.defaultBlockState();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int a = 0; a < length; a++) {
			for (int b = 0; b < width; b++) {
				int dx = alongX ? a : b;
				int dz = alongX ? b : a;
				boolean edge = a == 0 || a == length - 1 || b == 0 || b == width - 1;
				// foundation so tombs on slopes don't float
				for (int y = -3; y <= -1; y++) {
					pos.setWithOffset(origin, dx, y, dz);
					level.setBlock(pos, edge ? wall : fuel, 2);
				}
				if (edge) {
					for (int y = 0; y <= 1; y++) {
						pos.setWithOffset(origin, dx, y, dz);
						level.setBlock(pos, random.nextInt(5) == 0 ? cracked : wall, 2);
					}
				} else {
					pos.setWithOffset(origin, dx, 0, dz);
					level.setBlock(pos, flame, 2);
					pos.setWithOffset(origin, dx, 1, dz);
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
		// the lid lies cast aside next to the tomb
		for (int a = 0; a < length; a++) {
			int dx = alongX ? a : width;
			int dz = alongX ? width : a;
			pos.setWithOffset(origin, dx, 0, dz);
			if (level.getBlockState(pos).canBeReplaced()) {
				level.setBlock(pos, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB.defaultBlockState(), 2);
			}
		}
		return true;
	}
}
