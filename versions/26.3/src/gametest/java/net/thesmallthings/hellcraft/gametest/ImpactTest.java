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
			// each mark's cue sound played, timed to its landing (client/sound/DangerCues)
			int[] played = context.computeOnClient(client -> net.thesmallthings.hellcraft.client.sound.DangerCues.PLAYED.clone());
			if (played[0] < 1 || played[1] < 1 || played[2] < 1) {
				throw new AssertionError("each danger mark should play its cue: red, white, purple played " + java.util.Arrays.toString(played));
			}
			// and every cue sound (tools/gen_sounds.py) is known to the sound manager, its file found
			String missing = context.computeOnClient(client -> {
				java.util.List<String> gone = new java.util.ArrayList<>();
				for (net.minecraft.resources.Identifier id : client.getSoundManager().getAvailableSounds()) {
					if (!id.getNamespace().equals("hellcraft") || !id.getPath().startsWith("cue.")) {
						continue;
					}
					net.minecraft.resources.Identifier file = net.minecraft.resources.Identifier.fromNamespaceAndPath("hellcraft",
							"sounds/" + id.getPath().replace("cue.", "cue/") + ".ogg");
					if (client.getResourceManager().getResource(file).isEmpty()) {
						gone.add(id.toString());
					}
				}
				long cues = client.getSoundManager().getAvailableSounds().stream()
						.filter(id -> id.getNamespace().equals("hellcraft") && id.getPath().startsWith("cue.")).count();
				return cues < 10 ? "only " + cues + " cue sounds known" : String.join(", ", gone);
			});
			if (!missing.isEmpty()) {
				throw new AssertionError("cue sounds without their file: " + missing);
			}
		}
	}
}
