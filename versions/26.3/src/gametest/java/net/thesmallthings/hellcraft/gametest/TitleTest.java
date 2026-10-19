package net.thesmallthings.hellcraft.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.thesmallthings.hellcraft.world.InfernoGeometry;

import java.io.File;
import java.util.Locale;
import java.util.Properties;

/**
 * Hellcraft's title screen (client/title/HellTitle): the logo, the splash and the panorama of Hell with its embers
 * (screenshot title_screen). With HELLCRAFT_PANORAMA=1 it also films the panoramas the title screen turns through:
 * from a few places in an Inferno world, six faces each, into build/run/clientGameTest/panorama_<place>/screenshots
 * (copy the chosen set to assets/minecraft/textures/gui/title/background/panorama_N.png).
 */
public class TitleTest implements FabricClientGameTest {
	private record View(String name, double x, double y, double z, float yaw) {
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		context.setScreen(() -> new TitleScreen(false));
		context.waitTicks(40);
		context.takeScreenshot("title_screen");
		if (System.getenv("HELLCRAFT_PANORAMA") != null) {
			panoramas(context);
		}
	}

	private static void panoramas(ClientGameTestContext context) {
		Properties properties = new Properties();
		properties.setProperty("view-distance", "16");
		properties.setProperty("simulation-distance", "4");
		View[] views = {
				new View("gate", InfernoGeometry.gateX() + 30, 190, 6, 90),
				new View("dis", InfernoGeometry.scaled(1470), 92, 8, 90),
				new View("well", InfernoGeometry.scaled(470), 4, 30, 95),
				new View("greed", InfernoGeometry.scaled(1900), 130, 40, 90),
		};
		try (TestDedicatedServerContext server = context.worldBuilder().adjustSettings(MapTest::inferno).createServer(properties);
			 TestDedicatedServerConnection connection = server.connect()) {
			connection.waitForChunksRender();
			server.runCommand("gamemode spectator Player0");
			context.runOnClient(client -> {
				client.options.renderDistance().set(12);
				if (!client.gui.hud.isHidden()) {
					client.gui.hud.toggle();
				}
			});
			for (View view : views) {
				server.runCommand(String.format(Locale.ROOT, "tp Player0 %.1f %.1f %.1f %.1f 0", view.x(), view.y(), view.z(), view.yaw()));
				// far chunks take a while to make and draw; waitForChunksRender gives up at this distance
				context.waitTicks(900);
				context.runOnClient(client -> {
					File folder = new File(client.gameDirectory, "panorama_" + view.name());
					new File(folder, "screenshots").mkdirs();
					client.grabPanoramixScreenshot(folder);
				});
			}
		}
	}
}
