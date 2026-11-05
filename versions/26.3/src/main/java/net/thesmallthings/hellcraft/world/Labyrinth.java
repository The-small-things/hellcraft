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
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
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
import net.thesmallthings.hellcraft.hazard.guardian.GuardianManager;
import net.thesmallthings.hellcraft.net.Dialogue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Labyrinth of Crete, beside each of the Minotaur's lairs: a roofed maze of mossy, cracked stone, 15 by 15 cells of
 * corridors two blocks wide and seven high, with one door on its far side and one opening onto his arena. Its walls, roof
 * and floor can't be broken (and mend themselves if something blows them open), and the roof keeps anyone from climbing
 * over, so the only way to its heart is through it. At the heart, in a chamber, lies Ariadne's spool: right-click it for
 * Ariadne's Thread (once each half hour). While you carry the thread inside the maze, a red thread along the floor shows the
 * way out to the Minotaur's door. Carried into his fight, the thread makes every charge of his end with him stunned, as if
 * he'd hit the wall, and your blows hit him a fifth harder (MinotaurFight, BossRules); it's used up when he falls. Walking
 * the maze is never needed to fight him: the arena is open on every other side.
 *
 * Each maze is built once, the first time a player comes near that lair (HellState.labyrinths keeps "labyrinth_k:y", its
 * floor height, so protection and mending never drift), from a seed of its own, carved by a depth-first walk.
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
	/** The middle of the maze in local coordinates; the doors are the two blocks of the middle cell's column. */
	static final int MID = SIZE / 2;
	static final int MID_CELL = CELLS / 2;
	/** The heart: the cells MID_CELL-1 .. MID_CELL+1 each way, opened into one chamber. */
	static final int HEART_LO = MID_CELL - 1;
	static final int HEART_HI = MID_CELL + 1;
	/** The maze's centre lies this far from the lair's centre (the arena is 24 out; the maze starts 2 blocks past it). */
	static final int OFFSET = 26 + MID;
	/** How near a player comes before a maze is built. */
	private static final double NEAR = 128;
	/** How often a player may take a thread from the spool (ticks). */
	private static final long THREAD_GAP = 20L * 60 * 30;
	/** How many blocks of the way out the thread shows ahead of you. */
	private static final int TRAIL = 32;
	private static final DustParticleOptions THREAD = new DustParticleOptions(0xC0141C, 0.9f);
	private static final Map<UUID, Long> TAKEN = new HashMap<>();
	/** Hint bit for the first time a soul comes to a maze (CircleHazards.hintOnce). */
	private static final int HINT = 62;
	/** Each maze's layout (true = wall), by lair. */
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
		// the walls, roof and floor don't give: the only way to the heart is through the maze
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (!(level instanceof ServerLevel server) || player.isCreative() || !HellWorldgen.isInferno(server) || !protectedBlock(server, pos)) {
				return true;
			}
			if (player instanceof ServerPlayer sp) {
				sp.sendOverlayMessage(Component.literal("The Labyrinth's walls don't give: find the way through.").withStyle(ChatFormatting.GRAY));
			}
			return false;
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> commands(dispatcher));
	}

	public static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		int t = server.getTickCount();
		if (t % 10 == 3 && HellWorldgen.isInferno(level)) {
			trails(level);
		}
		if (t % 200 == 77 && HellWorldgen.isInferno(level)) {
			mend(level);
		}
		if (t % 40 != 11 || !HellWorldgen.isInferno(level)) {
			return;
		}
		for (int k = 0; k < Guardian.MINOTAUR.lairs(); k++) {
			if (floorY(level, k) != Integer.MIN_VALUE) {
				// the first time a soul comes to a maze, Virgil says what lies inside
				BlockPos c = centre(level, k);
				for (ServerPlayer p : level.players()) {
					if (!p.isSpectator() && p.distanceToSqr(c.getX(), c.getY(), c.getZ()) < 40 * 40) {
						net.thesmallthings.hellcraft.hazard.CircleHazards.hintOnce(p, HINT, "The Labyrinth of Crete. Ariadne's Thread lies at its heart: "
								+ "find it, and it will show you the way out to the Minotaur's door. Take it into his fight, and his charges will end with him stunned.");
					}
				}
				continue;
			}
			BlockPos column = Guardian.MINOTAUR.lairColumn(k);
			for (ServerPlayer p : level.players()) {
				if (p.isSpectator()) {
					continue;
				}
				if (p.distanceToSqr(column.getX(), p.getY(), column.getZ()) < NEAR * NEAR) {
					build(level, k);
					break;
				}
			}
		}
	}

	private static String id(int k) {
		return "labyrinth_" + k;
	}

	/** The floor height lair k's maze was built at, or Integer.MIN_VALUE if it hasn't been built. */
	static int floorY(ServerLevel level, int k) {
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

	/** Which way the maze lies from lair k: the side facing out of Hell (away from the centre), on a compass axis. */
	public static Direction side(int k) {
		BlockPos c = Guardian.MINOTAUR.lairColumn(k);
		if (Math.abs(c.getX()) >= Math.abs(c.getZ())) {
			return c.getX() >= 0 ? Direction.EAST : Direction.WEST;
		}
		return c.getZ() >= 0 ? Direction.SOUTH : Direction.NORTH;
	}

	/** The maze's centre at lair k (y: its floor, the arena's height when it was built). */
	public static BlockPos centre(ServerLevel level, int k) {
		Direction d = side(k);
		BlockPos column = Guardian.MINOTAUR.lairColumn(k);
		int y = floorY(level, k);
		if (y == Integer.MIN_VALUE) {
			y = GuardianManager.lair(level, Guardian.MINOTAUR, k).getY();
		}
		return new BlockPos(column.getX() + d.getStepX() * OFFSET, y, column.getZ() + d.getStepZ() * OFFSET);
	}

	/** Where the spool lies at lair k: in the middle of the heart, on the floor. */
	public static BlockPos spool(ServerLevel level, int k) {
		return centre(level, k);
	}

	/** World x, z of local (u, v) in lair k's maze: u runs across, v from the arena side (0) to the far side (SIZE-1). */
	private static int[] world(BlockPos centre, int k, int u, int v) {
		Direction toward = side(k).getOpposite();
		Direction across = toward.getClockWise();
		return new int[]{centre.getX() + (u - MID) * across.getStepX() + (MID - v) * toward.getStepX(),
				centre.getZ() + (u - MID) * across.getStepZ() + (MID - v) * toward.getStepZ()};
	}

	/** Local (u, v) of a world x, z in lair k's maze (may fall outside 0..SIZE-1). */
	private static int[] local(BlockPos centre, int k, double x, double z) {
		Direction toward = side(k).getOpposite();
		Direction across = toward.getClockWise();
		int ox = (int) Math.floor(x) - centre.getX();
		int oz = (int) Math.floor(z) - centre.getZ();
		return new int[]{MID + ox * across.getStepX() + oz * across.getStepZ(), MID - (ox * toward.getStepX() + oz * toward.getStepZ())};
	}

	private static boolean inside(int[] uv) {
		return uv[0] >= 0 && uv[1] >= 0 && uv[0] < SIZE && uv[1] < SIZE;
	}

	/** Lair k's layout, carved once. */
	static boolean[][] grid(int k) {
		if (GRID[k] == null) {
			boolean[][][] open = carve(k);
			boolean[][] g = new boolean[SIZE][SIZE];
			for (int u = 0; u < SIZE; u++) {
				for (int v = 0; v < SIZE; v++) {
					g[u][v] = wall(open, u, v);
				}
			}
			GRID[k] = g;
		}
		return GRID[k];
	}

	/** True for a block of a built maze's walls, roof or floor (the parts that can't be broken). */
	static boolean protectedBlock(ServerLevel level, BlockPos pos) {
		for (int k = 0; k < Guardian.MINOTAUR.lairs(); k++) {
			int y = floorY(level, k);
			if (y == Integer.MIN_VALUE || pos.getY() < y - 1 || pos.getY() > y + WALL) {
				continue;
			}
			int[] uv = local(centre(level, k), k, pos.getX(), pos.getZ());
			if (!inside(uv)) {
				continue;
			}
			return pos.getY() == y - 1 || pos.getY() == y + WALL || grid(k)[uv[0]][uv[1]];
		}
		return false;
	}

	/** Which lair's spool this block is, or -1. */
	static int spoolAt(ServerLevel level, BlockPos pos) {
		for (int k = 0; k < Guardian.MINOTAUR.lairs(); k++) {
			if (floorY(level, k) != Integer.MIN_VALUE && spool(level, k).equals(pos)) {
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
		Dialogue.notice(player, Component.literal("You took Ariadne's Thread. Follow the red thread along the floor: it leads out to the Minotaur's door. "
				+ "In his fight, each of his charges ends with him stunned, and you hit him harder. It's used up when he falls.").withStyle(ChatFormatting.RED));
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

	/** Whether (u, v) in the maze is wall: the grid between cells, opened where the walk passed, the heart and the two doors. */
	static boolean wall(boolean[][][] open, int u, int v) {
		boolean doorColumn = u == MID_CELL * PERIOD + 1 || u == MID_CELL * PERIOD + 2;
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

	/** Builds lair k's maze, with its floor at the arena's height. */
	public static void build(ServerLevel level, int k) {
		HellState state = HellState.get(level.getServer());
		state.labyrinths.removeIf(s -> s.startsWith(id(k) + ":"));
		int y = GuardianManager.lair(level, Guardian.MINOTAUR, k).getY();
		state.labyrinths.add(id(k) + ":" + y);
		state.setDirty();
		BlockPos centre = centre(level, k);
		RandomSource random = RandomSource.create(0x2c4f_0000L + k * 104729L);
		boolean[][] grid = grid(k);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int u = 0; u < SIZE; u++) {
			for (int v = 0; v < SIZE; v++) {
				int[] xz = world(centre, k, u, v);
				int x = xz[0];
				int z = xz[1];
				level.getChunk(x >> 4, z >> 4);
				// a floor at the arena's height, on a footing down to the ground, and the air above cleared (trees too)
				int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				for (int yy = Math.min(ground, y - 1); yy < y; yy++) {
					level.setBlock(pos.set(x, yy, z), yy == y - 1 ? floor(random) : Blocks.BLACKSTONE.defaultBlockState(), 2);
				}
				for (int yy = y; yy < Math.max(ground, y) + WALL + 14; yy++) {
					level.setBlock(pos.set(x, yy, z), Blocks.AIR.defaultBlockState(), 2);
				}
				if (grid[u][v]) {
					for (int h = 0; h < WALL; h++) {
						level.setBlock(pos.set(x, y + h, z), stone(random), 2);
					}
				}
				level.setBlock(pos.set(x, y + WALL, z), roof(random), 2);
				// moss and a few of the Wood's thorns over the roof
				float r = random.nextFloat();
				if (r < 0.06f) {
					level.setBlock(pos.set(x, y + WALL + 1, z), HellBlocks.THORNS.defaultBlockState(), 2);
				} else if (r < 0.3f) {
					level.setBlock(pos.set(x, y + WALL + 1, z), Blocks.MOSS_CARPET.defaultBlockState(), 2);
				}
				boolean cell = u % PERIOD == 1 && v % PERIOD == 1;
				boolean heart = heartCell(u / PERIOD, v / PERIOD);
				if (!grid[u][v] && cell && !heart) {
					float d = random.nextFloat();
					if (d < 0.3f) {
						// soul lanterns hung from the roof, so the way can be seen
						level.setBlock(pos.set(x, y + WALL - 1, z), Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);
					} else if (d < 0.38f) {
						level.setBlock(pos.set(x, y + WALL - 1, z), Blocks.COBWEB.defaultBlockState(), 2);
					} else if (d < 0.43f) {
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
		HellcraftMod.LOGGER.info("Labyrinth built beside the Minotaur's lair {} at {}", k, centre.toShortString());
	}

	/** Puts back any wall, roof or floor block that something (an explosion, a piston) took out, if no one stands in it. */
	private static void mend(ServerLevel level) {
		for (int k = 0; k < Guardian.MINOTAUR.lairs(); k++) {
			int y = floorY(level, k);
			if (y == Integer.MIN_VALUE) {
				continue;
			}
			BlockPos centre = centre(level, k);
			boolean near = false;
			for (ServerPlayer p : level.players()) {
				if (p.distanceToSqr(centre.getX(), centre.getY(), centre.getZ()) < 96 * 96) {
					near = true;
					break;
				}
			}
			if (!near) {
				continue;
			}
			RandomSource random = RandomSource.create();
			boolean[][] grid = grid(k);
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			int mended = 0;
			for (int u = 0; u < SIZE; u++) {
				for (int v = 0; v < SIZE; v++) {
					int[] xz = world(centre, k, u, v);
					if (!level.isLoaded(pos.set(xz[0], y, xz[1]))) {
						continue;
					}
					for (int yy = y - 1; yy <= y + WALL; yy++) {
						boolean part = yy == y - 1 || yy == y + WALL || grid[u][v];
						if (!part) {
							continue;
						}
						pos.set(xz[0], yy, xz[1]);
						BlockState there = level.getBlockState(pos);
						if (!there.canBeReplaced() || !level.getEntitiesOfClass(Player.class, new AABB(pos)).isEmpty()) {
							continue;
						}
						level.setBlock(pos, yy == y - 1 ? floor(random) : yy == y + WALL ? roof(random) : stone(random), 3);
						mended++;
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

	/** Shows each soul carrying the thread inside a maze the way out to the Minotaur's door: a red thread along the floor. */
	private static void trails(ServerLevel level) {
		for (ServerPlayer p : level.players()) {
			if (!p.getInventory().contains(new ItemStack(HellItems.ARIADNES_THREAD))) {
				continue;
			}
			for (int k = 0; k < Guardian.MINOTAUR.lairs(); k++) {
				int y = floorY(level, k);
				if (y == Integer.MIN_VALUE || p.getY() < y - 1 || p.getY() > y + WALL) {
					continue;
				}
				BlockPos centre = centre(level, k);
				int[] at = local(centre, k, p.getX(), p.getZ());
				if (!inside(at)) {
					continue;
				}
				List<int[]> path = wayOut(grid(k), at);
				for (int i = 0; i < Math.min(TRAIL, path.size()); i++) {
					int[] xz = world(centre, k, path.get(i)[0], path.get(i)[1]);
					level.sendParticles(p, THREAD, true, true, xz[0] + 0.5, y + 0.12, xz[1] + 0.5, 2, 0.12, 0.0, 0.12, 0);
				}
				break;
			}
		}
	}

	/** The shortest way over open blocks from (u, v) to the door onto the arena (v = 0), from the start; empty if none. */
	static List<int[]> wayOut(boolean[][] grid, int[] from) {
		List<int[]> path = new ArrayList<>();
		if (grid[from[0]][from[1]]) {
			return path;
		}
		int[][] prev = new int[SIZE * SIZE][];
		boolean[][] seen = new boolean[SIZE][SIZE];
		Deque<int[]> queue = new ArrayDeque<>();
		queue.add(from);
		seen[from[0]][from[1]] = true;
		int[] end = null;
		while (!queue.isEmpty()) {
			int[] a = queue.poll();
			if (a[1] == 0) {
				end = a;
				break;
			}
			for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				int nu = a[0] + d[0];
				int nv = a[1] + d[1];
				if (nu >= 0 && nv >= 0 && nu < SIZE && nv < SIZE && !seen[nu][nv] && !grid[nu][nv]) {
					seen[nu][nv] = true;
					prev[nu * SIZE + nv] = a;
					queue.add(new int[]{nu, nv});
				}
			}
		}
		for (int[] a = end; a != null; a = prev[a[0] * SIZE + a[1]]) {
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
	 * For the smoke test: in every maze, every block of corridor can be reached from the far door, the heart and the door
	 * onto the arena among them, and the thread leads from the heart to that door.
	 */
	public static String selfTest() {
		List<String> wrong = new ArrayList<>();
		for (int k = 0; k < Guardian.MINOTAUR.lairs(); k++) {
			boolean[][] grid = grid(k);
			boolean[][] seen = new boolean[SIZE][SIZE];
			Deque<int[]> queue = new ArrayDeque<>();
			int door = MID_CELL * PERIOD + 1;
			queue.add(new int[]{door, SIZE - 1});
			seen[door][SIZE - 1] = true;
			int reached = 0;
			int open = 0;
			while (!queue.isEmpty()) {
				int[] at = queue.poll();
				reached++;
				for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					int nu = at[0] + d[0];
					int nv = at[1] + d[1];
					if (nu >= 0 && nv >= 0 && nu < SIZE && nv < SIZE && !seen[nu][nv] && !grid[nu][nv]) {
						seen[nu][nv] = true;
						queue.add(new int[]{nu, nv});
					}
				}
			}
			for (int u = 0; u < SIZE; u++) {
				for (int v = 0; v < SIZE; v++) {
					open += grid[u][v] ? 0 : 1;
				}
			}
			if (!seen[door][0]) {
				wrong.add("maze " + k + " has no way through");
			}
			if (!seen[MID][MID]) {
				wrong.add("maze " + k + " has its heart walled off");
			}
			if (reached < open) {
				wrong.add("maze " + k + " has corridors walled off");
			}
			List<int[]> out = wayOut(grid, new int[]{MID, MID});
			if (out.isEmpty() || out.get(out.size() - 1)[1] != 0) {
				wrong.add("maze " + k + "'s thread leads nowhere");
			}
		}
		return wrong.isEmpty() ? "Labyrinth test OK: " + Guardian.MINOTAUR.lairs() + " mazes, each with a way to its heart and out to the arena"
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
							BlockPos c = centre(level, k);
							ctx.getSource().sendSuccess(() -> Component.literal("Built the Labyrinth beside the Minotaur's lair " + k + " at "
									+ c.toShortString() + "; its spool is at its heart, " + spool(level, k).toShortString() + "."), false);
							return 1;
						}))));
	}
}
