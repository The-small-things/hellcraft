package net.thesmallthings.hellcraft.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.phys.AABB;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.client.layer.SoulFlocks;
import net.thesmallthings.hellcraft.client.nav.ClientNav;
import net.thesmallthings.hellcraft.client.nav.JournalScreen;
import net.thesmallthings.hellcraft.client.nav.LayerCard;
import net.thesmallthings.hellcraft.client.nav.MapOfHellScreen;
import net.thesmallthings.hellcraft.client.nav.NavKeys;
import net.thesmallthings.hellcraft.entity.HellEntities;
import net.thesmallthings.hellcraft.entity.boss.gen.MinosAnim;
import net.thesmallthings.hellcraft.entity.npc.Shade;
import net.thesmallthings.hellcraft.hazard.guardian.Guardian;
import net.thesmallthings.hellcraft.hazard.guardian.GuardianManager;
import net.thesmallthings.hellcraft.world.Circle;
import net.thesmallthings.hellcraft.world.Fen;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.NobleCastle;
import net.thesmallthings.hellcraft.world.Shades;
import net.thesmallthings.hellcraft.world.Sullen;
import net.thesmallthings.hellcraft.world.WindStreams;
import net.thesmallthings.hellcraft.world.Zone;
import net.thesmallthings.hellcraft.world.feature.WatchtowerFeature;

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
			nobleCastle(context, server, connection);
			setPieces(context, server, connection);
			layerRules(context, server, connection);
			minosFight(context, server, connection);
		}
	}

	/**
	 * Minos's fight in his lair, against a dummy (screenshots fight_*): his entrance, then each attack as its danger
	 * mark fills: the white cone of his claw (and his white flash), the purple ring of his tail, the red circle of a
	 * sentence and the red line of his coil.
	 */
	private static void minosFight(ClientGameTestContext context, TestDedicatedServerContext server, TestDedicatedServerConnection connection) {
		server.runCommand("gamemode spectator Player0");
		int x = Guardian.MINOS.lairColumn().getX();
		int z = Guardian.MINOS.lairColumn().getZ();
		// stand back from the lair (its floor is level), looking down at where Minos will rise and his marks will show
		server.runCommand(String.format(Locale.ROOT, "forceload add %d %d", x, z));
		int ground = server.computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %d %d %d -20 38", x - 6, ground + 13, z - 17));
		context.waitTicks(10);
		connection.waitForChunksRender();
		String out = server.computeOnServer(s -> GuardianManager.summon(s, Guardian.MINOS));
		if (!out.contains("awakens")) {
			throw new AssertionError("Minos could not be summoned: " + out);
		}
		server.runCommand(String.format(Locale.ROOT, "summon minecraft:villager %d %d %d {NoAI:1b,Invulnerable:1b,PersistenceRequired:1b,Tags:[\"fight_dummy\"]}",
				x - 2, ground, z - 6));
		context.waitTicks(MinosAnim.INTRO.at("roar") + 6);
		context.takeScreenshot("fight_intro");
		context.waitTicks(MinosAnim.INTRO.ticks - MinosAnim.INTRO.at("roar"));
		String[][] shots = {
				{"strike", String.valueOf(MinosAnim.STRIKE.at("flash") + 5)},
				{"tail", String.valueOf(MinosAnim.TAIL.at("sweep") - 6)},
				{"sentence", "30"},
				{"coil", String.valueOf(MinosAnim.GRAB.at("lunge") - 4)},
		};
		for (String[] shot : shots) {
			String used = server.computeOnServer(s -> GuardianManager.attack(Guardian.MINOS, shot[0]));
			if (!used.contains(" uses ")) {
				throw new AssertionError("Minos could not use " + shot[0] + ": " + used);
			}
			context.waitTicks(Integer.parseInt(shot[1]));
			context.takeScreenshot("fight_" + shot[0]);
			context.waitTicks(80);
		}
		server.computeOnServer(s -> GuardianManager.slay(Guardian.MINOS));
		context.waitTicks(30);
		context.takeScreenshot("fight_death");
		server.runCommand("kill @e[tag=fight_dummy]");
		// meeting him opened his Codex entry, first after "Where you are"
		if (!context.computeOnClient(client -> ClientNav.codex().stream().anyMatch(e -> e.id().equals("minos")))) {
			throw new AssertionError("meeting Minos didn't open his Codex entry");
		}
		context.setScreen(() -> JournalScreen.at(1));
		context.waitTicks(5);
		context.takeScreenshot("fight_codex");
		context.setScreen(() -> null);
	}

	/**
	 * Limbo's Noble Castle (screenshots castle_*): from the air, then its court with the shades round the mound, and a
	 * shade's trades (talking to Homer must hand over Cerberus's Codex page).
	 */
	private static void nobleCastle(ClientGameTestContext context, TestDedicatedServerContext server, TestDedicatedServerConnection connection) {
		server.runCommand("gamemode spectator Player0");
		context.runOnClient(client -> {
			if (!client.gui.hud.isHidden()) {
				client.gui.hud.toggle();
			}
		});
		int cx = NobleCastle.X;
		int cz = NobleCastle.Z;
		server.runCommand(String.format(Locale.ROOT, "forceload add %d %d %d %d", cx - 56, cz - 56, cx + 56, cz + 56));
		// the ground in the inner court, clear of the walls and the mound
		int ground = server.computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx + 3, cz - 12));
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %d %d %d facing %d %d %d", cx + 78, ground + 42, cz + 62, cx, ground, cz));
		context.waitTicks(10);
		connection.waitForChunksRender();
		context.waitTicks(30);
		context.takeScreenshot("castle_aerial");
		int shades = server.computeOnServer(s -> Shades.ensure(s.overworld(), false));
		if (shades != Shades.ALL.size()) {
			throw new AssertionError("only " + shades + " of the " + Shades.ALL.size() + " shades are in the Noble Castle");
		}
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %.1f %d %.1f facing %d %d %d", cx + 4.5, ground + 3, cz - 17.5, cx, ground + 2, cz));
		context.waitTicks(10);
		connection.waitForChunksRender();
		context.waitFor(client -> client.level.getEntitiesOfClass(Shade.class, client.player.getBoundingBox().inflate(40)).size() >= Shades.ALL.size() - 1, 200);
		context.waitTicks(40);
		context.takeScreenshot("castle_court");
		// talk to Homer: a tip, Cerberus's Codex page, and his wares
		server.runCommand("gamemode creative Player0");
		server.computeOnServer(s -> {
			Shade homer = s.overworld().getEntitiesOfClass(Shade.class, new AABB(cx - 40, -64, cz - 40, cx + 40, 320, cz + 40), sh -> sh.who() == 0).getFirst();
			ServerPlayer player = s.getPlayerList().getPlayerByName("Player0");
			player.teleportTo(homer.getX() + 2, homer.getY(), homer.getZ());
			player.lookAt(EntityAnchorArgument.Anchor.EYES, homer.getEyePosition());
			return homer.mobInteract(player, InteractionHand.MAIN_HAND);
		});
		context.waitForScreen(MerchantScreen.class);
		context.waitTicks(10);
		context.takeScreenshot("castle_trade");
		context.setScreen(() -> null);
		context.waitFor(client -> ClientNav.codex().stream().anyMatch(e -> e.id().equals("cerberus")), 100);
		server.runCommand("gamemode spectator Player0");
		server.runCommand(String.format(Locale.ROOT, "forceload remove %d %d %d %d", cx - 56, cz - 56, cx + 56, cz + 56));
		context.runOnClient(client -> {
			if (client.gui.hud.isHidden()) {
				client.gui.hud.toggle();
			}
		});
	}

	/**
	 * The upper circles' set-pieces, placed at chosen spots and photographed (screenshots piece_*): the Mire's rotting
	 * trees, bloated fungus and a sunken feast table; Greed's coin hills, a vault and the giant scales.
	 */
	private static void setPieces(ClientGameTestContext context, TestDedicatedServerContext server, TestDedicatedServerConnection connection) {
		server.runCommand("gamemode spectator Player0");
		context.runOnClient(client -> {
			if (!client.gui.hud.isHidden()) {
				client.gui.hud.toggle();
			}
		});
		piece(context, server, connection, "piece_mire", 2104, 130, new String[][]{
				{"rotting_tree", "0", "-14"}, {"rotting_tree", "-14", "6"}, {"rotting_tree", "12", "10"},
				{"bloated_fungus", "-6", "-4"}, {"bloated_fungus", "8", "-6"}, {"feast_table", "-2", "8"}});
		piece(context, server, connection, "piece_counting_house", 1800, 150, new String[][]{
				{"coin_hill", "-10", "-8"}, {"coin_hill", "10", "-12"}, {"vault", "0", "4"}, {"giant_scales", "-14", "12"}});
		context.runOnClient(client -> {
			if (client.gui.hud.isHidden()) {
				client.gui.hud.toggle();
			}
		});
	}

	/** Places features round (x, z) (each {name, dx, dz}) and photographs them from the south-east, up high. */
	private static void piece(ClientGameTestContext context, TestDedicatedServerContext server, TestDedicatedServerConnection connection, String shot,
							  int x, int z, String[][] features) {
		server.runCommand(String.format(Locale.ROOT, "forceload add %d %d %d %d", x - 40, z - 40, x + 40, z + 40));
		int ground = server.computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
		for (String[] f : features) {
			int fx = x + Integer.parseInt(f[1]);
			int fz = z + Integer.parseInt(f[2]);
			int fy = server.computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, fx, fz));
			server.runCommand(String.format(Locale.ROOT, "place feature hellcraft:%s %d %d %d", f[0], fx, fy, fz));
		}
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %d %d %d facing %d %d %d", x + 26, ground + 16, z + 30, x, ground + 3, z));
		context.waitTicks(10);
		connection.waitForChunksRender();
		context.waitTicks(30);
		context.takeScreenshot(shot);
		server.runCommand(String.format(Locale.ROOT, "forceload remove %d %d %d %d", x - 40, z - 40, x + 40, z + 40));
	}

	/**
	 * The upper circles' rules (screenshots layer_*): a wind pad in Lust and the stream rising from it, a ride on the
	 * stream (it must carry the player along), the Styx bubbling over a sullen, and Greed's hoarders drawn by a purse
	 * of gold (they must come).
	 */
	private static void layerRules(ClientGameTestContext context, TestDedicatedServerContext server, TestDedicatedServerConnection connection) {
		server.runCommand("gamemode spectator Player0");
		context.runOnClient(client -> {
			if (!client.gui.hud.isHidden()) {
				client.gui.hud.toggle();
			}
		});
		// a pad, seen from behind, with the stream rising away from it
		WindStreams.Stream stream = WindStreams.all().getFirst();
		double dx = stream.x()[12] - stream.x()[0];
		double dz = stream.z()[12] - stream.z()[0];
		double len = Math.hypot(dx, dz);
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		double camX = stream.x()[0] - dx / len * 14;
		double camZ = stream.z()[0] - dz / len * 14;
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %.1f %.1f %.1f %.1f -8", camX, stream.y()[0] + 5, camZ, yaw));
		context.waitTicks(10);
		connection.waitForChunksRender();
		context.waitTicks(40);
		context.takeScreenshot("layer_wind_pad");
		// the souls whirling in flocks over Lust: look at where one will be in a few seconds
		long now = context.computeOnClient(client -> client.level.getGameTime());
		long at = now + 100;
		double[] flock = SoulFlocks.centre(0, at, InfernoGeometry.outerRadius(Zone.LUST), InfernoGeometry.outerRadius(Zone.GLUTTONY));
		double fr = Math.hypot(flock[0], flock[2]);
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %.1f %.1f %.1f facing %.1f %.1f %.1f", flock[0] * (fr + 75) / fr, flock[1] - 25,
				flock[2] * (fr + 75) / fr, flock[0], flock[1], flock[2]));
		context.waitTicks(10);
		connection.waitForChunksRender();
		context.waitFor(client -> client.level.getGameTime() >= at, 200);
		context.takeScreenshot("layer_lust_flock");
		// step into the stream: it carries you
		int from = 8;
		server.runCommand("gamemode creative Player0");
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %.2f %.2f %.2f %.1f 10", stream.x()[from], stream.y()[from] - 0.9, stream.z()[from], yaw));
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.waitTicks(40);
		context.takeScreenshot("layer_wind_ride");
		int reached = context.computeOnClient(client -> {
			WindStreams.Hit hit = WindStreams.find(client.player.getX(), client.player.getY() + 0.9, client.player.getZ());
			return hit == null ? -1 : hit.index();
		});
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		if (reached < from + 8) {
			throw new AssertionError("the wind didn't carry the player: from point " + from + " to " + reached + " in two seconds");
		}
		// the Styx, bubbling where a sullen lurks
		server.runCommand("gamemode spectator Player0");
		int[] spot = null;
		for (int x = 1450; x < 1640 && spot == null; x++) {
			int[] s = Sullen.spot(x, 60);
			if (s != null && s[0] > 1460) {
				spot = s;
			}
		}
		if (spot == null) {
			throw new AssertionError("no sullen on a line across the Styx");
		}
		// one in open water, with no tree over it
		for (int x = spot[0] + 1; x < 1640; x++) {
			int[] s = Sullen.spot(x, 60);
			if (s != null && server.computeOnServer(sv -> {
				sv.overworld().getChunk(s[0] >> 4, s[1] >> 4);
				return sv.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, s[0], s[1]) <= InfernoGeometry.fluidLevel(s[0], s[1]);
			})) {
				spot = s;
				break;
			}
		}
		int surface = InfernoGeometry.fluidLevel(spot[0], spot[1]);
		server.runCommand("effect give Player0 minecraft:night_vision 30 0 true");
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air replace #minecraft:leaves", spot[0] - 6, surface, spot[1] - 10,
				spot[0] + 6, surface + 12, spot[1] + 3));
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %.1f %d %.1f 0 50", spot[0] + 0.5, surface + 5, spot[1] - 4.5));
		context.waitTicks(10);
		connection.waitForChunksRender();
		context.waitTicks(30);
		context.takeScreenshot("layer_styx_bubbles");
		// a watchtower on the Fen's shore by the east road, and Dis glowing red through the fog beyond it
		int[] tower = Fen.tower(0);
		int towerBase = WatchtowerFeature.base(tower);
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %d %d %d facing %d %d %d", tower[0] + 16, towerBase + 8, tower[1] + 14,
				tower[0], towerBase + 10, tower[1]));
		context.waitTicks(10);
		connection.waitForChunksRender();
		context.waitTicks(30);
		context.takeScreenshot("layer_fen_watchtower");
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %d %d %d 90 -4", tower[0] - 20, surface + 3, tower[1] + 30));
		context.waitTicks(10);
		connection.waitForChunksRender();
		context.waitTicks(20);
		context.takeScreenshot("layer_fen_dis");
		// Greed: a purse of gold draws hoarders
		int gx = 1800;
		int gz = 60;
		server.runCommand(String.format(Locale.ROOT, "forceload add %d %d", gx, gz));
		int ground = server.computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz));
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %d %d %d 0 20", gx, ground, gz));
		server.runCommand("gamemode survival Player0");
		server.runCommand("effect give Player0 minecraft:resistance 60 4 true");
		server.runCommand("give Player0 minecraft:gold_ingot 64");
		context.waitTicks(10);
		connection.waitForChunksRender();
		boolean drawn = false;
		for (int i = 0; i < 30 && !drawn; i++) {
			context.waitTicks(20);
			drawn = server.computeOnServer(s -> !s.overworld().getEntities(HellEntities.HOARDER, h -> true).isEmpty());
		}
		if (!drawn) {
			throw new AssertionError("a purse of gold in Greed drew no hoarders");
		}
		// watch it come
		int hz = gz + 5;
		int hy = server.computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, hz));
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %d.5 %d %d.5 0 15", gx, ground, gz));
		server.runCommand(String.format(Locale.ROOT, "execute at Player0 run tp @e[type=hellcraft:hoarder,limit=1,sort=nearest] %d.5 %d %d.5 facing entity Player0",
				gx, hy, hz));
		context.waitTicks(15);
		context.takeScreenshot("layer_hoarder");
		server.runCommand("clear Player0 minecraft:gold_ingot");
		server.runCommand("kill @e[type=hellcraft:hoarder]");
		context.waitTicks(10);
		context.takeScreenshot("layer_hoarder_death");
		server.runCommand("gamemode spectator Player0");
		server.runCommand("effect clear Player0");
		server.runCommand(String.format(Locale.ROOT, "forceload remove %d %d", gx, gz));
		context.runOnClient(client -> {
			if (client.gui.hud.isHidden()) {
				client.gui.hud.toggle();
			}
		});
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
		// /guide opens the Journal too (the written guide book is gone)
		server.runCommand("execute as Player0 run guide");
		context.waitForScreen(JournalScreen.class);
		context.setScreen(() -> null);
	}
}
