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
			roll(context, world);
			guard(context, world);
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
