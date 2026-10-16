package net.thesmallthings.hellcraft.world.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.storage.loot.LootTable;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.entity.HellEntities;
import net.thesmallthings.hellcraft.entity.boss.CyclopsEntity;
import net.thesmallthings.hellcraft.world.ForgeGeometry;
import net.thesmallthings.hellcraft.world.InfernoGeometry;

/**
 * A Cyclops's camp in the Cyclops Quarries (Phase 6): a levelled yard round a fire pit, a hide shelter on posts, a
 * stockpile of cut stone, boulders, a chest (chests/cyclops_camp) and the Cyclops himself by his fire
 * (CyclopsEntity). One to a cell of the grid, deep in the quarries.
 */
public record CyclopsCampFeature() implements Feature {
	public static final CyclopsCampFeature INSTANCE = new CyclopsCampFeature();
	public static final MapCodec<CyclopsCampFeature> CODEC = MapCodec.unit(INSTANCE);
	public static final int CELL = 96;
	private static final int YARD = 7;
	private static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, HellcraftMod.id("chests/cyclops_camp"));

	@Override
	public MapCodec<CyclopsCampFeature> codec() {
		return CODEC;
	}

	/** The camp of cell (i, j), {x, z}, or null where the cell has none. */
	public static int[] site(int i, int j) {
		int x = (int) Math.floor((i + 0.3 + 0.4 * InfernoGeometry.hash01(i, j, 96)) * CELL);
		int z = (int) Math.floor((j + 0.3 + 0.4 * InfernoGeometry.hash01(i, j, 97)) * CELL);
		return ForgeGeometry.weights(x, z)[ForgeGeometry.Region.CYCLOPS_QUARRIES.ordinal()] > 0.8 ? new int[]{x, z} : null;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		int x0 = origin.getX() & ~15;
		int z0 = origin.getZ() & ~15;
		boolean placed = false;
		for (int i = Math.floorDiv(x0, CELL); i <= Math.floorDiv(x0 + 15, CELL); i++) {
			for (int j = Math.floorDiv(z0, CELL); j <= Math.floorDiv(z0 + 15, CELL); j++) {
				int[] at = site(i, j);
				if (at != null && at[0] >= x0 && at[0] < x0 + 16 && at[1] >= z0 && at[1] < z0 + 16) {
					placed |= build(level, at[0], at[1], random);
				}
			}
		}
		return placed;
	}

	private static boolean build(WorldGenLevel level, int x, int z, RandomSource random) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int floor = -1;
		for (int y = 100; y > ForgeGeometry.LAVA + 1; y--) {
			if (level.getBlockState(pos.set(x, y, z)).isSolid() && level.getBlockState(pos.set(x, y + 1, z)).isAir()) {
				floor = y + 1;
				break;
			}
		}
		if (floor < 0) {
			return false;
		}
		// a levelled yard of trodden earth and stone
		for (int dx = -YARD; dx <= YARD; dx++) {
			for (int dz = -YARD; dz <= YARD; dz++) {
				if (dx * dx + dz * dz > YARD * YARD + 2) {
					continue;
				}
				BlockState ground = (dx * 7 + dz * 3) % 5 == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.PACKED_MUD.defaultBlockState();
				for (int y = floor - 3; y < floor; y++) {
					level.setBlock(pos.set(x + dx, y, z + dz), y == floor - 1 ? ground : Blocks.BLACKSTONE.defaultBlockState(), 2);
				}
				for (int y = floor; y < floor + 7; y++) {
					level.setBlock(pos.set(x + dx, y, z + dz), Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
		// the fire pit
		for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
			level.setBlock(pos.set(x + d[0], floor, z + d[1]), Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState(), 2);
		}
		level.setBlock(pos.set(x, floor - 1, z), Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
		level.setBlock(pos.set(x, floor, z), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true), 2);
		// a shelter on four posts
		int sx = x + 3;
		int sz = z + 2;
		for (int[] d : new int[][]{{0, 0}, {3, 0}, {0, 3}, {3, 3}}) {
			for (int y = floor; y < floor + 4; y++) {
				level.setBlock(pos.set(sx + d[0], y, sz + d[1]), Blocks.DARK_OAK_FENCE.defaultBlockState(), 2);
			}
		}
		for (int dx = -1; dx <= 4; dx++) {
			for (int dz = -1; dz <= 4; dz++) {
				level.setBlock(pos.set(sx + dx, floor + 4, sz + dz), Blocks.DARK_OAK_SLAB.defaultBlockState(), 2);
			}
		}
		// under it, his chest
		level.setBlock(pos.set(sx + 1, floor, sz + 2), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, net.minecraft.core.Direction.WEST), 2);
		if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
			chest.setLootTable(LOOT, random.nextLong());
		}
		// a stockpile of cut stone, and boulders
		for (int k = 0; k < 6; k++) {
			level.setBlock(pos.set(x - 4 + k % 3, floor + k / 3, z - 3), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 2);
		}
		for (int[] b : new int[][]{{-5, 3}, {5, -4}, {-2, 5}}) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					for (int dy = 0; dy <= 1; dy++) {
						if (Math.abs(dx) + Math.abs(dz) + dy <= 2) {
							level.setBlock(pos.set(x + b[0] + dx, floor + dy, z + b[1] + dz), Blocks.BLACKSTONE.defaultBlockState(), 2);
						}
					}
				}
			}
		}
		// and the Cyclops, asleep by his fire until someone comes near
		CyclopsEntity cyclops = HellEntities.CYCLOPS.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
		if (cyclops != null) {
			cyclops.snapTo(x - 2.5, floor, z + 0.5, random.nextFloat() * 360f, 0);
			cyclops.setPersistenceRequired();
			level.addFreshEntity(cyclops);
		}
		return true;
	}
}
