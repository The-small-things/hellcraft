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
import net.thesmallthings.hellcraft.world.InfernoGeometry;

import java.util.Locale;
import java.util.Properties;

/**
 * An Inferno world on a dedicated server, seen from a spectator's camera at places that show how the map
 * leads you down: the Gate of Hell at spawn, the roads, the Walls of Dis, Malebolge's bridges and a way down
 * off the road (screenshots map_*).
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
		}
	}
}
