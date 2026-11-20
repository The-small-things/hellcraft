package net.thesmallthings.hellcraft.hazard;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.Judgement.Grade;
import net.thesmallthings.hellcraft.config.HellConfig;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * What a high style rank is worth while it lasts: from S up, every rank adds a little walking speed, a little attack speed
 * (Hellcraft's swings play faster: combat/Moveset.pace reads the attribute) and a little reach (vanilla's interaction range,
 * which combat/Swing reaches by). S is one step, SS two, SSS three, HELLISH four. The amounts per step are in the config
 * (`styleSpeedPerRank`, `styleAttackSpeedPerRank`, `styleReachPerRank`; `styleRankBonuses` turns them off), and they are
 * transient modifiers, so they never outlive the fight: StyleMeter sets them every tick and takes them off when the fight
 * ends, and a sweep takes them off anyone whose meter has stopped ticking (who left the arena, or whose fight was cut short).
 */
public final class RankBonus {
	private RankBonus() {
	}

	private static final Identifier SPEED = HellcraftMod.id("style_speed");
	private static final Identifier ATTACK = HellcraftMod.id("style_attack_speed");
	private static final Identifier REACH = HellcraftMod.id("style_reach");
	/** Ticks without a meter's word before the sweep takes a player's bonus off. */
	private static final int STALE = 40;

	/** Players with a bonus on: the steps they have, and the server tick it was last confirmed. */
	private static final Map<UUID, int[]> ON = new HashMap<>();

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(RankBonus::sweep);
	}

	/** Sets the bonus for this rank on the fighter (only touching their attributes when the steps change). */
	public static void apply(ServerPlayer player, Grade grade) {
		HellConfig config = HellConfig.get();
		int steps = config.styleRankBonuses ? grade.stepsFromS() : 0;
		MinecraftServer server = player.level().getServer();
		int now = server == null ? 0 : server.getTickCount();
		int[] held = ON.get(player.getUUID());
		if (steps == 0) {
			if (held != null) {
				clear(player);
			}
			return;
		}
		if (held != null && held[0] == steps) {
			held[1] = now;
			return;
		}
		set(player, Attributes.MOVEMENT_SPEED, SPEED, config.styleSpeedPerRank * steps, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		set(player, Attributes.ATTACK_SPEED, ATTACK, config.styleAttackSpeedPerRank * steps, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		set(player, Attributes.ENTITY_INTERACTION_RANGE, REACH, config.styleReachPerRank * steps, AttributeModifier.Operation.ADD_VALUE);
		ON.put(player.getUUID(), new int[] {steps, now});
	}

	/** Takes the bonus off. */
	public static void clear(ServerPlayer player) {
		ON.remove(player.getUUID());
		remove(player, Attributes.MOVEMENT_SPEED, SPEED);
		remove(player, Attributes.ATTACK_SPEED, ATTACK);
		remove(player, Attributes.ENTITY_INTERACTION_RANGE, REACH);
	}

	/** The steps a player has now (0: none). */
	public static int steps(ServerPlayer player) {
		int[] held = ON.get(player.getUUID());
		return held == null ? 0 : held[0];
	}

	/** What a rank gives, for the style panel: "+6% speed  +8% attack  +0.3 reach", or "" below S. */
	public static String describe(Grade grade) {
		HellConfig config = HellConfig.get();
		int steps = config.styleRankBonuses ? grade.stepsFromS() : 0;
		if (steps == 0) {
			return "";
		}
		return String.format(Locale.ROOT, "+%d%% speed  +%d%% attack  +%s reach",
				Math.round(config.styleSpeedPerRank * steps * 100), Math.round(config.styleAttackSpeedPerRank * steps * 100),
				trim(config.styleReachPerRank * steps));
	}

	private static String trim(double v) {
		String s = String.format(Locale.ROOT, "%.2f", v);
		while (s.contains(".") && (s.endsWith("0") || s.endsWith("."))) {
			s = s.substring(0, s.length() - 1);
		}
		return s;
	}

	private static void sweep(MinecraftServer server) {
		if (ON.isEmpty()) {
			return;
		}
		int now = server.getTickCount();
		for (Iterator<Map.Entry<UUID, int[]>> it = ON.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, int[]> e = it.next();
			if (now - e.getValue()[1] <= STALE) {
				continue;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			it.remove();
			if (player != null) {
				remove(player, Attributes.MOVEMENT_SPEED, SPEED);
				remove(player, Attributes.ATTACK_SPEED, ATTACK);
				remove(player, Attributes.ENTITY_INTERACTION_RANGE, REACH);
			}
		}
	}

	private static void set(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation operation) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		instance.removeModifier(id);
		if (amount != 0) {
			instance.addTransientModifier(new AttributeModifier(id, amount, operation));
		}
	}

	private static void remove(ServerPlayer player, Holder<Attribute> attribute, Identifier id) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null) {
			instance.removeModifier(id);
		}
	}

	/** Headless self-test for the smoke test: the steps per rank, and the panel's line at HELLISH with the defaults. */
	public static String selfTest() {
		StringBuilder steps = new StringBuilder();
		for (Grade g : Grade.values()) {
			steps.append(g.stepsFromS());
		}
		if (!steps.toString().equals("00001234")) {
			return "Rank bonus test FAILED: steps " + steps;
		}
		return "Rank bonus test: steps " + steps + ", HELLISH gives " + describe(Grade.HELLISH);
	}
}
