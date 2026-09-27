package net.thesmallthings.hellcraft.world.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/** The open, burning sepulchres of the heretics (Inferno, Canto IX-X). */
public record BurningTombFeature() implements Feature {
	public static final BurningTombFeature INSTANCE = new BurningTombFeature();
	public static final MapCodec<BurningTombFeature> CODEC = MapCodec.unit(INSTANCE);

	@Override
	public MapCodec<BurningTombFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		BlockPos origin = origin;
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
