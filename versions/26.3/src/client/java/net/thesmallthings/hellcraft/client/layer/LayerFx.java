package net.thesmallthings.hellcraft.client.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.client.cutscene.CutscenePlayer;
import net.thesmallthings.hellcraft.client.nav.ClientNav;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.Sullen;
import net.thesmallthings.hellcraft.world.WindStreams;
import net.thesmallthings.hellcraft.world.Zone;

import java.util.ArrayList;
import java.util.List;

/**
 * The upper circles' rules as you see and feel them, on the client:
 * <ul>
 *     <li>Lust's wind streams are pale twisting strands with streaks running along them (render), with a swirl
 *     over each pad; step into one and it carries you (the server lets you weigh nothing meanwhile: LayerRules).
 *     Sneak to let go.</li>
 *     <li>The Styx bubbles where the sullen lurk (Sullen), and across it the city of Dis glows red through the fog.</li>
 * </ul>
 * Both come from the shared geometry, so the server sends nothing for them.
 */
public final class LayerFx {
	private LayerFx() {
	}

	/** How far away the wind and the bubbles are shown. */
	private static final double SHOW = 56.0;
	private static int ticks;

	public static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		if (player == null || level == null || !ClientNav.inHell(mc) || mc.isPaused()) {
			return;
		}
		ticks++;
		Zone zone = InfernoGeometry.zoneAt(player.getX(), player.getZ());
		if (zone == Zone.LUST) {
			ride(player);
			wisps(level, player.position());
		}
		if (zone == Zone.STYX || zone == Zone.WALLS_OF_DIS) {
			bubbles(level, player.position());
		}
	}

	/** The wind carries you along its heart, unless you sneak. */
	private static void ride(LocalPlayer player) {
		if (player.isShiftKeyDown() || player.isPassenger() || player.isFallFlying() || player.isSpectator() || CutscenePlayer.active()) {
			return;
		}
		WindStreams.Hit hit = WindStreams.find(player.getX(), player.getY() + 0.9, player.getZ());
		if (hit == null || hit.atEnd()) {
			return;
		}
		double[] d = hit.direction();
		// along the stream, and drawn back toward its heart
		double pullX = (hit.x() - player.getX()) * 0.25;
		double pullY = (hit.y() - (player.getY() + 0.9)) * 0.25;
		double pullZ = (hit.z() - player.getZ()) * 0.25;
		player.setDeltaMovement(d[0] * WindStreams.SPEED + pullX, d[1] * WindStreams.SPEED + pullY, d[2] * WindStreams.SPEED + pullZ);
		player.resetFallDistance();
	}

	/** A swirl over each pad, where the wind takes you up. */
	private static void wisps(ClientLevel level, Vec3 at) {
		for (double[] pad : WindStreams.pads()) {
			double dx = pad[0] - at.x;
			double dz = pad[1] - at.z;
			if (dx * dx + dz * dz > SHOW * SHOW) {
				continue;
			}
			double ground = InfernoGeometry.baseHeight(pad[0], pad[1]);
			for (int k = 0; k < 3; k++) {
				double a = ticks * 0.35 + k * 2.094;
				double h = (ticks * 0.15 + k * 1.3) % 6.0;
				level.addParticle(ParticleTypes.CLOUD, pad[0] + Math.cos(a) * 1.4, ground + 1 + h, pad[1] + Math.sin(a) * 1.4, 0, 0.08, 0);
			}
		}
	}

	/**
	 * Draws the wind streams near the camera: three pale strands twisting round each stream's heart, with streaks
	 * running along them the way the wind blows. Camera-facing ribbons, drawn on both sides.
	 */
	public static void render(LevelRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !ClientNav.inHell(mc)) {
			return;
		}
		Vec3 cam = context.levelState().cameraRenderState.pos;
		double r = Math.sqrt(cam.x * cam.x + cam.z * cam.z);
		if (InfernoGeometry.zoneAt(cam.x, cam.z) == Zone.STYX) {
			disGlow(context, cam);
			return;
		}
		if (r > InfernoGeometry.outerRadius(Zone.LUST) + RIBBON_SHOW || r < InfernoGeometry.outerRadius(Zone.GLUTTONY) - RIBBON_SHOW) {
			return;
		}
		float time = ticks + context.levelState().worldPartialTicks;
		List<float[]> quads = new ArrayList<>();
		for (WindStreams.Stream s : WindStreams.all()) {
			for (int i = 0; i < s.size() - 1; i++) {
				double dx = s.x()[i] - cam.x;
				double dz = s.z()[i] - cam.z;
				if (dx * dx + dz * dz > RIBBON_SHOW * RIBBON_SHOW) {
					continue;
				}
				for (int k = 0; k < 3; k++) {
					strand(quads, s, i, k, time, cam);
				}
			}
		}
		if (quads.isEmpty()) {
			return;
		}
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.debugQuads(), (pose, buffer) -> {
			for (float[] q : quads) {
				// both windings, so the ribbon shows from either side
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

	/**
	 * Across the Fen the city of Dis burns red on the horizon, through the fog, so you always know which way is in
	 * (Inferno VIII: "the mosques within the valley, red as if they had come out of the fire"): a low red wash toward
	 * the centre of Hell, and over it the flickering towers of the burning city.
	 */
	private static void disGlow(LevelRenderContext context, Vec3 cam) {
		double centre = Math.atan2(-cam.z, -cam.x);
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.debugQuads(), (pose, buffer) -> {
			// the wash
			glowQuad(buffer, pose, centre, 0, 160, 90, -10, 22, 255, 70, 20, 80);
			// the towers, each flickering on its own
			for (int k = -3; k <= 3; k++) {
				double flicker = 0.75 + 0.25 * Math.sin(ticks * 0.21 + k * 1.7) * Math.sin(ticks * 0.13 + k);
				double height = (18 + 9 * Math.floorMod(k * 7 + 9, 4)) * (0.9 + 0.1 * flicker);
				glowQuad(buffer, pose, centre, k * 0.09, 175 + 6 * (k & 1), 2.5 + (k & 1) * 1.5, -6, height, 255, 120, 40, (int) (170 * flicker));
			}
		});
	}

	/** A quad facing the camera, toward angle + offset at dist, half wide, from low to high: alpha fades upward. */
	private static void glowQuad(VertexConsumer buffer, PoseStack.Pose pose, double angle, double offset, double dist, double half,
								 double low, double high, int r, int g, int b, int alpha) {
		double a = angle + offset;
		float cx = (float) (Math.cos(a) * dist);
		float cz = (float) (Math.sin(a) * dist);
		float sx = (float) (-Math.sin(a) * half);
		float sz = (float) (Math.cos(a) * half);
		float lo = (float) low;
		float hi = (float) high;
		buffer.addVertex(pose, cx - sx, lo, cz - sz).setColor(r, g, b, alpha);
		buffer.addVertex(pose, cx + sx, lo, cz + sz).setColor(r, g, b, alpha);
		buffer.addVertex(pose, cx + sx, hi, cz + sz).setColor(r, g, b, 0);
		buffer.addVertex(pose, cx - sx, hi, cz - sz).setColor(r, g, b, 0);
		buffer.addVertex(pose, cx - sx, hi, cz - sz).setColor(r, g, b, 0);
		buffer.addVertex(pose, cx + sx, hi, cz + sz).setColor(r, g, b, 0);
		buffer.addVertex(pose, cx + sx, lo, cz + sz).setColor(r, g, b, alpha);
		buffer.addVertex(pose, cx - sx, lo, cz - sz).setColor(r, g, b, alpha);
	}

	private static final double RIBBON_SHOW = 160.0;
	private static final double STRAND_RADIUS = 0.9;
	private static final double STRAND_WIDTH = 0.16;

	/** One segment of strand k between stream points i and i + 1. */
	private static void strand(List<float[]> quads, WindStreams.Stream s, int i, int k, float time, Vec3 cam) {
		double[] a = strandPoint(s, i, k, time);
		double[] b = strandPoint(s, i + 1, k, time);
		// streaks flow along the stream: brightest where the wave is high
		double wave = Math.sin(i * 0.9 + k * 2.1 - time * 0.55);
		float alpha = (float) (0.06 + 0.5 * Math.max(0, wave) * Math.max(0, wave));
		double mx = (a[0] + b[0]) / 2 - cam.x;
		double my = (a[1] + b[1]) / 2 - cam.y;
		double mz = (a[2] + b[2]) / 2 - cam.z;
		// strands brushing past the camera (while you ride) fade, or they would fill the screen
		double near = Math.sqrt(mx * mx + my * my + mz * mz);
		alpha *= (float) Math.max(0, Math.min(1, (near - 1.5) / 4.0));
		if (alpha < 0.01f) {
			return;
		}
		double sx = b[0] - a[0];
		double sy = b[1] - a[1];
		double sz = b[2] - a[2];
		// across the segment, facing the camera
		double cx = sy * mz - sz * my;
		double cy = sz * mx - sx * mz;
		double cz = sx * my - sy * mx;
		double len = Math.sqrt(cx * cx + cy * cy + cz * cz);
		if (len < 1e-6) {
			return;
		}
		double w = STRAND_WIDTH / len;
		cx *= w;
		cy *= w;
		cz *= w;
		quads.add(new float[]{
				(float) (a[0] - cx - cam.x), (float) (a[1] - cy - cam.y), (float) (a[2] - cz - cam.z),
				(float) (a[0] + cx - cam.x), (float) (a[1] + cy - cam.y), (float) (a[2] + cz - cam.z),
				(float) (b[0] + cx - cam.x), (float) (b[1] + cy - cam.y), (float) (b[2] + cz - cam.z),
				(float) (b[0] - cx - cam.x), (float) (b[1] - cy - cam.y), (float) (b[2] - cz - cam.z),
				alpha});
	}

	/** Where strand k passes stream point i: twisting round the heart as the wind turns. */
	private static double[] strandPoint(WindStreams.Stream s, int i, int k, float time) {
		int a = Math.max(0, i - 1);
		int b = Math.min(s.size() - 1, i + 1);
		double dx = s.x()[b] - s.x()[a];
		double dy = s.y()[b] - s.y()[a];
		double dz = s.z()[b] - s.z()[a];
		double len = Math.max(1e-6, Math.sqrt(dx * dx + dy * dy + dz * dz));
		dx /= len;
		dy /= len;
		dz /= len;
		// a frame round the direction: n1 level and across it, n2 square to both
		double ux = 0, uy = 1, uz = 0;
		if (Math.abs(dy) > 0.95) {
			ux = 1;
			uy = 0;
		}
		double n1x = dy * uz - dz * uy;
		double n1y = dz * ux - dx * uz;
		double n1z = dx * uy - dy * ux;
		double n1 = Math.sqrt(n1x * n1x + n1y * n1y + n1z * n1z);
		n1x /= n1;
		n1y /= n1;
		n1z /= n1;
		double n2x = n1y * dz - n1z * dy;
		double n2y = n1z * dx - n1x * dz;
		double n2z = n1x * dy - n1y * dx;
		double angle = i * 0.45 + k * 2.094 + time * 0.05;
		double c = Math.cos(angle) * STRAND_RADIUS;
		double sn = Math.sin(angle) * STRAND_RADIUS;
		return new double[]{s.x()[i] + n1x * c + n2x * sn, s.y()[i] + n1y * c + n2y * sn, s.z()[i] + n1z * c + n2z * sn};
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float[] q, int corner) {
		buffer.addVertex(pose, q[corner * 3], q[corner * 3 + 1], q[corner * 3 + 2]).setColor(232, 226, 255, (int) (q[12] * 255));
	}

	private static void bubbles(ClientLevel level, Vec3 at) {
		if (ticks % 3 != 0) {
			return;
		}
		RandomSource random = level.getRandom();
		int r = (int) SHOW / 2;
		int bx = (int) Math.floor(at.x);
		int bz = (int) Math.floor(at.z);
		for (int x = bx - r; x <= bx + r; x += 4) {
			for (int z = bz - r; z <= bz + r; z += 4) {
				int[] s = Sullen.spot(x, z);
				if (s == null || random.nextInt(2) != 0) {
					continue;
				}
				double surface = InfernoGeometry.fluidLevel(s[0] + 0.5, s[1] + 0.5);
				for (int k = 0; k < 3; k++) {
					level.addParticle(ParticleTypes.BUBBLE_POP, s[0] + 0.2 + random.nextDouble() * 0.6, surface + 0.05, s[1] + 0.2 + random.nextDouble() * 0.6, 0, 0.03, 0);
				}
				level.addParticle(ParticleTypes.SPLASH, s[0] + 0.5, surface + 0.1, s[1] + 0.5, 0, 0.05, 0);
				level.addParticle(ParticleTypes.BUBBLE, s[0] + 0.5, surface - 0.6, s[1] + 0.5, 0, 0.1, 0);
			}
		}
	}
}
