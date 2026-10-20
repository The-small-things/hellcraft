package net.thesmallthings.hellcraft.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.thesmallthings.hellcraft.blood.HellItems;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.blood.Pilgrimage;
import net.thesmallthings.hellcraft.client.nav.JournalScreen;
import net.thesmallthings.hellcraft.client.nav.MapOfHellScreen;
import net.thesmallthings.hellcraft.world.SecretWayLift;
import net.thesmallthings.hellcraft.world.SecretWays;

import java.util.Locale;
import java.util.Properties;

/**
 * The secret ways down and the Pilgrimage, in an Inferno world on a dedicated server: one landmark of each kind seen
 * from in front (secret_&lt;kind&gt;), a fall into Limbo's well (secret_float, then secret_landing on its cairn in Lust, and
 * the cairn itself, secret_cairn),
 * the Map of Hell with the way found (secret_map, secret_map_all) and the Journal's count (secret_journal); then the
 * Pilgrims' Stone by the spawn with the pilgrim's mark on the screen (pilgrimage_hud), the pilgrimage finished on Purgatory's
 * shore (pilgrimage_done), and a second one ended by a death. It runs the smoke test's self-tests first.
 */
public class SecretsTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		Properties properties = new Properties();
		properties.setProperty("view-distance", "6");
		properties.setProperty("simulation-distance", "4");
		try (TestDedicatedServerContext server = context.worldBuilder().adjustSettings(MapTest::inferno).createServer(properties);
			 TestDedicatedServerConnection connection = server.connect()) {
			connection.waitForChunksRender();
			// the smoke test's self-tests, on a real server
			String secrets = server.computeOnServer(SecretWayLift::selfTest);
			String rules = server.computeOnServer(Pilgrimage::selfTest);
			if (!secrets.startsWith("Secrets test OK") || !rules.startsWith("Pilgrimage test OK")) {
				throw new AssertionError(secrets + " / " + rules);
			}
			server.runCommand("gamemode spectator Player0");
			context.runOnClient(client -> {
				if (!client.gui.hud.isHidden()) {
					client.gui.hud.toggle();
				}
			});
			landmarks(context, server, connection);
			context.runOnClient(client -> {
				if (client.gui.hud.isHidden()) {
					client.gui.hud.toggle();
				}
			});
			useOne(context, server, connection);
			pilgrimage(context, server, connection);
		}
	}

	/** One of each kind, from in front and a little above, looking at its mouth. */
	private static void landmarks(ClientGameTestContext context, TestDedicatedServerContext server, TestDedicatedServerConnection connection) {
		for (SecretWays.Kind kind : SecretWays.Kind.values()) {
			SecretWays.Site s = SecretWays.all().stream().filter(x -> x.kind() == kind).findFirst().orElseThrow();
			// the hollow tree stands in a thicket: from the edge of its clearing
			int back = kind == SecretWays.Kind.HOLLOW_TREE ? kind.radius : kind.radius + (kind == SecretWays.Kind.HEAD ? 9 : 7);
			double cx = s.worldX(back, 1) + 0.5;
			double cz = s.worldZ(back, 1) + 0.5;
			double up = kind == SecretWays.Kind.HEAD ? 5 : kind == SecretWays.Kind.WHIRLPOOL ? 7 : kind == SecretWays.Kind.HOLLOW_TREE ? 3 : 6;
			double tx = s.worldX(s.kind().mouthF(), 0) + 0.5;
			double tz = s.worldZ(s.kind().mouthF(), 0) + 0.5;
			float yaw = (float) Math.toDegrees(Math.atan2(tz - cz, tx - cx)) - 90f;
			// above whatever stands where the camera is (Lust's crags, the Wood's thicket)
			server.runCommand(String.format(Locale.ROOT, "tp Player0 %.1f %d %.1f %.1f 60", cx, s.y() + 40, cz, yaw));
			context.waitTicks(10);
			connection.waitForChunksRender();
			int bx = (int) Math.floor(cx);
			int bz = (int) Math.floor(cz);
			int ground = server.computeOnServer(sv -> sv.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz));
			double camY = Math.max(s.y() + up, ground + 2);
			double look = s.y() + (kind == SecretWays.Kind.HEAD ? 3 : 0);
			float pitch = (float) Math.toDegrees(Math.atan2(camY - look, Math.hypot(tx - cx, tz - cz)));
			server.runCommand(String.format(Locale.ROOT, "tp Player0 %.1f %.1f %.1f %.1f %.1f", cx, camY, cz, yaw, pitch));
			context.waitTicks(10);
			connection.waitForChunksRender();
			context.waitTicks(30);
			context.takeScreenshot("secret_" + kind.name().toLowerCase(Locale.ROOT));
		}
	}

	/** Drop into Limbo's well: the float, then the landing in Lust; the soul remembers it and the map marks it. */
	private static void useOne(ClientGameTestContext context, TestDedicatedServerContext server, TestDedicatedServerConnection connection) {
		SecretWays.Site well = SecretWays.byId("limbo_1");
		double x = well.worldX(1, 1) + 0.5;
		double z = well.worldZ(1, 1) + 0.5;
		float yaw = (float) Math.toDegrees(Math.atan2(-well.fz(), -well.fx())) - 90f;
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %.1f %d %.1f %.1f 30", x, well.y() + 6, z, yaw));
		context.waitTicks(10);
		connection.waitForChunksRender();
		server.runCommand("gamemode survival Player0");
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %.1f %d %.1f %.1f 30", x, well.y() + 2, z, yaw));
		context.waitTicks(22);
		context.takeScreenshot("secret_float");
		context.waitTicks(50);
		connection.waitForChunksRender();
		context.waitTicks(20);
		context.takeScreenshot("secret_landing");
		boolean found = server.computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayerByName("Player0");
			HellState.Soul soul = HellState.get(s).existing(p.getUUID());
			double d = Math.hypot(p.getX() - well.landX() - 0.5, p.getZ() - well.landZ() - 0.5);
			return soul != null && soul.secrets.contains("limbo_1") && d < 4;
		});
		if (!found) {
			throw new AssertionError("The well in Limbo did not carry Player0 down to its landing in Lust");
		}
		// the cairn it set us down on, from a little way off
		server.runCommand("gamemode spectator Player0");
		int top = server.computeOnServer(sv -> Math.max(sv.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, well.landX(), well.landZ()),
				sv.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, well.landX() + 4, well.landZ())));
		int cairn = server.computeOnServer(sv -> sv.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, well.landX(), well.landZ()));
		float down = (float) Math.toDegrees(Math.atan2(top + 4 - cairn, 4.0));
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %.1f %d %.1f 90 %.1f", well.landX() + 4.5, top + 4, well.landZ() + 0.5, down));
		context.waitTicks(10);
		connection.waitForChunksRender();
		context.waitTicks(10);
		context.takeScreenshot("secret_cairn");
		context.setScreen(() -> MapOfHellScreen.at(well.x(), well.z(), 6));
		context.getInput().setCursorPos(2, 2);
		context.waitTicks(20);
		context.takeScreenshot("secret_map");
		server.runCommand("hellcraft secrets find Player0 all");
		context.waitTicks(5);
		context.setScreen(() -> MapOfHellScreen.at(0, 0, 1));
		context.getInput().setCursorPos(2, 2);
		context.waitTicks(20);
		context.takeScreenshot("secret_map_all");
		context.setScreen(() -> JournalScreen.at(0));
		context.waitTicks(5);
		// down the page to the secret ways
		double w = context.computeOnClient(c -> (double) c.getWindow().getScreenWidth());
		double h = context.computeOnClient(c -> (double) c.getWindow().getScreenHeight());
		context.getInput().setCursorPos(w * 0.7, h * 0.5);
		for (int i = 0; i < 14; i++) {
			context.getInput().scroll(-1);
			context.waitTicks(1);
		}
		context.waitTicks(5);
		context.takeScreenshot("secret_journal");
		context.setScreen(() -> null);
	}

	/** The Pilgrims' Stone by the spawn, a pilgrimage under way and its mark at the top left. */
	private static void pilgrimage(ClientGameTestContext context, TestDedicatedServerContext server, TestDedicatedServerConnection connection) {
		BlockPos lectern = server.computeOnServer(s -> {
			HellState state = HellState.get(s);
			return state.pilgrimStone == null ? null : state.pilgrimStone.pos();
		});
		if (lectern == null) {
			throw new AssertionError("The Pilgrims' Stone was not built");
		}
		// pilgrims walk in survival (a spectator's pilgrimage ends at once)
		server.runCommand("gamemode survival Player0");
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %d %d %d 0 15", lectern.getX(), lectern.getY() + 1, lectern.getZ() - 6));
		context.waitTicks(10);
		connection.waitForChunksRender();
		server.runCommand("hellcraft pilgrimage start Player0");
		context.waitTicks(70);
		context.takeScreenshot("pilgrimage_hud");
		boolean on = server.computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayerByName("Player0");
			HellState.Soul soul = HellState.get(s).existing(p.getUUID());
			return soul != null && soul.pilgrimTicks > 0;
		});
		if (!on) {
			throw new AssertionError("The pilgrimage did not start or its clock does not run");
		}
		// reaching Purgatory finishes it: the shell, the title
		server.runCommand("execute as Player0 run hellcraft purgatory");
		context.waitTicks(30);
		connection.waitForChunksRender();
		context.takeScreenshot("pilgrimage_done");
		boolean done = server.computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayerByName("Player0");
			HellState.Soul soul = HellState.get(s).existing(p.getUUID());
			return soul.pilgrimTicks == -1 && soul.pilgrimages == 1 && p.getInventory().contains(new ItemStack(HellItems.PILGRIM_SHELL));
		});
		if (!done) {
			throw new AssertionError("Reaching Purgatory did not finish the pilgrimage");
		}
		// back at the Gate, a second one ends in death, and the Stone remembers it
		server.runCommand(String.format(Locale.ROOT, "execute in minecraft:overworld run tp Player0 %d %d %d 0 15", lectern.getX(), lectern.getY() + 1,
				lectern.getZ() - 6));
		context.waitTicks(20);
		server.runCommand("hellcraft pilgrimage start Player0");
		context.waitTicks(30);
		server.runCommand("kill Player0");
		context.waitTicks(10);
		boolean fell = server.computeOnServer(s -> {
			HellState state = HellState.get(s);
			ServerPlayer p = s.getPlayerList().getPlayerByName("Player0");
			return state.existing(p.getUUID()).pilgrimTicks == -1 && state.pilgrims.stream().anyMatch(r -> r.name().equals("Player0") && !r.done())
					&& state.pilgrims.stream().anyMatch(r -> r.name().equals("Player0") && r.done());
		});
		if (!fell) {
			throw new AssertionError("Dying did not end the pilgrimage, or the Stone forgot it");
		}
	}
}
