package net.thesmallthings.hellcraft.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.thesmallthings.hellcraft.world.Paradiso;
import net.thesmallthings.hellcraft.world.ParadisoGeometry;
import net.thesmallthings.hellcraft.world.ParadisoGeometry.Sphere;
import net.thesmallthings.hellcraft.world.Purgatory;

import java.util.Locale;
import java.util.Properties;

/**
 * Purgatory and Paradiso (Phase 7), from a spectator's camera: Purgatory's island in its starlit sea and its summit
 * (purgatory_*), then each of Paradiso's spheres from its arrival isle, looking over its great island with the next
 * sphere and the column of light above (paradiso_*).
 */
public class ParadisoTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		Properties properties = new Properties();
		properties.setProperty("view-distance", "8");
		properties.setProperty("simulation-distance", "4");
		try (TestDedicatedServerContext server = context.worldBuilder().adjustSettings(MapTest::inferno).createServer(properties);
			 TestDedicatedServerConnection connection = server.connect()) {
			connection.waitForChunksRender();
			server.runCommand("gamemode spectator Player0");
			context.runOnClient(client -> {
				if (!client.gui.hud.isHidden()) {
					client.gui.hud.toggle();
				}
			});
			String status = server.computeOnServer(Paradiso::status);
			if (!status.contains("stands")) {
				throw new AssertionError(status);
			}
			shot(context, server, connection, "hellcraft:purgatory", "purgatory_island", 0, 80, 110, 0, 90, 0);
			shot(context, server, connection, "hellcraft:purgatory", "purgatory_shore", 12, Purgatory.SUMMIT_Y - 50, 62, 0, Purgatory.SUMMIT_Y - 50, 0);
			shot(context, server, connection, "hellcraft:purgatory", "purgatory_summit", 10, Purgatory.SUMMIT_Y + 6, 14, 0, Purgatory.SUMMIT_Y, 0);
			for (Sphere s : ParadisoGeometry.SPHERES) {
				double[] a = ParadisoGeometry.arrival(s);
				double[] c = ParadisoGeometry.centre(s);
				double bx = a[0] + (a[0] - c[0]) * 0.35;
				double bz = a[1] + (a[1] - c[1]) * 0.35;
				int y = ParadisoGeometry.topY(s);
				shot(context, server, connection, "hellcraft:paradiso", "paradiso_" + s.id, bx, y + 14, bz, c[0], y + 4, c[1]);
			}
			// from beside the Moon's departure dais, up its column to Mercury's arrival isle
			double[] d = ParadisoGeometry.departure(Sphere.MOON);
			int moonTop = ParadisoGeometry.topY(Sphere.MOON);
			shot(context, server, connection, "hellcraft:paradiso", "paradiso_climb", d[0] + 9, moonTop + 2, d[1] + 5, d[0], moonTop + ParadisoGeometry.STEP, d[1]);
		}
	}

	private static void shot(ClientGameTestContext context, TestDedicatedServerContext server, TestDedicatedServerConnection connection,
							 String dimension, String name, double x, double y, double z, double lx, double ly, double lz) {
		server.runCommand(String.format(Locale.ROOT, "execute in %s run tp Player0 %.1f %.1f %.1f facing %.1f %.1f %.1f", dimension, x, y, z, lx, ly, lz));
		context.waitTicks(30);
		connection.waitForChunksRender();
		context.waitTicks(20);
		context.takeScreenshot(name);
	}
}
