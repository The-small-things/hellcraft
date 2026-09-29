package net.thesmallthings.hellcraft.world.feature;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;

/** Paradiso: a small star (a glowing ball of one block) hanging in the sky above an island. */
public record StarClusterFeature(BlockState state) implements Feature {
	public static final MapCodec<StarClusterFeature> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			BlockState.CODEC.fieldOf("state").forGetter(StarClusterFeature::state)
	).apply(instance, StarClusterFeature::new));

	@Override
	public MapCodec<StarClusterFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		int ground = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, origin.getX(), origin.getZ());
		if (ground <= level.getMinY() + 1) {
			return false;
		}
		BlockPos center = new BlockPos(origin.getX(), ground + 8 + random.nextInt(24), origin.getZ());
		float radius = 0.8f + random.nextFloat() * 1.4f;
		int r = (int) Math.ceil(radius);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					if (dx * dx + dy * dy + dz * dz <= radius * radius + 0.5f) {
						pos.setWithOffset(center, dx, dy, dz);
						if (level.getBlockState(pos).isAir()) {
							level.setBlock(pos, state, 2);
						}
					}
				}
			}
		}
		return true;
	}
}
