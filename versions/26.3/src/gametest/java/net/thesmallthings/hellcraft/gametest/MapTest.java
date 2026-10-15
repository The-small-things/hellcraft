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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.phys.AABB;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.client.dialogue.DialogueHud;
import net.thesmallthings.hellcraft.client.layer.SoulFlocks;
import net.thesmallthings.hellcraft.client.nav.ClientNav;
import net.thesmallthings.hellcraft.client.nav.JournalScreen;
import net.thesmallthings.hellcraft.client.nav.LayerCard;
import net.thesmallthings.hellcraft.client.nav.MapOfHellScreen;
import net.thesmallthings.hellcraft.client.nav.NavKeys;
import net.thesmallthings.hellcraft.entity.Ferry;
import net.thesmallthings.hellcraft.entity.HellEntities;
import net.thesmallthings.hellcraft.entity.boss.gen.CerberusAnim;
import net.thesmallthings.hellcraft.entity.boss.gen.MinosAnim;
import net.thesmallthings.hellcraft.entity.boss.gen.PhlegyasAnim;
import net.thesmallthings.hellcraft.entity.boss.gen.PlutusAnim;
import net.thesmallthings.hellcraft.entity.npc.Shade;
import net.thesmallthings.hellcraft.hazard.LowerMonsters;
import net.thesmallthings.hellcraft.hazard.LowerRules;
import net.thesmallthings.hellcraft.hazard.guardian.Guardian;
import net.thesmallthings.hellcraft.hazard.guardian.GuardianManager;
import net.thesmallthings.hellcraft.world.Circle;
import net.thesmallthings.hellcraft.world.Fen;
import net.thesmallthings.hellcraft.world.FraudSites;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.NobleCastle;
import net.thesmallthings.hellcraft.world.Shades;
import net.thesmallthings.hellcraft.world.Sullen;
import net.thesmallthings.hellcraft.world.Tombs;
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
				// the east Spine over Cocytus, sagging and broken (Spine.bend)
				new Shot("map_spine", 345, 10, 32, 110, 28),
				new Shot("map_spine_above", 330, 70, 0, 90, 62),
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
			dialogueBox(context, server);
			nobleCastle(context, server, connection);
			setPieces(context, server, connection);
			layerRules(context, server, connection);
			lowerRules(context, server, connection);
			minosFight(context, server, connection);
			cerberusFight(context, server, connection);
			plutusFight(context, server, connection);
			phlegyasFight(context, server, connection);
		}
	}

	/**
	 * Minos's fight in his lair, against a dummy (screenshots fight_*): his entrance, then each attack as its danger
	 * mark fills: the white cone of his claw (and his white flash), the purple ring of his tail, the red circle of a
	 * sentence and the red line of his coil.
	 */
	private static void minosFight(ClientGameTestContext context, TestDedicatedServerContext server, TestDedicatedServerConnection connection) {
		guardianFight(context, server, connection, Guardian.MINOS, "fight_", MinosAnim.INTRO.at("roar"), MinosAnim.INTRO.ticks, new String[][]{
				{"strike", String.valueOf(MinosAnim.STRIKE.at("flash") + 5)},
				{"tail", String.valueOf(MinosAnim.TAIL.at("sweep") - 6)},
				{"sentence", "30"},
				{"coil", String.valueOf(MinosAnim.GRAB.at("lunge") - 4)},
		});
		context.setScreen(() -> JournalScreen.at(1));
		context.waitTicks(5);
		context.takeScreenshot("fight_codex");
		context.setScreen(() -> null);
	}

	/**
	 * Cerberus's fight in his lair (screenshots cerberus_fight_*): his entrance out of the mud, then a bite (white),
	 * Three Maws (a red cone), Filth (red circles) and his Howl (a purple ring), each as its mark fills.
	 */
	private static void cerberusFight(ClientGameTestContext context, TestDedicatedServerContext server, TestDedicatedServerConnection connection) {
		guardianFight(context, server, connection, Guardian.CERBERUS, "cerberus_fight_", CerberusAnim.INTRO.at("roar"), CerberusAnim.INTRO.ticks,
				new String[][]{
						{"bite", String.valueOf(CerberusAnim.BITE_MID.at("flash") + 4)},
						{"maws", String.valueOf(CerberusAnim.MAWS.at("maw1") - 6)},
						{"filth", String.valueOf(CerberusAnim.FILTH.at("spew") + 6)},
						{"howl", String.valueOf(CerberusAnim.HOWL.at("howl") - 6)},
				});
	}

	/**
	 * Plutus's fight in the Mint (screenshots plutus_fight_*): his entrance, his fist (white), the gold rain (red
	 * circles), his lunge (a red line) and Pape Satàn (a purple ring), then the great coins of his fury.
	 */
	private static void plutusFight(ClientGameTestContext context, TestDedicatedServerContext server, TestDedicatedServerConnection connection) {
		guardianFight(context, server, connection, Guardian.PLUTUS, "plutus_fight_", PlutusAnim.INTRO.at("roar"), PlutusAnim.INTRO.ticks,
				new String[][]{
						{"fist", String.valueOf(PlutusAnim.FIST.at("flash") + 4)},
						{"gold", String.valueOf(PlutusAnim.GOLD.at("rain") - 8)},
						{"lunge", String.valueOf(PlutusAnim.LUNGE.at("leap") - 4)},
						{"pape", String.valueOf(PlutusAnim.PAPE.at("shriek") - 6)},
						{"rage", String.valueOf(PlutusAnim.PHASE.at("roar") + 6)},
						{"coins", "40"},
				});
	}

	/**
	 * Phlegyas's fight on his landing in the Fen (screenshots phlegyas_fight_*): his rise from the pool, the oar (a
	 * white line), the sweep (a purple ring), the wave (a red ring) and the grab (a red circle), his fury and the
	 * sullen's hands. Beaten, he leaves his ferry at the landing: ride it to the Walls of Dis (ferry_*), which must set
	 * the player down on the east road by the gate.
	 */
	private static void phlegyasFight(ClientGameTestContext context, TestDedicatedServerContext server, TestDedicatedServerConnection connection) {
		guardianFight(context, server, connection, Guardian.PHLEGYAS, "phlegyas_fight_", PhlegyasAnim.INTRO.at("roar"), PhlegyasAnim.INTRO.ticks,
				new String[][]{
						{"oar", String.valueOf(PhlegyasAnim.OAR.at("flash") + 4)},
						{"sweep", String.valueOf(PhlegyasAnim.SWEEP.at("sweep") - 6)},
						{"wave", String.valueOf(PhlegyasAnim.WAVE.at("slam"))},
						{"grab", String.valueOf(PhlegyasAnim.GRAB.at("grab") - 8)},
						{"rage", String.valueOf(PhlegyasAnim.PHASE.at("roar") + 6)},
						{"hands", "24"},
				});
		// his ferry waits at the landing: board it
		int lx = Fen.LANDING_X;
		int lz = Fen.LANDING_Z;
		server.runCommand(String.format(Locale.ROOT, "forceload add %d %d %d %d", lx - 40, lz - 40, lx + 40, lz + 40));
		context.waitTicks(20);
		boolean docked = server.computeOnServer(s -> !s.overworld().getEntities(HellEntities.FERRY, f -> true).isEmpty());
		if (!docked) {
			throw new AssertionError("beaten, Phlegyas left no ferry at his landing");
		}
		server.runCommand("gamemode survival Player0");
		server.runCommand("effect give Player0 minecraft:resistance 120 4 true");
		server.computeOnServer(s -> {
			Ferry ferry = s.overworld().getEntities(HellEntities.FERRY, f -> true).getFirst();
			ServerPlayer player = s.getPlayerList().getPlayerByName("Player0");
			player.teleportTo(ferry.getX(), ferry.getY() + 1, ferry.getZ());
			return ferry.interact(player, InteractionHand.MAIN_HAND, ferry.position());
		});
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.waitTicks(200);
		connection.waitForChunksRender();
		context.takeScreenshot("ferry_ride");
		double wall = InfernoGeometry.outerRadius(Zone.WALLS_OF_DIS);
		context.waitFor(client -> !client.player.isPassenger() && Math.hypot(client.player.getX() - (wall + 6), client.player.getZ()) < 6, 900);
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		server.runCommand("execute as Player0 at @s run tp @s ~ ~ ~ 90 5");
		context.waitTicks(10);
		connection.waitForChunksRender();
		context.waitTicks(20);
		context.takeScreenshot("ferry_arrived");
		server.runCommand("effect clear Player0");
		server.runCommand("gamemode spectator Player0");
		server.runCommand(String.format(Locale.ROOT, "forceload remove %d %d %d %d", lx - 40, lz - 40, lx + 40, lz + 40));
	}

	/**
	 * A guardian's fight in its lair against a dummy: its entrance (shot at the roar), each attack after the given
	 * number of ticks, and its death; meeting it must open its Codex entry.
	 */
	private static void guardianFight(ClientGameTestContext context, TestDedicatedServerContext server, TestDedicatedServerConnection connection,
									  Guardian guardian, String prefix, int roar, int introTicks, String[][] shots) {
		server.runCommand("gamemode spectator Player0");
		int x = guardian.lairColumn().getX();
		int z = guardian.lairColumn().getZ();
		// stand back from the lair (its floor is level), looking down at where it will rise and its marks will show
		server.runCommand(String.format(Locale.ROOT, "forceload add %d %d", x, z));
		int ground = server.computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %d %d %d -20 38", x - 6, ground + 13, z - 17));
		context.waitTicks(10);
		connection.waitForChunksRender();
		String out = server.computeOnServer(s -> GuardianManager.summon(s, guardian));
		if (!out.contains("awakens")) {
			throw new AssertionError(guardian.id() + " could not be summoned: " + out);
		}
		server.runCommand(String.format(Locale.ROOT, "summon minecraft:villager %d %d %d {NoAI:1b,Invulnerable:1b,PersistenceRequired:1b,Tags:[\"fight_dummy\"]}",
				x - 2, ground, z - 6));
		context.waitTicks(roar + 6);
		context.takeScreenshot(prefix + "intro");
		context.waitTicks(introTicks - roar);
		for (String[] shot : shots) {
			if (shot[0].equals("rage")) {
				// bring it to under half its health: it turns to its fury
				server.computeOnServer(s -> {
					LivingEntity body = s.overworld().getEntitiesOfClass(LivingEntity.class, new AABB(x - 40, -64, z - 40, x + 40, 320, z + 40),
							e -> e.entityTags().contains("hellcraft_guardian_body")).getFirst();
					body.setHealth(body.getMaxHealth() * 0.45f);
					return true;
				});
				context.waitTicks(Integer.parseInt(shot[1]));
				context.takeScreenshot(prefix + "rage");
				context.waitTicks(40);
				continue;
			}
			String used = server.computeOnServer(s -> GuardianManager.attack(guardian, shot[0]));
			if (!used.contains(" uses ")) {
				throw new AssertionError(guardian.id() + " could not use " + shot[0] + ": " + used);
			}
			context.waitTicks(Integer.parseInt(shot[1]));
			context.takeScreenshot(prefix + shot[0]);
			context.waitTicks(80);
		}
		server.computeOnServer(s -> GuardianManager.slay(guardian));
		context.waitTicks(30);
		context.takeScreenshot(prefix + "death");
		server.runCommand("kill @e[tag=fight_dummy]");
		server.runCommand(String.format(Locale.ROOT, "forceload remove %d %d", x, z));
		if (!context.computeOnClient(client -> ClientNav.codex().stream().anyMatch(e -> e.id().equals(guardian.id())))) {
			throw new AssertionError("meeting " + guardian.id() + " didn't open its Codex entry");
		}
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
	 * Lower Hell's rules (screenshots lower_*): a tomb of Heresy warning, then blasting; the Burning Sands' countdown and
	 * the fire falling; a centaur watching the river from its post by a ford (its gaze is the red cone); the harpies the
	 * Wood's trees call; a fake Rest of Malebolge, and its trap springing.
	 */
	private static void lowerRules(ClientGameTestContext context, TestDedicatedServerContext server, TestDedicatedServerConnection connection) {
		// a spectator hangs where it's put (a creative player would drop to the ground)
		server.runCommand("gamemode spectator Player0");
		context.runOnClient(client -> {
			if (!client.gui.hud.isHidden()) {
				client.gui.hud.toggle();
			}
		});
		// Heresy: a tomb by the east road, its red circle filling, then the fire
		Tombs.Tomb tomb = Tombs.within(1180, 30, 1320, 140).getFirst();
		int tx = (int) Math.floor(tomb.centreX());
		int tz = (int) Math.floor(tomb.centreZ());
		server.runCommand(String.format(Locale.ROOT, "forceload add %d %d", tx, tz));
		int tombY = server.computeOnServer(s -> {
			s.overworld().getChunk(tx >> 4, tz >> 4);
			return s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tx, tz);
		});
		camera(server, tx - 7, tz - 9, 7, tomb.centreX(), tombY + 2, tomb.centreZ());
		context.waitTicks(10);
		connection.waitForChunksRender();
		waitForServerTime(context, server, t -> tomb.phase(t) == Tombs.WARN - 8);
		context.takeScreenshot("lower_tomb_warning");
		waitForServerTime(context, server, t -> tomb.phase(t) == Tombs.WARN + 8);
		context.takeScreenshot("lower_tomb_blast");
		server.runCommand(String.format(Locale.ROOT, "forceload remove %d %d", tx, tz));

		// the Burning Sands: the countdown, then the fire falling on the open sand
		int sx = 850;
		int sz = 70;
		server.runCommand(String.format(Locale.ROOT, "forceload add %d %d", sx, sz));
		int sandY = server.computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx, sz));
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %d %d %d 120 10", sx, sandY, sz));
		server.runCommand("gamemode survival Player0");
		server.runCommand("effect give Player0 minecraft:resistance 120 4 true");
		context.runOnClient(client -> {
			if (client.gui.hud.isHidden()) {
				client.gui.hud.toggle();
			}
		});
		context.waitTicks(10);
		connection.waitForChunksRender();
		waitForServerTime(context, server, t -> LowerRules.sandsPhase(t) == LowerRules.SANDS_WARN_AT + 30);
		context.takeScreenshot("lower_sands_warning");
		waitForServerTime(context, server, t -> LowerRules.sandsPhase(t) == LowerRules.SANDS_FIRE_AT + 30);
		context.takeScreenshot("lower_sands_fire");
		server.runCommand("gamemode creative Player0");
		server.runCommand("extinguish Player0");
		server.runCommand(String.format(Locale.ROOT, "forceload remove %d %d", sx, sz));

		// the river of blood: a centaur on its post by a ford, watching the river
		LowerMonsters.Post post = LowerMonsters.posts().getFirst();
		double out = Math.atan2(post.z(), post.x());
		int postY = server.computeOnServer(s -> {
			s.overworld().getChunk((int) Math.floor(post.x()) >> 4, (int) Math.floor(post.z()) >> 4);
			return s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(post.x()), (int) Math.floor(post.z()));
		});
		// from along the bank, looking past the centaur's post and out over the river
		double along = out + Math.PI / 2;
		camera(server, post.x() + Math.cos(along) * 14 + Math.cos(out) * 4, post.z() + Math.sin(along) * 14 + Math.sin(out) * 4, 9,
				post.x() - Math.cos(out) * 12, postY - 2, post.z() - Math.sin(out) * 12);
		context.runOnClient(client -> {
			if (!client.gui.hud.isHidden()) {
				client.gui.hud.toggle();
			}
		});
		context.waitTicks(10);
		connection.waitForChunksRender();
		boolean watching = false;
		for (int i = 0; i < 40 && !watching; i++) {
			context.waitTicks(10);
			watching = server.computeOnServer(s -> s.overworld().getEntities(HellEntities.CENTAUR, c -> c.watching()).size() > 0);
		}
		if (!watching) {
			throw new AssertionError("no centaur took its post by the ford and watched the river");
		}
		context.waitTicks(30);
		context.takeScreenshot("lower_ford_centaur");
		server.runCommand("kill @e[type=hellcraft:centaur]");

		// the Wood of the Suicides: the trees scream and the harpies come
		int wx = 975;
		int wz = 70;
		int woodY = server.computeOnServer(s -> {
			s.overworld().getChunk(wx >> 4, wz >> 4);
			return s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz);
		});
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %d %d %d 90 -25", wx, woodY + 1, wz));
		context.waitTicks(10);
		connection.waitForChunksRender();
		server.runOnServer(s -> LowerMonsters.harpies(s.overworld(), s.getPlayerList().getPlayerByName("Player0"), 3));
		context.waitTicks(30);
		context.takeScreenshot("lower_wood_harpies");
		server.runCommand("kill @e[type=hellcraft:harpy]");

		// Malebolge: a fake Rest, and its trap springing
		FraudSites.FakeRest fake = FraudSites.fakeRests().getFirst();
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %d %d %d", fake.x() - 30, 40, fake.z()));
		context.waitTicks(10);
		boolean built = false;
		for (int i = 0; i < 20 && !built; i++) {
			context.waitTicks(20);
			built = server.computeOnServer(s -> HellState.get(s).fakeRests.containsKey(fake.id()));
		}
		if (!built) {
			throw new AssertionError("the fake Rest " + fake.id() + " was never built");
		}
		int floor = server.computeOnServer(s -> HellState.get(s).fakeRests.get(fake.id()));
		// along the ridge (round the circle), so the walls of the ditches either side don't block the view
		double tangent = Math.atan2(fake.z(), fake.x()) + Math.PI / 2;
		server.runCommand("gamemode spectator Player0");
		camera(server, fake.x() + Math.cos(tangent) * 15, fake.z() + Math.sin(tangent) * 15, 12, fake.x(), floor, fake.z());
		context.waitTicks(10);
		connection.waitForChunksRender();
		context.waitTicks(20);
		context.takeScreenshot("lower_fake_rest");
		server.runCommand("gamemode survival Player0");
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %d %d %d", fake.x() + 2, floor, fake.z() - 3));
		context.waitTicks(40);
		camera(server, fake.x() + Math.cos(tangent) * 12, fake.z() + Math.sin(tangent) * 12, 5, fake.x(), floor, fake.z());
		context.waitTicks(20);
		context.takeScreenshot("lower_fake_rest_sprung");
		if (!server.computeOnServer(s -> HellState.get(s).fraudSpent.contains(fake.id()))) {
			throw new AssertionError("stepping into the fake Rest didn't spring its trap");
		}
		server.runCommand("kill @e[type=hellcraft:thief]");
		server.runCommand("kill @e[type=hellcraft:mimic]");
		server.runCommand("gamemode spectator Player0");
		server.runCommand("effect clear Player0");
		context.runOnClient(client -> {
			if (client.gui.hud.isHidden()) {
				client.gui.hud.toggle();
			}
		});
	}

	/** Puts the camera `above` blocks over the ground at (x, z) (or over the target, if that's higher), looking at the target. */
	private static void camera(TestDedicatedServerContext server, double x, double z, int above, double tx, double ty, double tz) {
		int ground = server.computeOnServer(s -> {
			s.overworld().getChunk((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
			return s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
		});
		double y = Math.max(ground, ty) + above;
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %.1f %.1f %.1f facing %.1f %.1f %.1f", x, y, z, tx, ty, tz));
	}

	/** Waits (up to a minute) until the server's game time satisfies the test. */
	private static void waitForServerTime(ClientGameTestContext context, TestDedicatedServerContext server, java.util.function.LongPredicate test) {
		for (int i = 0; i < 1200; i++) {
			long t = server.computeOnServer(s -> s.overworld().getGameTime());
			if (test.test(t)) {
				return;
			}
			context.waitTicks(1);
		}
		throw new AssertionError("waited a minute for the right moment and it never came");
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

	/**
	 * The dialogue box (screenshots dialogue_*): what characters say is drawn above the hotbar instead of in the
	 * chat. /hellcraft dialogue test sends a line, a shout and a notice.
	 */
	private static void dialogueBox(ClientGameTestContext context, TestDedicatedServerContext server) {
		context.waitFor(client -> DialogueHud.idle(), 1200);
		int before = context.computeOnClient(client -> DialogueHud.shown());
		server.runCommand("execute as Player0 run hellcraft dialogue test");
		context.waitFor(client -> DialogueHud.shown() > before, 100);
		context.waitTicks(60);
		context.takeScreenshot("dialogue_line");
		context.waitFor(client -> DialogueHud.shown() > before + 1, 300);
		context.waitTicks(4);
		context.takeScreenshot("dialogue_shout");
		context.waitFor(client -> DialogueHud.shown() > before + 2, 300);
		context.waitTicks(30);
		context.takeScreenshot("dialogue_notice");
	}
}
