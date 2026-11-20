package net.thesmallthings.hellcraft.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.Judgement.Grade;
import net.thesmallthings.hellcraft.client.cutscene.CutscenePlayer;
import net.thesmallthings.hellcraft.client.hud.HudLayout;
import net.thesmallthings.hellcraft.net.StylePayload;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * The style panel (REVAMP section 12), reworked in 3.6.1: during a boss fight, a slanted dark plate on the right (HudLayout's
 * top right, under the status effects) with your style rank as a big outlined letter in its colour, its name spaced out
 * under it, the flow multiplier, a segmented meter to the next rank (with what was just lost draining behind it), what the
 * rank gives you while you hold it (hazard/RankBonus, from S up), and a feed of what just scored ("+ PARRY") or cost ("- HIT"),
 * newest on top, sliding in and fading.
 * <p>
 * Each rank looks a little wilder than the one under it: S and up glow from below, SS and SSS tremble, and HELLISH, the rank
 * after SSS, burns: its letters flicker between blood and fire, flames lick along the plate's foot and embers rise through
 * it. A rank gained flashes the plate and pops the letter; a rank lost shakes it red. The server's StyleMeter sends it all
 * (StylePayload); the plate slides away when the fight ends.
 */
public final class StylePanel implements HudElement {
	public static final Identifier ID = HellcraftMod.id("style");

	private static final int WIDTH = 120;
	private static final int FEED = 4;
	private static final int FEED_TICKS = 60;
	private static final int SLIDE = 8;
	/** How far the plate's left edge leans over its height (pixels in, at the top). */
	private static final int LEAN = 10;
	/** The ranks' colours, D to HELLISH (HELLISH's is only its base: it flickers to {@link #FIRE}). */
	private static final int[] COLOURS = {0x8A8A8A, 0xC8C8C8, 0x5ADB6A, 0x4AD8F0, 0xE07AF0, 0xFFD84A, 0xFF4A3A, 0xD01010};
	private static final int FIRE = 0xFFB030;
	private static final int PLATE = 0x0C0808;

	private record Event(String text, int born) {
	}

	private static final Deque<Event> FEED_LINES = new ArrayDeque<>();
	private static boolean active;
	private static int grade;
	private static float progress;
	private static float flow = 1.0f;
	private static float shownFlow = 1.0f;
	private static int flowUpAt = -100;
	private static float shownProgress;
	/** The meter's lost part, draining after a hit or a rank lost. */
	private static float trail;
	private static String perks = "";
	private static int ticks;
	/** 0 hidden to SLIDE fully on screen. */
	private static int slide;
	private static int lastSlide;
	private static int rankedUpAt = -100;
	private static int rankedDownAt = -100;

	public static void receive(StylePayload payload) {
		if (payload.active() && !active) {
			FEED_LINES.clear();
			shownProgress = 0;
			trail = 0;
		}
		int newGrade = Math.max(0, Math.min(Grade.values().length - 1, payload.grade()));
		if (payload.active() && active) {
			if (newGrade > grade) {
				rankedUpAt = ticks;
				trail = 0;
			} else if (newGrade < grade) {
				rankedDownAt = ticks;
				// the meter was full of the rank above: let the whole of it drain away
				trail = 1;
			}
		}
		if (payload.flow() > flow + 0.01f && payload.active()) {
			flowUpAt = ticks;
		}
		flow = payload.flow();
		active = payload.active();
		grade = newGrade;
		progress = payload.progress();
		perks = payload.perks();
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
		lastSlide = slide;
		slide = Math.max(0, Math.min(SLIDE, slide + (active ? 1 : -1)));
		shownProgress += (progress - shownProgress) * 0.3f;
		shownFlow += (flow - shownFlow) * 0.35f;
		trail = trail > shownProgress ? Math.max(shownProgress, trail - Math.max(0.01f, (trail - shownProgress) * 0.08f)) : shownProgress;
		FEED_LINES.removeIf(e -> ticks - e.born() > FEED_TICKS);
		if (mc.level == null) {
			active = false;
		}
	}

	private static int height() {
		return 74 + (perks.isEmpty() ? 0 : 9) + FEED * 10;
	}

	/** For the HUD layout (client hud/HudLayout, top right): the plate's size while it shows. */
	public static HudLayout.Size measure(Minecraft mc, int width, int height) {
		if (slide == 0 && lastSlide == 0 || CutscenePlayer.active()) {
			return null;
		}
		return new HudLayout.Size(WIDTH, height());
	}

	/** The rank's colour now (HELLISH flickers between blood and fire). */
	private static int colour(int g, float t) {
		if (g == Grade.HELLISH.ordinal()) {
			float flicker = 0.5f + 0.5f * Mth.sin(t * 0.7f) * Mth.sin(t * 1.9f + 1.3f);
			return ARGB.srgbLerp(flicker, 0xFF000000 | COLOURS[g], 0xFF000000 | FIRE) & 0xFFFFFF;
		}
		return COLOURS[g];
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (slide == 0 && lastSlide == 0 || CutscenePlayer.active()) {
			return;
		}
		HudLayout.Box box = HudLayout.box(ID);
		if (box == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		float partial = delta.getGameTimeDeltaPartialTick(false);
		float t = ticks + partial;
		Grade rank = Grade.values()[grade];
		boolean hellish = rank == Grade.HELLISH;
		int colour = colour(grade, t);
		float alpha = Mth.lerp(partial, lastSlide, slide) / SLIDE;
		int x = box.x() + Math.round((1 - alpha) * (WIDTH + 12));
		int y = box.y();
		int h = height();

		// the plate: dark, its left edge leaning in, a band of the rank's colour down that edge, glowing from below from S up
		for (int row = 0; row < h; row++) {
			int lean = LEAN * (h - row) / h;
			g.fill(x + lean, y + row, x + WIDTH, y + row + 1, ARGB.color(alpha * 0.62f, PLATE));
			g.fill(x + lean, y + row, x + lean + 2, y + row + 1, ARGB.color(alpha, colour));
		}
		g.fill(x + LEAN, y, x + WIDTH, y + 1, ARGB.color(alpha * 0.5f, colour));
		if (rank.atLeast(Grade.S)) {
			float glow = rank == Grade.S ? 0.16f : rank == Grade.SS ? 0.22f : 0.3f;
			glow *= 0.8f + 0.2f * Mth.sin(t * 0.25f);
			g.fillGradient(x + 3, y + h / 2, x + WIDTH, y + h, ARGB.color(0f, colour), ARGB.color(alpha * glow, colour));
		}
		float upSince = t - rankedUpAt;
		if (upSince < 8) {
			// a rank gained: the plate flashes
			g.fill(x + 2, y, x + WIDTH, y + h, ARGB.color(alpha * 0.35f * (1 - upSince / 8f), 0xFFFFFF));
		}
		if (hellish) {
			flames(g, x, y + h, t, alpha, colour);
			embers(g, x, y, h, t, alpha);
		}

		// the header: STYLE spaced out, and the flow multiplier at the right, warming to the rank's colour as it climbs
		g.text(font, "S T Y L E", x + LEAN + 4, y + 4, ARGB.color(alpha * 0.6f, 0xBBBBBB), false);
		String mult = String.format(Locale.ROOT, "x%.1f", shownFlow);
		float warmth = Mth.clamp(shownFlow - 1f, 0f, 1f);
		int multColour = ARGB.srgbLerp(warmth, 0xFF9A9A9A, 0xFF000000 | colour);
		float flowSince = t - flowUpAt;
		float ms = 1.4f * (flowSince < 8 ? 1 + 0.3f * (1 - flowSince / 8f) : 1);
		g.pose().pushMatrix();
		g.pose().translate(x + WIDTH - 6 - font.width(mult) * ms, y + 3);
		g.pose().scale(ms, ms);
		g.text(font, mult, 0, 0, ARGB.color(alpha, multColour), true);
		g.pose().popMatrix();

		// the rank: big, outlined, popping when it rises, shaking red when it falls, trembling at the top
		float pop = upSince < 10 ? 1 + 0.35f * (1 - upSince / 10f) : 1;
		float downSince = t - rankedDownAt;
		int shakeX = 0;
		int shakeY = 0;
		int letterColour = colour;
		if (downSince < 10) {
			shakeX = Math.round(Mth.sin(t * 3.1f) * 2 * (1 - downSince / 10f));
			letterColour = ARGB.srgbLerp(1 - downSince / 10f, 0xFF000000 | colour, 0xFFFF3030) & 0xFFFFFF;
		} else if (rank.atLeast(Grade.SS) && (ticks / 2) % (hellish ? 1 : rank == Grade.SSS ? 2 : 4) == 0) {
			shakeX = (int) Math.signum(Mth.sin(t * 7.3f));
			shakeY = (int) Math.signum(Mth.sin(t * 5.9f));
		}
		String letters = rank.name();
		float base = letters.length() > 3 ? 1.9f : letters.length() > 2 ? 2.6f : 3.2f;
		float scale = base * pop;
		int letterW = Math.round(font.width(letters) * scale);
		int lx = x + LEAN + 6 + shakeX;
		int ly = y + 15 + shakeY + (letters.length() > 3 ? 6 : 0);
		g.pose().pushMatrix();
		g.pose().translate(lx, ly);
		g.pose().scale(scale, scale);
		int outline = ARGB.color(alpha * 0.9f, hellish ? 0x200000 : 0x000000);
		g.text(font, letters, -1, 0, outline, false);
		g.text(font, letters, 1, 0, outline, false);
		g.text(font, letters, 0, -1, outline, false);
		g.text(font, letters, 0, 1, outline, false);
		g.text(font, letters, 0, 0, ARGB.color(alpha, letterColour), false);
		g.pose().popMatrix();
		if (hellish) {
			// the top half of each letter hotter, like it's burning
			g.enableScissor(lx, ly - 1, lx + letterW + 2, ly + Math.round(4 * scale));
			g.pose().pushMatrix();
			g.pose().translate(lx, ly);
			g.pose().scale(scale, scale);
			g.text(font, letters, 0, 0, ARGB.color(alpha * 0.7f, FIRE), false);
			g.pose().popMatrix();
			g.disableScissor();
		}
		String title = hellish ? "BEYOND SATANIC" : rank.title;
		g.text(font, spaced(font, title, WIDTH - LEAN - 12), x + LEAN + 6, y + 45, ARGB.color(alpha * 0.9f, colour), false);

		// the meter: ten segments, what was just lost draining behind, a bright edge where it stands
		int by = y + 58;
		int left = x + LEAN / 2 + 6;
		int bw = WIDTH - LEAN / 2 - 12;
		int segments = 10;
		float shown = Mth.clamp(shownProgress, 0f, 1f);
		float lost = Mth.clamp(trail, 0f, 1f);
		for (int i = 0; i < segments; i++) {
			int s0 = left + bw * i / segments;
			int s1 = left + bw * (i + 1) / segments - 1;
			g.fill(s0, by, s1, by + 5, ARGB.color(alpha * 0.55f, 0x302828));
			float from = i / (float) segments;
			float fill = Mth.clamp((shown - from) * segments, 0f, 1f);
			float gone = Mth.clamp((lost - from) * segments, 0f, 1f);
			if (gone > fill) {
				g.fill(s0 + Math.round((s1 - s0) * fill), by, s0 + Math.round((s1 - s0) * gone), by + 5, ARGB.color(alpha * 0.8f, 0xB03A30));
			}
			if (fill > 0) {
				int e = s0 + Math.round((s1 - s0) * fill);
				g.fill(s0, by, e, by + 5, ARGB.color(alpha, colour));
				g.fill(s0, by, e, by + 1, ARGB.color(alpha * 0.5f, 0xFFFFFF));
			}
		}
		int edge = left + Math.round(bw * shown);
		if (shown > 0.01f) {
			g.fill(edge - 1, by - 1, edge + 1, by + 6, ARGB.color(alpha * (0.6f + 0.4f * Mth.sin(t * 0.5f)), 0xFFFFFF));
		}

		// what the rank gives while you hold it (S and up), small enough to fit the plate
		int fy = by + 10;
		if (!perks.isEmpty()) {
			float ps = Math.min(1f, (WIDTH - LEAN / 2f - 12) / Math.max(1, font.width(perks)));
			g.pose().pushMatrix();
			g.pose().translate(left, fy);
			g.pose().scale(ps, ps);
			g.text(font, perks, 0, 0, ARGB.color(alpha * 0.85f, colour), false);
			g.pose().popMatrix();
			fy += 9;
		}

		// what just scored or cost, newest on top, right-aligned, sliding in from the right
		fy += 2;
		for (Event e : FEED_LINES) {
			float age = (ticks - e.born() + partial) / FEED_TICKS;
			float a = alpha * Math.max(0f, 1f - age * age);
			boolean cost = e.text().startsWith("-");
			int c = cost ? 0xFF6A5A : 0xF2F2F2;
			String text = font.plainSubstrByWidth(e.text(), WIDTH - LEAN - 14);
			float in = Math.min(1f, (ticks - e.born() + partial) / 4f);
			int tx = x + WIDTH - 6 - font.width(text) + Math.round((1 - in) * 20);
			g.text(font, text, tx, fy, ARGB.color(a * in, c), true);
			g.fill(tx - 4, fy + 2, tx - 2, fy + 6, ARGB.color(a * in, cost ? 0xFF4030 : colour));
			fy += 10;
		}
	}

	/** "S A V A G E" if it fits the width, else the word as it is. */
	private static String spaced(Font font, String word, int width) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < word.length(); i++) {
			if (i > 0) {
				b.append(word.charAt(i) == ' ' ? "  " : " ");
			}
			if (word.charAt(i) != ' ') {
				b.append(word.charAt(i));
			}
		}
		String out = b.toString();
		return font.width(out) <= width ? out : word;
	}

	/** HELLISH: flames along the plate's foot. */
	private static void flames(GuiGraphicsExtractor g, int x, int bottom, float t, float alpha, int colour) {
		for (int i = 0; i < WIDTH - 4; i += 3) {
			float n = noise(t * 0.18f + i * 0.37f);
			int fh = Math.round(4 + 12 * n);
			g.fillGradient(x + 2 + i, bottom - fh, x + 4 + i, bottom, ARGB.color(0f, FIRE), ARGB.color(alpha * 0.7f, colour));
		}
	}

	/** HELLISH: embers rising through the plate. */
	private static void embers(GuiGraphicsExtractor g, int x, int y, int h, float t, float alpha) {
		for (int i = 0; i < 14; i++) {
			float life = frac(t / (28f + i * 3) + i * 0.137f);
			int ex = x + LEAN + 4 + Math.round(frac(i * 0.618f) * (WIDTH - LEAN - 8) + Mth.sin(t * 0.1f + i) * 3);
			int ey = y + h - 2 - Math.round(life * (h - 4));
			float a = alpha * (1 - life) * 0.9f;
			g.fill(ex, ey, ex + 1, ey + 1, ARGB.color(a, i % 3 == 0 ? 0xFFE080 : FIRE));
		}
	}

	private static float frac(float v) {
		return v - Mth.floor(v);
	}

	private static float noise(float v) {
		int i = Mth.floor(v);
		float f = v - i;
		float u = f * f * (3 - 2 * f);
		return Mth.lerp(u, hash(i), hash(i + 1));
	}

	private static float hash(int n) {
		n = (n << 13) ^ n;
		return ((n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff) / (float) 0x7fffffff;
	}

	/** For the client test: shows the panel at a rank without a fight (null event clears it). */
	public static void preview(int rankOrdinal, float meter, String event) {
		receive(new StylePayload(true, rankOrdinal, meter, event == null ? "" : event, 1.6f,
				rankOrdinal >= Grade.S.ordinal() ? "+" + 3 * (rankOrdinal - 3) + "% speed  +" + 4 * (rankOrdinal - 3) + "% attack  +"
						+ String.format(Locale.ROOT, "%.2f", 0.15 * (rankOrdinal - 3)).replaceAll("0+$", "") + " reach" : ""));
	}
}
