package net.thesmallthings.hellcraft.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.thesmallthings.hellcraft.client.nav.ClientNav;
import net.thesmallthings.hellcraft.client.nav.ClientWaymarks;
import net.thesmallthings.hellcraft.client.nav.JournalScreen;
import net.thesmallthings.hellcraft.client.nav.MapOfHellScreen;
import net.thesmallthings.hellcraft.net.WaymarksPayload;
import net.thesmallthings.hellcraft.world.InfernoGeometry;

import java.util.Locale;
import java.util.Properties;

/**
 * The Pilgrim's Journal as a book (journal_*: "Where you are", a Guide page, the Codex) and waymarks on the Map of Hell
 * (waymark_*): the panel that places one, the map with a few placed (one shared), and the compass following one. Also
 * runs the waymarks' own self-test on the server.
 */
public class WaymarkTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		Properties properties = new Properties();
		properties.setProperty("view-distance", "6");
		properties.setProperty("simulation-distance", "4");
		try (TestDedicatedServerContext server = context.worldBuilder().adjustSettings(MapTest::inferno).createServer(properties);
			 TestDedicatedServerConnection connection = server.connect()) {
			connection.waitForChunksRender();
			context.waitFor(client -> ClientNav.inferno() && !ClientNav.pages().isEmpty(), 200);
			server.runCommand("gamemode creative Player0");
			double x = InfernoGeometry.scaled(2160);
			double y = InfernoGeometry.baseHeight(x, 6) + 2;
			server.runCommand(String.format(Locale.ROOT, "tp Player0 %.1f %.1f 6.5 90 8", x, y));
			context.waitTicks(10);
			connection.waitForChunksRender();
			// a guardian met, so the Codex has a page
			server.runOnServer(s -> net.thesmallthings.hellcraft.net.MapSync.unlockCodex(s.getPlayerList().getPlayerByName("Player0"), "minos"));

			// the Journal
			context.setScreen(() -> JournalScreen.at(0));
			context.getInput().setCursorPos(2, 2);
			context.waitTicks(10);
			context.takeScreenshot("journal_where");
			int guide = context.computeOnClient(client -> {
				for (int i = 0; i < ClientNav.pages().size(); i++) {
					if (ClientNav.pages().get(i).title().equals("BLOOD ALTARS")) {
						return 1 + ClientNav.codex().size() + i;
					}
				}
				return 1 + ClientNav.codex().size();
			});
			context.setScreen(() -> JournalScreen.at(guide));
			context.waitTicks(5);
			context.takeScreenshot("journal_guide");
			if (context.computeOnClient(client -> !ClientNav.codex().isEmpty())) {
				context.setScreen(() -> JournalScreen.at(1));
				context.waitTicks(5);
				context.takeScreenshot("journal_codex");
			}
			context.setScreen(() -> null);

			// the waymarks' own test on the server
			String test = server.computeOnServer(net.thesmallthings.hellcraft.world.Waymarks::selfTest);
			if (!test.startsWith("Waymarks test OK")) {
				throw new AssertionError(test);
			}

			// placing one: the panel
			double wx = x - 260;
			context.setScreen(() -> MapOfHellScreen.placing(wx, 40, 4, "Iron farm", 3));
			context.getInput().setCursorPos(2, 2);
			context.waitTicks(10);
			context.takeScreenshot("waymark_panel");
			context.setScreen(() -> null);

			// a few placed, one shared, and the one we follow
			context.runOnClient(client -> {
				ClientWaymarks.save(0, "Iron farm", (int) wx, 40, 3, false);
				ClientWaymarks.save(0, "Our base", (int) x - 120, -90, 0, true);
				ClientWaymarks.save(0, "Cerberus's lair", (int) InfernoGeometry.scaled(2000), 300, 5, false);
			});
			context.waitFor(client -> ClientWaymarks.all().size() >= 3, 100);
			context.runOnClient(client -> {
				for (WaymarksPayload.Entry e : ClientWaymarks.all()) {
					if (e.name().equals("Our base")) {
						ClientWaymarks.follow(e.id());
					}
				}
			});
			if (context.computeOnClient(client -> ClientWaymarks.followed() == null || !ClientWaymarks.followed().shared())) {
				throw new AssertionError("the shared waymark was not saved, or can't be followed");
			}
			context.setScreen(() -> MapOfHellScreen.at(x - 150, 0, 4));
			context.getInput().setCursorPos(2, 2);
			context.waitTicks(10);
			context.takeScreenshot("waymark_map");
			context.setScreen(() -> null);
			server.runCommand(String.format(Locale.ROOT, "tp Player0 %.1f %.1f 6.5 120 8", x, y));
			context.waitTicks(20);
			context.takeScreenshot("waymark_compass");
		}
	}
}
