package net.thesmallthings.hellcraft.hazard.guardian;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.util.Signs;
import net.thesmallthings.hellcraft.world.Fen;
import net.thesmallthings.hellcraft.world.GreatForge;
import net.thesmallthings.hellcraft.world.Paradiso;
import net.thesmallthings.hellcraft.world.ParadisoGeometry;
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
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			for (GuardianFight f : FIGHTS.values()) {
				if (!f.allowDamage(entity, source)) {
					return false;
				}
			}
			return true;
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			for (GuardianFight f : new ArrayList<>(FIGHTS.values())) {
				f.afterDamage(entity, source, taken, blocked);
			}
		});
		// no towers or walls in a lair while its guardian is awake
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world.isClientSide() || !HellConfig.get().bossNoBuilding || player.isCreative()
					|| !(player.getItemInHand(hand).getItem() instanceof BlockItem)) {
				return InteractionResult.PASS;
			}
			for (GuardianFight f : FIGHTS.values()) {
				if (f.level() == world && !f.done() && (f.inArena(player) || f.inArena(hit.getBlockPos()))) {
					if (player instanceof ServerPlayer sp) {
						sp.sendOverlayMessage(Component.literal("You can't build in a guardian's lair while it's awake.").withStyle(ChatFormatting.AQUA));
						sp.containerMenu.sendAllDataToRemote();
					}
					return InteractionResult.FAIL;
				}
			}
			return InteractionResult.PASS;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			for (GuardianFight f : new ArrayList<>(FIGHTS.values())) {
				f.onDeath(entity, source);
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
			if (g.offMap() || FIGHTS.containsKey(g) || level.getGameTime() < state.guardianNext.getOrDefault(g.id(), 0L)) {
				continue;
			}
			// Phlegyas also comes to his first landing when the east watchtower's twin beacons are lit
			if (g.wakesByBeacon() && Fen.beaconsLit(level, 0)) {
				start(level, g, 0, false);
				continue;
			}
			// it wakes in whichever of its lairs someone walks into (one fight at a time)
			lairs:
			for (int k = 0; k < g.lairs(); k++) {
				BlockPos column = g.lairColumn(k);
				for (ServerPlayer p : level.players()) {
					double dx = p.getX() - (column.getX() + 0.5);
					double dz = p.getZ() - (column.getZ() + 0.5);
					if (!p.isSpectator() && !p.isCreative() && dx * dx + dz * dz < TRIGGER * TRIGGER) {
						start(level, g, k, false);
						break lairs;
					}
				}
			}
		}
		// Vulcan, at the Great Forge of Dis
		ServerLevel nether = GreatForge.nether(server);
		if (nether != null && GreatForge.built(server) && !FIGHTS.containsKey(Guardian.VULCAN)
				&& nether.getGameTime() >= state.guardianNext.getOrDefault(Guardian.VULCAN.id(), 0L)) {
			BlockPos c = GreatForge.center();
			for (ServerPlayer p : nether.players()) {
				if (!p.isSpectator() && !p.isCreative() && p.distanceToSqr(c.getX() + 0.5, c.getY(), c.getZ() + 0.5) < TRIGGER * TRIGGER) {
					start(nether, Guardian.VULCAN, 0, false);
					break;
				}
			}
		}
		// the Seraph, on the Primum Mobile's floor in Paradiso
		ServerLevel paradiso = Paradiso.level(server);
		if (paradiso != null && !FIGHTS.containsKey(Guardian.SERAPH)
				&& paradiso.getGameTime() >= state.guardianNext.getOrDefault(Guardian.SERAPH.id(), 0L)) {
			BlockPos c = lair(paradiso, Guardian.SERAPH, 0);
			for (ServerPlayer p : paradiso.players()) {
				if (!p.isSpectator() && !p.isCreative() && p.distanceToSqr(c.getX() + 0.5, c.getY(), c.getZ() + 0.5) < TRIGGER * TRIGGER) {
					start(paradiso, Guardian.SERAPH, 0, false);
					break;
				}
			}
		}
	}

	/** Lair k's centre, on the ground (Vulcan's is the Great Forge, the Seraph's the Primum Mobile's island). */
	public static BlockPos lair(ServerLevel level, Guardian g, int k) {
		if (g.inNether()) {
			return GreatForge.center();
		}
		if (g.inParadiso()) {
			double[] c = ParadisoGeometry.centre(ParadisoGeometry.Sphere.PRIMUM_MOBILE);
			return BlockPos.containing(c[0], ParadisoGeometry.topY(ParadisoGeometry.Sphere.PRIMUM_MOBILE) + 1, c[1]);
		}
		BlockPos column = g.lairColumn(k);
		level.getChunk(column.getX() >> 4, column.getZ() >> 4);
		return new BlockPos(column.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()), column.getZ());
	}

	private static void start(ServerLevel level, Guardian g, int k, boolean debug) {
		GuardianFight fight = g.factory.apply(new GuardianContext(level, g, lair(level, g, k), k, debug));
		FIGHTS.put(g, fight);
		fight.start();
	}

	// ------------------------------------------------------------------ commands

	/** Wakes a guardian in its first lair (for tests). */
	public static String summon(MinecraftServer server, Guardian g) {
		return summon(server, g, 0);
	}

	/** Wakes a guardian in lair k (the nearest to whoever asked, from the command). */
	public static String summon(MinecraftServer server, Guardian g, int k) {
		if (FIGHTS.containsKey(g)) {
			return g.title + " is already awake.";
		}
		ServerLevel level = g.inNether() ? GreatForge.nether(server) : g.inParadiso() ? Paradiso.level(server) : server.overworld();
		if (g.inNether()) {
			if (level == null) {
				return "This server has no Nether.";
			}
			GreatForge.build(server);
		} else if (g.inParadiso()) {
			if (level == null) {
				return "This world has no Paradiso.";
			}
			BlockPos c = lair(level, g, 0);
			level.getChunk(c.getX() >> 4, c.getZ() >> 4);
		} else if (!HellWorldgen.isInferno(level)) {
			return "This world is not an Inferno world.";
		}
		int lair = g.offMap() ? 0 : Math.max(0, Math.min(g.lairs() - 1, k));
		start(level, g, lair, true);
		return g.title + " awakens at " + lair(level, g, lair).toShortString() + ".";
	}

	/** True while this guardian is awake. */
	public static boolean awake(Guardian g) {
		return FIGHTS.containsKey(g);
	}

	/** The lair this guardian is awake in, or -1. */
	public static int awakeIn(Guardian g) {
		GuardianFight f = FIGHTS.get(g);
		return f == null ? -1 : f.lairIndex;
	}

	/** True while this guardian sleeps after being beaten (or is otherwise not ready to wake). */
	public static boolean resting(ServerLevel level, Guardian g) {
		return level.getGameTime() < HellState.get(level.getServer()).guardianNext.getOrDefault(g.id(), 0L);
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

	/** A ring of soul-fire braziers round each lair, and a sign beside it, once per world. */
	private static void buildLairs(MinecraftServer server) {
		ServerLevel level = server.overworld();
		HellState state = HellState.get(server);
		if (!HellWorldgen.isInferno(level) || (state.lairsBuilt && state.moreLairsBuilt)) {
			return;
		}
		for (Guardian g : Guardian.values()) {
			// (a world built when each guardian had one lair gets the others now)
			for (int k = state.lairsBuilt ? 1 : 0; k < g.lairs(); k++) {
				buildLair(level, g, k);
			}
		}
		state.lairsBuilt = true;
		state.moreLairsBuilt = true;
		state.setDirty();
	}

	private static void buildLair(ServerLevel level, Guardian g, int k) {
		BlockPos c = lair(level, g, k);
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
		// a sign just outside the ring, on the side facing out of Hell (the way most come from), facing out
		double out = Math.atan2(c.getZ(), c.getX());
		int sx = c.getX() + (int) Math.round(Math.cos(out) * 17);
		int sz = c.getZ() + (int) Math.round(Math.sin(out) * 17);
		level.getChunk(sx >> 4, sz >> 4);
		int sy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx, sz);
		int facing = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(-Math.cos(out), Math.sin(out))) / 22.5), 16);
		Signs.place(level, new BlockPos(sx, sy, sz), facing, List.of(
				Component.literal(g.title.toUpperCase(java.util.Locale.ROOT)).withStyle(ChatFormatting.BOLD),
				Component.literal(g.subtitle),
				Component.literal("Step inside the"),
				Component.literal("fire to wake it.")));
		HellcraftMod.LOGGER.info("Lair {} of {} at {}", k + 1, g.id(), c.toShortString());
	}
}
