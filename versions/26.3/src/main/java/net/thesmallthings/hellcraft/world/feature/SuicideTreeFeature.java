package net.thesmallthings.hellcraft.world.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.thesmallthings.hellcraft.block.HellBlocks;
import net.thesmallthings.hellcraft.world.InfernoGeometry;

/**
 * The Wood of the Suicides (Inferno XIII): "not green the leaves, but of a dusky colour; not smooth the branches, but
 * gnarled and tangled; no fruit there, but thorns with poison". Each tree is a soul: a trunk that leans and twists as it
 * climbs, its branches reaching out low (at head height, so the wood can't be walked through in a straight line), a few
 * dusky leaves and black thorns at its foot. Placed thickly, they make a wood you have to push or sneak through
 * (LowerRules: pushing past the branches makes them scream). Never in a guardian's arena; the roads clear their way.
 */
public record SuicideTreeFeature() implements Feature {
	public static final SuicideTreeFeature INSTANCE = new SuicideTreeFeature();
	public static final MapCodec<SuicideTreeFeature> CODEC = MapCodec.unit(INSTANCE);

	@Override
	public MapCodec<SuicideTreeFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		if (InfernoGeometry.nearLair(origin.getX(), origin.getZ(), InfernoGeometry.LAIR_CLEARANCE)) {
			return false;
		}
		if (!level.getBlockState(origin.below()).isSolid() || !level.getFluidState(origin).isEmpty() || !level.getBlockState(origin).canBeReplaced()
				|| InfernoGeometry.onRoad(origin.getX() + 0.5, origin.getZ() + 0.5)) {
			return false;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		// the trunk: it leans one way and twists as it climbs
		int height = 4 + random.nextInt(4);
		Direction lean = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		int x = 0;
		int z = 0;
		for (int y = 0; y < height; y++) {
			if (y > 1 && random.nextInt(3) == 0) {
				Direction step = random.nextInt(3) == 0 ? lean.getClockWise() : lean;
				// a knot where it bends: the log below reaches across
				set(level, pos.setWithOffset(origin, x + step.getStepX(), y - 1, z + step.getStepZ()), log(random, step.getAxis()));
				x += step.getStepX();
				z += step.getStepZ();
			}
			set(level, pos.setWithOffset(origin, x, y, z), log(random, Direction.Axis.Y));
		}
		// gnarled branches: some low across the way, some up into the dark
		int branches = 3 + random.nextInt(3);
		for (int b = 0; b < branches; b++) {
			int startY = b == 0 ? 1 : 1 + random.nextInt(Math.max(1, height - 1));
			branch(level, random, pos, origin.offset(x * startY / Math.max(1, height), startY, z * startY / Math.max(1, height)),
					Direction.Plane.HORIZONTAL.getRandomDirection(random), 2 + random.nextInt(3));
		}
		// a crown of twigs and dusky leaves
		for (int k = 0; k < 2 + random.nextInt(3); k++) {
			branch(level, random, pos, origin.offset(x, height - 1, z), Direction.Plane.HORIZONTAL.getRandomDirection(random), 1 + random.nextInt(3));
		}
		// thorns round its foot
		for (int k = 0; k < 4 + random.nextInt(5); k++) {
			int tx = random.nextInt(5) - 2;
			int tz = random.nextInt(5) - 2;
			for (int dy = 1; dy >= -2; dy--) {
				pos.setWithOffset(origin, tx, dy, tz);
				if (level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
					set(level, pos, HellBlocks.THORNS.defaultBlockState());
					break;
				}
			}
		}
		return true;
	}

	/** A branch: out from the trunk, kinking up or down and sideways as it goes, ending in a twig or a few leaves. */
	private static void branch(WorldGenLevel level, RandomSource random, BlockPos.MutableBlockPos pos, BlockPos start, Direction out, int length) {
		int x = start.getX();
		int y = start.getY();
		int z = start.getZ();
		Direction dir = out;
		for (int i = 0; i < length; i++) {
			x += dir.getStepX();
			z += dir.getStepZ();
			if (random.nextInt(3) == 0) {
				y += random.nextInt(3) == 0 ? -1 : 1;
			}
			if (random.nextInt(4) == 0) {
				dir = random.nextBoolean() ? dir.getClockWise() : dir.getCounterClockWise();
			}
			if (!set(level, pos.set(x, y, z), log(random, dir.getAxis()))) {
				return;
			}
		}
		// the tip: a crooked twig of roots, or a handful of dusky leaves
		pos.set(x + dir.getStepX(), y + (random.nextBoolean() ? 1 : 0), z + dir.getStepZ());
		if (random.nextInt(3) == 0) {
			set(level, pos, Blocks.MANGROVE_ROOTS.defaultBlockState());
		} else {
			BlockState leaves = Blocks.DARK_OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
			set(level, pos, leaves);
			if (random.nextBoolean()) {
				set(level, pos.move(Direction.UP), leaves);
			}
		}
	}

	private static BlockState log(RandomSource random, Direction.Axis axis) {
		int roll = random.nextInt(8);
		BlockState state = roll < 4 ? Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState() : roll < 7 ? Blocks.DARK_OAK_LOG.defaultBlockState()
				: Blocks.STRIPPED_MANGROVE_LOG.defaultBlockState();
		return state.setValue(RotatedPillarBlock.AXIS, axis);
	}

	/**
	 * Sets a block where there's room (air, plants, thorns); false where something solid is in the way, or on a road (a
	 * tree from the next chunk may reach over a road that is already built).
	 */
	private static boolean set(WorldGenLevel level, BlockPos pos, BlockState state) {
		if (InfernoGeometry.onRoad(pos.getX() + 0.5, pos.getZ() + 0.5)) {
			return false;
		}
		BlockState there = level.getBlockState(pos);
		if (!there.isAir() && !there.canBeReplaced() && !there.is(HellBlocks.THORNS)) {
			return false;
		}
		level.setBlock(pos, state, 2);
		return true;
	}
}
