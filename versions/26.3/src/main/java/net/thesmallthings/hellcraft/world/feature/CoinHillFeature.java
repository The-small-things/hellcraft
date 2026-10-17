package net.thesmallthings.hellcraft.world.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.thesmallthings.hellcraft.world.InfernoGeometry;

/**
 * Greed's hoards (L-IV, the Counting House): heaps of gold coin. A mound of gold and raw gold over a core of
 * blackstone, strewn with loose coins (gold pressure plates) that spill down its sides and across the ground.
 */
public record CoinHillFeature() implements Feature {
	public static final CoinHillFeature INSTANCE = new CoinHillFeature();
	public static final MapCodec<CoinHillFeature> CODEC = MapCodec.unit(INSTANCE);

	@Override
	public MapCodec<CoinHillFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		// not in a guardian's arena
		if (InfernoGeometry.nearLair(origin.getX(), origin.getZ(), InfernoGeometry.LAIR_CLEARANCE)) {
			return false;
		}
		if (!level.getBlockState(origin.below()).isSolid() || !level.getFluidState(origin).isEmpty()) {
			return false;
		}
		double radius = 3.5 + random.nextDouble() * 4;
		double height = radius * (0.5 + random.nextDouble() * 0.35);
		int r = (int) Math.ceil(radius) + 3;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz) + random.nextDouble() * 0.6;
				int top = (int) Math.round(height * Math.max(0, 1 - (d / radius) * (d / radius)));
				for (int y = -1; y < top; y++) {
					pos.setWithOffset(origin, dx, y, dz);
					if (!level.getBlockState(pos).canBeReplaced() && y >= 0) {
						continue;
					}
					level.setBlock(pos, y >= top - 1 ? gold(random) : Blocks.BLACKSTONE.defaultBlockState(), 2);
				}
				// loose coins: thick on the hoard, thinning out across the ground round it
				double chance = d < radius ? 0.35 : 0.25 * Math.max(0, 1 - (d - radius) / 3);
				if (random.nextDouble() < chance) {
					pos.setWithOffset(origin, dx, Math.max(0, top), dz);
					while (pos.getY() > origin.getY() - 3 && level.getBlockState(pos.below()).canBeReplaced()) {
						pos.move(0, -1, 0);
					}
					if (level.getBlockState(pos).canBeReplaced() && level.getFluidState(pos).isEmpty() && level.getBlockState(pos.below()).isSolid()) {
						level.setBlock(pos, Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState(), 2);
					}
				}
			}
		}
		return true;
	}

	private static BlockState gold(RandomSource random) {
		int roll = random.nextInt(10);
		if (roll < 4) {
			return Blocks.GOLD_BLOCK.defaultBlockState();
		}
		if (roll < 8) {
			return Blocks.RAW_GOLD_BLOCK.defaultBlockState();
		}
		return Blocks.GILDED_BLACKSTONE.defaultBlockState();
	}
}
