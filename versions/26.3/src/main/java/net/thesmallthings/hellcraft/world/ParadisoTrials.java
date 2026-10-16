package net.thesmallthings.hellcraft.world;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.thesmallthings.hellcraft.blood.BloodItems;
import net.thesmallthings.hellcraft.blood.Hearts;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.blood.Relics;
import net.thesmallthings.hellcraft.net.Dialogue;
import net.thesmallthings.hellcraft.net.MapSync;
import net.thesmallthings.hellcraft.util.Feedback;
import net.thesmallthings.hellcraft.world.ParadisoGeometry.Sphere;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Paradiso's trials (Phase 7): a short one in each sphere, passed once per soul (HellState.Soul.spheres); passing it
 * opens that sphere's column of light. Most are lights to gather, which only you can see until you take them (the Moon,
 * the Sun, the Fixed Stars; Mercury's against the clock; Venus's circling; Jupiter's in order; Saturn's one at the top
 * of Jacob's Ladder). Mars's is a bout with the Angelic Guard; the Primum Mobile's is the Seraph. Each gives a blessing
 * (healed, three levels, a Blood Fragment); Saturn's gives Seraph Wings.
 */
public final class ParadisoTrials {
	private ParadisoTrials() {
	}

	/** Mercury's lights must all be taken within this long of the first. */
	public static final int MERCURY_TICKS = 60 * 20;
	private static final String[] EAGLE = {"the tail", "the left wing", "the right wing", "the heart", "the eye"};
	private static final String MARS_TAG = "hellcraft_mars_trial";
	private static final int MARS_WAVES = 2;

	/** Lights each player has taken in each sphere (bit i for light i), until the trial is passed or they log out. */
	private static final Map<UUID, Map<Sphere, Integer>> TAKEN = new HashMap<>();
	private static final Map<UUID, Long> MERCURY_START = new HashMap<>();
	/** Mars's bout: who is in it, which wave, and when it was last seen fought. */
	private static final Set<UUID> MARS_FIGHTERS = new HashSet<>();
	private static int marsWave;
	private static long marsSeen;
	private static int ticks;

	public static void tick(MinecraftServer server) {
		if (++ticks % 2 != 0) {
			return;
		}
		ServerLevel level = Paradiso.level(server);
		if (level == null) {
			return;
		}
		for (ServerPlayer player : new ArrayList<>(level.players())) {
			if (player.isSpectator()) {
				continue;
			}
			HellState.Soul soul = Hearts.soul(player);
			Sphere s = ParadisoGeometry.sphereAtY(player.getY());
			if (Paradiso.passed(soul, s)) {
				continue;
			}
			switch (s) {
				case MOON, SUN, SATURN, FIXED_STARS -> gather(level, player, s, ParadisoGeometry.lights(s), false);
				case JUPITER -> gather(level, player, s, ParadisoGeometry.lights(s), true);
				case VENUS -> {
					List<double[]> lights = new ArrayList<>();
					for (int i = 0; i < 4; i++) {
						lights.add(ParadisoGeometry.venusLight(i, level.getGameTime()));
					}
					gather(level, player, s, lights, false);
				}
				case MERCURY -> mercury(level, player);
				case MARS -> marsStart(level, player);
				default -> {
				}
			}
		}
		mars(level);
	}

	/** The lights of a gathering trial: shown to this player alone until taken; in order, only the next one counts. */
	private static void gather(ServerLevel level, ServerPlayer player, Sphere s, List<double[]> lights, boolean inOrder) {
		int taken = TAKEN.computeIfAbsent(player.getUUID(), k -> new EnumMap<>(Sphere.class)).getOrDefault(s, 0);
		int next = Integer.numberOfTrailingZeros(~taken);
		boolean show = ticks % 10 == 0;
		DustParticleOptions dust = new DustParticleOptions(s.colour, 2.0f);
		for (int i = 0; i < lights.size(); i++) {
			if ((taken >> i & 1) != 0) {
				continue;
			}
			double[] l = lights.get(i);
			boolean counts = !inOrder || i == next;
			if (show) {
				level.sendParticles(player, counts ? ParticleTypes.END_ROD : ParticleTypes.WHITE_ASH, true, true, l[0], l[1], l[2],
						counts ? 4 : 2, 0.15, 0.25, 0.15, 0.01);
				level.sendParticles(player, dust, true, true, l[0], l[1], l[2], counts ? 3 : 1, 0.2, 0.2, 0.2, 0);
			}
			if (!counts || Math.abs(player.getX() - l[0]) > 1.3 || Math.abs(player.getZ() - l[2]) > 1.3 || Math.abs(player.getY() + 0.9 - l[1]) > 1.6) {
				continue;
			}
			taken |= 1 << i;
			TAKEN.get(player.getUUID()).put(s, taken);
			int count = Integer.bitCount(taken);
			Feedback.sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 0.8f + 0.1f * count);
			level.sendParticles(player, ParticleTypes.END_ROD, true, true, l[0], l[1], l[2], 20, 0.3, 0.3, 0.3, 0.08);
			if (count >= lights.size()) {
				pass(player, s);
				return;
			}
			String more = inOrder && count < EAGLE.length ? " Next: " + EAGLE[count] + "." : "";
			player.sendOverlayMessage(Component.literal("Light " + count + " of " + lights.size() + "." + more).withStyle(ChatFormatting.GOLD));
		}
	}

	/** Mercury: the six lights within a minute of the first. */
	private static void mercury(ServerLevel level, ServerPlayer player) {
		Long start = MERCURY_START.get(player.getUUID());
		long now = level.getGameTime();
		if (start != null && now - start > MERCURY_TICKS) {
			MERCURY_START.remove(player.getUUID());
			TAKEN.computeIfAbsent(player.getUUID(), k -> new EnumMap<>(Sphere.class)).remove(Sphere.MERCURY);
			Dialogue.notice(player, Component.literal("Too slow: Mercury's lights came back. Take all six within a minute of the first.")
					.withStyle(ChatFormatting.GOLD));
			return;
		}
		int before = TAKEN.getOrDefault(player.getUUID(), Map.of()).getOrDefault(Sphere.MERCURY, 0);
		gather(level, player, Sphere.MERCURY, ParadisoGeometry.lights(Sphere.MERCURY), false);
		int after = TAKEN.getOrDefault(player.getUUID(), Map.of()).getOrDefault(Sphere.MERCURY, 0);
		if (before == 0 && after != 0) {
			MERCURY_START.put(player.getUUID(), now);
		}
		if (start != null && after != 0 && ticks % 20 == 0) {
			long left = (MERCURY_TICKS - (now - start)) / 20;
			player.sendOverlayMessage(Component.literal(Integer.bitCount(after) + " of 6 lights, " + left + " s left").withStyle(ChatFormatting.AQUA));
		}
	}

	// -------------------------------------------------------------------------------------------- Mars

	/** The heart of Mars's cross, where the bout starts. */
	public static BlockPos marsHeart() {
		double[] c = ParadisoGeometry.centre(Sphere.MARS);
		return BlockPos.containing(c[0], ParadisoGeometry.topY(Sphere.MARS) + 1, c[1]);
	}

	private static void marsStart(ServerLevel level, ServerPlayer player) {
		BlockPos heart = marsHeart();
		boolean atHeart = Math.abs(player.getX() - (heart.getX() + 0.5)) < 1.5 && Math.abs(player.getZ() - (heart.getZ() + 0.5)) < 1.5
				&& Math.abs(player.getY() - heart.getY()) < 2;
		if (marsWave > 0) {
			if (player.blockPosition().distSqr(heart) < 30 * 30) {
				MARS_FIGHTERS.add(player.getUUID());
			}
			return;
		}
		if (!atHeart) {
			return;
		}
		MARS_FIGHTERS.clear();
		MARS_FIGHTERS.add(player.getUUID());
		marsWave = 1;
		marsSeen = level.getGameTime();
		Heaven.angels(level, heart, 2, MARS_TAG);
		for (ServerPlayer p : level.players()) {
			if (p.blockPosition().distSqr(heart) < 40 * 40) {
				Dialogue.notice(p, Component.literal("The warriors of Mars test your arm: beat the Angelic Guard, two waves of them.")
						.withStyle(ChatFormatting.GOLD));
			}
		}
	}

	/** The bout goes on while anyone fights it; each wave cleared brings the next; the last cleared passes everyone in it. */
	private static void mars(ServerLevel level) {
		if (marsWave == 0) {
			return;
		}
		BlockPos heart = marsHeart();
		long now = level.getGameTime();
		boolean anyone = level.players().stream().anyMatch(p -> !p.isSpectator() && p.isAlive() && MARS_FIGHTERS.contains(p.getUUID())
				&& p.blockPosition().distSqr(heart) < 40 * 40);
		if (anyone) {
			marsSeen = now;
		} else if (now - marsSeen > 30 * 20) {
			endMars(level);
			return;
		}
		boolean clear = level.getEntities(net.minecraft.world.entity.EntityTypes.VEX, e -> e.isAlive() && e.entityTags().contains(MARS_TAG)).isEmpty();
		if (!clear) {
			return;
		}
		if (marsWave < MARS_WAVES) {
			marsWave++;
			Heaven.angels(level, heart, 3, MARS_TAG);
			for (UUID id : MARS_FIGHTERS) {
				if (level.getServer().getPlayerList().getPlayer(id) instanceof ServerPlayer p) {
					p.sendOverlayMessage(Component.literal("Wave " + marsWave + " of " + MARS_WAVES).withStyle(ChatFormatting.GOLD));
				}
			}
			return;
		}
		for (UUID id : new ArrayList<>(MARS_FIGHTERS)) {
			if (level.getServer().getPlayerList().getPlayer(id) instanceof ServerPlayer p && p.level() == level && p.isAlive()
					&& p.blockPosition().distSqr(heart) < 40 * 40) {
				pass(p, Sphere.MARS);
			}
		}
		endMars(level);
	}

	private static void endMars(ServerLevel level) {
		for (Entity e : level.getEntities(net.minecraft.world.entity.EntityTypes.VEX, e -> e.entityTags().contains(MARS_TAG))) {
			e.discard();
		}
		marsWave = 0;
		MARS_FIGHTERS.clear();
	}

	/** For /hellcraft paradiso test and the client test: is Mars's bout running, and how many of its angels are alive? */
	public static String marsStatus(ServerLevel level) {
		int alive = level.getEntities(net.minecraft.world.entity.EntityTypes.VEX, e -> e.isAlive() && e.entityTags().contains(MARS_TAG)).size();
		return marsWave == 0 ? "Mars: no bout" : "Mars: wave " + marsWave + ", " + alive + " angels, " + MARS_FIGHTERS.size() + " fighting";
	}

	// -------------------------------------------------------------------------------------------- passing

	/** Passes a sphere's trial for a player: its column opens, and a blessing (Saturn's: Seraph Wings). */
	public static void pass(ServerPlayer player, Sphere s) {
		HellState.Soul soul = Hearts.soul(player);
		if (Paradiso.passed(soul, s)) {
			return;
		}
		soul.spheres |= 1 << s.ordinal();
		HellState.get(player.level().getServer()).setDirty();
		Map<Sphere, Integer> taken = TAKEN.get(player.getUUID());
		if (taken != null) {
			taken.remove(s);
		}
		MERCURY_START.remove(player.getUUID());
		MapSync.send(player);
		player.heal(player.getMaxHealth());
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
		player.giveExperienceLevels(3);
		BloodItems.give(player, BloodItems.fragment(1));
		Feedback.sound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0f, 1.3f);
		player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
		player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("Trial passed").withStyle(ChatFormatting.GOLD)));
		player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(s.title).withStyle(ChatFormatting.WHITE)));
		Sphere next = s.last() ? null : ParadisoGeometry.SPHERES[s.ordinal() + 1];
		Dialogue.notice(player, Component.literal("You passed " + s.title + "'s trial: healed, 3 levels and a Blood Fragment."
				+ (next == null ? "" : " Its column of light on the departure isle now lifts you to " + next.title + "."))
				.withStyle(ChatFormatting.GOLD));
		if (s == Sphere.SATURN) {
			Relics.give(player, Relics.Relic.SERAPH_WINGS);
			Dialogue.notice(player, Component.literal("At the top of the ladder you were given Seraph Wings (an elytra that never "
					+ "breaks).").withStyle(ChatFormatting.AQUA));
		}
	}

	public static void forget(ServerPlayer player) {
		TAKEN.remove(player.getUUID());
		MERCURY_START.remove(player.getUUID());
	}
}
