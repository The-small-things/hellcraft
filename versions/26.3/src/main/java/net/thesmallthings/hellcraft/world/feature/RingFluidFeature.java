package net.thesmallthings.hellcraft.world.feature;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.thesmallthings.hellcraft.world.InfernoGeometry;

/**
 * Fills the three rivers of Hell (Acheron, Styx, Phlegethon) up to their level, column by column
 * across the chunk being decorated. The terrain around each river rises above its level, so the
 * pools stay put.
 */
public class RingFluidFeature extends Feature<NoneFeatureConfiguration> {
	public RingFluidFeature() {
		super(NoneFeatureConfiguration.CODEC);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		WorldGenLevel level = context.level();
		int x0 = context.origin().getX() & ~15;
		int z0 = context.origin().getZ() & ~15;
		double r = Math.sqrt((x0 + 8.0) * (x0 + 8.0) + (z0 + 8.0) * (z0 + 8.0));
		if (!InfernoGeometry.nearRiver(r, 16)) {
			return false;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		boolean placed = false;
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = x0 + dx;
				int z = z0 + dz;
				InfernoGeometry.Fluid fluid = InfernoGeometry.fluidAt(x, z);
				if (fluid == InfernoGeometry.Fluid.NONE) {
					continue;
				}
				int surface = InfernoGeometry.fluidLevel(x, z);
				BlockState state = fluid == InfernoGeometry.Fluid.WATER ? Blocks.WATER.defaultBlockState() : Blocks.LAVA.defaultBlockState();
				int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
				for (int y = floor; y < surface; y++) {
					pos.set(x, y, z);
					BlockState existing = level.getBlockState(pos);
					if (existing.isAir() || existing.canBeReplaced()) {
						level.setBlock(pos, state, 2);
						placed = true;
					}
				}
			}
		}
		return placed;
	}
}
