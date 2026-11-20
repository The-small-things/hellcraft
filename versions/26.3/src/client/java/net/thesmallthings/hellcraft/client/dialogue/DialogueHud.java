package net.thesmallthings.hellcraft.client.dialogue;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.client.cutscene.CutscenePlayer;
import net.thesmallthings.hellcraft.net.Dialogue.Kind;
import net.thesmallthings.hellcraft.net.DialoguePayload;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The message box (Dialogue on the server): every line of text Hellcraft shows goes here, in one small box above the
 * hotbar, one style for all of it. A speaker's name leads their line in their colour, a headline's title sits over its
 * line, and a thin bar down the left takes the line's colour; that is the only difference between kinds.
 *
 * One message at a time, with a quick status word (what the action bar was for) just under it. Messages queue: a
 * headline first, then what characters say, then tips and notices; a line already shown or waiting is dropped, so is
 * a notice left waiting too long, and the queue never holds more than {@link #MAX_WAITING}. Each line stays long
 * enough to read at an easy pace, a little less when others are waiting, and a "+n" beside the box says how many
 * are. Lines wait while a cutscene plays.
 */
public final class DialogueHud implements HudElement {
	public static final Identifier ID = HellcraftMod.id("dialogue");

	/** The widest the box's text runs, in GUI pixels. */
	private static final int MAX_WIDTH = 220;
	/** Most lines waiting; past it the least important, oldest goes. */
	private static final int MAX_WAITING = 6;
	private static final float LETTERS_PER_TICK = 3f;
	private static final int FADE_IN = 3;
	private static final int FADE_OUT = 6;
	/** How long a line stays once typed: a base and a little per letter (an easy reading pace), at most HOLD_MAX. */
	private static final int HOLD = 40;
	private static final float HOLD_PER_LETTER = 1.1f;
	private static final int HOLD_MAX = 220;
	/** With others waiting, a line still stays this long at least (about 22 letters a second, plus a moment). */
	private static final int HURRIED_MIN = 50;
	private static final float HURRIED_PER_LETTER = 0.9f;
	/** The same line isn't shown again within this many ticks. */
	private static final int REPEAT_AFTER = 600;
	/** How far above the bottom of the screen the boxes sit: clear of the hotbar, the hearts and the action bar. */
	private static final int ABOVE_BOTTOM = 74;
	private static final int ROW = 10;
	private static final int PAD_X = 6;
	private static final int PAD_Y = 4;
	private static final int GAP = 2;

	private static final class Line {
		final Kind kind;
		final String speaker;
		final int colour;
		final Component text;
		final boolean shout;
		final String tag;
		final int letters;
		int waited;

		Line(Kind kind, String speaker, int colour, Component text, boolean shout, String tag) {
			this.kind = kind;
			this.speaker = speaker;
			this.colour = colour;
			this.text = text;
			this.shout = shout;
			this.tag = tag;
			this.letters = text.getString().length() + (kind == Kind.HEADLINE ? speaker.length() : 0);
		}

		String key() {
			return speaker + "|" + text.getString();
		}

		int priority() {
			return switch (kind) {
				case HEADLINE -> 3;
				case SPEECH -> 2;
				default -> 1;
			};
		}

		/** Ticks it may wait before it's no longer worth showing (a queue of a character's lines can take a minute to clear). */
		int patience() {
			return switch (kind) {
				case HEADLINE -> 400;
				case SPEECH -> 1200;
				case TIP -> 1800;
				default -> 1800;
			};
		}

		int typed() {
			return (int) Math.ceil(letters / LETTERS_PER_TICK);
		}

		int hold() {
			return Math.min(HOLD_MAX, HOLD + (int) (letters * HOLD_PER_LETTER));
		}
	}

	private static final List<Line> WAITING = new ArrayList<>();
	/** When each line was last put on screen (clock ticks), so a repeat is dropped. */
	private static final Map<String, Integer> SEEN = new HashMap<>();
	private static Line current;
	private static int age;
	private static int endAt;
	private static int shown;
	private static int clock;

	private static Line status;
	private static int statusAge;
	private static int statusEnd;

	public static void receive(DialoguePayload payload) {
		Kind kind = Kind.of(payload.kind());
		if (kind == Kind.STATUS) {
			status(payload.colour(), payload.line());
			return;
		}
		post(new Line(kind, payload.speaker(), payload.colour(), payload.line(), payload.shout(), ""), false);
	}

	/** A headline made on this side (a circle's card): a title, a line under it, a colour, and a tag to find it by. */
	public static void headline(String title, int colour, Component line, String tag, boolean again) {
		post(new Line(Kind.HEADLINE, title, colour, line, false, tag), again);
	}

	/** The tag of the line on screen ("" for none or untagged). */
	public static String showingTag() {
		Line line = current;
		return line == null ? "" : line.tag;
	}

	private static void post(Line line, boolean again) {
		String key = line.key();
		remember((line.speaker.isEmpty() ? "" : line.speaker + ": ") + line.text.getString());
		if (again && current != null && current.key().equals(key)) {
			// shown again on purpose (the client test): start it over
			age = 0;
			endAt = current.typed() + current.hold() + FADE_OUT;
			return;
		}
		if (again) {
			WAITING.removeIf(waiting -> waiting.key().equals(key));
		} else {
			if (current != null && current.key().equals(key)) {
				return;
			}
			Integer seen = SEEN.get(key);
			if (seen != null && clock - seen < REPEAT_AFTER) {
				return;
			}
			for (Line waiting : WAITING) {
				if (waiting.key().equals(key)) {
					return;
				}
			}
		}
		WAITING.add(line);
		while (WAITING.size() > MAX_WAITING) {
			// the least important goes first, the oldest of those
			Line drop = WAITING.get(0);
			for (Line waiting : WAITING) {
				if (waiting.priority() < drop.priority()) {
					drop = waiting;
				}
			}
			WAITING.remove(drop);
			remember("(dropped) " + drop.text.getString());
		}
	}

	private static void status(int colour, Component text) {
		Line line = new Line(Kind.STATUS, "", colour, text, false, "");
		boolean same = status != null && status.key().equals(line.key()) && statusAge < statusEnd - FADE_OUT;
		if (!same) {
			statusAge = 0;
		}
		status = line;
		// a meter sent again and again stays up while it keeps coming, without fading in each time
		statusEnd = statusAge + Math.min(120, 30 + (int) (line.letters * 0.9f)) + FADE_OUT;
	}

	/** The last lines received (and any dropped), newest last: for the client test's failure messages. */
	private static final Deque<String> RECENT = new ArrayDeque<>();

	private static void remember(String line) {
		if (RECENT.size() >= 16) {
			RECENT.pollFirst();
		}
		RECENT.addLast(line);
	}

	/** The last lines received, newest last (for the client test). */
	public static String recent() {
		return String.join(" | ", RECENT);
	}

	/** How many lines have been put on screen since the game started (for the client test). */
	public static int shown() {
		return shown;
	}

	/** The line on screen now ("" for none), for the client test. */
	public static String showing() {
		Line line = current;
		return line == null ? "" : line.text.getString();
	}

	/** The status word on screen now ("" for none), for the client test. */
	public static String showingStatus() {
		Line line = status;
		return line == null ? "" : line.text.getString();
	}

	/** True when nothing is on screen or waiting. */
	public static boolean idle() {
		return current == null && WAITING.isEmpty();
	}

	public static void reset() {
		WAITING.clear();
		SEEN.clear();
		current = null;
		status = null;
	}

	/** Every client tick: ages the lines on screen, lets go of stale ones and brings on the next. */
	public static void tick(Minecraft mc) {
		if (mc.isPaused() || CutscenePlayer.active()) {
			return;
		}
		clock++;
		if (status != null && ++statusAge >= statusEnd) {
			status = null;
		}
		for (Iterator<Line> it = WAITING.iterator(); it.hasNext(); ) {
			Line waiting = it.next();
			if (++waiting.waited > waiting.patience()) {
				it.remove();
				remember("(too late) " + waiting.text.getString());
			}
		}
		if (current == null) {
			current = next();
			if (current != null) {
				age = 0;
				endAt = current.typed() + current.hold() + FADE_OUT;
				SEEN.put(current.key(), clock);
				shown++;
			}
			return;
		}
		age++;
		if (!WAITING.isEmpty()) {
			// others are waiting: once this one has been up long enough to read, let it go
			int enough = current.typed() + Math.max(HURRIED_MIN, (int) (current.letters * HURRIED_PER_LETTER)) + FADE_OUT;
			endAt = Math.min(endAt, Math.max(age + FADE_OUT, enough));
		}
		if (age >= endAt) {
			current = null;
		}
	}

	/** The most important line waiting, the first of those. */
	private static Line next() {
		Line best = null;
		for (Line waiting : WAITING) {
			if (best == null || waiting.priority() > best.priority()) {
				best = waiting;
			}
		}
		if (best != null) {
			WAITING.remove(best);
		}
		return best;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (CutscenePlayer.active()) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		float partial = delta.getGameTimeDeltaPartialTick(false);
		int maxWidth = Math.min(MAX_WIDTH, g.guiWidth() - 40);
		int bottom = g.guiHeight() - ABOVE_BOTTOM;

		Line word = status;
		if (word != null) {
			float t = statusAge + partial;
			float alpha = Math.clamp(Math.min(t / FADE_IN, (statusEnd - t) / FADE_OUT), 0f, 1f);
			if (alpha >= 0.03f) {
				List<FormattedCharSequence> rows = font.split(word.text, maxWidth);
				int h = box(g, font, rows, null, word.colour, alpha, bottom, 0, 0, Integer.MAX_VALUE);
				bottom -= h + GAP;
			}
		}

		Line line = current;
		if (line == null) {
			return;
		}
		float t = age + partial;
		float alpha = Math.clamp(Math.min(t / FADE_IN, (endAt - t) / FADE_OUT), 0f, 1f);
		if (alpha < 0.03f) {
			return;
		}
		int shakeX = 0;
		int shakeY = 0;
		if (line.shout && t < 12) {
			shakeX = (int) Math.round(Math.sin(t * 2.9) * 2 * (1 - t / 12));
			shakeY = (int) Math.round(Math.cos(t * 3.7) * (1 - t / 12));
		}
		Style named = Style.EMPTY.withColor(line.colour).withBold(true);
		List<FormattedCharSequence> rows;
		FormattedCharSequence title = null;
		int instant = 0;
		if (line.kind == Kind.HEADLINE) {
			title = Component.literal(line.speaker).withStyle(named).getVisualOrderText();
			rows = font.split(line.text, maxWidth);
		} else if (!line.speaker.isEmpty()) {
			// the speaker's name leads the line, on the same row
			MutableComponent full = Component.empty().append(Component.literal(line.speaker).withStyle(named)).append(" ").append(line.text);
			rows = font.split(full, maxWidth);
			instant = line.speaker.length() + 1;
		} else {
			rows = font.split(line.text, maxWidth);
		}
		int letters = instant + (int) (t * LETTERS_PER_TICK);
		box(g, font, rows, title, line.colour, alpha, bottom, shakeX, shakeY, letters);
		if (!WAITING.isEmpty()) {
			Component more = Component.literal("+" + WAITING.size()).withStyle(ChatFormatting.GRAY);
			int w = boxWidth(font, rows, title);
			int x = (g.guiWidth() + w) / 2 + 3;
			int h = rows.size() * ROW + (title == null ? 0 : ROW) + PAD_Y * 2 - 2;
			g.text(font, more, x, bottom - h, ARGB.white(alpha * 0.8f));
		}
	}

	private static int boxWidth(Font font, List<FormattedCharSequence> rows, FormattedCharSequence title) {
		int textWidth = title == null ? 0 : font.width(title);
		for (FormattedCharSequence row : rows) {
			textWidth = Math.max(textWidth, font.width(row));
		}
		return textWidth + PAD_X + 2 + PAD_X - 2;
	}

	/** Draws one box whose bottom edge is at {@code bottom}, centred; the letters past {@code letters} not yet typed. Returns its height. */
	private static int box(GuiGraphicsExtractor g, Font font, List<FormattedCharSequence> rows, FormattedCharSequence title, int colour,
			float alpha, int bottom, int shakeX, int shakeY, int letters) {
		int w = boxWidth(font, rows, title);
		int h = rows.size() * ROW + (title == null ? 0 : ROW) + PAD_Y * 2 - 2;
		int x = (g.guiWidth() - w) / 2 + shakeX;
		int y = bottom - h + shakeY;
		g.fill(x, y, x + w, y + h, ARGB.color(alpha * 0.72f, 0x0C0808));
		g.fill(x, y, x + 2, y + h, ARGB.color(alpha, colour));
		int tx = x + PAD_X;
		int ty = y + PAD_Y;
		if (title != null) {
			g.text(font, title, tx, ty, ARGB.white(alpha));
			ty += ROW;
		}
		int left = letters;
		for (FormattedCharSequence row : rows) {
			if (left <= 0) {
				break;
			}
			g.text(font, clip(row, left), tx, ty, ARGB.white(alpha));
			left -= length(row);
			ty += ROW;
		}
		return h;
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
