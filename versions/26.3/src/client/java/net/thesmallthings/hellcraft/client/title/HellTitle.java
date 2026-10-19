package net.thesmallthings.hellcraft.client.title;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.thesmallthings.hellcraft.HellcraftMod;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hellcraft's title screen (mixin/TitleScreenMixin, mixin/ScreenMixin): the menus' turning panorama is a view of Hell
 * (the panorama textures in assets/minecraft, from the client test PanoramaTest), darkened and lit red from below,
 * with embers rising through it; the HELLCRAFT logo (gen_textures.py title_logo) in place of Minecraft's, the gate's
 * words under it, a line of Dante (or a plain hint) for the splash, and the mod's version in the corner.
 */
public final class HellTitle {
	private HellTitle() {
	}

	private static final Identifier LOGO = HellcraftMod.id("textures/gui/title/hellcraft.png");
	/** The logo's flames rise above the letters: the texture starts this far above where Minecraft's logo would. */
	private static final int FLAMES = 12;
	private static final Component GATE = Component.literal("Lasciate ogne speranza, voi ch'intrate");
	private static final int EMBERS = 70;

	/** The splash: Dante's words, the game's own hints, and a little gallows humour. */
	private static final List<String> SPLASHES = List.of(
			"Abandon all hope!",
			"Midway upon the journey of our life...",
			"The way down is the way out!",
			"Love moves the sun and the other stars.",
			"Hell is full!",
			"Blood is fuel!",
			"Now with nine circles!",
			"Red: get out! White: block! Purple: jump!",
			"Sneak through the Wood!",
			"Don't look at Medusa!",
			"Light the braziers!",
			"Pape Satàn, pape Satàn aleppe!",
			"Through me the way into the woeful city!",
			"There is no greater sorrow...",
			"Virgil is waiting at the Rest!",
			"Mind the Slag Sea!",
			"Seven P's to burn!",
			"Lucifer is listening!",
			"The stars, again!");

	public static Component splash() {
		return Component.literal(SPLASHES.get(ThreadLocalRandom.current().nextInt(SPLASHES.size())));
	}

	/** In place of Minecraft's logo: HELLCRAFT, burning, and the gate's words under it. */
	public static void logo(GuiGraphicsExtractor g, int width, float alpha) {
		int x = width / 2 - 128;
		int y = 30 - FLAMES;
		g.blit(RenderPipelines.GUI_TEXTURED, LOGO, x, y, 0.0F, 0.0F, 256, 64, 256, 64, ARGB.white(alpha));
		Font font = Minecraft.getInstance().font;
		int glow = (int) (200 + 55 * Math.sin(Util.getMillis() / 700.0));
		g.text(font, GATE, width / 2 - font.width(GATE) / 2, y + 62, ARGB.color(alpha, ARGB.color(glow, 60, 40)), true);
	}

	/** Over the panorama: darkness above, a red glow from below, and embers rising. */
	public static void underworld(GuiGraphicsExtractor g, int width, int height) {
		g.fillGradient(0, 0, width, height / 2, 0x90000000, 0x30200000);
		g.fillGradient(0, height / 2, width, height, 0x30200000, 0xA0701005);
		double t = Util.getMillis() / 1000.0;
		for (int i = 0; i < EMBERS; i++) {
			// each ember has its own speed, start and sway, worked out from its number alone
			double r1 = hash(i * 3 + 1);
			double r2 = hash(i * 3 + 2);
			double r3 = hash(i * 3 + 3);
			double speed = 18 + r2 * 40;
			double travel = height + 40;
			double rise = (t * speed + r3 * travel) % travel;
			int y = (int) (height + 10 - rise);
			int x = (int) (r1 * width + Math.sin(t * (0.6 + r3) + i) * (6 + r2 * 10));
			float life = (float) (rise / travel);
			float flicker = 0.6f + 0.4f * (float) Math.sin(t * 9 + i * 1.7);
			int a = (int) (255 * Mth.clamp((1 - life) * 1.4f, 0, 1) * flicker);
			if (a <= 4) {
				continue;
			}
			int colour = life < 0.3f ? 0xFFE070 : life < 0.65f ? 0xFF8A20 : 0xC02810;
			int size = r2 > 0.85 ? 2 : 1;
			g.fill(x, y, x + size, y + size, ARGB.color(a, colour));
			if (size == 2) {
				g.fill(x - 1, y + 1, x + 3, y + 2, ARGB.color(a / 3, colour));
			}
		}
	}

	/** The mod's version, over Minecraft's in the corner. */
	public static void version(GuiGraphicsExtractor g, int height, float alpha) {
		Font font = Minecraft.getInstance().font;
		String version = HellcraftMod.VERSION.split("\\+")[0];
		g.text(font, "Hellcraft " + version, 2, height - 20, ARGB.color(alpha, 0xFF6A40), true);
	}

	private static double hash(int n) {
		long h = n * 0x9E3779B97F4A7C15L;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return (h >>> 11) * 0x1.0p-53;
	}
}
