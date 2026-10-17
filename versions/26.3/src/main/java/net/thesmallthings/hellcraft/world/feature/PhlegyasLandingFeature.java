package net.thesmallthings.hellcraft.world.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.thesmallthings.hellcraft.world.Fen;
import net.thesmallthings.hellcraft.world.InfernoGeometry;

/**
 * Phlegyas's landing in the Fen (Fen.landingAt): a ring of planks on the water round a deep pool, railed with posts and
 * lanterns, and a jetty out from the east road. He rises from the pool when the watchtower's beacons are lit; the
 * planks keep the sullen's hands off you, but the water beyond the rail is theirs.
 */
public record PhlegyasLandingFeature() implements Feature {
	public static final PhlegyasLandingFeature INSTANCE = new PhlegyasLandingFeature();
	public static final MapCodec<PhlegyasLandingFeature> CODEC = MapCodec.unit(INSTANCE);

	@Override
	public MapCodec<PhlegyasLandingFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		int x0 = origin.getX() & ~15;
		int z0 = origin.getZ() & ~15;
		// the landing (and its jetty back to the road) this chunk may hold
		int[] landing = null;
		for (int k = 0; k < Fen.towers() && landing == null; k++) {
			int[] c = Fen.landing(k);
			double reach = Fen.LANDING + 48;
			if (x0 + 15 >= c[0] - reach && x0 <= c[0] + reach && z0 + 15 >= c[1] - reach && z0 <= c[1] + reach) {
				landing = c;
			}
		}
		if (landing == null) {
			return false;
		}
		int water = InfernoGeometry.fluidLevel(landing[0], landing[1]);
		int deck = water - 1;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		boolean placed = false;
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = x0 + dx;
				int z = z0 + dz;
				Fen.Landing part = Fen.landingAt(x + 0.5, z + 0.5);
				if (part == Fen.Landing.NONE) {
					continue;
				}
				placed = true;
				if (part == Fen.Landing.POOL) {
					// deep water, down to a muddy bottom
					for (int y = deck - 3; y <= deck; y++) {
						level.setBlock(pos.set(x, y, z), y == deck - 3 ? Blocks.MUD.defaultBlockState() : Blocks.WATER.defaultBlockState(), 2);
					}
					clearAbove(level, pos, x, deck + 1, z);
					continue;
				}
				// planks on the water (or on the ground where an island rises), propped on posts below
				BlockState plank = (x + z) % 7 == 0 ? Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X)
						: Blocks.SPRUCE_PLANKS.defaultBlockState();
				level.setBlock(pos.set(x, deck, z), plank, 2);
				clearAbove(level, pos, x, deck + 1, z);
				if (Math.floorMod(x, 4) == 0 && Math.floorMod(z, 4) == 0) {
					for (int y = deck - 1; y > deck - 8 && !level.getBlockState(pos.set(x, y, z)).isSolid(); y--) {
						level.setBlock(pos, Blocks.SPRUCE_LOG.defaultBlockState(), 2);
					}
				}
				if (part == Fen.Landing.RAIL) {
					boolean post = Math.floorMod(x * 3 + z * 5, 9) == 0;
					level.setBlock(pos.set(x, deck + 1, z), Blocks.SPRUCE_FENCE.defaultBlockState(), 2);
					if (post) {
						level.setBlock(pos.set(x, deck + 2, z), Blocks.SPRUCE_FENCE.defaultBlockState(), 2);
						level.setBlock(pos.set(x, deck + 3, z), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false), 2);
					}
				}
			}
		}
		return placed;
	}

	private static void clearAbove(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int from, int z) {
		for (int y = from; y < from + 12; y++) {
			pos.set(x, y, z);
			if (!level.getBlockState(pos).isAir()) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
			}
		}
	}
}
