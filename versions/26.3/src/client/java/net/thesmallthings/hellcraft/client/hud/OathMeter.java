package net.thesmallthings.hellcraft.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.HellWeapons;

/**
 * The Blood Oath meter: a thin line resting on the stamina bar (over the food bar; HudLayout's ABOVE_FOOD, after the stamina
 * bar, so it sits on it when that shows and takes its place when it doesn't). Under an oath it is a bright blood-red line
 * that drains over the oath's 30 seconds, beating like a pulse in its last five; while the oath cools down it is a dim line
 * filling back up, shown only with a hell weapon in hand; when the oath is ready again it flashes once and fades.
 */
public final class OathMeter implements HudElement {
	public static final Identifier ID = HellcraftMod.id("oath");
	public static final int WIDTH = 81;
	/** The bar is 2 high inside a 1 pixel frame. */
	public static final int HEIGHT = 4;

	private static float alpha;
	private static float lastAlpha;
	private static int ticks;

	/** Is there anything to show? */
	private static boolean wanted(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || player.isSpectator()) {
			return false;
		}
		if (BloodClient.underOath() || BloodClient.sinceOathReady() < 40) {
			return true;
		}
		return BloodClient.cooling() && HellWeapons.of(player.getMainHandItem()) != null;
	}

	public static void tick(Minecraft mc) {
		if (mc.isPaused()) {
			return;
		}
		ticks++;
		lastAlpha = alpha;
		alpha = Mth.clamp(alpha + (wanted(mc) ? 0.2f : -0.08f), 0f, 1f);
	}

	public static void reset() {
		alpha = lastAlpha = 0;
	}

	/** For HudLayout: its size while it shows. */
	public static HudLayout.Size measure(Minecraft mc, int width, int height) {
		return alpha > 0.01f || lastAlpha > 0.01f ? new HudLayout.Size(WIDTH + 6, HEIGHT) : null;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		HudLayout.Box box = HudLayout.box(ID);
		float partial = delta.getGameTimeDeltaPartialTick(false);
		float a = Mth.lerp(partial, lastAlpha, alpha);
		if (box == null || a <= 0.01f || mc.gui.hud.isHidden()) {
			return;
		}
		int left = box.x() + 5;
		int right = left + WIDTH;
		int y = box.y() + 1;
		float t = ticks + partial;
		g.fill(left - 1, y - 1, right + 1, y + 3, ARGB.color(a * 0.7f, 0x100606));
		g.fill(left, y, right, y + 2, ARGB.color(a * 0.8f, 0x2A1414));
		if (BloodClient.underOath()) {
			float left01 = BloodClient.oath();
			int to = left + Math.round(WIDTH * left01);
			// the last five seconds beat like a pulse
			float pulse = left01 < 1f / 6f ? 0.5f + 0.5f * Mth.sin(t * 0.9f) : 1f;
			int colour = ARGB.srgbLerp(pulse, 0xFF7A0A0A, 0xFFE0202A);
			if (to > left) {
				g.fill(left, y, to, y + 2, ARGB.multiplyAlpha(colour, a));
				g.fill(left, y, to, y + 1, ARGB.color(a * 0.45f, 0xFFB0A0));
				// the bright head of the line, where it drains from
				g.fill(Math.max(left, to - 2), y - 1, to, y + 3, ARGB.color(a * pulse, 0xFF5040));
			}
		} else if (BloodClient.cooling()) {
			int to = left + Math.round(WIDTH * BloodClient.recovered());
			if (to > left) {
				g.fill(left, y, to, y + 2, ARGB.color(a * 0.6f, 0x6A1A1E));
			}
		} else {
			// ready again: a flash along the whole line, fading
			float flash = Math.max(0f, 1f - BloodClient.sinceOathReady() / 40f);
			g.fill(left, y, right, y + 2, ARGB.color(a * (0.4f + 0.6f * flash), 0xC8202A));
		}
		// a drop at the left end marks it as the oath's (blood), not stamina
		g.fill(left - 4, y, left - 2, y + 2, ARGB.color(a, 0xB01018));
		g.fill(left - 3, y - 1, left - 2, y, ARGB.color(a, 0xB01018));
	}
}
