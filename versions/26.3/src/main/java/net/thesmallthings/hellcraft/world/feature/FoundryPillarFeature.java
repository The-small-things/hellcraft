package net.thesmallthings.hellcraft.world.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.thesmallthings.hellcraft.world.ForgeGeometry;
import net.thesmallthings.hellcraft.world.InfernoGeometry;

/**
 * The Foundry Halls' pillars (Phase 6): square columns of blackstone brick from floor to roof, a gilded band round
 * the middle, arms near the top with lanterns hung on chains, a chain-lift up the south face to the top of the south
 * arm (ForgeOfDis.chainLifts: stand by the chain to rise, sneak to come down), and at the foot of some a smith's corner
 * (a blast furnace, an anvil and a lava cauldron). One to a cell of the grid.
 */
public record FoundryPillarFeature() implements Feature {
	public static final FoundryPillarFeature INSTANCE = new FoundryPillarFeature();
	public static final MapCodec<FoundryPillarFeature> CODEC = MapCodec.unit(INSTANCE);
	public static final int CELL = 30;
	/** The chain-lift's column is this far east of the pillar's middle, on its south face (z + 3); its chain is beside it. */
	public static final int LIFT_DX = 1;

	@Override
	public MapCodec<FoundryPillarFeature> codec() {
		return CODEC;
	}

	/** The pillar of cell (i, j), {x, z}, or null where the cell has none. */
	public static int[] site(int i, int j) {
		if (InfernoGeometry.hash01(i, j, 91) < 0.25) {
			return null;
		}
		int x = (int) Math.floor((i + 0.25 + 0.5 * InfernoGeometry.hash01(i, j, 92)) * CELL);
		int z = (int) Math.floor((j + 0.25 + 0.5 * InfernoGeometry.hash01(i, j, 93)) * CELL);
		return ForgeGeometry.weights(x, z)[ForgeGeometry.Region.FOUNDRY_HALLS.ordinal()] > 0.7 ? new int[]{x, z} : null;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		int x0 = origin.getX() & ~15;
		int z0 = origin.getZ() & ~15;
		boolean placed = false;
		for (int i = Math.floorDiv(x0, CELL); i <= Math.floorDiv(x0 + 15, CELL); i++) {
			for (int j = Math.floorDiv(z0, CELL); j <= Math.floorDiv(z0 + 15, CELL); j++) {
				int[] at = site(i, j);
				if (at != null && at[0] >= x0 && at[0] < x0 + 16 && at[1] >= z0 && at[1] < z0 + 16) {
					placed |= build(level, at[0], at[1], InfernoGeometry.hash01(i, j, 94));
				}
			}
		}
		return placed;
	}

	private static boolean build(WorldGenLevel level, int x, int z, double roll) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		// the floor: the first rock under open space, coming down from the halls' middle height
		int floor = -1;
		for (int y = 100; y > ForgeGeometry.LAVA; y--) {
			if (level.getBlockState(pos.set(x, y, z)).isSolid() && level.getBlockState(pos.set(x, y + 1, z)).isAir()) {
				floor = y + 1;
				break;
			}
		}
		if (floor < 0) {
			return false;
		}
		int ceiling = -1;
		for (int y = floor + 4; y < 124; y++) {
			if (level.getBlockState(pos.set(x, y, z)).isSolid()) {
				ceiling = y;
				break;
			}
		}
		if (ceiling < 0 || ceiling - floor < 14) {
			return false;
		}
		BlockState brick = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		BlockState chiseled = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
		BlockState gilded = Blocks.GILDED_BLACKSTONE.defaultBlockState();
		int middle = (floor + ceiling) / 2;
		// the plinth, then the shaft
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				for (int y = floor - 3; y < floor; y++) {
					level.setBlock(pos.set(x + dx, y, z + dz), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 2);
				}
			}
		}
		for (int y = floor - 1; y <= ceiling + 1; y++) {
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
					BlockState s = y == middle || y == middle + 1 ? gilded : corner && (y - floor) % 6 == 0 ? chiseled : brick;
					level.setBlock(pos.set(x + dx, y, z + dz), s, 2);
				}
			}
		}
		// arms near the top, lanterns hung from their ends
		int arm = Math.min(ceiling - 3, floor + 16);
		BlockState lantern = (roll < 0.5 ? Blocks.SOUL_LANTERN : Blocks.LANTERN).defaultBlockState().setValue(LanternBlock.HANGING, true);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			for (int k = 3; k <= 5; k++) {
				level.setBlock(pos.set(x + d.getStepX() * k, arm, z + d.getStepZ() * k), brick, 2);
			}
			int hang = 3 + (int) (roll * 4);
			int ex = x + d.getStepX() * 5;
			int ez = z + d.getStepZ() * 5;
			for (int k = 1; k <= hang; k++) {
				level.setBlock(pos.set(ex, arm - k, ez), Blocks.IRON_CHAIN.defaultBlockState(), 2);
			}
			level.setBlock(pos.set(ex, arm - hang - 1, ez), lantern, 2);
		}
		// the chain-lift up the south face: a chain from the floor to the arm, beside the column you ride (LIFT_DX), and a
		// plate at its foot to show where to stand
		for (int y = floor; y <= arm; y++) {
			level.setBlock(pos.set(x + LIFT_DX + 1, y, z + 3), Blocks.IRON_CHAIN.defaultBlockState(), 2);
		}
		level.setBlock(pos.set(x + LIFT_DX, floor, z + 3), Blocks.POLISHED_BLACKSTONE_PRESSURE_PLATE.defaultBlockState(), 2);
		// a smith's corner at the foot of some
		if (roll > 0.6) {
			level.setBlock(pos.set(x + 4, floor, z - 1), Blocks.BLAST_FURNACE.defaultBlockState(), 2);
			level.setBlock(pos.set(x + 4, floor, z + 1), Blocks.ANVIL.defaultBlockState(), 2);
			level.setBlock(pos.set(x + 4, floor, z), Blocks.LAVA_CAULDRON.defaultBlockState(), 2);
		}
		return true;
	}
}
