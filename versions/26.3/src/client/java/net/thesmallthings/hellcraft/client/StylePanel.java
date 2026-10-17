package net.thesmallthings.hellcraft.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.Judgement.Grade;
import net.thesmallthings.hellcraft.client.cutscene.CutscenePlayer;
import net.thesmallthings.hellcraft.net.StylePayload;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The style panel (REVAMP section 12): during a boss fight, on the right of the screen, your style rank as a big
 * letter in its colour with its name, a meter to the next rank, and a feed of what just scored ("+ PARRY") or cost
 * ("- HIT"), newest on top, fading. The server's StyleMeter sends it (StylePayload); it slides away when the fight ends.
 */
public final class StylePanel implements HudElement {
	public static final Identifier ID = HellcraftMod.id("style");

	private static final int WIDTH = 104;
	private static final int FEED = 4;
	private static final int FEED_TICKS = 60;
	private static final int SLIDE = 8;
	/** The ranks' colours, D to SSS. */
	private static final int[] COLOURS = {0x8A8A8A, 0xC8C8C8, 0x5ADB6A, 0x4AD8F0, 0xE07AF0, 0xFFD84A, 0xFF4A3A};

	private record Event(String text, int born) {
	}

	private static final Deque<Event> FEED_LINES = new ArrayDeque<>();
	private static boolean active;
	private static int grade;
	private static float progress;
	private static float shownProgress;
	private static int ticks;
	/** 0 hidden to SLIDE fully on screen. */
	private static int slide;
	private static int rankedUpAt = -100;

	public static void receive(StylePayload payload) {
		if (payload.active() && !active) {
			FEED_LINES.clear();
			shownProgress = 0;
		}
		if (payload.grade() > grade && payload.active()) {
			rankedUpAt = ticks;
		}
		active = payload.active();
		grade = Math.max(0, Math.min(Grade.values().length - 1, payload.grade()));
		progress = payload.progress();
		if (!payload.event().isEmpty()) {
			FEED_LINES.addFirst(new Event(payload.event(), ticks));
			while (FEED_LINES.size() > FEED) {
				FEED_LINES.removeLast();
			}
		}
	}

	/** For the client test: is the panel up, and at which rank? */
	public static String state() {
		return active ? Grade.values()[grade].name() : "off";
	}

	public static void tick(Minecraft mc) {
		ticks++;
		slide = Math.max(0, Math.min(SLIDE, slide + (active ? 1 : -1)));
		shownProgress += (progress - shownProgress) * 0.3f;
		FEED_LINES.removeIf(e -> ticks - e.born() > FEED_TICKS);
		if (mc.level == null) {
			active = false;
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (slide == 0 || CutscenePlayer.active()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		Grade rank = Grade.values()[grade];
		int colour = COLOURS[grade];
		float alpha = slide / (float) SLIDE;
		int x = g.guiWidth() - 8 - WIDTH + (int) ((1 - alpha) * (WIDTH + 12));
		// high on the right, clear of the boss bar and of the dialogue box above the hotbar
		int y = 34;
		int h = 68 + FEED_LINES.size() * 10;
		g.fill(x, y, x + WIDTH, y + h, ARGB.color(alpha * 0.55f, 0x0C0808));
		g.fill(x, y, x + 2, y + h, ARGB.color(alpha, colour));

		// the rank: a big letter (shaken for a moment when it rises), and its name under it
		g.text(font, "STYLE", x + 8, y + 5, ARGB.color(alpha * 0.7f, 0xBBBBBB));
		float since = ticks - rankedUpAt + delta.getGameTimeDeltaPartialTick(false);
		float pop = since < 10 ? 1 + 0.35f * (1 - since / 10f) : 1;
		float scale = (rank.name().length() > 2 ? 2.4f : 3f) * pop;
		g.pose().pushMatrix();
		g.pose().translate(x + 8, y + 17);
		g.pose().scale(scale, scale);
		g.text(font, rank.name(), 0, 0, ARGB.color(alpha, colour));
		g.pose().popMatrix();
		g.text(font, rank.title, x + 8, y + 17 + (int) (9 * 3f) + 3, ARGB.color(alpha, colour));

		// the meter to the next rank
		int by = y + 60;
		int bw = WIDTH - 16;
		g.fill(x + 8, by, x + 8 + bw, by + 4, ARGB.color(alpha * 0.6f, 0x302828));
		g.fill(x + 8, by, x + 8 + Math.round(bw * Math.max(0f, Math.min(1f, shownProgress))), by + 4, ARGB.color(alpha, colour));

		// what just scored or cost, newest on top
		int fy = by + 9;
		for (Event e : FEED_LINES) {
			float age = (ticks - e.born()) / (float) FEED_TICKS;
			float a = alpha * Math.max(0f, 1f - age * age);
			int c = e.text().startsWith("-") ? 0xFF6A5A : 0xF2F2F2;
			String text = font.plainSubstrByWidth(e.text(), WIDTH - 16);
			g.text(font, text, x + 8, fy, ARGB.color(a, c));
			fy += 10;
		}
	}
}
