package net.thesmallthings.hellcraft.world.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.storage.loot.LootTable;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.world.Fen;
import net.thesmallthings.hellcraft.world.InfernoGeometry;

/**
 * The Fen's watchtowers (L-V): a dark tower on the Styx's outer shore beside each road, a ladder up its inside to a
 * platform with two unlit beacons. "Two little flames we saw set there" (Inferno VIII): light them, and the ferryman
 * Phlegyas comes for you. A cache of supplies waits at the foot of the ladder (chests/fen_cache).
 */
public record WatchtowerFeature() implements Feature {
	public static final WatchtowerFeature INSTANCE = new WatchtowerFeature();
	public static final MapCodec<WatchtowerFeature> CODEC = MapCodec.unit(INSTANCE);
	public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, HellcraftMod.id("chests/fen_cache"));

	@Override
	public MapCodec<WatchtowerFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		int x0 = origin.getX() & ~15;
		int z0 = origin.getZ() & ~15;
		boolean placed = false;
		for (int k = 0; k < Fen.towers(); k++) {
			int[] t = Fen.tower(k);
			if (t[0] + Fen.HALF + 1 < x0 || t[0] - Fen.HALF - 1 > x0 + 15 || t[1] + Fen.HALF + 1 < z0 || t[1] - Fen.HALF - 1 > z0 + 15) {
				continue;
			}
			build(level, random, k, t, x0, z0);
			placed = true;
		}
		return placed;
	}

	/** The door faces the road; the ladder climbs the opposite wall. */
	public static Direction door(int k) {
		double a = InfernoGeometry.ROAD_ANGLES[k];
		// the tower stands to the road's right (walking in), so the road lies to its left
		return Direction.getApproximateNearest(Math.sin(a), 0, -Math.cos(a));
	}

	/** The tower's door step: the shore, or a block above the water. */
	public static int base(int[] t) {
		return Math.max((int) Math.round(InfernoGeometry.baseHeight(t[0], t[1])) + 1, InfernoGeometry.fluidLevel(t[0], t[1]) + 1);
	}

	private static void build(WorldGenLevel level, RandomSource random, int k, int[] t, int x0, int z0) {
		int y0 = base(t);
		int top = y0 + Fen.HEIGHT;
		Direction door = door(k);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -Fen.HALF - 1; dx <= Fen.HALF + 1; dx++) {
			for (int dz = -Fen.HALF - 1; dz <= Fen.HALF + 1; dz++) {
				int x = t[0] + dx;
				int z = t[1] + dz;
				if (x < x0 || x > x0 + 15 || z < z0 || z > z0 + 15) {
					continue;
				}
				boolean overhang = Math.abs(dx) == Fen.HALF + 1 || Math.abs(dz) == Fen.HALF + 1;
				boolean wall = !overhang && (Math.abs(dx) == Fen.HALF || Math.abs(dz) == Fen.HALF);
				if (!overhang) {
					// the foundation goes down to firm ground under the water
					int bottom = y0 - 2;
					while (bottom > level.getMinY() + 1 && !solidGround(level.getBlockState(pos.set(x, bottom - 1, z)))) {
						bottom--;
					}
					for (int y = bottom; y < y0; y++) {
						level.setBlock(pos.set(x, y, z), Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 2);
					}
					for (int y = y0; y < top; y++) {
						pos.set(x, y, z);
						if (wall) {
							boolean slit = (y - y0) % 6 == 4 && (dx == 0 || dz == 0);
							level.setBlock(pos, slit ? Blocks.AIR.defaultBlockState() : brick(random), 2);
						} else {
							level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
						}
					}
				}
				// the platform, wider than the tower, with a parapet round it
				level.setBlock(pos.set(x, top, z), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 2);
				if (overhang) {
					level.setBlock(pos.set(x, top - 1, z), Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState()
							.setValue(StairBlock.HALF, Half.TOP)
							.setValue(StairBlock.FACING, Direction.getApproximateNearest(-Integer.signum(dx), 0, -Integer.signum(dz))), 2);
					level.setBlock(pos.set(x, top + 1, z), ((dx + dz) & 1) == 0 ? Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
				} else {
					for (int y = top + 1; y < top + 4; y++) {
						level.setBlock(pos.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
					}
				}
			}
		}
		// the door, a lantern by it, the ladder up the far wall through a hatch in the platform
		BlockPos centre = new BlockPos(t[0], y0, t[1]);
		BlockPos doorway = centre.relative(door, Fen.HALF);
		setIfInChunk(level, x0, z0, doorway, Blocks.AIR.defaultBlockState());
		setIfInChunk(level, x0, z0, doorway.above(), Blocks.AIR.defaultBlockState());
		setIfInChunk(level, x0, z0, doorway.relative(door).above(2), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		BlockPos ladder = centre.relative(door.getOpposite(), Fen.HALF - 1);
		for (int y = 0; y <= Fen.HEIGHT; y++) {
			setIfInChunk(level, x0, z0, ladder.above(y), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, door));
		}
		BlockPos cache = centre.relative(door.getClockWise(), Fen.HALF - 1).relative(door.getOpposite(), Fen.HALF - 1);
		if (inChunk(x0, z0, cache)) {
			level.setBlock(cache, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, door), 2);
			RandomizableContainer.setBlockEntityLootTable(level, random, cache, LOOT);
		}
		// the twin beacons, unlit, each on a block of blackstone
		for (BlockPos b : Fen.beacons(k, top)) {
			setIfInChunk(level, x0, z0, b, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
		}
	}

	private static boolean solidGround(BlockState state) {
		return state.isSolid() && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS);
	}

	private static BlockState brick(RandomSource random) {
		int roll = random.nextInt(10);
		return roll == 0 ? Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState() : roll == 1 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
				: Blocks.DEEPSLATE_BRICKS.defaultBlockState();
	}

	private static boolean inChunk(int x0, int z0, BlockPos pos) {
		return pos.getX() >= x0 && pos.getX() <= x0 + 15 && pos.getZ() >= z0 && pos.getZ() <= z0 + 15;
	}

	private static void setIfInChunk(WorldGenLevel level, int x0, int z0, BlockPos pos, BlockState state) {
		if (inChunk(x0, z0, pos)) {
			level.setBlock(pos, state, 2);
		}
	}
}
