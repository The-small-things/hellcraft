package net.thesmallthings.hellcraft.world.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.Zone;

/**
 * The four Pilgrim Roads (see {@link InfernoGeometry#ROAD_ANGLES}): paving in each circle's own stone over the
 * levelled ground, worn like a ruin: a stair where the ground steps up (most face up the slope, some lie askew, as
 * if the road had settled), so it is walked without jumping; here and there a stone sunk to a slab, cracked stones
 * and broken edges. Where one circle's stone gives way to the next the two are mixed over a dozen blocks, never a
 * straight seam. Plants and trees are cleared off, and a lantern post stands every {@link #LAMP_EVERY} blocks on
 * alternating sides. Runs late, after trees and the walls.
 */
public record PilgrimRoadFeature() implements Feature {
	public static final PilgrimRoadFeature INSTANCE = new PilgrimRoadFeature();
	public static final MapCodec<PilgrimRoadFeature> CODEC = MapCodec.unit(INSTANCE);

	public static final int LAMP_EVERY = 24;
	/** How far from the road's middle the lantern posts stand. */
	private static final int LAMP_SIDE = 4;

	/** Over how many blocks (either way) one circle's paving gives way to the next's. */
	private static final int BLEND = 7;

	/** What a stretch of road is made of: its paving, a worn stone for the cracks and edges, slab, stair, lamp post. */
	private record Look(Block paving, Block worn, Block slab, Block stair, Block post, Block lamp) {
	}

	private static Look look(Zone zone) {
		return switch (zone) {
			case DARK_WOOD -> new Look(Blocks.MOSSY_COBBLESTONE, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE_SLAB, Blocks.MOSSY_COBBLESTONE_STAIRS,
					Blocks.OAK_FENCE, Blocks.LANTERN);
			case VESTIBULE -> new Look(Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.COBBLESTONE_SLAB, Blocks.COBBLESTONE_STAIRS,
					Blocks.SPRUCE_FENCE, Blocks.LANTERN);
			case ACHERON, LIMBO -> new Look(Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.STONE_BRICK_SLAB, Blocks.STONE_BRICK_STAIRS,
					Blocks.BIRCH_FENCE, Blocks.LANTERN);
			case LUST -> new Look(Blocks.TUFF_BRICKS, Blocks.TUFF, Blocks.TUFF_BRICK_SLAB, Blocks.TUFF_BRICK_STAIRS, Blocks.TUFF_BRICK_WALL,
					Blocks.LANTERN);
			case GLUTTONY -> new Look(Blocks.MUD_BRICKS, Blocks.PACKED_MUD, Blocks.MUD_BRICK_SLAB, Blocks.MUD_BRICK_STAIRS, Blocks.MANGROVE_FENCE,
					Blocks.LANTERN);
			case GREED -> new Look(Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB,
					Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.POLISHED_BLACKSTONE_WALL, Blocks.LANTERN);
			case STYX -> new Look(Blocks.DARK_OAK_PLANKS, Blocks.STRIPPED_DARK_OAK_WOOD, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_STAIRS,
					Blocks.DARK_OAK_FENCE, Blocks.LANTERN);
			case WALLS_OF_DIS -> new Look(Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS,
					Blocks.POLISHED_BLACKSTONE_BRICK_SLAB, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.BLACKSTONE_WALL, Blocks.SOUL_LANTERN);
			case HERESY -> new Look(Blocks.DEEPSLATE_TILES, Blocks.CRACKED_DEEPSLATE_TILES, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DEEPSLATE_TILE_STAIRS,
					Blocks.BLACKSTONE_WALL, Blocks.SOUL_LANTERN);
			case PHLEGETHON -> new Look(Blocks.NETHER_BRICKS, Blocks.CRACKED_NETHER_BRICKS, Blocks.NETHER_BRICK_SLAB, Blocks.NETHER_BRICK_STAIRS,
					Blocks.NETHER_BRICK_FENCE, Blocks.LANTERN);
			case WOOD_OF_SUICIDES -> new Look(Blocks.BLACKSTONE, Blocks.GILDED_BLACKSTONE, Blocks.BLACKSTONE_SLAB, Blocks.BLACKSTONE_STAIRS,
					Blocks.DARK_OAK_FENCE, Blocks.SOUL_LANTERN);
			case BURNING_SANDS -> new Look(Blocks.SMOOTH_RED_SANDSTONE, Blocks.RED_SANDSTONE, Blocks.SMOOTH_RED_SANDSTONE_SLAB,
					Blocks.SMOOTH_RED_SANDSTONE_STAIRS, Blocks.RED_SANDSTONE_WALL, Blocks.LANTERN);
			default -> new Look(Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICK_SLAB, Blocks.DEEPSLATE_BRICK_STAIRS,
					Blocks.DEEPSLATE_BRICK_WALL, Blocks.SOUL_LANTERN);
		};
	}

	/**
	 * The look of the road at this block: its circle's, except near where the circle changes along the road, where each
	 * block takes the look a few blocks further along or back (a different amount for each block), so the two stones mix.
	 */
	private static Look lookAt(int x, int z) {
		Direction out = outward(x, z);
		int shift = Math.floorMod(hash(x, z) >>> 8, 2 * BLEND + 1) - BLEND;
		return look(InfernoGeometry.zoneAt(x + out.getStepX() * shift, z + out.getStepZ() * shift));
	}

	@Override
	public MapCodec<PilgrimRoadFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		int x0 = origin.getX() & ~15;
		int z0 = origin.getZ() & ~15;
		// the roads run along the axes: only chunks touching one need looking at
		if (Math.min(Math.abs(x0 + 8), Math.abs(z0 + 8)) > 8 + LAMP_SIDE + 8) {
			return false;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		boolean placed = false;
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = x0 + dx;
				int z = z0 + dz;
				if (InfernoGeometry.onRoad(x + 0.5, z + 0.5)) {
					placed |= pave(level, pos, x, z);
				} else if (lampHere(x, z)) {
					placed |= lamp(level, pos, x, z);
				}
			}
		}
		return placed;
	}

	private static boolean pave(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
		int y = ground(level, pos, x, z);
		if (y == Integer.MIN_VALUE) {
			return false;
		}
		Look look = lookAt(x, z);
		Direction out = outward(x, z);
		long h = hash(x, z);
		// where the road climbs a block, a stair on the lower stone, so it can be walked without jumping: most face up
		// the slope, but one in three has settled askew (still half a step on one side)
		int higherOut = ground(level, pos, x + out.getStepX(), z + out.getStepZ());
		int higherIn = ground(level, pos, x - out.getStepX(), z - out.getStepZ());
		Direction up = higherOut == y + 1 ? out : higherIn == y + 1 ? out.getOpposite() : null;
		// worn like a ruin: a stone here and there sunk to half its height, others cracked, and the edges broken
		boolean wet = InfernoGeometry.fluidAt(x, z) != InfernoGeometry.Fluid.NONE;
		boolean sunk = up == null && !wet && Math.floorMod(h, 14) == 0;
		// (the roads run along the axes: the distance from the road's middle is the other coordinate)
		double side = Math.abs((out.getStepX() != 0 ? z : x) + 0.5);
		boolean edge = side > InfernoGeometry.ROAD_HALF_WIDTH - 1.0;
		boolean cracked = Math.floorMod(h >>> 4, edge ? 3 : 9) == 0;
		BlockState paving = sunk ? look.slab.defaultBlockState() : (cracked ? look.worn : look.paving).defaultBlockState();
		level.setBlock(pos.set(x, y, z), paving, 2);
		for (int above = 1; above <= 4; above++) {
			pos.set(x, y + above, z);
			if (clearable(level.getBlockState(pos))) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
			}
		}
		if (up != null) {
			int skew = Math.floorMod(h >>> 12, 6);
			Direction facing = skew == 0 ? up.getClockWise() : skew == 1 ? up.getCounterClockWise() : up;
			level.setBlock(pos.set(x, y + 1, z), look.stair.defaultBlockState().setValue(StairBlock.FACING, facing), 2);
		}
		return true;
	}

	/** A hash of a column (the same on every server), for the worn stones and the mixing. */
	private static long hash(int x, int z) {
		long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return h;
	}

	private static boolean lampHere(int x, int z) {
		for (double angle : InfernoGeometry.ROAD_ANGLES) {
			int ax = (int) Math.round(Math.cos(angle));
			int az = (int) Math.round(Math.sin(angle));
			int along = x * ax + z * az;
			int side = x * -az + z * ax;
			if (along <= 0 || along % LAMP_EVERY != 0 || along > InfernoGeometry.roadOuterRadius()
					|| along < InfernoGeometry.roadInnerRadius()) {
				continue;
			}
			if (side == ((along / LAMP_EVERY) % 2 == 0 ? LAMP_SIDE : -LAMP_SIDE)) {
				return true;
			}
		}
		return false;
	}

	private static boolean lamp(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
		int y = ground(level, pos, x, z);
		if (y == Integer.MIN_VALUE || InfernoGeometry.fluidAt(x, z) != InfernoGeometry.Fluid.NONE) {
			return false;
		}
		Look look = lookAt(x, z);
		for (int up = 1; up <= 3; up++) {
			pos.set(x, y + up, z);
			if (!level.getBlockState(pos).isAir() && !clearable(level.getBlockState(pos))) {
				return false;
			}
		}
		level.setBlock(pos.set(x, y + 1, z), look.post.defaultBlockState(), 2);
		level.setBlock(pos.set(x, y + 2, z), look.post.defaultBlockState(), 2);
		level.setBlock(pos.set(x, y + 3, z), look.lamp.defaultBlockState(), 2);
		return true;
	}

	/** The road's direction away from the centre at this column. */
	private static Direction outward(int x, int z) {
		if (Math.abs(x) >= Math.abs(z)) {
			return x >= 0 ? Direction.EAST : Direction.WEST;
		}
		return z >= 0 ? Direction.SOUTH : Direction.NORTH;
	}

	/**
	 * The top block of the ground: the first solid one below the levelled height (vegetation, snow and the like
	 * don't count). Scanning starts just above the ground, under the lintel of any gate the road runs through.
	 */
	private static int ground(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
		int top = (int) Math.round(InfernoGeometry.baseHeight(x, z)) + 2;
		for (int y = top; y > top - 10; y--) {
			BlockState state = level.getBlockState(pos.set(x, y, z));
			if (!state.isAir() && !clearable(state) && state.getFluidState().isEmpty()) {
				return y;
			}
		}
		return Integer.MIN_VALUE;
	}

	/** Plants, leaves, logs and snow on the road are cleared away; anything built stays. */
	private static boolean clearable(BlockState state) {
		return state.canBeReplaced() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.is(BlockTags.FLOWERS)
				|| state.is(Blocks.SNOW) || state.is(Blocks.COBWEB) || state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.RED_MUSHROOM_BLOCK)
				|| state.is(Blocks.MUSHROOM_STEM);
	}
}
