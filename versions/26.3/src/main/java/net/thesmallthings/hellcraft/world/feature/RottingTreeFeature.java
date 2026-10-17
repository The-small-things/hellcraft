package net.thesmallthings.hellcraft.world.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.thesmallthings.hellcraft.world.InfernoGeometry;

/**
 * Gluttony's rotting giants (L-III, the Mire): huge dead trees going soft in the endless rain, roots splayed into
 * the mud, shelves of fungus up their trunks and a broken crown of bare branches. One in three has already fallen
 * and lies in the mud, sprouting mushrooms. Their roots and logs are firm footing in the mire (LayerRules).
 */
public record RottingTreeFeature() implements Feature {
	public static final RottingTreeFeature INSTANCE = new RottingTreeFeature();
	public static final MapCodec<RottingTreeFeature> CODEC = MapCodec.unit(INSTANCE);

	@Override
	public MapCodec<RottingTreeFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		// not in a guardian's arena
		if (InfernoGeometry.nearLair(origin.getX(), origin.getZ(), InfernoGeometry.LAIR_CLEARANCE)) {
			return false;
		}
		BlockState below = level.getBlockState(origin.below());
		if (!below.isSolid() || !level.getFluidState(origin).isEmpty()) {
			return false;
		}
		if (random.nextInt(3) == 0) {
			fallen(level, random, origin);
		} else {
			standing(level, random, origin);
		}
		return true;
	}

	private static BlockState wood(RandomSource random, Direction.Axis axis) {
		int roll = random.nextInt(10);
		BlockState state = roll < 5 ? Blocks.DARK_OAK_LOG.defaultBlockState() : roll < 8 ? Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState()
				: Blocks.MANGROVE_LOG.defaultBlockState();
		return state.setValue(RotatedPillarBlock.AXIS, axis);
	}

	/** Rot: a patch of moss or fungus in place of bark. */
	private static BlockState rot(RandomSource random) {
		return random.nextInt(3) == 0 ? Blocks.MOSS_BLOCK.defaultBlockState()
				: Blocks.MUSHROOM_STEM.defaultBlockState().setValue(HugeMushroomBlock.UP, false).setValue(HugeMushroomBlock.DOWN, false);
	}

	private static void standing(WorldGenLevel level, RandomSource random, BlockPos origin) {
		int height = 12 + random.nextInt(9);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		// a trunk three blocks across (a plus shape), its top broken off unevenly
		int[][] core = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] c : core) {
			int top = height - (c[0] == 0 && c[1] == 0 ? 0 : random.nextInt(5));
			for (int y = -2; y < top; y++) {
				pos.setWithOffset(origin, c[0], y, c[1]);
				level.setBlock(pos, random.nextInt(9) == 0 ? rot(random) : wood(random, Direction.Axis.Y), 2);
			}
		}
		// roots splayed into the mud, arching down from the trunk
		for (int k = 0; k < 6 + random.nextInt(3); k++) {
			double a = random.nextDouble() * Math.PI * 2;
			int length = 3 + random.nextInt(4);
			for (int i = 1; i <= length; i++) {
				int x = (int) Math.round(Math.cos(a) * (i + 0.5));
				int z = (int) Math.round(Math.sin(a) * (i + 0.5));
				int y = Math.max(-1, 2 - i);
				pos.setWithOffset(origin, x, y, z);
				if (level.getBlockState(pos).canBeReplaced() || level.getBlockState(pos).is(Blocks.MUD)) {
					level.setBlock(pos, i < length ? Blocks.MANGROVE_ROOTS.defaultBlockState() : Blocks.MUDDY_MANGROVE_ROOTS.defaultBlockState(), 2);
				}
				pos.setWithOffset(origin, x, y - 1, z);
				if (level.getBlockState(pos).canBeReplaced()) {
					level.setBlock(pos, Blocks.MUDDY_MANGROVE_ROOTS.defaultBlockState(), 2);
				}
			}
		}
		// shelves of fungus on the trunk
		for (int k = 0; k < 4 + random.nextInt(4); k++) {
			Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(random);
			int y = 2 + random.nextInt(Math.max(1, height - 5));
			BlockState shelf = random.nextBoolean() ? Blocks.BROWN_MUSHROOM_BLOCK.defaultBlockState() : Blocks.RED_MUSHROOM_BLOCK.defaultBlockState();
			for (int w = -1; w <= 1; w++) {
				pos.setWithOffset(origin, side.getStepX() * 2 + (side.getAxis() == Direction.Axis.Z ? w : 0), y,
						side.getStepZ() * 2 + (side.getAxis() == Direction.Axis.X ? w : 0));
				if (level.getBlockState(pos).isAir()) {
					level.setBlock(pos, shelf, 2);
				}
			}
		}
		// a broken crown: bare branches reaching out and up
		for (int k = 0; k < 3 + random.nextInt(3); k++) {
			double a = random.nextDouble() * Math.PI * 2;
			int length = 4 + random.nextInt(5);
			int base = height - 3 - random.nextInt(4);
			Direction.Axis axis = Math.abs(Math.cos(a)) > Math.abs(Math.sin(a)) ? Direction.Axis.X : Direction.Axis.Z;
			for (int i = 1; i <= length; i++) {
				int x = (int) Math.round(Math.cos(a) * (i + 0.5));
				int z = (int) Math.round(Math.sin(a) * (i + 0.5));
				int y = base + i / 2;
				pos.setWithOffset(origin, x, y, z);
				if (level.getBlockState(pos).isAir()) {
					level.setBlock(pos, wood(random, i % 3 == 0 ? Direction.Axis.Y : axis), 2);
				}
			}
		}
	}

	private static void fallen(WorldGenLevel level, RandomSource random, BlockPos origin) {
		Direction along = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		int length = 10 + random.nextInt(8);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < length; i++) {
			for (int w = -1; w <= 1; w++) {
				for (int h = 0; h <= 1; h++) {
					// round in section: drop the corners
					if (w != 0 && h == 1) {
						continue;
					}
					int x = along.getStepX() * i + (along.getAxis() == Direction.Axis.Z ? w : 0);
					int z = along.getStepZ() * i + (along.getAxis() == Direction.Axis.X ? w : 0);
					pos.setWithOffset(origin, x, h - 1, z);
					if (level.getBlockState(pos).canBeReplaced() || level.getBlockState(pos).is(Blocks.MUD)) {
						level.setBlock(pos, random.nextInt(7) == 0 ? rot(random) : wood(random, along.getAxis()), 2);
					}
				}
			}
			// mushrooms on its back
			if (random.nextInt(3) == 0) {
				pos.setWithOffset(origin, along.getStepX() * i, 1, along.getStepZ() * i);
				if (level.getBlockState(pos).isAir()) {
					level.setBlock(pos, random.nextBoolean() ? Blocks.BROWN_MUSHROOM.defaultBlockState() : Blocks.RED_MUSHROOM.defaultBlockState(), 2);
				}
			}
		}
		// the torn-up root plate stands on end at its foot
		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = 0; dy <= 4; dy++) {
				if (Math.abs(dx) + Math.abs(dy - 2) > 3) {
					continue;
				}
				int x = -along.getStepX() + (along.getAxis() == Direction.Axis.Z ? dx : 0);
				int z = -along.getStepZ() + (along.getAxis() == Direction.Axis.X ? dx : 0);
				pos.setWithOffset(origin, x, dy - 1, z);
				if (level.getBlockState(pos).canBeReplaced()) {
					level.setBlock(pos, random.nextInt(3) == 0 ? Blocks.MUD.defaultBlockState() : Blocks.MUDDY_MANGROVE_ROOTS.defaultBlockState(), 2);
				}
			}
		}
	}
}
