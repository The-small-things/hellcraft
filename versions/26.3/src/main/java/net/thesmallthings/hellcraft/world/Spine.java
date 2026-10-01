package net.thesmallthings.hellcraft.world;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.hazard.lucifer.LuciferManager;
import net.thesmallthings.hellcraft.util.Feedback;
import net.thesmallthings.hellcraft.util.Signs;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Emperor's Spines: the quiet roads to the bottom of Hell. Four backbones of bone, one at each
 * point of the compass, leave the Well of Giants' rim, hang high over the frozen lake, then become
 * long staircases down to the edge of Lucifer's pit. Nothing hostile can exist on or near them; the
 * only company is a heartbeat that quickens as you descend, and Lucifer's voice from below.
 *
 * <p>Positions are worked out along each spine: {@code u} is the distance from the centre of Hell
 * along the spine, {@code v} the sideways offset from its middle.
 */
public final class Spine {
	private Spine() {
	}

	/** One spine, running from the centre of Hell out along a compass direction. */
	public enum Way {
		EAST("east", 1, 0), SOUTH("south", 0, 1), WEST("west", -1, 0), NORTH("north", 0, -1);

		public final String id;
		final int ax;
		final int az;

		Way(String id, int ax, int az) {
			this.id = id;
			this.ax = ax;
			this.az = az;
		}

		public int x(int u, int v) {
			return u * ax - v * az;
		}

		public int z(int u, int v) {
			return u * az + v * ax;
		}

		double u(double x, double z) {
			return x * ax + z * az;
		}

		double v(double x, double z) {
			return z * ax - x * az;
		}

		Direction.Axis along() {
			return ax != 0 ? Direction.Axis.X : Direction.Axis.Z;
		}

		Direction.Axis across() {
			return ax != 0 ? Direction.Axis.Z : Direction.Axis.X;
		}

		/** Sign rotation facing outward, toward pilgrims arriving from the Well (0 south, 4 west, 8 north, 12 east). */
		int outwardRotation() {
			return ax > 0 ? 12 : ax < 0 ? 4 : az > 0 ? 0 : 8;
		}
	}

	/** Where a spine leaves the Well of Giants (distance from the centre). */
	public static final int START = 660;
	/** Where it reaches the rim of the pit. */
	public static final int END = 34;
	/** From here inward it is a staircase. */
	public static final int STAIRS_FROM = 150;
	/** The walkway over Cocytus, one block above the Well's floor. */
	public static final int DECK_Y = -13;
	/** Monsters never appear this close (blocks) to a spine, nor anywhere in Judecca. */
	private static final int SANCTUARY_HALF_WIDTH = 40;
	private static final double JUDECCA_RADIUS = 150.0;

	private static final List<Entity> EXILED = new ArrayList<>();
	private static final Map<UUID, Integer> NEXT_WHISPER = new HashMap<>();
	private static final Map<UUID, Long> NEXT_BEAT = new HashMap<>();
	private static final Set<UUID> HINTED = new HashSet<>();

	/** Lucifer's voice, one line per landmark on the way down (distance from the centre at which it is spoken). */
	private static final int[] WHISPER_AT = {652, 560, 470, 380, 290, 200, 146, 60};
	private static final String[] WHISPERS = {
			"Ah. Thou hast found my spine. I broke it when He cast me down. Walk it; it leads to me.",
			"Nothing will touch thee on this road. Not a demon, not a shade. I forbid it. I want thee WHOLE when thou arrivest.",
			"Look down. That is Cocytus. My wings froze it, and every traitor in it can hear thy footsteps.",
			"They called me Light-Bringer once. Tell me, heart-thief... dost thou see any light down here?",
			"Every heart thou stolest to come this far is still beating. I can hear them. Not one of them is thine.",
			"Judas walked this road. Brutus. Cassius. They were so sure of themselves, too.",
			"Down now. Down, to the bottom of all things.",
			"Closer. I have waited since before thy world had a name.",
	};

	/** The walkway's height at distance u: level over the lake, then one step down every 4 blocks. */
	public static int deckY(int u) {
		return u >= STAIRS_FROM ? DECK_Y : DECK_Y - (STAIRS_FROM - u + 3) / 4;
	}

	@Nullable
	public static Way byId(String id) {
		for (Way way : Way.values()) {
			if (way.id.equals(id) || way.id.substring(0, 1).equals(id)) {
				return way;
			}
		}
		return null;
	}

	/** True where monsters may not exist: along the spines and in all of Judecca. */
	public static boolean sanctuary(double x, double z) {
		if (x * x + z * z < JUDECCA_RADIUS * JUDECCA_RADIUS) {
			return true;
		}
		for (Way way : Way.values()) {
			double u = way.u(x, z);
			if (u >= 0 && u <= START + 30 && Math.abs(way.v(x, z)) <= SANCTUARY_HALF_WIDTH) {
				return true;
			}
		}
		return false;
	}

	/** The spine a player is walking right now, if any (the circle's freezing torment spares them). */
	@Nullable
	public static Way walking(ServerPlayer player) {
		for (Way way : Way.values()) {
			double u = way.u(player.getX(), player.getZ());
			int ui = (int) Math.floor(u);
			double dy = player.getY() - deckY(ui);
			if (ui >= END - 2 && ui <= START + 6 && Math.abs(way.v(player.getX(), player.getZ())) <= 4.5 && dy >= -1.0 && dy <= 8.0) {
				return way;
			}
		}
		return null;
	}

	public static boolean shelters(ServerPlayer player) {
		return walking(player) != null;
	}

	// ---------------------------------------------------------------------------------- building

	/** Lays every spine this world doesn't have yet (worlds from before the other three get them added). */
	public static void buildOnce(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (!HellWorldgen.isInferno(level)) {
			return;
		}
		HellState state = HellState.get(server);
		boolean changed = false;
		for (Way way : Way.values()) {
			boolean built = way == Way.EAST ? state.spineBuilt : state.spinesBuilt;
			if (built) {
				continue;
			}
			build(level, way);
			changed = true;
		}
		if (changed) {
			state.spineBuilt = true;
			state.spinesBuilt = true;
			state.setDirty();
		}
	}

	private static void build(ServerLevel level, Way way) {
		HellcraftMod.LOGGER.info("Laying the Emperor's {} Spine...", way.id);
		for (int u = END; u <= START; u++) {
			segment(level, way, u);
		}
		for (int u = END; u <= START; u++) {
			if (u % 4 == 0) {
				vertebra(level, way, u, deckY(u), u % 8 == 0, u % 16 == 0);
			}
		}
		for (int u : new int[]{START - 1, START - 5, START - 9, END + 2, END + 6}) {
			arch(level, way, u, deckY(u));
		}
		int sx = way.x(START + 3, 3);
		int sz = way.z(START + 3, 3);
		int signY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx, sz);
		Signs.place(level, new BlockPos(sx, signY, sz), way.outwardRotation(), List.of(
				Component.literal("THE EMPEROR'S").withStyle(ChatFormatting.BOLD),
				Component.literal("SPINE").withStyle(ChatFormatting.BOLD),
				Component.literal("No demon walks"),
				Component.literal("this road.")));
		HellcraftMod.LOGGER.info("The Emperor's Spine runs from {} {} {} down to {} {} {}", way.x(START, 0), DECK_Y, way.z(START, 0),
				way.x(END, 0), deckY(END), way.z(END, 0));
	}

	/** Where a spine starts, for hints and teleports. */
	public static BlockPos start(Way way) {
		return new BlockPos(way.x(START, 0), DECK_Y, way.z(START, 0));
	}

	private static BlockState bone(Direction.Axis axis) {
		return Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
	}

	private static BlockPos at(Way way, int u, int y, int v) {
		return new BlockPos(way.x(u, v), y, way.z(u, v));
	}

	private static void set(ServerLevel level, Way way, int u, int y, int v, BlockState state) {
		level.setBlock(at(way, u, y, v), state, 3);
	}

	private static boolean isAir(ServerLevel level, Way way, int u, int y, int v) {
		BlockState state = level.getBlockState(at(way, u, y, v));
		return state.isAir() || state.canBeReplaced();
	}

	/** The walkway at u: a 3-wide deck of bone with a cord of flesh beneath, and headroom carved above. */
	private static void segment(ServerLevel level, Way way, int u) {
		for (int v = -8; v <= 8; v += 8) {
			level.getChunk(way.x(u, v) >> 4, way.z(u, v) >> 4);
		}
		int d = deckY(u);
		for (int v = -2; v <= 2; v++) {
			for (int y = d + 1; y <= d + 5; y++) {
				if (!isAir(level, way, u, y, v) || level.getBlockState(at(way, u, y, v)).is(Blocks.SNOW)) {
					set(level, way, u, y, v, Blocks.AIR.defaultBlockState());
				}
			}
		}
		for (int v = -1; v <= 1; v++) {
			set(level, way, u, d, v, bone(way.along()));
		}
		set(level, way, u, d - 1, 0, Blocks.NETHER_WART_BLOCK.defaultBlockState());
	}

	/** One vertebra: a wider body under the deck, processes to the sides and below, and (every other one) a pair of ribs. */
	private static void vertebra(ServerLevel level, Way way, int u, int d, boolean ribs, boolean lanterns) {
		for (int v = -2; v <= 2; v++) {
			set(level, way, u, d - 1, v, bone(way.along()));
		}
		for (int v = -1; v <= 1; v++) {
			set(level, way, u, d - 2, v, bone(way.along()));
		}
		for (int s : new int[]{-1, 1}) {
			set(level, way, u, d, 2 * s, bone(way.across()));
			set(level, way, u, d - 1, 3 * s, bone(way.across()));
			if (lanterns) {
				set(level, way, u, d + 1, 2 * s, Blocks.SOUL_LANTERN.defaultBlockState());
			}
		}
		for (int y = d - 4; y <= d - 3; y++) {
			if (isAir(level, way, u, y, 0)) {
				set(level, way, u, y, 0, bone(Direction.Axis.Y));
			}
		}
		if (!ribs) {
			return;
		}
		// each rib curls out and down into the dark; it stops wherever it meets the ground
		int[][] rib = {{4, -1}, {5, -2}, {6, -3}, {6, -4}, {6, -5}, {5, -6}, {5, -7}, {4, -8}};
		for (int s : new int[]{-1, 1}) {
			for (int[] p : rib) {
				int v = p[0] * s;
				int y = d + p[1];
				if (!isAir(level, way, u, y, v)) {
					break;
				}
				set(level, way, u, y, v, bone(Direction.Axis.Y));
			}
		}
	}

	/** A pair of ribs meeting over the walkway: the gates at either end. */
	private static void arch(ServerLevel level, Way way, int u, int d) {
		int[][] half = {{3, 1}, {3, 2}, {3, 3}, {3, 4}, {3, 5}, {3, 6}, {2, 7}, {1, 8}, {0, 8}};
		for (int s : new int[]{-1, 1}) {
			for (int[] p : half) {
				set(level, way, u, d + p[1], p[0] * s, bone(Direction.Axis.Y));
			}
			set(level, way, u, d, 3 * s, bone(Direction.Axis.Y));
		}
		set(level, way, u, d + 9, 0, Blocks.SOUL_LANTERN.defaultBlockState());
	}

	/** The spines' own blocks can't be broken by hand (creative players excepted). */
	public static boolean isProtected(ServerLevel level, BlockPos pos) {
		for (Way way : Way.values()) {
			int u = (int) Math.floor(way.u(pos.getX() + 0.5, pos.getZ() + 0.5));
			if (u < END - 2 || u > START + 4 || Math.abs(way.v(pos.getX() + 0.5, pos.getZ() + 0.5)) > 6.5) {
				continue;
			}
			int dy = pos.getY() - deckY(u);
			if (dy < -9 || dy > 10) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			return state.is(Blocks.BONE_BLOCK) || state.is(Blocks.NETHER_WART_BLOCK) || state.is(Blocks.SOUL_LANTERN) || state.is(Blocks.DARK_OAK_SIGN);
		}
		return false;
	}

	// ---------------------------------------------------------------------------------- the walk

	/** Monsters that appear near a spine or in Judecca are gone a tick later (named ones and Lucifer's own excepted). */
	public static void onLoad(Entity entity, ServerLevel level) {
		if (entity instanceof Enemy && !entity.hasCustomName() && !LuciferManager.isLucifer(entity)
				&& sanctuary(entity.getX(), entity.getZ()) && HellWorldgen.isInferno(level)) {
			EXILED.add(entity);
		}
	}

	public static void tick(MinecraftServer server) {
		if (!EXILED.isEmpty()) {
			for (Entity e : EXILED) {
				e.discard();
			}
			EXILED.clear();
		}
		ServerLevel level = server.overworld();
		if (!HellWorldgen.isInferno(level)) {
			return;
		}
		long now = level.getGameTime();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			if (now % 20 == 0) {
				hint(player);
			}
			Way way = walking(player);
			if (way == null) {
				continue;
			}
			double u = way.u(player.getX(), player.getZ());
			double progress = Math.max(0.0, Math.min(1.0, (START - u) / (double) (START - END)));
			// a heartbeat that quickens as you go down
			if (now >= NEXT_BEAT.getOrDefault(player.getUUID(), 0L)) {
				Feedback.sound(player, SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 0.5f + 0.7f * (float) progress, 0.7f);
				NEXT_BEAT.put(player.getUUID(), now + Math.round(44 - 26 * progress));
			}
			if (now % 10 == 0) {
				level.sendParticles(ParticleTypes.WHITE_ASH, player.getX(), player.getY() + 2, player.getZ(), 24, 8, 4, 8, 0.0);
			}
			whisper(player, u);
		}
	}

	private static void whisper(ServerPlayer player, double u) {
		if (LuciferManager.fighting()) {
			return;
		}
		int next = NEXT_WHISPER.getOrDefault(player.getUUID(), 0);
		if (next >= WHISPERS.length || u > WHISPER_AT[next]) {
			return;
		}
		// skip lines for stretches the player passed without hearing them (e.g. they jumped on halfway)
		while (next + 1 < WHISPERS.length && u <= WHISPER_AT[next + 1]) {
			next++;
		}
		NEXT_WHISPER.put(player.getUUID(), next + 1);
		player.sendSystemMessage(Component.literal("A voice from far below: ").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC)
				.append(Component.literal(WHISPERS[next]).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
		Feedback.sound(player, SoundEvents.ENDERMAN_AMBIENT, SoundSource.HOSTILE, 0.5f, 0.4f);
	}

	/** The spine start nearest to a position. */
	public static Way nearest(double x, double z) {
		Way best = Way.EAST;
		double bestDist = Double.MAX_VALUE;
		for (Way way : Way.values()) {
			BlockPos s = start(way);
			double d = (s.getX() - x) * (s.getX() - x) + (s.getZ() - z) * (s.getZ() - z);
			if (d < bestDist) {
				bestDist = d;
				best = way;
			}
		}
		return best;
	}

	/** The first time someone reaches the Well of Giants or the ice, tell them where the nearest quiet road is. */
	private static void hint(ServerPlayer player) {
		Zone zone = InfernoGeometry.zoneAt(player.getX(), player.getZ());
		if ((zone != Zone.WELL_OF_GIANTS && zone != Zone.COCYTUS) || !HINTED.add(player.getUUID())) {
			return;
		}
		Way way = nearest(player.getX(), player.getZ());
		BlockPos s = start(way);
		player.sendSystemMessage(Component.literal("The nearest spine starts on the " + way.id + " edge of the Well (" + s.getX() + ", " + s.getY() + ", " + s.getZ()
				+ "). It's a safe path down to Lucifer's pit: no monsters spawn on it. "
				+ "There are four: north, east, south and west.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}

	public static void forget(ServerPlayer player) {
		NEXT_BEAT.remove(player.getUUID());
	}
}
