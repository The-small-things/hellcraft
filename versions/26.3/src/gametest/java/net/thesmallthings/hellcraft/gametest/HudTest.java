package net.thesmallthings.hellcraft.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.thesmallthings.hellcraft.blood.BloodItems;
import net.thesmallthings.hellcraft.blood.HellItems;
import net.thesmallthings.hellcraft.blood.Relics;

/**
 * The Blood Heart HUD and the blood items, in single player: hearts rimmed in a prestige rank's colour, and the
 * five blood items in the hotbar and the inventory, and the Halo and Seraph Wings as worn.
 */
public class HudTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		names(context);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival Player0");
			world.getServer().runCommand("hellcraft prestige Player0 set 3");
			world.getServer().runCommand("give Player0 hellcraft:blood_heart 3");
			world.getServer().runCommand("give Player0 hellcraft:blood_fragment 8");
			world.getServer().runCommand("give Player0 hellcraft:lucifers_bane");
			world.getServer().runCommand("give Player0 hellcraft:vigil_candle 2");
			world.getServer().runCommand("give Player0 hellcraft:soul_anchor");
			// the blood gear, real items since 3.0, an infernal piece, and Paradiso's relics
			for (String gear : new String[]{"bloodletter", "reaper_of_minos", "tithe_axe", "blood_pickaxe", "blood_helmet", "blood_chestplate",
					"blood_leggings", "blood_boots", "infernal_bloodletter", "halo", "seraph_wings", "beatrices_rose"}) {
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
			relicsWorn(context, world);
			doubleUse(context, world);
		}
	}

	/**
	 * One right-click on the ground uses a Hellcraft item once: the click reaches the server as a use on the block and
	 * again as a use in the air, which used to spend two (two Vigil Candles, or 16 fragments into two hearts). The item's
	 * cooldown stops the second, and a quick second click; one a moment later works.
	 */
	private static void doubleUse(ClientGameTestContext context, TestSingleplayerContext world) {
		context.setScreen(() -> null);
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		world.getServer().runCommand("clear Player0");
		world.getServer().runCommand("item replace entity Player0 hotbar.0 with hellcraft:blood_fragment 16");
		world.getServer().runCommand("tp Player0 ~ ~ ~ 0 90");
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
		context.waitTicks(10);
		context.getInput().pressKey(options -> options.keyUse);
		context.waitTicks(4);
		context.getInput().pressKey(options -> options.keyUse);
		context.waitTicks(10);
		int[] first = carried(world);
		if (first[0] != 1 || first[1] != 8) {
			throw new AssertionError("one click (and a quick second) on the ground made " + first[0] + " Blood Heart(s), leaving "
					+ first[1] + " fragments: expected 1 and 8");
		}
		context.waitTicks(10);
		context.getInput().pressKey(options -> options.keyUse);
		context.waitTicks(10);
		int[] second = carried(world);
		if (second[0] != 2 || second[1] != 0) {
			throw new AssertionError("a click after the cooldown made " + second[0] + " heart(s) in all, leaving " + second[1] + " fragments: expected 2 and 0");
		}
	}

	/** The Blood Hearts and Blood Fragments Player0 carries. */
	private static int[] carried(TestSingleplayerContext world) {
		return world.getServer().computeOnServer(server -> {
			var player = server.getPlayerList().getPlayerByName("Player0");
			return new int[]{BloodItems.countHearts(player), BloodItems.countFragments(player)};
		});
	}

	/** The Halo and the Seraph Wings as worn, seen from in front and behind (relics_worn_front, relics_worn_back). */
	private static void relicsWorn(ClientGameTestContext context, TestSingleplayerContext world) {
		context.setScreen(() -> null);
		world.getServer().runCommand("item replace entity Player0 armor.head with hellcraft:halo");
		world.getServer().runCommand("item replace entity Player0 armor.chest with hellcraft:seraph_wings");
		world.getServer().runCommand("tp Player0 ~ ~ ~ 0 10");
		String worn = world.getServer().computeOnServer(server -> {
			var player = server.getPlayerList().getPlayerByName("Player0");
			return Relics.of(player.getItemBySlot(EquipmentSlot.HEAD)) + " " + Relics.of(player.getItemBySlot(EquipmentSlot.CHEST))
					+ " " + player.getItemBySlot(EquipmentSlot.CHEST).has(DataComponents.GLIDER);
		});
		if (!worn.equals("HALO SERAPH_WINGS true")) {
			throw new AssertionError("the relics aren't worn: " + worn);
		}
		context.runOnClient(client -> {
			client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			if (!client.gui.hud.isHidden()) {
				client.gui.hud.toggle();
			}
		});
		context.waitTicks(10);
		context.takeScreenshot("relics_worn_front");
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.waitTicks(10);
		context.takeScreenshot("relics_worn_back");
		context.runOnClient(client -> {
			client.options.setCameraType(CameraType.FIRST_PERSON);
			client.gui.hud.toggle();
		});
	}

	/**
	 * Every Hellcraft item, block and creature has a name in the language file (tools/gen_models.py): screens that look a
	 * thing up by its translation key (recipe viewers, statistics) otherwise show the bare key.
	 */
	private static void names(ClientGameTestContext context) {
		String missing = context.computeOnClient(client -> {
			net.minecraft.locale.Language lang = net.minecraft.locale.Language.getInstance();
			java.util.List<String> keys = new java.util.ArrayList<>();
			net.minecraft.core.registries.BuiltInRegistries.ITEM.forEach(i -> keys.add(i.getDescriptionId()));
			net.minecraft.core.registries.BuiltInRegistries.BLOCK.forEach(b -> keys.add(b.getDescriptionId()));
			net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.forEach(e -> keys.add(e.getDescriptionId()));
			return String.join(", ", keys.stream().filter(k -> k.contains("hellcraft") && !lang.has(k)).sorted().toList());
		});
		if (!missing.isEmpty()) {
			throw new AssertionError("these have no name in assets/hellcraft/lang/en_us.json (tools/gen_models.py): " + missing);
		}
	}
}
