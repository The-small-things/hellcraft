package net.thesmallthings.hellcraft.world.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.thesmallthings.hellcraft.block.HellBlocks;
import net.thesmallthings.hellcraft.world.ForgeGeometry;
import net.thesmallthings.hellcraft.world.InfernoGeometry;

/**
 * The Slag Sea's crust (Phase 6): floes of cooled slag on the molten sea, and four causeways of crust straight out
 * from the Great Forge's stairs along the axes, joined by a ring half way out. Crust cracks under you and melts
 * (SlagCrustBlock), so the sea is crossed at a run.
 */
public record SlagCrustFeature() implements Feature {
	public static final SlagCrustFeature INSTANCE = new SlagCrustFeature();
	public static final MapCodec<SlagCrustFeature> CODEC = MapCodec.unit(INSTANCE);
	/** The causeways are this many blocks either side of the axis. */
	public static final int CAUSEWAY = 2;

	@Override
	public MapCodec<SlagCrustFeature> codec() {
		return CODEC;
	}

	/** Whether the sea at this column wears crust. */
	public static boolean crusted(int x, int z) {
		double r = Math.sqrt((double) x * x + (double) z * z);
		double ring = (ForgeGeometry.MESA_RADIUS + ForgeGeometry.SEA_OUTER) / 2.0;
		if (Math.abs(x) <= CAUSEWAY || Math.abs(z) <= CAUSEWAY || Math.abs(r - ring) <= 1.5) {
			return true;
		}
		return InfernoGeometry.fbm(x / 9.0, z / 9.0, 90) > 0.2;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		int x0 = origin.getX() & ~15;
		int z0 = origin.getZ() & ~15;
		int y = ForgeGeometry.LAVA - 1;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		boolean placed = false;
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = x0 + dx;
				int z = z0 + dz;
				if (ForgeGeometry.weights(x, z)[ForgeGeometry.Region.SLAG_SEA.ordinal()] < 0.5 || !crusted(x, z)) {
					continue;
				}
				if (level.getBlockState(pos.set(x, y, z)).is(Blocks.LAVA) && level.getBlockState(pos.set(x, y + 1, z)).isAir()) {
					level.setBlock(pos.set(x, y, z), HellBlocks.SLAG_CRUST.defaultBlockState(), 2);
					placed = true;
				}
			}
		}
		return placed;
	}
}
