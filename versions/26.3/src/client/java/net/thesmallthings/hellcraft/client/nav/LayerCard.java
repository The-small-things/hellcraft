package net.thesmallthings.hellcraft.client.nav;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.world.Circle;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * The card shown on entering a circle: its name typed out big ("CIRCLE II: LUST"), then one plain line on what
 * the place does to you and the gear worth having. The circle is worked out here from the shared geometry, so
 * the server only says whether cards are on (circleTitles).
 */
public final class LayerCard implements HudElement {
	public static final Identifier ID = HellcraftMod.id("layer_card");

	private static final long FADE_IN = 300;
	private static final long HOLD = 6000;
	private static final long FADE_OUT = 1200;
	/** Milliseconds per letter of the title. */
	private static final long TYPE_SPEED = 45;
	/** The same circle's card isn't shown again within this long, so walking along a border doesn't flash it. */
	private static final long AGAIN_AFTER = 60_000;

	/** The circle the player stands in (null outside the Inferno). */
	private static Circle current;
	/** The card on screen, or null. */
	private static Circle showing;
	/** Game ticks the card has been up (it keeps time with the game, so a lag spike doesn't eat it). */
	private static int age;
	private static final Map<Circle, Long> LAST_SHOWN = new EnumMap<>(Circle.class);

	/** Every client tick: shows the card when the player crosses into another circle. */
	public static void tick(Minecraft mc) {
		if (showing != null && !mc.isPaused()) {
			age++;
		}
		if (!ClientNav.inHell(mc)) {
			current = null;
			return;
		}
		Circle circle = InfernoGeometry.circleAt(mc.player.getX(), mc.player.getZ());
		if (circle == current) {
			return;
		}
		current = circle;
		long now = Util.getMillis();
		Long last = LAST_SHOWN.get(circle);
		if (ClientNav.cards() && (last == null || now - last > AGAIN_AFTER)) {
			show(circle);
		}
	}

	/** Puts this circle's card on screen now. */
	public static void show(Circle circle) {
		showing = circle;
		age = 0;
		LAST_SHOWN.put(circle, Util.getMillis());
	}

	@Nullable
	public static Circle showing() {
		return showing;
	}

	public static void reset() {
		current = null;
		showing = null;
		LAST_SHOWN.clear();
	}

	/** Each circle's colour on the card, the compass and the Map of Hell. */
	public static int accent(Circle circle) {
		return switch (circle) {
			case DARK_WOOD -> 0x8FB878;
			case VESTIBULE -> 0xB8B098;
			case LIMBO -> 0xC4D0D4;
			case LUST -> 0xD86AAA;
			case GLUTTONY -> 0xB08A50;
			case GREED -> 0xF0C040;
			case WRATH -> 0x5AB0A0;
			case HERESY -> 0xF08040;
			case VIOLENCE -> 0xE03030;
			case FRAUD -> 0xA898C8;
			case TREACHERY -> 0xA8E0F8;
		};
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
		Circle circle = showing;
		if (circle == null) {
			return;
		}
		long t = (long) ((age + delta.getGameTimeDeltaPartialTick(false)) * 50);
		if (t > FADE_IN + HOLD + FADE_OUT) {
			showing = null;
			return;
		}
		float alpha = t < FADE_IN ? t / (float) FADE_IN : t < FADE_IN + HOLD ? 1 : 1 - (t - FADE_IN - HOLD) / (float) FADE_OUT;
		if (alpha < 0.02f) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		int w = g.guiWidth();
		int cy = g.guiHeight() / 4;

		// a dark band across the screen, feathered top and bottom
		int band = (int) (alpha * 150);
		int top = cy - 30;
		int bottom = cy + 34;
		g.fillGradient(0, top - 8, w, top, ARGB.black(0), ARGB.black(band));
		g.fill(0, top, w, bottom, ARGB.black(band));
		g.fillGradient(0, bottom, w, bottom + 8, ARGB.black(band), ARGB.black(0));

		// the title, typed out
		String title = circle.cardTitle();
		int letters = (int) Math.min(title.length(), Math.max(0, t / TYPE_SPEED));
		float scale = Math.min(3f, (w - 24) / (float) font.width(title));
		int colour = ARGB.color(alpha, accent(circle));
		g.pose().pushMatrix();
		g.pose().translate(w / 2f, cy - 22);
		g.pose().scale(scale, scale);
		// centred on the whole title, so it doesn't slide while it types
		g.text(font, title.substring(0, letters), -font.width(title) / 2, 0, colour);
		g.pose().popMatrix();

		// a rule under it that runs out to the title's width
		int full = (int) (font.width(title) * scale / 2) + 6;
		int half = (int) (full * Math.min(1f, t / (float) (TYPE_SPEED * title.length())));
		g.fill(w / 2 - half, cy + 6, w / 2 + half, cy + 7, ARGB.color(alpha * 0.8f, accent(circle)));

		// then what the place does, and what to bring
		long after = t - TYPE_SPEED * title.length();
		if (after > 0) {
			float fade = alpha * Math.min(1f, after / 400f);
			if (fade >= 0.02f) {
				centred(g, font, circle.rule(), w / 2, cy + 12, w - 16, ARGB.white(fade));
				centred(g, font, "Bring: " + circle.gear(), w / 2, cy + 23, w - 16, ARGB.color(fade, 0xA0A0A0));
			}
		}
	}

	/** Centred text, shrunk to fit on narrow screens. */
	static void centred(GuiGraphicsExtractor g, Font font, String text, int x, int y, int maxWidth, int colour) {
		int width = font.width(text);
		if (width <= maxWidth) {
			g.centeredText(font, text, x, y, colour);
			return;
		}
		float scale = maxWidth / (float) width;
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.text(font, text, -width / 2, 0, colour);
		g.pose().popMatrix();
	}
}
