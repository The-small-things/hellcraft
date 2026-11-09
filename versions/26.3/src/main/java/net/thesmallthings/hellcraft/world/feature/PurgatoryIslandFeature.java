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
 * stepping up to the summit, each column solid from the sea floor. Purgatory.buildOnce adds the stairs, signs and garden.
 */
public record PurgatoryIslandFeature() implements Feature {
	public static final PurgatoryIslandFeature INSTANCE = new PurgatoryIslandFeature();
	public static final MapCodec<PurgatoryIslandFeature> CODEC = MapCodec.unit(INSTANCE);

	@Override
	public MapCodec<PurgatoryIslandFeature> codec() {
		return CODEC;
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
					level.setBlock(pos.set(x, y, z), Purgatory.material(k, y, top, random), 2);
				}
				placed = true;
			}
		}
		return placed;
	}
}
