package net.thesmallthings.hellcraft.world.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/** Great chains hanging from the Nether's ceiling, ending in a hook, a lantern or a shackled anvil. */
public record ChainPillarFeature() implements Feature {
	public static final ChainPillarFeature INSTANCE = new ChainPillarFeature();
	public static final MapCodec<ChainPillarFeature> CODEC = MapCodec.unit(INSTANCE);

	@Override
	public MapCodec<ChainPillarFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		BlockPos top = NetherScan.ceiling(level, origin, 50);
		if (top == null) {
			return false;
		}
		int length = 6 + random.nextInt(18);
		BlockPos.MutableBlockPos pos = top.mutable();
		int placed = 0;
		for (int i = 0; i < length; i++) {
			if (!level.getBlockState(pos).isAir()) {
				break;
			}
			level.setBlock(pos, Blocks.IRON_CHAIN.defaultBlockState(), 2);
			placed++;
			pos.move(0, -1, 0);
		}
		if (placed > 2 && level.getBlockState(pos).isAir()) {
			BlockState end = switch (random.nextInt(3)) {
				case 0 -> Blocks.SOUL_LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true);
				case 1 -> Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true);
				default -> Blocks.ANVIL.defaultBlockState();
			};
			level.setBlock(pos, end, 2);
		}
		return placed > 0;
	}
}
