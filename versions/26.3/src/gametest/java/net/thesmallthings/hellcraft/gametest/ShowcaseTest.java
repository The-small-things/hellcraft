package net.thesmallthings.hellcraft.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.entity.npc.Damned;
import net.thesmallthings.hellcraft.entity.npc.Damned.Punishment;
import net.thesmallthings.hellcraft.hazard.Torments;
import net.thesmallthings.hellcraft.hazard.guardian.Guardian;
import net.thesmallthings.hellcraft.hazard.guardian.GuardianManager;
import net.thesmallthings.hellcraft.world.Fen;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.NobleCastle;
import net.thesmallthings.hellcraft.world.ParadisoGeometry.Sphere;
import net.thesmallthings.hellcraft.world.ParadisoGeometry;
import net.thesmallthings.hellcraft.world.Purgatory;
import net.thesmallthings.hellcraft.world.Shades;
import net.thesmallthings.hellcraft.world.feature.WatchtowerFeature;

import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Pictures for showing the mod off (screenshots showcase_*), taken only when HELLCRAFT_SHOWCASE=1: no HUD, a long view,
 * 1920x1080. The Inferno from the Gate to Cocytus, the damned, the guardians fighting in their own lairs, the Forge of
 * Dis, Purgatory and Paradiso. Without the variable it does nothing, so CI doesn't pay for it.
 */
public class ShowcaseTest implements FabricClientGameTest {
	private static final boolean ON = System.getenv("HELLCRAFT_SHOWCASE") != null;
	private static final int VIEW = 12;
	/** How long a far view may take to draw (ticks): the software renderer is slow at this distance. */
	private static final int SETTLE = 12000;

	private static double s(double r) {
		return InfernoGeometry.scaled(r);
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!ON) {
			return;
		}
		Properties properties = new Properties();
		properties.setProperty("view-distance", String.valueOf(VIEW));
		properties.setProperty("simulation-distance", "6");
		try (TestDedicatedServerContext server = context.worldBuilder().adjustSettings(MapTest::inferno).createServer(properties);
			 TestDedicatedServerConnection connection = server.connect()) {
			connection.waitForChunksRender(SETTLE);
			server.runCommand("gamemode spectator Player0");
			context.runOnClient(client -> {
				client.options.renderDistance().set(VIEW);
				if (!client.gui.hud.isHidden()) {
					client.gui.hud.toggle();
				}
			});
			String o = "minecraft:overworld";

			// the way in, and the funnel opening below
			int gate = InfernoGeometry.gateX();
			shot(context, server, connection, o, "gate", gate + 70, 215, 30, gate - 20, 175, 0);
			shot(context, server, connection, o, "funnel", s(2650), 240, s(-420), s(1500), 60, 0);
			shot(context, server, connection, o, "road_lust", s(2520), 140, 10, s(2300), 110, 0);
			castle(context, server, connection);
			damned(context, server, connection, Punishment.LUST, 2425, 30, 10, 2);
			fen(context, server, connection);
			shot(context, server, connection, o, "walls_of_dis", s(1500), 100, 40, s(1380), 85, 0);
			damned(context, server, connection, Punishment.HERESY, 1240, 24, 9, 3.5);
			shot(context, server, connection, o, "malebolge", s(720), 50, 30, s(600), 20, 0);
			damned(context, server, connection, Punishment.MALEBOLGE, 640, 24, 11, 2.5);
			shot(context, server, connection, o, "spine", s(360), 16, 50, s(260), -20, 0);
			shot(context, server, connection, o, "cocytus", 120, -22, 34, 0, -20, 0);

			// the guardians, each fighting a dummy in its own lair
			guardian(context, server, connection, Guardian.MINOS, 14, 5, new String[]{"strike", "tail"}, new int[]{16, 20});
			guardian(context, server, connection, Guardian.CERBERUS, 18, 6, new String[]{"howl", "maws"}, new int[]{22, 16});
			guardian(context, server, connection, Guardian.PLUTUS, 18, 6, new String[]{"gold", "pape"}, new int[]{18, 22});
			guardian(context, server, connection, Guardian.PHLEGYAS, 18, 6, new String[]{"wave", "oar"}, new int[]{20, 14});
			guardian(context, server, connection, Guardian.FURIES, 16, 6, new String[]{"medusa", "shriek"}, new int[]{28, 14});
			guardian(context, server, connection, Guardian.FARINATA, 14, 5, new String[]{"pyres", "disdain"}, new int[]{24, 16});
			guardian(context, server, connection, Guardian.MINOTAUR, 16, 5, new String[]{"stomp", "gore"}, new int[]{22, 16});
			guardian(context, server, connection, Guardian.GERYON, 18, 7, new String[]{"coil", "sting"}, new int[]{26, 16});

			// the Forge of Dis, and Vulcan in it
			String n = "minecraft:the_nether";
			shot(context, server, connection, n, "forge", 110, 70, 60, 0, 60, 0);
			shot(context, server, connection, n, "slag_sea", 100, 50, 20, 40, 32, -10);
			guardian(context, server, connection, Guardian.VULCAN, 15, 5, new String[]{"hammerfall", "molten"}, new int[]{24, 26});

			// Purgatory, Paradiso and the Seraph
			String p = "hellcraft:purgatory";
			shot(context, server, connection, p, "purgatory", 20, 95, 140, 0, Purgatory.SUMMIT_Y - 60, 0);
			shot(context, server, connection, p, "earthly_paradise", 16, Purgatory.SUMMIT_Y + 8, 20, 0, Purgatory.SUMMIT_Y + 1, 0);
			String h = "hellcraft:paradiso";
			for (Sphere sphere : List.of(Sphere.SUN, Sphere.MARS, Sphere.FIXED_STARS)) {
				double[] a = ParadisoGeometry.arrival(sphere);
				double[] c = ParadisoGeometry.centre(sphere);
				int y = ParadisoGeometry.topY(sphere);
				shot(context, server, connection, h, "paradiso_" + sphere.id, a[0] + (a[0] - c[0]) * 0.6, y + 22, a[1] + (a[1] - c[1]) * 0.6, c[0], y, c[1]);
			}
			server.runCommand("hellcraft paradiso host");
			double[] ec = ParadisoGeometry.centre(Sphere.EMPYREAN);
			int ey = ParadisoGeometry.topY(Sphere.EMPYREAN);
			shot(context, server, connection, h, "rose", ec[0] + 44, ey + 16, ec[1] + 26, ec[0], ey + 8, ec[1]);
			guardian(context, server, connection, Guardian.SERAPH, 16, 5, new String[]{"gloria", "wings"}, new int[]{40, 18});
		}
	}

	/** Stands somewhere, looks at something, waits for the world to draw, and takes a full-HD picture. */
	private static void shot(ClientGameTestContext context, TestDedicatedServerContext server, TestDedicatedServerConnection connection,
							 String dimension, String name, double x, double y, double z, double lx, double ly, double lz) {
		server.runCommand(String.format(Locale.ROOT, "execute in %s run tp Player0 %.1f %.1f %.1f facing %.1f %.1f %.1f", dimension, x, y, z, lx, ly, lz));
		context.waitTicks(30);
		connection.waitForChunksRender(SETTLE);
		context.waitTicks(40);
		snap(context, name);
	}

	private static void snap(ClientGameTestContext context, String name) {
		context.takeScreenshot(TestScreenshotOptions.of("showcase_" + name).withSize(1920, 1080));
	}

	/** The Noble Castle from the air, then its court with the shades. */
	private static void castle(ClientGameTestContext context, TestDedicatedServerContext server, TestDedicatedServerConnection connection) {
		int cx = NobleCastle.X;
		int cz = NobleCastle.Z;
		server.runCommand(String.format(Locale.ROOT, "forceload add %d %d %d %d", cx - 56, cz - 56, cx + 56, cz + 56));
		int ground = server.computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx + 3, cz - 12));
		shot(context, server, connection, "minecraft:overworld", "castle", cx + 70, ground + 30, cz + 58, cx, ground + 4, cz);
		server.computeOnServer(s -> Shades.ensure(s.overworld(), false));
		shot(context, server, connection, "minecraft:overworld", "castle_court", cx + 6.5, ground + 4, cz - 20.5, cx, ground + 2, cz);
		server.runCommand(String.format(Locale.ROOT, "forceload remove %d %d %d %d", cx - 56, cz - 56, cx + 56, cz + 56));
	}

	/** A watchtower on the Fen's shore, its beacon lit against the red sky. */
	private static void fen(ClientGameTestContext context, TestDedicatedServerContext server, TestDedicatedServerConnection connection) {
		int[] tower = Fen.tower(0);
		int base = WatchtowerFeature.base(tower);
		shot(context, server, connection, "minecraft:overworld", "fen", tower[0] + 26, base + 6, tower[1] + 22, tower[0], base + 12, tower[1]);
	}

	/** A group of the damned suffering their punishment, a little way off the east road, seen from a few blocks away. */
	private static void damned(ClientGameTestContext context, TestDedicatedServerContext server, TestDedicatedServerConnection connection,
							   Punishment punishment, double r, double z0, double far, double up) {
		double x = s(r);
		double z = z0;
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %.1f 200 %.1f", x, z));
		context.waitTicks(10);
		connection.waitForChunksRender(SETTLE);
		int ground = server.computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, (int) x, (int) z));
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %.1f %d %.1f", x, ground + 6, z));
		context.waitTicks(10);
		connection.waitForChunksRender(SETTLE);
		List<Integer> ids = server.computeOnServer(s -> Torments.spawnNear(s.overworld(), punishment, x, z, 3, 40, 400).stream()
				.map(Entity::getId).toList());
		if (ids.isEmpty()) {
			throw new AssertionError("nowhere near " + (int) x + ", " + (int) z + " to show " + punishment.id());
		}
		Vec3 at = server.computeOnServer(s -> s.overworld().getEntity(ids.getFirst()).position());
		double dx = x - at.x;
		double dz = z - at.z;
		double len = Math.max(0.1, Math.hypot(dx, dz));
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %.2f %.2f %.2f facing %.2f %.2f %.2f", at.x + dx / len * far, at.y + up,
				at.z + dz / len * far, at.x, at.y + 1.2, at.z));
		context.waitTicks(5);
		connection.waitForChunksRender(SETTLE);
		context.waitTicks(30);
		snap(context, "damned_" + punishment.id());
		server.computeOnServer(s -> {
			s.overworld().getEntitiesOfClass(Damned.class, new AABB(x - 80, -64, z - 80, x + 80, 320, z + 80)).forEach(Entity::discard);
			return null;
		});
	}

	/**
	 * A guardian woken in its lair against a dummy, the camera low and far off on the dummy's side so it faces us: each
	 * attack is shot the given ticks after it starts, as its danger mark fills.
	 */
	private static void guardian(ClientGameTestContext context, TestDedicatedServerContext server, TestDedicatedServerConnection connection,
								 Guardian guardian, double far, double up, String[] attacks, int[] after) {
		String dimension = guardian.inNether() ? "minecraft:the_nether" : guardian.inParadiso() ? "hellcraft:paradiso" : "minecraft:overworld";
		int x;
		int z;
		int ground;
		if (guardian == Guardian.VULCAN) {
			x = 1;
			z = -2;
			ground = 70;
		} else if (guardian == Guardian.SERAPH) {
			double[] c = ParadisoGeometry.centre(Sphere.PRIMUM_MOBILE);
			x = (int) c[0];
			z = (int) c[1];
			ground = ParadisoGeometry.topY(Sphere.PRIMUM_MOBILE) + 1;
		} else {
			x = guardian.lairColumn().getX();
			z = guardian.lairColumn().getZ();
			server.runCommand(String.format(Locale.ROOT, "forceload add %d %d", x, z));
			ground = server.computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
		}
		// from the lair towards the centre of the world (or the south-west in the realms), a little to one side
		double ax = guardian.inNether() || guardian.inParadiso() ? -1 : -x;
		double az = guardian.inNether() || guardian.inParadiso() ? -0.8 : -z + 0.35 * x;
		double len = Math.max(1, Math.hypot(ax, az));
		ax /= len;
		az /= len;
		server.runCommand(String.format(Locale.ROOT, "execute in %s run tp Player0 %.1f %.1f %.1f facing %d %d %d", dimension,
				x + ax * far, ground + up, z + az * far, x, ground + 3, z));
		context.waitTicks(20);
		connection.waitForChunksRender(SETTLE);
		String out = server.computeOnServer(s -> GuardianManager.summon(s, guardian));
		if (!out.contains("awakens")) {
			throw new AssertionError(guardian.id() + " could not be summoned: " + out);
		}
		server.runCommand(String.format(Locale.ROOT, "execute in %s run summon minecraft:villager %.1f %d %.1f {NoAI:1b,Invulnerable:1b,"
				+ "PersistenceRequired:1b,Silent:1b,Tags:[\"fight_dummy\"]}", dimension, x + ax * 5, ground, z + az * 5));
		for (int i = 0; i < attacks.length; i++) {
			String attack = attacks[i];
			// wait out the entrance: try the attack until the fight takes it
			boolean used = false;
			for (int t = 0; t < 40 && !used; t++) {
				context.waitTicks(10);
				used = server.computeOnServer(s -> GuardianManager.attack(guardian, attack)).contains(" uses ");
			}
			if (!used) {
				throw new AssertionError(guardian.id() + " never used " + attack);
			}
			context.waitTicks(after[i]);
			snap(context, guardian.id() + "_" + attack);
			context.waitTicks(60);
		}
		server.computeOnServer(s -> GuardianManager.slay(guardian));
		context.waitTicks(80);
		server.runCommand(String.format(Locale.ROOT, "execute in %s run kill @e[tag=fight_dummy]", dimension));
		if (!guardian.inNether() && !guardian.inParadiso()) {
			server.runCommand(String.format(Locale.ROOT, "forceload remove %d %d", x, z));
		}
	}
}
