package net.thesmallthings.hellcraft.hazard.lucifer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.minecraft.world.phys.AABB;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.world.InfernoGeometry;

import java.util.HashMap;
import java.util.Map;

/**
 * The pit at 0,0 and the ring of ice that seals it during the fight. The pit can't be dug out by hand
 * (ever), and during a fight whatever explosions and the Emperor tear out of it freezes back.
 */
final class LuciferArena {
	private LuciferArena() {
	}

	/** Players and targets count as "in the arena" inside this horizontal radius. */
	static final double RADIUS = 30.0;
	private static final double WALL_INNER = 31.0;
	private static final double WALL_OUTER = 33.0;
	private static final int WALL_HEIGHT = 9;
	/** The protected cylinder: the pit, its seal and its ice pillars. */
	private static final double PROTECTED_RADIUS = 34.0;
	private static final int PROTECTED_TOP = InfernoGeometry.PIT_FLOOR_Y + 24;

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

	/** The pit floor, its seal and its pillars can't be broken by hand (creative players excepted). */
	static boolean isProtected(BlockPos pos) {
		double x = pos.getX() + 0.5;
		double z = pos.getZ() + 0.5;
		return x * x + z * z <= PROTECTED_RADIUS * PROTECTED_RADIUS && pos.getY() <= PROTECTED_TOP;
	}

	/** Remembers every solid block of the pit so that {@link #heal} can put back what the fight destroys. */
	static Map<Long, BlockState> snapshot(ServerLevel level) {
		Map<Long, BlockState> blocks = new HashMap<>();
		int r = (int) PROTECTED_RADIUS;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				pos.set(x, PROTECTED_TOP, z);
				if (!isProtected(pos)) {
					continue;
				}
				for (int y = InfernoGeometry.PIT_FLOOR_Y - 8; y <= PROTECTED_TOP; y++) {
					pos.set(x, y, z);
					BlockState state = level.getBlockState(pos);
					if (!state.isAir() && !state.canBeReplaced()) {
						blocks.put(pos.asLong(), state);
					}
				}
			}
		}
		return blocks;
	}

	/** Refreezes every remembered block that was blown or broken away. Returns how many came back. */
	static int heal(ServerLevel level, Map<Long, BlockState> blocks) {
		int healed = 0;
		for (Map.Entry<Long, BlockState> e : blocks.entrySet()) {
			BlockPos pos = BlockPos.of(e.getKey());
			BlockState now = level.getBlockState(pos);
			if (now == e.getValue() || !(now.isAir() || now.canBeReplaced())) {
				continue;
			}
			if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(pos)).isEmpty()) {
				// never freeze anyone inside a block; try again next time
				continue;
			}
			level.setBlock(pos, e.getValue(), 3);
			if (healed++ < 24) {
				level.sendParticles(ParticleTypes.SNOWFLAKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 4, 0.3, 0.2, 0.3, 0.0);
			}
		}
		return healed;
	}
}
