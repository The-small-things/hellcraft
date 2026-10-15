package net.thesmallthings.hellcraft.client.dialogue;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.client.cutscene.CutscenePlayer;
import net.thesmallthings.hellcraft.net.DialoguePayload;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * The dialogue box (Dialogue on the server): one line at a time in a dark box above the hotbar, the speaker's name
 * on top in their colour, the words typed out. A line holds long enough to read, shorter when more are waiting;
 * a shout shakes the box. Lines wait while a cutscene plays.
 */
public final class DialogueHud implements HudElement {
	public static final Identifier ID = HellcraftMod.id("dialogue");

	/** The widest the box's text runs, in GUI pixels. */
	private static final int MAX_WIDTH = 260;
	/** Lines waiting beyond this many drop the oldest. */
	private static final int MAX_WAITING = 6;
	private static final float LETTERS_PER_TICK = 1.8f;
	private static final int FADE_IN = 4;
	private static final int FADE_OUT = 8;
	/** How long a line stays once typed: a base, a little per letter, and less when others are waiting. */
	private static final int HOLD = 50;
	private static final int HOLD_MAX = 170;
	private static final int HOLD_HURRIED = 30;
	/** How far above the bottom of the screen the box sits: clear of the hotbar, the hearts and the action bar. */
	private static final int ABOVE_BOTTOM = 74;

	private record Line(String speaker, int colour, Component text, boolean shout, int letters) {
		int typed() {
			return (int) Math.ceil(letters / LETTERS_PER_TICK);
		}
	}

	private static final Deque<Line> WAITING = new ArrayDeque<>();
	private static Line current;
	private static int age;
	private static int endAt;
	private static int shown;

	public static void receive(DialoguePayload payload) {
		if (WAITING.size() >= MAX_WAITING) {
			WAITING.pollFirst();
		}
		WAITING.addLast(new Line(payload.speaker(), payload.colour(), payload.line(), payload.shout(), payload.line().getString().length()));
	}

	/** How many lines have been put on screen since the game started (for the client test). */
	public static int shown() {
		return shown;
	}

	/** True when nothing is on screen or waiting. */
	public static boolean idle() {
		return current == null && WAITING.isEmpty();
	}

	public static void reset() {
		WAITING.clear();
		current = null;
	}

	/** Every client tick: ages the line on screen and brings on the next. */
	public static void tick(Minecraft mc) {
		if (mc.isPaused() || CutscenePlayer.active()) {
			return;
		}
		if (current == null) {
			current = WAITING.pollFirst();
			if (current != null) {
				age = 0;
				endAt = current.typed() + Math.min(HOLD_MAX, HOLD + current.letters()) + FADE_OUT;
				shown++;
			}
			return;
		}
		age++;
		if (!WAITING.isEmpty()) {
			// others are waiting: once this one is typed and has been up a moment, let it go
			endAt = Math.min(endAt, Math.max(age + FADE_OUT, current.typed() + HOLD_HURRIED + FADE_OUT));
		}
		if (age >= endAt) {
			current = null;
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
		Line line = current;
		if (line == null || CutscenePlayer.active()) {
			return;
		}
		float t = age + delta.getGameTimeDeltaPartialTick(false);
		float alpha = Math.clamp(Math.min(t / FADE_IN, (endAt - t) / FADE_OUT), 0f, 1f);
		if (alpha < 0.03f) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		int maxWidth = Math.min(MAX_WIDTH, g.guiWidth() - 40);
		List<FormattedCharSequence> rows = font.split(line.text(), maxWidth);
		boolean named = !line.speaker().isEmpty();
		Component name = Component.literal(line.speaker()).withStyle(ChatFormatting.BOLD);
		int textWidth = named ? font.width(name) : 0;
		for (FormattedCharSequence row : rows) {
			textWidth = Math.max(textWidth, font.width(row));
		}
		int w = textWidth + 14;
		int h = rows.size() * 10 + 8 + (named ? 12 : 0);
		int x = (g.guiWidth() - w) / 2;
		int y = g.guiHeight() - ABOVE_BOTTOM - h;
		if (line.shout() && t < 12) {
			x += (int) Math.round(Math.sin(t * 2.9) * 2 * (1 - t / 12));
			y += (int) Math.round(Math.cos(t * 3.7) * (1 - t / 12));
		}

		g.fill(x, y, x + w, y + h, ARGB.color(alpha * 0.75f, 0x0C0808));
		g.outline(x, y, w, h, ARGB.color(alpha * 0.55f, line.colour()));
		g.fill(x, y, x + 2, y + h, ARGB.color(alpha, line.colour()));
		int ty = y + 5;
		if (named) {
			g.text(font, name, x + 8, ty, ARGB.color(alpha, line.colour()));
			ty += 12;
		}
		// typed out a letter at a time, across the wrapped rows
		int left = (int) (t * LETTERS_PER_TICK);
		for (FormattedCharSequence row : rows) {
			if (left <= 0) {
				break;
			}
			g.text(font, clip(row, left), x + 8, ty, ARGB.white(alpha));
			left -= length(row);
			ty += 10;
		}
	}

	private static FormattedCharSequence clip(FormattedCharSequence row, int letters) {
		return sink -> {
			int[] n = {0};
			return row.accept((position, style, codepoint) -> n[0]++ < letters && sink.accept(position, style, codepoint));
		};
	}

	private static int length(FormattedCharSequence row) {
		int[] n = {0};
		row.accept((position, style, codepoint) -> {
			n[0]++;
			return true;
		});
		return n[0];
	}
}
