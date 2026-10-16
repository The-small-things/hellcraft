package net.thesmallthings.hellcraft.client.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.client.nav.ClientNav;
import net.thesmallthings.hellcraft.world.GreatForge;

/**
 * The column of heat over the Great Forge (Phase 6, "finding your way" in the Forge of Dis): a glowing shaft from the
 * top of the Forge's dome to the roof of the Nether, flickering, with embers rising round it. It isn't fogged, so it can
 * be seen across the Nether wherever the rock doesn't hide it; the compass points the same way.
 */
public final class HeatColumn {
	private HeatColumn() {
	}

	/** From just over the dome to the roof. */
	private static final double BOTTOM = GreatForge.FLOOR_Y + 22;
	private static final double TOP = 124;
	private static final int EMBERS = 40;

	public static void render(LevelRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || !ClientNav.inForge(mc)) {
			return;
		}
		Vec3 cam = context.levelState().cameraRenderState.pos;
		double t = mc.level.getGameTime() + context.levelState().worldPartialTicks;
		float ox = (float) (0.5 - cam.x);
		float oz = (float) (0.5 - cam.z);
		float flicker = (float) (0.85 + 0.15 * Math.sin(t * 0.37) * Math.sin(t * 0.11 + 1.3));
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.debugQuads(), (pose, buffer) -> {
			float y0 = (float) (BOTTOM - cam.y);
			float y1 = (float) (TOP - cam.y);
			// the shaft: a hot core inside a duller sheath
			box(buffer, pose, ox, oz, 2.6f, y0, y1, Math.round(150 * flicker), Math.round(48 * flicker), 12);
			box(buffer, pose, ox, oz, 1.2f, y0 - 1, y1, 255, Math.round(170 * flicker), 60);
			// embers, rising and turning round it
			for (int i = 0; i < EMBERS; i++) {
				double phase = (t * (0.08 + 0.02 * (i % 5)) + i * 7.3) % (TOP - BOTTOM);
				double a = i * 2.399 + t * 0.02;
				double r = 2.0 + (i % 7) * 0.6;
				float ex = ox + (float) (Math.cos(a) * r);
				float ez = oz + (float) (Math.sin(a) * r);
				float ey = (float) (BOTTOM + phase - cam.y);
				box(buffer, pose, ex, ez, 0.18f, ey, ey + 0.36f, 255, 200 - (i % 4) * 30, 80);
			}
		});
	}

	/** A square column centred on (x, z), half a side wide, between two heights, its four sides and top in one colour. */
	private static void box(VertexConsumer buffer, PoseStack.Pose pose, float x, float z, float half, float y0, float y1, int r, int g, int b) {
		float a0 = x - half;
		float a1 = x + half;
		float b0 = z - half;
		float b1 = z + half;
		quad(buffer, pose, a0, y0, b0, a1, y0, b0, a1, y1, b0, a0, y1, b0, r, g, b);
		quad(buffer, pose, a1, y0, b1, a0, y0, b1, a0, y1, b1, a1, y1, b1, r, g, b);
		quad(buffer, pose, a0, y0, b1, a0, y0, b0, a0, y1, b0, a0, y1, b1, r, g, b);
		quad(buffer, pose, a1, y0, b0, a1, y0, b1, a1, y1, b1, a1, y1, b0, r, g, b);
		quad(buffer, pose, a0, y1, b0, a1, y1, b0, a1, y1, b1, a0, y1, b1, r, g, b);
	}

	private static void quad(VertexConsumer buffer, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1,
							 float x2, float y2, float z2, float x3, float y3, float z3, int r, int g, int b) {
		buffer.addVertex(pose, x0, y0, z0).setColor(r, g, b, 255);
		buffer.addVertex(pose, x1, y1, z1).setColor(r, g, b, 255);
		buffer.addVertex(pose, x2, y2, z2).setColor(r, g, b, 255);
		buffer.addVertex(pose, x3, y3, z3).setColor(r, g, b, 255);
	}
}
