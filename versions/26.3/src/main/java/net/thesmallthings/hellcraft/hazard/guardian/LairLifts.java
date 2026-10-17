package net.thesmallthings.hellcraft.hazard.guardian;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.levelgen.Heightmap;
import net.thesmallthings.hellcraft.blood.Hearts;
import net.thesmallthings.hellcraft.net.Dialogue;
import net.thesmallthings.hellcraft.world.HellWorldgen;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.Zone;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The guardians' lifts (REVAMP Q2: beating a boss opens a shortcut down that cliff; the roads and ramps stay open).
 * Once you have beaten a guardian, a column of soul fire in the middle of its lair takes you down to the next circle:
 * stand in it for three seconds. Minos's goes down to Gluttony, Cerberus's to Greed, Plutus's to the Styx (onto the
 * nearest road's causeway), Farinata's past the river of blood to the Wood of the Suicides, the Minotaur's to the
 * Burning Sands. The others have their own way on (Phlegyas's ferry, Geryon's flight, the Furies keep the gate).
 */
public final class LairLifts {
	private LairLifts() {
	}

	/** Seconds of standing in the column before it takes you. */
	private static final int STAND = 3;
	private static final Map<UUID, Integer> STANDING = new HashMap<>();
	private static int ticks;

	/** Where a guardian's lift goes, or null for those with no lift. */
	@Nullable
	public static Zone destination(Guardian g) {
		return switch (g) {
			case MINOS -> Zone.GLUTTONY;
			case CERBERUS -> Zone.GREED;
			case PLUTUS -> Zone.STYX;
			case FARINATA -> Zone.WOOD_OF_SUICIDES;
			case MINOTAUR -> Zone.BURNING_SANDS;
			default -> null;
		};
	}

	/** A lift's destination, as players know it. */
	static String place(Zone zone) {
		return switch (zone) {
			case GLUTTONY -> "Gluttony";
			case GREED -> "Greed";
			case STYX -> "the Styx";
			case WOOD_OF_SUICIDES -> "the Wood of the Suicides";
			case BURNING_SANDS -> "the Burning Sands";
			default -> zone.id();
		};
	}

	/**
	 * The column a lift sets you down on, {x, z}: the middle of the next circle straight in from the lair, or, where that
	 * is water or lava, the nearest road (its causeways cross the rivers).
	 */
	public static double[] landing(Guardian g) {
		Zone zone = destination(g);
		BlockPos lair = g.lairColumn();
		double angle = Math.atan2(lair.getZ(), lair.getX());
		double r = (InfernoGeometry.outerRadius(zone) + innerRadius(zone)) / 2;
		double x = Math.cos(angle) * r;
		double z = Math.sin(angle) * r;
		if (InfernoGeometry.fluidAt(x, z) != InfernoGeometry.Fluid.NONE) {
			double best = InfernoGeometry.ROAD_ANGLES[0];
			for (double a : InfernoGeometry.ROAD_ANGLES) {
				if (InfernoGeometry.angularDistance(a, angle) < InfernoGeometry.angularDistance(best, angle)) {
					best = a;
				}
			}
			x = Math.cos(best) * r;
			z = Math.sin(best) * r;
		}
		return new double[]{x, z};
	}

	private static double innerRadius(Zone zone) {
		double outer = InfernoGeometry.outerRadius(zone);
		double inner = 0;
		for (Zone other : Zone.values()) {
			double o;
			try {
				o = InfernoGeometry.outerRadius(other);
			} catch (IllegalArgumentException e) {
				continue;
			}
			if (o < outer && o > inner) {
				inner = o;
			}
		}
		return inner;
	}

	/** Said to each victor: their way down is open. */
	public static void announce(ServerPlayer player, Guardian g) {
		Zone zone = destination(g);
		if (zone != null) {
			Dialogue.notice(player, Component.literal(g.title + "'s way down is open to you: stand in the soul fire in the middle of the lair "
					+ "for " + STAND + " seconds to go down to " + place(zone) + ". The roads and ramps are still there too.")
					.withStyle(ChatFormatting.AQUA));
		}
	}

	/** Twice a second: the columns burn in beaten guardians' lairs, and take whoever has earned them and stands in them. */
	public static void tick(MinecraftServer server) {
		if (++ticks % 10 != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		if (!HellWorldgen.isInferno(level)) {
			return;
		}
		for (Guardian g : Guardian.values()) {
			if (destination(g) == null || GuardianManager.awake(g)) {
				continue;
			}
			BlockPos column = g.lairColumn();
			double cx = column.getX() + 0.5;
			double cz = column.getZ() + 0.5;
			boolean near = false;
			for (ServerPlayer p : level.players()) {
				double dx = p.getX() - cx;
				double dz = p.getZ() - cz;
				double d2 = dx * dx + dz * dz;
				if (d2 > 48 * 48 || p.isSpectator()) {
					continue;
				}
				near = true;
				boolean earned = Hearts.soul(p).guardianSpoils.containsKey(g.id());
				if (!earned || d2 > 1.4 * 1.4) {
					STANDING.remove(p.getUUID());
					continue;
				}
				int stood = STANDING.merge(p.getUUID(), 1, Integer::sum);
				if (stood == 1) {
					p.sendOverlayMessage(Component.literal("Stay in the soul fire to go down to " + place(destination(g)) + "...")
							.withStyle(ChatFormatting.AQUA));
				}
				if (stood >= STAND * 2) {
					STANDING.remove(p.getUUID());
					lift(level, p, g);
				}
			}
			if (near && ticks % 20 == 0) {
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
				for (int dy = 0; dy < 5; dy++) {
					level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, cx, y + dy + 0.3, cz, 3, 0.25, 0.3, 0.25, 0.01);
				}
			}
		}
	}

	private static void lift(ServerLevel level, ServerPlayer player, Guardian g) {
		double[] at = landing(g);
		int bx = (int) Math.floor(at[0]);
		int bz = (int) Math.floor(at[1]);
		level.getChunk(bx >> 4, bz >> 4);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
		level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1, player.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
		player.teleportTo(level, bx + 0.5, y + 1, bz + 0.5, java.util.Set.of(), player.getYRot(), player.getXRot(), true);
		player.resetFallDistance();
		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, true, false, false));
		level.playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.5f, 0.8f);
		Dialogue.notice(player, Component.literal("You came down by " + g.title + "'s way into " + place(destination(g)) + ".")
				.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}

	/** /hellcraft lifts test: every lift lands in its circle, on dry ground. */
	public static String selfTest() {
		StringBuilder out = new StringBuilder();
		boolean ok = true;
		for (Guardian g : Guardian.values()) {
			Zone zone = destination(g);
			if (zone == null) {
				continue;
			}
			double[] at = landing(g);
			Zone lands = InfernoGeometry.zoneAt(at[0], at[1]);
			boolean dry = InfernoGeometry.fluidAt(at[0], at[1]) == InfernoGeometry.Fluid.NONE;
			ok &= lands == zone && dry;
			out.append(String.format(Locale.ROOT, " %s->%s%s", g.id(), lands.id(), dry ? "" : "(wet)"));
		}
		return (ok ? "Lifts test OK:" : "Lifts test FAILED:") + out;
	}

	public static void forget(ServerPlayer player) {
		STANDING.remove(player.getUUID());
	}
}
