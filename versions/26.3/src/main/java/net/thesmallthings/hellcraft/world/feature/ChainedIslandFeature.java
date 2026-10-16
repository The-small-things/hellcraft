package net.thesmallthings.hellcraft.world.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.thesmallthings.hellcraft.world.ForgeGeometry;

import java.util.ArrayList;
import java.util.List;

/**
 * The Chainlands' chains and bridges (Phase 6). Each island (ForgeGeometry.island) hangs from the roof on four great
 * chains, and a sagging bridge of blackstone slabs with chain railings runs from each island to its neighbours east
 * and south (or, at the region's edge, to the land there). Each chunk builds the parts of them that fall inside it,
 * all worked out from the geometry, so they join up across chunks.
 */
public record ChainedIslandFeature() implements Feature {
	public static final ChainedIslandFeature INSTANCE = new ChainedIslandFeature();
	public static final MapCodec<ChainedIslandFeature> CODEC = MapCodec.unit(INSTANCE);
	private static final double CELL = ForgeGeometry.ISLAND_CELL;

	@Override
	public MapCodec<ChainedIslandFeature> codec() {
		return CODEC;
	}

	/** A bridge: from (x0, z0) at surface y0 to (x1, z1) at y1. */
	public record Bridge(double x0, double z0, double y0, double x1, double z1, double y1) {
		double length() {
			return Math.hypot(x1 - x0, z1 - z0);
		}

		/** The walking surface at a fraction of the way along: a straight line sagging a little in the middle. */
		double surface(double t) {
			double sag = Math.min(3.0, length() / 14.0);
			return y0 + (y1 - y0) * t - sag * 4 * t * (1 - t);
		}
	}

	static boolean inChainlands(double x, double z) {
		return ForgeGeometry.weights(x, z)[ForgeGeometry.Region.CHAINLANDS.ordinal()] > 0.5;
	}

	/** The bridges that leave island (i, j): to the islands of the cells east and south, or to the land there. */
	public static List<Bridge> bridges(int i, int j) {
		List<Bridge> out = new ArrayList<>();
		double[] a = ForgeGeometry.island(i, j);
		if (a == null || !inChainlands(a[0], a[1])) {
			return out;
		}
		for (int[] n : new int[][]{{i + 1, j}, {i, j + 1}}) {
			double[] b = ForgeGeometry.island(n[0], n[1]);
			double bx;
			double bz;
			double by;
			double br;
			if (b != null && inChainlands(b[0], b[1])) {
				bx = b[0];
				bz = b[1];
				by = b[2];
				br = b[3];
			} else {
				// the edge of the Chainlands: run to the land at the neighbour cell's middle, if there is land there
				bx = (n[0] + 0.5) * CELL;
				bz = (n[1] + 0.5) * CELL;
				if (inChainlands(bx, bz)) {
					continue;
				}
				int ground = ForgeGeometry.groundBelow((int) bx, 100, (int) bz);
				if (ground <= ForgeGeometry.LAVA + 1) {
					continue;
				}
				by = ground;
				br = 3;
			}
			double d = Math.hypot(bx - a[0], bz - a[1]);
			double ux = (bx - a[0]) / d;
			double uz = (bz - a[1]) / d;
			// from just inside one rim to just inside the other, so the ends sit on rock
			double sx = a[0] + ux * (a[3] - 3);
			double sz = a[1] + uz * (a[3] - 3);
			double ex = bx - ux * (br - 3);
			double ez = bz - uz * (br - 3);
			out.add(new Bridge(sx, sz, a[2], ex, ez, by));
		}
		return out;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		int x0 = origin.getX() & ~15;
		int z0 = origin.getZ() & ~15;
		int ci = (int) Math.floor(x0 / CELL);
		int cj = (int) Math.floor(z0 / CELL);
		boolean placed = false;
		for (int i = ci - 2; i <= ci + 2; i++) {
			for (int j = cj - 2; j <= cj + 2; j++) {
				double[] is = ForgeGeometry.island(i, j);
				if (is != null && inChainlands(is[0], is[1])) {
					placed |= chains(level, is, x0, z0);
				}
				for (Bridge b : bridges(i, j)) {
					placed |= bridge(level, b, x0, z0);
				}
			}
		}
		return placed;
	}

	private static boolean inChunk(int x, int z, int x0, int z0) {
		return x >= x0 && x < x0 + 16 && z >= z0 && z < z0 + 16;
	}

	/** Four great chains from the island's top up to the roof, each anchored in a block of blackstone. */
	private static boolean chains(WorldGenLevel level, double[] is, int x0, int z0) {
		boolean placed = false;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (Direction d : Direction.Plane.HORIZONTAL) {
			int x = (int) Math.floor(is[0] + d.getStepX() * is[3] * 0.55);
			int z = (int) Math.floor(is[1] + d.getStepZ() * is[3] * 0.55);
			if (!inChunk(x, z, x0, z0)) {
				continue;
			}
			// up from the island's surface until the roof
			int y = (int) is[2] - 3;
			while (y < 120 && level.getBlockState(pos.set(x, y, z)).isSolid()) {
				y++;
			}
			level.setBlock(pos.set(x, y, z), Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), 2);
			for (y++; y < 124 && level.getBlockState(pos.set(x, y, z)).isAir(); y++) {
				level.setBlock(pos, Blocks.IRON_CHAIN.defaultBlockState(), 2);
			}
			placed = true;
		}
		return placed;
	}

	/** The bridge's deck, two blocks wide, in half-block steps, with chain railings and a lantern at the middle. */
	private static boolean bridge(WorldGenLevel level, Bridge b, int x0, int z0) {
		double len = b.length();
		double ux = (b.x1() - b.x0()) / len;
		double uz = (b.z1() - b.z0()) / len;
		// the side step: one block across the bridge's main direction
		boolean alongX = Math.abs(ux) >= Math.abs(uz);
		int sx = alongX ? 0 : 1;
		int sz = alongX ? 1 : 0;
		BlockState full = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		BlockState slab = Blocks.POLISHED_BLACKSTONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
		BlockState rail = Blocks.IRON_CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, alongX ? Direction.Axis.X : Direction.Axis.Z);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		boolean placed = false;
		int steps = (int) Math.ceil(len * 2);
		for (int k = 0; k <= steps; k++) {
			double t = k / (double) steps;
			int x = (int) Math.floor(b.x0() + (b.x1() - b.x0()) * t);
			int z = (int) Math.floor(b.z0() + (b.z1() - b.z0()) * t);
			// the walking surface, to the half block: a whole number stands on a full block, a half on a slab
			double surface = Math.round(b.surface(t) * 2) / 2.0;
			int base = (int) Math.floor(surface);
			boolean half = surface - base > 0.25;
			for (int w = -1; w <= 2; w++) {
				int px = x + sx * w;
				int pz = z + sz * w;
				if (!inChunk(px, pz, x0, z0)) {
					continue;
				}
				if (w == -1 || w == 2) {
					// railings on both sides, a block above the deck
					int ry = half ? base + 1 : base;
					if (level.getBlockState(pos.set(px, ry, pz)).isAir()) {
						level.setBlock(pos, rail, 2);
					}
					continue;
				}
				if (half) {
					level.setBlock(pos.set(px, base, pz), slab, 2);
				} else {
					level.setBlock(pos.set(px, base - 1, pz), full, 2);
				}
				// clear the way above the deck
				for (int up = half ? 1 : 0; up <= 2; up++) {
					BlockState above = level.getBlockState(pos.set(px, base + up, pz));
					if (!above.isAir() && !above.is(Blocks.IRON_CHAIN)) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
					}
				}
				placed = true;
			}
			// a lantern hanging under the middle of the deck
			if (k == steps / 2 && inChunk(x, z, x0, z0)) {
				level.setBlock(pos.set(x, base - 2, z), Blocks.IRON_CHAIN.defaultBlockState(), 2);
				level.setBlock(pos.set(x, base - 3, z), Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);
			}
		}
		return placed;
	}
}
