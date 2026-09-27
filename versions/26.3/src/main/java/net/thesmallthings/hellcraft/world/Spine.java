package net.thesmallthings.hellcraft.world;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.TagValueInput;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.hazard.lucifer.LuciferManager;
import net.thesmallthings.hellcraft.util.Feedback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Emperor's Spine: the quiet road to the bottom of Hell. A backbone of bone as long as Cocytus is
 * wide leaves the Well of Giants' east rim, hangs high over the frozen lake, then becomes a long
 * staircase down to the edge of Lucifer's pit. Nothing hostile can exist on or near it; the only
 * company is a heartbeat that quickens as you descend, and Lucifer's voice from below.
 */
public final class Spine {
	private Spine() {
	}

	/** Where the spine leaves the Well of Giants (it runs along z = 0 toward the centre). */
	public static final int START_X = 660;
	/** Where it reaches the rim of the pit. */
	public static final int END_X = 34;
	/** From here inward it is a staircase. */
	public static final int STAIRS_FROM_X = 150;
	/** The walkway over Cocytus, one block above the Well's floor. */
	public static final int DECK_Y = -13;
	/** Monsters never appear this close (blocks) to the spine, nor anywhere in Judecca. */
	private static final int SANCTUARY_HALF_WIDTH = 40;
	private static final double JUDECCA_RADIUS = 150.0;

	private static final List<Entity> EXILED = new ArrayList<>();
	private static final Map<UUID, Integer> NEXT_WHISPER = new HashMap<>();
	private static final Map<UUID, Long> NEXT_BEAT = new HashMap<>();
	private static final Set<UUID> HINTED = new HashSet<>();

	/** Lucifer's voice, one line per landmark on the way down (x at which it is spoken). */
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

	/** The walkway's height at x: level over the lake, then one step down every 4 blocks. */
	public static int deckY(int x) {
		return x >= STAIRS_FROM_X ? DECK_Y : DECK_Y - (STAIRS_FROM_X - x + 3) / 4;
	}

	/** True where monsters may not exist: along the spine and in all of Judecca. */
	public static boolean sanctuary(double x, double z) {
		return (x >= 0 && x <= START_X + 30 && Math.abs(z) <= SANCTUARY_HALF_WIDTH) || x * x + z * z < JUDECCA_RADIUS * JUDECCA_RADIUS;
	}

	/** True while a player walks the spine itself (the circle's freezing torment spares them). */
	public static boolean shelters(ServerPlayer player) {
		int x = (int) Math.floor(player.getX());
		double y = player.getY() - deckY(x);
		return x >= END_X - 2 && x <= START_X + 6 && Math.abs(player.getZ()) <= 4.5 && y >= -1.0 && y <= 8.0;
	}

	// ---------------------------------------------------------------------------------- building

	public static void buildOnce(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (!HellWorldgen.isInferno(level)) {
			return;
		}
		HellState state = HellState.get(server);
		if (state.spineBuilt) {
			return;
		}
		HellcraftMod.LOGGER.info("Laying the Emperor's Spine...");
		for (int x = END_X; x <= START_X; x++) {
			segment(level, x);
		}
		for (int x = END_X; x <= START_X; x++) {
			if (x % 4 == 0) {
				vertebra(level, x, deckY(x), x % 8 == 0, x % 16 == 0);
			}
		}
		for (int x : new int[]{START_X - 1, START_X - 5, START_X - 9, END_X + 2, END_X + 6}) {
			arch(level, x, deckY(x));
		}
		int signY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, START_X + 3, 3);
		sign(level, new BlockPos(START_X + 3, signY, 3), 12, List.of(
				Component.literal("THE EMPEROR'S").withStyle(ChatFormatting.BOLD),
				Component.literal("SPINE").withStyle(ChatFormatting.BOLD),
				Component.literal("No demon walks"),
				Component.literal("this road.")));
		state.spineBuilt = true;
		state.setDirty();
		HellcraftMod.LOGGER.info("The Emperor's Spine runs from {} {} 0 down to {} {} 0", START_X, DECK_Y, END_X, deckY(END_X));
	}

	private static BlockState bone(Direction.Axis axis) {
		return Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
	}

	private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
		level.setBlock(new BlockPos(x, y, z), state, 3);
	}

	private static boolean isAir(ServerLevel level, int x, int y, int z) {
		BlockState state = level.getBlockState(new BlockPos(x, y, z));
		return state.isAir() || state.canBeReplaced();
	}

	/** The walkway at x: a 3-wide deck of bone with a cord of flesh beneath, and headroom carved above. */
	private static void segment(ServerLevel level, int x) {
		level.getChunk(x >> 4, 0);
		level.getChunk(x >> 4, -1);
		int d = deckY(x);
		for (int z = -2; z <= 2; z++) {
			for (int y = d + 1; y <= d + 5; y++) {
				if (!isAir(level, x, y, z) || level.getBlockState(new BlockPos(x, y, z)).is(Blocks.SNOW)) {
					set(level, x, y, z, Blocks.AIR.defaultBlockState());
				}
			}
		}
		for (int z = -1; z <= 1; z++) {
			set(level, x, d, z, bone(Direction.Axis.X));
		}
		set(level, x, d - 1, 0, Blocks.NETHER_WART_BLOCK.defaultBlockState());
	}

	/** One vertebra: a wider body under the deck, processes to the sides and below, and (every other one) a pair of ribs. */
	private static void vertebra(ServerLevel level, int x, int d, boolean ribs, boolean lanterns) {
		for (int z = -2; z <= 2; z++) {
			set(level, x, d - 1, z, bone(Direction.Axis.X));
		}
		for (int z = -1; z <= 1; z++) {
			set(level, x, d - 2, z, bone(Direction.Axis.X));
		}
		for (int s : new int[]{-1, 1}) {
			set(level, x, d, 2 * s, bone(Direction.Axis.Z));
			set(level, x, d - 1, 3 * s, bone(Direction.Axis.Z));
			if (lanterns) {
				set(level, x, d + 1, 2 * s, Blocks.SOUL_LANTERN.defaultBlockState());
			}
		}
		for (int y = d - 4; y <= d - 3; y++) {
			if (isAir(level, x, y, 0)) {
				set(level, x, y, 0, bone(Direction.Axis.Y));
			}
		}
		if (!ribs) {
			return;
		}
		// each rib curls out and down into the dark; it stops wherever it meets the ground
		int[][] rib = {{4, -1}, {5, -2}, {6, -3}, {6, -4}, {6, -5}, {5, -6}, {5, -7}, {4, -8}};
		for (int s : new int[]{-1, 1}) {
			for (int[] p : rib) {
				int z = p[0] * s;
				int y = d + p[1];
				if (!isAir(level, x, y, z)) {
					break;
				}
				set(level, x, y, z, bone(Direction.Axis.Y));
			}
		}
	}

	/** A pair of ribs meeting over the walkway: the gates at either end. */
	private static void arch(ServerLevel level, int x, int d) {
		int[][] half = {{3, 1}, {3, 2}, {3, 3}, {3, 4}, {3, 5}, {3, 6}, {2, 7}, {1, 8}, {0, 8}};
		for (int s : new int[]{-1, 1}) {
			for (int[] p : half) {
				set(level, x, d + p[1], p[0] * s, bone(Direction.Axis.Y));
			}
			set(level, x, d, 3 * s, bone(Direction.Axis.Y));
		}
		set(level, x, d + 9, 0, Blocks.SOUL_LANTERN.defaultBlockState());
	}

	/** A waxed standing sign in glowing red; rotation 0 faces south, 12 east. */
	private static void sign(ServerLevel level, BlockPos pos, int rotation, List<Component> lines) {
		BlockState state = Blocks.DARK_OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, rotation);
		level.setBlock(pos, state, 3);
		if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
			CompoundTag tag = new CompoundTag();
			tag.put("front_text", SignText.CODEC.encodeStart(NbtOps.INSTANCE, new SignText(lines, lines, DyeColor.RED, true)).getOrThrow());
			tag.putBoolean("is_waxed", true);
			sign.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
			sign.setChanged();
			level.sendBlockUpdated(pos, state, state, 3);
		}
	}

	/** The spine's own blocks can't be broken by hand (creative players excepted). */
	public static boolean isProtected(ServerLevel level, BlockPos pos) {
		int x = pos.getX();
		if (x < END_X - 2 || x > START_X + 4 || Math.abs(pos.getZ()) > 6) {
			return false;
		}
		int dy = pos.getY() - deckY(x);
		if (dy < -9 || dy > 10) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		return state.is(Blocks.BONE_BLOCK) || state.is(Blocks.NETHER_WART_BLOCK) || state.is(Blocks.SOUL_LANTERN) || state.is(Blocks.DARK_OAK_SIGN);
	}

	// ---------------------------------------------------------------------------------- the walk

	/** Monsters that appear near the spine or in Judecca are gone a tick later (named ones and Lucifer's own excepted). */
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
			if (!shelters(player)) {
				continue;
			}
			double progress = Math.max(0.0, Math.min(1.0, (START_X - player.getX()) / (double) (START_X - END_X)));
			// a heartbeat that quickens as you go down
			if (now >= NEXT_BEAT.getOrDefault(player.getUUID(), 0L)) {
				Feedback.sound(player, SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 0.5f + 0.7f * (float) progress, 0.7f);
				NEXT_BEAT.put(player.getUUID(), now + Math.round(44 - 26 * progress));
			}
			if (now % 10 == 0) {
				level.sendParticles(ParticleTypes.WHITE_ASH, player.getX(), player.getY() + 2, player.getZ(), 24, 8, 4, 8, 0.0);
			}
			whisper(player);
		}
	}

	private static void whisper(ServerPlayer player) {
		if (LuciferManager.fighting()) {
			return;
		}
		int next = NEXT_WHISPER.getOrDefault(player.getUUID(), 0);
		if (next >= WHISPERS.length || player.getX() > WHISPER_AT[next]) {
			return;
		}
		// skip lines for stretches the player passed without hearing them (e.g. they jumped on halfway)
		while (next + 1 < WHISPERS.length && player.getX() <= WHISPER_AT[next + 1]) {
			next++;
		}
		NEXT_WHISPER.put(player.getUUID(), next + 1);
		player.sendSystemMessage(Component.literal("A voice from far below: ").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC)
				.append(Component.literal(WHISPERS[next]).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
		Feedback.sound(player, SoundEvents.ENDERMAN_AMBIENT, SoundSource.HOSTILE, 0.5f, 0.4f);
	}

	/** The first time someone reaches the Well of Giants or the ice, tell them where the quiet road is. */
	private static void hint(ServerPlayer player) {
		Zone zone = InfernoGeometry.zoneAt(player.getX(), player.getZ());
		if ((zone != Zone.WELL_OF_GIANTS && zone != Zone.COCYTUS) || !HINTED.add(player.getUUID())) {
			return;
		}
		player.sendSystemMessage(Component.literal("On the Well's eastern rim (" + START_X + ", " + DECK_Y + ", 0) a great spine reaches down across the ice to the bottom of Hell. "
				+ "It is the one road where nothing hunts you.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}

	public static void forget(ServerPlayer player) {
		NEXT_BEAT.remove(player.getUUID());
	}
}
