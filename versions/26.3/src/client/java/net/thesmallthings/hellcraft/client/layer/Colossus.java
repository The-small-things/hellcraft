package net.thesmallthings.hellcraft.client.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.client.mixin.BossOverlayAccessor;
import net.thesmallthings.hellcraft.client.nav.ClientNav;
import net.thesmallthings.hellcraft.client.render.boss.AnimatedBossModel;
import net.thesmallthings.hellcraft.client.render.boss.AnimatedBossRenderState;
import net.thesmallthings.hellcraft.client.render.boss.AnimatedBossRenderer;
import net.thesmallthings.hellcraft.client.render.gen.EmperorAnimations;
import net.thesmallthings.hellcraft.entity.boss.EmperorEntity;
import net.thesmallthings.hellcraft.entity.boss.gen.EmperorAnim;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The Emperor asleep at the bottom of Hell (L1, "the windmill"): while no fight is on, the client draws him by itself,
 * chest-deep in the pit, his wings turning slowly, so he is there for everyone and can be seen from far off. Near (within
 * the view distance) he is drawn as he is, textured and in the fog; further off, where the fog would swallow him, as a
 * dark shape against it, "as when a thick fog breathes ... a mill which the wind turns" (Inferno XXXIV), clearer the
 * nearer you come. When the fight brings the real Emperor (EmperorEntity), this one steps aside.
 */
public final class Colossus {
	private Colossus() {
	}

	private static final Identifier TEXTURE = HellcraftMod.id("textures/entity/emperor.png");
	/** He faces east, the way the first road comes in. */
	private static final float FACING = -90.0f;
	/** The top of Judecca's ice: whatever of him is lower, far off, is hidden by the rim of the pit. */
	private static final double RIM_Y = -42.5;

	private static AnimatedBossModel model;
	private static AnimatedBossRenderState state;

	private static void init(Minecraft mc) {
		if (model != null) {
			return;
		}
		ModelPart root = mc.getEntityModels().bakeLayer(AnimatedBossRenderer.layer("emperor"));
		model = new AnimatedBossModel(root, EmperorAnimations.all(), -1, 0.0f);
		model.hideable("root", "upper", "neck", "head", "eyes");
		state = new AnimatedBossRenderState(EmperorAnim.values().length);
		state.partHidden = true;
		state.states[EmperorAnim.IDLE.ordinal()].start(0);
		state.states[EmperorAnim.DORMANT.ordinal()].start(0);
		for (int i = 0; i < state.speeds.length; i++) {
			state.speeds[i] = 1.0f;
		}
	}

	/** His feet: on the pit's floor at the centre of Hell. */
	public static Vec3 feet() {
		return new Vec3(0.5, InfernoGeometry.PIT_FLOOR_Y, 0.5);
	}

	public static void render(LevelRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || !ClientNav.inHell(mc)) {
			return;
		}
		Vec3 feet = feet();
		if (!mc.level.getEntitiesOfClass(EmperorEntity.class, new AABB(feet, feet).inflate(96)).isEmpty() || luciferAwake(mc)) {
			return;
		}
		init(mc);
		Vec3 cam = context.levelState().cameraRenderState.pos;
		double dist = Math.sqrt((cam.x - feet.x) * (cam.x - feet.x) + (cam.z - feet.z) * (cam.z - feet.z));
		double view = mc.options.getEffectiveRenderDistance() * 16.0;
		state.ageInTicks = mc.level.getGameTime() + context.levelState().worldPartialTicks;
		model.setupAnim(state);
		if (dist < view * 0.85) {
			PoseStack pose = context.poseStack();
			pose.pushPose();
			place(pose, feet.subtract(cam));
			BlockPos at = BlockPos.containing(feet.x, feet.y + 20, feet.z);
			int light = LightCoordsUtil.pack(mc.level.getBrightness(LightLayer.BLOCK, at), mc.level.getBrightness(LightLayer.SKY, at));
			context.submitNodeCollector().submitModel(model, state, pose, RenderTypes.entityCutout(TEXTURE), light, OverlayTexture.NO_OVERLAY, -1, null, 0);
			pose.popPose();
		} else {
			silhouette(context, feet.subtract(cam), cam, dist);
		}
	}

	/** The same placing as an entity renderer's: turned to face, flipped into model space, scaled, feet on the ground. */
	private static void place(PoseStack pose, Vec3 offset) {
		pose.translate(offset.x, offset.y, offset.z);
		pose.rotateDegrees(Axis.YP, 180.0f - FACING);
		pose.scale(-1.0f, -1.0f, 1.0f);
		pose.scale(EmperorEntity.SCALE, EmperorEntity.SCALE, EmperorEntity.SCALE);
		pose.translate(0.0f, -1.501f, 0.0f);
	}

	/** True while Lucifer's fight is on (his boss bar shows): the fight draws him then. */
	private static boolean luciferAwake(Minecraft mc) {
		for (LerpingBossEvent e : ((BossOverlayAccessor) mc.gui.hud.getBossOverlay()).hellcraft$events().values()) {
			if (e.getName().getString().toUpperCase(java.util.Locale.ROOT).startsWith("LUCIFER")) {
				return true;
			}
		}
		return false;
	}

	/** His shape as flat dark faces (no fog reaches them: they grey a little with distance instead). */
	private static void silhouette(LevelRenderContext context, Vec3 offset, Vec3 cam, double dist) {
		List<float[]> quads = new ArrayList<>();
		// a little greyer the further off, but always darker than the fog, so he looms out of it
		float fade = (float) Math.max(0.0, Math.min(0.3, (dist - 150) / 1500));
		float rimY = (float) (RIM_Y - cam.y);
		// worked out relative to the camera, in the world's axes (so the rim can cut him off), then drawn as the sky's specks are
		PoseStack local = new PoseStack();
		place(local, offset);
		model.root().visit(local, (p, path, index, cube) -> {
			for (ModelPart.Polygon polygon : cube.polygons) {
				float[] q = new float[13];
				Vector3f n = p.transformNormal(polygon.normal(), new Vector3f());
				for (int i = 0; i < 4; i++) {
					ModelPart.Vertex v = polygon.vertices()[i];
					Vector3f w = p.pose().transformPosition(v.worldX(), v.worldY(), v.worldZ(), new Vector3f());
					q[i * 3] = w.x;
					q[i * 3 + 1] = Math.max(rimY, w.y);
					q[i * 3 + 2] = w.z;
				}
				// lit a little from above, so his shape reads
				q[12] = 0.75f + 0.25f * Math.max(0, n.y);
				quads.add(q);
			}
		});
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.debugQuads(), (p, buffer) -> {
			for (float[] q : quads) {
				int r = shade(22, q[12], fade, 70);
				int g = shade(22, q[12], fade, 72);
				int b = shade(30, q[12], fade, 84);
				for (int i = 0; i < 4; i++) {
					vertex(buffer, p, q, i, r, g, b);
				}
			}
		});
	}

	private static int shade(int base, float light, float fade, int fog) {
		return Math.round((base * light) * (1 - fade) + fog * fade);
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float[] q, int i, int r, int g, int b) {
		buffer.addVertex(pose, q[i * 3], q[i * 3 + 1], q[i * 3 + 2]).setColor(r, g, b, 255);
	}
}
