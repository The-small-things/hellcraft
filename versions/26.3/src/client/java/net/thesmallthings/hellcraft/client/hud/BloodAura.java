package net.thesmallthings.hellcraft.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.HellWeapons;

/**
 * A Blood Art ready to loose, shown where the weapon sits: while the blood charge is full (BloodClient), every hotbar slot
 * holding a hell weapon burns with a blood aura instead of the enchantment glint older versions put on the weapon. It is
 * drawn twice: {@link #UNDER}, before the hotbar, a soft red glow round the slot with flames of blood licking up over the
 * hotbar's top edge; and {@link #OVER}, after it, a pulsing rim on the slot and a few drops rising at its sides, clear of the
 * item itself. It flares for a moment when the charge fills.
 */
public final class BloodAura implements HudElement {
	public static final Identifier UNDER_ID = HellcraftMod.id("blood_aura_under");
	public static final Identifier OVER_ID = HellcraftMod.id("blood_aura_over");
	public static final BloodAura UNDER = new BloodAura(true);
	public static final BloodAura OVER = new BloodAura(false);

	private static final int DEEP = 0x7A0608;
	private static final int BLOOD = 0xC8141A;
	private static final int BRIGHT = 0xFF4A3A;

	private static int ticks;

	private final boolean under;

	private BloodAura(boolean under) {
		this.under = under;
	}

	public static void tick(Minecraft mc) {
		if (!mc.isPaused()) {
			ticks++;
		}
	}

	/** True if the hotbar shows an aura on slot {@code i} now (tests). */
	public static boolean shows(LocalPlayer player, int i) {
		return BloodClient.artReady() && HellWeapons.of(player.getInventory().getItem(i)) != null;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || !BloodClient.artReady() || player.isSpectator() || mc.gui.hud.isHidden()) {
			return;
		}
		float t = (ticks + delta.getGameTimeDeltaPartialTick(false)) / 20f;
		// a flare as the charge fills, then a slow heartbeat
		float flare = Math.max(0f, 1f - BloodClient.sinceReady() / 14f);
		float beat = 0.5f + 0.5f * Mth.sin(t * 5.2f) * Mth.sin(t * 5.2f);
		int cx = g.guiWidth() / 2;
		int bottom = g.guiHeight();
		for (int i = 0; i < 9; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (HellWeapons.of(stack) == null) {
				continue;
			}
			int ix = cx - 88 + i * 20;
			int iy = bottom - 19;
			float phase = i * 1.7f;
			if (under) {
				under(g, ix, iy, t + phase, beat, flare);
			} else {
				over(g, ix, iy, t + phase, beat, flare);
			}
		}
	}

	/** The glow round slot (ix, iy) (its item's top left), and blood flames over the hotbar's edge. */
	private static void under(GuiGraphicsExtractor g, int ix, int iy, float t, float beat, float flare) {
		float strength = 0.55f + 0.35f * beat + 0.6f * flare;
		for (int k = 0; k < 4; k++) {
			int e = 2 + k * 2;
			float a = strength * (0.26f - k * 0.055f);
			g.fill(ix - e, iy - e, ix + 16 + e, iy + 16 + e, ARGB.color(Mth.clamp(a, 0f, 1f), k == 0 ? BLOOD : DEEP));
		}
		// tongues of blood rising over the hotbar's top edge (y = iy - 3), each its own height and sway
		int edge = iy - 3;
		for (int j = 0; j < 5; j++) {
			float n = noise(t * 2.3f + j * 3.1f);
			int height = Math.round(3 + 7 * n * (0.7f + 0.3f * beat) + 6 * flare);
			int x = ix + 1 + j * 3 + Math.round(Mth.sin(t * 3f + j) * 1.2f);
			int top = edge - height;
			g.fillGradient(x, top, x + 2, edge + 1, ARGB.color(0f, BRIGHT), ARGB.color(Mth.clamp(0.75f * strength, 0f, 1f), BLOOD));
		}
	}

	/** The rim on slot (ix, iy), and drops rising at its sides. */
	private static void over(GuiGraphicsExtractor g, int ix, int iy, float t, float beat, float flare) {
		float a = Mth.clamp(0.45f + 0.4f * beat + flare, 0f, 1f);
		int rim = ARGB.srgbLerp(flare, ARGB.opaque(BLOOD), 0xFFFFE0D0);
		g.outline(ix - 1, iy - 1, 18, 18, ARGB.multiplyAlpha(rim, a));
		// the corners brighter, like the blood pools there
		int corner = ARGB.color(a, BRIGHT);
		g.fill(ix - 1, iy - 1, ix + 1, iy + 1, corner);
		g.fill(ix + 15, iy - 1, ix + 17, iy + 1, corner);
		g.fill(ix - 1, iy + 15, ix + 1, iy + 17, corner);
		g.fill(ix + 15, iy + 15, ix + 17, iy + 17, corner);
		// drops rising up the slot's sides and fading, two at a time
		for (int d = 0; d < 2; d++) {
			float life = frac(t * 0.9f + d * 0.5f);
			int x = d == 0 ? ix - 1 : ix + 15;
			int y = iy + 15 - Math.round(life * 20);
			float da = (1 - life) * a;
			g.fill(x, y, x + 2, y + 2, ARGB.color(da, BRIGHT));
		}
	}

	private static float frac(float v) {
		return v - Mth.floor(v);
	}

	/** Smooth value noise in 0..1. */
	private static float noise(float x) {
		int i = Mth.floor(x);
		float f = x - i;
		float u = f * f * (3 - 2 * f);
		return Mth.lerp(u, hash(i), hash(i + 1));
	}

	private static float hash(int n) {
		n = (n << 13) ^ n;
		return ((n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff) / (float) 0x7fffffff;
	}
}
