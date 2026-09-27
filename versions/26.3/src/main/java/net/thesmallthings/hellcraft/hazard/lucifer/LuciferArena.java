package net.thesmallthings.hellcraft.hazard.lucifer;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.HellState;

/** The pit at 0,0 and the ring of ice that seals it during the fight. */
final class LuciferArena {
	private LuciferArena() {
	}

	/** Players and targets count as "in the arena" inside this horizontal radius. */
	static final double RADIUS = 30.0;
	private static final double WALL_INNER = 31.0;
	private static final double WALL_OUTER = 33.0;
	private static final int WALL_HEIGHT = 9;

	static int floorY(ServerLevel level) {
		level.getChunk(0, 0);
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
	}

	static boolean inside(double x, double z) {
		return x * x + z * z < RADIUS * RADIUS;
	}

	private static boolean wallColumn(int x, int z) {
		double d = Math.sqrt((x + 0.5) * (x + 0.5) + (z + 0.5) * (z + 0.5));
		return d >= WALL_INNER && d < WALL_OUTER;
	}

	static void seal(ServerLevel level) {
		int r = (int) WALL_OUTER + 1;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				if (!wallColumn(x, z)) {
					continue;
				}
				level.getChunk(x >> 4, z >> 4);
				int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				for (int y = ground; y < ground + WALL_HEIGHT; y++) {
					pos.set(x, y, z);
					BlockState state = level.getBlockState(pos);
					if (state.isAir() || state.canBeReplaced()) {
						level.setBlock(pos, Blocks.ICE.defaultBlockState(), 3);
					}
				}
			}
		}
		HellState state = HellState.get(level.getServer());
		state.arenaSealed = true;
		state.setDirty();
	}

	static void unseal(ServerLevel level) {
		int r = (int) WALL_OUTER + 1;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				if (!wallColumn(x, z)) {
					continue;
				}
				level.getChunk(x >> 4, z >> 4);
				for (int y = level.getMinY() + 5; y < 40; y++) {
					pos.set(x, y, z);
					BlockState state = level.getBlockState(pos);
					if (state.is(Blocks.ICE) || state.is(Blocks.WATER)) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
					}
				}
			}
		}
		HellState state = HellState.get(level.getServer());
		state.arenaSealed = false;
		state.setDirty();
		HellcraftMod.LOGGER.info("Arena unsealed");
	}

	/**
	 * Keeps (or stops keeping) the pit's chunks loaded. While he fights, Lucifer must never unload,
	 * e.g. when every player has died and respawned at the rim thousands of blocks away.
	 */
	static void forceLoad(ServerLevel level, boolean forced) {
		int c = ((int) WALL_OUTER >> 4) + 1;
		for (int cx = -c; cx < c; cx++) {
			for (int cz = -c; cz < c; cz++) {
				level.setChunkForced(cx, cz, forced);
			}
		}
	}

	/** True for the ice of the seal while a fight is on (it can't be broken). */
	static boolean isSealBlock(ServerLevel level, BlockPos pos) {
		return HellState.get(level.getServer()).arenaSealed && wallColumn(pos.getX(), pos.getZ()) && level.getBlockState(pos).is(Blocks.ICE);
	}
}
