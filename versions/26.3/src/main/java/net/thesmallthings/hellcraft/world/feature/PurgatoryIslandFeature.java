package net.thesmallthings.hellcraft.world.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.thesmallthings.hellcraft.world.Purgatory;

/**
 * The Mountain of Purgatory's rock and its beach (world/Purgatory), built chunk by chunk as the dimension's chunks are
 * made, so the game lights them as it goes: the beach running down into the sea, and the mountain's seven terraces
 * stepping up to the summit, each column solid from the sea floor, each terrace dressed for its sin ({@link #decorate}).
 * Purgatory.buildOnce adds the stairs, signs and garden.
 */
public record PurgatoryIslandFeature() implements Feature {
	public static final PurgatoryIslandFeature INSTANCE = new PurgatoryIslandFeature();
	public static final MapCodec<PurgatoryIslandFeature> CODEC = MapCodec.unit(INSTANCE);

	@Override
	public MapCodec<PurgatoryIslandFeature> codec() {
		return CODEC;
	}

	/** A number in [0, n) for this column and purpose, the same on every server. */
	private static int roll(int x, int z, int salt, int n) {
		long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ salt * 0x632BE59BD9B4E019L;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return (int) Math.floorMod(h, (long) n);
	}

	/**
	 * Each terrace shows its sin, after Dante: Pride's souls bowed under boulders below carved reliefs; Envy's livid
	 * stone with seats along the cliff and chains hanging down it; Wrath's smoke; Sloth's running track round the
	 * mountain; Avarice's dust; Gluttony's trees with their fruit out of reach; Lust's wall of fire along the cliff.
	 * Decorations keep off the ways up and leave the outer edge of every terrace clear to walk.
	 *
	 * @param d how far this column is from the cliff at the back of its terrace (0 at its foot)
	 */
	private static void decorate(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, double r, int k, int top, int x0, int z0) {
		if (Purgatory.onAWayUp(x, z, k)) {
			return;
		}
		double d = r - Purgatory.cliff(k);
		if (d > Purgatory.TERRACE_WIDTH - 2.5) {
			// the outer edge of every terrace stays clear to walk round
			return;
		}
		int y = top + 1;
		switch (k) {
			case 1 -> {
				// Pride: boulders the proud souls bow under, and reliefs carved in the cliff above
				if (d > 2 && roll(x, z, 1, 30) == 0) {
					int h = 1 + roll(x, z, 2, 2);
					for (int i = 0; i < h; i++) {
						level.setBlock(pos.set(x, y + i, z), (roll(x, z, 3 + i, 3) == 0 ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE).defaultBlockState(), 2);
					}
				}
				if (d < 1 && roll(x, z, 4, 6) == 0) {
					// (the cliff face behind: the next terrace's outer column, a block in)
					int cx = (int) Math.round(x - x / r);
					int cz = (int) Math.round(z - z / r);
					if (sameChunk(cx, cz, x0, z0)) {
						for (int i = 2; i <= 4; i++) {
							level.setBlock(pos.set(cx, top + i, cz), (i == 3 ? Blocks.CHISELED_STONE_BRICKS : Blocks.SMOOTH_QUARTZ).defaultBlockState(), 2);
						}
					}
				}
			}
			case 2 -> {
				// Envy: the livid stone they sit on along the cliff, eyes sewn shut, and chains hanging down it
				if (d < 1.5 && roll(x, z, 5, 5) == 0) {
					level.setBlock(pos.set(x, y, z), Blocks.POLISHED_TUFF_SLAB.defaultBlockState(), 2);
				} else if (d < 1 && roll(x, z, 6, 9) == 0) {
					for (int i = 2; i <= 6; i++) {
						level.setBlock(pos.set(x, top + i, z), Blocks.IRON_CHAIN.defaultBlockState(), 2);
					}
				}
				if (d >= 1.5 && roll(x, z, 7, 4) == 0) {
					level.setBlock(pos.set(x, top, z), Blocks.POLISHED_TUFF.defaultBlockState(), 2);
				}
			}
			case 3 -> {
				// Wrath: smoke, from fires along the foot of the cliff, and the floor blackened round them
				if (d < 1.5 && roll(x, z, 8, 14) == 0) {
					level.setBlock(pos.set(x, top, z), Blocks.HAY_BLOCK.defaultBlockState(), 2);
					level.setBlock(pos.set(x, y, z), Blocks.CAMPFIRE.defaultBlockState(), 2);
				} else if (d < 4 && roll(x, z, 9, 5) == 0) {
					level.setBlock(pos.set(x, top, z), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 2);
				}
			}
			case 4 -> {
				// Sloth: a running track round the mountain, and lantern posts to run by
				if (d >= 4 && d < 6) {
					level.setBlock(pos.set(x, top, z), Blocks.POLISHED_ANDESITE.defaultBlockState(), 2);
				} else if (d < 1 && roll(x, z, 10, 14) == 0) {
					level.setBlock(pos.set(x, y, z), Blocks.STONE_BRICK_WALL.defaultBlockState(), 2);
					level.setBlock(pos.set(x, y + 1, z), Blocks.LANTERN.defaultBlockState(), 2);
				}
			}
			case 5 -> {
				// Avarice: lying face down in the dust
				if (roll(x, z, 11, 4) == 0) {
					level.setBlock(pos.set(x, top, z), (roll(x, z, 12, 3) == 0 ? Blocks.GRAVEL : Blocks.COARSE_DIRT).defaultBlockState(), 2);
					if (roll(x, z, 13, 5) == 0) {
						level.setBlock(pos.set(x, y, z), Blocks.DEAD_BUSH.defaultBlockState(), 2);
					}
				}
			}
			case 6 -> {
				// Gluttony: tall trees with their blossom (the fruit) far out of reach, on a patch of grass
				if (d > 3 && d < 7 && roll(x, z, 14, 70) == 0 && sameChunk(x - 2, z - 2, x0, z0) && sameChunk(x + 2, z + 2, x0, z0)) {
					for (int dx = -1; dx <= 1; dx++) {
						for (int dz = -1; dz <= 1; dz++) {
							level.setBlock(pos.set(x + dx, top, z + dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
						}
					}
					int trunk = 7;
					for (int i = 0; i < trunk; i++) {
						level.setBlock(pos.set(x, y + i, z), Blocks.OAK_LOG.defaultBlockState(), 2);
					}
					for (int dx = -2; dx <= 2; dx++) {
						for (int dz = -2; dz <= 2; dz++) {
							for (int dy = trunk - 1; dy <= trunk + 1; dy++) {
								if (dx * dx + dz * dz + (dy - trunk) * (dy - trunk) <= 5 && !(dx == 0 && dz == 0 && dy < trunk)) {
									level.setBlock(pos.set(x + dx, y + dy, z + dz), Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState()
											.setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true), 2);
								}
							}
						}
					}
				}
			}
			case 7 -> {
				// Lust: a wall of fire along the foot of the cliff (the path beside it is clear)
				if (d < 1.5) {
					level.setBlock(pos.set(x, top, z), Blocks.NETHERRACK.defaultBlockState(), 2);
					level.setBlock(pos.set(x, y, z), Blocks.FIRE.defaultBlockState(), 2);
				}
			}
			default -> {
			}
		}
	}

	private static boolean sameChunk(int x, int z, int x0, int z0) {
		return x >= x0 && x < x0 + 16 && z >= z0 && z < z0 + 16;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource ignored, BlockPos origin) {
		int x0 = origin.getX() & ~15;
		int z0 = origin.getZ() & ~15;
		int reach = Purgatory.BEACH_RADIUS;
		if (x0 > reach || x0 + 15 < -reach || z0 > reach || z0 + 15 < -reach) {
			return false;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		boolean placed = false;
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = x0 + dx;
				int z = z0 + dz;
				double r = Math.sqrt((double) x * x + (double) z * z);
				int beach = Purgatory.beachY(r);
				if (beach != Integer.MIN_VALUE) {
					for (int y = Purgatory.SEA_FLOOR; y <= beach; y++) {
						level.setBlock(pos.set(x, y, z), y >= beach - 1 ? Blocks.SAND.defaultBlockState() : Blocks.SANDSTONE.defaultBlockState(), 2);
					}
					placed = true;
					continue;
				}
				int k = Purgatory.ring(r);
				if (k < 0) {
					continue;
				}
				int top = Purgatory.topY(r);
				// the same stones on every server: each column's own random
				RandomSource random = RandomSource.create(BlockPos.asLong(x, 0, z) * 0x9E3779B97F4A7C15L + 1300L);
				for (int y = Purgatory.SEA_FLOOR; y <= top; y++) {
					// (each terrace's own floor and the lip of its cliff; elsewhere the rock's stone)
					net.minecraft.world.level.block.state.BlockState floor = Purgatory.floor(k, x, z, r, y, top);
					level.setBlock(pos.set(x, y, z), floor != null ? floor : Purgatory.material(k, y, top, random), 2);
				}
				placed = true;
			}
		}
		// then each terrace's dressing, over the finished rock (some of it reaches into the cliff behind)
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = x0 + dx;
				int z = z0 + dz;
				double r = Math.sqrt((double) x * x + (double) z * z);
				int k = Purgatory.ring(r);
				if (k >= 1 && k <= Purgatory.TERRACES && Purgatory.beachY(r) == Integer.MIN_VALUE) {
					decorate(level, pos, x, z, r, k, Purgatory.topY(r), x0, z0);
				}
			}
		}
		return placed;
	}
}
