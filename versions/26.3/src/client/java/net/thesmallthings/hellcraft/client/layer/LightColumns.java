package net.thesmallthings.hellcraft.client.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.client.nav.ClientNav;
import net.thesmallthings.hellcraft.world.ParadisoGeometry;
import net.thesmallthings.hellcraft.world.ParadisoGeometry.Sphere;

/**
 * Paradiso's columns of light (Phase 7): from each sphere's departure dais up through the hole in the next sphere's
 * arrival isle, in the sphere's colour, with motes of light rising in them. Drawn here rather than with beacons (a beacon
 * goes dark under any solid block, and the arrival isles have canopies). A column whose trial you haven't passed is
 * drawn thin and dim (ClientNav.spheres); stand in a bright one to rise.
 */
public final class LightColumns {
	private LightColumns() {
	}

	private static final int MOTES = 24;

	public static void render(LevelRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || !ClientNav.inParadiso(mc)) {
			return;
		}
		Vec3 cam = context.levelState().cameraRenderState.pos;
		double t = mc.level.getGameTime() + context.levelState().worldPartialTicks;
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.debugQuads(), (pose, buffer) -> {
			for (Sphere s : ParadisoGeometry.SPHERES) {
				double[] d = ParadisoGeometry.departure(s);
				if (d == null) {
					continue;
				}
				boolean open = ClientNav.passed(s);
				double bottom = ParadisoGeometry.topY(s) + 1;
				double top = ParadisoGeometry.topY(ParadisoGeometry.SPHERES[s.ordinal() + 1]) + 6;
				float x = (float) (d[0] - cam.x);
				float z = (float) (d[1] - cam.z);
				float y0 = (float) (bottom - cam.y);
				float y1 = (float) (top - cam.y);
				int r = s.colour >> 16 & 255;
				int g = s.colour >> 8 & 255;
				int b = s.colour & 255;
				float pulse = (float) (0.85 + 0.15 * Math.sin(t * 0.08 + s.ordinal()));
				if (open) {
					box(buffer, pose, x, z, 1.1f, y0, y1, Math.round(r * 0.75f * pulse), Math.round(g * 0.75f * pulse), Math.round(b * 0.75f * pulse));
					box(buffer, pose, x, z, 0.4f, y0, y1, 255, 255, 255);
				} else {
					box(buffer, pose, x, z, 0.25f, y0, y1, r / 2, g / 2, b / 2);
				}
				for (int i = 0; i < (open ? MOTES : MOTES / 4); i++) {
					double span = top - bottom;
					double phase = (t * (0.25 + 0.05 * (i % 4)) + i * 13.7) % span;
					double a = i * 2.399 + t * 0.05;
					float mx = x + (float) (Math.cos(a) * 0.8);
					float mz = z + (float) (Math.sin(a) * 0.8);
					float my = (float) (bottom + phase - cam.y);
					box(buffer, pose, mx, mz, 0.08f, my, my + 0.3f, 255, 255, 230);
				}
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
