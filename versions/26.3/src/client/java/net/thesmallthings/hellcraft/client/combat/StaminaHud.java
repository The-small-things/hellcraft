package net.thesmallthings.hellcraft.client.combat;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.combat.Combat;

/**
 * The stamina bar (combat/Stamina): a thin bar over the hunger bar, where the air bubbles go (it moves up a row under
 * water). It shows while stamina is being spent or coming back and fades away a moment after it is full; the lost part
 * drains away behind the bar, and the bar flashes red when there wasn't enough for what the player tried.
 */
public final class StaminaHud implements HudElement {
	public static final Identifier ID = HellcraftMod.id("stamina");
	private static final int WIDTH = 81;

	/** What the server last said, moved on between its updates by what this client knows it spent. */
	private static float stamina = 100;
	/** What the bar shows: eased towards {@link #stamina}. */
	private static float shown = 100;
	private static float lastShown = 100;
	/** The pale part still draining after a loss. */
	private static float trail = 100;
	private static float lastTrail = 100;
	private static int fullFor = 1000;
	private static int flash;
	private static float alpha;
	private static float lastAlpha;

	public static void receive(float value) {
		stamina = value;
		if (value < Combat.rules(true).staminaMax()) {
			fullFor = 0;
		}
	}

	/** This client spent stamina just now (it will hear the server's count a moment later). */
	public static void predict(float cost) {
		stamina = Math.max(0, stamina - cost);
		fullFor = 0;
	}

	public static float stamina() {
		return stamina;
	}

	/** The player tried something they had no stamina for. */
	public static void refuse() {
		flash = 10;
		fullFor = 0;
	}

	public static void reset() {
		stamina = shown = lastShown = trail = lastTrail = Combat.rules(true).staminaMax();
		fullFor = 1000;
		flash = 0;
		alpha = lastAlpha = 0;
	}

	public static void tick(Minecraft client) {
		if (client.isPaused()) {
			return;
		}
		float max = Combat.rules(true).staminaMax();
		lastShown = shown;
		lastTrail = trail;
		lastAlpha = alpha;
		shown += (stamina - shown) * 0.5f;
		if (Math.abs(stamina - shown) < 0.05f) {
			shown = stamina;
		}
		trail = trail > shown ? Math.max(shown, trail - Math.max(0.8f, (trail - shown) * 0.12f)) : shown;
		if (stamina >= max - 0.01f) {
			fullFor++;
		} else {
			fullFor = 0;
		}
		if (flash > 0) {
			flash--;
		}
		boolean show = Combat.rules(true).on() && (fullFor < 30 || flash > 0);
		alpha = Mth.clamp(alpha + (show ? 0.25f : -0.1f), 0, 1);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		float partial = delta.getGameTimeDeltaPartialTick(false);
		float a = Mth.lerp(partial, lastAlpha, alpha);
		if (player == null || a <= 0.01f || mc.gui.hud.isHidden() || player.isSpectator() || player.hasInfiniteMaterials()) {
			return;
		}
		float max = Math.max(1, Combat.rules(true).staminaMax());
		int right = g.guiWidth() / 2 + 91;
		int left = right - WIDTH;
		// the row over the hunger bar; the air bubbles take it under water, so sit above them then
		boolean bubbles = player.getAirSupply() < player.getMaxAirSupply() || player.isUnderWater();
		int y = g.guiHeight() - 49 + 3 - (bubbles ? 10 : 0);
		if (player.getVehicle() instanceof net.minecraft.world.entity.LivingEntity) {
			y -= 10;
		}
		int h = 4;
		g.fill(left - 1, y - 1, right + 1, y + h + 1, ARGB.color(a * 0.75f, 0x100A0A));
		g.fill(left, y, right, y + h, ARGB.color(a * 0.85f, 0x2A2020));
		float t = Mth.clamp(Mth.lerp(partial, lastTrail, trail) / max, 0, 1);
		float s = Mth.clamp(Mth.lerp(partial, lastShown, shown) / max, 0, 1);
		int trailTo = left + Math.round(WIDTH * t);
		int to = left + Math.round(WIDTH * s);
		if (trailTo > to) {
			g.fill(to, y, trailTo, y + h, ARGB.color(a * 0.8f, 0xB03A30));
		}
		boolean red = flash > 0 && (flash / 2) % 2 == 0;
		boolean low = shown < Combat.rules(true).dodgeCost();
		int colour = red ? 0xE04030 : low ? 0xC08A38 : 0xE6CC62;
		if (to > left) {
			g.fill(left, y, to, y + h, ARGB.color(a, colour));
			g.fill(left, y, to, y + 1, ARGB.color(a * 0.55f, 0xFFF4C0));
			g.fill(left, y + h - 1, to, y + h, ARGB.color(a * 0.35f, 0x402808));
		}
		if (red) {
			g.fill(left - 1, y - 1, right + 1, y, ARGB.color(a, 0xE04030));
			g.fill(left - 1, y + h, right + 1, y + h + 1, ARGB.color(a, 0xE04030));
		}
	}
}
