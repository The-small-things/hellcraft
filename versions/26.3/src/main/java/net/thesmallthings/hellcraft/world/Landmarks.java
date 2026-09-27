package net.thesmallthings.hellcraft.world;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.HellState;

/**
 * One-time construction of the Inferno's landmarks the first time the server starts on a fresh
 * Inferno world: the spawn and world border, the Gate of Hell, a starter blood altar, the chained
 * giants, and Lucifer's frozen pit.
 */
public final class Landmarks {
	private Landmarks() {
	}

	public static void buildOnce(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (!HellWorldgen.isInferno(level)) {
			HellcraftMod.LOGGER.warn("The overworld is not an Inferno world (it was probably created before Hellcraft was installed). "
					+ "Worldgen features only apply to new worlds; lifesteal still works.");
			return;
		}
		HellState state = HellState.get(server);
		if (state.landmarksBuilt) {
			return;
		}
		HellcraftMod.LOGGER.info("Building the landmarks of the Inferno...");

		WorldBorder border = level.getWorldBorder();
		border.setCenter(0.0, 0.0);
		border.setSize(InfernoGeometry.BORDER_RADIUS * 2.0);
		level.getGameRules().getRule(GameRules.RULE_DOINSOMNIA).set(false, server);

		// the Gate of Hell itself is a ring wall built by worldgen (GateWallFeature); spawn just outside its +X gate
		int gateX = InfernoGeometry.gateX();
		int gateY = surface(level, gateX, 0);
		BlockPos spawn = new BlockPos(gateX + 24, surface(level, gateX + 24, 0), 0);
		clear(level, spawn.getX() - 2, spawn.getY(), spawn.getZ() - 2, spawn.getX() + 2, spawn.getY() + 3, spawn.getZ() + 2);
		level.setDefaultSpawnPos(spawn, 90.0f);
		buildAltar(level, gateX + 14, surface(level, gateX + 14, 9), 9);

		String[] giants = {"Nimrod", "Ephialtes", "Antaeus"};
		for (int i = 0; i < giants.length; i++) {
			double angle = InfernoGeometry.spokeAngle(i * 4 + 2) + 0.05;
			int x = (int) Math.round(Math.cos(angle) * InfernoGeometry.GIANT_RADIUS);
			int z = (int) Math.round(Math.sin(angle) * InfernoGeometry.GIANT_RADIUS);
			spawnGiant(level, giants[i], x, surface(level, x, z), z);
		}
		buildPit(level);

		state.landmarksBuilt = true;
		state.setDirty();
		int wall = 0;
		for (int x = gateX - 3; x <= gateX + 3; x++) {
			for (int z = -12; z <= 12; z++) {
				for (int y = gateY - 32; y <= gateY + 4; y++) {
					BlockState b = level.getBlockState(new BlockPos(x, y, z));
					if (b.is(Blocks.POLISHED_BLACKSTONE_BRICKS) || b.is(Blocks.DEEPSLATE_TILES) || b.is(Blocks.CHISELED_POLISHED_BLACKSTONE)) {
						wall++;
					}
				}
			}
		}
		HellcraftMod.LOGGER.info("Gate wall: {} blocks at {} {} 0", wall, gateX, gateY);
		HellcraftMod.LOGGER.info("The Gate of Hell stands at {} {} 0. Abandon all hope.", gateX, gateY);
	}

	private static int surface(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
	}

	private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
		level.setBlock(new BlockPos(x, y, z), state, 3);
	}

	private static void clear(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1) {
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					set(level, x, y, z, Blocks.AIR.defaultBlockState());
				}
			}
		}
	}

	private static void buildAltar(ServerLevel level, int x, int ground, int z) {
		clear(level, x - 2, ground, z - 2, x + 2, ground + 4, z + 2);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				boolean core = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
				set(level, x + dx, ground - 1, z + dz, core ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
			}
		}
		set(level, x, ground, z, Blocks.RESPAWN_ANCHOR.defaultBlockState());
		for (int[] c : new int[][]{{-2, -2}, {-2, 2}, {2, -2}, {2, 2}}) {
			set(level, x + c[0], ground, z + c[1], Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState());
			set(level, x + c[0], ground + 1, z + c[1], Blocks.SOUL_LANTERN.defaultBlockState());
		}
	}

	private static void spawnGiant(ServerLevel level, String name, int x, int y, int z) {
		Giant giant = EntityType.GIANT.create(level);
		if (giant == null) {
			return;
		}
		giant.moveTo(x + 0.5, y, z + 0.5, (float) Math.toDegrees(Math.atan2(-x, z)) + 180.0f, 0.0f);
		giant.setCustomName(Component.literal(name));
		giant.setNoAi(true);
		giant.setInvulnerable(true);
		giant.setPersistenceRequired();
		giant.setSilent(true);
		level.addFreshEntity(giant);
		for (int dy = 0; dy < 12; dy++) {
			set(level, x + 2, y + dy, z, Blocks.CHAIN.defaultBlockState());
			set(level, x - 2, y + dy, z, Blocks.CHAIN.defaultBlockState());
		}
	}

	/** Pillars of ice ring the pit where Lucifer is frozen. */
	private static void buildPit(ServerLevel level) {
		int floor = surface(level, 0, 0);
		for (int i = 0; i < 9; i++) {
			double a = i * 2.0 * Math.PI / 9.0;
			int x = (int) Math.round(Math.cos(a) * 24);
			int z = (int) Math.round(Math.sin(a) * 24);
			int base = surface(level, x, z);
			for (int y = base - 2; y < base + 14; y++) {
				set(level, x, y, z, Blocks.PACKED_ICE.defaultBlockState());
				set(level, x + 1, y, z, Blocks.BLUE_ICE.defaultBlockState());
			}
		}
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				if (dx * dx + dz * dz <= 9) {
					set(level, dx, floor - 1, dz, Blocks.BLUE_ICE.defaultBlockState());
				}
			}
		}
	}
}
