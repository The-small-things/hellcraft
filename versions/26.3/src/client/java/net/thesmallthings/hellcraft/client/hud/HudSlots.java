package net.thesmallthings.hellcraft.client.hud;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.thesmallthings.hellcraft.client.PilgrimHud;
import net.thesmallthings.hellcraft.client.StylePanel;
import net.thesmallthings.hellcraft.client.combat.StaminaHud;
import net.thesmallthings.hellcraft.client.combat.anim.Settings;
import net.thesmallthings.hellcraft.client.dialogue.DialogueHud;
import net.thesmallthings.hellcraft.client.nav.CompassHud;
import net.thesmallthings.hellcraft.net.BloodPayload;

/**
 * The one place where every piece of Hellcraft's HUD is given its spot (HudLayout): its anchor and its place in the queue,
 * lower first, so when space runs short in a busy fight the pieces that matter most keep theirs.
 * <ol>
 *     <li>10: the stamina bar, over the food bar;</li>
 *     <li>11: the Blood Oath meter, resting on the stamina bar;</li>
 *     <li>20: the message box, bottom middle, over everything at the bottom;</li>
 *     <li>30: the minimap, in its corner (top left unless moved);</li>
 *     <li>40: the style panel, top right under the status effects;</li>
 *     <li>50: the compass, top middle (it gives way to boss bars);</li>
 *     <li>60: the pilgrim's mark, top left (under the minimap).</li>
 * </ol>
 * Also here: the blood aura on the hotbar, the oath meter's and the minimap's ticks, and /hellcraft-client minimap.
 */
public final class HudSlots {
	private HudSlots() {
	}

	private static boolean openSettings;

	public static void register() {
		HudLayout.register(StaminaHud.ID, () -> HudLayout.Anchor.ABOVE_FOOD, 10, StaminaHud::measure);
		HudLayout.register(OathMeter.ID, () -> HudLayout.Anchor.ABOVE_FOOD, 11, OathMeter::measure);
		HudLayout.register(DialogueHud.ID, () -> HudLayout.Anchor.BOTTOM_CENTER, 20, DialogueHud::measure);
		HudLayout.register(Minimap.ID, Minimap::anchor, 30, Minimap::measure);
		HudLayout.register(StylePanel.ID, () -> HudLayout.Anchor.TOP_RIGHT, 40, StylePanel::measure);
		HudLayout.register(CompassHud.ID, () -> HudLayout.Anchor.TOP_CENTER, 50, CompassHud::measure);
		HudLayout.register(PilgrimHud.ID, () -> HudLayout.Anchor.TOP_LEFT, 60, PilgrimHud::measure);

		// the layout is worked out first, before anything else of the HUD is drawn
		HudElementRegistry.addFirst(HudLayout.ID, HudLayout.INSTANCE);
		HudElementRegistry.attachElementBefore(VanillaHudElements.HOTBAR, BloodAura.UNDER_ID, BloodAura.UNDER);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, BloodAura.OVER_ID, BloodAura.OVER);
		HudElementRegistry.attachElementAfter(StaminaHud.ID, OathMeter.ID, new OathMeter());
		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, Minimap.ID, new Minimap());
		Minimap.register();

		ClientPlayNetworking.registerGlobalReceiver(BloodPayload.TYPE, (payload, context) -> BloodClient.receive(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			BloodClient.reset();
			OathMeter.reset();
			Minimap.reset();
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			BloodClient.tick(client);
			BloodAura.tick(client);
			OathMeter.tick(client);
			Minimap.tick(client);
			if (openSettings && client.gui.screen() == null) {
				openSettings = false;
				client.gui.setScreen(new MinimapScreen(null));
			}
		});
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
				ClientCommands.literal("hellcraft-client").then(ClientCommands.literal("minimap")
						.executes(ctx -> minimap(ctx.getSource(), null))
						.then(ClientCommands.literal("on").executes(ctx -> minimap(ctx.getSource(), true)))
						.then(ClientCommands.literal("off").executes(ctx -> minimap(ctx.getSource(), false)))
						.then(ClientCommands.literal("settings").executes(ctx -> {
							// after the chat screen has closed
							openSettings = true;
							return 1;
						})))));
	}

	private static int minimap(FabricClientCommandSource source, Boolean on) {
		if (on != null) {
			Settings.minimap().on = on;
			Settings.save();
		}
		source.sendFeedback(Component.literal(Minimap.describe()));
		return 1;
	}

	/** For the client test: is the HUD laid out with nothing over anything else this frame? */
	public static boolean clear(Minecraft mc) {
		return HudLayout.clear();
	}
}
