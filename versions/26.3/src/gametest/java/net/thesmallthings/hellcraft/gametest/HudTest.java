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
import net.thesmallthings.hellcraft.client.combat.anim.Settings;
import net.thesmallthings.hellcraft.client.layer.MobHealthBars;

/**
 * The Blood Heart HUD and the blood items, in single player: hearts rimmed in a prestige rank's colour, and the
 * five blood items in the hotbar and the inventory, the Halo and Seraph Wings as worn, and mobs' health bars.
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
					"blood_leggings", "blood_boots", "infernal_bloodletter", "infernal_blood_helmet", "infernal_blood_chestplate", "halo", "seraph_wings", "beatrices_rose"}) {
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
			armourWorn(context, world);
			doubleUse(context, world);
			healthBars(context, world);
			newHud(context, world);
		}
	}

	/**
	 * The HUD of 3.6.1: the blood aura on hotbar slots with a Blood Art ready (hud_blood_aura), the Blood Oath meter over the
	 * stamina bar (hud_oath_meter), the style panel at S, SSS and HELLISH (hud_style_*), the rank bonus on the server, the
	 * minimap in its shapes and views (hud_minimap_*), and everything at once with boss bars, effects and messages, checking
	 * that HudLayout put nothing over anything else (hud_chaos).
	 */
	private static void newHud(ClientGameTestContext context, TestSingleplayerContext world) {
		context.setScreen(() -> null);
		world.getServer().runCommand("time set noon");
		world.getServer().runCommand("item replace entity Player0 hotbar.0 with hellcraft:bloodletter");
		world.getServer().runCommand("item replace entity Player0 hotbar.1 with hellcraft:reaper_of_minos");
		world.getServer().runCommand("item replace entity Player0 hotbar.4 with hellcraft:tithe_axe");
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
		world.getServer().runCommand("hellcraft charge Player0");
		context.waitTicks(20);
		boolean aura = context.computeOnClient(client -> net.thesmallthings.hellcraft.client.hud.BloodAura.shows(client.player, 0)
				&& net.thesmallthings.hellcraft.client.hud.BloodAura.shows(client.player, 4)
				&& !net.thesmallthings.hellcraft.client.hud.BloodAura.shows(client.player, 2));
		if (!aura) {
			throw new AssertionError("a full charge should put the blood aura on the hell weapons' slots (and only theirs)");
		}
		boolean glint = world.getServer().computeOnServer(server -> server.getPlayerList().getPlayerByName("Player0")
				.getInventory().getItem(0).has(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
		if (glint) {
			throw new AssertionError("a charged hell weapon still gets the enchantment glint");
		}
		context.takeScreenshot("hud_blood_aura");
		// an oath being sworn: the meter on the stamina bar, as the server would send it
		context.runOnClient(client -> {
			net.thesmallthings.hellcraft.client.hud.BloodClient.set(100, 420, 3600);
			net.thesmallthings.hellcraft.client.combat.StaminaHud.predict(40);
		});
		context.waitTicks(10);
		context.takeScreenshot("hud_oath_meter");
		if (context.computeOnClient(client -> net.thesmallthings.hellcraft.client.hud.HudLayout.target(
				net.thesmallthings.hellcraft.client.hud.OathMeter.ID)) == null) {
			throw new AssertionError("the oath meter has no place on the HUD");
		}

		// the style panel at S, SSS and HELLISH
		String[] ranks = {"s", "sss", "hellish"};
		int[] ordinals = {4, 6, 7};
		for (int i = 0; i < ranks.length; i++) {
			int ordinal = ordinals[i];
			context.runOnClient(client -> net.thesmallthings.hellcraft.client.StylePanel.preview(ordinal, 0.65f, "+ SWORD FINISHER x1.6"));
			context.waitTicks(12);
			context.takeScreenshot("hud_style_" + ranks[i]);
		}
		// the rank bonus on the server: HELLISH makes Player0 faster, quicker and longer-reaching; it comes off again
		String bonus = world.getServer().computeOnServer(server -> {
			var player = server.getPlayerList().getPlayerByName("Player0");
			double speed = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
			double reach = player.entityInteractionRange();
			net.thesmallthings.hellcraft.hazard.RankBonus.apply(player, net.thesmallthings.hellcraft.blood.Judgement.Grade.HELLISH);
			double fast = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
			double far = player.entityInteractionRange();
			net.thesmallthings.hellcraft.hazard.RankBonus.clear(player);
			double after = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
			return fast > speed * 1.1 && far > reach + 0.5 && Math.abs(after - speed) < 1.0e-6 ? "ok" : speed + " " + fast + " " + after + " " + reach + " " + far;
		});
		if (!bonus.equals("ok")) {
			throw new AssertionError("the HELLISH rank bonus is wrong: " + bonus);
		}

		// the minimap: a circle turning with the player (the default), a square with north up in the top right, the cave view
		context.waitTicks(40);
		if (context.computeOnClient(client -> net.thesmallthings.hellcraft.client.hud.Minimap.middleColour()) == 0) {
			throw new AssertionError("the minimap drew nothing");
		}
		context.takeScreenshot("hud_minimap_circle");
		context.runOnClient(client -> {
			Settings.MapPrefs p = Settings.minimap();
			p.shape = Settings.Shape.SQUARE;
			p.rotate = false;
			p.corner = Settings.Corner.TOP_RIGHT;
		});
		context.waitTicks(10);
		context.takeScreenshot("hud_minimap_square");
		context.runOnClient(client -> Settings.minimap().caves = Settings.Caves.ON);
		context.waitTicks(40);
		context.takeScreenshot("hud_minimap_caves");
		context.runOnClient(client -> {
			Settings.MapPrefs p = Settings.minimap();
			p.shape = Settings.Shape.CIRCLE;
			p.rotate = true;
			p.corner = Settings.Corner.TOP_LEFT;
			p.caves = Settings.Caves.AUTO;
		});

		// everything at once: boss bars, effects of both kinds, messages, the style panel, stamina, the oath, the minimap
		world.getServer().runCommand("bossbar add hellcraft:hud_test \"A Guardian\"");
		world.getServer().runCommand("bossbar set hellcraft:hud_test players Player0");
		world.getServer().runCommand("effect give Player0 speed 60 0 true");
		world.getServer().runCommand("effect give Player0 strength 60 0 true");
		world.getServer().runCommand("effect give Player0 weakness 60 0 true");
		world.getServer().runCommand("effect give Player0 slowness 60 0 true");
		world.getServer().runCommand("execute as Player0 run hellcraft dialogue test");
		context.runOnClient(client -> {
			net.thesmallthings.hellcraft.client.StylePanel.preview(7, 0.4f, "- HIT");
			net.thesmallthings.hellcraft.client.combat.StaminaHud.predict(30);
		});
		context.waitTicks(20);
		boolean clear = context.computeOnClient(client -> net.thesmallthings.hellcraft.client.hud.HudLayout.clear());
		String placed = context.computeOnClient(client -> net.thesmallthings.hellcraft.client.hud.HudLayout.everything().toString());
		context.takeScreenshot("hud_chaos");
		if (!clear) {
			throw new AssertionError("HUD elements overlap: " + placed);
		}
		world.getServer().runCommand("bossbar remove hellcraft:hud_test");
		world.getServer().runCommand("effect clear Player0");
		context.runOnClient(client -> {
			net.thesmallthings.hellcraft.client.StylePanel.receive(new net.thesmallthings.hellcraft.net.StylePayload(false, 0, 0, "", 1, ""));
			net.thesmallthings.hellcraft.client.hud.BloodClient.reset();
		});
	}

	/** The blood armour and the infernal (reinforced) armour as worn, front and back (armour_blood_*, armour_infernal_*). */
	private static void armourWorn(ClientGameTestContext context, TestSingleplayerContext world) {
		context.setScreen(() -> null);
		world.getServer().runCommand("tp Player0 ~ ~ ~ 0 10");
		context.runOnClient(client -> {
			if (!client.gui.hud.isHidden()) {
				client.gui.hud.toggle();
			}
		});
		for (String set : new String[]{"blood", "infernal"}) {
			String prefix = set.equals("blood") ? "" : "infernal_";
			world.getServer().runCommand("item replace entity Player0 armor.head with hellcraft:" + prefix + "blood_helmet");
			world.getServer().runCommand("item replace entity Player0 armor.chest with hellcraft:" + prefix + "blood_chestplate");
			world.getServer().runCommand("item replace entity Player0 armor.legs with hellcraft:" + prefix + "blood_leggings");
			world.getServer().runCommand("item replace entity Player0 armor.feet with hellcraft:" + prefix + "blood_boots");
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(10);
			context.takeScreenshot("armour_" + set + "_front");
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(10);
			context.takeScreenshot("armour_" + set + "_back");
		}
		context.runOnClient(client -> {
			client.options.setCameraType(CameraType.FIRST_PERSON);
			client.gui.hud.toggle();
		});
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

	/**
	 * Mobs' health bars (client layer/MobHealthBars): a hurt husk (red) and a hurt pig (green) ahead show bars over their
	 * heads, then upright beside them (health_bars_above, health_bars_side).
	 */
	private static void healthBars(ClientGameTestContext context, TestSingleplayerContext world) {
		context.setScreen(() -> null);
		world.getServer().runCommand("time set noon");
		world.getServer().runCommand("tp Player0 ~ ~ ~ 0 15");
		world.getServer().runCommand("execute as Player0 at Player0 run summon husk ^1.2 ^ ^4 {NoAI:1b,PersistenceRequired:1b,Health:7f,Rotation:[180f,0f]}");
		world.getServer().runCommand("execute as Player0 at Player0 run summon pig ^-1.4 ^ ^4.5 {NoAI:1b,PersistenceRequired:1b,Health:4f,Rotation:[180f,0f]}");
		context.waitTicks(30);
		int bars = context.computeOnClient(client -> MobHealthBars.count());
		if (bars < 2) {
			throw new AssertionError("expected health bars over the hurt husk and pig, found " + bars);
		}
		context.takeScreenshot("health_bars_above");
		context.runOnClient(client -> Settings.setHealthBars(Settings.HealthBars.SIDE));
		context.waitTicks(5);
		context.takeScreenshot("health_bars_side");
		context.runOnClient(client -> Settings.setHealthBars(Settings.HealthBars.ABOVE));
		world.getServer().runCommand("kill @e[type=husk]");
		world.getServer().runCommand("kill @e[type=pig]");
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
