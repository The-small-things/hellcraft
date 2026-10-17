package net.thesmallthings.hellcraft.world.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.thesmallthings.hellcraft.world.InfernoGeometry;

/**
 * Gluttony's bloated fungus (L-III, the Mire): mushrooms swollen past their shape, a fat cap sagging on a short
 * stem, glowing from inside where it has split. Some are only a swollen puffball half sunk in the mud.
 */
public record BloatedFungusFeature() implements Feature {
	public static final BloatedFungusFeature INSTANCE = new BloatedFungusFeature();
	public static final MapCodec<BloatedFungusFeature> CODEC = MapCodec.unit(INSTANCE);

	@Override
	public MapCodec<BloatedFungusFeature> codec() {
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
		boolean red = random.nextBoolean();
		BlockState cap = (red ? Blocks.RED_MUSHROOM_BLOCK : Blocks.BROWN_MUSHROOM_BLOCK).defaultBlockState();
		BlockState stem = Blocks.MUSHROOM_STEM.defaultBlockState();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		boolean puffball = random.nextInt(3) == 0;
		int stemHeight = puffball ? 0 : 2 + random.nextInt(3);
		for (int y = -1; y < stemHeight; y++) {
			for (int dx = 0; dx <= (stemHeight > 3 ? 1 : 0); dx++) {
				for (int dz = 0; dz <= (stemHeight > 3 ? 1 : 0); dz++) {
					pos.setWithOffset(origin, dx, y, dz);
					level.setBlock(pos, stem, 2);
				}
			}
		}
		// the cap: a squashed, lopsided ball, hollow, with light inside where it has split
		double rx = 3 + random.nextDouble() * 2.5;
		double ry = puffball ? rx * 0.8 : 2 + random.nextDouble() * 1.5;
		double rz = rx * (0.8 + random.nextDouble() * 0.4);
		double sag = random.nextDouble() * 1.5;
		int cy = stemHeight + (int) Math.round(ry * (puffball ? 0.5 : 0.6));
		int bx = (int) Math.ceil(rx);
		int by = (int) Math.ceil(ry);
		int bz = (int) Math.ceil(rz);
		for (int dx = -bx; dx <= bx; dx++) {
			for (int dy = -by; dy <= by; dy++) {
				for (int dz = -bz; dz <= bz; dz++) {
					// the cap sags toward one side
					double ox = dx + sag * (dy < 0 ? 0 : dy / ry);
					double d = (ox * ox) / (rx * rx) + (dy * dy) / (ry * ry) + (dz * dz) / (rz * rz);
					if (d > 1.0) {
						continue;
					}
					pos.setWithOffset(origin, dx, cy + dy, dz);
					if (!level.getBlockState(pos).canBeReplaced() && !level.getBlockState(pos).is(Blocks.MUD)) {
						continue;
					}
					boolean skin = d > 0.62;
					if (skin) {
						boolean split = dy < 0 && random.nextInt(6) == 0;
						level.setBlock(pos, split ? Blocks.SHROOMLIGHT.defaultBlockState() : cap.setValue(HugeMushroomBlock.DOWN, dy > -by + 1), 2);
					} else if (dy == -by + 1 && random.nextInt(4) == 0) {
						level.setBlock(pos, Blocks.SHROOMLIGHT.defaultBlockState(), 2);
					} else {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
					}
				}
			}
		}
		return true;
	}
}
