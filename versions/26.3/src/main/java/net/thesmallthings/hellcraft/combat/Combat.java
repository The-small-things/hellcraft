package net.thesmallthings.hellcraft.combat;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.thesmallthings.hellcraft.blood.BloodAltar;
import net.thesmallthings.hellcraft.blood.HellWeapons;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.entity.boss.gen.PlayerMovesAnim;
import net.thesmallthings.hellcraft.net.ArtPayload;
import net.thesmallthings.hellcraft.net.CombatPayload;
import net.thesmallthings.hellcraft.net.FeelPayload;
import net.thesmallthings.hellcraft.net.MovePayload;
import net.thesmallthings.hellcraft.net.StaminaPayload;

/**
 * Hellcraft's combat (docs/COMBAT.md): stamina ({@link Stamina}), the dodge roll ({@link Dodge}), guarding and parrying
 * with weapons ({@link Guard}). The server decides what lands and what it costs; clients play their own rolls and swings
 * at once and draw everything (client/combat). On unless the combatOverhaul setting is off.
 */
public final class Combat {
	private Combat() {
	}

	/**
	 * What this game's server said (client side: there is no server config to read). A single-player game runs both sides
	 * in one JVM, where the server's own answer is the same thing.
	 */
	private static volatile CombatPayload client = new CombatPayload(false, 100, 0, 16, 3.4f, 0, 8);
	private static CombatPayload sent;

	/** Server side: is Hellcraft's combat on? */
	public static boolean on() {
		return HellConfig.get().combatOverhaul;
	}

	/** Either side: the rules in force (the client's copy is what the server sent). */
	public static CombatPayload rules(boolean clientSide) {
		if (clientSide) {
			return client;
		}
		HellConfig c = HellConfig.get();
		return new CombatPayload(c.combatOverhaul, c.staminaMax, c.dodgeCost, c.dodgeCooldownTicks, (float) c.dodgeDistance, c.heavyCost,
				c.dodgeInvulnerableTicks);
	}

	/** Client side: the server's rules arrived (or the game was left: off). */
	public static void clientRules(CombatPayload rules) {
		client = rules == null ? new CombatPayload(false, 100, 0, 16, 3.4f, 0, 8) : rules;
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(CombatPayload.TYPE, CombatPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(StaminaPayload.TYPE, StaminaPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MovePayload.TYPE, MovePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(FeelPayload.TYPE, FeelPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ArtPayload.TYPE, ArtPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ArtPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (player.isAlive() && !player.isSpectator() && !Dodge.rolling(player)) {
				HellWeapons.art(player);
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			if (ServerPlayNetworking.canSend(player, CombatPayload.TYPE)) {
				ServerPlayNetworking.send(player, rules(false));
			}
			Stamina.join(player);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Stamina.forget(handler.getPlayer());
			Dodge.forget(handler.getPlayer());
			Guard.forget(handler.getPlayer());
			Swing.forget(handler.getPlayer());
		});
		ServerTickEvents.END_SERVER_TICK.register(Combat::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Combat::struck);
		Stamina.register();
		Dodge.register();
		Guard.register();
		Swing.register();
	}

	private static void tick(MinecraftServer server) {
		// the rules changed (/hellcraft config): tell everyone
		CombatPayload now = rules(false);
		if (!now.equals(sent)) {
			sent = now;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (ServerPlayNetworking.canSend(player, CombatPayload.TYPE)) {
					ServerPlayNetworking.send(player, now);
				}
			}
		}
		Stamina.tick(server);
		Guard.tick(server);
		Swing.tick(server);
	}

	/**
	 * A player's own blow landed: they feel it (a held frame and a kick of the view, client combat/HitFeel) and the
	 * target shows it (blood from flesh, chips from bone, sparks from stone and metal).
	 */
	private static void struck(LivingEntity target, DamageSource source, float base, float taken, boolean blocked) {
		if (!on() || taken <= 0 || !(source.getDirectEntity() instanceof ServerPlayer attacker) || target == attacker
				|| !(target.level() instanceof ServerLevel level)) {
			return;
		}
		float strength = Math.max(0.5f, Math.min(2.0f, taken / 7.0f));
		feel(attacker, taken >= 12 || Swing.landingHeavy() ? FeelPayload.Kind.HEAVY_HIT : FeelPayload.Kind.HIT, strength);
		double x = target.getX();
		double y = target.getY() + target.getBbHeight() * 0.6;
		double z = target.getZ();
		int count = Math.min(14, 3 + Math.round(taken));
		if (target.is(EntityTypeTags.SKELETONS)) {
			level.sendParticles(ParticleTypes.WHITE_ASH, x, y, z, count * 2, 0.25, 0.3, 0.25, 0.02);
			level.sendParticles(ParticleTypes.CRIT, x, y, z, count / 2, 0.2, 0.25, 0.2, 0.2);
		} else if (target instanceof AbstractGolem) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, count, 0.25, 0.3, 0.25, 0.3);
		} else {
			level.sendParticles(BloodAltar.BLOOD, x, y, z, count, 0.22, 0.28, 0.22, 0.0);
		}
	}

	/** Shows a move on a player's body to everyone watching, and to the player too unless they began it themselves. */
	public static void show(ServerPlayer player, PlayerMovesAnim move, float x, float z, float speed, boolean toSelf) {
		MovePayload payload = new MovePayload(player.getId(), move.ordinal(), x, z, speed);
		for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
			if (watcher != player && ServerPlayNetworking.canSend(watcher, MovePayload.TYPE)) {
				ServerPlayNetworking.send(watcher, payload);
			}
		}
		if (toSelf && ServerPlayNetworking.canSend(player, MovePayload.TYPE)) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	/** The pause and kick a player feels (FeelPayload). */
	public static void feel(ServerPlayer player, FeelPayload.Kind kind, float strength) {
		if (ServerPlayNetworking.canSend(player, FeelPayload.TYPE)) {
			ServerPlayNetworking.send(player, new FeelPayload(kind, strength));
		}
	}

	/** Checks the pieces that need no world: the self-test behind /hellcraft combat test. */
	public static String selfTest() {
		StringBuilder problems = new StringBuilder();
		String s = Stamina.selfTest();
		if (!s.isEmpty()) {
			problems.append(s).append("; ");
		}
		String g = Guard.selfTest();
		if (!g.isEmpty()) {
			problems.append(g).append("; ");
		}
		String w = Swing.selfTest();
		if (!w.isEmpty()) {
			problems.append(w).append("; ");
		}
		String r = RollShape.selfTest();
		if (!r.isEmpty()) {
			problems.append(r).append("; ");
		}
		String d = Dodge.selfTest();
		if (!d.isEmpty()) {
			problems.append(d).append("; ");
		}
		for (PlayerMovesAnim move : PlayerMovesAnim.values()) {
			if (move.ticks <= 0) {
				problems.append(move).append(" has no length; ");
			}
		}
		return problems.isEmpty() ? "Combat self-test OK: " + PlayerMovesAnim.values().length + " moves" : "Combat self-test FAILED: " + problems;
	}
}
