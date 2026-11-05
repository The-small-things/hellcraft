package net.thesmallthings.hellcraft.world;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
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
 * The Labyrinth of Crete, beside each of the Minotaur's lairs: a ruined maze of mossy, cracked stone, its walls topped
 * with the Wood's thorns (climb over and they tear you; sneak and they don't), its far end opening straight into his
 * arena. At that end, by the door, lies Ariadne's spool: right-click it for Ariadne's Thread (once each half hour).
 * Carried into the Minotaur's fight, the thread makes every charge of his end with him stunned, as if he'd hit the wall,
 * and your blows hit him a fifth harder (MinotaurFight, BossRules); it's used up when he falls. Walking the maze is never
 * needed: the arena is open on every other side.
 *
 * Each maze is built once, the first time a player comes near that lair (HellState.labyrinths), from a seed of its own:
 * 11 by 11 cells, carved by a depth-first walk, so there's one way through and many dead ends.
 */
public final class Labyrinth {
	private Labyrinth() {
	}

	/** Cells across (odd walls between them make the maze 2n+1 blocks across), wall height, and how far out from the arena. */
	static final int CELLS = 11;
	static final int SIZE = CELLS * 2 + 1;
	static final int WALL = 4;
	/** The maze's centre lies this far from the lair's centre (the arena is 24 out; the maze starts 2 blocks past it). */
	static final int OFFSET = 26 + SIZE / 2;
	/** How near a player comes before a maze is built. */
	private static final double NEAR = 112;
	/** How often a player may take a thread from the spool (ticks). */
	private static final long THREAD_GAP = 20L * 60 * 30;
	private static final Map<UUID, Long> TAKEN = new HashMap<>();
	/** Hint bit for the first time a soul comes to a maze (CircleHazards.hintOnce). */
	private static final int HINT = 62;

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
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> commands(dispatcher));
	}

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 40 != 11) {
			return;
		}
		ServerLevel level = server.overworld();
		if (!HellWorldgen.isInferno(level)) {
			return;
		}
		HellState state = HellState.get(server);
		for (int k = 0; k < Guardian.MINOTAUR.lairs(); k++) {
			if (state.labyrinths.contains(id(k))) {
				// the first time a soul comes to a maze's far door, Virgil says what lies inside
				BlockPos c = centre(level, k);
				for (ServerPlayer p : level.players()) {
					if (!p.isSpectator() && p.distanceToSqr(c.getX(), c.getY(), c.getZ()) < 30 * 30) {
						net.thesmallthings.hellcraft.hazard.CircleHazards.hintOnce(p, HINT, "The Labyrinth of Crete. Ariadne's Thread lies at its far end, "
								+ "by the door into the Minotaur's lair: take it into his fight, and his charges will end with him stunned.");
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
		return "minotaur_" + k;
	}

	/** Which way the maze lies from lair k: the side facing out of Hell (away from the centre), on a compass axis. */
	public static Direction side(int k) {
		BlockPos c = Guardian.MINOTAUR.lairColumn(k);
		if (Math.abs(c.getX()) >= Math.abs(c.getZ())) {
			return c.getX() >= 0 ? Direction.EAST : Direction.WEST;
		}
		return c.getZ() >= 0 ? Direction.SOUTH : Direction.NORTH;
	}

	/** The maze's centre at lair k (y: the arena floor). */
	public static BlockPos centre(ServerLevel level, int k) {
		BlockPos floor = GuardianManager.lair(level, Guardian.MINOTAUR, k);
		Direction d = side(k);
		return floor.offset(d.getStepX() * OFFSET, 0, d.getStepZ() * OFFSET);
	}

	/** Where the spool stands at lair k: set into the wall beside the door into the arena, at the floor. */
	public static BlockPos spool(ServerLevel level, int k) {
		Direction toward = side(k).getOpposite();
		Direction across = toward.getClockWise();
		return centre(level, k).offset(across.getStepX() + toward.getStepX() * (SIZE / 2), 0, across.getStepZ() + toward.getStepZ() * (SIZE / 2));
	}

	/** Which lair's spool this block is, or -1. */
	static int spoolAt(ServerLevel level, BlockPos pos) {
		HellState state = HellState.get(level.getServer());
		for (int k = 0; k < Guardian.MINOTAUR.lairs(); k++) {
			if (state.labyrinths.contains(id(k)) && spool(level, k).equals(pos)) {
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
		Dialogue.notice(player, Component.literal("You took Ariadne's Thread. Carry it into the Minotaur's fight, through the door ahead: "
				+ "each of his charges ends with him stunned, and you hit him harder. It's used up when he falls.").withStyle(ChatFormatting.RED));
	}

	/** Carves the maze for lair k: a depth-first walk over the cells from a seed of its own. Returns, per cell, its open sides. */
	static boolean[][][] carve(int k) {
		// open[cx][cz][0..3]: east, south, west, north
		boolean[][][] open = new boolean[CELLS][CELLS][4];
		boolean[][] seen = new boolean[CELLS][CELLS];
		RandomSource random = RandomSource.create(0x1ab7_0000L + k * 7919L);
		Deque<int[]> stack = new ArrayDeque<>();
		stack.push(new int[]{CELLS / 2, CELLS - 1});
		seen[CELLS / 2][CELLS - 1] = true;
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
		return open;
	}

	/**
	 * Builds lair k's maze. Local coordinates (u, v) run 0..SIZE-1; v = 0 is the side by the arena (its door and the spool),
	 * v = SIZE-1 the far side (the way in).
	 */
	public static void build(ServerLevel level, int k) {
		HellState state = HellState.get(level.getServer());
		state.labyrinths.add(id(k));
		state.setDirty();
		BlockPos centre = centre(level, k);
		Direction toward = side(k).getOpposite();
		Direction across = toward.getClockWise();
		int y = centre.getY();
		RandomSource random = RandomSource.create(0x2c4f_0000L + k * 104729L);
		boolean[][][] open = carve(k);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int u = 0; u < SIZE; u++) {
			for (int v = 0; v < SIZE; v++) {
				int ox = (u - SIZE / 2) * across.getStepX() + (SIZE / 2 - v) * toward.getStepX();
				int oz = (u - SIZE / 2) * across.getStepZ() + (SIZE / 2 - v) * toward.getStepZ();
				int x = centre.getX() + ox;
				int z = centre.getZ() + oz;
				level.getChunk(x >> 4, z >> 4);
				// a floor at the arena's height, on a footing down to the ground, and the air above cleared
				int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				for (int yy = Math.min(ground, y - 1); yy < y; yy++) {
					level.setBlock(pos.set(x, yy, z), yy == y - 1 ? floor(random) : Blocks.BLACKSTONE.defaultBlockState(), 2);
				}
				for (int yy = y; yy < Math.max(ground, y) + WALL + 4; yy++) {
					level.setBlock(pos.set(x, yy, z), Blocks.AIR.defaultBlockState(), 2);
				}
				if (wall(open, u, v)) {
					for (int h = 0; h < WALL; h++) {
						level.setBlock(pos.set(x, y + h, z), stone(random, h), 2);
					}
					level.setBlock(pos.set(x, y + WALL, z), HellBlocks.THORNS.defaultBlockState(), 2);
				} else if ((u % 2 == 1 && v % 2 == 1) && random.nextFloat() < 0.08f) {
					// cobwebs and bones in the corners of the dead ends
					level.setBlock(pos.set(x, y, z), random.nextBoolean() ? Blocks.COBWEB.defaultBlockState() : Blocks.BONE_BLOCK.defaultBlockState(), 2);
				}
				if (wall(open, u, v) && u % 4 == 0 && v % 6 == 3 && random.nextFloat() < 0.6f) {
					// a soul lantern here and there, hung on the wall's top, so the way can be found
					level.setBlock(pos.set(x, y + WALL, z), Blocks.SOUL_LANTERN.defaultBlockState(), 2);
				}
			}
		}
		// the spool, by the door into the arena
		BlockPos spool = spool(level, k);
		level.setBlock(spool, Blocks.LODESTONE.defaultBlockState(), 3);
		HellcraftMod.LOGGER.info("Labyrinth built beside the Minotaur's lair {} at {}", k, centre.toShortString());
	}

	/** Whether (u, v) in the maze is wall: the grid between cells, opened where the walk passed, and the two doors. */
	static boolean wall(boolean[][][] open, int u, int v) {
		boolean door = u == SIZE / 2 && (v == 0 || v == SIZE - 1);
		if (door) {
			return false;
		}
		if (u == 0 || v == 0 || u == SIZE - 1 || v == SIZE - 1) {
			return true;
		}
		boolean cellU = u % 2 == 1;
		boolean cellV = v % 2 == 1;
		if (cellU && cellV) {
			return false;
		}
		if (!cellU && !cellV) {
			return true;
		}
		// a passage between two cells: open if the walk went through
		int cx = (u - 1) / 2;
		int cz = (v - 1) / 2;
		if (!cellU) {
			// between (cx, cz) and (cx + 1, cz): east of the cell to the west
			return !open[(u - 2) / 2][cz][0];
		}
		return !open[cx][(v - 2) / 2][1];
	}

	private static BlockState stone(RandomSource random, int h) {
		float r = random.nextFloat();
		if (h == WALL - 1 && r < 0.15f) {
			return Blocks.MOSSY_STONE_BRICK_WALL.defaultBlockState();
		}
		return r < 0.45f ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : r < 0.8f ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
				: Blocks.STONE_BRICKS.defaultBlockState();
	}

	private static BlockState floor(RandomSource random) {
		float r = random.nextFloat();
		return r < 0.5f ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : r < 0.8f ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
				: Blocks.BLACKSTONE.defaultBlockState();
	}

	/** For the smoke test: every maze has one way through from the far door to the door by the arena, and walls round it. */
	public static String selfTest() {
		List<String> wrong = new ArrayList<>();
		for (int k = 0; k < Guardian.MINOTAUR.lairs(); k++) {
			boolean[][][] open = carve(k);
			// walk from the far door to the near door over open blocks
			boolean[][] seen = new boolean[SIZE][SIZE];
			Deque<int[]> queue = new ArrayDeque<>();
			queue.add(new int[]{SIZE / 2, SIZE - 1});
			seen[SIZE / 2][SIZE - 1] = true;
			int reached = 0;
			while (!queue.isEmpty()) {
				int[] at = queue.poll();
				reached++;
				for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					int nu = at[0] + d[0];
					int nv = at[1] + d[1];
					if (nu >= 0 && nv >= 0 && nu < SIZE && nv < SIZE && !seen[nu][nv] && !wall(open, nu, nv)) {
						seen[nu][nv] = true;
						queue.add(new int[]{nu, nv});
					}
				}
			}
			if (!seen[SIZE / 2][0]) {
				wrong.add("maze " + k + " has no way through");
			}
			if (reached < CELLS * CELLS) {
				wrong.add("maze " + k + " has cells walled off");
			}
		}
		return wrong.isEmpty() ? "Labyrinth test OK: " + Guardian.MINOTAUR.lairs() + " mazes, each with one way through to the arena"
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
									+ c.toShortString() + "; its spool is at " + spool(level, k).toShortString() + "."), false);
							return 1;
						}))));
	}
}
