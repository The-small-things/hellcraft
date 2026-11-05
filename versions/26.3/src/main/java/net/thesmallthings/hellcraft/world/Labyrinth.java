package net.thesmallthings.hellcraft.world;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.block.HellBlocks;
import net.thesmallthings.hellcraft.blood.BloodItems;
import net.thesmallthings.hellcraft.blood.HellItems;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.hazard.guardian.Guardian;
import net.thesmallthings.hellcraft.net.Dialogue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Labyrinth of Crete, one for each of the Minotaur's lairs: a sealed maze deep under the Wood of the Suicides, near the
 * bedrock, reached only through an old stone archway on the surface ten blocks out from his arena. Step through the arch
 * and you're taken down to the maze's entrance landing; nothing of it shows above ground. The maze is 15 by 15 cells of
 * corridors two blocks wide and seven high; its walls, roof and floor can't be broken (anything blown open mends itself),
 * so there's no digging in or out. At its heart, in a chamber, lies Ariadne's spool: right-click it for Ariadne's Thread
 * (once each half hour). While you carry the thread in the maze, a red thread along the floor shows the way to the
 * Minotaur's door, whose landing takes you up to the edge of his arena. The entrance landing takes you back up to the arch
 * if you give up. Carried into his fight, the thread makes every charge of his end with him stunned, as if he'd hit the
 * wall, and your blows hit him a fifth harder (MinotaurFight, BossRules); it's used up when he falls. The maze is never
 * needed to fight him.
 *
 * Built once, the first time a player comes near that lair: the vault from a seed of its own (carved by a depth-first
 * walk), and the arch at the ground's height there, kept as "labyrinth_k:y" in HellState.labyrinths.
 *
 * Local coordinates: u runs across (0..SIZE-1), v from the arena side (0) to the far side (SIZE-1); the landings lie past
 * either end (v -4..-1 by the Minotaur's door, SIZE..SIZE+3 by the entrance), inside a ring of wall (v -5 and SIZE+4).
 */
public final class Labyrinth {
	private Labyrinth() {
	}

	/** Cells across, the block period of a cell (a wall and two blocks of corridor), and the maze's size in blocks. */
	static final int CELLS = 15;
	static final int PERIOD = 3;
	static final int SIZE = CELLS * PERIOD + 1;
	/** Corridor height: the roof lies this far above the floor. */
	static final int WALL = 7;
	/** The middle of the maze; the doors are the two blocks of the middle cell's column. */
	static final int MID = SIZE / 2;
	static final int MID_CELL = CELLS / 2;
	static final int DOOR_A = MID_CELL * PERIOD + 1;
	static final int DOOR_B = MID_CELL * PERIOD + 2;
	/** The heart: the cells MID_CELL-1 .. MID_CELL+1 each way, opened into one chamber. */
	static final int HEART_LO = MID_CELL - 1;
	static final int HEART_HI = MID_CELL + 1;
	/** Landings past either end, and the ring of wall round everything. */
	static final int LANDING = 4;
	static final int V_MIN = -LANDING - 1;
	static final int V_MAX = SIZE + LANDING;
	/** The vault's floor lies this far above the world's bottom (clear of the bedrock). */
	static final int DEPTH = 14;
	/** The maze's centre lies this far out from the lair's centre (under the ground beyond the arena), and the arch this far. */
	static final int OFFSET = 26 + MID;
	static final int ARCH = 34;
	/** How near a player comes before a maze is built. */
	private static final double NEAR = 128;
	/** How often a player may take a thread from the spool (ticks). */
	private static final long THREAD_GAP = 20L * 60 * 30;
	/** How many blocks of the way out the thread shows ahead of you. */
	private static final int TRAIL = 40;
	private static final DustParticleOptions THREAD = new DustParticleOptions(0xE01E28, 1.4f);
	private static final Map<UUID, Long> TAKEN = new HashMap<>();
	/** No second trip for a moment after one (ticks of game time), so nobody bounces between arch and landing. */
	private static final Map<UUID, Long> MOVED = new HashMap<>();
	private static final long MOVE_GAP = 60;
	/** Hint bit for the first time a soul comes to an arch (CircleHazards.hintOnce). */
	private static final int HINT = 62;
	/** Each vault's layout (true = solid), by lair, indexed [u][v - V_MIN]. */
	private static final boolean[][][] GRID = new boolean[Guardian.MINOTAUR.lairs()][][];

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			BlockState state = level.getBlockState(hit.getBlockPos());
			if (!state.is(Blocks.LODESTONE) || spoolAt(server, hit.getBlockPos()) < 0) {
				return InteractionResult.PASS;
			}
			takeThread(sp);
			return InteractionResult.SUCCESS;
		});
		// the vault's walls, roof and floor and the arch don't give: the only way to the heart is through the maze
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (!(level instanceof ServerLevel server) || player.isCreative() || !HellWorldgen.isInferno(server) || !protectedBlock(server, pos)) {
				return true;
			}
			if (player instanceof ServerPlayer sp) {
				sp.sendOverlayMessage(Component.literal("The Labyrinth's stones don't give: find the way through.").withStyle(ChatFormatting.GRAY));
			}
			return false;
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> commands(dispatcher));
	}

	public static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (!HellWorldgen.isInferno(level)) {
			return;
		}
		int t = server.getTickCount();
		if (t % 2 == 0) {
			passages(level);
		}
		if (t % 10 == 3) {
			trails(level);
			veils(level);
		}
		if (t % 200 == 77) {
			mend(level);
		}
		if (t % 40 != 11) {
			return;
		}
		for (int k = 0; k < Guardian.MINOTAUR.lairs(); k++) {
			if (archY(level, k) != Integer.MIN_VALUE) {
				// the first time a soul comes to an arch, Virgil says what lies beyond it
				BlockPos a = arch(level, k);
				for (ServerPlayer p : level.players()) {
					if (!p.isSpectator() && p.distanceToSqr(a.getX(), a.getY(), a.getZ()) < 16 * 16) {
						net.thesmallthings.hellcraft.hazard.CircleHazards.hintOnce(p, HINT, "The Labyrinth of Crete lies beneath this arch: step through. "
								+ "Ariadne's Thread waits at its heart and shows you the way out to the Minotaur. Take it into his fight, and his charges will end with him stunned.");
					}
				}
				continue;
			}
			BlockPos column = Guardian.MINOTAUR.lairColumn(k);
			for (ServerPlayer p : level.players()) {
				if (!p.isSpectator() && p.distanceToSqr(column.getX(), p.getY(), column.getZ()) < NEAR * NEAR) {
					build(level, k);
					break;
				}
			}
		}
	}

	private static String id(int k) {
		// (the 3.2 betas' surface mazes were "minotaur_k" and "labyrinth_k:y")
		return "labyrinth_arch_" + k;
	}

	/** The ground height lair k's arch stands at, or Integer.MIN_VALUE if its Labyrinth hasn't been built. */
	static int archY(ServerLevel level, int k) {
		String prefix = id(k) + ":";
		for (String s : HellState.get(level.getServer()).labyrinths) {
			if (s.startsWith(prefix)) {
				try {
					return Integer.parseInt(s.substring(prefix.length()));
				} catch (NumberFormatException e) {
					return Integer.MIN_VALUE;
				}
			}
		}
		return Integer.MIN_VALUE;
	}

	static boolean built(ServerLevel level, int k) {
		return archY(level, k) != Integer.MIN_VALUE;
	}

	/** Which way the maze and its arch lie from lair k: the side facing out of Hell (away from the centre), on a compass axis. */
	public static Direction side(int k) {
		BlockPos c = Guardian.MINOTAUR.lairColumn(k);
		if (Math.abs(c.getX()) >= Math.abs(c.getZ())) {
			return c.getX() >= 0 ? Direction.EAST : Direction.WEST;
		}
		return c.getZ() >= 0 ? Direction.SOUTH : Direction.NORTH;
	}

	/** The vault's floor height. */
	static int floor(ServerLevel level) {
		return level.getMinY() + DEPTH;
	}

	/** The maze's centre at lair k, on the vault's floor. */
	public static BlockPos centre(ServerLevel level, int k) {
		Direction d = side(k);
		BlockPos column = Guardian.MINOTAUR.lairColumn(k);
		return new BlockPos(column.getX() + d.getStepX() * OFFSET, floor(level), column.getZ() + d.getStepZ() * OFFSET);
	}

	/** The middle of lair k's arch on the ground (the floor of its opening), once built. */
	public static BlockPos arch(ServerLevel level, int k) {
		Direction d = side(k);
		BlockPos column = Guardian.MINOTAUR.lairColumn(k);
		int y = archY(level, k);
		if (y == Integer.MIN_VALUE) {
			y = ground(level, column.getX() + d.getStepX() * ARCH, column.getZ() + d.getStepZ() * ARCH);
		}
		return new BlockPos(column.getX() + d.getStepX() * ARCH, y, column.getZ() + d.getStepZ() * ARCH);
	}

	/** The ground's height at x, z, under any of the Wood's trees and thorns. */
	private static int ground(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
		while (pos.getY() > level.getMinY() + DEPTH + WALL + 4) {
			BlockState below = level.getBlockState(pos.below());
			if (!(below.is(BlockTags.LOGS) || below.is(BlockTags.LEAVES) || below.is(HellBlocks.THORNS) || below.canBeReplaced())) {
				break;
			}
			pos.move(Direction.DOWN);
		}
		return pos.getY();
	}

	/** Where the spool lies at lair k: in the middle of the heart, on the floor. */
	public static BlockPos spool(ServerLevel level, int k) {
		return centre(level, k);
	}

	/** World x, z of local (u, v) in lair k's vault. */
	private static int[] world(BlockPos centre, int k, int u, int v) {
		Direction toward = side(k).getOpposite();
		Direction across = toward.getClockWise();
		return new int[]{centre.getX() + (u - MID) * across.getStepX() + (MID - v) * toward.getStepX(),
				centre.getZ() + (u - MID) * across.getStepZ() + (MID - v) * toward.getStepZ()};
	}

	/** Local (u, v) of a world x, z in lair k's vault (may fall outside it). */
	private static int[] local(BlockPos centre, int k, double x, double z) {
		Direction toward = side(k).getOpposite();
		Direction across = toward.getClockWise();
		int ox = (int) Math.floor(x) - centre.getX();
		int oz = (int) Math.floor(z) - centre.getZ();
		return new int[]{MID + ox * across.getStepX() + oz * across.getStepZ(), MID - (ox * toward.getStepX() + oz * toward.getStepZ())};
	}

	private static boolean inside(int[] uv) {
		return uv[0] >= 0 && uv[0] < SIZE && uv[1] >= V_MIN && uv[1] <= V_MAX;
	}

	/** Whether local (u, v) of lair k's vault is solid (wall or ring). */
	static boolean solid(int k, int u, int v) {
		return grid(k)[u][v - V_MIN];
	}

	/** Lair k's layout, carved once: the maze, its landings and the ring round them. */
	static boolean[][] grid(int k) {
		if (GRID[k] == null) {
			boolean[][][] open = carve(k);
			boolean[][] g = new boolean[SIZE][V_MAX - V_MIN + 1];
			for (int u = 0; u < SIZE; u++) {
				for (int v = V_MIN; v <= V_MAX; v++) {
					boolean landing = u >= DOOR_A - 1 && u <= DOOR_B + 1 && ((v >= -LANDING && v < 0) || (v >= SIZE && v < SIZE + LANDING));
					g[u][v - V_MIN] = v >= 0 && v < SIZE ? wall(open, u, v) : !landing;
				}
			}
			GRID[k] = g;
		}
		return GRID[k];
	}

	/** True on a pad: the Minotaur's (v = -LANDING, up to his arena) or the entrance's (v = SIZE+LANDING-1, up to the arch). */
	private static boolean minotaurPad(int[] uv) {
		return uv[1] == -LANDING && (uv[0] == DOOR_A || uv[0] == DOOR_B);
	}

	private static boolean entrancePad(int[] uv) {
		return uv[1] == SIZE + LANDING - 1 && (uv[0] == DOOR_A || uv[0] == DOOR_B);
	}

	/** True for a block of a built vault's walls, roof or floor, or of an arch (the parts that can't be broken). */
	static boolean protectedBlock(ServerLevel level, BlockPos pos) {
		int y = floor(level);
		for (int k = 0; k < Guardian.MINOTAUR.lairs(); k++) {
			if (!built(level, k)) {
				continue;
			}
			if (pos.getY() >= y - 1 && pos.getY() <= y + WALL) {
				int[] uv = local(centre(level, k), k, pos.getX(), pos.getZ());
				if (inside(uv)) {
					return pos.getY() == y - 1 || pos.getY() == y + WALL || solid(k, uv[0], uv[1]);
				}
			}
			if (archBlock(level, k, pos)) {
				return true;
			}
		}
		return false;
	}

	/** True for a stone of lair k's arch (its two pillars, its lintel and the sill of its opening). */
	private static boolean archBlock(ServerLevel level, int k, BlockPos pos) {
		BlockPos a = arch(level, k);
		Direction out = side(k);
		Direction across = out.getClockWise();
		int dx = pos.getX() - a.getX();
		int dz = pos.getZ() - a.getZ();
		int along = dx * out.getStepX() + dz * out.getStepZ();
		int side = dx * across.getStepX() + dz * across.getStepZ();
		int h = pos.getY() - a.getY();
		if (along < 0 || along > 1 || side < -2 || side > 2 || h < -1 || h > 4) {
			return false;
		}
		return h == -1 || h == 4 || Math.abs(side) == 2;
	}

	/** True inside lair k's arch's opening (3 wide, 4 high, 2 deep). */
	private static boolean inOpening(ServerLevel level, int k, double x, double y, double z) {
		BlockPos a = arch(level, k);
		Direction out = side(k);
		Direction across = out.getClockWise();
		int dx = (int) Math.floor(x) - a.getX();
		int dz = (int) Math.floor(z) - a.getZ();
		int along = dx * out.getStepX() + dz * out.getStepZ();
		int side = dx * across.getStepX() + dz * across.getStepZ();
		int h = (int) Math.floor(y) - a.getY();
		return along >= 0 && along <= 1 && side >= -1 && side <= 1 && h >= 0 && h <= 3;
	}

	/** Which lair's spool this block is, or -1. */
	static int spoolAt(ServerLevel level, BlockPos pos) {
		for (int k = 0; k < Guardian.MINOTAUR.lairs(); k++) {
			if (built(level, k) && spool(level, k).equals(pos)) {
				return k;
			}
		}
		return -1;
	}

	private static void takeThread(ServerPlayer player) {
		long now = player.level().getGameTime();
		Long last = TAKEN.get(player.getUUID());
		if (last != null && now - last < THREAD_GAP) {
			player.sendOverlayMessage(Component.literal("The spool is bare for now: come back in " + (THREAD_GAP - (now - last)) / 1200 + " min.")
					.withStyle(ChatFormatting.GRAY));
			return;
		}
		TAKEN.put(player.getUUID(), now);
		BloodItems.give(player, new ItemStack(HellItems.ARIADNES_THREAD));
		player.level().playSound(null, player.blockPosition(), SoundEvents.WOOL_BREAK, SoundSource.PLAYERS, 1.0f, 0.8f);
		Dialogue.notice(player, Component.literal("You took Ariadne's Thread. Follow the red thread along the floor: it leads to the Minotaur's door, "
				+ "and up to his arena. In his fight, each of his charges ends with him stunned, and you hit him harder. It's used up when he falls.")
				.withStyle(ChatFormatting.RED));
	}

	/** Carves the maze for lair k: a depth-first walk over the cells from a seed of its own. Returns, per cell, its open sides. */
	static boolean[][][] carve(int k) {
		// open[cx][cz][0..3]: east (+u), south (+v), west, north
		boolean[][][] open = new boolean[CELLS][CELLS][4];
		boolean[][] seen = new boolean[CELLS][CELLS];
		RandomSource random = RandomSource.create(0x1ab7_0000L + k * 7919L);
		Deque<int[]> stack = new ArrayDeque<>();
		stack.push(new int[]{MID_CELL, CELLS - 1});
		seen[MID_CELL][CELLS - 1] = true;
		int[][] step = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
		while (!stack.isEmpty()) {
			int[] at = stack.peek();
			List<Integer> ways = new ArrayList<>();
			for (int i = 0; i < 4; i++) {
				int nx = at[0] + step[i][0];
				int nz = at[1] + step[i][1];
				if (nx >= 0 && nz >= 0 && nx < CELLS && nz < CELLS && !seen[nx][nz]) {
					ways.add(i);
				}
			}
			if (ways.isEmpty()) {
				stack.pop();
				continue;
			}
			int i = ways.get(random.nextInt(ways.size()));
			int nx = at[0] + step[i][0];
			int nz = at[1] + step[i][1];
			open[at[0]][at[1]][i] = true;
			open[nx][nz][(i + 2) % 4] = true;
			seen[nx][nz] = true;
			stack.push(new int[]{nx, nz});
		}
		// the heart: its nine cells open into one chamber
		for (int cx = HEART_LO; cx <= HEART_HI; cx++) {
			for (int cz = HEART_LO; cz <= HEART_HI; cz++) {
				if (cx < HEART_HI) {
					open[cx][cz][0] = true;
					open[cx + 1][cz][2] = true;
				}
				if (cz < HEART_HI) {
					open[cx][cz][1] = true;
					open[cx][cz + 1][3] = true;
				}
			}
		}
		return open;
	}

	private static boolean heartCell(int cx, int cz) {
		return cx >= HEART_LO && cx <= HEART_HI && cz >= HEART_LO && cz <= HEART_HI;
	}

	/** Whether (u, v) in the maze proper is wall: the grid between cells, opened where the walk passed, the heart and the two doors. */
	static boolean wall(boolean[][][] open, int u, int v) {
		boolean doorColumn = u == DOOR_A || u == DOOR_B;
		if (doorColumn && (v == 0 || v == SIZE - 1)) {
			return false;
		}
		if (u == 0 || v == 0 || u == SIZE - 1 || v == SIZE - 1) {
			return true;
		}
		boolean lineU = u % PERIOD == 0;
		boolean lineV = v % PERIOD == 0;
		if (!lineU && !lineV) {
			return false;
		}
		if (lineU && lineV) {
			// a corner post: gone only inside the heart, where four heart cells meet
			return !(heartCell(u / PERIOD - 1, v / PERIOD - 1) && heartCell(u / PERIOD, v / PERIOD));
		}
		if (lineU) {
			// between (u/P - 1, v/P) and (u/P, v/P): open if the walk went east from the western one
			return !open[u / PERIOD - 1][v / PERIOD][0];
		}
		return !open[u / PERIOD][v / PERIOD - 1][1];
	}

	/** Builds lair k's Labyrinth: the vault deep under the ground, and the arch on the surface. */
	public static void build(ServerLevel level, int k) {
		HellState state = HellState.get(level.getServer());
		BlockPos archAt = arch(level, k);
		state.labyrinths.removeIf(s -> s.startsWith(id(k) + ":"));
		state.labyrinths.add(id(k) + ":" + archAt.getY());
		state.setDirty();
		buildVault(level, k);
		buildArch(level, k);
		HellcraftMod.LOGGER.info("Labyrinth built beside the Minotaur's lair {}: vault at {}, arch at {}", k, centre(level, k).toShortString(),
				arch(level, k).toShortString());
	}

	private static void buildVault(ServerLevel level, int k) {
		BlockPos centre = centre(level, k);
		int y = centre.getY();
		RandomSource random = RandomSource.create(0x2c4f_0000L + k * 104729L);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int u = 0; u < SIZE; u++) {
			for (int v = V_MIN; v <= V_MAX; v++) {
				int[] xz = world(centre, k, u, v);
				int x = xz[0];
				int z = xz[1];
				level.getChunk(x >> 4, z >> 4);
				boolean wall = solid(k, u, v);
				// a sealed shell of deepslate under the floor and over the roof, the floor, the walls or air, the roof
				level.setBlock(pos.set(x, y - 2, z), Blocks.DEEPSLATE.defaultBlockState(), 2);
				level.setBlock(pos.set(x, y - 1, z), floor(random), 2);
				for (int h = 0; h < WALL; h++) {
					level.setBlock(pos.set(x, y + h, z), wall ? stone(random) : Blocks.AIR.defaultBlockState(), 2);
				}
				level.setBlock(pos.set(x, y + WALL, z), roof(random), 2);
				level.setBlock(pos.set(x, y + WALL + 1, z), Blocks.DEEPSLATE.defaultBlockState(), 2);
				if (wall) {
					continue;
				}
				int[] uv = {u, v};
				if (minotaurPad(uv) || entrancePad(uv)) {
					// the pads: chiselled stone under a hanging light
					level.setBlock(pos.set(x, y - 1, z), Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), 2);
					level.setBlock(pos.set(x, y + WALL - 1, z), Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);
					continue;
				}
				boolean cell = u % PERIOD == 1 && v % PERIOD == 1 && v >= 0 && v < SIZE;
				boolean heart = heartCell(u / PERIOD, v / PERIOD);
				if (cell && !heart) {
					float d = random.nextFloat();
					if (d < 0.45f) {
						// soul lanterns hung from the roof, so the way can be seen
						level.setBlock(pos.set(x, y + WALL - 1, z), Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);
					} else if (d < 0.52f) {
						level.setBlock(pos.set(x, y + WALL - 1, z), Blocks.COBWEB.defaultBlockState(), 2);
					} else if (d < 0.57f) {
						level.setBlock(pos.set(x, y, z), Blocks.BONE_BLOCK.defaultBlockState(), 2);
					}
				}
			}
		}
		// the heart: lanterns on chains at its four corners, bones about, and the spool in the middle
		int lo = HEART_LO * PERIOD + 1;
		int hi = HEART_HI * PERIOD + 2;
		for (int[] corner : new int[][]{{lo + 1, lo + 1}, {lo + 1, hi - 1}, {hi - 1, lo + 1}, {hi - 1, hi - 1}}) {
			int[] xz = world(centre, k, corner[0], corner[1]);
			level.setBlock(pos.set(xz[0], y + WALL - 1, xz[1]), Blocks.IRON_CHAIN.defaultBlockState(), 2);
			level.setBlock(pos.set(xz[0], y + WALL - 2, xz[1]), Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);
		}
		for (int i = 0; i < 6; i++) {
			int[] xz = world(centre, k, lo + random.nextInt(hi - lo + 1), lo + random.nextInt(hi - lo + 1));
			if (xz[0] != centre.getX() || xz[1] != centre.getZ()) {
				level.setBlock(pos.set(xz[0], y, xz[1]), random.nextBoolean() ? Blocks.BONE_BLOCK.defaultBlockState() : Blocks.SKELETON_SKULL.defaultBlockState(), 2);
			}
		}
		level.setBlock(spool(level, k), Blocks.LODESTONE.defaultBlockState(), 3);
	}

	/** The arch: two pillars and a lintel of old mossy stone over a sill, its opening cleared, thorns grown over the top. */
	private static void buildArch(ServerLevel level, int k) {
		BlockPos a = arch(level, k);
		Direction out = side(k);
		Direction across = out.getClockWise();
		RandomSource random = RandomSource.create(0x3d1a_0000L + k * 31L);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int along = -1; along <= 2; along++) {
			for (int side = -3; side <= 3; side++) {
				int x = a.getX() + out.getStepX() * along + across.getStepX() * side;
				int z = a.getZ() + out.getStepZ() * along + across.getStepZ() * side;
				for (int h = -1; h <= 6; h++) {
					pos.set(x, a.getY() + h, z);
					if (archBlock(level, k, pos)) {
						BlockState stone = h == 4 && side == 0 ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState() : h == -1
								? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : stone(random);
						level.setBlock(pos, stone, 3);
					} else if (h >= 0 && along >= 0 && along <= 1 && Math.abs(side) <= 1 && h <= 3) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
					} else if (h == 5 && along >= 0 && along <= 1 && Math.abs(side) <= 2 && random.nextFloat() < 0.5f) {
						level.setBlock(pos, HellBlocks.THORNS.defaultBlockState(), 3);
					}
				}
				// a footing under the pillars, down to the ground
				if (along >= 0 && along <= 1 && Math.abs(side) <= 2) {
					for (int yy = a.getY() - 2; yy > a.getY() - 8 && level.getBlockState(pos.set(x, yy, z)).canBeReplaced(); yy--) {
						level.setBlock(pos, Blocks.STONE_BRICKS.defaultBlockState(), 3);
					}
				}
			}
		}
	}

	/** Takes souls through: the arch down to the entrance landing, the entrance pad back up, the Minotaur's pad up to his arena. */
	private static void passages(ServerLevel level) {
		long now = level.getGameTime();
		int y = floor(level);
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator() || now - MOVED.getOrDefault(p.getUUID(), -MOVE_GAP) < MOVE_GAP) {
				continue;
			}
			for (int k = 0; k < Guardian.MINOTAUR.lairs(); k++) {
				if (!built(level, k)) {
					continue;
				}
				if (p.getY() > y + WALL + 2) {
					if (inOpening(level, k, p.getX(), p.getY(), p.getZ())) {
						down(level, p, k);
						break;
					}
					continue;
				}
				if (Math.floor(p.getY()) != y) {
					continue;
				}
				int[] uv = local(centre(level, k), k, p.getX(), p.getZ());
				if (minotaurPad(uv)) {
					up(level, p, k, true);
					break;
				}
				if (entrancePad(uv)) {
					up(level, p, k, false);
					break;
				}
			}
		}
	}

	private static float yaw(Direction facing) {
		return (float) Math.toDegrees(Math.atan2(-facing.getStepX(), facing.getStepZ()));
	}

	private static void move(ServerLevel level, ServerPlayer player, double x, double y, double z, float yaw) {
		MOVED.put(player.getUUID(), level.getGameTime());
		player.stopRiding();
		level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1, player.getZ(), 20, 0.4, 0.8, 0.4, 0.02);
		player.teleportTo(level, x, y, z, java.util.Set.of(), yaw, 0f, true);
		player.resetFallDistance();
		player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, true, false, false));
		level.playSound(null, x, y, z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.2f, 0.6f);
		level.playSound(null, x, y, z, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.6f, 0.5f);
	}

	/** Through the arch: down to the entrance landing, facing into the maze. */
	private static void down(ServerLevel level, ServerPlayer player, int k) {
		BlockPos centre = centre(level, k);
		int[] a = world(centre, k, DOOR_A, SIZE + 1);
		int[] b = world(centre, k, DOOR_B, SIZE + 1);
		move(level, player, (a[0] + b[0]) / 2.0 + 0.5, centre.getY(), (a[1] + b[1]) / 2.0 + 0.5, yaw(side(k).getOpposite()));
		player.sendOverlayMessage(Component.literal("The Labyrinth of Crete. The pad behind you leads back up.").withStyle(ChatFormatting.GRAY));
	}

	/** Up from a pad: to the edge of the Minotaur's arena, facing it, or back out in front of the arch. */
	private static void up(ServerLevel level, ServerPlayer player, int k, boolean toArena) {
		Direction out = side(k);
		BlockPos column = Guardian.MINOTAUR.lairColumn(k);
		int along = toArena ? 27 : ARCH + 4;
		int x = column.getX() + out.getStepX() * along;
		int z = column.getZ() + out.getStepZ() * along;
		level.getChunk(x >> 4, z >> 4);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		move(level, player, x + 0.5, y, z + 0.5, yaw(out.getOpposite()));
		if (toArena) {
			player.sendOverlayMessage(Component.literal("Out of the Labyrinth: the Minotaur's arena lies ahead.").withStyle(ChatFormatting.RED));
		}
	}

	/** A dark veil in each arch's opening, so it reads as a way somewhere. */
	private static void veils(ServerLevel level) {
		for (int k = 0; k < Guardian.MINOTAUR.lairs(); k++) {
			if (!built(level, k)) {
				continue;
			}
			BlockPos a = arch(level, k);
			if (level.getNearestPlayer(a.getX(), a.getY(), a.getZ(), 40, false) == null) {
				continue;
			}
			Direction out = side(k);
			double x = a.getX() + 0.5 + out.getStepX() * 0.5;
			double z = a.getZ() + 0.5 + out.getStepZ() * 0.5;
			double sx = out.getStepX() == 0 ? 1.0 : 0.15;
			double sz = out.getStepZ() == 0 ? 1.0 : 0.15;
			level.sendParticles(ParticleTypes.SQUID_INK, x, a.getY() + 1.8, z, 6, sx, 1.0, sz, 0.0);
			level.sendParticles(ParticleTypes.SOUL, x, a.getY() + 0.6, z, 1, sx, 0.4, sz, 0.01);
		}
	}

	/** Puts back any wall, roof, floor or arch block that something (an explosion, a piston) took out, if no one stands in it. */
	private static void mend(ServerLevel level) {
		int y = floor(level);
		for (int k = 0; k < Guardian.MINOTAUR.lairs(); k++) {
			if (!built(level, k)) {
				continue;
			}
			BlockPos centre = centre(level, k);
			BlockPos a = arch(level, k);
			boolean near = false;
			for (ServerPlayer p : level.players()) {
				if (p.distanceToSqr(centre.getX(), centre.getY(), centre.getZ()) < 96 * 96 || p.distanceToSqr(a.getX(), a.getY(), a.getZ()) < 96 * 96) {
					near = true;
					break;
				}
			}
			if (!near) {
				continue;
			}
			RandomSource random = RandomSource.create();
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			int mended = 0;
			for (int u = 0; u < SIZE; u++) {
				for (int v = V_MIN; v <= V_MAX; v++) {
					int[] xz = world(centre, k, u, v);
					if (!level.isLoaded(pos.set(xz[0], y, xz[1]))) {
						continue;
					}
					boolean wall = solid(k, u, v);
					for (int yy = y - 1; yy <= y + WALL; yy++) {
						if (!(yy == y - 1 || yy == y + WALL || wall)) {
							continue;
						}
						pos.set(xz[0], yy, xz[1]);
						if (mendable(level, pos)) {
							level.setBlock(pos, yy == y - 1 ? floor(random) : yy == y + WALL ? roof(random) : stone(random), 3);
							mended++;
						}
					}
				}
			}
			Direction out = side(k);
			Direction across = out.getClockWise();
			for (int along = 0; along <= 1; along++) {
				for (int side = -2; side <= 2; side++) {
					for (int h = -1; h <= 4; h++) {
						pos.set(a.getX() + out.getStepX() * along + across.getStepX() * side, a.getY() + h, a.getZ() + out.getStepZ() * along + across.getStepZ() * side);
						if (archBlock(level, k, pos) && mendable(level, pos)) {
							level.setBlock(pos, stone(random), 3);
							mended++;
						}
					}
				}
			}
			BlockPos spool = spool(level, k);
			if (level.isLoaded(spool) && level.getBlockState(spool).canBeReplaced()) {
				level.setBlock(spool, Blocks.LODESTONE.defaultBlockState(), 3);
				mended++;
			}
			if (mended > 0) {
				HellcraftMod.LOGGER.info("Labyrinth {} mended {} blocks", k, mended);
			}
		}
	}

	private static boolean mendable(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).canBeReplaced() && level.getEntitiesOfClass(Player.class, new AABB(pos)).isEmpty();
	}

	/** Shows each soul carrying the thread in a maze the way to the Minotaur's pad: a red thread along the floor. */
	private static void trails(ServerLevel level) {
		int y = floor(level);
		for (ServerPlayer p : level.players()) {
			if (p.getY() < y - 1 || p.getY() > y + WALL || !p.getInventory().contains(new ItemStack(HellItems.ARIADNES_THREAD))) {
				continue;
			}
			for (int k = 0; k < Guardian.MINOTAUR.lairs(); k++) {
				if (!built(level, k)) {
					continue;
				}
				BlockPos centre = centre(level, k);
				int[] at = local(centre, k, p.getX(), p.getZ());
				if (!inside(at)) {
					continue;
				}
				List<int[]> path = wayOut(k, at);
				for (int i = 0; i < Math.min(TRAIL, path.size()); i++) {
					int[] xz = world(centre, k, path.get(i)[0], path.get(i)[1]);
					level.sendParticles(p, THREAD, true, true, xz[0] + 0.5, y + 0.15, xz[1] + 0.5, 4, 0.15, 0.02, 0.15, 0);
				}
				break;
			}
		}
	}

	/** The shortest way over open blocks from (u, v) to the Minotaur's pad, from the start; empty if none. */
	static List<int[]> wayOut(int k, int[] from) {
		List<int[]> path = new ArrayList<>();
		if (solid(k, from[0], from[1])) {
			return path;
		}
		int rows = V_MAX - V_MIN + 1;
		int[][] prev = new int[SIZE * rows][];
		boolean[][] seen = new boolean[SIZE][rows];
		Deque<int[]> queue = new ArrayDeque<>();
		queue.add(from);
		seen[from[0]][from[1] - V_MIN] = true;
		int[] end = null;
		while (!queue.isEmpty()) {
			int[] a = queue.poll();
			if (minotaurPad(a)) {
				end = a;
				break;
			}
			for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				int nu = a[0] + d[0];
				int nv = a[1] + d[1];
				if (nu >= 0 && nu < SIZE && nv >= V_MIN && nv <= V_MAX && !seen[nu][nv - V_MIN] && !solid(k, nu, nv)) {
					seen[nu][nv - V_MIN] = true;
					prev[nu * rows + nv - V_MIN] = a;
					queue.add(new int[]{nu, nv});
				}
			}
		}
		for (int[] a = end; a != null; a = prev[a[0] * rows + a[1] - V_MIN]) {
			path.add(0, a);
		}
		return path;
	}

	private static BlockState stone(RandomSource random) {
		float r = random.nextFloat();
		return r < 0.45f ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : r < 0.8f ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
				: Blocks.STONE_BRICKS.defaultBlockState();
	}

	private static BlockState roof(RandomSource random) {
		float r = random.nextFloat();
		return r < 0.5f ? Blocks.STONE_BRICKS.defaultBlockState() : r < 0.8f ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
				: Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
	}

	private static BlockState floor(RandomSource random) {
		float r = random.nextFloat();
		return r < 0.5f ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : r < 0.8f ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
				: Blocks.BLACKSTONE.defaultBlockState();
	}

	/**
	 * For the smoke test: in every vault, every open block can be reached from the entrance landing, the heart and the
	 * Minotaur's pad among them, and the thread leads from the heart to that pad.
	 */
	public static String selfTest() {
		List<String> wrong = new ArrayList<>();
		int rows = V_MAX - V_MIN + 1;
		for (int k = 0; k < Guardian.MINOTAUR.lairs(); k++) {
			boolean[][] seen = new boolean[SIZE][rows];
			Deque<int[]> queue = new ArrayDeque<>();
			int[] start = {DOOR_A, SIZE + 1};
			queue.add(start);
			seen[start[0]][start[1] - V_MIN] = true;
			int reached = 0;
			int open = 0;
			boolean pad = false;
			while (!queue.isEmpty()) {
				int[] at = queue.poll();
				reached++;
				pad |= minotaurPad(at);
				for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					int nu = at[0] + d[0];
					int nv = at[1] + d[1];
					if (nu >= 0 && nu < SIZE && nv >= V_MIN && nv <= V_MAX && !seen[nu][nv - V_MIN] && !solid(k, nu, nv)) {
						seen[nu][nv - V_MIN] = true;
						queue.add(new int[]{nu, nv});
					}
				}
			}
			for (int u = 0; u < SIZE; u++) {
				for (int v = V_MIN; v <= V_MAX; v++) {
					open += solid(k, u, v) ? 0 : 1;
				}
			}
			if (!pad) {
				wrong.add("maze " + k + " has no way through");
			}
			if (!seen[MID][MID - V_MIN]) {
				wrong.add("maze " + k + " has its heart walled off");
			}
			if (reached < open) {
				wrong.add("maze " + k + " has corridors walled off");
			}
			List<int[]> out = wayOut(k, new int[]{MID, MID});
			if (out.isEmpty() || !minotaurPad(out.get(out.size() - 1))) {
				wrong.add("maze " + k + "'s thread leads nowhere");
			}
		}
		return wrong.isEmpty() ? "Labyrinth test OK: " + Guardian.MINOTAUR.lairs() + " mazes, each with a way to its heart and out to the Minotaur"
				: "Labyrinth test FAILED: " + String.join(", ", wrong);
	}

	private static void commands(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("hellcraft")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("labyrinth")
						.then(Commands.literal("test").executes(ctx -> {
							String result = selfTest();
							HellcraftMod.LOGGER.info(result);
							ctx.getSource().sendSuccess(() -> Component.literal(result), false);
							return 1;
						}))
						.then(Commands.literal("build").executes(ctx -> {
							ServerLevel level = ctx.getSource().getServer().overworld();
							int k = Guardian.MINOTAUR.nearestLair(ctx.getSource().getPosition().x, ctx.getSource().getPosition().z);
							build(level, k);
							ctx.getSource().sendSuccess(() -> Component.literal("Built the Labyrinth beside the Minotaur's lair " + k + ": its arch at "
									+ arch(level, k).toShortString() + ", its heart (the spool) at " + spool(level, k).toShortString() + "."), false);
							return 1;
						}))));
	}
}
