package net.thesmallthings.hellcraft.world;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.Hearts;
import net.thesmallthings.hellcraft.blood.HellState;
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
 * whoever steps into it comes out on the shore of Purgatory, a mountain island floating high above
 * the pit under the stars ("Thence we came forth to rebehold the stars").
 *
 * <p>Seven terraces spiral up the mountain, one for each capital sin (an angel erases a P from your
 * brow at each), to the Earthly Paradise on the summit, where two streams run: <b>Lethe</b>, which
 * washes away every affliction, and <b>Eunoë</b>, which restores two hearts once for every victory
 * over Lucifer. The Gate of Return in the middle of the garden leads back to the Gate of Hell.
 */
public final class Purgatory {
	private Purgatory() {
	}

	/** The beach, the lowest ledge of the island (its top block). */
	static final int SHORE_Y = 226;
	private static final int SHORE_RADIUS = 52;
	private static final int FIRST_TERRACE = 44;
	private static final double TERRACE_WIDTH = 4.5;
	private static final int TERRACE_RISE = 8;
	private static final int TERRACES = 7;
	/** The summit's top block (the Earthly Paradise). */
	public static final int SUMMIT_Y = SHORE_Y + TERRACE_RISE * (TERRACES + 1);
	private static final int BOTTOM_Y = 186;
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

	/** Where you arrive: the southern shore. */
	public static BlockPos shore() {
		return new BlockPos(0, SHORE_Y + 1, 48);
	}

	@Nullable
	private static BlockPos burrow;
	private static long burrowUntil;
	private static final Map<UUID, Integer> TERRACE_REACHED = new HashMap<>();
	private static final Map<UUID, Long> STREAM_READY = new HashMap<>();

	// ---------------------------------------------------------------------------------- the mountain

	/** Terrace index at a distance from the axis: 0 the shore, 1..7 the terraces, 8 the summit, -1 outside. */
	static int ring(double r) {
		if (r > SHORE_RADIUS) {
			return -1;
		}
		if (r >= FIRST_TERRACE) {
			return 0;
		}
		int k = (int) Math.floor((FIRST_TERRACE - r) / TERRACE_WIDTH) + 1;
		return Math.min(k, TERRACES + 1);
	}

	static int topY(double r) {
		int k = ring(r);
		return k < 0 ? Integer.MIN_VALUE : SHORE_Y + TERRACE_RISE * k;
	}

	private static int bottomY(double r) {
		return (int) Math.round(SHORE_Y - 2 - (SHORE_RADIUS - r) * 0.75);
	}

	public static void buildOnce(MinecraftServer server) {
		ServerLevel level = server.overworld();
		HellState state = HellState.get(server);
		if (!HellWorldgen.isInferno(level)) {
			return;
		}
		closeLeftoverBurrow(level);
		if (state.purgatoryBuilt) {
			return;
		}
		HellcraftMod.LOGGER.info("Raising the Mountain of Purgatory...");
		RandomSource random = RandomSource.create(1300L);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = -SHORE_RADIUS; x <= SHORE_RADIUS; x++) {
			for (int z = -SHORE_RADIUS; z <= SHORE_RADIUS; z++) {
				double r = Math.sqrt(x * x + z * z);
				int k = ring(r);
				if (k < 0) {
					continue;
				}
				level.getChunk(x >> 4, z >> 4);
				int top = topY(r);
				int bottom = bottomY(r);
				// fill solid only where it can be seen: the underside, the surface, and cliff faces
				int lowestNeighbour = top;
				for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					double rn = Math.sqrt((x + d[0]) * (x + d[0]) + (z + d[1]) * (z + d[1]));
					lowestNeighbour = Math.min(lowestNeighbour, ring(rn) < 0 ? bottom : topY(rn));
				}
				for (int y = bottom; y <= top; y++) {
					boolean visible = y <= bottom + 3 || y >= top - 3 || y >= lowestNeighbour - 1;
					if (!visible) {
						continue;
					}
					pos.set(x, y, z);
					level.setBlock(pos, material(k, y, top, random), 2);
				}
			}
		}
		for (int k = 0; k <= TERRACES; k++) {
			stairway(level, k);
		}
		for (int k = 1; k <= TERRACES; k++) {
			double r = FIRST_TERRACE - (k - 0.5) * TERRACE_WIDTH;
			double a = stairAngle(k) - 0.35;
			int x = (int) Math.round(Math.cos(a) * r);
			int z = (int) Math.round(Math.sin(a) * r);
			Signs.place(level, new BlockPos(x, SHORE_Y + TERRACE_RISE * k + 1, z), 0, List.of(
					Component.literal("TERRACE " + k).withStyle(ChatFormatting.BOLD),
					Component.literal(SINS[k - 1]),
					Component.literal(SIN_LINES[k - 1]),
					Component.literal("")));
		}
		paradise(level, random);
		Signs.place(level, shore().offset(2, 0, 0), 8, List.of(
				Component.literal("PURGATORY").withStyle(ChatFormatting.BOLD),
				Component.literal("Seven terraces"),
				Component.literal("to the Earthly"),
				Component.literal("Paradise")));
		state.purgatoryBuilt = true;
		state.setDirty();
		HellcraftMod.LOGGER.info("The Mountain of Purgatory rises over the pit, summit at y {}", SUMMIT_Y);
	}

	private static BlockState material(int k, int y, int top, RandomSource random) {
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

	private static double stairAngle(int k) {
		// the stairways spiral around the mountain, starting from the southern shore
		return Math.PI / 2 + k * 2 * Math.PI / 8;
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
				Component.literal("Step in to forget"),
				Component.literal("every affliction"),
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

	/** Opens the natural burrow at the bottom of the pit, for five minutes. */
	public static void openBurrow(ServerLevel level, int floorY) {
		BlockPos at = new BlockPos(0, floorY, 0);
		level.setBlock(at, Blocks.END_GATEWAY.defaultBlockState(), 3);
		burrow = at;
		burrowUntil = level.getGameTime() + BURROW_TICKS;
		level.getServer().getPlayerList().broadcastSystemMessage(Component.literal("Where Lucifer was frozen, a narrow burrow opens in the ice. "
				+ "Step into it to climb out of Hell (for five minutes).").withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC), false);
		HellcraftMod.LOGGER.info("The burrow opens at {}", at.toShortString());
	}

	/** A burrow left open by a restart closes (it only stays open for five minutes). */
	private static void closeLeftoverBurrow(ServerLevel level) {
		level.getChunk(0, 0);
		for (int y = InfernoGeometry.PIT_FLOOR_Y - 8; y < InfernoGeometry.PIT_FLOOR_Y + 16; y++) {
			BlockPos p = new BlockPos(0, y, 0);
			if (level.getBlockState(p).is(Blocks.END_GATEWAY)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
			}
		}
	}

	// ---------------------------------------------------------------------------------- every tick

	public static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (!HellWorldgen.isInferno(level)) {
			return;
		}
		long now = level.getGameTime();
		if (burrow != null && now > burrowUntil) {
			level.setBlock(burrow, Blocks.AIR.defaultBlockState(), 3);
			burrow = null;
		}
		for (ServerPlayer player : new ArrayList<>(level.players())) {
			if (player.isSpectator()) {
				continue;
			}
			double r = Math.sqrt(player.getX() * player.getX() + player.getZ() * player.getZ());
			if (burrow != null && player.distanceToSqr(burrow.getX() + 0.5, burrow.getY() + 0.5, burrow.getZ() + 0.5) < 1.5 * 1.5) {
				arrive(player);
				continue;
			}
			if (r > 60 || player.getY() < BOTTOM_Y - 70) {
				continue;
			}
			// an angel catches anyone who falls off the mountain
			if (player.getY() < SHORE_Y - 8 && player.getY() > BOTTOM_Y - 70 && player.getDeltaMovement().y < -0.6 && !player.isFallFlying()) {
				teleport(player, shore());
				player.resetFallDistance();
				player.sendSystemMessage(Component.literal("A white wing sweeps beneath you, and sets you down on the shore.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
				continue;
			}
			if (player.getY() < SHORE_Y) {
				continue;
			}
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

	private static void arrive(ServerPlayer player) {
		teleport(player, shore());
		Journey.award(player, "journey/purgatory");
		player.resetFallDistance();
		player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 30));
		player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("PURGATORY").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)));
		player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Thence we came forth to rebehold the stars.")
				.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
		Feedback.sound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0f, 1.4f);
		player.sendSystemMessage(Component.literal("You climb out onto the shore of the Mountain of Purgatory. Seven terraces rise to the "
				+ "Earthly Paradise, where Lethe and Eunoë run, and the Gate of Return.").withStyle(ChatFormatting.AQUA));
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
		player.sendOverlayMessage(Component.literal("Terrace of " + SINS[k - 1].charAt(0) + SINS[k - 1].substring(1).toLowerCase(java.util.Locale.ROOT)
				+ ": an angel's wing brushes your brow, and a P is gone (" + k + "/7)").withStyle(ChatFormatting.AQUA));
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
			player.sendOverlayMessage(Component.literal("Lethe washes over you, and you forget your afflictions.").withStyle(ChatFormatting.AQUA));
			return;
		}
		// Eunoë: strength restored, once for every victory over Lucifer
		HellState state = HellState.get(level.getServer());
		HellState.Soul soul = Hearts.soul(player);
		if (soul.pendingEunoe <= 0) {
			player.sendOverlayMessage(Component.literal("Eunoë is only sweet to those who have cast Lucifer down.").withStyle(ChatFormatting.GRAY));
			return;
		}
		soul.pendingEunoe--;
		state.setDirty();
		int gained = Hearts.add(player, 2);
		player.heal(player.getMaxHealth());
		level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.5, player.getZ(), 8, 0.5, 0.3, 0.5, 0);
		Feedback.sound(player, SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 1.0f, 0.8f);
		player.sendSystemMessage(Component.literal("You drink from Eunoë: +" + gained + " ❤, made new, and ready to rise to the stars.")
				.withStyle(ChatFormatting.GOLD));
	}

	private static void home(ServerPlayer player) {
		ServerLevel level = player.level();
		int x = InfernoGeometry.gateX() + 24;
		level.getChunk(x >> 4, 0);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, 0);
		teleport(player, new BlockPos(x, y, 0));
		player.sendSystemMessage(Component.literal("You step through the Gate of Return, and stand once more before the Gate of Hell.")
				.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}

	private static void teleport(ServerPlayer player, BlockPos to) {
		player.teleportTo(player.level(), to.getX() + 0.5, to.getY(), to.getZ() + 0.5, java.util.Set.of(), player.getYRot(), player.getXRot(), true);
	}

	public static void forget(ServerPlayer player) {
		STREAM_READY.remove(player.getUUID());
	}
}
