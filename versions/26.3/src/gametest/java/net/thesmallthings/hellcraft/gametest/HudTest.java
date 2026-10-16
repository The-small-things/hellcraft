package net.thesmallthings.hellcraft.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.thesmallthings.hellcraft.blood.BloodItems;
import net.thesmallthings.hellcraft.blood.HellItems;

/**
 * The Blood Heart HUD and the blood items, in single player: hearts rimmed in a prestige rank's colour, and the
 * five blood items in the hotbar and the inventory.
 */
public class HudTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival Player0");
			world.getServer().runCommand("hellcraft prestige Player0 set 3");
			world.getServer().runCommand("give Player0 hellcraft:blood_heart 3");
			world.getServer().runCommand("give Player0 hellcraft:blood_fragment 8");
			world.getServer().runCommand("give Player0 hellcraft:lucifers_bane");
			world.getServer().runCommand("give Player0 hellcraft:vigil_candle 2");
			world.getServer().runCommand("give Player0 hellcraft:soul_anchor");
			// the blood gear, real items since 3.0, and an infernal piece
			for (String gear : new String[]{"bloodletter", "reaper_of_minos", "tithe_axe", "blood_pickaxe", "blood_helmet", "blood_chestplate",
					"blood_leggings", "blood_boots", "infernal_bloodletter"}) {
				world.getServer().runCommand("give Player0 hellcraft:" + gear);
			}
			context.waitTicks(20);
			int carried = world.getServer().computeOnServer(server -> {
				var player = server.getPlayerList().getPlayerByName("Player0");
				return BloodItems.countHearts(player) + BloodItems.countFragments(player);
			});
			if (carried != 11) {
				throw new AssertionError("expected 3 Blood Hearts and 8 Blood Fragments, counted " + carried);
			}
			context.takeScreenshot("hud_blood_hearts_rank3");
			context.setScreen(() -> new InventoryScreen(context.computeOnClient(client -> client.player)));
			context.waitTicks(5);
			context.takeScreenshot("inventory_blood_items");
			if (!context.computeOnClient(client -> client.player.getInventory().contains(stack -> stack.is(HellItems.BLOOD_HEART)))) {
				throw new AssertionError("the client doesn't see the Blood Hearts in the inventory");
			}
		}
	}
}
