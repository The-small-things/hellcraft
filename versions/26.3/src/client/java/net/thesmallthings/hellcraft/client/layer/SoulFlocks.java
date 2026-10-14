package net.thesmallthings.hellcraft.client.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.client.nav.ClientNav;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.Zone;

import java.util.ArrayList;
import java.util.List;

/**
 * Lust's sky (L-II): the souls of the lustful whirl overhead in vast flocks, "as the starlings with their wings are
 * borne in the cold season in large band and full" (Inferno V). Each flock is a few hundred dark specks that swell,
 * stretch and fold like a murmuration, and is blown round the circle by the hurricane that never rests. Drawn on the
 * client only, from the time: every player sees the same sky.
 */
public final class SoulFlocks {
	private SoulFlocks() {
	}

	private static final int FLOCKS = 7;
	private static final int BIRDS = 420;
	/** How far away a flock is drawn. */
	private static final double SHOW = 320;
	private static final float[][] SHAPE = new float[BIRDS][4];

	static {
		// birds spread through a squashed ball, thicker toward its skin, each with its own wobble phase
		long seed = 0x5EEDL;
		for (int i = 0; i < BIRDS; i++) {
			seed = seed * 6364136223846793005L + 1442695040888963407L;
			double u = ((seed >>> 11) & 0xFFFF) / 65535.0 * 2 - 1;
			seed = seed * 6364136223846793005L + 1442695040888963407L;
			double t = ((seed >>> 11) & 0xFFFF) / 65535.0 * Math.PI * 2;
			seed = seed * 6364136223846793005L + 1442695040888963407L;
			double r = Math.pow(((seed >>> 11) & 0xFFFF) / 65535.0, 0.35);
			double s = Math.sqrt(1 - u * u);
			SHAPE[i][0] = (float) (s * Math.cos(t) * r);
			SHAPE[i][1] = (float) (u * r);
			SHAPE[i][2] = (float) (s * Math.sin(t) * r);
			seed = seed * 6364136223846793005L + 1442695040888963407L;
			SHAPE[i][3] = (float) (((seed >>> 11) & 0xFFFF) / 65535.0 * Math.PI * 2);
		}
	}

	public static void render(LevelRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || !ClientNav.inHell(mc)) {
			return;
		}
		Vec3 cam = context.levelState().cameraRenderState.pos;
		double cr = Math.sqrt(cam.x * cam.x + cam.z * cam.z);
		double outer = InfernoGeometry.outerRadius(Zone.LUST);
		double inner = InfernoGeometry.outerRadius(Zone.GLUTTONY);
		if (cr > outer + SHOW || cr < inner - SHOW) {
			return;
		}
		// the game time, so everyone sees the flocks in the same places
		double time = mc.level.getGameTime() + context.levelState().worldPartialTicks;
		List<float[]> specks = new ArrayList<>();
		for (int f = 0; f < FLOCKS; f++) {
			double[] c = centre(f, time, outer, inner);
			double dx = c[0] - cam.x;
			double dz = c[2] - cam.z;
			if (dx * dx + dz * dz > SHOW * SHOW) {
				continue;
			}
			flock(specks, f, time, c, cam);
		}
		if (specks.isEmpty()) {
			return;
		}
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.debugQuads(), (pose, buffer) -> {
			for (float[] q : specks) {
				vertex(buffer, pose, q, 0);
				vertex(buffer, pose, q, 1);
				vertex(buffer, pose, q, 2);
				vertex(buffer, pose, q, 3);
				vertex(buffer, pose, q, 3);
				vertex(buffer, pose, q, 2);
				vertex(buffer, pose, q, 1);
				vertex(buffer, pose, q, 0);
			}
		});
	}

	/** Where flock f is: blown round the circle, drifting in and out, rising and falling well above the spires. */
	public static double[] centre(int f, double time, double outer, double inner) {
		double speed = 0.00045 * (1 + 0.15 * (f % 3));
		double angle = f * (Math.PI * 2 / FLOCKS) + time * speed;
		double mid = (outer + inner) / 2;
		double r = mid + (outer - inner) * 0.3 * Math.sin(time * 0.0007 + f * 1.7);
		double x = Math.cos(angle) * r;
		double z = Math.sin(angle) * r;
		double y = InfernoGeometry.baseHeight(x, z) + 62 + 14 * Math.sin(time * 0.0011 + f * 2.3);
		return new double[]{x, y, z};
	}

	private static void flock(List<float[]> specks, int f, double time, double[] c, Vec3 cam) {
		// the flock's shape breathes and stretches: three slow waves on its three axes, and a slow turn
		double a = 34 + 12 * Math.sin(time * 0.013 + f);
		double b = 8 + 5 * Math.sin(time * 0.017 + f * 2.1);
		double d = 18 + 10 * Math.sin(time * 0.011 + f * 0.7);
		double turn = time * 0.004 + f;
		double cos = Math.cos(turn);
		double sin = Math.sin(turn);
		// a fold travels through it, as in a real murmuration
		double fold = time * 0.05 + f;
		for (int i = 0; i < BIRDS; i++) {
			float[] s = SHAPE[i];
			double lx = s[0] * a;
			double ly = s[1] * b + Math.sin(s[0] * 2.5 + fold) * 4;
			double lz = s[2] * d;
			double wob = time * 0.09 + s[3];
			lx += Math.sin(wob) * 0.8;
			ly += Math.cos(wob * 1.3) * 0.6;
			double x = c[0] + lx * cos - lz * sin - cam.x;
			double y = c[1] + ly - cam.y;
			double z = c[2] + lx * sin + lz * cos - cam.z;
			double dist = Math.sqrt(x * x + y * y + z * z);
			// a speck about a third of a block, never smaller than a few pixels far off; wings beat as it flies
			double size = Math.max(0.45, dist * 0.004) * (0.75 + 0.25 * Math.abs(Math.sin(time * 0.6 + s[3] * 3)));
			// a quad facing the camera: across = up x view, upward = view x across
			double vx = x / dist;
			double vy = y / dist;
			double vz = z / dist;
			double ax = -vz;
			double az = vx;
			double al = Math.max(1e-6, Math.sqrt(ax * ax + az * az));
			ax = ax / al * size;
			az = az / al * size;
			double ux = vy * az;
			double uy = vz * ax - vx * az;
			double uz = -vy * ax;
			double ul = Math.max(1e-6, Math.sqrt(ux * ux + uy * uy + uz * uz));
			ux = ux / ul * size * 0.6;
			uy = uy / ul * size * 0.6;
			uz = uz / ul * size * 0.6;
			specks.add(new float[]{
					(float) (x - ax - ux), (float) (y - uy), (float) (z - az - uz),
					(float) (x + ax - ux), (float) (y - uy), (float) (z + az - uz),
					(float) (x + ax + ux), (float) (y + uy), (float) (z + az + uz),
					(float) (x - ax + ux), (float) (y + uy), (float) (z - az + uz)});
		}
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float[] q, int corner) {
		buffer.addVertex(pose, q[corner * 3], q[corner * 3 + 1], q[corner * 3 + 2]).setColor(28, 16, 34, 230);
	}
}
