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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.storage.loot.LootTable;
import net.thesmallthings.hellcraft.HellcraftMod;

/**
 * A ruined forge of Dis in the Nether: a cracked blackstone floor, broken walls, an anvil, a cauldron of
 * lava, a blast furnace and a chest of the smiths' leavings. Finds its own floor below a random point.
 */
public record ForgeRuinFeature() implements Feature {
	public static final ForgeRuinFeature INSTANCE = new ForgeRuinFeature();
	public static final MapCodec<ForgeRuinFeature> CODEC = MapCodec.unit(INSTANCE);
	public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, HellcraftMod.id("chests/forge_ruin"));

	@Override
	public MapCodec<ForgeRuinFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		BlockPos floor = NetherScan.floor(level, origin, 40);
		if (floor == null) {
			return false;
		}
		int r = 3;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		// the floor, levelled onto the ground
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				for (int dy = -3; dy <= -1; dy++) {
					pos.setWithOffset(floor, dx, dy, dz);
					BlockState b = dy == -1 ? (random.nextInt(4) == 0 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
							: Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()) : Blocks.BLACKSTONE.defaultBlockState();
					if (dy == -1 || !level.getBlockState(pos).isSolid()) {
						level.setBlock(pos, b, 2);
					}
				}
				for (int dy = 0; dy <= 4; dy++) {
					pos.setWithOffset(floor, dx, dy, dz);
					boolean wall = Math.abs(dx) == r || Math.abs(dz) == r;
					// broken walls: shorter toward one corner, with gaps
					int height = wall ? Math.max(0, 3 - (dx + dz + 6) / 4 - random.nextInt(2)) : 0;
					level.setBlock(pos, wall && dy < height ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
		level.setBlock(floor, (random.nextBoolean() ? Blocks.CHIPPED_ANVIL : Blocks.DAMAGED_ANVIL).defaultBlockState(), 2);
		level.setBlock(floor.offset(-1, 0, 1), Blocks.LAVA_CAULDRON.defaultBlockState(), 2);
		level.setBlock(floor.offset(1, 0, 1), Blocks.BLAST_FURNACE.defaultBlockState(), 2);
		level.setBlock(floor.offset(-2, 0, -2), Blocks.IRON_CHAIN.defaultBlockState(), 2);
		BlockPos chest = floor.offset(1, 0, -1);
		level.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH), 2);
		RandomizableContainer.setBlockEntityLootTable(level, random, chest, LOOT);
		return true;
	}
}
