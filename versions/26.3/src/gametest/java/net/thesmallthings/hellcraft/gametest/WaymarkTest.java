package net.thesmallthings.hellcraft.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.client.nav.ClientNav;
import net.thesmallthings.hellcraft.client.nav.ClientWaymarks;
import net.thesmallthings.hellcraft.client.nav.JournalScreen;
import net.thesmallthings.hellcraft.client.nav.MapOfHellScreen;
import net.thesmallthings.hellcraft.client.nav.MapTiles;
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
			// the ground seen round Player0 is drawn in its real blocks (MapTiles)
			context.waitFor(client -> MapTiles.seen((int) x, 6), 400);
			context.waitTicks(100);
			context.setScreen(() -> MapOfHellScreen.at(x, 6, 30));
			context.getInput().setCursorPos(2, 2);
			context.waitTicks(10);
			context.takeScreenshot("map_explored");
			context.setScreen(() -> MapOfHellScreen.at(x, 6, 10));
			context.getInput().setCursorPos(2, 2);
			context.waitTicks(10);
			context.takeScreenshot("map_explored_far");
			context.setScreen(() -> MapOfHellScreen.at(x - 150, 0, 4));
			context.getInput().setCursorPos(2, 2);
			context.waitTicks(10);
			context.takeScreenshot("waymark_map");
			context.setScreen(() -> MapOfHellScreen.at(0, 0, 1));
			context.getInput().setCursorPos(2, 2);
			context.waitTicks(10);
			context.takeScreenshot("waymark_map_whole");
			mapMemory(context);
			context.setScreen(() -> null);
			server.runCommand(String.format(Locale.ROOT, "tp Player0 %.1f %.1f 6.5 120 8", x, y));
			context.waitTicks(20);
			context.takeScreenshot("waymark_compass");
			labyrinth(context, server, connection);
			gatehouses(context, server, connection);
			wallRooms(context, server, connection);
			roomKinds(context, server, connection);
			// first steps: what Virgil has taught Player0 by now (a new soul here)
			String taught = server.computeOnServer(s -> String.join(",", net.thesmallthings.hellcraft.blood.HellState.get(s)
					.existing(s.getPlayerList().getPlayerByName("Player0").getUUID()).lessons));
			System.out.println("Lessons taught to Player0: " + taught);
		}
	}

	/**
	 * The gatehouse towers (world/Gatehouses) at the spawn gate of the Gate of Hell and at a gate of Dis: from outside
	 * (gatehouse_outside), the guard room (gatehouse_guard), the stair climbing past a floor (gatehouse_stair), the walk over
	 * the gate (gatehouse_walk), a room in Dis (gatehouse_dis) and a door onto the wall on a tower's far side (gatehouse_far_door).
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
		// a tower whose stair left no room for a door onto the walk over its gate: its door onto the wall on the far side,
		// and the walk levelled along the wall's top from it (gatehouse_far_door)
		net.thesmallthings.hellcraft.world.Gatehouses.Tower far = server.computeOnServer(s -> {
			for (String id : net.thesmallthings.hellcraft.world.Gatehouses.ids()) {
				for (int side = -1; side <= 1; side += 2) {
					net.thesmallthings.hellcraft.world.Gatehouses.Tower c = net.thesmallthings.hellcraft.world.Gatehouses.tower(s.overworld(), id, side);
					if (c.farDoor()) {
						net.thesmallthings.hellcraft.world.Gatehouses.build(s.overworld(), id);
						return c;
					}
				}
			}
			return null;
		});
		if (far == null) {
			System.out.println("Gatehouse far door: no tower in this world needs one");
			return;
		}
		double fa = Math.cos(far.walkDoorAngle());
		double fb = Math.sin(far.walkDoorAngle());
		System.out.println("Gatehouse far door: " + far.site() + " at " + far.centre().toShortString());
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %.2f %d %.2f facing %.2f %d %.2f", far.cx() + fa * 14, far.walk() + 2, far.cz() + fb * 14,
				far.cx(), far.walk() + 1, far.cz()));
		context.waitTicks(20);
		connection.waitForChunksRender();
		context.waitTicks(10);
		context.takeScreenshot("gatehouse_far_door");
	}

	/**
	 * A stretch of the Gate of Hell's wall lived in (world/WallRooms): seven bays round a watch tower, built; the tower from Hell's
	 * side (wall_outside), the walk along the wall's top (wall_walk), the tower's stair (wall_stair) and its open top
	 * (wall_tower_top), a room at the foot of the wall (wall_room) and one above it (wall_room_upper). The tower must have its
	 * stair, and the walk must be clear.
	 */
	private static void wallRooms(ClientGameTestContext context, TestDedicatedServerContext server, TestDedicatedServerConnection connection) {
		server.runCommand("gamemode spectator Player0");
		net.thesmallthings.hellcraft.world.WallRooms.Wall gate = net.thesmallthings.hellcraft.world.WallRooms.Wall.GATE;
		net.thesmallthings.hellcraft.world.WallRooms.Bay tower = net.thesmallthings.hellcraft.world.WallRooms.bay(gate, 320);
		net.thesmallthings.hellcraft.world.WallRooms.Bay walk = net.thesmallthings.hellcraft.world.WallRooms.bay(gate, 321);
		net.thesmallthings.hellcraft.world.WallRooms.Bay room = net.thesmallthings.hellcraft.world.WallRooms.bay(gate, 322);
		net.thesmallthings.hellcraft.world.WallRooms.Built[] built = server.computeOnServer(s -> {
			net.thesmallthings.hellcraft.world.WallRooms.Built[] out = new net.thesmallthings.hellcraft.world.WallRooms.Built[7];
			for (int k = 317; k <= 323; k++) {
				out[k - 317] = net.thesmallthings.hellcraft.world.WallRooms.build(s.overworld(), net.thesmallthings.hellcraft.world.WallRooms.bay(gate, k));
			}
			return out;
		});
		net.thesmallthings.hellcraft.world.WallRooms.Built t = built[3];
		net.thesmallthings.hellcraft.world.WallRooms.Built r = built[5];
		if (t == null || r == null) {
			throw new AssertionError("the watch tower or the wall room beside it wasn't built");
		}
		System.out.println("Wall rooms: tower floors " + java.util.Arrays.toString(t.floors()) + ", room floors " + java.util.Arrays.toString(r.floors()));
		net.thesmallthings.hellcraft.world.Rooms.Frame tf = tower.frame();
		boolean stairs = server.computeOnServer(s -> {
			int found = 0;
			for (int a = 1; a <= 4; a++) {
				for (int b = 1; b <= 4; b++) {
					for (int y = t.ground(); y < t.ground() + 8; y++) {
						found += s.overworld().getBlockState(tf.at(y, a, b)).getBlock() instanceof net.minecraft.world.level.block.StairBlock ? 1 : 0;
					}
				}
			}
			return found >= 4;
		});
		if (!stairs) {
			throw new AssertionError("the watch tower has no stair");
		}
		net.thesmallthings.hellcraft.world.Rooms.Frame wf = walk.frame();
		boolean clear = server.computeOnServer(s -> s.overworld().getBlockState(wf.at(built[4] == null ? 0 : built[4].roof() + 1, 0, 0)).isAir());
		if (built[4] != null && !clear) {
			throw new AssertionError("the walk on the wall's top isn't clear");
		}
		// the tower, from Hell's side
		stand(server, tf.at(t.ground() + 6, 0, -26), tf.at(t.roof() + 2, 0, 0));
		context.waitTicks(20);
		connection.waitForChunksRender();
		context.waitTicks(20);
		context.takeScreenshot("wall_outside");
		// along the walk, toward the tower
		if (built[4] != null) {
			stand(server, wf.at(built[4].roof() + 1, 3, 0), tf.at(t.roof() + 3, 0, 0));
			context.waitTicks(20);
			connection.waitForChunksRender();
			context.waitTicks(10);
			context.takeScreenshot("wall_walk");
		}
		// the tower's stair, from its door
		stand(server, tf.at(t.ground() + 1, -2, -3), tf.at(t.ground() + 3, 3, 3));
		context.waitTicks(20);
		connection.waitForChunksRender();
		context.waitTicks(10);
		context.takeScreenshot("wall_stair");
		// its open top, looking out over Hell
		int platform = t.floors()[t.floors().length - 1];
		stand(server, tf.at(platform + 1, 3, 2), tf.at(platform, -4, -4));
		context.waitTicks(20);
		connection.waitForChunksRender();
		context.waitTicks(10);
		context.takeScreenshot("wall_tower_top");
		// a room at the foot of the wall, from the end by its stair, and one above
		net.thesmallthings.hellcraft.world.Rooms.Frame rf = room.frame();
		int far = -2 * room.side();
		stand(server, rf.at(r.ground() + 1, -far, -2), rf.at(r.ground() + 1, far, 1));
		context.waitTicks(20);
		connection.waitForChunksRender();
		context.waitTicks(10);
		context.takeScreenshot("wall_room");
		if (r.floors().length > 1) {
			stand(server, rf.at(r.floors()[1] + 1, -far, -2), rf.at(r.floors()[1] + 1, far, 1));
			context.waitTicks(20);
			context.takeScreenshot("wall_room_upper");
		}
	}

	/**
	 * One room of every kind (world/Rooms), side by side high in the air with their fronts open and lit from above, filmed from
	 * the front with the HUD hidden (room_<kind>). In the Gate of Hell's dark oak; Dis's rooms are the same in crimson.
	 */
	private static void roomKinds(ClientGameTestContext context, TestDedicatedServerContext server, TestDedicatedServerConnection connection) {
		server.runCommand("gamemode spectator Player0");
		net.thesmallthings.hellcraft.world.Rooms.Kind[] kinds = net.thesmallthings.hellcraft.world.Rooms.Kind.values();
		int y = 230;
		int z = 1000;
		int x0 = 1000;
		server.computeOnServer(s -> {
			net.minecraft.server.level.ServerLevel level = s.overworld();
			net.minecraft.world.level.block.state.BlockState wall = net.minecraft.world.level.block.Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
			net.minecraft.world.level.block.state.BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
			for (int i = 0; i < kinds.length; i++) {
				int cx = x0 + i * 12;
				for (int dx = -4; dx <= 4; dx++) {
					for (int dz = -3; dz <= 4; dz++) {
						boolean edge = Math.abs(dx) == 4 || dz == -3 || dz == 4;
						level.setBlock(new net.minecraft.core.BlockPos(cx + dx, y - 1, z + dz), edge ? wall
								: net.minecraft.world.level.block.Blocks.DARK_OAK_PLANKS.defaultBlockState(), 2);
						for (int h = 0; h <= 5; h++) {
							level.setBlock(new net.minecraft.core.BlockPos(cx + dx, y + h, z + dz), air, 2);
						}
						if (edge && dz != -3) {
							// a low wall round the back and sides; the front stays open to the camera
							level.setBlock(new net.minecraft.core.BlockPos(cx + dx, y, z + dz), wall, 2);
						}
						level.setBlock(new net.minecraft.core.BlockPos(cx + dx, y + 5, z + dz), net.minecraft.world.level.block.Blocks.LIGHT.defaultBlockState(), 2);
					}
				}
				net.thesmallthings.hellcraft.world.Rooms.Frame f = net.thesmallthings.hellcraft.world.Rooms.Frame.axes(cx + 0.5, z + 0.5);
				net.thesmallthings.hellcraft.world.Rooms.carpet(level, f, net.thesmallthings.hellcraft.world.Rooms.Shape.square(3, 2), y, false);
				net.thesmallthings.hellcraft.world.Rooms.furnish(level, f, kinds[i], y, false, net.minecraft.util.RandomSource.create(i), p -> true);
			}
			return true;
		});
		context.runOnClient(client -> {
			if (!client.gui.hud.isHidden()) {
				client.gui.hud.toggle();
			}
		});
		for (int i = 0; i < kinds.length; i++) {
			int cx = x0 + i * 12;
			server.runCommand(String.format(Locale.ROOT, "tp Player0 %.1f %d %.1f facing %.1f %.1f %.1f", cx + 0.5, y + 4, z - 5.5, cx + 0.5, y + 0.5,
					z + 1.0));
			context.waitTicks(15);
			connection.waitForChunksRender();
			context.runOnClient(client -> {
				net.thesmallthings.hellcraft.client.dialogue.DialogueHud.reset();
				net.thesmallthings.hellcraft.client.cutscene.CutscenePlayer.reset();
			});
			context.waitTicks(5);
			context.takeScreenshot("room_" + kinds[i].name().toLowerCase(Locale.ROOT));
		}
		context.runOnClient(client -> {
			if (client.gui.hud.isHidden()) {
				client.gui.hud.toggle();
			}
		});
	}

	/** Teleports Player0 to stand at a block, facing another. */
	/**
	 * The map stays light however much has been explored (MapTiles): the unexplored map is painted sharp once zoomed in
	 * (map_unexplored_zoom); then 400 tiles are marked explored as if the whole middle of Hell had been walked, and the
	 * detail kept in memory must stay under its cap while the map shows them all from afar (map_overview, the overview)
	 * and close to (map_detail, a block a pixel, read back from disk).
	 */
	private static void mapMemory(ClientGameTestContext context) {
		double x = InfernoGeometry.scaled(2160) + 700;
		context.setScreen(() -> MapOfHellScreen.at(x, 400, 30));
		context.getInput().setCursorPos(2, 2);
		context.waitFor(client -> MapOfHellScreen.sharp(), 400);
		context.waitTicks(5);
		context.takeScreenshot("map_unexplored_zoom");
		int before = context.computeOnClient(client -> MapTiles.explored());
		context.runOnClient(client -> MapTiles.pretendExplored(-10, -10, 9, 9));
		context.waitTicks(10);
		int[] counts = context.computeOnClient(client -> new int[]{MapTiles.explored(), MapTiles.loaded(), MapTiles.cap()});
		// (the 400 tiles of the square are all explored now, some of them perhaps already by the tests before)
		if (counts[0] < Math.max(before, 400) || counts[1] > counts[2]) {
			throw new AssertionError("after exploring a square of 400 tiles: " + counts[0] + " explored (from " + before + "), " + counts[1]
					+ " in memory, cap " + counts[2]);
		}
		context.setScreen(() -> MapOfHellScreen.at(0, 0, 1));
		context.getInput().setCursorPos(2, 2);
		context.waitTicks(20);
		context.takeScreenshot("map_overview");
		context.setScreen(() -> MapOfHellScreen.at(-700, -400, 12));
		context.getInput().setCursorPos(2, 2);
		// the tiles in view come back from disk in the background
		context.waitFor(client -> MapTiles.loaded() >= 20, 400);
		context.waitTicks(20);
		context.takeScreenshot("map_detail");
		counts = context.computeOnClient(client -> new int[]{MapTiles.explored(), MapTiles.loaded(), MapTiles.cap()});
		if (counts[1] > counts[2]) {
			throw new AssertionError("with the map zoomed in: " + counts[1] + " tiles in memory, cap " + counts[2]);
		}
		HellcraftMod.LOGGER.info("Map memory: {} tiles explored, {} in memory (cap {})", counts[0], counts[1], counts[2]);
		// the made-up ground goes again, so it isn't on the map in the tests after
		context.setScreen(() -> null);
		context.runOnClient(MapTiles::forgetAll);
		context.waitFor(client -> MapTiles.isOpen() && MapTiles.explored() < 100, 400);
	}

	private static void stand(TestDedicatedServerContext server, net.minecraft.core.BlockPos at, net.minecraft.core.BlockPos toward) {
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %.2f %d %.2f facing %.2f %.2f %.2f", at.getX() + 0.5, at.getY(), at.getZ() + 0.5,
				toward.getX() + 0.5, toward.getY() + 0.5, toward.getZ() + 0.5));
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
