package net.thesmallthings.hellcraft.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.Pilgrimage;
import net.thesmallthings.hellcraft.net.PilgrimPayload;

/**
 * The pilgrim's mark (Pilgrimage): while on a pilgrimage, a scallop shell at the top left with the run's time, ticking on
 * between the server's updates, and the deepest circle reached (red when the server makes it hardcore).
 */
public final class PilgrimHud implements HudElement {
	public static final Identifier ID = HellcraftMod.id("pilgrim");
	private static final Identifier SHELL = HellcraftMod.id("textures/item/pilgrim_shell.png");

	private static volatile boolean on;
	private static volatile int ticks;
	private static volatile int deepest;
	private static volatile boolean hardcore;
	/** Client ticks since the last update, so the clock runs smoothly. */
	private static volatile int since;

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(PilgrimPayload.TYPE, (payload, context) -> receive(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> on = false);
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(PilgrimHud::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, ID, new PilgrimHud());
	}

	public static void receive(PilgrimPayload payload) {
		on = payload.on();
		ticks = payload.ticks();
		deepest = payload.deepest();
		hardcore = payload.hardcore();
		since = 0;
	}

	/** For the Journal: null when not on a pilgrimage, else "1h 02m, deepest Greed". */
	public static String summary() {
		return on ? Pilgrimage.time(ticks + since) + ", deepest " + Pilgrimage.depthName(deepest) + (hardcore ? " (hardcore)" : "") : null;
	}

	public static void tick(Minecraft client) {
		if (on && client.level != null && !client.isPaused() && since < 20 * 5) {
			since++;
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (!on) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		int x = 4;
		int y = 4;
		String time = "Pilgrimage " + Pilgrimage.time(ticks + since);
		String depth = "Deepest: " + Pilgrimage.depthName(deepest);
		int width = Math.max(mc.font.width(time), mc.font.width(depth));
		if (x + 24 + width > g.guiWidth() / 2 - 102) {
			// a narrow screen: below the compass instead of beside it
			y = 44;
		}
		g.fill(x - 2, y - 2, x + 22 + width, y + 20, ARGB.black(110));
		g.blit(RenderPipelines.GUI_TEXTURED, SHELL, x, y + 1, 0.0F, 0.0F, 16, 16, 16, 16, ARGB.white(1.0f));
		g.text(mc.font, time, x + 20, y, hardcore ? 0xFFFF6050 : 0xFFF0D090);
		g.text(mc.font, depth, x + 20, y + 10, 0xFFB0A890);
	}
}
