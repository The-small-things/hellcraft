package net.thesmallthings.hellcraft.hazard.guardian;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.util.Signs;
import net.thesmallthings.hellcraft.world.HellWorldgen;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Wakes the circle guardians when someone walks into a lair, runs their fights, and builds the lairs
 * (a ring of soul-fire braziers and a sign) once per world.
 */
public final class GuardianManager {
	private GuardianManager() {
	}

	private static final double TRIGGER = 16.0;
	private static final Map<Guardian, GuardianFight> FIGHTS = new EnumMap<>(Guardian.class);
	private static final List<Entity> STRAYS = new ArrayList<>();
	private static int ticks;

	public static boolean isGuardian(Entity entity) {
		return entity.entityTags().contains(GuardianFight.TAG);
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			for (GuardianFight f : FIGHTS.values()) {
				f.afterDamage(entity, taken);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			for (GuardianFight f : new ArrayList<>(FIGHTS.values())) {
				f.onDeath(entity);
			}
		});
		// a guardian body or model left over from a restart belongs to no fight: it goes (a tick later,
		// once a fresh body's fight has recorded it)
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(GuardianFight.TAG + "_body") || entity.entityTags().contains(GuardianFight.TAG + "_model")) {
				STRAYS.add(entity);
			}
		});
		ServerLifecycleEvents.SERVER_STARTED.register(GuardianManager::buildLairs);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (GuardianFight f : FIGHTS.values()) {
				f.end(false);
			}
			FIGHTS.clear();
		});
	}

	public static void tick(MinecraftServer server) {
		if (!STRAYS.isEmpty()) {
			for (Entity e : STRAYS) {
				boolean owned = FIGHTS.values().stream().anyMatch(f -> f.owns(e.getUUID()));
				if (!owned) {
					e.discard();
				}
			}
			STRAYS.clear();
		}
		FIGHTS.values().removeIf(f -> {
			f.tick();
			return f.done();
		});
		if (++ticks % 20 != 0 || !HellConfig.get().guardians) {
			return;
		}
		ServerLevel level = server.overworld();
		if (!HellWorldgen.isInferno(level)) {
			return;
		}
		HellState state = HellState.get(server);
		for (Guardian g : Guardian.values()) {
			if (FIGHTS.containsKey(g) || level.getGameTime() < state.guardianNext.getOrDefault(g.id(), 0L)) {
				continue;
			}
			for (ServerPlayer p : level.players()) {
				double dx = p.getX() - (g.radius + 0.5);
				double dz = p.getZ() - 0.5;
				if (!p.isSpectator() && !p.isCreative() && dx * dx + dz * dz < TRIGGER * TRIGGER) {
					start(level, g, false);
					break;
				}
			}
		}
	}

	private static BlockPos lair(ServerLevel level, Guardian g) {
		BlockPos column = g.lairColumn();
		level.getChunk(column.getX() >> 4, column.getZ() >> 4);
		return new BlockPos(column.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()), column.getZ());
	}

	private static void start(ServerLevel level, Guardian g, boolean debug) {
		GuardianFight fight = g.factory.apply(new GuardianContext(level, g, lair(level, g), debug));
		FIGHTS.put(g, fight);
		fight.start();
	}

	// ------------------------------------------------------------------ commands

	public static String summon(MinecraftServer server, Guardian g) {
		if (FIGHTS.containsKey(g)) {
			return g.title + " is already awake.";
		}
		ServerLevel level = server.overworld();
		if (!HellWorldgen.isInferno(level)) {
			return "This world is not an Inferno world.";
		}
		start(level, g, true);
		return g.title + " awakens at " + lair(level, g).toShortString() + ".";
	}

	public static String slay(Guardian g) {
		GuardianFight f = FIGHTS.get(g);
		if (f == null) {
			return g.title + " is not awake.";
		}
		f.slay();
		return g.title + " is slain.";
	}

	public static String stop(Guardian g) {
		GuardianFight f = FIGHTS.remove(g);
		if (f == null) {
			return g.title + " is not awake.";
		}
		f.end(false);
		return g.title + " sleeps again.";
	}

	public static String status(Guardian g) {
		GuardianFight f = FIGHTS.get(g);
		return f == null ? g.title + " is not awake." : f.status();
	}

	public static String attack(Guardian g, String attack) {
		GuardianFight f = FIGHTS.get(g);
		if (f == null) {
			return g.title + " is not awake.";
		}
		return f.forceAttack(attack) ? g.title + " uses " + attack + "." : g.title + " can't use " + attack + " now (attacks: " + String.join(", ", f.attacks()) + ").";
	}

	// ------------------------------------------------------------------ the lairs

	/** A ring of soul-fire braziers and a sign, on the road in from the Gate, once per world. */
	private static void buildLairs(MinecraftServer server) {
		ServerLevel level = server.overworld();
		HellState state = HellState.get(server);
		if (!HellWorldgen.isInferno(level) || state.lairsBuilt) {
			return;
		}
		for (Guardian g : Guardian.values()) {
			BlockPos c = lair(level, g);
			for (int i = 0; i < 8; i++) {
				double a = i * Math.PI / 4;
				int x = c.getX() + (int) Math.round(Math.cos(a) * 14);
				int z = c.getZ() + (int) Math.round(Math.sin(a) * 14);
				level.getChunk(x >> 4, z >> 4);
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				level.setBlock(new BlockPos(x, y, z), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 3);
				level.setBlock(new BlockPos(x, y + 1, z), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 3);
				level.setBlock(new BlockPos(x, y + 2, z), Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true), 3);
			}
			int sx = c.getX() + 18;
			int sz = 2;
			level.getChunk(sx >> 4, sz >> 4);
			int sy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx, sz);
			Signs.place(level, new BlockPos(sx, sy, sz), 12, List.of(
					Component.literal(g.title.toUpperCase(java.util.Locale.ROOT)).withStyle(ChatFormatting.BOLD),
					Component.literal(g.subtitle),
					Component.literal("waits within."),
					Component.literal("Enter if you dare.")));
			HellcraftMod.LOGGER.info("Lair of {} at {}", g.id(), c.toShortString());
		}
		state.lairsBuilt = true;
		state.setDirty();
	}
}
