package net.thesmallthings.hellcraft.world.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.NobleCastle;

/**
 * Builds Limbo's Noble Castle (NobleCastle), chunk by chunk: seven tiers of pale stone walls with a gate on each
 * axis, round towers on the innermost and outermost walls, the rivulet around it, paths from gate to gate, and at
 * the heart a grassy mound under a quartz pavilion full of light. The east road runs through on its own paving.
 */
public record NobleCastleFeature() implements Feature {
	public static final NobleCastleFeature INSTANCE = new NobleCastleFeature();
	public static final MapCodec<NobleCastleFeature> CODEC = MapCodec.unit(INSTANCE);

	/** The pavilion's roof stands this high over the castle's ground; its pillars stand at +-PILLAR. */
	private static final int ROOF = 9;
	private static final int PILLAR = 3;
	private static final int ROOF_HALF = 4;

	@Override
	public MapCodec<NobleCastleFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		int x0 = origin.getX() & ~15;
		int z0 = origin.getZ() & ~15;
		double reach = NobleCastle.STREAM_OUTER + 2;
		double cx = Math.max(x0, Math.min(x0 + 15, NobleCastle.X));
		double cz = Math.max(z0, Math.min(z0 + 15, NobleCastle.Z));
		if (NobleCastle.distance(cx, cz) > reach) {
			return false;
		}
		int base = (int) Math.round(InfernoGeometry.baseHeight(NobleCastle.X, NobleCastle.Z));
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		boolean placed = false;
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = x0 + dx;
				int z = z0 + dz;
				NobleCastle.Part part = NobleCastle.partAt(x + 0.5, z + 0.5);
				if (part == NobleCastle.Part.NONE) {
					continue;
				}
				int ground = groundAt(level, x, z);
				switch (part) {
					case MEADOW -> meadow(level, pos, x, ground, z);
					case PATH -> path(level, pos, x, ground, z);
					case WALL -> wall(level, random, pos, x, ground, z, NobleCastle.wallAt(x + 0.5, z + 0.5), false);
					case GATE -> wall(level, random, pos, x, ground, z, NobleCastle.wallAt(x + 0.5, z + 0.5), true);
					case TOWER -> tower(level, random, pos, x, ground, z);
					case STREAM -> stream(level, pos, x, ground, z);
					case MOUND -> mound(level, pos, x, ground, z, base);
					default -> {
					}
				}
				clearTreesAbove(level, pos, x, z, ground + 1);
				placed = true;
			}
		}
		return placed;
	}

	/** Grass on the top block (the castle's biome makes it green). */
	private static void meadow(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int ground, int z) {
		pos.set(x, ground - 1, z);
		if (level.getBlockState(pos).is(BlockTags.DIRT)) {
			level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
		}
	}

	/** Gravel paths from gate to gate along the axes; the east road (along z = 0) is paved by the road itself. */
	private static void path(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int ground, int z) {
		if (Math.abs(z + 0.5 - NobleCastle.Z) <= NobleCastle.GATE_HALF_WIDTH) {
			return;
		}
		pos.set(x, ground - 1, z);
		level.setBlock(pos, Blocks.DIRT_PATH.defaultBlockState(), 2);
		pos.set(x, ground, z);
		if (level.getBlockState(pos).canBeReplaced()) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
	}

	private static BlockState brick(RandomSource random) {
		int roll = random.nextInt(12);
		if (roll == 0) {
			return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
		}
		if (roll == 1) {
			return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
		}
		return Blocks.STONE_BRICKS.defaultBlockState();
	}

	/** A wall column (or a gate's: open below GATE_HEIGHT under a chiselled lintel), crenellated on top. */
	private static void wall(WorldGenLevel level, RandomSource random, BlockPos.MutableBlockPos pos, int x, int ground, int z, int wall, boolean gate) {
		int top = ground + NobleCastle.wallHeight(Math.max(0, wall));
		for (int y = ground - 3; y < top; y++) {
			pos.set(x, y, z);
			if (gate && y >= ground && y < ground + NobleCastle.GATE_HEIGHT) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
			} else if (gate && y == ground + NobleCastle.GATE_HEIGHT) {
				level.setBlock(pos, Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 2);
			} else if (gate && y == ground - 1 && Math.abs(z + 0.5 - NobleCastle.Z) > NobleCastle.GATE_HALF_WIDTH) {
				level.setBlock(pos, Blocks.DIRT_PATH.defaultBlockState(), 2);
			} else if (!(gate && y == ground - 1)) {
				level.setBlock(pos, brick(random), 2);
			}
		}
		if (((x + z) & 1) == 0) {
			pos.set(x, top, z);
			level.setBlock(pos, Blocks.STONE_BRICK_SLAB.defaultBlockState(), 2);
		}
	}

	/** A round tower, three blocks above its wall, with a lantern burning on top at its heart. */
	private static void tower(WorldGenLevel level, RandomSource random, BlockPos.MutableBlockPos pos, int x, int ground, int z) {
		double d = NobleCastle.distance(x + 0.5, z + 0.5);
		int wall = d < (NobleCastle.radius(0) + NobleCastle.radius(NobleCastle.WALLS - 1)) / 2 ? 0 : NobleCastle.WALLS - 1;
		int top = ground + NobleCastle.wallHeight(wall) + 3;
		for (int y = ground - 3; y < top; y++) {
			pos.set(x, y, z);
			level.setBlock(pos, brick(random), 2);
		}
		pos.set(x, top, z);
		level.setBlock(pos, ((x + z) & 1) == 0 ? Blocks.STONE_BRICK_WALL.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
		if (towerHeart(x, z, wall)) {
			level.setBlock(pos.set(x, top, z), Blocks.LANTERN.defaultBlockState(), 2);
		}
	}

	/** The column nearest a tower's centre. */
	private static boolean towerHeart(int x, int z, int wall) {
		double r = NobleCastle.radius(wall);
		for (int q = 0; q < 4; q++) {
			double a = Math.PI / 4 + q * Math.PI / 2;
			int tx = (int) Math.floor(NobleCastle.X + Math.cos(a) * r);
			int tz = (int) Math.floor(NobleCastle.Z + Math.sin(a) * r);
			if (x == tx && z == tz) {
				return true;
			}
		}
		return false;
	}

	/** The fair rivulet: a block of water over clay, its banks grassed. */
	private static void stream(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int ground, int z) {
		pos.set(x, ground - 2, z);
		level.setBlock(pos, Blocks.CLAY.defaultBlockState(), 2);
		pos.set(x, ground - 1, z);
		level.setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
		for (int up = 0; up < 3; up++) {
			pos.set(x, ground + up, z);
			if (level.getBlockState(pos).canBeReplaced()) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
			}
		}
	}

	/**
	 * The luminous mound at the heart: a low dome of grass with steps of path up from each axis, and over it a
	 * pavilion of quartz pillars under a roof hung with froglights.
	 */
	private static void mound(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int ground, int z, int base) {
		double ox = x + 0.5 - NobleCastle.X;
		double oz = z + 0.5 - NobleCastle.Z;
		double d = Math.hypot(ox, oz);
		int rise = (int) Math.round(2.0 * Math.sqrt(Math.max(0, 1 - (d / NobleCastle.MOUND) * (d / NobleCastle.MOUND))));
		for (int up = 0; up < rise; up++) {
			pos.set(x, ground + up - 1, z);
			level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 2);
		}
		boolean axis = Math.abs(ox) < 1.5 || Math.abs(oz) < 1.5;
		pos.set(x, ground + rise - 1, z);
		level.setBlock(pos, axis ? Blocks.DIRT_PATH.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState(), 2);
		int floor = ground + rise;
		for (int y = floor; y < base + ROOF + 2; y++) {
			pos.set(x, y, z);
			if (!level.getBlockState(pos).isAir()) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
			}
		}
		int ax = (int) Math.floor(ox);
		int az = (int) Math.floor(oz);
		int roof = base + ROOF;
		if (Math.abs(ax + 0.5) == PILLAR + 0.5 && Math.abs(az + 0.5) == PILLAR + 0.5) {
			for (int y = floor; y < roof; y++) {
				pos.set(x, y, z);
				level.setBlock(pos, Blocks.QUARTZ_PILLAR.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y), 2);
			}
		}
		if (Math.abs(ax + 0.5) <= ROOF_HALF + 0.5 && Math.abs(az + 0.5) <= ROOF_HALF + 0.5) {
			boolean edge = Math.abs(ax + 0.5) == ROOF_HALF + 0.5 || Math.abs(az + 0.5) == ROOF_HALF + 0.5;
			pos.set(x, roof, z);
			level.setBlock(pos, edge ? Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM)
					: Blocks.SMOOTH_QUARTZ.defaultBlockState(), 2);
			if (!edge && (ax + az) % 2 == 0 && Math.abs(ax + 0.5) < ROOF_HALF && Math.abs(az + 0.5) < ROOF_HALF) {
				pos.set(x, roof - 1, z);
				level.setBlock(pos, Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState(), 2);
			}
		}
		// lanterns ring the foot of the mound
		if (Math.abs(d - (NobleCastle.MOUND - 0.7)) < 0.5 && !axis && (Math.floorMod(ax * 3 + az * 5, 7) == 0)) {
			pos.set(x, floor, z);
			level.setBlock(pos, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false), 2);
		}
	}

	/** Solid ground under any trees or plants: the first block of air above it. */
	private static int groundAt(WorldGenLevel level, int x, int z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1, z);
		while (pos.getY() > level.getMinY() + 5) {
			BlockState state = level.getBlockState(pos);
			boolean plant = state.isAir() || state.canBeReplaced() || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)
					|| !state.getFluidState().isEmpty() || state.is(BlockTags.FLOWERS);
			if (!plant) {
				break;
			}
			pos.move(0, -1, 0);
		}
		return pos.getY() + 1;
	}

	private static void clearTreesAbove(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int from) {
		for (int y = from; y < from + 24; y++) {
			pos.set(x, y, z);
			BlockState state = level.getBlockState(pos);
			if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
			}
		}
	}
}
