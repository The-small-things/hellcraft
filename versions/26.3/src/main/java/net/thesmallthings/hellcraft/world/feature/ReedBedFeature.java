package net.thesmallthings.hellcraft.world.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * The Fen's reed beds (L-V): thick walls of reeds along every bank and tall grass on the islands, so the marsh is a
 * maze you see only a few blocks into.
 */
public record ReedBedFeature() implements Feature {
	public static final ReedBedFeature INSTANCE = new ReedBedFeature();
	public static final MapCodec<ReedBedFeature> CODEC = MapCodec.unit(INSTANCE);
	private static final int RADIUS = 7;

	@Override
	public MapCodec<ReedBedFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		boolean placed = false;
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				if (dx * dx + dz * dz > RADIUS * RADIUS) {
					continue;
				}
				int x = origin.getX() + dx;
				int z = origin.getZ() + dz;
				int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
				pos.set(x, y - 1, z);
				BlockState ground = level.getBlockState(pos);
				if (!level.getBlockState(pos.above()).isAir() || !(ground.is(BlockTags.DIRT) || ground.is(Blocks.SOUL_SOIL) || ground.is(Blocks.SAND))) {
					continue;
				}
				if (nextToWater(level, pos) && random.nextInt(10) < 7) {
					int height = 2 + random.nextInt(3);
					for (int h = 0; h < height; h++) {
						level.setBlock(pos.set(x, y + h, z), Blocks.SUGAR_CANE.defaultBlockState(), 2);
					}
					placed = true;
				} else if (random.nextInt(10) < 4) {
					BlockState lower = (random.nextBoolean() ? Blocks.TALL_GRASS : Blocks.LARGE_FERN).defaultBlockState();
					level.setBlock(pos.set(x, y, z), lower.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER), 2);
					level.setBlock(pos.set(x, y + 1, z), lower.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER), 2);
					placed = true;
				}
			}
		}
		return placed;
	}

	private static boolean nextToWater(WorldGenLevel level, BlockPos ground) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (level.getFluidState(ground.relative(d)).is(FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}
}
