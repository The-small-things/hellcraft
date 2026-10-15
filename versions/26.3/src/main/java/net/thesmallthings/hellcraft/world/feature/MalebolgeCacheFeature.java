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
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.storage.loot.LootTable;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.world.FraudSites;

/**
 * Malebolge's caches (L-VIII): a chest on the ridge between two ditches, on a few flagstones with a guttering candle
 * beside it. Where FraudSites says a cache is a mimic, nothing is built: the server sets the mimic down there instead
 * (Frauds), looking just the same.
 */
public record MalebolgeCacheFeature() implements Feature {
	public static final MalebolgeCacheFeature INSTANCE = new MalebolgeCacheFeature();
	public static final MapCodec<MalebolgeCacheFeature> CODEC = MapCodec.unit(INSTANCE);
	public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, HellcraftMod.id("chests/malebolge_cache"));

	@Override
	public MapCodec<MalebolgeCacheFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		int x0 = origin.getX() & ~15;
		int z0 = origin.getZ() & ~15;
		boolean placed = false;
		for (FraudSites.Cache c : FraudSites.cachesNear(x0 + 8, z0 + 8, 8)) {
			int x = (int) Math.floor(c.x());
			int z = (int) Math.floor(c.z());
			if (c.mimic() || x < x0 || x > x0 + 15 || z < z0 || z > z0 + 15) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					pos.set(x + dx, y - 1, z + dz);
					if (random.nextInt(4) != 0) {
						level.setBlock(pos, random.nextBoolean() ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState(), 2);
					}
				}
			}
			BlockPos chest = new BlockPos(x, y, z);
			level.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.from2DDataValue(c.facing())), 2);
			RandomizableContainer.setBlockEntityLootTable(level, random, chest, LOOT);
			BlockPos candle = chest.relative(Direction.from2DDataValue(c.facing()).getClockWise());
			if (level.getBlockState(candle).canBeReplaced()) {
				level.setBlock(candle, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 1 + random.nextInt(3))
						.setValue(CandleBlock.LIT, true), 2);
			}
			placed = true;
		}
		return placed;
	}
}
