package net.thesmallthings.hellcraft.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.resources.Identifier;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.NobleCastle;
import net.thesmallthings.hellcraft.world.Zone;

import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Limbo without colour (client/layer/LimboGrey): out on the grey meadow (screenshot limbo_grey, which must have the
 * deepest grey effect on) and on the Noble Castle's green, where the colour comes back (limbo_castle).
 */
public class LimboTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		Properties properties = new Properties();
		properties.setProperty("view-distance", "6");
		properties.setProperty("simulation-distance", "4");
		try (TestDedicatedServerContext server = context.worldBuilder().adjustSettings(MapTest::inferno).createServer(properties);
			 TestDedicatedServerConnection connection = server.connect()) {
			connection.waitForChunksRender();
			server.runCommand("gamemode spectator Player0");
			double mid = (InfernoGeometry.outerRadius(Zone.LIMBO) + InfernoGeometry.outerRadius(Zone.LUST)) / 2;
			// a quarter of the way round from the castle, out on the meadow
			server.runCommand(String.format(Locale.ROOT, "tp Player0 0 %d %.1f 180 12", 150, mid));
			context.waitTicks(20);
			connection.waitForChunksRender();
			context.waitTicks(20);
			List<Identifier> effects = context.computeOnClient(client -> List.copyOf(client.gameRenderer.getRequestedPostEffects()));
			if (!effects.contains(Identifier.fromNamespaceAndPath("hellcraft", "limbo_5"))) {
				throw new AssertionError("Limbo's grey is not on in the middle of Limbo: " + effects);
			}
			context.takeScreenshot("limbo_grey");
			server.runCommand(String.format(Locale.ROOT, "tp Player0 %d %d %d 90 15", NobleCastle.X + 30, 150, NobleCastle.Z));
			context.waitTicks(20);
			connection.waitForChunksRender();
			context.waitTicks(20);
			context.takeScreenshot("limbo_castle");
		}
	}
}
