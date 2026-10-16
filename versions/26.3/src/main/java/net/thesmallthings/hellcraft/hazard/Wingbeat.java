package net.thesmallthings.hellcraft.hazard;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.hazard.lucifer.LuciferManager;
import net.thesmallthings.hellcraft.net.WingbeatPayload;
import net.thesmallthings.hellcraft.world.Circle;
import net.thesmallthings.hellcraft.world.HellWorldgen;
import net.thesmallthings.hellcraft.world.InfernoGeometry;

/**
 * Lucifer's wingbeat (L1): every few minutes, all over Hell at once, the ground shudders and a cold wind blows from the
 * centre: the screen shakes, a great slow flap is heard, and frost creeps in at the screen's edges, stronger the deeper
 * you are (client/layer/Wingbeat). In Cocytus it chills you too. The first time, a plain tip says what it was. During
 * his fight his own wingbeats come from the fight instead.
 */
public final class Wingbeat {
	private Wingbeat() {
	}

	/** Hint bit: the first wingbeat someone feels. */
	private static final int HINT = 41;
	private static long next = -1;

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(WingbeatPayload.TYPE, WingbeatPayload.CODEC);
	}

	public static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (!HellWorldgen.isInferno(level) || HellConfig.get().wingbeatMinutes <= 0) {
			return;
		}
		long now = level.getGameTime();
		if (next < 0) {
			next = now + schedule(level);
			return;
		}
		if (now < next) {
			return;
		}
		next = now + schedule(level);
		if (LuciferManager.fighting()) {
			return;
		}
		int felt = 0;
		for (ServerPlayer p : level.players()) {
			beat(p, strengthAt(p.getX(), p.getZ()));
			felt++;
		}
		HellcraftMod.LOGGER.info("Lucifer's wings beat ({} felt it)", felt);
	}

	/** Ticks to the next beat: the configured minutes, give or take a third. */
	private static long schedule(ServerLevel level) {
		double minutes = HellConfig.get().wingbeatMinutes;
		return Math.max(200, Math.round(minutes * 1200 * (0.67 + 0.66 * level.getRandom().nextDouble())));
	}

	/** How hard the beat is felt here: faint at the rim, overwhelming at the bottom. */
	public static float strengthAt(double x, double z) {
		double depth = 1.0 - Math.min(1.0, Math.sqrt(x * x + z * z) / InfernoGeometry.outerRadius(net.thesmallthings.hellcraft.world.Zone.DARK_WOOD));
		return (float) (0.15 + 0.85 * Math.pow(depth, 2.5));
	}

	/** One beat felt by one player (also used by the Emperor's own wingbeat, at full strength). */
	public static void beat(ServerPlayer player, float strength) {
		if (ServerPlayNetworking.canSend(player, WingbeatPayload.TYPE)) {
			ServerPlayNetworking.send(player, new WingbeatPayload(strength));
		}
		if (InfernoGeometry.zoneAt(player.getX(), player.getZ()).circle() == Circle.TREACHERY && player.canFreeze()) {
			// the wind that froze Cocytus: a sudden chill
			player.setTicksFrozen(Math.min(player.getTicksRequiredToFreeze() + 40, player.getTicksFrozen() + Math.round(60 * strength)));
		}
		CircleHazards.hintOnce(player, HINT, "That was Lucifer's wings beating at the bottom of Hell. The deeper you go, the stronger you'll feel "
				+ "them.");
	}

	/** Only the shake and the boom (a slammed hand, nearby): no frost, no tip. */
	public static void shake(ServerPlayer player, float strength) {
		if (ServerPlayNetworking.canSend(player, WingbeatPayload.TYPE)) {
			ServerPlayNetworking.send(player, new WingbeatPayload(-strength));
		}
	}

	/** /hellcraft wingbeat: one beat now, for everyone. */
	public static String now(MinecraftServer server) {
		next = 0;
		tick(server);
		return "Lucifer's wings beat.";
	}
}
