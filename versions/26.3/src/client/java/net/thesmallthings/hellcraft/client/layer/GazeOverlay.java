package net.thesmallthings.hellcraft.client.layer;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.entity.boss.MedusaEntity;

/**
 * Medusa's gaze, seen from inside: while her eyes are open and you look at her, the screen turns stone grey from the
 * edges in (FuriesFight turns you to stone by the same measure: how far off her eyes you look). Look away and it
 * clears.
 */
public final class GazeOverlay implements HudElement {
	public static final Identifier ID = HellcraftMod.id("medusa_gaze");
	/** The same as FuriesFight: how far off her eyes still counts as looking (radians), and ticks until stone. */
	private static final double ARC = 0.55;
	private static final float STONE_AT = 24;

	private static float stone;
	private static float previous;

	public static void tick(Minecraft mc) {
		previous = stone;
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			stone = 0;
			return;
		}
		boolean looking = false;
		for (MedusaEntity m : mc.level.getEntitiesOfClass(MedusaEntity.class, new AABB(player.blockPosition()).inflate(64))) {
			if (!m.gazing()) {
				continue;
			}
			Vec3 to = new Vec3(m.getX(), m.eyesY(), m.getZ()).subtract(player.getEyePosition()).normalize();
			if (Math.acos(Mth.clamp(to.dot(player.getLookAngle()), -1, 1)) < ARC) {
				looking = true;
			}
		}
		stone = looking ? Math.min(STONE_AT, stone + 1) : Math.max(0, stone - 2);
	}

	public static void reset() {
		stone = 0;
		previous = 0;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
		float s = Mth.lerp(delta.getGameTimeDeltaPartialTick(false), previous, stone) / STONE_AT;
		if (s <= 0.01f) {
			return;
		}
		int w = g.guiWidth();
		int h = g.guiHeight();
		// grey closing in from the edges, then over everything
		int band = (int) (Math.min(w, h) * 0.5f * s);
		int edge = ARGB.color((int) (200 * s), 0x8A, 0x86, 0x80);
		g.fill(0, 0, w, band, edge);
		g.fill(0, h - band, w, h, edge);
		g.fill(0, band, band, h - band, edge);
		g.fill(w - band, band, w, h - band, edge);
		g.fill(0, 0, w, h, ARGB.color((int) (110 * s * s), 0x8A, 0x86, 0x80));
		if (s > 0.25f) {
			g.centeredText(Minecraft.getInstance().font, "You are turning to stone: look away from Medusa!", w / 2, h / 2 + 24, 0xFFE0E0E0);
		}
	}
}
