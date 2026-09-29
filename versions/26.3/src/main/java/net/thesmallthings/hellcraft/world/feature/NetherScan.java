package net.thesmallthings.hellcraft.world.feature;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import org.jetbrains.annotations.Nullable;

/** Finding floors and ceilings inside the Nether's caverns from a random starting point. */
final class NetherScan {
	private NetherScan() {
	}

	/** The first air block standing on solid ground at or below {@code from} (within {@code range}), not on lava. */
	@Nullable
	static BlockPos floor(WorldGenLevel level, BlockPos from, int range) {
		BlockPos.MutableBlockPos pos = from.mutable();
		for (int i = 0; i < range && pos.getY() > level.getMinY() + 2; i++) {
			if (level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).isSolid()
					&& level.getBlockState(pos.above()).isAir() && level.getBlockState(pos.below()).getFluidState().isEmpty()) {
				return pos.immutable();
			}
			pos.move(0, -1, 0);
		}
		return null;
	}

	/** The last air block under a solid ceiling at or above {@code from} (within {@code range}). */
	@Nullable
	static BlockPos ceiling(WorldGenLevel level, BlockPos from, int range) {
		BlockPos.MutableBlockPos pos = from.mutable();
		for (int i = 0; i < range && pos.getY() < level.getMaxY() - 2; i++) {
			if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isSolid()) {
				return pos.immutable();
			}
			pos.move(0, 1, 0);
		}
		return null;
	}
}
