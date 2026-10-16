package net.thesmallthings.hellcraft.client.layer;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.thesmallthings.hellcraft.HellcraftMod;

/**
 * Lucifer's wingbeat as you feel it (hazard/Wingbeat): two great slow flaps and a far-off boom, the view shaking, and
 * frost creeping in at the screen's edges and melting away, all as strong as the server says (stronger the deeper
 * you are).
 */
public final class WingbeatFx implements HudElement {
	public static final Identifier ID = HellcraftMod.id("wingbeat");
	private static final Identifier FROST = Identifier.withDefaultNamespace("textures/misc/powder_snow_outline.png");
	/** How long one beat lasts (ticks): the shake, and the frost's coming and going. */
	private static final int LENGTH = 80;
	private static final int SHAKE = 36;

	private static float strength;
	/** False for a lesser shock (a slammed hand): a shake and a boom, no frost or flaps. */
	private static boolean frost;
	private static int age = -1;

	private WingbeatFx() {
	}

	public static final WingbeatFx INSTANCE = new WingbeatFx();

	/** The server says the wings beat this hard (negative: only the ground shaking, this hard). */
	public static void beat(float s) {
		frost = s >= 0;
		strength = Mth.clamp(Math.abs(s), 0, 1);
		age = 0;
	}

	public static void tick(Minecraft mc) {
		if (age < 0 || mc.player == null) {
			return;
		}
		if (frost && (age == 0 || age == 14)) {
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ENDER_DRAGON_FLAP, 0.45f, 0.3f + 0.3f * strength));
		}
		if (age == 4) {
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.3f, 0.15f + 0.25f * strength));
		}
		if (++age > LENGTH) {
			age = -1;
		}
	}

	/** How far the view is thrown this frame (degrees of yaw and pitch), or zeros. */
	public static float[] shake(float partialTicks) {
		if (age < 0 || age > SHAKE) {
			return new float[]{0, 0};
		}
		float t = age + partialTicks;
		float k = strength * 1.6f * (1f - t / SHAKE);
		return new float[]{k * (Mth.sin(t * 2.1f) * 1.2f + Mth.sin(t * 4.7f) * 0.5f), k * (Mth.cos(t * 2.7f) * 1.0f + Mth.sin(t * 6.1f) * 0.4f)};
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (age < 0 || !frost) {
			return;
		}
		float t = (age + delta.getGameTimeDeltaPartialTick(false)) / LENGTH;
		// the frost rushes in with the wind, then melts slowly
		float alpha = strength * (t < 0.15f ? t / 0.15f : 1f - (t - 0.15f) / 0.85f);
		if (alpha <= 0.01f) {
			return;
		}
		g.blit(RenderPipelines.GUI_TEXTURED, FROST, 0, 0, 0.0F, 0.0F, g.guiWidth(), g.guiHeight(), g.guiWidth(), g.guiHeight(), ARGB.white(alpha));
	}
}
