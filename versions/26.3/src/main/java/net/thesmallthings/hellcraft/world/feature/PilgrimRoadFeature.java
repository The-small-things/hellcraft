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
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.thesmallthings.hellcraft.block.HellBlocks;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.Zone;

/**
 * The four Pilgrim Roads (see {@link InfernoGeometry#ROAD_ANGLES}): paving in each circle's own stone over the
 * levelled ground, worn like a ruin: where the ground steps up, the top of the step breaks into a ragged band of
 * stairs and slabs in every turn and shape (most rising away from the lower ground), so it's walked without jumping;
 * on the flat, dents of rainwater (waterlogged stairs, slabs and the odd wall stub; dry rubble where Hell is too hot),
 * a stone here and there sunk to a slab, cracked stones and broken edges. Where one circle's stone gives way to the next the two are mixed over a dozen blocks, never a
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
		// the ground along the road, up to three blocks either way (0 here: the column itself)
		int[] along = new int[2 * STEP_REACH + 1];
		for (int d = -STEP_REACH; d <= STEP_REACH; d++) {
			along[d + STEP_REACH] = d == 0 ? y : ground(level, pos, x + out.getStepX() * d, z + out.getStepZ() * d);
		}
		// how far to the nearest lower stretch of road (along it or across it), and which way up the step climbs
		int lowerAt = 0;
		Direction upFromLower = null;
		for (int d = 1; d <= STEP_REACH && lowerAt == 0; d++) {
			for (Direction dir : FACINGS) {
				int nx = x + dir.getStepX() * d;
				int nz = z + dir.getStepZ() * d;
				int g = dir == out ? along[STEP_REACH + d] : dir == out.getOpposite() ? along[STEP_REACH - d]
						: InfernoGeometry.onRoad(nx + 0.5, nz + 0.5) ? ground(level, pos, nx, nz) : Integer.MIN_VALUE;
				if (g != Integer.MIN_VALUE && g < y) {
					lowerAt = d;
					// the lower road lies that way: the step climbs the other way
					upFromLower = dir.getOpposite();
					break;
				}
			}
		}
		int nextOut = along[STEP_REACH + 1];
		int nextIn = along[STEP_REACH - 1];
		boolean flat = nextOut == y && nextIn == y;
		boolean wetGround = InfernoGeometry.fluidAt(x, z) != InfernoGeometry.Fluid.NONE;
		// (the roads run along the axes: the distance from the road's middle is the other coordinate)
		double side = Math.abs((out.getStepX() != 0 ? z : x) + 0.5);
		boolean edge = side > InfernoGeometry.ROAD_HALF_WIDTH - 1.0;
		boolean cracked = Math.floorMod(h >>> 4, edge ? 3 : 9) == 0;

		BlockState top;
		if (lowerAt > 0 && roll(x, z, 1, 100) < BAND_CHANCE[lowerAt - 1]) {
			// the top of a step: its own stones broken into a ragged band of half steps, stairs and slabs every which way
			// (most still rise away from the lower ground), so it's walked without jumping, like an old worn stair
			boolean slab = roll(x, z, 2, 3) == 0;
			top = slab ? slab(look) : stair(look, x, z, roll(x, z, 3, 5) < 2 ? upFromLower : null);
			if (roll(x, z, 4, 10) == 0 && !hot(x, z) && mayHoldWater(level, pos, x, z, y)) {
				top = top.setValue(BlockStateProperties.WATERLOGGED, true);
			}
		} else if (flat && !edge && !wetGround && roll(x, z, 5, WATER_EVERY) == 0 && mayHoldWater(level, pos, x, z, y)) {
			// a dent worn in the paving, holding rainwater (or, where Hell is too hot for water, rubble)
			int kind = roll(x, z, 6, 16);
			top = kind < 2 ? slab(look) : kind == 2 && look.post instanceof WallBlock ? stub(look) : stair(look, x, z, null);
			if (!hot(x, z)) {
				top = top.setValue(BlockStateProperties.WATERLOGGED, true);
			}
		} else if (flat && !wetGround && Math.floorMod(h, SUNK_EVERY) == 0) {
			// a stone sunk to half its height
			top = slab(look);
		} else {
			top = (cracked ? look.worn : look.paving).defaultBlockState();
		}
		level.setBlock(pos.set(x, y, z), top, 2);
		for (int above = 1; above <= 4; above++) {
			pos.set(x, y + above, z);
			if (clearable(level.getBlockState(pos))) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
			}
		}
		// a step two blocks tall (rare on the levelled roads) still gets a stair below it, so it can be climbed
		Direction tall = nextOut >= y + 2 && nextOut != Integer.MIN_VALUE ? out : nextIn >= y + 2 && nextIn != Integer.MIN_VALUE ? out.getOpposite() : null;
		if (tall != null) {
			level.setBlock(pos.set(x, y + 1, z), look.stair.defaultBlockState().setValue(StairBlock.FACING, tall), 2);
		}
		return true;
	}

	/** How far back from a step's edge its top may break up into half steps, and how likely at each distance (%). */
	private static final int STEP_REACH = 3;
	private static final int[] BAND_CHANCE = {100, 55, 25};
	/** One flat stone in so many holds a dent of water. */
	private static final int WATER_EVERY = 20;
	/** One flat stone in so many has sunk to a slab. */
	private static final int SUNK_EVERY = 30;
	private static final StairsShape[] SHAPES = StairsShape.values();
	private static final Direction[] FACINGS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	/** A number in [0, n) for this column and purpose, the same on every server. */
	private static int roll(int x, int z, int salt, int n) {
		return (int) Math.floorMod(hash(x * 31 + salt * 7919, z * 17 - salt * 104729) >>> 7, (long) n);
	}

	/** The circle's stair at a random turn and shape (or facing {@code lean}, if given). */
	private static BlockState stair(Look look, int x, int z, Direction lean) {
		Direction facing = lean != null ? lean : FACINGS[roll(x, z, 7, 4)];
		return look.stair.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.SHAPE, SHAPES[roll(x, z, 8, SHAPES.length)]);
	}

	private static BlockState slab(Look look) {
		return look.slab.defaultBlockState();
	}

	/** A broken stub of the circle's wall, as in a gutter. */
	private static BlockState stub(Look look) {
		return look.post.defaultBlockState().setValue(WallBlock.UP, false).setValue(WallBlock.EAST, WallSide.TALL)
				.setValue(WallBlock.WEST, WallSide.TALL).setValue(WallBlock.NORTH, WallSide.TALL).setValue(WallBlock.SOUTH, WallSide.NONE);
	}

	/** Where water would make no sense: the Burning Sands and the river of boiling blood. */
	private static boolean hot(int x, int z) {
		Zone zone = InfernoGeometry.zoneAt(x, z);
		return zone == Zone.BURNING_SANDS || zone == Zone.PHLEGETHON;
	}

	/**
	 * True if water set in at (x, y, z) stays put: on the road, every block round it at that height is ground or road
	 * (never air it could spill into).
	 */
	private static boolean mayHoldWater(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int y) {
		if (hot(x, z)) {
			return true;
		}
		for (Direction d : FACINGS) {
			int nx = x + d.getStepX();
			int nz = z + d.getStepZ();
			if (!InfernoGeometry.onRoad(nx + 0.5, nz + 0.5)) {
				return false;
			}
			int g = ground(level, pos, nx, nz);
			if (g == Integer.MIN_VALUE || g < y) {
				return false;
			}
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
				|| state.is(Blocks.MUSHROOM_STEM) || state.is(Blocks.MANGROVE_ROOTS) || state.is(HellBlocks.THORNS);
	}
}
