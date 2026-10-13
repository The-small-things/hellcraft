package net.thesmallthings.hellcraft.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.thesmallthings.hellcraft.entity.HellEntities;
import net.thesmallthings.hellcraft.entity.TestRig;

/**
 * The jointed, keyframe-animated test rig, three frames half a second apart: its arms, head and body should be
 * in a different pose in each (screenshots rig_wave_0 to rig_wave_2).
 */
public class RigTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("execute as Player0 at @s run tp @s ~ ~ ~ 0 10");
			world.getServer().runCommand("execute at Player0 run summon hellcraft:test_rig ~ ~ ~3 {Rotation:[180f,0f],NoAI:1b,Silent:1b,PersistenceRequired:1b}");
			world.getConnection().waitForClientboundEntityUpdates(HellEntities.TEST_RIG);
			context.runOnClient(client -> {
				if (!client.gui.hud.isHidden()) {
					client.gui.hud.toggle();
				}
			});
			context.waitTicks(20);
			boolean seen = context.computeOnClient(client -> client.level.getEntitiesOfClass(TestRig.class,
					client.player.getBoundingBox().inflate(8)).stream().anyMatch(rig -> rig.wave.isStarted()));
			if (!seen) {
				throw new AssertionError("the client sees no test rig with its animation running");
			}
			for (int frame = 0; frame < 3; frame++) {
				context.takeScreenshot("rig_wave_" + frame);
				context.waitTicks(10);
			}
			context.runOnClient(client -> {
				if (client.gui.hud.isHidden()) {
					client.gui.hud.toggle();
				}
			});
		}
	}
}
