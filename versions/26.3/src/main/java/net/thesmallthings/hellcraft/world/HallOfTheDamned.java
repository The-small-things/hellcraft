package net.thesmallthings.hellcraft.world;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.util.Signs;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.ToIntFunction;

/**
 * The Hall of the Damned: five blackstone pillars beside the spawn, each crowned with the head of the
 * soul who leads it and signed with the top three. It names the Inferno's greatest and its most damned.
 */
public final class HallOfTheDamned {
	private HallOfTheDamned() {
	}

	/** One pillar of the Hall. */
	public record Board(String title, ChatFormatting colour, ToIntFunction<HellState.Soul> score) {
	}

	public static final List<Board> BOARDS = List.of(
			new Board("MOST HEARTS", ChatFormatting.DARK_RED, s -> s.ghost ? 0 : s.hearts),
			new Board("P'S BURNED", ChatFormatting.GOLD, s -> s.prestige),
			new Board("LUCIFER SLAIN", ChatFormatting.RED, s -> s.luciferKills),
			new Board("GUARDIANS SLAIN", ChatFormatting.DARK_PURPLE, s -> s.guardiansSlain),
			new Board("MOST DAMNED", ChatFormatting.DARK_GRAY, s -> s.deaths));

	private static final int SPACING = 3;
	private static String shown = "";

	/** The top three souls of a board (only those who scored). */
	public static List<HellState.Soul> top(MinecraftServer server, Board board) {
		List<HellState.Soul> souls = new ArrayList<>();
		for (HellState.Soul soul : HellState.get(server).souls().values()) {
			if (!soul.name.isEmpty() && board.score().applyAsInt(soul) > 0) {
				souls.add(soul);
			}
		}
		souls.sort(Comparator.comparingInt((HellState.Soul s) -> board.score().applyAsInt(s)).reversed()
				.thenComparing(s -> s.name.toLowerCase(Locale.ROOT)));
		return souls.subList(0, Math.min(3, souls.size()));
	}

	/** The whole Hall as chat lines, for /hellcraft hall. */
	public static List<Component> describe(MinecraftServer server) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal("☠ The Hall of the Damned ☠").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
		for (Board board : BOARDS) {
			StringBuilder sb = new StringBuilder();
			List<HellState.Soul> top = top(server, board);
			for (int i = 0; i < top.size(); i++) {
				sb.append(i == 0 ? "" : ",  ").append(i + 1).append(". ").append(top.get(i).name).append(" (")
						.append(board.score().applyAsInt(top.get(i))).append(")");
			}
			lines.add(Component.literal(board.title() + ": ").withStyle(board.colour())
					.append(Component.literal(top.isEmpty() ? "nobody yet" : sb.toString()).withStyle(ChatFormatting.GRAY)));
		}
		return lines;
	}

	/** At server start: raise the Hall beside the spawn of a fresh (or not yet furnished) Inferno world. */
	public static void buildOnce(MinecraftServer server) {
		HellState state = HellState.get(server);
		if (!HellConfig.get().hallMonument || state.hall != null || !HellWorldgen.isInferno(server.overworld())) {
			return;
		}
		build(server);
	}

	/** Builds (or rebuilds) the Hall; returns where its first pillar stands. */
	public static BlockPos build(MinecraftServer server) {
		ServerLevel level = server.overworld();
		HellState state = HellState.get(server);
		// north of the spawn (which is 24 blocks outside the Gate), facing it
		int x0 = InfernoGeometry.gateX() + 24 - SPACING * 2;
		int z = -9;
		int y = Integer.MIN_VALUE;
		for (int i = 0; i < BOARDS.size(); i++) {
			level.getChunk((x0 + i * SPACING) >> 4, z >> 4);
			y = Math.max(y, ground(level, x0 + i * SPACING, z));
		}
		int x1 = x0 + SPACING * (BOARDS.size() - 1);
		// a floor of blackstone, cleared above; the pillars stand on its back row, their signs on the front
		for (int x = x0 - 3; x <= x1 + 3; x++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int dy = -3; dy < 0; dy++) {
					level.setBlock(new BlockPos(x, y + dy, z + dz), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 3);
				}
				level.setBlock(new BlockPos(x, y - 1, z + dz), (x + dz) % 2 == 0 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
						: Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), 3);
				for (int dy = 0; dy <= 5; dy++) {
					level.setBlock(new BlockPos(x, y + dy, z + dz), Blocks.AIR.defaultBlockState(), 3);
				}
			}
		}
		for (int i = 0; i < BOARDS.size(); i++) {
			int x = x0 + i * SPACING;
			for (int dy = 0; dy < 3; dy++) {
				level.setBlock(new BlockPos(x, y + dy, z - 1), Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState(), 3);
			}
			level.setBlock(new BlockPos(x, y + 2, z - 1), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 3);
		}
		// soul fire at both ends
		for (int x : new int[] {x0 - 2, x1 + 2}) {
			level.setBlock(new BlockPos(x, y, z - 1), Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true), 3);
		}
		state.hall = new HellState.GlobalSpot(level.dimension(), new BlockPos(x0, y, z));
		state.setDirty();
		shown = "";
		refresh(server);
		HellcraftMod.LOGGER.info("The Hall of the Damned at {} {} {}", x0, y, z);
		return state.hall.pos();
	}

	private static int ground(ServerLevel level, int x, int z) {
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
	}

	/** Once a minute: rewrites the signs and heads when the standings have changed. */
	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 1200 != 0 || !HellConfig.get().hallMonument) {
			return;
		}
		refresh(server);
	}

	private static void refresh(MinecraftServer server) {
		HellState state = HellState.get(server);
		if (state.hall == null) {
			return;
		}
		ServerLevel level = server.overworld();
		BlockPos base = state.hall.pos();
		if (!level.isLoaded(base) || !level.isLoaded(base.offset(SPACING * (BOARDS.size() - 1), 0, 0))) {
			return;
		}
		StringBuilder signature = new StringBuilder();
		List<List<HellState.Soul>> tops = new ArrayList<>();
		for (Board board : BOARDS) {
			List<HellState.Soul> top = top(server, board);
			tops.add(top);
			for (HellState.Soul soul : top) {
				signature.append(soul.name).append('=').append(board.score().applyAsInt(soul)).append(';');
			}
			signature.append('|');
		}
		if (signature.toString().equals(shown)) {
			return;
		}
		shown = signature.toString();
		for (int i = 0; i < BOARDS.size(); i++) {
			Board board = BOARDS.get(i);
			List<HellState.Soul> top = tops.get(i);
			BlockPos pillar = base.offset(i * SPACING, 0, -1);
			List<Component> lines = new ArrayList<>();
			lines.add(Component.literal(board.title()).withStyle(board.colour(), ChatFormatting.BOLD));
			for (int rank = 0; rank < 3; rank++) {
				if (rank < top.size()) {
					HellState.Soul soul = top.get(rank);
					String name = soul.name.length() > 10 ? soul.name.substring(0, 10) : soul.name;
					lines.add(Component.literal((rank + 1) + " " + name + " " + board.score().applyAsInt(soul)));
				} else {
					lines.add(Component.literal(rank == 0 ? "- nobody yet -" : ""));
				}
			}
			Signs.place(level, pillar.offset(0, 0, 1), 0, lines);
			// the leader's head crowns the pillar (a skull while nobody leads)
			String head = top.isEmpty() || !safeName(top.getFirst().name) ? "minecraft:skeleton_skull[rotation=0]"
					: "minecraft:player_head[rotation=0]{profile:\"" + top.getFirst().name + "\"}";
			BlockPos crown = pillar.above(3);
			level.setBlock(crown, Blocks.AIR.defaultBlockState(), 3);
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(level).withSuppressedOutput(),
					String.format(Locale.ROOT, "setblock %d %d %d %s", crown.getX(), crown.getY(), crown.getZ(), head));
		}
	}

	/** Names are only letters, digits and underscores; anything else never reaches a command. */
	static boolean safeName(String name) {
		return name.matches("[A-Za-z0-9_]{1,16}");
	}
}
