package net.thesmallthings.hellcraft.world;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.Hearts;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.net.Dialogue;
import net.thesmallthings.hellcraft.util.Feedback;
import net.thesmallthings.hellcraft.util.Journey;
import net.thesmallthings.hellcraft.util.Signs;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The climb out. When Lucifer falls, a "natural burrow" opens at the bottom of his pit for a while;
 * whoever steps into it comes out on the shore of Purgatory, a mountain island in a starlit sea, in its own
 * dimension ({@link #LEVEL}, Phase 7: gen_realms.py), in the hour before dawn ("Thence we came forth to rebehold
 * the stars").
 *
 * <p>Seven terraces step up the mountain like a staircase, one for each capital sin, each wide enough to walk round.
 * The Great Stair climbs straight up the southern face from the shore, a flight to each terrace, to the Earthly Paradise
 * on the summit; narrower steps round the mountain are the side ways up. On the summit two streams run: <b>Lethe</b>, which
 * washes away every affliction, and <b>Eunoë</b>, which restores two hearts once for every victory
 * over Lucifer. The Gate of Return in the middle of the garden leads back to the Gate of Hell.
 */
public final class Purgatory {
	private Purgatory() {
	}

	/** Purgatory's dimension: the sea and the island mountain. */
	public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, HellcraftMod.id("purgatory"));
	/** The sea's surface (its top water block) and its sandy floor (the flat generator's layers, gen_realms.py). */
	static final int SEA_Y = 62;
	public static final int SEA_FLOOR = 34;
	/** The beach, the lowest ledge of the island (its top block). */
	static final int SHORE_Y = 66;
	/** Past the shore ring the beach runs down into the sea. */
	public static final int BEACH_RADIUS = 117;
	private static final int SHORE_RADIUS = 103;
	public static final int FIRST_TERRACE = 89;
	/** Each terrace is this deep: wide enough to walk round, and to hold a flight of the Great Stair and its landing. */
	public static final double TERRACE_WIDTH = 11;
	/** The Great Stair runs straight up the southern face, this many blocks either side of its middle. */
	public static final int STAIR_HALF = 2;
	public static final int TERRACE_RISE = 8;
	public static final int TERRACES = 7;
	/** The summit's top block (the Earthly Paradise). */
	public static final int SUMMIT_Y = SHORE_Y + TERRACE_RISE * (TERRACES + 1);
	private static final int BURROW_TICKS = 5 * 60 * 20;

	private static final String[] SINS = {"PRIDE", "ENVY", "WRATH", "SLOTH", "AVARICE", "GLUTTONY", "LUST"};
	private static final String[] SIN_LINES = {
			"Bowed beneath stones",
			"Eyes sewn with wire",
			"Walking in smoke",
			"Running, never resting",
			"Face down in the dust",
			"Starving by the tree",
			"Walking through fire",
	};

	/** Where you arrive: the southern shore, at the foot of the Great Stair. */
	public static BlockPos shore() {
		return new BlockPos(0, SHORE_Y + 1, FIRST_TERRACE + TERRACE_RISE + 4);
	}

	@Nullable
	private static BlockPos burrow;
	private static long burrowUntil;
	private static final Map<UUID, Integer> TERRACE_REACHED = new HashMap<>();
	private static final Map<UUID, Long> STREAM_READY = new HashMap<>();

	// ---------------------------------------------------------------------------------- the mountain

	/** Terrace index at a distance from the axis: 0 the shore, 1..7 the terraces, 8 the summit, -1 outside. */
	public static int ring(double r) {
		if (r > SHORE_RADIUS) {
			return -1;
		}
		if (r >= FIRST_TERRACE) {
			return 0;
		}
		int k = (int) Math.floor((FIRST_TERRACE - r) / TERRACE_WIDTH) + 1;
		return Math.min(k, TERRACES + 1);
	}

	public static int topY(double r) {
		int k = ring(r);
		return k < 0 ? Integer.MIN_VALUE : SHORE_Y + TERRACE_RISE * k;
	}

	/** The beach's sand, from the shore ring down under the sea (or MIN_VALUE where there is none). */
	public static int beachY(double r) {
		return r <= SHORE_RADIUS || r > BEACH_RADIUS ? Integer.MIN_VALUE : (int) Math.round(SHORE_Y - (r - SHORE_RADIUS) * 1.35);
	}

	/** Purgatory's dimension, or null when the world has none (not an Inferno world). */
	@Nullable
	public static ServerLevel level(MinecraftServer server) {
		return server.getLevel(LEVEL);
	}

	public static void buildOnce(MinecraftServer server) {
		HellState state = HellState.get(server);
		if (!HellWorldgen.isInferno(server.overworld())) {
			return;
		}
		closeLeftoverBurrow(server.overworld());
		ServerLevel level = level(server);
		if (level == null || state.purgatoryIsland) {
			return;
		}
		HellcraftMod.LOGGER.info("Raising the Mountain of Purgatory...");
		// (the beach and the mountain themselves are generated with the dimension's chunks, lit as they are made:
		// world/feature/PurgatoryIslandFeature; only the stairs, the signs and the garden are added here)
		RandomSource random = RandomSource.create(1300L);
		// the side ways up: narrow steps cut into the cliffs round the mountain, for those who explore the terraces
		// (none on the southern face, where the Great Stair runs, and none into the garden: the Great Stair reaches it)
		for (int k = 0; k < TERRACES; k++) {
			stairway(level, k);
		}
		// the straight way up: the Great Stair, one flight to each terrace and the last to the garden
		for (int k = 0; k <= TERRACES; k++) {
			greatStair(level, k);
		}
		paradise(level, random);
		Signs.place(level, new BlockPos(STAIR_HALF + 2, SHORE_Y + 1, FIRST_TERRACE + TERRACE_RISE + 2), 0, List.of(
				Component.literal("PURGATORY").withStyle(ChatFormatting.BOLD),
				Component.literal("The Great Stair"),
				Component.literal("climbs to the"),
				Component.literal("garden on top")));
		state.purgatoryIsland = true;
		state.setDirty();
		HellcraftMod.LOGGER.info("The Mountain of Purgatory rises from the sea, summit at y {}", SUMMIT_Y);
	}

	public static BlockState material(int k, int y, int top, RandomSource random) {
		if (y == top) {
			if (k == 0) {
				return Blocks.SAND.defaultBlockState();
			}
			if (k > TERRACES) {
				return Blocks.GRASS_BLOCK.defaultBlockState();
			}
			float f = random.nextFloat();
			return f < 0.15f ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : f < 0.3f ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
					: Blocks.STONE_BRICKS.defaultBlockState();
		}
		if (k == 0 && y >= top - 2) {
			return Blocks.SANDSTONE.defaultBlockState();
		}
		if (k > TERRACES && y >= top - 2) {
			return Blocks.DIRT.defaultBlockState();
		}
		return random.nextFloat() < 0.2f ? Blocks.ANDESITE.defaultBlockState() : Blocks.STONE.defaultBlockState();
	}

	/** A terrace's floor: the band at its edges (and the cliff's lip), the stones of its middle, and the accent laid across it. */
	private record Floor(BlockState border, BlockState[] main, BlockState accent) {
	}

	private static final Floor[] FLOORS = {
			// Pride: a pale carved pavement (in Dante it is carved with examples of humility)
			new Floor(Blocks.SMOOTH_STONE.defaultBlockState(), new BlockState[]{Blocks.STONE_BRICKS.defaultBlockState(), Blocks.POLISHED_ANDESITE.defaultBlockState(),
					Blocks.STONE_BRICKS.defaultBlockState(), Blocks.CRACKED_STONE_BRICKS.defaultBlockState()}, Blocks.CHISELED_STONE_BRICKS.defaultBlockState()),
			// Envy: livid grey stone
			new Floor(Blocks.POLISHED_TUFF.defaultBlockState(), new BlockState[]{Blocks.TUFF_BRICKS.defaultBlockState(), Blocks.TUFF.defaultBlockState(),
					Blocks.TUFF_BRICKS.defaultBlockState(), Blocks.CHISELED_TUFF.defaultBlockState()}, Blocks.CHISELED_TUFF_BRICKS.defaultBlockState()),
			// Wrath: stone darkened by the smoke
			new Floor(Blocks.POLISHED_DEEPSLATE.defaultBlockState(), new BlockState[]{Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.COBBLED_DEEPSLATE.defaultBlockState(),
					Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState()}, Blocks.CHISELED_DEEPSLATE.defaultBlockState()),
			// Sloth: neglected, gone to moss
			new Floor(Blocks.STONE_BRICKS.defaultBlockState(), new BlockState[]{Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_COBBLESTONE.defaultBlockState(),
					Blocks.STONE_BRICKS.defaultBlockState(), Blocks.MOSS_BLOCK.defaultBlockState()}, Blocks.CRACKED_STONE_BRICKS.defaultBlockState()),
			// Avarice: the dust they lie in, trodden earth between worn sandstone (not pale sand, which read as snow)
			new Floor(Blocks.PACKED_MUD.defaultBlockState(), new BlockState[]{Blocks.COARSE_DIRT.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(),
					Blocks.PACKED_MUD.defaultBlockState(), Blocks.CUT_SANDSTONE.defaultBlockState()}, Blocks.CHISELED_SANDSTONE.defaultBlockState()),
			// Gluttony: the garden of its trees, moss and grass between the bricks
			new Floor(Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), new BlockState[]{Blocks.MOSS_BLOCK.defaultBlockState(), Blocks.GRASS_BLOCK.defaultBlockState(),
					Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), Blocks.ROOTED_DIRT.defaultBlockState()}, Blocks.MUD_BRICKS.defaultBlockState()),
			// Lust: fire-red brick and granite
			new Floor(Blocks.POLISHED_GRANITE.defaultBlockState(), new BlockState[]{Blocks.BRICKS.defaultBlockState(), Blocks.GRANITE.defaultBlockState(),
					Blocks.BRICKS.defaultBlockState(), Blocks.RED_SANDSTONE.defaultBlockState()}, Blocks.CHISELED_RED_SANDSTONE.defaultBlockState()),
	};

	/**
	 * The stone at the top of terrace k's column (x, z): its floor at {@code top}, and the cliff's lip a block below where
	 * the terrace meets the drop (null where the rock's own stone stays). A band of the border stone runs along the foot of
	 * the cliff and the outer edge; the middle is laid in patches three blocks across, with an accent line across the
	 * terrace every few blocks round.
	 */
	@Nullable
	public static BlockState floor(int k, int x, int z, double r, int y, int top) {
		if (k < 1 || k > TERRACES) {
			return null;
		}
		Floor f = FLOORS[k - 1];
		double d = r - cliff(k);
		boolean edge = d >= TERRACE_WIDTH - 1.5;
		if (y == top - 1) {
			return edge ? f.border : null;
		}
		if (y != top) {
			return null;
		}
		if (edge || d < 1) {
			return f.border;
		}
		// an accent line across the terrace every nine blocks round
		long along = Math.round(Math.atan2(z, x) * cliff(k));
		if (Math.floorMod(along, 9) == 0 && d >= 2 && d < TERRACE_WIDTH - 2.5) {
			return f.accent;
		}
		// patches three blocks across, each of one of the middle's stones, and here and there a single other stone
		long cell = hash(Math.floorDiv(x, 3), Math.floorDiv(z, 3), k);
		int pick = (int) Math.floorMod(cell, (long) f.main.length);
		if (Math.floorMod(hash(x, z, k + 31), 7L) == 0) {
			pick = (pick + 1) % f.main.length;
		}
		return f.main[pick];
	}

	private static long hash(int x, int z, int salt) {
		long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ salt * 0x632BE59BD9B4E019L;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return h;
	}

	/** The radius of the cliff between terrace k and k + 1 (k = 0: between the shore and the first terrace). */
	public static double cliff(int k) {
		return FIRST_TERRACE - k * TERRACE_WIDTH;
	}

	/**
	 * True if (x, z) lies on or by a way up that terrace k's decorations must keep clear of: the Great Stair, or the
	 * side stairs from terrace k up and from the terrace below up to k.
	 */
	public static boolean onAWayUp(int x, int z, int k) {
		if (Math.abs(x) <= STAIR_HALF + 3 && z > 0) {
			return true;
		}
		double a = Math.atan2(z, x);
		double r = Math.sqrt((double) x * x + (double) z * z);
		for (int j : new int[]{k - 1, k}) {
			// (a side stair only cuts the ring round its own cliff: elsewhere the terrace is free)
			if (j < 0 || j >= TERRACES || Math.abs(r - (cliff(j) - 1.5)) > 3.5) {
				continue;
			}
			double from = stairAngle(j) - 0.05;
			double to = stairAngle(j) + (TERRACE_RISE + 3) / Math.max(8, r);
			double d = Math.IEEEremainder(a - from, 2 * Math.PI);
			if (d < 0) {
				d += 2 * Math.PI;
			}
			if (d <= to - from) {
				return true;
			}
		}
		return false;
	}

	private static double stairAngle(int k) {
		// the side stairways spiral round the mountain, starting just west of the Great Stair on the southern face
		return Math.PI / 2 + Math.PI / 8 + k * 2 * Math.PI / 8;
	}

	/**
	 * The flight of the Great Stair from level k (0 the shore) up to level k + 1: stone steps {@link #STAIR_HALF} * 2 + 1
	 * wide, built on terrace k against the cliff and climbing north to its top, with a lantern either side of the foot and,
	 * on the landing at the top, a sign naming the terrace (each runs on round the mountain to either side).
	 */
	private static void greatStair(ServerLevel level, int k) {
		int base = SHORE_Y + TERRACE_RISE * k;
		int cliff = FIRST_TERRACE - (int) Math.round(k * TERRACE_WIDTH);
		BlockState step = Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, net.minecraft.core.Direction.NORTH);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int s = 1; s <= TERRACE_RISE; s++) {
			// step s stands s blocks up, the top one against the cliff
			int z = cliff + TERRACE_RISE + 1 - s;
			for (int x = -STAIR_HALF; x <= STAIR_HALF; x++) {
				for (int y = base + 1; y < base + s; y++) {
					level.setBlock(pos.set(x, y, z), Blocks.STONE_BRICKS.defaultBlockState(), 2);
				}
				level.setBlock(pos.set(x, base + s, z), step, 2);
				for (int y = base + s + 1; y <= base + s + 3; y++) {
					level.setBlock(pos.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
		// a solid landing from the top step onto the terrace above (where the cliff curves away, its edge would leave a gap)
		// and the way onto it clear
		for (int x = -STAIR_HALF - 1; x <= STAIR_HALF + 1; x++) {
			for (int z = cliff - 2; z <= cliff; z++) {
				for (int y = base + 1; y <= base + TERRACE_RISE; y++) {
					if (!level.getBlockState(pos.set(x, y, z)).isSolid()) {
						level.setBlock(pos, y == base + TERRACE_RISE ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.STONE.defaultBlockState(), 2);
					}
				}
				for (int y = base + TERRACE_RISE + 1; y <= base + TERRACE_RISE + 3; y++) {
					if (Math.abs(x) <= STAIR_HALF) {
						level.setBlock(pos.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
					}
				}
			}
		}
		// lanterns on posts either side of the foot of the flight
		for (int x : new int[]{-STAIR_HALF - 1, STAIR_HALF + 1}) {
			level.setBlock(pos.set(x, base + 1, cliff + TERRACE_RISE + 1), Blocks.STONE_BRICK_WALL.defaultBlockState(), 2);
			level.setBlock(pos.set(x, base + 2, cliff + TERRACE_RISE + 1), Blocks.LANTERN.defaultBlockState(), 2);
		}
		if (k < TERRACES) {
			Signs.place(level, new BlockPos(STAIR_HALF + 2, base + TERRACE_RISE + 1, cliff - 1), 0, List.of(
					Component.literal("TERRACE " + (k + 1)).withStyle(ChatFormatting.BOLD),
					Component.literal(SINS[k]),
					Component.literal(SIN_LINES[k]),
					Component.literal("Stair goes on up")));
		}
	}

	/** Steps cut into the cliff from level k up to level k + 1, running around the mountain. */
	private static void stairway(ServerLevel level, int k) {
		double r = FIRST_TERRACE - k * TERRACE_WIDTH - 1.5;
		double a0 = stairAngle(k);
		int base = SHORE_Y + TERRACE_RISE * k;
		for (int s = 1; s <= TERRACE_RISE + 1; s++) {
			double a = a0 + s / r;
			for (double dr : new double[]{-0.5, 0.5}) {
				int x = (int) Math.round(Math.cos(a) * (r + dr));
				int z = (int) Math.round(Math.sin(a) * (r + dr));
				int step = Math.min(base + s, base + TERRACE_RISE);
				level.setBlock(new BlockPos(x, step, z), Blocks.STONE_BRICKS.defaultBlockState(), 2);
				for (int y = step + 1; y <= step + 3; y++) {
					level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
	}

	/** The Earthly Paradise: grass, flowers and trees, the two streams, and the Gate of Return. */
	private static void paradise(ServerLevel level, RandomSource random) {
		int y = SUMMIT_Y;
		BlockState[] flowers = {Blocks.POPPY.defaultBlockState(), Blocks.DANDELION.defaultBlockState(), Blocks.CORNFLOWER.defaultBlockState(),
				Blocks.LILY_OF_THE_VALLEY.defaultBlockState(), Blocks.AZURE_BLUET.defaultBlockState()};
		for (int x = -11; x <= 11; x++) {
			for (int z = -11; z <= 11; z++) {
				if (x * x + z * z < 11 * 11 && random.nextFloat() < 0.25f) {
					level.setBlock(new BlockPos(x, y + 1, z), flowers[random.nextInt(flowers.length)], 2);
				}
			}
		}
		for (int[] t : new int[][]{{-7, 6}, {6, 7}, {8, -2}, {-8, -1}}) {
			tree(level, t[0], y + 1, t[1]);
		}
		// Lethe to the west, Eunoë to the east, both from the northern spring
		for (int x = -10; x <= 10; x++) {
			if (Math.abs(x) < 3) {
				continue;
			}
			for (int z = -7; z <= -6; z++) {
				level.setBlock(new BlockPos(x, y - 1, z), Blocks.STONE.defaultBlockState(), 2);
				level.setBlock(new BlockPos(x, y, z), Blocks.WATER.defaultBlockState(), 2);
				level.setBlock(new BlockPos(x, y + 1, z), Blocks.AIR.defaultBlockState(), 2);
			}
		}
		Signs.place(level, new BlockPos(-4, y + 1, -4), 8, List.of(
				Component.literal("LETHE").withStyle(ChatFormatting.BOLD),
				Component.literal("Step in to remove"),
				Component.literal("all your effects"),
				Component.literal("")));
		Signs.place(level, new BlockPos(4, y + 1, -4), 8, List.of(
				Component.literal("EUNOË").withStyle(ChatFormatting.BOLD),
				Component.literal("Step in: +2 hearts"),
				Component.literal("once for every"),
				Component.literal("victory over Lucifer")));
		// the Gate of Return
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				level.setBlock(new BlockPos(x, y, z), Blocks.CRYING_OBSIDIAN.defaultBlockState(), 2);
				level.setBlock(new BlockPos(x, y + 1, z), Blocks.AIR.defaultBlockState(), 2);
			}
		}
		level.setBlock(new BlockPos(0, y + 1, 0), Blocks.END_GATEWAY.defaultBlockState(), 3);
		Signs.place(level, new BlockPos(0, y + 1, 3), 0, List.of(
				Component.literal("GATE OF RETURN").withStyle(ChatFormatting.BOLD),
				Component.literal("Step in to go"),
				Component.literal("back to the"),
				Component.literal("Gate of Hell")));
	}

	private static void tree(ServerLevel level, int x, int y, int z) {
		for (int dy = 0; dy < 4; dy++) {
			level.setBlock(new BlockPos(x, y + dy, z), Blocks.OAK_LOG.defaultBlockState(), 2);
		}
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int dy = 3; dy <= 5; dy++) {
					if (dx * dx + dz * dz + (dy - 4) * (dy - 4) <= 5 && !(dx == 0 && dz == 0 && dy < 4)) {
						level.setBlock(new BlockPos(x + dx, y + dy, z + dz), Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState()
								.setValue(LeavesBlock.PERSISTENT, true), 2);
					}
				}
			}
		}
	}

	// ---------------------------------------------------------------------------------- the burrow

	/** Opens the burrow in the pit's floor (at the Emperor's back: LuciferFight.burrowAt), for five minutes. */
	public static void openBurrow(ServerLevel level, BlockPos at) {
		level.setBlock(at, Blocks.END_GATEWAY.defaultBlockState(), 3);
		burrow = at;
		burrowUntil = level.getGameTime() + BURROW_TICKS;
		level.getServer().getPlayerList().broadcastSystemMessage(Component.literal("A tunnel opened in the ice behind Lucifer's frozen body. "
				+ "Step into it within 5 minutes to climb out to Purgatory.").withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC), false);
		HellcraftMod.LOGGER.info("The burrow opens at {}", at.toShortString());
	}

	/** A burrow left open by a restart closes (it only stays open for five minutes). */
	private static void closeLeftoverBurrow(ServerLevel level) {
		BlockPos column = net.thesmallthings.hellcraft.hazard.lucifer.LuciferFight.burrowAt(0);
		for (int[] xz : new int[][]{{0, 0}, {column.getX(), column.getZ()}}) {
			level.getChunk(xz[0] >> 4, xz[1] >> 4);
			for (int y = InfernoGeometry.PIT_FLOOR_Y - 8; y < InfernoGeometry.PIT_FLOOR_Y + 16; y++) {
				BlockPos p = new BlockPos(xz[0], y, xz[1]);
				if (level.getBlockState(p).is(Blocks.END_GATEWAY)) {
					level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
				}
			}
		}
	}

	// ---------------------------------------------------------------------------------- every tick

	public static void tick(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		ServerLevel level = level(server);
		if (!HellWorldgen.isInferno(overworld) || level == null) {
			return;
		}
		long now = overworld.getGameTime();
		if (burrow != null) {
			if (now > burrowUntil) {
				overworld.setBlock(burrow, Blocks.AIR.defaultBlockState(), 3);
				burrow = null;
			} else {
				for (ServerPlayer player : new ArrayList<>(overworld.players())) {
					if (!player.isSpectator() && player.distanceToSqr(burrow.getX() + 0.5, burrow.getY() + 0.5, burrow.getZ() + 0.5) < 1.5 * 1.5) {
						arrive(player);
					}
				}
			}
		}
		if (now % 40 == 20) {
			penitents(level);
		}
		for (ServerPlayer player : new ArrayList<>(level.players())) {
			if (player.isSpectator() || player.getY() < SHORE_Y) {
				continue;
			}
			double r = Math.sqrt(player.getX() * player.getX() + player.getZ() * player.getZ());
			terrace(player, r);
			if (now % 10 == 0) {
				streams(player);
				if (Math.abs(player.getX()) < 1.2 && Math.abs(player.getZ()) < 1.2 && Math.abs(player.getY() - (SUMMIT_Y + 1)) < 1.5) {
					home(player);
				} else if (player.blockPosition().distSqr(Heaven.ascent()) < 2) {
					Heaven.ascend(player);
				}
			}
		}
	}

	// ---------------------------------------------------------------------------------- the penitents

	/** How many penitents a terrace shows near one player, and how far round the terrace from them they come out. */
	private static final int PENITENTS_NEAR = 5;
	private static final double PENITENT_FROM = 12;
	private static final double PENITENT_TO = 32;

	/**
	 * The souls purging their sins (entity/npc/Damned's PENANCE_ punishments): every two seconds, near each player on a
	 * terrace, one more is brought out round the terrace from them until it shows a handful, each at its penance (see
	 * Damned.Punishment). They keep off the stairs and go when nobody is near. Config damnedSouls turns them off.
	 */
	private static void penitents(ServerLevel level) {
		if (!net.thesmallthings.hellcraft.config.HellConfig.get().damnedSouls) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator()) {
				continue;
			}
			double r = Math.hypot(p.getX(), p.getZ());
			int k = ring(r);
			if (k < 1 || k > TERRACES || Math.abs(p.getY() - (topY(r) + 1)) > 3) {
				continue;
			}
			int terrace = k;
			int near = level.getEntitiesOfClass(net.thesmallthings.hellcraft.entity.npc.Damned.class, p.getBoundingBox().inflate(40),
					d -> d.punishment().terrace == terrace).size();
			if (near < PENITENTS_NEAR) {
				bringOut(level, k, Math.atan2(p.getZ(), p.getX()), PENITENT_FROM, PENITENT_TO, 8);
			}
		}
	}

	/**
	 * Brings out one penitent of terrace k somewhere {@code from} to {@code to} blocks round the terrace (either way) from
	 * angle a0, on a free block of its floor clear of the ways up, trying a few spots; null if none would do. Envy's huddle
	 * against the cliff, facing out; Avarice's lie in the dust; Gluttony's stand; the rest walk round.
	 */
	@Nullable
	public static net.thesmallthings.hellcraft.entity.npc.Damned bringOut(ServerLevel level, int k, double a0, double from, double to, int tries) {
		net.thesmallthings.hellcraft.entity.npc.Damned.Punishment penance = net.thesmallthings.hellcraft.entity.npc.Damned.Punishment.penance(k);
		RandomSource random = level.getRandom();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < tries; i++) {
			double d = switch (penance) {
				case PENANCE_ENVY -> 1.2 + random.nextDouble() * 0.5;
				case PENANCE_AVARICE, PENANCE_GLUTTONY -> 2 + random.nextDouble() * 6.5;
				default -> 4 + random.nextDouble() * 3;
			};
			double r = cliff(k) + d;
			double arc = (from + random.nextDouble() * (to - from)) * (random.nextBoolean() ? 1 : -1);
			double a = a0 + arc / r;
			int x = (int) Math.floor(Math.cos(a) * r);
			int z = (int) Math.floor(Math.sin(a) * r);
			double rc = Math.hypot(x + 0.5, z + 0.5);
			if (ring(rc) != k || onAWayUp(x, z, k) || !level.isLoaded(pos.set(x, 0, z))) {
				continue;
			}
			int top = topY(rc);
			// standing room: the floor solid, the feet and head clear of the terrace's dressing
			if (level.getBlockState(pos.set(x, top, z)).getCollisionShape(level, pos).isEmpty()
					|| !level.getBlockState(pos.set(x, top + 1, z)).getCollisionShape(level, pos).isEmpty()
					|| !level.getBlockState(pos.set(x, top + 2, z)).getCollisionShape(level, pos).isEmpty()) {
				continue;
			}
			net.minecraft.world.phys.Vec3 home = new net.minecraft.world.phys.Vec3(x + 0.5, top + 1 + (penance == net.thesmallthings.hellcraft.entity.npc.Damned.Punishment.PENANCE_AVARICE ? 0.2 : 0), z + 0.5);
			if (!level.getEntitiesOfClass(net.thesmallthings.hellcraft.entity.npc.Damned.class, new net.minecraft.world.phys.AABB(home, home).inflate(1.5)).isEmpty()) {
				continue;
			}
			net.thesmallthings.hellcraft.entity.npc.Damned soul = net.thesmallthings.hellcraft.entity.HellEntities.DAMNED.create(level,
					net.minecraft.world.entity.EntitySpawnReason.EVENT);
			if (soul == null) {
				return null;
			}
			soul.punish(penance, home, !penance.walks);
			if (penance.walks) {
				soul.walkRound(rc, random.nextBoolean() ? 1 : -1);
			} else {
				// facing out from the mountain (Envy's backs to the cliff), or any way along the terrace
				double out = Math.atan2(z + 0.5, x + 0.5) + (penance == net.thesmallthings.hellcraft.entity.npc.Damned.Punishment.PENANCE_ENVY ? 0
						: (random.nextDouble() - 0.5) * Math.PI);
				float yaw = (float) Math.toDegrees(Math.atan2(-Math.cos(out), Math.sin(out)));
				soul.setYRot(yaw);
				soul.setYBodyRot(yaw);
				soul.setYHeadRot(yaw);
			}
			level.addFreshEntity(soul);
			return soul;
		}
		return null;
	}

	/** /hellcraft damned spawn penance_(sin): a penitent near here, which must be on that sin's terrace. */
	public static String spawnCommand(ServerLevel level, net.minecraft.world.phys.Vec3 at, net.thesmallthings.hellcraft.entity.npc.Damned.Punishment penance) {
		String where = "Penitents of " + penance.id() + " show only on terrace " + penance.terrace + " of Purgatory.";
		if (level.dimension() != LEVEL) {
			return where;
		}
		net.thesmallthings.hellcraft.entity.npc.Damned soul = bringOut(level, penance.terrace, Math.atan2(at.z, at.x), 0, 24, 200);
		if (soul == null) {
			return where + " Stand on it, away from the stairs.";
		}
		return String.format(java.util.Locale.ROOT, "Brought out a penitent (%s) at %.1f %.1f %.1f", penance.id(), soul.getX(), soul.getY(), soul.getZ());
	}

	/** Sets a player down on Purgatory's shore, out of Hell (the burrow, or the climb out after Lucifer). */
	public static void arrive(ServerPlayer player) {
		ServerLevel level = level(player.level().getServer());
		if (level == null) {
			return;
		}
		teleport(player, level, shore());
		Journey.award(player, "journey/purgatory");
		player.resetFallDistance();
		player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 30));
		player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("PURGATORY").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)));
		player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Thence we came forth to rebehold the stars.")
				.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
		Feedback.sound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0f, 1.4f);
		Dialogue.notice(player, Component.literal("You climbed out to Purgatory. The Great Stair ahead climbs straight up the mountain to the "
				+ "garden on top, with its two streams (Lethe and Eunoë), the Gate of Return home and the Ascent to Paradiso. Each of its seven "
				+ "landings is a terrace you can follow round the mountain.").withStyle(ChatFormatting.AQUA));
		TERRACE_REACHED.put(player.getUUID(), 0);
	}

	/** An angel erases one P from your brow at each new terrace. */
	private static void terrace(ServerPlayer player, double r) {
		int k = ring(r);
		if (k < 1 || k > TERRACES || player.getY() < topY(r) + 0.5) {
			return;
		}
		int reached = TERRACE_REACHED.getOrDefault(player.getUUID(), 0);
		if (k <= reached) {
			return;
		}
		TERRACE_REACHED.put(player.getUUID(), k);
		if (k == TERRACES) {
			// the angel at the gate keeps the Keys of Peter for whoever comes this far, if no-one has them
			net.thesmallthings.hellcraft.blood.KeysOfPeter.offer(player);
		}
		player.sendOverlayMessage(Component.literal("Terrace of " + SINS[k - 1].charAt(0) + SINS[k - 1].substring(1).toLowerCase(java.util.Locale.ROOT)
				+ " reached (" + k + "/7)").withStyle(ChatFormatting.AQUA));
		Feedback.sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.MASTER, 1.0f, 1.2f);
	}

	private static void streams(ServerPlayer player) {
		if (!player.isInWater() || player.getY() < SUMMIT_Y - 1 || Math.abs(player.getZ() + 6.5) > 1.5) {
			return;
		}
		long now = player.level().getGameTime();
		if (now < STREAM_READY.getOrDefault(player.getUUID(), 0L)) {
			return;
		}
		STREAM_READY.put(player.getUUID(), now + 100);
		ServerLevel level = player.level();
		if (player.getX() < 0) {
			// Lethe: forget every affliction
			List<MobEffectInstance> harmful = new ArrayList<>();
			for (MobEffectInstance e : player.getActiveEffects()) {
				if (!e.getEffect().value().isBeneficial()) {
					harmful.add(e);
				}
			}
			for (MobEffectInstance e : harmful) {
				player.removeEffect(e.getEffect());
			}
			player.setTicksFrozen(0);
			player.clearFire();
			level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 1, player.getZ(), 30, 0.5, 0.5, 0.5, 0.1);
			player.sendOverlayMessage(Component.literal("Lethe: all your effects are removed.").withStyle(ChatFormatting.AQUA));
			return;
		}
		// Eunoë: strength restored, once for every victory over Lucifer
		HellState state = HellState.get(level.getServer());
		HellState.Soul soul = Hearts.soul(player);
		if (soul.pendingEunoe <= 0) {
			player.sendOverlayMessage(Component.literal("Eunoë only works for players who have beaten Lucifer.").withStyle(ChatFormatting.GRAY));
			return;
		}
		soul.pendingEunoe--;
		state.setDirty();
		int gained = Hearts.add(player, 2);
		player.heal(player.getMaxHealth());
		level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.5, player.getZ(), 8, 0.5, 0.3, 0.5, 0);
		Feedback.sound(player, SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 1.0f, 0.8f);
		Dialogue.notice(player, Component.literal("Eunoë: +" + gained + " ❤.")
				.withStyle(ChatFormatting.GOLD));
	}

	/** Back down from Paradiso (the gate on the Moon's arrival isle) to the summit's garden. */
	public static void toSummit(ServerPlayer player) {
		ServerLevel level = level(player.level().getServer());
		if (level != null) {
			level.getChunk(0, 0);
			teleport(player, level, new BlockPos(-4, SUMMIT_Y + 1, 5));
			player.resetFallDistance();
			Dialogue.notice(player, Component.literal("You're back on Purgatory's summit. The Ascent beside you leads up to Paradiso again.")
					.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
	}

	/** Home to the Gate of Hell (the Gate of Return here, and the one in the Empyrean). */
	public static void home(ServerPlayer player) {
		ServerLevel level = player.level().getServer().overworld();
		int x = InfernoGeometry.gateX() + 24;
		level.getChunk(x >> 4, 0);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, 0);
		teleport(player, level, new BlockPos(x, y, 0));
		Dialogue.notice(player, Component.literal("You're back at the Gate of Hell.")
				.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}

	public static void teleport(ServerPlayer player, ServerLevel level, BlockPos to) {
		player.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, java.util.Set.of(), player.getYRot(), player.getXRot(), true);
	}

	public static void forget(ServerPlayer player) {
		STREAM_READY.remove(player.getUUID());
	}
}
