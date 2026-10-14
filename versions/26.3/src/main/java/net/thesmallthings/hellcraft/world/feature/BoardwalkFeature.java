package net.thesmallthings.hellcraft.world.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.thesmallthings.hellcraft.world.Fen;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.Zone;

/**
 * The Fen's plank roads (L-V): boardwalks winding across the Styx (Fen.boardwalk), laid on the water. The sullen
 * can't reach you on them (LayerRules). Now and then a post holds a soul lantern.
 */
public record BoardwalkFeature() implements Feature {
	public static final BoardwalkFeature INSTANCE = new BoardwalkFeature();
	public static final MapCodec<BoardwalkFeature> CODEC = MapCodec.unit(INSTANCE);

	@Override
	public MapCodec<BoardwalkFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		int x0 = origin.getX() & ~15;
		int z0 = origin.getZ() & ~15;
		if (InfernoGeometry.zoneAt(x0 + 8, z0 + 8) != Zone.STYX && InfernoGeometry.zoneAt(x0, z0) != Zone.STYX) {
			return false;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		boolean placed = false;
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = x0 + dx;
				int z = z0 + dz;
				if (InfernoGeometry.zoneAt(x, z) != Zone.STYX || InfernoGeometry.fluidAt(x, z) != InfernoGeometry.Fluid.WATER || !Fen.boardwalk(x, z)) {
					continue;
				}
				int surface = InfernoGeometry.fluidLevel(x, z);
				pos.set(x, surface - 1, z);
				if (!level.getFluidState(pos).is(FluidTags.WATER) || !level.getBlockState(pos.above()).isAir()) {
					continue;
				}
				level.setBlock(pos, Blocks.SPRUCE_PLANKS.defaultBlockState(), 2);
				placed = true;
				// a lantern post where the walk passes a hashed spot, set on its edge
				if (Math.floorMod(x * 73 + z * 31, 97) == 0 && !Fen.boardwalk(x + 1, z)) {
					level.setBlock(pos.set(x, surface, z), Blocks.SPRUCE_FENCE.defaultBlockState(), 2);
					level.setBlock(pos.set(x, surface + 1, z), Blocks.SPRUCE_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, false), 2);
					level.setBlock(pos.set(x, surface + 2, z), Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false), 2);
				}
			}
		}
		return placed;
	}
}
