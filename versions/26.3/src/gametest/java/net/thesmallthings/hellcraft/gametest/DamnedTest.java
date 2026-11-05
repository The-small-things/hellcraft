package net.thesmallthings.hellcraft.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.entity.npc.Damned.Punishment;
import net.thesmallthings.hellcraft.entity.npc.Damned;
import net.thesmallthings.hellcraft.hazard.Onlookers;
import net.thesmallthings.hellcraft.hazard.Torments;
import net.thesmallthings.hellcraft.world.InfernoGeometry;

import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * The damned in their torments (Torments), in an Inferno world on a dedicated server: for each circle, a group is
 * brought out near the east road and filmed from a few blocks off (screenshots damned_*): the Vestibule's line after
 * the banner, Limbo's longing, Lust's storm, Gluttony's mud, Greed's weights, Wrath's brawl in the Styx, Heresy's
 * tombs, the river of blood, the Burning Sands, Malebolge's gilded procession and the ice of Cocytus. Then a player
 * in Lust must find souls brought out round them on their own, and none of them hurt; then an onlooker (onlooker_*).
 */
public class DamnedTest implements FabricClientGameTest {
	/** Where to look for each punishment: a radius along the east road (as first drawn) and how far off the road. */
	private record Stop(Punishment punishment, double r, double z) {
	}

	private static final Stop[] STOPS = {
			new Stop(Punishment.VESTIBULE, 3080, 24),
			new Stop(Punishment.LIMBO, 2770, 24),
			new Stop(Punishment.LUST, 2425, 30),
			new Stop(Punishment.GLUTTONY, 2100, 24),
			new Stop(Punishment.GREED, 1800, 24),
			new Stop(Punishment.WRATH, 1530, 24),
			new Stop(Punishment.HERESY, 1240, 24),
			new Stop(Punishment.PHLEGETHON, 1065, 24),
			new Stop(Punishment.SANDS, 855, 24),
			new Stop(Punishment.MALEBOLGE, 640, 24),
			new Stop(Punishment.COCYTUS, 265, 24),
	};

	@Override
	public void runTest(ClientGameTestContext context) {
		Properties properties = new Properties();
		properties.setProperty("view-distance", "6");
		properties.setProperty("simulation-distance", "4");
		properties.setProperty("server-port", "25587"); // TEMP-LOCAL
		properties.setProperty("max-tick-time", "-1"); // TEMP-LOCAL
		try (TestDedicatedServerContext server = context.worldBuilder().adjustSettings(MapTest::inferno).createServer(properties);
			 TestDedicatedServerConnection connection = server.connect()) {
			connection.waitForChunksRender();
			server.runCommand("gamemode spectator Player0");
			context.runOnClient(client -> {
				if (!client.gui.hud.isHidden()) {
					client.gui.hud.toggle();
				}
			});
			for (Stop stop : STOPS) {
				film(context, server, connection, stop);
			}
			ambient(context, server, connection);
			onlooker(context, server, connection);
		}
	}

	/**
	 * An onlooker (hazard/Onlookers): a shade stands far off on open ground in Lust and stares at a player (onlooker_far,
	 * then onlooker_near from 28 blocks), and fades when they walk up to it (onlooker_faded).
	 */
	private static void onlooker(ClientGameTestContext context, TestDedicatedServerContext server, TestDedicatedServerConnection connection) {
		double x = InfernoGeometry.scaled(2425);
		double z = 30;
		server.runCommand("gamemode creative Player0");
		Vec3 at = null;
		for (int yaw = 0; yaw < 360 && at == null; yaw += 45) {
			server.runCommand(String.format(Locale.ROOT, "tp Player0 %.1f 200 %.1f %d 0", x, z, yaw));
			context.waitTicks(5);
			int ground = server.computeOnServer(s -> s.overworld().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) x, (int) z));
			server.runCommand(String.format(Locale.ROOT, "tp Player0 %.1f %d %.1f %d 0", x, ground + 1, z, yaw));
			context.waitTicks(10);
			connection.waitForChunksRender();
			at = server.computeOnServer(s -> {
				Mannequin m = Onlookers.spawnFor(s.getPlayerList().getPlayerByName("Player0"));
				return m == null ? null : m.position();
			});
		}
		if (at == null) {
			throw new AssertionError("no spot for an onlooker anywhere round the player");
		}
		Vec3 shade = at;
		server.runCommand(String.format(Locale.ROOT, "execute as Player0 at @s run tp @s ~ ~ ~ facing %.1f %.1f %.1f", shade.x, shade.y + 1.5, shade.z));
		context.waitTicks(40);
		context.takeScreenshot("onlooker_far");
		Vec3 player = server.computeOnServer(s -> s.getPlayerList().getPlayerByName("Player0").position());
		Vec3 toward = player.subtract(shade).multiply(1, 0, 1).normalize();
		Vec3 near = shade.add(toward.scale(28));
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %.1f %.1f %.1f facing %.1f %.1f %.1f", near.x, shade.y + 4, near.z, shade.x, shade.y + 1.5, shade.z));
		context.waitTicks(20);
		connection.waitForChunksRender();
		context.waitTicks(20);
		context.takeScreenshot("onlooker_near");
		if (server.computeOnServer(s -> s.overworld().getEntities(net.minecraft.world.entity.EntityTypes.MANNEQUIN, e -> e.entityTags().contains(Onlookers.TAG)).isEmpty())) {
			throw new AssertionError("the onlooker faded before the player came near");
		}
		Vec3 close = shade.add(toward.scale(8));
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %.1f %.1f %.1f facing %.1f %.1f %.1f", close.x, shade.y + 1, close.z, shade.x, shade.y + 1.5, shade.z));
		context.waitTicks(10);
		context.takeScreenshot("onlooker_faded");
		if (!server.computeOnServer(s -> s.overworld().getEntities(net.minecraft.world.entity.EntityTypes.MANNEQUIN, e -> e.entityTags().contains(Onlookers.TAG)).isEmpty())) {
			throw new AssertionError("the onlooker didn't fade when the player came near");
		}
		server.runCommand("gamemode spectator Player0");
	}

	private static void film(ClientGameTestContext context, TestDedicatedServerContext server, TestDedicatedServerConnection connection, Stop stop) {
		double x = InfernoGeometry.scaled(stop.r());
		double z = stop.z();
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %.1f 200 %.1f", x, z));
		context.waitTicks(10);
		connection.waitForChunksRender();
		int ground = server.computeOnServer(s -> s.overworld().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) x, (int) z));
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %.1f %d %.1f", x, ground + 6, z));
		context.waitTicks(10);
		connection.waitForChunksRender();
		List<Integer> ids = server.computeOnServer(s -> Torments.spawnNear(s.overworld(), stop.punishment(), x, z, 3, 40, 400).stream()
				.map(Entity::getId).toList());
		if (ids.isEmpty()) {
			throw new AssertionError("nowhere near " + (int) x + ", " + (int) z + " to show " + stop.punishment().id());
		}
		int first = ids.getFirst();
		context.waitTicks(30);
		// stand a few blocks off the first of them, a little above, and look at it
		Vec3 at = server.computeOnServer(s -> {
			Entity e = s.overworld().getEntity(first);
			if (!(e instanceof Damned d)) {
				throw new AssertionError("the " + stop.punishment().id() + " soul is gone");
			}
			if (d.hurtServer(s.overworld(), s.overworld().damageSources().inFire(), 10) || d.getHealth() < d.getMaxHealth()) {
				throw new AssertionError("a soul of " + stop.punishment().id() + " was hurt");
			}
			return d.position();
		});
		double dx = x - at.x;
		double dz = z - at.z;
		double len = Math.max(0.1, Math.hypot(dx, dz));
		double far = switch (stop.punishment()) {
			case VESTIBULE, MALEBOLGE, LUST -> 9;
			case GREED -> 8;
			default -> 5.5;
		};
		double up = switch (stop.punishment()) {
			case LUST -> 1;
			case COCYTUS, GLUTTONY, PHLEGETHON, HERESY -> 2.6;
			default -> 2;
		};
		double look = switch (stop.punishment()) {
			case COCYTUS -> 1.5;
			case GLUTTONY -> 0.3;
			case PHLEGETHON, HERESY -> 1.5;
			default -> 1.1;
		};
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %.2f %.2f %.2f facing %.2f %.2f %.2f", at.x + dx / len * far, at.y + up,
				at.z + dz / len * far, at.x, at.y + look, at.z));
		context.waitTicks(5);
		connection.waitForChunksRender();
		context.waitTicks(stop.punishment() == Punishment.LUST ? 2 : 20);
		context.takeScreenshot("damned_" + stop.punishment().id());
		server.computeOnServer(s -> {
			s.overworld().getEntitiesOfClass(Damned.class, new AABB(x - 80, -64, z - 80, x + 80, 320, z + 80)).forEach(Entity::discard);
			return null;
		});
	}

	/** A player (not a spectator) in Lust: souls are brought out round them on their own within a few seconds. */
	private static void ambient(ClientGameTestContext context, TestDedicatedServerContext server, TestDedicatedServerConnection connection) {
		double x = InfernoGeometry.scaled(2425);
		double z = 30;
		server.runCommand("gamemode creative Player0");
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %.1f 200 %.1f", x, z));
		context.waitTicks(10);
		connection.waitForChunksRender();
		int ground = server.computeOnServer(s -> s.overworld().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) x, (int) z));
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %.1f %d %.1f -90 -15", x, ground + 1, z));
		context.waitFor(client -> !client.level.getEntitiesOfClass(Damned.class, client.player.getBoundingBox().inflate(48)).isEmpty(), 600);
		context.waitTicks(40);
		context.takeScreenshot("damned_ambient_lust");
		server.runCommand("gamemode spectator Player0");
	}
}
