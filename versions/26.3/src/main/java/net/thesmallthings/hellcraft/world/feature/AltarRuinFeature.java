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
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.storage.loot.LootTable;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.BloodAltar;

/**
 * A forgotten blood altar: a respawn anchor on crying obsidian (see {@link BloodAltar}) inside a
 * broken ring of pillars, with a chest of offerings.
 */
public record AltarRuinFeature() implements Feature {
	public static final AltarRuinFeature INSTANCE = new AltarRuinFeature();
	public static final MapCodec<AltarRuinFeature> CODEC = MapCodec.unit(INSTANCE);
	public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, HellcraftMod.id("chests/altar_ruin"));

	@Override
	public MapCodec<AltarRuinFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		if (!level.getBlockState(origin.below()).isSolid()) {
			return false;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				for (int y = -3; y <= -1; y++) {
					pos.setWithOffset(origin, dx, y, dz);
					boolean core = Math.abs(dx) <= 1 && Math.abs(dz) <= 1 && y == -1;
					level.setBlock(pos, core ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
							: random.nextInt(4) == 0 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
							: Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 2);
				}
				for (int y = 0; y <= 4; y++) {
					pos.setWithOffset(origin, dx, y, dz);
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
		level.setBlock(origin, Blocks.RESPAWN_ANCHOR.defaultBlockState(), 2);
		int[][] corners = {{-3, -3}, {-3, 3}, {3, -3}, {3, 3}};
		for (int[] c : corners) {
			int height = 1 + random.nextInt(4);
			for (int y = 0; y < height; y++) {
				pos.setWithOffset(origin, c[0], y, c[1]);
				level.setBlock(pos, Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState(), 2);
			}
			if (height >= 3) {
				pos.setWithOffset(origin, c[0], height, c[1]);
				level.setBlock(pos, Blocks.SOUL_LANTERN.defaultBlockState(), 2);
			}
		}
		BlockPos chest = origin.offset(0, 0, 2);
		level.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH), 2);
		RandomizableContainer.setBlockEntityLootTable(level, random, chest, LOOT);
		return true;
	}
}
