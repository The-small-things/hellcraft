package net.thesmallthings.hellcraft.client.cutscene;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.cutscene.Cutscene;
import net.thesmallthings.hellcraft.cutscene.Cutscenes;
import net.thesmallthings.hellcraft.net.CutsceneDonePayload;
import net.thesmallthings.hellcraft.net.CutscenePayload;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Plays a cutscene (common/cutscene): flies the camera along its shots (Catmull-Rom curves through them, so it
 * glides), with letterbox bars, a fade in, camera shake, the name card and captions, then glides back to the
 * player's own eyes. The player can't move or look around meanwhile, and the HUD is hidden. A cutscene seen before
 * can be skipped with the jump key; a skipped one still shows its name card.
 */
public final class CutscenePlayer {
	private CutscenePlayer() {
	}

	/** Where the camera is this frame, and which way it looks. */
	public record Frame(Vec3 pos, float yaw, float pitch) {
	}

	private static final int BARS_IN = 10;

	@Nullable
	private static Cutscene playing;
	private static Vec3 anchor = Vec3.ZERO;
	private static float anchorYaw;
	private static boolean skippable;
	private static int age;
	private static boolean hudWasHidden;
	/** A name card shown on its own after a skip. */
	@Nullable
	private static Cutscene.Card lateCard;
	private static int lateAge;

	public static void start(CutscenePayload payload) {
		Minecraft mc = Minecraft.getInstance();
		Cutscene cutscene = Cutscenes.byId(payload.id());
		if (cutscene == null || mc.player == null) {
			ClientPlayNetworking.send(new CutsceneDonePayload(payload.id()));
			return;
		}
		if (playing == null) {
			hudWasHidden = mc.gui.hud.isHidden();
		}
		playing = cutscene;
		anchor = new Vec3(payload.x(), payload.y(), payload.z());
		anchorYaw = payload.yaw();
		skippable = payload.skippable();
		age = 0;
		lateCard = null;
		if (!mc.gui.hud.isHidden()) {
			mc.gui.hud.toggle();
		}
		while (mc.options.keyJump.consumeClick()) {
			// a jump pressed before it began doesn't skip it
		}
	}

	public static boolean active() {
		return playing != null;
	}

	public static void tick(Minecraft mc) {
		if (lateCard != null && ++lateAge > 70) {
			lateCard = null;
		}
		if (playing == null) {
			return;
		}
		if (mc.player == null || mc.level == null) {
			stop(mc, false);
			return;
		}
		if (mc.isPaused()) {
			return;
		}
		age++;
		boolean skip = false;
		while (mc.options.keyJump.consumeClick()) {
			skip = skippable;
		}
		if (skip || age >= playing.ticks()) {
			stop(mc, skip);
		}
	}

	private static void stop(Minecraft mc, boolean skipped) {
		Cutscene done = playing;
		playing = null;
		if (mc.gui.hud.isHidden() != hudWasHidden) {
			mc.gui.hud.toggle();
		}
		if (done != null && mc.getConnection() != null) {
			ClientPlayNetworking.send(new CutsceneDonePayload(done.id()));
		}
		if (skipped && done != null && done.card() != null) {
			lateCard = done.card();
			lateAge = 0;
		}
	}

	public static void reset() {
		playing = null;
		lateCard = null;
	}

	// ------------------------------------------------------------------ the camera

	/**
	 * The camera for this frame, or null when nothing plays. The player's own camera is given so the end of the
	 * cutscene can glide back into it.
	 */
	@Nullable
	public static Frame frame(float partial, Vec3 eyes, float eyeYaw, float eyePitch) {
		Cutscene c = playing;
		if (c == null || c.shots().isEmpty()) {
			return null;
		}
		float t = Math.min(c.ticks(), age + partial);
		List<Cutscene.Shot> shots = c.shots();
		int i = 0;
		while (i < shots.size() - 1 && shots.get(i + 1).tick() <= t) {
			i++;
		}
		Cutscene.Shot p1 = shots.get(i);
		Cutscene.Shot p2 = shots.get(Math.min(i + 1, shots.size() - 1));
		Cutscene.Shot p0 = shots.get(Math.max(i - 1, 0));
		Cutscene.Shot p3 = shots.get(Math.min(i + 2, shots.size() - 1));
		float u = p2.tick() == p1.tick() ? 0 : Mth.clamp((t - p1.tick()) / (p2.tick() - p1.tick()), 0, 1);
		Vec3 pos = world(curve(u, p0.right(), p1.right(), p2.right(), p3.right()), curve(u, p0.up(), p1.up(), p2.up(), p3.up()),
				curve(u, p0.forward(), p1.forward(), p2.forward(), p3.forward()));
		Vec3 look = world(curve(u, p0.lookRight(), p1.lookRight(), p2.lookRight(), p3.lookRight()),
				curve(u, p0.lookUp(), p1.lookUp(), p2.lookUp(), p3.lookUp()),
				curve(u, p0.lookForward(), p1.lookForward(), p2.lookForward(), p3.lookForward()));
		Vec3 dir = look.subtract(pos);
		float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)));
		for (Cutscene.Shake s : c.shakes()) {
			if (t >= s.from() && t <= s.to()) {
				float fade = 1f - (t - s.from()) / Math.max(1f, s.to() - s.from());
				float k = s.strength() * fade;
				yaw += k * (Mth.sin(t * 2.3f) * 1.4f + Mth.sin(t * 5.1f) * 0.6f);
				pitch += k * (Mth.cos(t * 2.9f) * 1.1f + Mth.sin(t * 6.7f) * 0.5f);
			}
		}
		// the last stretch glides back to the player's eyes
		float back = c.blendOut() <= 0 ? 0 : Mth.clamp((t - (c.ticks() - c.blendOut())) / c.blendOut(), 0, 1);
		if (back > 0) {
			float e = back * back * (3 - 2 * back);
			pos = pos.lerp(eyes, e);
			yaw = yaw + Mth.wrapDegrees(eyeYaw - yaw) * e;
			pitch = Mth.lerp(e, pitch, eyePitch);
		}
		return new Frame(pos, yaw, pitch);
	}

	/** From the anchor's frame (right, up, forward) to the world. */
	private static Vec3 world(double right, double up, double forward) {
		double yaw = Math.toRadians(anchorYaw);
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		// facing south (yaw 0) your right hand is to the west
		double rx = -Math.cos(yaw);
		double rz = -Math.sin(yaw);
		return new Vec3(anchor.x + rx * right + fx * forward, anchor.y + up, anchor.z + rz * right + fz * forward);
	}

	private static double curve(float u, double p0, double p1, double p2, double p3) {
		double u2 = u * u;
		double u3 = u2 * u;
		return 0.5 * (2 * p1 + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u2 + (-p0 + 3 * p1 - 3 * p2 + p3) * u3);
	}

	// ------------------------------------------------------------------ the overlay

	/** Letterbox, fade, name card, captions and the skip hint (drawn over everything, even with the HUD hidden). */
	public static void drawOverlay(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		int w = g.guiWidth();
		int h = g.guiHeight();
		float partial = delta.getGameTimeDeltaPartialTick(false);
		Cutscene c = playing;
		if (c == null) {
			if (lateCard != null) {
				card(g, font, lateCard, (lateAge + partial) * 1f, 0, 70, w, h);
			}
			return;
		}
		float t = Math.min(c.ticks(), age + partial);
		// letterbox bars slide in at the start and out at the end
		float in = Mth.clamp(t / BARS_IN, 0, 1);
		float out = Mth.clamp((c.ticks() - t) / BARS_IN, 0, 1);
		int bar = (int) (h * 0.11f * Math.min(in, out));
		g.fill(0, 0, w, bar, 0xFF000000);
		g.fill(0, h - bar, w, h, 0xFF000000);
		if (c.fadeIn() > 0 && t < c.fadeIn()) {
			g.fill(0, 0, w, h, ARGB.black(1f - t / c.fadeIn()));
		}
		if (c.card() != null) {
			card(g, font, c.card(), t, c.card().from(), c.card().to(), w, h);
		}
		for (Cutscene.Caption cap : c.captions()) {
			if (t < cap.from() || t > cap.to()) {
				continue;
			}
			float alpha = Math.min(1f, Math.min((t - cap.from()) / 6f, (cap.to() - t) / 6f));
			if (alpha <= 0.02f) {
				continue;
			}
			Component line = Component.literal(cap.speaker() + ": ").withColor(cap.colour()).append(Component.literal(cap.line()).withColor(0xFFFFFF));
			List<FormattedCharSequence> rows = font.split(line, (int) (w * 0.8f));
			int y = h - Math.max(bar, (int) (h * 0.11f)) - 6 - rows.size() * 10;
			for (int i = 0; i < rows.size(); i++) {
				FormattedCharSequence row = rows.get(i);
				g.fill(w / 2 - font.width(row) / 2 - 3, y + i * 10 - 1, w / 2 + font.width(row) / 2 + 3, y + i * 10 + 9, ARGB.black(alpha * 0.55f));
				g.text(font, row, w / 2 - font.width(row) / 2, y + i * 10, ARGB.white(alpha));
			}
		}
		if (skippable) {
			Component hint = Component.literal("Press ").append(mc.options.keyJump.getTranslatedKeyMessage()).append(" to skip");
			g.text(font, hint, w - font.width(hint) - 8, h - bar / 2 - 4, 0xFF9A9A9A);
		}
	}

	/** A boss's name, typed out big, and its title under it. */
	private static void card(GuiGraphicsExtractor g, Font font, Cutscene.Card card, float t, int from, int to, int w, int h) {
		if (t < from || t > to) {
			return;
		}
		float local = t - from;
		float alpha = Math.min(1f, Math.min(local / 5f, (to - t) / 10f));
		if (alpha <= 0.02f) {
			return;
		}
		int letters = Math.min(card.title().length(), (int) (local / 1.5f));
		float scale = Math.min(5f, (w - 24) / (float) font.width(card.title()));
		int cy = (int) (h * 0.30f);
		g.pose().pushMatrix();
		g.pose().translate(w / 2f, cy);
		g.pose().scale(scale, scale);
		g.text(font, card.title().substring(0, letters), -font.width(card.title()) / 2, 0, ARGB.color(alpha, card.colour()));
		g.pose().popMatrix();
		int lineY = cy + (int) (9 * scale) + 3;
		int half = (int) (font.width(card.title()) * scale / 2 * Math.min(1f, local / (1.5f * card.title().length())));
		g.fill(w / 2 - half, lineY, w / 2 + half, lineY + 1, ARGB.color(alpha * 0.8f, card.colour()));
		if (local > 1.5f * card.title().length()) {
			float sub = alpha * Math.min(1f, (local - 1.5f * card.title().length()) / 8f);
			if (sub > 0.02f) {
				g.centeredText(font, card.subtitle(), w / 2, lineY + 5, ARGB.color(sub, 0xD8D0C0));
			}
		}
	}
}
