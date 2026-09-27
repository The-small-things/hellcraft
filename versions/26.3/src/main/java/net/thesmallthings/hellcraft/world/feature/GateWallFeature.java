package net.thesmallthings.hellcraft.world.feature;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.world.InfernoGeometry;

/**
 * The Gate of Hell: a black ring wall around the Vestibule and Acheron, crowned with towers of soul
 * fire and pierced by twelve gates, each bearing Dante's inscription. Placed in the last decoration
 * step so it cuts through the Dark Wood's trees rather than being swallowed by them.
 */
public class GateWallFeature extends Feature<NoneFeatureConfiguration> {
	public GateWallFeature() {
		super(NoneFeatureConfiguration.CODEC);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		WorldGenLevel level = context.level();
		RandomSource random = context.random();
		int x0 = context.origin().getX() & ~15;
		int z0 = context.origin().getZ() & ~15;
		double r = Math.sqrt((x0 + 8.0) * (x0 + 8.0) + (z0 + 8.0) * (z0 + 8.0));
		if (!InfernoGeometry.nearGateWall(r, 16)) {
			return false;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		boolean placed = false;
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = x0 + dx;
				int z = z0 + dz;
				InfernoGeometry.WallPart part = InfernoGeometry.gateWallAt(x, z);
				if (part == InfernoGeometry.WallPart.NONE) {
					continue;
				}
				int ground = groundAt(level, x, z);
				int top = ground + (part == InfernoGeometry.WallPart.TOWER ? InfernoGeometry.GATE_TOWER_HEIGHT : InfernoGeometry.GATE_WALL_HEIGHT);
				for (int y = ground - 3; y <= top; y++) {
					pos.set(x, y, z);
					if (part == InfernoGeometry.WallPart.GATE) {
						if (y == ground - 1) {
							level.setBlock(pos, Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 2);
							continue;
						}
						if (y >= ground && y < ground + InfernoGeometry.GATE_OPENING_HEIGHT) {
							level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
							continue;
						}
						if (y == ground + InfernoGeometry.GATE_OPENING_HEIGHT) {
							level.setBlock(pos, Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), 2);
							continue;
						}
					}
					level.setBlock(pos, stone(random), 2);
				}
				if (part == InfernoGeometry.WallPart.TOWER) {
					pos.set(x, top, z);
					level.setBlock(pos, Blocks.SOUL_SOIL.defaultBlockState(), 2);
					pos.set(x, top + 1, z);
					level.setBlock(pos, Blocks.SOUL_FIRE.defaultBlockState(), 2);
				} else if (part == InfernoGeometry.WallPart.WALL && ((x + z) & 1) == 0) {
					pos.set(x, top + 1, z);
					level.setBlock(pos, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 2);
				}
				clearTreesAbove(level, pos, x, z, top + 2);
				placed = true;
			}
		}
		for (int k = 0; k < InfernoGeometry.GATE_COUNT; k++) {
			int[] spot = InfernoGeometry.gateSignSpot(k);
			if (spot[0] >> 4 == x0 >> 4 && spot[1] >> 4 == z0 >> 4) {
				try {
					placeInscription(level, spot[0], spot[1], InfernoGeometry.gateAngle(k));
				} catch (RuntimeException e) {
					// decoration must never break chunk generation
					HellcraftMod.LOGGER.warn("Could not place the inscription at gate {}", k, e);
				}
			}
		}
		return placed;
	}

	/** Solid ground under any trees or giant mushrooms. */
	private static int groundAt(WorldGenLevel level, int x, int z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1, z);
		while (pos.getY() > level.getMinBuildHeight() + 5) {
			BlockState state = level.getBlockState(pos);
			boolean plant = state.isAir() || state.canBeReplaced() || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)
					|| state.is(Blocks.MUSHROOM_STEM) || state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.RED_MUSHROOM_BLOCK);
			if (!plant) {
				break;
			}
			pos.move(0, -1, 0);
		}
		return pos.getY() + 1;
	}

	private static void clearTreesAbove(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int from) {
		for (int y = from; y < from + 30; y++) {
			pos.set(x, y, z);
			BlockState state = level.getBlockState(pos);
			if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
			}
		}
	}

	/** "Abandon all hope, ye who enter here" on a sign facing those who approach from the Dark Wood. */
	private static void placeInscription(WorldGenLevel level, int x, int z, double angle) {
		int ground = groundAt(level, x, z);
		BlockPos pos = new BlockPos(x, ground, z);
		for (int y = 0; y < 3; y++) {
			level.setBlock(pos.above(y), Blocks.AIR.defaultBlockState(), 2);
		}
		level.setBlock(pos.below(), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 2);
		// a player walking inward faces the centre; the sign's front must face them
		float inwardYaw = (float) Math.toDegrees(Math.atan2(Math.cos(angle), -Math.sin(angle)));
		level.setBlock(pos, Blocks.DARK_OAK_SIGN.defaultBlockState()
				.setValue(StandingSignBlock.ROTATION, RotationSegment.convertToSegment(inwardYaw + 180.0f)), 2);
		if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
			SignText text = new SignText()
					.setMessage(0, Component.literal("LASCIATE OGNE").withStyle(ChatFormatting.BOLD))
					.setMessage(1, Component.literal("SPERANZA").withStyle(ChatFormatting.BOLD))
					.setMessage(2, Component.literal("Abandon all hope"))
					.setMessage(3, Component.literal("ye who enter here"))
					.setColor(DyeColor.RED)
					.setHasGlowingText(true);
			// SignBlockEntity.setText()/setWaxed() notify the world, which doesn't exist yet during worldgen:
			// load the text as saved data instead
			CompoundTag tag = new CompoundTag();
			tag.put("front_text", SignText.DIRECT_CODEC.encodeStart(NbtOps.INSTANCE, text).getOrThrow());
			tag.putBoolean("is_waxed", true);
			sign.loadWithComponents(tag, level.registryAccess());
		}
		for (int side = -1; side <= 1; side += 2) {
			double tx = -Math.sin(angle) * side * 2;
			double tz = Math.cos(angle) * side * 2;
			BlockPos lamp = new BlockPos((int) Math.round(x + tx), groundAt(level, (int) Math.round(x + tx), (int) Math.round(z + tz)),
					(int) Math.round(z + tz));
			level.setBlock(lamp, Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState(), 2);
			level.setBlock(lamp.above(), Blocks.SOUL_LANTERN.defaultBlockState(), 2);
		}
	}

	private static BlockState stone(RandomSource random) {
		int roll = random.nextInt(20);
		if (roll < 2) {
			return Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		}
		if (roll < 5) {
			return Blocks.DEEPSLATE_TILES.defaultBlockState();
		}
		if (roll == 5) {
			return Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState();
		}
		return Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
	}
}
