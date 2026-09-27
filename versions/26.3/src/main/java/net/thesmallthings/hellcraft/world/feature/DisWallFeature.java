package net.thesmallthings.hellcraft.world.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.thesmallthings.hellcraft.world.InfernoGeometry;

/**
 * The iron-red Walls of Dis: a continuous ring wall between Styx and Heresy with burning towers
 * and four open gates. Built one column at a time from {@link InfernoGeometry#disWallAt}.
 */
public record DisWallFeature() implements Feature {
	public static final DisWallFeature INSTANCE = new DisWallFeature();
	public static final MapCodec<DisWallFeature> CODEC = MapCodec.unit(INSTANCE);
	private static final int BASE_Y = 58;

	@Override
	public MapCodec<DisWallFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		int x0 = origin.getX() & ~15;
		int z0 = origin.getZ() & ~15;
		double r = Math.sqrt((x0 + 8.0) * (x0 + 8.0) + (z0 + 8.0) * (z0 + 8.0));
		if (!InfernoGeometry.nearDisWall(r, 16)) {
			return false;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		boolean placed = false;
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = x0 + dx;
				int z = z0 + dz;
				InfernoGeometry.WallPart part = InfernoGeometry.disWallAt(x, z);
				if (part == InfernoGeometry.WallPart.NONE) {
					continue;
				}
				int ground = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
				int bottom = Math.min(ground, BASE_Y) - 4;
				int top = switch (part) {
					case TOWER -> BASE_Y + InfernoGeometry.DIS_TOWER_HEIGHT;
					default -> BASE_Y + InfernoGeometry.DIS_WALL_HEIGHT;
				};
				int gateTop = ground + InfernoGeometry.DIS_GATE_HEIGHT;
				for (int y = bottom; y <= top; y++) {
					pos.set(x, y, z);
					if (part == InfernoGeometry.WallPart.GATE && y >= ground && y < gateTop) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
						continue;
					}
					if (part == InfernoGeometry.WallPart.GATE && y == ground - 1) {
						level.setBlock(pos, Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 2);
						continue;
					}
					level.setBlock(pos, brick(random), 2);
				}
				if (part == InfernoGeometry.WallPart.TOWER) {
					pos.set(x, top, z);
					level.setBlock(pos, Blocks.NETHERRACK.defaultBlockState(), 2);
					if (random.nextInt(3) == 0) {
						pos.set(x, top + 1, z);
						level.setBlock(pos, Blocks.FIRE.defaultBlockState(), 2);
					}
				} else if (((x + z) & 1) == 0) {
					// crenellations
					pos.set(x, top + 1, z);
					level.setBlock(pos, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 2);
				}
				placed = true;
			}
		}
		return placed;
	}

	private static BlockState brick(RandomSource random) {
		int roll = random.nextInt(20);
		if (roll < 3) {
			return Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		}
		if (roll == 3) {
			return Blocks.RED_NETHER_BRICKS.defaultBlockState();
		}
		if (roll == 4) {
			return Blocks.GILDED_BLACKSTONE.defaultBlockState();
		}
		return Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
	}
}
