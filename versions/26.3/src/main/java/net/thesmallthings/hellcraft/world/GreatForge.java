package net.thesmallthings.hellcraft.world;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.util.Signs;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Great Forge of Dis: Vulcan's smithy, a domed blackstone hall carved into the Nether at its centre
 * (0, 70, 0). The Hellforge stands in it (an anvil on magma: infernal blood gear, see Hellforge), and Vulcan
 * wakes when someone enters (GuardianManager). Built the first time a player comes near.
 */
public final class GreatForge {
	private GreatForge() {
	}

	public static final int FLOOR_Y = 70;
	private static final int RADIUS = 20;
	private static final int HEIGHT = 20;
	private static final int BUILD_RANGE = 160;
	private static int ticks;

	/** Where Vulcan stands: the middle of the hall's floor. */
	public static BlockPos center() {
		return new BlockPos(0, FLOOR_Y, 0);
	}

	/** The Hellforge in the hall (an anvil on magma). */
	public static BlockPos hellforge() {
		return new BlockPos(0, FLOOR_Y, -12);
	}

	@Nullable
	public static ServerLevel nether(MinecraftServer server) {
		return server.getLevel(Level.NETHER);
	}

	public static boolean built(MinecraftServer server) {
		return HellState.get(server).forgeBuilt;
	}

	public static void tick(MinecraftServer server) {
		if (++ticks % 40 != 0 || built(server)) {
			return;
		}
		ServerLevel level = nether(server);
		if (level == null) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			if (p.getX() * p.getX() + p.getZ() * p.getZ() < BUILD_RANGE * BUILD_RANGE && level.hasChunk(0, 0)) {
				build(server);
				return;
			}
		}
	}

	/** Carves and furnishes the hall (once per world). */
	public static void build(MinecraftServer server) {
		ServerLevel level = nether(server);
		HellState state = HellState.get(server);
		if (level == null || state.forgeBuilt) {
			return;
		}
		for (int cx = -3; cx <= 2; cx++) {
			for (int cz = -3; cz <= 2; cz++) {
				level.getChunk(cx, cz);
			}
		}
		BlockState bricks = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int x = -RADIUS - 1; x <= RADIUS + 1; x++) {
			for (int z = -RADIUS - 1; z <= RADIUS + 1; z++) {
				double r = Math.sqrt(x * x + z * z);
				if (r > RADIUS + 1.5) {
					continue;
				}
				// the dome: highest in the middle
				int roof = FLOOR_Y + (int) Math.round(HEIGHT - (r * r) / (RADIUS * 2.2));
				boolean wall = r > RADIUS - 0.5;
				for (int d = 1; d <= 4; d++) {
					set(level, x, FLOOR_Y - d, z, d == 1 ? floorBlock(x, z) : Blocks.BLACKSTONE.defaultBlockState());
				}
				for (int y = FLOOR_Y; y <= roof + 1; y++) {
					if (wall || y == roof + 1) {
						set(level, x, y, z, (x * 7 + y * 3 + z * 5) % 11 == 0 ? Blocks.MAGMA_BLOCK.defaultBlockState() : bricks);
					} else {
						set(level, x, y, z, air);
					}
				}
			}
		}
		// eight basalt pillars, with chains from the vault
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4;
			int px = (int) Math.round(Math.cos(a) * 14);
			int pz = (int) Math.round(Math.sin(a) * 14);
			int roof = FLOOR_Y + (int) Math.round(HEIGHT - (14.0 * 14.0) / (RADIUS * 2.2));
			for (int y = FLOOR_Y; y <= roof; y++) {
				set(level, px, y, pz, Blocks.POLISHED_BASALT.defaultBlockState());
			}
			set(level, px, FLOOR_Y + 3, pz, Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
			double b = a + Math.PI / 8;
			int chx = (int) Math.round(Math.cos(b) * 9);
			int chz = (int) Math.round(Math.sin(b) * 9);
			int top = FLOOR_Y + (int) Math.round(HEIGHT - (9.0 * 9.0) / (RADIUS * 2.2));
			for (int y = top; y > top - 7; y--) {
				set(level, chx, y, chz, Blocks.IRON_CHAIN.defaultBlockState());
			}
			set(level, chx, top - 7, chz, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		}
		// four doors, and tunnels out into the Nether
		for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
			for (int step = RADIUS - 2; step <= RADIUS + 14; step++) {
				for (int w = -1; w <= 1; w++) {
					int x = d[0] * step + d[1] * w;
					int z = d[1] * step + d[0] * w;
					set(level, x, FLOOR_Y - 1, z, bricks);
					for (int y = FLOOR_Y; y <= FLOOR_Y + 3; y++) {
						set(level, x, y, z, air);
					}
				}
			}
		}
		// the Hellforge: an anvil on magma between two cauldrons of lava, and what it does
		BlockPos forge = hellforge();
		set(level, forge.getX(), forge.getY() - 1, forge.getZ(), Blocks.MAGMA_BLOCK.defaultBlockState());
		set(level, forge.getX(), forge.getY(), forge.getZ(), Blocks.ANVIL.defaultBlockState());
		set(level, forge.getX() - 1, forge.getY(), forge.getZ(), Blocks.LAVA_CAULDRON.defaultBlockState());
		set(level, forge.getX() + 1, forge.getY(), forge.getZ(), Blocks.LAVA_CAULDRON.defaultBlockState());
		set(level, forge.getX() - 2, forge.getY(), forge.getZ(), Blocks.BLAST_FURNACE.defaultBlockState());
		set(level, forge.getX() + 2, forge.getY(), forge.getZ(), Blocks.SMITHING_TABLE.defaultBlockState());
		Signs.place(level, forge.offset(0, 0, 2), 8, List.of(
				Component.literal("THE HELLFORGE").withStyle(ChatFormatting.BOLD),
				Component.literal("Use blood gear on"),
				Component.literal("the anvil: netherite"),
				Component.literal("+ Blood Fragments")));
		state.forgeBuilt = true;
		state.setDirty();
		HellcraftMod.LOGGER.info("The Great Forge of Dis at {}", center().toShortString());
	}

	/** The floor: blackstone bricks with glowing seams of magma radiating from the middle. */
	private static BlockState floorBlock(int x, int z) {
		boolean seam = x == 0 || z == 0 || x == z || x == -z;
		return seam && (Math.abs(x) + Math.abs(z)) % 3 == 0 ? Blocks.MAGMA_BLOCK.defaultBlockState()
				: Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
	}

	private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
		level.setBlock(new BlockPos(x, y, z), state, 2);
	}
}
