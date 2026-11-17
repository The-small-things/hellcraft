package net.thesmallthings.hellcraft.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.thesmallthings.hellcraft.client.combat.anim.PlayerAnimator;
import net.thesmallthings.hellcraft.entity.boss.gen.PlayerMovesAnim;

import java.util.function.Function;

/**
 * How players get about and use things (client/combat/anim/Locomotion, the item stances, PoseSmoother), played for real
 * in single player and filmed from in front in third person (motion_*): walking, sprinting, sneaking, a jump, a long fall
 * and its landing, a bow drawn and loosed, a shield raised and struck, eating, drinking, a crossbow fired and a trident
 * thrown. Each step also checks the animator saw it (the sprint's weight, the air, the landing, the stance, the release).
 */
public class MotionTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("weather clear");
			world.getServer().runCommand("difficulty easy");
			world.getServer().runCommand("gamemode survival Player0");
			// a long stone track running south, clear above
			world.getServer().runCommand("execute at Player0 run fill ~-4 ~-1 ~-4 ~4 ~-1 ~44 minecraft:smooth_stone");
			world.getServer().runCommand("execute at Player0 run fill ~-4 ~ ~-4 ~4 ~16 ~44 minecraft:air");
			world.getServer().runCommand("execute at Player0 run kill @e[type=!minecraft:player,distance=..60]");
			world.getServer().runCommand("execute as Player0 at @s align xyz run tp @s ~0.5 ~ ~0.5 0 10");
			world.getServer().runCommand("effect give Player0 minecraft:resistance 600 4 true");
			world.getServer().runCommand("effect give Player0 minecraft:saturation 600 0 true");
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(20);
			getAbout(context, world);
			items(context, world);
			firstPerson(context, world);
		}
	}

	private static void back(TestSingleplayerContext world) {
		world.getServer().runCommand("execute as Player0 at @s run tp @s ~ ~ ~ 0 10");
	}

	private static void hold(ClientGameTestContext context, Function<Options, KeyMapping> key, int ticks) {
		context.getInput().holdKey(key);
		context.waitTicks(ticks);
	}

	/** A jump (the key held a moment: jumping reads whether it is down, which a press and release in one go never is). */
	private static void jump(ClientGameTestContext context, int ticks) {
		context.getInput().holdKey(o -> o.keyJump);
		context.waitTicks(2);
		context.getInput().releaseKey(o -> o.keyJump);
		context.waitTicks(ticks - 2);
	}

	private static PlayerAnimator animator(ClientGameTestContext context) {
		return context.computeOnClient(client -> PlayerAnimator.of(client.player));
	}

	// ------------------------------------------------------------------------------------------------- getting about

	private static void getAbout(ClientGameTestContext context, TestSingleplayerContext world) {
		if (!context.computeOnClient(client -> PlayerAnimator.get(client.player.getId()) != null && PlayerAnimator.get(client.player.getId()).moving())) {
			throw new AssertionError("no animator is getting the player about");
		}
		// walking, then sprinting
		hold(context, o -> o.keyUp, 14);
		context.takeScreenshot("motion_walk");
		hold(context, o -> o.keySprint, 16);
		context.takeScreenshot("motion_run");
		float sprint = context.computeOnClient(client -> PlayerAnimator.of(client.player).motion.sprinting());
		if (sprint < 0.8f) {
			throw new AssertionError("sprinting, but the run's weight is only " + sprint);
		}
		// a jump mid-run
		jump(context, 4);
		context.takeScreenshot("motion_run_jump");
		float air = context.computeOnClient(client -> PlayerAnimator.of(client.player).motion.airborne());
		context.getInput().releaseKey(o -> o.keySprint);
		context.getInput().releaseKey(o -> o.keyUp);
		if (air < 0.3f) {
			throw new AssertionError("jumping, but the air's weight is only " + air);
		}
		context.waitTicks(20);
		// standing still, then a jump on the spot
		context.takeScreenshot("motion_idle");
		jump(context, 5);
		context.takeScreenshot("motion_jump");
		context.waitTicks(20);
		// a long fall and its landing
		world.getServer().runCommand("execute as Player0 at @s run tp @s ~ ~14 ~ 0 10");
		context.waitTicks(16);
		context.takeScreenshot("motion_fall");
		float falling = context.computeOnClient(client -> PlayerAnimator.of(client.player).motion.falling());
		if (falling < 0.3f) {
			throw new AssertionError("falling 14 blocks, but the fall's weight is only " + falling);
		}
		boolean landed = false;
		for (int i = 0; i < 40 && !landed; i++) {
			context.waitTicks(1);
			landed = context.computeOnClient(client -> PlayerAnimator.of(client.player).motion.sinceLanding() <= 2);
		}
		if (!landed) {
			throw new AssertionError("the player came down, but no landing was seen");
		}
		context.takeScreenshot("motion_land");
		context.waitTicks(20);
		// sneaking forward: the crouch eases down (not at once) and the short steps play
		context.getInput().holdKey(o -> o.keyShift);
		context.waitTicks(2);
		float crouch = context.computeOnClient(client -> PlayerAnimator.of(client.player).motion.crouch(1));
		if (crouch <= 0.05f || crouch >= 0.95f) {
			throw new AssertionError("two ticks into a crouch it should be part way down, not " + crouch);
		}
		hold(context, o -> o.keyUp, 14);
		context.takeScreenshot("motion_sneak");
		context.getInput().releaseKey(o -> o.keyUp);
		context.getInput().releaseKey(o -> o.keyShift);
		context.waitTicks(10);
		back(world);
	}

	// ------------------------------------------------------------------------------------------------- using things

	private static void items(ClientGameTestContext context, TestSingleplayerContext world) {
		// a bow, drawn and loosed
		world.getServer().runCommand("item replace entity Player0 weapon.mainhand with minecraft:bow");
		world.getServer().runCommand("give Player0 minecraft:arrow 16");
		context.waitTicks(5);
		hold(context, o -> o.keyUse, 6);
		context.takeScreenshot("motion_bow_nock");
		context.waitTicks(18);
		context.takeScreenshot("motion_bow_drawn");
		expectUsing(context, PlayerMovesAnim.BOW_DRAW);
		context.getInput().releaseKey(o -> o.keyUse);
		context.waitTicks(2);
		context.takeScreenshot("motion_bow_release");
		expectMove(context, PlayerMovesAnim.BOW_RELEASE);
		context.waitTicks(20);

		// a shield raised, then struck
		world.getServer().runCommand("item replace entity Player0 weapon.mainhand with minecraft:air");
		world.getServer().runCommand("item replace entity Player0 weapon.offhand with minecraft:shield");
		world.getServer().runCommand("execute at Player0 run summon minecraft:zombie ~2.5 ~ ~1.5 {NoAI:1b,Silent:1b,Rotation:[120f,0f]}");
		context.waitTicks(5);
		hold(context, o -> o.keyUse, 12);
		context.takeScreenshot("motion_shield");
		expectUsing(context, PlayerMovesAnim.SHIELD_BRACE);
		world.getServer().runCommand("damage Player0 2 minecraft:mob_attack by @e[type=minecraft:zombie,limit=1,sort=nearest]");
		context.waitTicks(2);
		context.takeScreenshot("motion_shield_hit");
		expectMove(context, PlayerMovesAnim.SHIELD_HIT);
		context.getInput().releaseKey(o -> o.keyUse);
		// gone without a puff of smoke in front of the camera
		world.getServer().runCommand("tp @e[type=minecraft:zombie] ~ -100 ~");
		world.getServer().runCommand("item replace entity Player0 weapon.offhand with minecraft:air");
		context.waitTicks(15);

		// eating and drinking
		world.getServer().runCommand("item replace entity Player0 weapon.mainhand with minecraft:golden_apple 4");
		context.waitTicks(5);
		hold(context, o -> o.keyUse, 12);
		context.takeScreenshot("motion_eat");
		expectUsing(context, PlayerMovesAnim.EAT);
		context.getInput().releaseKey(o -> o.keyUse);
		context.waitTicks(10);
		world.getServer().runCommand("item replace entity Player0 weapon.mainhand with minecraft:potion[potion_contents={potion:\"minecraft:water\"}]");
		context.waitTicks(5);
		hold(context, o -> o.keyUse, 14);
		context.takeScreenshot("motion_drink");
		expectUsing(context, PlayerMovesAnim.DRINK);
		context.getInput().releaseKey(o -> o.keyUse);
		context.waitTicks(10);

		// a loaded crossbow fired
		world.getServer().runCommand("item replace entity Player0 weapon.mainhand with minecraft:crossbow[charged_projectiles=[{id:\"minecraft:arrow\"}]]");
		context.waitTicks(10);
		context.takeScreenshot("motion_crossbow_loaded");
		context.getInput().pressKey(o -> o.keyUse);
		context.waitTicks(2);
		context.takeScreenshot("motion_crossbow_fire");
		expectMove(context, PlayerMovesAnim.CROSSBOW_FIRE);
		context.waitTicks(15);

		// a trident thrown
		world.getServer().runCommand("item replace entity Player0 weapon.mainhand with minecraft:trident");
		context.waitTicks(5);
		hold(context, o -> o.keyUse, 14);
		context.takeScreenshot("motion_trident_aim");
		context.getInput().releaseKey(o -> o.keyUse);
		context.waitTicks(3);
		context.takeScreenshot("motion_trident_throw");
		expectMove(context, PlayerMovesAnim.THROW);
		context.waitTicks(15);
	}

	// ------------------------------------------------------------------------------------------------- first person

	private static void firstPerson(ClientGameTestContext context, TestSingleplayerContext world) {
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		back(world);
		world.getServer().runCommand("item replace entity Player0 weapon.mainhand with minecraft:iron_sword");
		context.waitTicks(10);
		context.takeScreenshot("motion_fp_stand");
		// sprinting: the sword carried lower, pumping
		hold(context, o -> o.keyUp, 6);
		hold(context, o -> o.keySprint, 10);
		context.takeScreenshot("motion_fp_run");
		jump(context, 4);
		context.takeScreenshot("motion_fp_jump");
		context.getInput().releaseKey(o -> o.keySprint);
		context.getInput().releaseKey(o -> o.keyUp);
		context.waitTicks(20);
		back(world);
		// a shield coming up: part way there two ticks in, not snapped
		world.getServer().runCommand("item replace entity Player0 weapon.offhand with minecraft:shield");
		world.getServer().runCommand("item replace entity Player0 weapon.mainhand with minecraft:air");
		context.waitTicks(10);
		context.getInput().holdKey(o -> o.keyUse);
		context.waitTicks(2);
		context.takeScreenshot("motion_fp_shield_raising");
		context.waitTicks(8);
		context.takeScreenshot("motion_fp_shield_up");
		context.getInput().releaseKey(o -> o.keyUse);
		context.waitTicks(2);
		context.takeScreenshot("motion_fp_shield_lowering");
		context.waitTicks(10);
		world.getServer().runCommand("item replace entity Player0 weapon.offhand with minecraft:air");
		// a bow loosed: it eases back down instead of snapping
		world.getServer().runCommand("item replace entity Player0 weapon.mainhand with minecraft:bow");
		context.waitTicks(10);
		hold(context, o -> o.keyUse, 22);
		context.takeScreenshot("motion_fp_bow_drawn");
		context.getInput().releaseKey(o -> o.keyUse);
		context.waitTicks(2);
		context.takeScreenshot("motion_fp_bow_loosed");
		context.waitTicks(10);
	}

	private static void expectUsing(ClientGameTestContext context, PlayerMovesAnim want) {
		PlayerMovesAnim using = context.computeOnClient(client -> PlayerAnimator.of(client.player).using());
		if (using != want) {
			throw new AssertionError("expected the " + want + " stance, but it is " + using);
		}
	}

	private static void expectMove(ClientGameTestContext context, PlayerMovesAnim want) {
		PlayerMovesAnim move = context.computeOnClient(client -> PlayerAnimator.of(client.player).move());
		if (move != want) {
			throw new AssertionError("expected the " + want + " move, but it is " + move);
		}
	}
}
