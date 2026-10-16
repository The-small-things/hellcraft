package net.thesmallthings.hellcraft.world.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.Spine;
import net.thesmallthings.hellcraft.world.Zone;

/**
 * Cocytus's braziers (L-IX): cold iron bowls on blackstone plinths scattered across the frozen lake, one every
 * {@link #CELL} blocks or so, all of them out. Light one (flint and steel, or a torch: hazard/CircleHazards) and it
 * burns for good, for everyone: the cold can't reach anyone standing by it, so lit braziers mark a warm way across.
 */
public record BrazierFeature() implements Feature {
	public static final BrazierFeature INSTANCE = new BrazierFeature();
	public static final MapCodec<BrazierFeature> CODEC = MapCodec.unit(INSTANCE);
	/** The braziers stand one to each cell of a grid this many blocks across, somewhere in its middle. */
	public static final int CELL = 40;

	@Override
	public MapCodec<BrazierFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		int x0 = origin.getX() & ~15;
		int z0 = origin.getZ() & ~15;
		boolean placed = false;
		for (int cx = Math.floorDiv(x0, CELL); cx <= Math.floorDiv(x0 + 15, CELL); cx++) {
			for (int cz = Math.floorDiv(z0, CELL); cz <= Math.floorDiv(z0 + 15, CELL); cz++) {
				int[] at = site(cx, cz);
				if (at != null && at[0] >= x0 && at[0] <= x0 + 15 && at[1] >= z0 && at[1] <= z0 + 15) {
					build(level, at[0], at[1]);
					placed = true;
				}
			}
		}
		return placed;
	}

	/** Where cell (cx, cz)'s brazier stands, or null if it has none (off the ice, by a Spine, at the pit). */
	public static int[] site(int cx, int cz) {
		long h = cx * 0x9E3779B97F4A7C15L ^ cz * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		int x = cx * CELL + 8 + (int) Math.floorMod(h, CELL - 16);
		int z = cz * CELL + 8 + (int) Math.floorMod(h >>> 20, CELL - 16);
		Zone zone = InfernoGeometry.zoneAt(x, z);
		double r = Math.sqrt((double) x * x + (double) z * z);
		if (zone != Zone.COCYTUS && zone != Zone.JUDECCA || r < InfernoGeometry.PIT_RADIUS + 10 || r > InfernoGeometry.outerRadius(Zone.COCYTUS) - 25) {
			return null;
		}
		if (Spine.onSpine(x, z, 8)) {
			return null;
		}
		return new int[]{x, z};
	}

	/**
	 * /hellcraft brazier test: the braziers' sites east of the pit (their chunks generated now) each have an unlit bowl on
	 * a plinth.
	 */
	public static String selfTest(net.minecraft.server.level.ServerLevel level) {
		int found = 0;
		int built = 0;
		String where = "";
		for (int cx = Math.floorDiv(150, CELL); cx <= Math.floorDiv(380, CELL); cx++) {
			for (int cz = -2; cz <= 1; cz++) {
				int[] at = site(cx, cz);
				if (at == null) {
					continue;
				}
				found++;
				level.getChunk(at[0] >> 4, at[1] >> 4);
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at[0], at[1]);
				for (int dy = -3; dy <= 1; dy++) {
					net.minecraft.world.level.block.state.BlockState state = level.getBlockState(new BlockPos(at[0], y + dy, at[1]));
					if (state.is(Blocks.CAMPFIRE)) {
						built++;
						where = at[0] + " " + (y + dy) + " " + at[1];
						break;
					}
				}
			}
		}
		return (found > 0 && built == found ? "Brazier OK: " : "Brazier FAILED: ") + built + " of " + found + " sites east of the pit have one"
				+ (where.isEmpty() ? "" : " (one at " + where + ")");
	}

	private static void build(WorldGenLevel level, int x, int z) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		// a stepped plinth of blackstone, the bowl (an unlit campfire) on top, and a rim of slabs round its foot
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				level.setBlock(pos.set(x + dx, y - 1, z + dz), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 2);
				level.setBlock(pos.set(x + dx, y, z + dz), dx == 0 && dz == 0 ? Blocks.POLISHED_BLACKSTONE.defaultBlockState()
						: Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState(), 2);
				for (int up = 1; up <= 3; up++) {
					if (!level.getBlockState(pos.set(x + dx, y + up, z + dz)).isAir()) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
					}
				}
			}
		}
		level.setBlock(pos.set(x, y + 1, z), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false), 2);
	}
}
