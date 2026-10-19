package net.thesmallthings.hellcraft.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * A boss's blows landing (client/danger/Impacts): a red circle, a white ring and a purple line in front of the player
 * (/hellcraft danger show), filmed as they fill and the moment they erupt (screenshots impact_*).
 */
public class ImpactTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("execute as Player0 at @s run tp @s ~ ~ ~ 0 18");
			context.waitTicks(20);
			world.getServer().runCommand("execute as Player0 at @s run hellcraft danger show");
			context.waitTicks(30);
			context.takeScreenshot("impact_warning");
			context.waitTicks(11);
			context.takeScreenshot("impact_land");
			context.waitTicks(6);
			context.takeScreenshot("impact_after");
		}
	}
}
