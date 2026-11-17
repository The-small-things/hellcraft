package net.thesmallthings.hellcraft.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.thesmallthings.hellcraft.client.DashKey;
import net.thesmallthings.hellcraft.client.combat.StaminaHud;
import net.thesmallthings.hellcraft.client.combat.anim.PlayerAnimator;
import net.thesmallthings.hellcraft.combat.Guard;
import net.thesmallthings.hellcraft.combat.Stamina;
import net.thesmallthings.hellcraft.entity.boss.gen.PlayerMovesAnim;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Hellcraft's combat (docs/COMBAT.md), in single player.
 *
 * <p>Films: every move of the player's combat rig, stopped at chosen moments, on three mannequins seen from the front,
 * three-quarters and the side at once (combat_&lt;move&gt;_&lt;tick&gt;), and in first person (combat_&lt;move&gt;_fp_&lt;tick&gt;). By
 * default a few key frames per move; HELLCRAFT_COMBAT_FILM=&lt;move,move|all&gt; films the named moves tick by tick, to tune
 * an animation (tools/art/player_moves.py).
 *
 * <p>Checks: the dodge roll carries the player its distance and costs stamina; a sword's guard goes up on right-click and
 * turns aside its share of a blow; a guard raised just before the blow parries it whole and staggers the attacker; and a
 * blow taken with no stamina left breaks the guard.
 */
public class CombatTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("weather clear");
			world.getServer().runCommand("difficulty easy");
			world.getServer().runCommand("gamemode survival Player0");
			// a stone stage, clear above, with the player at one end looking down it (south)
			world.getServer().runCommand("execute at Player0 run fill ~-9 ~-1 ~-4 ~9 ~-1 ~12 minecraft:smooth_stone");
			world.getServer().runCommand("execute at Player0 run fill ~-9 ~ ~-4 ~9 ~7 ~12 minecraft:air");
			world.getServer().runCommand("execute at Player0 run kill @e[type=!minecraft:player,distance=..40]");
			world.getServer().runCommand("execute as Player0 at @s align xyz run tp @s ~0.5 ~ ~0.5 0 8");
			world.getServer().runCommand("item replace entity Player0 weapon.mainhand with minecraft:iron_sword");
			context.waitTicks(10);
			String self = world.getServer().computeOnServer(server -> net.thesmallthings.hellcraft.combat.Combat.selfTest());
			if (!self.startsWith("Combat self-test OK")) {
				throw new AssertionError(self);
			}
			film(context, world);
			if (!System.getenv().getOrDefault("HELLCRAFT_COMBAT_FILM", "").isEmpty()) {
				// tuning an animation: the films are all that's wanted
				return;
			}
			roll(context, world);
			guard(context, world);
			swings(context, world);
			hellWeapons(context, world);
			bloodArt(context, world);
			overhead(context, world);
		}
	}

	// ------------------------------------------------------------------------------------------------- films

	private static void film(ClientGameTestContext context, TestSingleplayerContext world) {
		// three mannequins with swords: facing the camera, three-quarters, and side on (their sword arm towards us)
		float[][] stands = {{-2.6f, 180f}, {0f, 140f}, {2.6f, 90f}};
		for (float[] stand : stands) {
			world.getServer().runCommand(String.format(Locale.ROOT,
					"execute at Player0 run summon minecraft:mannequin ~%.1f ~ ~5 {Rotation:[%.0ff,0f],NoAI:1b,Silent:1b,Invulnerable:1b}", stand[0], stand[1]));
		}
		world.getConnection().waitForClientboundEntityUpdates(EntityTypes.MANNEQUIN);
		world.getServer().runCommand("item replace entity @e[type=minecraft:mannequin] weapon.mainhand with minecraft:iron_sword");
		context.waitTicks(10);
		String wanted = System.getenv().getOrDefault("HELLCRAFT_COMBAT_FILM", "");
		for (PlayerMovesAnim move : PlayerMovesAnim.values()) {
			String name = move.name().toLowerCase(Locale.ROOT);
			boolean every = wanted.equals("all") || List.of(wanted.split(",")).contains(name);
			List<Float> ticks = new ArrayList<>();
			if (every) {
				for (float t = 0; t <= move.ticks; t += move.looping ? 10 : 1) {
					ticks.add(t);
				}
			} else if (!wanted.isEmpty()) {
				continue;
			} else if (move.looping) {
				ticks.add(0f);
			} else if (move.ordinal() >= PlayerMovesAnim.SWORD_1.ordinal()) {
				// a swing: wound up, landing, followed through
				ticks.add((float) move.at("swing"));
				ticks.add((float) move.at("hit"));
				ticks.add(move.at("hit") + 2f);
			} else {
				ticks.add(move.ticks * 0.25f);
				ticks.add(move.ticks * 0.5f);
				ticks.add(move.ticks * 0.75f);
			}
			for (float tick : ticks) {
				context.runOnClient(client -> {
					client.options.setCameraType(CameraType.FIRST_PERSON);
					for (Entity entity : client.level.entitiesForRendering()) {
						if (entity instanceof Avatar && !(entity instanceof Player)) {
							PlayerAnimator.of(entity).hold(move, tick, entity.getYRot());
						}
					}
					PlayerAnimator.of(client.player).hold(move, tick, client.player.getYRot());
				});
				context.waitTicks(2);
				// the mannequins fill the middle of the view; the player's own hand shows the first-person side of the move
				context.takeScreenshot(String.format(Locale.ROOT, "combat_%s_%02d", name, Math.round(tick)));
			}
		}
		context.runOnClient(client -> PlayerAnimator.clear());
		world.getServer().runCommand("kill @e[type=minecraft:mannequin]");
		context.waitTicks(5);
	}

	// ------------------------------------------------------------------------------------------------- checks

	private static double[] place(TestSingleplayerContext world) {
		return world.getServer().computeOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayerByName("Player0");
			return new double[]{player.getX(), player.getZ(), Stamina.get(player), player.getHealth()};
		});
	}

	private static void roll(ClientGameTestContext context, TestSingleplayerContext world) {
		double[] before = place(world);
		// with no way held the roll is a hop back (north, away from where the player looks)
		context.getInput().pressKey(DashKey.DASH);
		context.waitTicks(3);
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.waitTicks(2);
		context.takeScreenshot("combat_roll_live");
		context.waitTicks(16);
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		double[] after = place(world);
		double moved = before[1] - after[1];
		if (moved < 2.6 || moved > 4.2) {
			throw new AssertionError("a roll should carry the player about 3.4 blocks; they moved " + moved);
		}
		if (after[2] >= before[2]) {
			throw new AssertionError("a roll should cost stamina: " + before[2] + " before, " + after[2] + " after");
		}
		float shown = context.computeOnClient(client -> StaminaHud.stamina());
		if (Math.abs(shown - after[2]) > 12) {
			throw new AssertionError("the client's stamina (" + shown + ") isn't the server's (" + after[2] + ")");
		}
		context.takeScreenshot("combat_stamina_bar");
		// back to the start, stamina back
		world.getServer().runCommand("execute as Player0 at @s run tp @s ~ ~ ~" + String.format(Locale.ROOT, "%.2f", moved) + " 0 8");
		context.waitTicks(80);
	}

	private static void guard(ClientGameTestContext context, TestSingleplayerContext world) {
		// an attacker in front (it never moves: the blows are dealt by hand, so the timing is exact)
		context.getInput().holdKey(options -> options.keyUse);
		context.waitTicks(12);
		boolean up = world.getServer().computeOnServer(server -> Guard.guarding(server.getPlayerList().getPlayerByName("Player0")));
		if (!up) {
			throw new AssertionError("holding right-click with a sword should raise its guard");
		}
		context.takeScreenshot("combat_guard_first_person");
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		context.waitTicks(3);
		context.takeScreenshot("combat_guard_third_person");
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		world.getServer().runCommand("execute at Player0 run summon minecraft:husk ~ ~ ~2 {NoAI:1b,Silent:1b,PersistenceRequired:1b,Rotation:[180f,0f]}");
		context.waitTicks(10);
		// a blow on a guard long held: most of it turned, at a cost in stamina
		double[] before = place(world);
		strike(world, 6.0f);
		context.waitTicks(5);
		double[] after = place(world);
		double lost = before[3] - after[3];
		if (lost <= 0.5 || lost >= 4.5) {
			throw new AssertionError("a sword's guard should turn most of a 6-damage blow aside; the player lost " + lost);
		}
		if (after[2] >= before[2]) {
			throw new AssertionError("a guarded blow should cost stamina");
		}
		// lower it, wait, raise it and take the blow at once: a parry
		context.getInput().releaseKey(options -> options.keyUse);
		world.getServer().runCommand("effect give Player0 minecraft:instant_health 1 4 true");
		context.waitTicks(40);
		context.getInput().holdKey(options -> options.keyUse);
		context.waitTicks(3);
		before = place(world);
		strike(world, 6.0f);
		context.waitTicks(3);
		context.takeScreenshot("combat_parry");
		after = place(world);
		if (after[3] < before[3]) {
			throw new AssertionError("a parry should let nothing through; the player lost " + (before[3] - after[3]));
		}
		boolean reeling = world.getServer().computeOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayerByName("Player0");
			return player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(8), e -> e.getType() == EntityTypes.HUSK)
					.stream().anyMatch(e -> e.hasEffect(MobEffects.WEAKNESS));
		});
		if (!reeling) {
			throw new AssertionError("a parried attacker should reel (weakness and slowness)");
		}
		// out of stamina, the next blow breaks the guard
		context.waitTicks(20);
		world.getServer().runOnServer(server -> Stamina.drain(server.getPlayerList().getPlayerByName("Player0"), Stamina.max() - 4));
		strike(world, 6.0f);
		context.waitTicks(4);
		context.takeScreenshot("combat_guard_break");
		boolean still = world.getServer().computeOnServer(server -> Guard.guarding(server.getPlayerList().getPlayerByName("Player0")));
		if (still) {
			throw new AssertionError("a blow with no stamina left should break the guard");
		}
		context.getInput().releaseKey(options -> options.keyUse);
		world.getServer().runCommand("kill @e[type=minecraft:husk]");
		context.waitTicks(10);
	}

	/** Each husk's health, by its name tag. */
	private static java.util.Map<String, Float> husks(TestSingleplayerContext world) {
		return world.getServer().computeOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayerByName("Player0");
			java.util.Map<String, Float> health = new java.util.TreeMap<>();
			for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(10), e -> e.getType() == EntityTypes.HUSK)) {
				health.put(e.getCustomName() == null ? "?" : e.getCustomName().getString(), e.getHealth());
			}
			return health;
		});
	}

	private static PlayerMovesAnim playing(ClientGameTestContext context) {
		return context.computeOnClient(client -> {
			PlayerAnimator animator = PlayerAnimator.get(client.player.getId());
			return animator == null ? null : animator.move();
		});
	}

	/**
	 * Swings: a click cuts everything in the arc in front and nothing behind; clicks in time chain into the next moves;
	 * holding the key makes the heavy move, for stamina; and with a block under the crosshair and nothing to fight, the
	 * key still mines.
	 */
	private static void swings(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("effect give Player0 minecraft:instant_health 1 4 true");
		context.waitTicks(60);
		String husk = "{NoAI:1b,Silent:1b,PersistenceRequired:1b,Rotation:[180f,0f],CustomName:\"%s\"}";
		world.getServer().runCommand("execute at Player0 run summon minecraft:husk ~ ~ ~2.4 " + String.format(husk, "front"));
		world.getServer().runCommand("execute at Player0 run summon minecraft:husk ~1.6 ~ ~1.9 " + String.format(husk, "left"));
		world.getServer().runCommand("execute at Player0 run summon minecraft:husk ~-1.6 ~ ~1.9 " + String.format(husk, "right"));
		world.getServer().runCommand("execute at Player0 run summon minecraft:husk ~ ~ ~-2.4 " + String.format(husk, "behind"));
		world.getServer().runCommand("execute as Player0 at @s run tp @s ~ ~ ~ 0 0");
		context.waitTicks(15);
		java.util.Map<String, Float> before = husks(world);
		// one click: the first cut
		context.getInput().pressKey(options -> options.keyAttack);
		context.waitTicks(2);
		if (playing(context) != PlayerMovesAnim.SWORD_1) {
			throw new AssertionError("a click with a sword should begin its first cut, not " + playing(context));
		}
		context.waitTicks(3);
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.waitTicks(1);
		context.takeScreenshot("combat_swing_live");
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		context.waitTicks(4);
		java.util.Map<String, Float> after = husks(world);
		for (String name : new String[]{"front", "left", "right"}) {
			if (after.get(name) >= before.get(name)) {
				throw new AssertionError("the cut should strike the husk in the arc at " + name + ": " + before + " then " + after);
			}
		}
		if (after.get("behind") < before.get("behind")) {
			throw new AssertionError("the cut struck the husk behind the player");
		}
		if (before.get("front") - after.get("front") <= before.get("left") - after.get("left")) {
			throw new AssertionError("the husk aimed at should take the most: " + before + " then " + after);
		}
		// click again as it ends, and again: the chain
		context.waitTicks(12);
		context.getInput().pressKey(options -> options.keyAttack);
		context.waitTicks(8);
		context.getInput().pressKey(options -> options.keyAttack);
		context.waitTicks(4);
		if (playing(context) != PlayerMovesAnim.SWORD_2) {
			throw new AssertionError("a second click in time should chain into the second cut, not " + playing(context));
		}
		context.waitTicks(4);
		context.getInput().pressKey(options -> options.keyAttack);
		context.waitTicks(6);
		if (playing(context) != PlayerMovesAnim.SWORD_3) {
			throw new AssertionError("a third click in time should chain into the thrust, not " + playing(context));
		}
		context.waitTicks(3);
		context.takeScreenshot("combat_thrust_first_person");
		context.waitTicks(30);
		java.util.Map<String, Float> chained = husks(world);
		if (chained.get("front") >= after.get("front") - 8) {
			throw new AssertionError("three more blows should have landed on the husk in front: " + after + " then " + chained);
		}
		// hold the key: the heavy move, for stamina
		world.getServer().runCommand("effect give @e[type=minecraft:husk] minecraft:instant_damage 1 4 true");
		context.waitTicks(40);
		double[] fresh = place(world);
		context.getInput().holdKey(options -> options.keyAttack);
		context.waitTicks(9);
		if (playing(context) != PlayerMovesAnim.SWORD_HEAVY) {
			throw new AssertionError("holding the attack key should turn the cut into the heavy blow, not " + playing(context));
		}
		context.takeScreenshot("combat_heavy_first_person");
		// (asked now: stamina is back before the blow has even recovered)
		double[] spent = place(world);
		if (spent[2] >= fresh[2]) {
			throw new AssertionError("a heavy blow should cost stamina: " + fresh[2] + " then " + spent[2]);
		}
		context.waitTicks(12);
		context.getInput().releaseKey(options -> options.keyAttack);
		context.waitTicks(10);
		java.util.Map<String, Float> heavy = husks(world);
		// (the husks were healed to full before it)
		if (heavy.get("front") > 12) {
			throw new AssertionError("the heavy blow should have taken about half the health of the husk in front: " + heavy);
		}
		// nothing to fight and a block under the crosshair: the key mines
		world.getServer().runCommand("kill @e[type=minecraft:husk]");
		world.getServer().runCommand("execute as Player0 at @s run tp @s ~ ~ ~ 0 80");
		context.waitTicks(20);
		context.getInput().pressKey(options -> options.keyAttack);
		context.waitTicks(3);
		if (playing(context) != null) {
			throw new AssertionError("with a block under the crosshair and nothing to fight, the attack key should mine, not swing (" + playing(context) + ")");
		}
		world.getServer().runCommand("execute as Player0 at @s run tp @s ~ ~ ~ 0 8");
		context.waitTicks(5);
	}

	/**
	 * The hell weapons' own moves: the Bloodletter's third cut is its spin (seen from outside, its blade trailing blood),
	 * and the Tithe Axe's held slam shakes the ground, hurting what stands beside the blade's path.
	 */
	private static void hellWeapons(ClientGameTestContext context, TestSingleplayerContext world) {
		String husk = "{NoAI:1b,Silent:1b,PersistenceRequired:1b,Rotation:[180f,0f],CustomName:\"%s\"}";
		world.getServer().runCommand("item replace entity Player0 weapon.mainhand with hellcraft:bloodletter");
		world.getServer().runCommand("execute at Player0 run summon minecraft:husk ~ ~ ~2.4 " + String.format(husk, "front"));
		world.getServer().runCommand("execute at Player0 run summon minecraft:husk ~ ~ ~-2.2 " + String.format(husk, "behind"));
		world.getServer().runCommand("execute as Player0 at @s run tp @s ~ ~ ~ 0 0");
		context.waitTicks(15);
		context.getInput().pressKey(options -> options.keyAttack);
		context.waitTicks(7);
		context.getInput().pressKey(options -> options.keyAttack);
		context.waitTicks(7);
		context.getInput().pressKey(options -> options.keyAttack);
		context.waitTicks(4);
		if (playing(context) != PlayerMovesAnim.BLOOD_3) {
			throw new AssertionError("the Bloodletter's third click should be its spin, not " + playing(context));
		}
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		context.waitTicks(3);
		context.takeScreenshot("combat_bloodletter_spin");
		context.waitTicks(10);
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		java.util.Map<String, Float> spun = husks(world);
		if (spun.get("behind") >= 20) {
			throw new AssertionError("the Bloodletter's spin should strike the husk behind too: " + spun);
		}
		world.getServer().runCommand("kill @e[type=minecraft:husk]");
		// the Tithe Axe's slam: a husk off to the side, outside the blade's arc but inside the shockwave
		world.getServer().runCommand("item replace entity Player0 weapon.mainhand with hellcraft:tithe_axe");
		world.getServer().runCommand("execute at Player0 run summon minecraft:husk ~2.8 ~ ~2.0 " + String.format(husk, "side"));
		context.waitTicks(60);
		context.getInput().holdKey(options -> options.keyAttack);
		context.waitTicks(8);
		if (playing(context) != PlayerMovesAnim.TITHE_HEAVY) {
			throw new AssertionError("holding attack with the Tithe Axe should begin its slam, not " + playing(context));
		}
		context.waitTicks(11);
		context.takeScreenshot("combat_tithe_slam");
		context.getInput().releaseKey(options -> options.keyAttack);
		context.waitTicks(12);
		java.util.Map<String, Float> shaken = husks(world);
		if (shaken.get("side") >= 20) {
			throw new AssertionError("the Tithe Axe's slam should shake the husk beside its path: " + shaken);
		}
		world.getServer().runCommand("kill @e[type=minecraft:husk]");
		world.getServer().runCommand("item replace entity Player0 weapon.mainhand with minecraft:iron_sword");
		context.waitTicks(5);
	}

	/** A full hell weapon's Blood Art, by its own key (R), and again from the guard with the attack key. */
	private static void bloodArt(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("item replace entity Player0 weapon.mainhand with hellcraft:bloodletter");
		world.getServer().runCommand("execute as Player0 at @s run tp @s ~ ~ ~ 0 0");
		context.waitTicks(10);
		for (String how : new String[]{"key", "guard"}) {
			world.getServer().runCommand("hellcraft charge Player0");
			context.waitTicks(5);
			int before = world.getServer().computeOnServer(server -> net.thesmallthings.hellcraft.blood.HellWeapons.charge(server.getPlayerList().getPlayerByName("Player0")));
			if (before < 100) {
				throw new AssertionError("/hellcraft charge should fill the blood charge, not " + before);
			}
			if (how.equals("key")) {
				context.getInput().pressKey(DashKey.BLOOD_ART);
				context.waitTicks(2);
				context.takeScreenshot("combat_blood_art_key");
			} else {
				context.getInput().holdKey(options -> options.keyUse);
				context.waitTicks(8);
				context.getInput().pressKey(options -> options.keyAttack);
				context.waitTicks(3);
				context.getInput().releaseKey(options -> options.keyUse);
			}
			context.waitTicks(10);
			int after = world.getServer().computeOnServer(server -> net.thesmallthings.hellcraft.blood.HellWeapons.charge(server.getPlayerList().getPlayerByName("Player0")));
			if (after != 0) {
				throw new AssertionError("the Blood Art (by " + how + ") should spend the charge; it is still " + after);
			}
			world.getServer().runCommand("execute as Player0 at @s run tp @s ~ ~ ~-5 0 0");
			context.waitTicks(30);
		}
		world.getServer().runCommand("item replace entity Player0 weapon.mainhand with minecraft:iron_sword");
		context.waitTicks(5);
	}

	/** A blow from straight overhead (a lover's dive) is blocked by a raised guard whichever way the player faces. */
	private static void overhead(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("execute at Player0 run summon minecraft:husk ~0.3 ~2.3 ~-0.6 {NoAI:1b,NoGravity:1b,Silent:1b,PersistenceRequired:1b}");
		world.getServer().runCommand("effect give Player0 minecraft:instant_health 1 4 true");
		context.waitTicks(40);
		context.getInput().holdKey(options -> options.keyUse);
		context.waitTicks(14);
		double[] before = place(world);
		strike(world, 6.0f);
		context.waitTicks(4);
		double[] after = place(world);
		context.getInput().releaseKey(options -> options.keyUse);
		if (before[3] - after[3] >= 4.5) {
			throw new AssertionError("a guard should block a blow from overhead, behind the player's back; they lost " + (before[3] - after[3]));
		}
		world.getServer().runCommand("kill @e[type=minecraft:husk]");
		context.waitTicks(5);
	}

	/** The husk in front deals the player a blow, by hand. */
	private static void strike(TestSingleplayerContext world, float damage) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayerByName("Player0");
			LivingEntity husk = player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(8), e -> e.getType() == EntityTypes.HUSK)
					.get(0);
			player.setInvulnerableTime(0);
			player.hurtServer(player.level(), player.level().damageSources().mobAttack(husk), damage);
		});
	}
}
