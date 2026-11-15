package net.thesmallthings.hellcraft.world;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.BloodAltar;
import net.thesmallthings.hellcraft.hazard.guardian.GuardianManager;
import net.thesmallthings.hellcraft.hazard.lucifer.LuciferManager;
import net.thesmallthings.hellcraft.util.Feedback;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The ruined altars scattered through Hell (feature/AltarRuinFeature: a respawn anchor on crying obsidian inside a broken
 * ring of blackstone pillars) are small safe camps, like Virgil's Rests: no monster appears within {@link #RADIUS} blocks,
 * one that walks in is thrown back out by the soul fire, and the circles' hazards leave you alone there (Shrines.inside
 * asks {@link #inside}). The world keeps no list of them, so each chunk of an Inferno overworld is looked over as it loads
 * (its surface columns, for an altar with the ruin's pillars or floor round it), which works in worlds made before too.
 * Kept only in memory, for the loaded chunks; a ruin whose altar is broken stops protecting.
 */
public final class RuinedAltars {
	private RuinedAltars() {
	}

	/** How far round a ruined altar its protection reaches (the ruin is 7 blocks across). */
	public static final int RADIUS = 10;
	/** And how far above and below it. */
	private static final int HEIGHT = 12;

	/** The ruined altars in loaded chunks, by chunk. */
	private static final Map<Long, List<BlockPos>> RUINS = new ConcurrentHashMap<>();
	/** Altars found on a chunk's edge, waiting for the chunks beside them to load before they're looked at. */
	private static final Set<BlockPos> PENDING = ConcurrentHashMap.newKeySet();
	private static int ticks;

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> commands(dispatcher));
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> scan(level, chunk));
		ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
			if (level.dimension() == Level.OVERWORLD) {
				RUINS.remove(chunk.getPos().pack());
			}
		});
	}

	private static void scan(ServerLevel level, LevelChunk chunk) {
		if (level.dimension() != Level.OVERWORLD || !HellWorldgen.isInferno(level)) {
			return;
		}
		ChunkPos cp = chunk.getPos();
		List<BlockPos> found = new ArrayList<>();
		for (int lx = 0; lx < 16; lx++) {
			for (int lz = 0; lz < 16; lz++) {
				int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, lx, lz);
				BlockPos anchor = new BlockPos(cp.getMinBlockX() + lx, top, cp.getMinBlockZ() + lz);
				if (!chunk.getBlockState(anchor).is(Blocks.RESPAWN_ANCHOR)) {
					// the heightmap may stop on the anchor or the block above it
					anchor = anchor.below();
					if (!chunk.getBlockState(anchor).is(Blocks.RESPAWN_ANCHOR)) {
						continue;
					}
				}
				if (lx < 3 || lx > 12 || lz < 3 || lz > 12) {
					// its ruin reaches into the chunks beside: look once they're loaded
					PENDING.add(anchor);
				} else if (isRuin(level, anchor)) {
					found.add(anchor);
				}
			}
		}
		if (!found.isEmpty()) {
			RUINS.put(cp.pack(), found);
		}
	}

	/** Looks a loaded chunk over again (for a ruin built after it loaded: the client test). */
	public static void rescan(ServerLevel level, ChunkPos pos) {
		scan(level, level.getChunk(pos.x(), pos.z()));
	}

	/** A Blood Altar with the ruin round it: at least two of its corner pillars, or most of its floor of blackstone brick. */
	static boolean isRuin(Level level, BlockPos anchor) {
		if (!BloodAltar.isAltar(level, anchor)) {
			return false;
		}
		int pillars = 0;
		int floor = 0;
		for (int[] c : new int[][]{{-3, -3}, {-3, 3}, {3, -3}, {3, 3}}) {
			if (level.getBlockState(anchor.offset(c[0], 0, c[1])).is(Blocks.POLISHED_BLACKSTONE_BRICK_WALL)) {
				pillars++;
			}
		}
		for (int[] c : new int[][]{{-3, 0}, {3, 0}, {0, -3}, {0, 3}, {-2, -2}, {-2, 2}, {2, -2}, {2, 2}}) {
			BlockState s = level.getBlockState(anchor.offset(c[0], -1, c[1]));
			if (s.is(Blocks.POLISHED_BLACKSTONE_BRICKS) || s.is(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS)) {
				floor++;
			}
		}
		return pillars >= 2 || floor >= 6;
	}

	private static boolean loadedAround(ServerLevel level, BlockPos anchor) {
		for (int dx = -3; dx <= 3; dx += 6) {
			for (int dz = -3; dz <= 3; dz += 6) {
				if (!level.hasChunk((anchor.getX() + dx) >> 4, (anchor.getZ() + dz) >> 4)) {
					return false;
				}
			}
		}
		return true;
	}

	/** True within a ruined altar's protection. */
	public static boolean inside(double x, double y, double z) {
		for (List<BlockPos> ruins : RUINS.values()) {
			for (BlockPos r : ruins) {
				double dx = r.getX() + 0.5 - x;
				double dz = r.getZ() + 0.5 - z;
				if (dx * dx + dz * dz <= RADIUS * RADIUS && Math.abs(y - r.getY()) < HEIGHT) {
					return true;
				}
			}
		}
		return false;
	}

	/** The ruined altars known now (in loaded chunks). */
	public static List<BlockPos> all() {
		List<BlockPos> out = new ArrayList<>();
		RUINS.values().forEach(out::addAll);
		return out;
	}

	/**
	 * Once a second: looks over the altars waiting on their neighbours, forgets broken ones, and throws monsters out of
	 * the rings of those a player is near.
	 */
	public static void tick(MinecraftServer server) {
		if (++ticks % 20 != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		if (!HellWorldgen.isInferno(level)) {
			return;
		}
		for (BlockPos anchor : List.copyOf(PENDING)) {
			if (!level.hasChunk(anchor.getX() >> 4, anchor.getZ() >> 4)) {
				PENDING.remove(anchor);
			} else if (loadedAround(level, anchor)) {
				PENDING.remove(anchor);
				if (isRuin(level, anchor)) {
					RUINS.computeIfAbsent(ChunkPos.containing(anchor).pack(), k -> new ArrayList<>()).add(anchor);
				}
			}
		}
		if (level.players().isEmpty()) {
			return;
		}
		forgetBroken(level);
		for (List<BlockPos> ruins : RUINS.values()) {
			for (BlockPos r : ruins) {
				boolean near = false;
				for (ServerPlayer p : level.players()) {
					if (p.distanceToSqr(r.getX() + 0.5, r.getY(), r.getZ() + 0.5) < 64 * 64) {
						near = true;
						break;
					}
				}
				if (near) {
					driveOut(level, r);
				}
			}
		}
	}

	/** Forgets the ruins whose altar has been broken. */
	private static void forgetBroken(ServerLevel level) {
		for (List<BlockPos> ruins : RUINS.values()) {
			ruins.removeIf(r -> level.hasChunk(r.getX() >> 4, r.getZ() >> 4) && !BloodAltar.isAltar(level, r));
		}
		RUINS.values().removeIf(List::isEmpty);
	}

	public static void commands(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("hellcraft")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("ruins")
						.then(Commands.literal("test").executes(ctx -> {
							String result = selfTest(ctx.getSource().getServer().overworld());
							HellcraftMod.LOGGER.info(result);
							ctx.getSource().sendSuccess(() -> Component.literal(result), false);
							return 1;
						}))
						.then(Commands.literal("list").executes(ctx -> {
							List<BlockPos> all = all();
							ctx.getSource().sendSuccess(() -> Component.literal(all.size() + " ruined altar(s) in loaded ground"
									+ (all.isEmpty() ? "" : ": " + all.stream().map(BlockPos::toShortString).toList())), false);
							return all.size();
						}))));
	}

	/**
	 * For the smoke test: builds a ruined altar in Lust, looks its chunk over as a load would, checks its ring protects
	 * (and nothing beyond it), then breaks the altar and checks the protection goes with it.
	 */
	public static String selfTest(ServerLevel level) {
		int x = (int) InfernoGeometry.scaled(2400) + 8;
		int z = 296 + 8;
		LevelChunk chunk = level.getChunk(x >> 4, z >> 4);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos at = new BlockPos(x, y, z);
		level.setBlock(at.below(), Blocks.STONE.defaultBlockState(), 2);
		net.thesmallthings.hellcraft.world.feature.AltarRuinFeature.INSTANCE.place(level, level.getChunkSource().getGenerator(),
				net.minecraft.util.RandomSource.create(5), at);
		scan(level, chunk);
		boolean found = all().contains(at);
		boolean protects = inside(x + 6.5, y, z + 0.5) && Shrines.near(level, at.offset(-5, 0, 3));
		boolean outside = !inside(x + 14.5, y, z + 0.5);
		level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
		forgetBroken(level);
		boolean broken = !inside(x + 6.5, y, z + 0.5);
		return "Ruined altars test: found=" + found + " protects=" + protects + " outside=" + outside + " broken stops=" + broken;
	}

	/** Hostile mobs that walk into the ring are thrown back out by the soul fire, as at a Rest. */
	private static void driveOut(ServerLevel level, BlockPos r) {
		double cx = r.getX() + 0.5;
		double cz = r.getZ() + 0.5;
		AABB box = new AABB(cx - RADIUS, r.getY() - HEIGHT, cz - RADIUS, cx + RADIUS, r.getY() + HEIGHT, cz + RADIUS);
		for (Mob mob : level.getEntitiesOfClass(Mob.class, box, m -> m instanceof Enemy && m.isAlive() && !m.hasCustomName()
				&& !GuardianManager.isGuardian(m) && !LuciferManager.isLucifer(m))) {
			double dx = mob.getX() - cx;
			double dz = mob.getZ() - cz;
			if (dx * dx + dz * dz > RADIUS * RADIUS) {
				continue;
			}
			Feedback.pushAway(mob, cx, cz, 1.2);
			mob.setTarget(null);
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, mob.getX(), mob.getY() + 1, mob.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
		}
	}
}
