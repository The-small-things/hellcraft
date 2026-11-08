package net.thesmallthings.hellcraft.world;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.entity.Ferry;
import net.thesmallthings.hellcraft.entity.HellEntities;
import net.thesmallthings.hellcraft.hazard.guardian.Guardian;
import net.thesmallthings.hellcraft.hazard.guardian.GuardianManager;
import net.thesmallthings.hellcraft.net.Dialogue;
import org.jetbrains.annotations.Nullable;

/**
 * "Light the tower's beacon and the ferry comes" (L-V): lighting both beacons on a Fen watchtower calls Phlegyas's ferry
 * out of the fog to the water by the tower, and it carries whoever boards it across the Styx to the gate of Dis on that
 * road. At the east tower the beacons call Phlegyas himself while he is there to fight (GuardianManager); once he is
 * beaten his ferry waits at his landing, and the beacons call it as at the others. The roads cross the Styx too: the
 * ferry is a shortcut, never the only way.
 */
public final class Ferries {
	private Ferries() {
	}

	/** How long a ferry waits at the dock for someone to board, in ticks. */
	private static final int WAIT = 1200;
	private static final Ferry[] ACTIVE = new Ferry[4];

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		if (!HellWorldgen.isInferno(level)) {
			return;
		}
		for (int k = 0; k < Fen.towers(); k++) {
			if (ACTIVE[k] != null && ACTIVE[k].isRemoved()) {
				ACTIVE[k] = null;
			}
			if (ACTIVE[k] != null || !Fen.beaconsLit(level, k)) {
				continue;
			}
			if (k == 0 && (GuardianManager.awake(Guardian.PHLEGYAS) || !GuardianManager.resting(level, Guardian.PHLEGYAS))) {
				// these beacons call Phlegyas himself
				continue;
			}
			call(level, k);
		}
	}

	/** Calls the ferry to tower k's dock; it comes in out of the fog. */
	@Nullable
	public static Ferry call(ServerLevel level, int k) {
		Vec3 dock = dock(level, k);
		double a = InfernoGeometry.ROAD_ANGLES[k];
		Vec3 from = dock.add(-Math.cos(a) * 40, 0, -Math.sin(a) * 40);
		Ferry ferry = spawn(level, k, from, dock, roadAcross(k), roadLand(k));
		Fen.douse(level, k);
		if (ferry != null) {
			int[] t = Fen.tower(k);
			for (ServerPlayer p : level.players()) {
				if (p.distanceToSqr(t[0], p.getY(), t[1]) < 64 * 64) {
					Dialogue.notice(p, Component.literal("Out of the fog a ferry comes gliding toward the tower. Board it (right-click): it carries you "
							+ "across the Styx and sets you down inside the Walls of Dis, and nothing can hurt you aboard.")
							.withStyle(ChatFormatting.DARK_AQUA));
				}
			}
			level.playSound(null, dock.x, dock.y, dock.z, SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 2.0f, 0.5f);
		}
		return ferry;
	}

	/**
	 * Phlegyas is beaten at landing k: his ferry waits by its deck, on the side toward Dis. It crosses straight in from
	 * there (the landings stand on the diagonals, far from the roads' gates) and sets its riders down just inside the
	 * Walls of Dis, a little to one side of the Furies' lair on the same diagonal.
	 */
	public static void dockAtLanding(ServerLevel level, int k) {
		int[] c = Fen.landing(k);
		double a = Math.atan2(c[1], c[0]);
		double x = c[0] + 0.5 - Math.cos(a) * (Fen.LANDING + 4);
		double z = c[1] + 0.5 - Math.sin(a) * (Fen.LANDING + 4);
		Vec3 dock = new Vec3(x, water(), z);
		if (ACTIVE[k] != null && !ACTIVE[k].isRemoved()) {
			return;
		}
		double wall = InfernoGeometry.outerRadius(Zone.WALLS_OF_DIS);
		Vec3 across = new Vec3(Math.cos(a) * (wall + 10), water(), Math.sin(a) * (wall + 10));
		spawn(level, k, dock, dock, across, landingLand(k));
	}

	/** Where the ferry from Phlegyas's landing k sets its riders down: inside the Walls of Dis, off the Furies' lair. */
	public static Vec3 landingLand(int k) {
		int[] c = Fen.landing(k);
		double a = Math.atan2(c[1], c[0]) + LAND_TURN;
		double inside = InfernoGeometry.DIS_WALL_RADIUS - InfernoGeometry.DIS_WALL_HALF_THICKNESS - 10;
		return new Vec3(Math.cos(a) * inside + 0.5, 0, Math.sin(a) * inside + 0.5);
	}

	/** How far round from the landing's diagonal the riders are set down (radians): well clear of the Furies' lair. */
	private static final double LAND_TURN = 0.06;

	/** On the water beside road k, just short of the Walls of Dis. */
	private static Vec3 roadAcross(int k) {
		double a = InfernoGeometry.ROAD_ANGLES[k];
		double wall = InfernoGeometry.outerRadius(Zone.WALLS_OF_DIS);
		return new Vec3(Math.cos(a) * (wall + 10) - Math.sin(a) * 7, water(), Math.sin(a) * (wall + 10) + Math.cos(a) * 7);
	}

	/** On road k inside its gate of Dis. */
	private static Vec3 roadLand(int k) {
		double a = InfernoGeometry.ROAD_ANGLES[k];
		double inside = InfernoGeometry.DIS_WALL_RADIUS - InfernoGeometry.DIS_WALL_HALF_THICKNESS - 10;
		return new Vec3(Math.cos(a) * inside + 0.5, 0, Math.sin(a) * inside + 0.5);
	}

	@Nullable
	private static Ferry spawn(ServerLevel level, int k, Vec3 from, Vec3 dock, Vec3 across, Vec3 land) {
		Ferry ferry = HellEntities.FERRY.create(level, EntitySpawnReason.EVENT);
		if (ferry == null) {
			return null;
		}
		ferry.route(from, dock, across, land, WAIT);
		level.addFreshEntity(ferry);
		ACTIVE[k] = ferry;
		HellcraftMod.LOGGER.info("Ferry called to tower {} at {}", k, dock);
		return ferry;
	}

	/** The ferry's dock by tower k: on the water a little inward of it, beside the road. */
	static Vec3 dock(ServerLevel level, int k) {
		int[] t = Fen.tower(k);
		double a = InfernoGeometry.ROAD_ANGLES[k];
		return new Vec3(t[0] + 0.5 - Math.cos(a) * 9, water(), t[1] + 0.5 - Math.sin(a) * 9);
	}

	/** The ferry floats with its deck at the Styx's surface. */
	private static double water() {
		// (the Styx's water, on the east road between its shore and the Walls of Dis)
		return InfernoGeometry.fluidLevel((InfernoGeometry.outerRadius(Zone.STYX) + InfernoGeometry.outerRadius(Zone.WALLS_OF_DIS)) / 2, 0) - 0.6;
	}

	/** For the smoke test: a ferry is called to the south tower, and its route runs from the Fen to the Walls of Dis. */
	public static String selfTest(ServerLevel level) {
		Ferry ferry = call(level, 1);
		String out = ferry == null ? "Ferry test: no ferry" : "Ferry test: called to tower 1, " + ferry.phase().name().toLowerCase(java.util.Locale.ROOT);
		if (ferry != null) {
			ferry.discard();
			ACTIVE[1] = null;
		}
		return out;
	}
}
