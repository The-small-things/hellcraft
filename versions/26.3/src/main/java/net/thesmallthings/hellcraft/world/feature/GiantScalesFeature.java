package net.thesmallthings.hellcraft.world.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Greed's giant scales (L-IV, the Counting House): a balance the height of a tower, its beam tipped forever toward
 * the pan heaped with gold. Climb the pillar for a look over the hoards.
 */
public record GiantScalesFeature() implements Feature {
	public static final GiantScalesFeature INSTANCE = new GiantScalesFeature();
	public static final MapCodec<GiantScalesFeature> CODEC = MapCodec.unit(INSTANCE);

	@Override
	public MapCodec<GiantScalesFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		if (!level.getBlockState(origin.below()).isSolid() || !level.getFluidState(origin).isEmpty()) {
			return false;
		}
		Direction beam = random.nextBoolean() ? Direction.EAST : Direction.SOUTH;
		int height = 14 + random.nextInt(5);
		int arm = 7;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		// a stepped plinth and the pillar
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int y = -2; y <= (Math.max(Math.abs(dx), Math.abs(dz)) == 2 ? 0 : 1); y++) {
					pos.setWithOffset(origin, dx, y, dz);
					level.setBlock(pos, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 2);
				}
			}
		}
		for (int y = 2; y < height; y++) {
			pos.setWithOffset(origin, 0, y, 0);
			level.setBlock(pos, y % 4 == 0 ? Blocks.GILDED_BLACKSTONE.defaultBlockState()
					: Blocks.POLISHED_BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y), 2);
		}
		pos.setWithOffset(origin, 0, height, 0);
		level.setBlock(pos, Blocks.GOLD_BLOCK.defaultBlockState(), 2);
		// the beam, tipped: the heavy (gold) end three blocks lower than the light one
		for (int i = -arm; i <= arm; i++) {
			int dy = (int) Math.round(-i * 3.0 / (2 * arm) * 2);
			pos.setWithOffset(origin, beam.getStepX() * i, height + dy, beam.getStepZ() * i);
			level.setBlock(pos, Math.abs(i) == arm ? Blocks.GOLD_BLOCK.defaultBlockState()
					: Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 2);
		}
		pan(level, random, pos, origin, beam, arm, height + (int) Math.round(-arm * 3.0 / (2 * arm) * 2), 4, true);
		pan(level, random, pos, origin, beam, -arm, height + (int) Math.round(arm * 3.0 / (2 * arm) * 2), 7, false);
		return true;
	}

	/** A pan hanging on chains from the beam's end at offset i: the heavy one low and full of gold, the other empty. */
	private static void pan(WorldGenLevel level, RandomSource random, BlockPos.MutableBlockPos pos, BlockPos origin, Direction beam, int i, int top,
							int drop, boolean heavy) {
		int cx = origin.getX() + beam.getStepX() * i;
		int cz = origin.getZ() + beam.getStepZ() * i;
		int panY = top - drop;
		for (int y = panY + 1; y < top; y++) {
			pos.set(cx, y, cz);
			level.setBlock(pos, Blocks.IRON_CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y), 2);
		}
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (Math.abs(dx) == 2 && Math.abs(dz) == 2) {
					continue;
				}
				pos.set(cx + dx, panY, cz + dz);
				level.setBlock(pos, Blocks.GOLD_BLOCK.defaultBlockState(), 2);
				if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
					pos.set(cx + dx, panY + 1, cz + dz);
					level.setBlock(pos, Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState(), 2);
				} else if (heavy) {
					pos.set(cx + dx, panY + 1, cz + dz);
					level.setBlock(pos, random.nextInt(3) == 0 ? Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState() : Blocks.RAW_GOLD_BLOCK.defaultBlockState(), 2);
				}
			}
		}
	}
}
