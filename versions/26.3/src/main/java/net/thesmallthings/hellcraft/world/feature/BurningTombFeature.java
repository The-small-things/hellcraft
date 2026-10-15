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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.storage.loot.LootTable;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.world.Tombs;

/**
 * The open, burning sepulchres of the heretics (Inferno, Canto IX-X), one on each of the Tombs sites: their fires
 * blast up in turn (LayerRules times them), so the way through the Iron City is a matter of rhythm.
 */
public record BurningTombFeature() implements Feature {
	public static final BurningTombFeature INSTANCE = new BurningTombFeature();
	public static final MapCodec<BurningTombFeature> CODEC = MapCodec.unit(INSTANCE);
	public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, HellcraftMod.id("chests/heretic_tomb"));

	@Override
	public MapCodec<BurningTombFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		int x0 = origin.getX() & ~15;
		int z0 = origin.getZ() & ~15;
		boolean placed = false;
		// each tomb is built from the chunk holding its corner (Tombs: the server times their blasts by the same sites)
		for (Tombs.Tomb t : Tombs.within(x0, z0, x0 + 15, z0 + 15)) {
			if (t.x() < x0 || t.x() > x0 + 15 || t.z() < z0 || t.z() > z0 + 15) {
				continue;
			}
			int spanX = t.alongX() ? Tombs.LENGTH : Tombs.WIDTH;
			int spanZ = t.alongX() ? Tombs.WIDTH : Tombs.LENGTH;
			int y = Integer.MIN_VALUE;
			for (int[] c : new int[][]{{t.x(), t.z()}, {t.x() + spanX - 1, t.z()}, {t.x(), t.z() + spanZ - 1}, {t.x() + spanX - 1, t.z() + spanZ - 1}}) {
				y = Math.max(y, level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, c[0], c[1]));
			}
			build(level, random, new BlockPos(t.x(), y, t.z()), t.alongX());
			placed = true;
		}
		return placed;
	}

	private static void build(WorldGenLevel level, RandomSource random, BlockPos origin, boolean alongX) {
		boolean soul = random.nextInt(3) == 0;
		int length = Tombs.LENGTH;
		int width = Tombs.WIDTH;
		BlockState wall = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		BlockState cracked = Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		BlockState fuel = soul ? Blocks.SOUL_SOIL.defaultBlockState() : Blocks.NETHERRACK.defaultBlockState();
		BlockState flame = soul ? Blocks.SOUL_FIRE.defaultBlockState() : Blocks.FIRE.defaultBlockState();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int a = 0; a < length; a++) {
			for (int b = 0; b < width; b++) {
				int dx = alongX ? a : b;
				int dz = alongX ? b : a;
				boolean edge = a == 0 || a == length - 1 || b == 0 || b == width - 1;
				// foundation so tombs on slopes don't float
				for (int y = -3; y <= -1; y++) {
					pos.setWithOffset(origin, dx, y, dz);
					level.setBlock(pos, edge ? wall : fuel, 2);
				}
				if (edge) {
					for (int y = 0; y <= 1; y++) {
						pos.setWithOffset(origin, dx, y, dz);
						level.setBlock(pos, random.nextInt(5) == 0 ? cracked : wall, 2);
					}
				} else {
					pos.setWithOffset(origin, dx, 0, dz);
					level.setBlock(pos, flame, 2);
					pos.setWithOffset(origin, dx, 1, dz);
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
		// the lid lies cast aside next to the tomb
		for (int a = 0; a < length; a++) {
			int dx = alongX ? a : width;
			int dz = alongX ? width : a;
			pos.setWithOffset(origin, dx, 0, dz);
			if (level.getBlockState(pos).canBeReplaced()) {
				level.setBlock(pos, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB.defaultBlockState(), 2);
			}
		}
		// one tomb in four keeps its heretic's forbidden books, in a chest at its foot
		if (random.nextInt(4) == 0) {
			pos.setWithOffset(origin, alongX ? length : 1, 0, alongX ? 1 : length);
			if (level.getBlockState(pos).canBeReplaced() && level.getBlockState(pos.below()).isSolid()) {
				level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, alongX ? Direction.WEST : Direction.NORTH), 2);
				RandomizableContainer.setBlockEntityLootTable(level, random, pos.immutable(), LOOT);
			}
		}
	}
}
