package net.thesmallthings.hellcraft.world.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * The Burning Sands' shelters (L-VII): wind-carved rock tables standing on two or three legs, their roofs wide enough
 * to shelter a few souls when the fire falls (LowerRules). One stands in most chunks, so cover is never far.
 */
public record SandRockFeature() implements Feature {
	public static final SandRockFeature INSTANCE = new SandRockFeature();
	public static final MapCodec<SandRockFeature> CODEC = MapCodec.unit(INSTANCE);

	@Override
	public MapCodec<SandRockFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		if (!level.getBlockState(origin.below()).isSolid() || !level.getFluidState(origin).isEmpty()) {
			return false;
		}
		double rx = 3.5 + random.nextDouble() * 1.5;
		double rz = 3.5 + random.nextDouble() * 1.5;
		int clearance = 4 + random.nextInt(2);
		int thick = 2 + random.nextInt(2);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		// the roof: a rough slab of banded stone
		int r = (int) Math.ceil(Math.max(rx, rz)) + 1;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				double e = (dx * dx) / (rx * rx) + (dz * dz) / (rz * rz) + random.nextDouble() * 0.25;
				if (e > 1.0) {
					continue;
				}
				// thinner toward the rim, and a ragged top
				int layers = e > 0.7 ? Math.max(1, thick - 1) : thick + (random.nextInt(4) == 0 ? 1 : 0);
				for (int y = 0; y < layers; y++) {
					pos.setWithOffset(origin, dx, clearance + y, dz);
					level.setBlock(pos, band(clearance + y, random), 2);
				}
			}
		}
		// two or three legs, set in from the rim, each down to the sand
		int legs = 2 + random.nextInt(2);
		double turn = random.nextDouble() * Math.PI * 2;
		for (int i = 0; i < legs; i++) {
			double a = turn + i * Math.PI * 2 / legs;
			int lx = (int) Math.round(Math.cos(a) * (rx - 1.5));
			int lz = (int) Math.round(Math.sin(a) * (rz - 1.5));
			for (int ox = 0; ox <= 1; ox++) {
				for (int oz = 0; oz <= 1; oz++) {
					for (int y = clearance - 1; y >= -3; y--) {
						pos.setWithOffset(origin, lx + ox, y, lz + oz);
						if (y < 0 && level.getBlockState(pos).isSolid()) {
							break;
						}
						level.setBlock(pos, band(y, random), 2);
					}
				}
			}
		}
		return true;
	}

	/** Banded like the rock of a desert mesa. */
	private static BlockState band(int y, RandomSource random) {
		int b = Math.floorMod(y, 4);
		if (random.nextInt(9) == 0) {
			return Blocks.SMOOTH_SANDSTONE.defaultBlockState();
		}
		return b == 0 ? Blocks.RED_SANDSTONE.defaultBlockState() : b == 2 ? Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState()
				: Blocks.SANDSTONE.defaultBlockState();
	}
}
