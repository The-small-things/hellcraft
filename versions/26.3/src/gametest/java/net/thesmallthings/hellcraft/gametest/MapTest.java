package net.thesmallthings.hellcraft.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.client.nav.ClientNav;
import net.thesmallthings.hellcraft.client.nav.JournalScreen;
import net.thesmallthings.hellcraft.client.nav.LayerCard;
import net.thesmallthings.hellcraft.client.nav.MapOfHellScreen;
import net.thesmallthings.hellcraft.client.nav.NavKeys;
import net.thesmallthings.hellcraft.world.Circle;
import net.thesmallthings.hellcraft.world.InfernoGeometry;

import java.util.Locale;
import java.util.Properties;

/**
 * An Inferno world on a dedicated server, seen from a spectator's camera at places that show how the map
 * leads you down: the Gate of Hell at spawn, the roads, the Walls of Dis, Malebolge's bridges and a way down
 * off the road (screenshots map_*). Then the circle card, the compass, the Map of Hell and the Journal (nav_*).
 */
public class MapTest implements FabricClientGameTest {
	/** A camera position: where, and which way it looks (Minecraft yaw: 0 south, 90 west, 180 north, -90 east). */
	private record Shot(String name, double x, double y, double z, float yaw, float pitch) {
	}

	/** Chooses the Inferno world type, as a player would on the World tab. */
	static void inferno(WorldCreationUiState ui) {
		HolderLookup.RegistryLookup<WorldPreset> presets = ui.getSettings().worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET);
		ui.setWorldType(new WorldCreationUiState.WorldTypeEntry(presets.getOrThrow(ResourceKey.create(Registries.WORLD_PRESET, HellcraftMod.id("inferno")))));
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		Properties properties = new Properties();
		properties.setProperty("view-distance", "8");
		properties.setProperty("simulation-distance", "4");
		int gate = InfernoGeometry.gateX();
		double way = Math.PI / 6;
		Shot[] shots = {
				new Shot("map_gate_of_hell", gate + 45, 205, 6, 90, 22),
				new Shot("map_road_lust", 2480, 150, 6, 90, 28),
				new Shot("map_walls_of_dis", 1470, 95, 8, 90, 22),
				new Shot("map_malebolge_bridge", 700, 45, 6, 90, 30),
				new Shot("map_way_down_greed", Math.cos(way) * 1740, 112, Math.sin(way) * 1740, 120, 30),
		};
		// the test world is made on the client first, so the world type is chosen there, not in server.properties
		try (TestDedicatedServerContext server = context.worldBuilder().adjustSettings(MapTest::inferno).createServer(properties);
			 TestDedicatedServerConnection connection = server.connect()) {
			connection.waitForChunksRender();
			server.runCommand("gamemode spectator Player0");
			context.runOnClient(client -> {
				if (!client.gui.hud.isHidden()) {
					client.gui.hud.toggle();
				}
			});
			for (Shot shot : shots) {
				server.runCommand(String.format(Locale.ROOT, "tp Player0 %.1f %.1f %.1f %.1f %.1f", shot.x(), shot.y(), shot.z(), shot.yaw(), shot.pitch()));
				context.waitTicks(10);
				connection.waitForChunksRender();
				context.waitTicks(20);
				context.takeScreenshot(shot.name());
			}
			context.runOnClient(client -> {
				if (client.gui.hud.isHidden()) {
					client.gui.hud.toggle();
				}
			});
			findingTheWay(context, server, connection);
		}
	}

	/**
	 * The navigation HUD and screens (screenshots nav_*): Gluttony's card and the compass on the east road, the
	 * Map of Hell (opened with its key, then zoomed on the east road) and the Journal.
	 */
	private static void findingTheWay(ClientGameTestContext context, TestDedicatedServerContext server, TestDedicatedServerConnection connection) {
		if (!context.computeOnClient(client -> ClientNav.inferno() && !ClientNav.pages().isEmpty())) {
			throw new AssertionError("the server never sent the map state and the Guide's pages");
		}
		server.runCommand("gamemode creative Player0");
		double x = 2160;
		double y = InfernoGeometry.baseHeight(x, 6) + 2;
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %.1f %.1f 6.5 90 8", x, y));
		context.waitTicks(10);
		connection.waitForChunksRender();
		context.waitFor(client -> LayerCard.showing() == Circle.GLUTTONY, 100);
		context.waitTicks(30);
		context.takeScreenshot("nav_card_gluttony");
		// the card fades after about seven seconds, leaving the compass
		context.waitFor(client -> LayerCard.showing() == null, 300);
		context.waitTicks(5);
		context.takeScreenshot("nav_compass");

		context.getInput().pressKey(NavKeys.MAP);
		context.waitForScreen(MapOfHellScreen.class);
		// the mouse off the map (opening a screen centres it), so no tooltip covers it
		context.getInput().setCursorPos(2, 2);
		context.waitTicks(40);
		context.takeScreenshot("nav_map");
		context.setScreen(() -> MapOfHellScreen.at(2000, 0, 5));
		context.getInput().setCursorPos(2, 2);
		context.waitTicks(10);
		context.takeScreenshot("nav_map_east_road");
		context.setScreen(() -> null);

		context.getInput().pressKey(NavKeys.JOURNAL);
		context.waitForScreen(JournalScreen.class);
		context.waitTicks(5);
		context.takeScreenshot("nav_journal");
		int page = context.computeOnClient(client -> {
			for (int i = 0; i < ClientNav.pages().size(); i++) {
				if (ClientNav.pages().get(i).title().equals("FINDING THE WAY")) {
					return i + 1;
				}
			}
			throw new AssertionError("no FINDING THE WAY page in the Journal");
		});
		context.setScreen(() -> JournalScreen.at(page));
		context.waitTicks(5);
		context.takeScreenshot("nav_journal_guide");
		context.setScreen(() -> null);
	}
}
