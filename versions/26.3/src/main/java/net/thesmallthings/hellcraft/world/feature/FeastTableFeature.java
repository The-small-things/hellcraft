package net.thesmallthings.hellcraft.world.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.storage.loot.LootTable;
import net.thesmallthings.hellcraft.HellcraftMod;

/**
 * Gluttony's sunken feast tables (L-III, the Mire): long banquet tables sinking end-first into the mud, their
 * chairs toppled, laid with rotting cakes and guttering candles and the bones of the last course. A barrel at the
 * head of each holds what is left of the feast (loot table chests/feast_table). The planks are firm footing.
 */
public record FeastTableFeature() implements Feature {
	public static final FeastTableFeature INSTANCE = new FeastTableFeature();
	public static final MapCodec<FeastTableFeature> CODEC = MapCodec.unit(INSTANCE);
	public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, HellcraftMod.id("chests/feast_table"));

	@Override
	public MapCodec<FeastTableFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		if (!level.getBlockState(origin.below()).isSolid() || !level.getFluidState(origin).isEmpty()) {
			return false;
		}
		Direction along = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		Direction side = along.getClockWise();
		int length = 7 + random.nextInt(5);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockState top = Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
		BlockState plank = Blocks.DARK_OAK_PLANKS.defaultBlockState();
		for (int i = 0; i < length; i++) {
			// the far end has sunk: the top steps down a block, then into the mud
			int sink = i * 3 / length;
			for (int w = -1; w <= 1; w++) {
				BlockPos column = origin.relative(along, i).relative(side, w);
				pos.set(column.getX(), column.getY() - sink, column.getZ());
				level.setBlock(pos, sink >= 2 ? plank : top, 2);
				// legs at the corners and middle, buried in the mud below
				pos.move(0, -1, 0);
				if ((i == 0 || i == length - 1 || i == length / 2) && w != 0) {
					level.setBlock(pos, Blocks.DARK_OAK_FENCE.defaultBlockState(), 2);
				} else if (!level.getBlockState(pos).isSolid()) {
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
				}
				// what's left on the table
				pos.set(column.getX(), column.getY() - sink + 1, column.getZ());
				if (w == 0 && level.getBlockState(pos).canBeReplaced()) {
					level.setBlock(pos, dish(random), 2);
				}
			}
			// chairs along both sides, some toppled into the mud
			for (int s : new int[]{-2, 2}) {
				if (random.nextInt(3) == 0) {
					continue;
				}
				BlockPos chair = origin.relative(along, i).relative(side, s);
				pos.set(chair.getX(), chair.getY() - sink - (random.nextInt(4) == 0 ? 1 : 0), chair.getZ());
				if (level.getBlockState(pos).canBeReplaced()) {
					Direction facing = s < 0 ? side : side.getOpposite();
					level.setBlock(pos, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing.getOpposite()), 2);
				}
			}
		}
		// the barrel at the head of the table
		BlockPos head = origin.relative(along, -1);
		level.setBlock(head, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, along.getOpposite()), 2);
		RandomizableContainer.setBlockEntityLootTable(level, random, head, LOOT);
		return true;
	}

	private static BlockState dish(RandomSource random) {
		return switch (random.nextInt(9)) {
			case 0, 1 -> Blocks.CAKE.defaultBlockState();
			case 2 -> Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 1 + random.nextInt(4)).setValue(CandleBlock.LIT, true);
			case 3 -> Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, random.nextInt(RotationSegment.getMaxSegmentIndex() + 1));
			case 4 -> Blocks.BROWN_MUSHROOM.defaultBlockState();
			case 5 -> Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 1 + random.nextInt(3));
			default -> Blocks.AIR.defaultBlockState();
		};
	}
}
