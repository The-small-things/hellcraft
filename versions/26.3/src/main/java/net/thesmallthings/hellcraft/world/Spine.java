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
import net.thesmallthings.hellcraft.net.Dialogue;
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
 * along the spine, {@code v} the sideways offset from its middle. It broke when he fell, so it isn't straight:
 * its middle wanders off the line in long curves ({@link #bend}), and no two of its bones are alike.
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
	public static final int START = (int) Math.round(InfernoGeometry.scaled(450));
	/** Where it reaches the rim of the pit. */
	public static final int END = (int) InfernoGeometry.PIT_RADIUS + 4;
	/** From here inward it is a staircase. */
	public static final int STAIRS_FROM = (int) Math.round(InfernoGeometry.scaled(110));
	/** The walkway over Cocytus, one block above the Well's floor. */
	public static final int DECK_Y = -13;
	/** Monsters never appear this close (blocks) to a spine, nor anywhere in Judecca. */
	private static final int SANCTUARY_HALF_WIDTH = 40;
	private static final double JUDECCA_RADIUS = InfernoGeometry.scaled(110.0);

	private static final List<Entity> EXILED = new ArrayList<>();
	private static final Map<UUID, Integer> NEXT_WHISPER = new HashMap<>();
	private static final Map<UUID, Long> NEXT_BEAT = new HashMap<>();
	private static final Set<UUID> HINTED = new HashSet<>();

	/** Lucifer's voice, one line per landmark on the way down (distance from the centre at which it is spoken). */
	private static final int[] WHISPER_AT = java.util.Arrays.stream(new int[]{445, 383, 323, 263, 203, 143, 107, 51})
			.map(u -> (int) Math.round(InfernoGeometry.scaled(u))).toArray();
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

	/**
	 * How far (blocks, sideways) the spine's middle has sagged off the straight line at u: two slow waves that
	 * die away at both ends, so it still leaves the Well and meets the pit where the signs and the map say. Each
	 * spine sags its own way. Never more than a block's change per block along, so the walkway stays whole.
	 */
	public static int bend(Way way, int u) {
		double t = Math.clamp((u - END) / (double) (START - END), 0.0, 1.0);
		double envelope = Math.sin(Math.PI * t);
		double[] phase = {0.4, 2.1, 3.9, 5.3};
		double[] side = {1, -1, -1, 1};
		int k = way.ordinal();
		double wave = 9.0 * Math.sin(Math.PI * 2 * t * 1.3 + phase[k]) + 3.0 * Math.sin(Math.PI * 2 * t * 3.4 + phase[(k + 2) % 4] * 1.7);
		return (int) Math.round(side[k] * envelope * wave);
	}

	/** A stable random number in [0, 1) for this bone of this spine (salt tells the uses apart). */
	private static double roll(Way way, int u, int salt) {
		long h = (way.ordinal() + 1) * 0x9E3779B97F4A7C15L ^ (u * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x165667B19E3779F9L);
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		return (h >>> 11) * 0x1.0p-53;
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

	/** True within halfWidth blocks (sideways) of a spine's middle, anywhere along it. */
	public static boolean onSpine(double x, double z, double halfWidth) {
		for (Way way : Way.values()) {
			double u = way.u(x, z);
			int ui = (int) Math.floor(u);
			if (ui >= END - 2 && ui <= START + 6 && Math.abs(way.v(x, z) - bend(way, ui)) <= halfWidth) {
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
			if (ui >= END - 2 && ui <= START + 6 && Math.abs(way.v(player.getX(), player.getZ()) - bend(way, ui)) <= 4.5 && dy >= -1.0 && dy <= 8.0) {
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

	/** A block of the spine: v is measured from its (bent) middle at u. */
	private static BlockPos at(Way way, int u, int y, int v) {
		int bent = v + bend(way, u);
		return new BlockPos(way.x(u, bent), y, way.z(u, bent));
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
		for (int v = -10; v <= 10; v += 10) {
			BlockPos p = at(way, u, 0, v);
			level.getChunk(p.getX() >> 4, p.getZ() >> 4);
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

	/**
	 * One vertebra: a body under the deck, processes to the sides and below, and (every other one) a pair of ribs.
	 * No two are alike: bodies swell and shrink, processes are snapped off, and each rib has its own length and
	 * curl on its own side (some broken short, some splintered at the end, a few gone).
	 */
	private static void vertebra(ServerLevel level, Way way, int u, int d, boolean ribs, boolean lanterns) {
		int left = roll(way, u, 1) < 0.3 ? 1 : 2;
		int right = roll(way, u, 2) < 0.3 ? 1 : 2;
		for (int v = -left; v <= right; v++) {
			set(level, way, u, d - 1, v, bone(way.along()));
		}
		for (int v = -1; v <= 1; v++) {
			set(level, way, u, d - 2, v, bone(way.along()));
		}
		if (roll(way, u, 3) < 0.35) {
			// a swollen one: a knob of bone bulging under it
			for (int v = -1; v <= 1; v++) {
				set(level, way, u + (roll(way, u, 4) < 0.5 ? 1 : -1), d - 2, v, bone(way.along()));
			}
			set(level, way, u, d - 3, (int) Math.floor(roll(way, u, 5) * 3) - 1, bone(way.along()));
		}
		for (int s : new int[]{-1, 1}) {
			int salt = s < 0 ? 10 : 20;
			if (roll(way, u, salt) > 0.2) {
				set(level, way, u, d, 2 * s, bone(way.across()));
			}
			if (roll(way, u, salt + 1) > 0.25) {
				set(level, way, u, d - 1, 3 * s, bone(way.across()));
				if (roll(way, u, salt + 2) < 0.3) {
					set(level, way, u, d - 1, 4 * s, bone(way.across()));
				}
			}
			if (lanterns) {
				set(level, way, u, d + 1, 2 * s, Blocks.SOUL_LANTERN.defaultBlockState());
			}
		}
		int spur = 2 + (int) Math.floor(roll(way, u, 6) * 3);
		for (int y = d - 2 - spur; y <= d - 3; y++) {
			if (isAir(level, way, u, y, 0)) {
				set(level, way, u, y, 0, bone(Direction.Axis.Y));
			}
		}
		if (!ribs) {
			return;
		}
		// each rib curls out and down into the dark; it stops wherever it meets the ground
		for (int s : new int[]{-1, 1}) {
			int salt = s < 0 ? 30 : 40;
			double r = roll(way, u, salt);
			if (r < 0.12) {
				continue;
			}
			// broken short, whole, or long; flared wide or drawn in; and leaning forward or back
			int length = r < 0.35 ? 2 + (int) (roll(way, u, salt + 1) * 3) : r < 0.85 ? 8 : 10;
			int flare = (int) Math.floor(roll(way, u, salt + 2) * 3) - 1;
			int lean = roll(way, u, salt + 3) < 0.25 ? (roll(way, u, salt + 4) < 0.5 ? 1 : -1) : 0;
			int[][] rib = {{4, -1}, {5, -2}, {6, -3}, {6, -4}, {6, -5}, {5, -6}, {5, -7}, {4, -8}, {4, -9}, {3, -10}};
			for (int i = 0; i < length; i++) {
				int[] p = rib[i];
				int out = p[0] + (i >= 2 ? flare : 0);
				int along = i >= 4 ? lean : 0;
				int v = out * s;
				int y = d + p[1];
				if (!isAir(level, way, u + along, y, v)) {
					break;
				}
				set(level, way, u + along, y, v, bone(Direction.Axis.Y));
			}
			if (r < 0.35 && roll(way, u, salt + 5) < 0.6) {
				// splintered: a shard of bone juts off the broken end
				int[] p = rib[length - 1];
				int v = (p[0] + (length - 1 >= 2 ? flare : 0) + 1) * s;
				int along = length - 1 >= 4 ? lean : 0;
				if (isAir(level, way, u + along, d + p[1] - 1, v)) {
					set(level, way, u + along, d + p[1] - 1, v, bone(way.across()));
				}
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

	/**
	 * /hellcraft spine test: the deck is bone along each spine's bent middle, over the lake (u 400) and on the stair
	 * (u 60), and it really is bent somewhere.
	 */
	public static String selfTest(ServerLevel level) {
		StringBuilder out = new StringBuilder("Spines:");
		boolean ok = true;
		for (Way way : Way.values()) {
			for (int u : new int[]{400, 60}) {
				BlockPos p = at(way, u, deckY(u), 0);
				level.getChunk(p.getX() >> 4, p.getZ() >> 4);
				boolean bone = level.getBlockState(p).is(Blocks.BONE_BLOCK);
				ok &= bone;
				out.append(' ').append(way.id).append('@').append(u).append(bone ? " bone" : " MISSING at " + p.toShortString());
			}
		}
		int most = 0;
		for (int u = END; u <= START; u++) {
			most = Math.max(most, Math.abs(bend(Way.EAST, u)));
		}
		ok &= most >= 6;
		return (ok ? "Spine test OK. " : "Spine test FAILED. ") + out + "; the east spine bends up to " + most + " blocks";
	}

	/** The spines' own blocks can't be broken by hand (creative players excepted). */
	public static boolean isProtected(ServerLevel level, BlockPos pos) {
		for (Way way : Way.values()) {
			int u = (int) Math.floor(way.u(pos.getX() + 0.5, pos.getZ() + 0.5));
			if (u < END - 2 || u > START + 4 || Math.abs(way.v(pos.getX() + 0.5, pos.getZ() + 0.5) - bend(way, u)) > 9.5) {
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
		Dialogue.say(player, "A voice from far below", ChatFormatting.DARK_PURPLE,
				Component.literal(WHISPERS[next]).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
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
