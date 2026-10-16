package net.thesmallthings.hellcraft.world;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.thesmallthings.hellcraft.blood.Hearts;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.hazard.CircleHazards;
import net.thesmallthings.hellcraft.net.Dialogue;
import net.thesmallthings.hellcraft.util.Feedback;
import net.thesmallthings.hellcraft.util.Journey;
import net.thesmallthings.hellcraft.world.ParadisoGeometry.Sphere;
import net.thesmallthings.hellcraft.world.feature.ParadisoIslandFeature;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Paradiso in a running world (Phase 7): its own dimension ({@link #LEVEL}), nine spheres stacked one above the other
 * (the shape is ParadisoGeometry, the islands ParadisoIslandFeature, the trials ParadisoTrials).
 * <ul>
 *     <li>Stand in a sphere's column of light to rise to the next sphere, once you have passed this sphere's trial; hold
 *     sneak in it to sink back down.</li>
 *     <li>Nobody is hurt by falling here, and an angel catches anyone who falls off a sphere and sets them down on its
 *     arrival isle.</li>
 *     <li>The spheres open one at a time: whoever gets above a sphere they haven't earned (an ender pearl, rockets) is
 *     set back down.</li>
 *     <li>The gate on the Moon's arrival isle goes back to Purgatory's summit; the one in the Empyrean to the Gate of Hell.</li>
 * </ul>
 */
public final class Paradiso {
	private Paradiso() {
	}

	public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, net.thesmallthings.hellcraft.HellcraftMod.id("paradiso"));
	/** Hint bits 51 to 60: each sphere's trial, the first time you reach it. */
	private static final int HINT = 51;

	/** The sphere each player last stood in (where an angel sets them down if they fall). */
	private static final Map<UUID, Sphere> STANDING = new HashMap<>();
	private static final Map<UUID, Sphere> TITLED = new HashMap<>();
	private static final Map<UUID, Long> TOLD = new HashMap<>();
	private static int ticks;

	@Nullable
	public static ServerLevel level(MinecraftServer server) {
		return server.getLevel(LEVEL);
	}

	public static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(entity instanceof ServerPlayer && entity.level().dimension() == LEVEL && source.is(DamageTypeTags.IS_FALL)));
	}

	/** Has this soul passed a sphere's trial (the Seraph's, for the Primum Mobile)? */
	public static boolean passed(HellState.Soul soul, Sphere s) {
		return (soul.spheres >> s.ordinal() & 1) != 0;
	}

	/** The highest sphere a soul may be in: the first whose trial it hasn't passed (or the Empyrean). */
	public static Sphere reach(HellState.Soul soul) {
		for (Sphere s : ParadisoGeometry.SPHERES) {
			if (!s.last() && !passed(soul, s)) {
				return s;
			}
		}
		return Sphere.EMPYREAN;
	}

	/** Rises into Paradiso: from the Ascent on Purgatory's summit to the Moon. */
	public static void enter(ServerPlayer player) {
		ServerLevel level = level(player.level().getServer());
		if (level == null) {
			return;
		}
		setDown(player, level, Sphere.MOON);
		level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.4f);
		Dialogue.notice(player, Component.literal("You rose into Paradiso. Nine spheres are stacked above you: pass each one's trial, then "
				+ "stand in its column of light to rise to the next. The Celestial Rose is at the top.").withStyle(ChatFormatting.GOLD));
		Dialogue.notice(player, Component.literal("Falling can't hurt you here: an angel catches you. Hold sneak in a column to sink "
				+ "back down. The gate under this canopy goes back to Purgatory.").withStyle(ChatFormatting.WHITE));
	}

	/** Puts a player on a sphere's arrival isle, beside the column, facing the sphere's great island. */
	public static void setDown(ServerPlayer player, ServerLevel level, Sphere s) {
		double[] at = ParadisoGeometry.landing(s);
		double[] c = ParadisoGeometry.centre(s);
		float yaw = (float) Math.toDegrees(Math.atan2(c[1] - at[2], c[0] - at[0])) - 90f;
		level.getChunk((int) Math.floor(at[0]) >> 4, (int) Math.floor(at[2]) >> 4);
		player.teleportTo(level, at[0], at[1], at[2], java.util.Set.of(), yaw, 0, true);
		player.resetFallDistance();
		STANDING.put(player.getUUID(), s);
	}

	public static void tick(MinecraftServer server) {
		ticks++;
		ServerLevel level = level(server);
		if (level == null) {
			return;
		}
		for (ServerPlayer player : new ArrayList<>(level.players())) {
			if (player.isSpectator()) {
				continue;
			}
			tick(level, player);
		}
	}

	private static void tick(ServerLevel level, ServerPlayer player) {
		HellState.Soul soul = Hearts.soul(player);
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		Sphere band = ParadisoGeometry.sphereAtY(y);
		Sphere column = ParadisoGeometry.columnAt(x, y, z);
		if (column != null) {
			ride(level, player, soul, column);
			return;
		}
		if (player.onGround()) {
			STANDING.put(player.getUUID(), band);
		}
		Sphere standing = STANDING.getOrDefault(player.getUUID(), band);
		if (y < ParadisoGeometry.topY(standing) - 28 || y < 8) {
			setDown(player, level, y < 8 ? Sphere.MOON : standing);
			Dialogue.notice(player, Component.literal("An angel caught you and set you down on the arrival isle.")
					.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			return;
		}
		Sphere reach = reach(soul);
		if (!player.isCreative() && band.ordinal() > reach.ordinal()) {
			setDown(player, level, reach);
			Dialogue.notice(player, Component.literal("The spheres open one at a time: pass " + reach.title + "'s trial first ("
					+ reach.trial + ")").withStyle(ChatFormatting.GOLD));
			return;
		}
		if (ticks % 10 == 0) {
			gates(level, player);
		}
		if (ticks % 20 == 0 && TITLED.put(player.getUUID(), band) != band) {
			player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 25));
			player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(band.title).withStyle(ChatFormatting.GOLD)));
			player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(band.tagline).withStyle(ChatFormatting.WHITE)));
			CircleHazards.hintOnce(player, HINT + band.ordinal(), band.title + ": " + band.trial
					+ (band.last() ? "" : " Then its column of light lifts you to the next sphere."));
			if (band.last()) {
				Journey.award(player, "journey/empyrean");
			}
		}
	}

	/** In a column of light: rise (if this sphere's trial is passed), or sink while sneaking. */
	private static void ride(ServerLevel level, ServerPlayer player, HellState.Soul soul, Sphere column) {
		Sphere next = ParadisoGeometry.SPHERES[column.ordinal() + 1];
		if (player.isShiftKeyDown()) {
			player.removeEffect(MobEffects.LEVITATION);
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, true, false, false));
			return;
		}
		if (!passed(soul, column) && !player.isCreative()) {
			long now = level.getGameTime();
			if (now >= TOLD.getOrDefault(player.getUUID(), 0L)) {
				TOLD.put(player.getUUID(), now + 80);
				player.sendOverlayMessage(Component.literal("This column opens when you pass " + column.title + "'s trial: " + column.trial)
						.withStyle(ChatFormatting.GOLD));
			}
			return;
		}
		if (player.getY() >= ParadisoGeometry.topY(next) + 1.0) {
			player.removeEffect(MobEffects.LEVITATION);
			setDown(player, level, next);
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0, true, false, false));
			Feedback.sound(player, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 1.2f);
			return;
		}
		player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 6, 19, true, false, false));
		player.resetFallDistance();
		if (ticks % 4 == 0) {
			level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY(), player.getZ(), 2, 0.4, 0.2, 0.4, 0.02);
		}
	}

	/** The way back down to Purgatory (the Moon's gate) and home to the Gate of Hell (the Empyrean's). */
	private static void gates(ServerLevel level, ServerPlayer player) {
		double[] moon = ParadisoGeometry.arrival(Sphere.MOON);
		if (near(player, moon[0], ParadisoGeometry.topY(Sphere.MOON) + 1, moon[1])) {
			Purgatory.toSummit(player);
			return;
		}
		int[] home = ParadisoIslandFeature.homeGate();
		if (near(player, home[0] + 0.5, home[1], home[2] + 0.5)) {
			Purgatory.home(player);
		}
	}

	private static boolean near(ServerPlayer player, double x, double y, double z) {
		return Math.abs(player.getX() - x) < 0.9 && Math.abs(player.getZ() - z) < 0.9 && Math.abs(player.getY() - y) < 1.2;
	}

	/** Beatrice's bell, in the middle of the Celestial Rose. */
	public static BlockPos bell() {
		return ParadisoIslandFeature.bell();
	}

	/** For /hellcraft paradiso status. */
	public static String status(MinecraftServer server) {
		ServerLevel level = level(server);
		if (level == null) {
			return "Paradiso: no dimension (not an Inferno world)";
		}
		double[] c = ParadisoGeometry.centre(Sphere.MOON);
		BlockPos moon = BlockPos.containing(c[0], ParadisoGeometry.topY(Sphere.MOON), c[1]);
		level.getChunk(moon.getX() >> 4, moon.getZ() >> 4);
		String biome = level.getBiome(moon).getRegisteredName();
		boolean ground = !level.getBlockState(moon).isAir();
		return "Paradiso: " + ParadisoGeometry.SPHERES.length + " spheres, the Moon's island " + (ground ? "stands" : "MISSING")
				+ " at " + moon.toShortString() + " in " + biome;
	}

	public static void forget(ServerPlayer player) {
		STANDING.remove(player.getUUID());
		TITLED.remove(player.getUUID());
		TOLD.remove(player.getUUID());
	}
}
