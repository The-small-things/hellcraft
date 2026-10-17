package net.thesmallthings.hellcraft.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.thesmallthings.hellcraft.entity.HellEntities;
import net.thesmallthings.hellcraft.entity.boss.gen.SeraphAnim;
import net.thesmallthings.hellcraft.hazard.guardian.Guardian;
import net.thesmallthings.hellcraft.hazard.guardian.GuardianManager;
import net.thesmallthings.hellcraft.world.Paradiso;
import net.thesmallthings.hellcraft.world.ParadisoGeometry;
import net.thesmallthings.hellcraft.world.ParadisoGeometry.Sphere;
import net.thesmallthings.hellcraft.world.Purgatory;

import java.util.Locale;
import java.util.Properties;

/**
 * Purgatory and Paradiso (Phase 7), from a spectator's camera: Purgatory's island in its starlit sea and its summit
 * (purgatory_*), then each of Paradiso's spheres from its arrival isle, looking over its great island with the next
 * sphere and the column of light above (paradiso_*).
 */
public class ParadisoTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		Properties properties = new Properties();
		properties.setProperty("view-distance", "8");
		properties.setProperty("simulation-distance", "4");
		try (TestDedicatedServerContext server = context.worldBuilder().adjustSettings(MapTest::inferno).createServer(properties);
			 TestDedicatedServerConnection connection = server.connect()) {
			connection.waitForChunksRender();
			loadout(server);
			server.runCommand("gamemode spectator Player0");
			context.runOnClient(client -> {
				if (!client.gui.hud.isHidden()) {
					client.gui.hud.toggle();
				}
			});
			String status = server.computeOnServer(Paradiso::status);
			if (!status.contains("stands")) {
				throw new AssertionError(status);
			}
			shot(context, server, connection, "hellcraft:purgatory", "purgatory_island", 0, 80, 110, 0, 90, 0);
			shot(context, server, connection, "hellcraft:purgatory", "purgatory_shore", 12, Purgatory.SUMMIT_Y - 50, 62, 0, Purgatory.SUMMIT_Y - 50, 0);
			shot(context, server, connection, "hellcraft:purgatory", "purgatory_summit", 10, Purgatory.SUMMIT_Y + 6, 14, 0, Purgatory.SUMMIT_Y, 0);
			for (Sphere s : ParadisoGeometry.SPHERES) {
				double[] a = ParadisoGeometry.arrival(s);
				double[] c = ParadisoGeometry.centre(s);
				double bx = a[0] + (a[0] - c[0]) * 0.35;
				double bz = a[1] + (a[1] - c[1]) * 0.35;
				int y = ParadisoGeometry.topY(s);
				shot(context, server, connection, "hellcraft:paradiso", "paradiso_" + s.id, bx, y + 14, bz, c[0], y + 4, c[1]);
			}
			String test = server.computeOnServer(Paradiso::selfTest);
			if (!test.startsWith("Paradiso test OK")) {
				throw new AssertionError(test);
			}
			// the Moon's trial lights, which only a player still doing the trial sees (not a spectator)
			double[] moonCentre = ParadisoGeometry.centre(Sphere.MOON);
			double[] light = ParadisoGeometry.lights(Sphere.MOON).get(0);
			server.runCommand("gamemode adventure Player0");
			shot(context, server, connection, "hellcraft:paradiso", "paradiso_moon_lights", moonCentre[0], ParadisoGeometry.topY(Sphere.MOON) + 3,
					moonCentre[1], light[0], light[1], light[2]);
			server.runCommand("gamemode spectator Player0");
			seraphFight(context, server, connection);
			stylePanel(context, server, connection);
			// from beside the Moon's departure dais, up its column to Mercury's arrival isle
			double[] d = ParadisoGeometry.departure(Sphere.MOON);
			int moonTop = ParadisoGeometry.topY(Sphere.MOON);
			shot(context, server, connection, "hellcraft:paradiso", "paradiso_climb", d[0] + 9, moonTop + 2, d[1] + 5, d[0], moonTop + ParadisoGeometry.STEP, d[1]);
		}
	}

	/**
	 * The Seraph's fight on the Primum Mobile's floor, against a dummy (screenshots seraph_fight_*): its descent, then each
	 * blow as its danger mark fills, its flare at half health, and its end.
	 */
	private static void seraphFight(ClientGameTestContext context, TestDedicatedServerContext server, TestDedicatedServerConnection connection) {
		double[] c = ParadisoGeometry.centre(Sphere.PRIMUM_MOBILE);
		int y = ParadisoGeometry.topY(Sphere.PRIMUM_MOBILE) + 1;
		server.runCommand(String.format(Locale.ROOT, "execute in hellcraft:paradiso run tp Player0 %.1f %d %.1f facing %.1f %d %.1f",
				c[0] - 9, y + 5, c[1] - 12, c[0], y + 3, c[1]));
		context.waitTicks(20);
		connection.waitForChunksRender();
		String out = server.computeOnServer(s -> GuardianManager.summon(s, Guardian.SERAPH));
		if (!out.contains("awakens")) {
			throw new AssertionError("The Seraph could not be summoned: " + out);
		}
		server.runCommand(String.format(Locale.ROOT, "execute in hellcraft:paradiso run summon minecraft:villager %.1f %d %.1f "
				+ "{NoAI:1b,Invulnerable:1b,PersistenceRequired:1b,Tags:[\"fight_dummy\"]}", c[0] - 3, y, c[1] - 4));
		context.waitTicks(SeraphAnim.INTRO.at("cry") + 6);
		context.takeScreenshot("seraph_fight_intro");
		context.waitTicks(SeraphAnim.INTRO.ticks - SeraphAnim.INTRO.at("cry") + 10);
		String[][] shots = {
				{"wings", String.valueOf(SeraphAnim.WINGS.at("flash") + 6)},
				{"fire", String.valueOf(SeraphAnim.FIRE.at("call") + 8)},
				{"coal", String.valueOf(SeraphAnim.COAL.at("throw") + 4)},
				{"gloria", String.valueOf(SeraphAnim.GLORIA.at("unveil") + 10)},
				{"turning", String.valueOf(SeraphAnim.TURNING.at("turn") + 14)},
				{"host", String.valueOf(SeraphAnim.HOST.at("host") + 10)},
		};
		for (String[] shot : shots) {
			String used = server.computeOnServer(s -> GuardianManager.attack(Guardian.SERAPH, shot[0]));
			if (!used.contains(" uses ")) {
				throw new AssertionError("The Seraph could not use " + shot[0] + ": " + used);
			}
			context.waitTicks(Integer.parseInt(shot[1]));
			context.takeScreenshot("seraph_fight_" + shot[0]);
			context.waitTicks(90);
		}
		server.runCommand("execute in hellcraft:paradiso run kill @e[type=minecraft:vex]");
		server.runOnServer(s -> Paradiso.level(s).getEntities(HellEntities.SERAPH, e -> true).forEach(v -> v.setHealth(v.getMaxHealth() * 0.45f)));
		context.waitTicks(SeraphAnim.PHASE.at("flare") + 4);
		context.takeScreenshot("seraph_fight_flare");
		context.waitTicks(80);
		server.computeOnServer(s -> GuardianManager.slay(Guardian.SERAPH));
		context.waitTicks(SeraphAnim.DEATH.at("yield") + 10);
		context.takeScreenshot("seraph_fight_yield");
		context.waitTicks(SeraphAnim.DEATH.ticks);
		server.runCommand("execute in hellcraft:paradiso run kill @e[tag=fight_dummy]");
	}

	/**
	 * The style panel (screenshot style_panel): a player in the Seraph's fight (unhurtable for the test) strikes it a few
	 * times; their rank, meter and the feed of what scored show on the right of the screen.
	 */
	private static void stylePanel(ClientGameTestContext context, TestDedicatedServerContext server, TestDedicatedServerConnection connection) {
		double[] c = ParadisoGeometry.centre(Sphere.PRIMUM_MOBILE);
		int y = ParadisoGeometry.topY(Sphere.PRIMUM_MOBILE) + 1;
		// (the spheres open one at a time: a survival player must have passed the trials below the Primum Mobile)
		server.runCommand("hellcraft paradiso pass Player0 8");
		server.runCommand("gamemode survival Player0");
		server.runCommand("effect give Player0 minecraft:resistance 600 4 true");
		server.runCommand("effect give Player0 minecraft:regeneration 600 4 true");
		server.runCommand(String.format(Locale.ROOT, "execute in hellcraft:paradiso run tp Player0 %.1f %d %.1f facing %.1f %d %.1f",
				c[0] - 4, y, c[1] - 4, c[0], y + 3, c[1]));
		context.runOnClient(client -> {
			if (client.gui.hud.isHidden()) {
				client.gui.hud.toggle();
			}
		});
		context.waitTicks(20);
		String out = server.computeOnServer(s -> GuardianManager.summon(s, Guardian.SERAPH));
		if (!out.contains("awakens")) {
			throw new AssertionError("The Seraph could not be summoned again: " + out);
		}
		context.waitTicks(SeraphAnim.INTRO.ticks + 20);
		server.runCommand("item replace entity Player0 weapon.mainhand with minecraft:netherite_sword");
		for (int i = 0; i < 6; i++) {
			server.runOnServer(s -> {
				var p = s.getPlayerList().getPlayerByName("Player0");
				Paradiso.level(s).getEntities(HellEntities.SERAPH, e -> true).forEach(seraph -> {
					seraph.setPermanentlyInvulnerable(false);
					p.resetAttackStrengthTicker();
					p.attack(seraph);
				});
			});
			context.waitTicks(14);
		}
		context.waitTicks(4);
		String state = context.computeOnClient(client -> net.thesmallthings.hellcraft.client.StylePanel.state());
		context.takeScreenshot("style_panel");
		if (state.equals("off")) {
			throw new AssertionError("the style panel didn't come up in the fight");
		}
		server.computeOnServer(s -> GuardianManager.stop(Guardian.SERAPH));
		server.runCommand("effect clear Player0");
		server.runCommand("gamemode spectator Player0");
		context.runOnClient(client -> {
			if (!client.gui.hud.isHidden()) {
				client.gui.hud.toggle();
			}
		});
	}

	/** /hellcraft loadout puts the armour on and hands over the rest, the full kit and a journey kit (here, before the camera tour). */
	private static void loadout(TestDedicatedServerContext server) {
		server.runCommand("hellcraft loadout Player0");
		String kit = server.computeOnServer(s -> {
			var p = s.getPlayerList().getPlayerByName("Player0");
			return p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getItem() + " "
					+ p.getInventory().countItem(net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE) + " "
					+ p.getInventory().countItem(net.minecraft.world.item.Items.ENDER_PEARL);
		});
		if (!kit.contains("netherite_chestplate 16 16")) {
			throw new AssertionError("the loadout is wrong: " + kit);
		}
		server.runCommand("clear Player0");
		// a journey kit: about what you'd have by upper Hell, not everything
		server.runCommand("hellcraft loadout Player0 upper");
		String upper = server.computeOnServer(s -> {
			var p = s.getPlayerList().getPlayerByName("Player0");
			return p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getItem() + " "
					+ p.getInventory().countItem(net.minecraft.world.item.Items.GOLDEN_APPLE) + " "
					+ p.getInventory().countItem(net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE);
		});
		if (!upper.contains("iron_chestplate 2 0")) {
			throw new AssertionError("the upper Hell kit is wrong: " + upper);
		}
		server.runCommand("clear Player0");
	}

	private static void shot(ClientGameTestContext context, TestDedicatedServerContext server, TestDedicatedServerConnection connection,
							 String dimension, String name, double x, double y, double z, double lx, double ly, double lz) {
		server.runCommand(String.format(Locale.ROOT, "execute in %s run tp Player0 %.1f %.1f %.1f facing %.1f %.1f %.1f", dimension, x, y, z, lx, ly, lz));
		context.waitTicks(30);
		connection.waitForChunksRender();
		context.waitTicks(20);
		context.takeScreenshot(name);
	}
}
