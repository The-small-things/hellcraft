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
 * The Pilgrim's Journal as a book (journal_*: "Where you are", Guide pages, the Codex) and waymarks on the Map of Hell
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
			int guide = guidePage(context, "BLOOD ALTARS");
			context.setScreen(() -> JournalScreen.at(guide));
			context.waitTicks(5);
			context.takeScreenshot("journal_guide");
			int weapons = guidePage(context, "HELL WEAPONS");
			context.setScreen(() -> JournalScreen.at(weapons));
			context.waitTicks(5);
			context.takeScreenshot("journal_weapons");
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
			context.setScreen(() -> MapOfHellScreen.at(0, 0, 1));
			context.getInput().setCursorPos(2, 2);
			context.waitTicks(10);
			context.takeScreenshot("waymark_map_whole");
			context.setScreen(() -> null);
			server.runCommand(String.format(Locale.ROOT, "tp Player0 %.1f %.1f 6.5 120 8", x, y));
			context.waitTicks(20);
			context.takeScreenshot("waymark_compass");
			labyrinth(context, server, connection);
			gatehouses(context, server, connection);
			// first steps: what Virgil has taught Player0 by now (a new soul here)
			String taught = server.computeOnServer(s -> String.join(",", net.thesmallthings.hellcraft.blood.HellState.get(s)
					.existing(s.getPlayerList().getPlayerByName("Player0").getUUID()).lessons));
			System.out.println("Lessons taught to Player0: " + taught);
		}
	}

	/**
	 * The gatehouse towers (world/Gatehouses) at the spawn gate of the Gate of Hell and at a gate of Dis: from outside
	 * (gatehouse_outside), the guard room (gatehouse_guard), the stair climbing past a floor (gatehouse_stair), the walk over
	 * the gate (gatehouse_walk) and a room in Dis (gatehouse_dis).
	 */
	private static void gatehouses(ClientGameTestContext context, TestDedicatedServerContext server, TestDedicatedServerConnection connection) {
		server.runCommand("gamemode spectator Player0");
		net.thesmallthings.hellcraft.world.Gatehouses.Tower t = server.computeOnServer(s -> {
			net.thesmallthings.hellcraft.world.Gatehouses.build(s.overworld(), "gate_0");
			return net.thesmallthings.hellcraft.world.Gatehouses.tower(s.overworld(), "gate_0", 1);
		});
		boolean furnished = server.computeOnServer(s -> s.overworld().getBlockState(t.centre())
				.is(net.minecraft.world.level.block.Blocks.DARK_OAK_FENCE));
		if (!furnished) {
			throw new AssertionError("the gatehouse's guard room has no table");
		}
		double ca = Math.cos(t.doorAngle());
		double sa = Math.sin(t.doorAngle());
		// outside: out from the wall, looking back at the gate between its towers
		double gx = t.cx() + ca * 11;
		double gz = t.cz() + sa * 11;
		double rx = gx / Math.hypot(gx, gz);
		double rz = gz / Math.hypot(gx, gz);
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %.1f %d %.1f facing %.1f %d %.1f", gx + rx * 30, t.ground() + 10, gz + rz * 30, gx,
				t.ground() + 10, gz));
		context.waitTicks(20);
		connection.waitForChunksRender();
		context.waitTicks(20);
		context.takeScreenshot("gatehouse_outside");
		// the guard room, from the far side of the aisle, looking at the table and the door
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %.2f %d %.2f facing %.2f %d %.2f", t.cx() - ca * 3.0, t.ground() + 1, t.cz() - sa * 3.0,
				t.cx() + ca * 4, t.ground(), t.cz() + sa * 4));
		context.waitTicks(20);
		connection.waitForChunksRender();
		context.waitTicks(10);
		context.takeScreenshot("gatehouse_guard");
		// the stair, from the middle of the first floor up, looking round at it
		int f1 = t.floors()[1];
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %.2f %d %.2f facing %.2f %d %.2f", t.cx() + sa * 1.5, f1 + 1, t.cz() - ca * 1.5,
				t.cx() - sa * 5, f1 + 2, t.cz() + ca * 5));
		context.waitTicks(20);
		context.takeScreenshot("gatehouse_stair");
		// the walk over the gate, looking at the tower's door
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %.2f %d %.2f facing %.2f %d %.2f", gx, t.walk() + 1, gz, t.cx(), t.walk() + 1, t.cz()));
		context.waitTicks(20);
		context.takeScreenshot("gatehouse_walk");
		// a room in Dis
		net.thesmallthings.hellcraft.world.Gatehouses.Tower d = server.computeOnServer(s -> {
			net.thesmallthings.hellcraft.world.Gatehouses.build(s.overworld(), "dis_0");
			return net.thesmallthings.hellcraft.world.Gatehouses.tower(s.overworld(), "dis_0", -1);
		});
		int f2 = d.floors()[Math.min(2, d.floors().length - 2)];
		double da = Math.cos(d.doorAngle());
		double db = Math.sin(d.doorAngle());
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %.2f %d %.2f facing %.2f %d %.2f", d.cx() - da * 3.0, f2 + 1, d.cz() - db * 3.0, d.cx(),
				f2, d.cz()));
		context.waitTicks(20);
		connection.waitForChunksRender();
		context.waitTicks(10);
		context.takeScreenshot("gatehouse_dis");
	}

	/** The Journal's page number of a Guide page, by its title. */
	private static int guidePage(ClientGameTestContext context, String title) {
		return context.computeOnClient(client -> {
			for (int i = 0; i < ClientNav.pages().size(); i++) {
				if (ClientNav.pages().get(i).title().equals(title)) {
					return 1 + ClientNav.codex().size() + i;
				}
			}
			return 1 + ClientNav.codex().size();
		});
	}

	/**
	 * The Labyrinth of the Minotaur's first lair (world/Labyrinth): built, its arch on the surface (labyrinth_arch); walking
	 * through the arch must take the player down into the vault (labyrinth_inside, the entrance landing looking in); the spool
	 * must lie at the heart and the vault be sealed; in the heart with the thread in hand, its red trail leads out
	 * (labyrinth_heart).
	 */
	private static void labyrinth(ClientGameTestContext context, TestDedicatedServerContext server, TestDedicatedServerConnection connection) {
		server.runCommand("gamemode spectator Player0");
		net.minecraft.core.BlockPos[] at = server.computeOnServer(s -> {
			net.thesmallthings.hellcraft.world.Labyrinth.build(s.overworld(), 0);
			return new net.minecraft.core.BlockPos[]{net.thesmallthings.hellcraft.world.Labyrinth.centre(s.overworld(), 0),
					net.thesmallthings.hellcraft.world.Labyrinth.arch(s.overworld(), 0)};
		});
		net.minecraft.core.BlockPos c = at[0];
		net.minecraft.core.BlockPos arch = at[1];
		boolean spool = server.computeOnServer(s -> s.overworld().getBlockState(net.thesmallthings.hellcraft.world.Labyrinth.spool(s.overworld(), 0))
				.is(net.minecraft.world.level.block.Blocks.LODESTONE));
		if (!spool) {
			throw new AssertionError("the Labyrinth has no spool at its heart");
		}
		net.minecraft.core.Direction out = net.thesmallthings.hellcraft.world.Labyrinth.side(0);
		// sealed: the outer wall beside the entrance door stands from the floor to the roof, and the roof is solid over the heart
		boolean sealed = server.computeOnServer(s -> {
			for (int h = 0; h <= 7; h++) {
				if (s.overworld().getBlockState(c.offset(out.getStepX() * 22, h, out.getStepZ() * 22).relative(out.getClockWise(), 3)).isAir()) {
					return false;
				}
			}
			return !s.overworld().getBlockState(c.above(7)).isAir() && !s.overworld().getBlockState(c.above(8)).isAir();
		});
		if (!sealed) {
			throw new AssertionError("the Labyrinth's vault isn't sealed");
		}
		// the arch, seen from outside
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %.1f %d %.1f facing %d %d %d", arch.getX() + out.getStepX() * 9 + 3.5, arch.getY() + 3,
				arch.getZ() + out.getStepZ() * 9 + 3.5, arch.getX(), arch.getY() + 2, arch.getZ()));
		context.waitTicks(20);
		connection.waitForChunksRender();
		context.waitTicks(20);
		context.takeScreenshot("labyrinth_arch");
		// walk through it: down into the vault
		server.runCommand("gamemode adventure Player0");
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %.1f %d %.1f", arch.getX() + 0.5, arch.getY(), arch.getZ() + 0.5));
		context.waitTicks(20);
		double y = server.computeOnServer(s -> s.getPlayerList().getPlayerByName("Player0").getY());
		if (y > c.getY() + 1) {
			throw new AssertionError("walking through the Labyrinth's arch didn't take the player down (y " + y + ")");
		}
		context.waitTicks(40);
		connection.waitForChunksRender();
		context.waitTicks(10);
		context.takeScreenshot("labyrinth_inside");
		// in the heart, with the thread: the red trail runs from here towards the Minotaur's door
		server.runCommand("gamemode spectator Player0");
		server.runCommand("give Player0 hellcraft:ariadnes_thread");
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %.1f %d %.1f facing %d %d %d", c.getX() + out.getStepX() * 3 + 0.5, c.getY() + 2,
				c.getZ() + out.getStepZ() * 3 + 0.5, c.getX() - out.getStepX() * 4, c.getY(), c.getZ() - out.getStepZ() * 4));
		context.waitTicks(30);
		connection.waitForChunksRender();
		context.waitTicks(20);
		context.takeScreenshot("labyrinth_heart");
		server.runCommand("clear Player0 hellcraft:ariadnes_thread");
	}
}
