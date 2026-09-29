package net.thesmallthings.hellcraft.world.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/** Heaps of slag from the forges: magma, basalt and blackstone, with a glint of gold. */
public record SlagHeapFeature() implements Feature {
	public static final SlagHeapFeature INSTANCE = new SlagHeapFeature();
	public static final MapCodec<SlagHeapFeature> CODEC = MapCodec.unit(INSTANCE);

	@Override
	public MapCodec<SlagHeapFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		BlockPos floor = NetherScan.floor(level, origin, 40);
		if (floor == null) {
			return false;
		}
		float radius = 2.5f + random.nextFloat() * 2.5f;
		int r = (int) Math.ceil(radius);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				int height = (int) Math.round((radius - d) * 0.8 + random.nextFloat());
				for (int dy = -1; dy < height; dy++) {
					pos.setWithOffset(floor, dx, dy, dz);
					if (!level.getBlockState(pos).isAir() && dy >= 0) {
						continue;
					}
					float roll = random.nextFloat();
					BlockState b = roll < 0.35f ? Blocks.MAGMA_BLOCK.defaultBlockState()
							: roll < 0.65f ? Blocks.BASALT.defaultBlockState()
							: roll < 0.93f ? Blocks.BLACKSTONE.defaultBlockState()
							: Blocks.GILDED_BLACKSTONE.defaultBlockState();
					level.setBlock(pos, b, 2);
				}
			}
		}
		return true;
	}
}
