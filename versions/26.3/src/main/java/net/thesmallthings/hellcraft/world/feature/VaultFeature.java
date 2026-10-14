package net.thesmallthings.hellcraft.world.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.storage.loot.LootTable;
import net.thesmallthings.hellcraft.HellcraftMod;

/**
 * Greed's vaults (L-IV, the Counting House): squat strongrooms of blackstone sunk into the hoards, each with a great
 * round door of iron and gold standing ajar. Inside, by lantern light, are the misers' chests (chests/vault).
 */
public record VaultFeature() implements Feature {
	public static final VaultFeature INSTANCE = new VaultFeature();
	public static final MapCodec<VaultFeature> CODEC = MapCodec.unit(INSTANCE);
	public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, HellcraftMod.id("chests/vault"));

	/** Half the vault's width (it is 2 * HALF + 1 square inside its walls) and its height. */
	private static final int HALF = 3;
	private static final int HEIGHT = 6;

	@Override
	public MapCodec<VaultFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		if (!level.getBlockState(origin.below()).isSolid() || !level.getFluidState(origin).isEmpty()) {
			return false;
		}
		Direction front = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		Direction right = front.getClockWise();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockState wall = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		// sunk two blocks into the ground, with a gold band round its roof
		for (int f = -HALF - 1; f <= HALF + 1; f++) {
			for (int s = -HALF - 1; s <= HALF + 1; s++) {
				boolean edge = Math.abs(f) == HALF + 1 || Math.abs(s) == HALF + 1;
				for (int y = -2; y <= HEIGHT; y++) {
					set(pos, origin, front, right, f, y, s);
					if (y == -2 || y == HEIGHT || edge) {
						BlockState b = y == HEIGHT - 1 && edge ? Blocks.GILDED_BLACKSTONE.defaultBlockState()
								: random.nextInt(8) == 0 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : wall;
						level.setBlock(pos, b, 2);
					} else {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
					}
				}
			}
		}
		// the round door in the front wall: an iron ring with a gold boss, swung ajar (its right half is open)
		int doorCentre = 1;
		for (int s = -2; s <= 2; s++) {
			for (int y = -1; y <= 3; y++) {
				double d = Math.hypot(s, y - doorCentre);
				if (d > 2.4) {
					continue;
				}
				set(pos, origin, front, right, HALF + 1, y, s);
				if (s > 0 && y >= -1 && y <= 2) {
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
				} else {
					level.setBlock(pos, d < 0.6 ? Blocks.GOLD_BLOCK.defaultBlockState() : d > 1.7 ? Blocks.IRON_BLOCK.defaultBlockState()
							: Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), 2);
				}
			}
		}
		// steps down into it
		for (int y = -1; y <= 0; y++) {
			set(pos, origin, front, right, HALF + 2, y, 1);
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
			set(pos, origin, front, right, HALF + 2, y, 2);
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		set(pos, origin, front, right, HALF + 2, -2, 1);
		level.setBlock(pos, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.defaultBlockState()
				.setValue(StairBlock.FACING, front.getOpposite()), 2);
		// the chests along the back wall, and a lantern overhead
		for (int s : new int[]{-2, 0, 2}) {
			if (s != 0 && random.nextInt(3) == 0) {
				continue;
			}
			set(pos, origin, front, right, -HALF, -1, s);
			level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, front), 2);
			RandomizableContainer.setBlockEntityLootTable(level, random, pos.immutable(), LOOT);
		}
		set(pos, origin, front, right, 0, HEIGHT - 1, 0);
		level.setBlock(pos, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);
		// gold heaped on the floor
		for (int k = 0; k < 6; k++) {
			set(pos, origin, front, right, random.nextInt(2 * HALF + 1) - HALF, -1, random.nextInt(2 * HALF + 1) - HALF);
			if (level.getBlockState(pos).isAir()) {
				level.setBlock(pos, random.nextBoolean() ? Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState() : Blocks.RAW_GOLD_BLOCK.defaultBlockState(), 2);
			}
		}
		return true;
	}

	/** pos = origin + f along front + y up + s along right. */
	private static void set(BlockPos.MutableBlockPos pos, BlockPos origin, Direction front, Direction right, int f, int y, int s) {
		pos.set(origin.getX() + front.getStepX() * f + right.getStepX() * s, origin.getY() + y, origin.getZ() + front.getStepZ() * f + right.getStepZ() * s);
	}
}
