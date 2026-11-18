package net.thesmallthings.hellcraft.client.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.client.combat.anim.Settings;
import net.thesmallthings.hellcraft.entity.boss.BossEntity;
import net.thesmallthings.hellcraft.entity.monster.Mimic;
import net.thesmallthings.hellcraft.entity.npc.Angel;
import net.thesmallthings.hellcraft.entity.npc.Damned;
import net.thesmallthings.hellcraft.entity.npc.Shade;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Health bars on mobs: a small bar over each hurt mob within {@link #RANGE} blocks (and over the one under the
 * crosshair, hurt or not), or, with /hellcraft-client healthbars side, standing upright beside it. Red for monsters,
 * green for the rest; a pale stretch shows what the last blow took and drains away after it. Bosses keep their boss
 * bar instead, players, armour stands and the souls who can't be hurt (the damned, Shades, angels) get none, and a mimic still
 * hiding gives itself away only once it is hurt. Drawn in the world like the danger marks (depth tested, so walls hide
 * them), turned to face the camera.
 */
public final class MobHealthBars {
	/** How far off bars are drawn (blocks). */
	public static final double RANGE = 24;
	/** Ticks a bar stays after the reason for it ends (looked away, healed), fading over the last FADE. */
	private static final int LINGER = 40;
	private static final int FADE = 10;
	/** Ticks the pale "lost" stretch waits before draining, and how much of the bar it drains a tick. */
	private static final int LAG_WAIT = 10;
	private static final float LAG_DRAIN = 0.02f;

	private static final Map<Integer, Bar> BARS = new HashMap<>();

	private MobHealthBars() {
	}

	/** One mob's bar: how full it was drawn, what the pale stretch still shows, and how long since it was wanted. */
	private static final class Bar {
		float lag = 1f;
		float last = 1f;
		int sinceHit = LAG_WAIT;
		int unwanted;
	}

	/** Who gets a bar at all. */
	public static boolean eligible(LivingEntity e) {
		if (!(e instanceof Mob) || e instanceof BossEntity || e instanceof EnderDragon || e instanceof WitherBoss || e instanceof Damned || e instanceof Shade || e instanceof Angel) {
			return false;
		}
		if (e instanceof Mimic m && m.dormant() && e.getHealth() >= e.getMaxHealth()) {
			return false;
		}
		return e.isAlive() && !e.isInvisible() && e.getMaxHealth() > 0;
	}

	private static boolean wanted(LivingEntity e, Entity looked) {
		return e.getHealth() < e.getMaxHealth() || e == looked;
	}

	/** Each tick: which mobs want a bar, and the pale stretch's drain. */
	public static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null || Settings.healthBars() == Settings.HealthBars.OFF) {
			BARS.clear();
			return;
		}
		Entity looked = mc.hitResult instanceof EntityHitResult hit ? hit.getEntity() : null;
		Map<Integer, LivingEntity> near = new HashMap<>();
		for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, mc.player.getBoundingBox().inflate(RANGE))) {
			if (eligible(e)) {
				near.put(e.getId(), e);
			}
		}
		for (LivingEntity e : near.values()) {
			if (wanted(e, looked)) {
				BARS.computeIfAbsent(e.getId(), id -> {
					Bar b = new Bar();
					b.lag = b.last = fraction(e);
					return b;
				}).unwanted = 0;
			}
		}
		Iterator<Map.Entry<Integer, Bar>> it = BARS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, Bar> entry = it.next();
			LivingEntity e = near.get(entry.getKey());
			Bar b = entry.getValue();
			if (e == null) {
				it.remove();
				continue;
			}
			if (!wanted(e, looked) && ++b.unwanted > LINGER) {
				it.remove();
				continue;
			}
			float now = fraction(e);
			if (now < b.last) {
				b.sinceHit = 0;
			}
			b.last = now;
			if (now >= b.lag) {
				b.lag = now;
			} else if (++b.sinceHit > LAG_WAIT) {
				b.lag = Math.max(now, b.lag - LAG_DRAIN);
			}
		}
	}

	private static float fraction(LivingEntity e) {
		return Math.max(0f, Math.min(1f, e.getHealth() / e.getMaxHealth()));
	}

	/** How many mobs have a bar now (for tests). */
	public static int count() {
		return BARS.size();
	}

	public static void clear() {
		BARS.clear();
	}

	public static void render(LevelRenderContext context) {
		Settings.HealthBars mode = Settings.healthBars();
		Minecraft mc = Minecraft.getInstance();
		if (BARS.isEmpty() || mode == Settings.HealthBars.OFF || mc.level == null) {
			return;
		}
		Vec3 cam = context.levelState().cameraRenderState.pos;
		float partial = context.levelState().worldPartialTicks;
		List<float[]> quads = new ArrayList<>();
		for (Map.Entry<Integer, Bar> entry : BARS.entrySet()) {
			if (!(mc.level.getEntity(entry.getKey()) instanceof LivingEntity e) || !e.isAlive()) {
				continue;
			}
			Bar b = entry.getValue();
			float alpha = b.unwanted <= LINGER - FADE ? 1f : Math.max(0f, (LINGER - b.unwanted) / (float) FADE);
			if (alpha <= 0.02f) {
				continue;
			}
			Vec3 at = e.getPosition(partial);
			// turned to face the camera about the vertical: "right" is to the camera's right as it looks at the mob
			double dx = cam.x - at.x;
			double dz = cam.z - at.z;
			double len = Math.sqrt(dx * dx + dz * dz);
			if (len < 1e-3) {
				continue;
			}
			double toX = dx / len;
			double toZ = dz / len;
			double rightX = toZ;
			double rightZ = -toX;
			float now = fraction(e);
			boolean enemy = e instanceof Enemy;
			int[] fill = enemy ? new int[]{196, 28, 28} : new int[]{70, 180, 60};
			if (mode == Settings.HealthBars.SIDE) {
				// upright beside the mob, at the camera's right of it, from its feet to near its top
				double h = Math.max(0.5, Math.min(2.4, e.getBbHeight() * 0.85));
				double w = 0.09;
				double off = e.getBbWidth() / 2 + 0.3;
				double cx = at.x + rightX * off;
				double cz = at.z + rightZ * off;
				double y0 = at.y + (e.getBbHeight() - h) / 2;
				upright(quads, cam, cx, y0, cz, rightX, rightZ, toX, toZ, w, h, 0, 1, new int[]{18, 8, 8}, 170 * alpha, 0);
				upright(quads, cam, cx, y0, cz, rightX, rightZ, toX, toZ, w * 0.7, h, now, b.lag, new int[]{235, 225, 215}, 200 * alpha, 1);
				upright(quads, cam, cx, y0, cz, rightX, rightZ, toX, toZ, w * 0.7, h, 0, now, fill, 235 * alpha, 2);
			} else {
				// over its head, a little above where a name would sit
				double w = Math.max(0.6, Math.min(2.0, e.getBbWidth() * 1.1));
				double h = 0.1;
				double y = at.y + e.getBbHeight() + 0.35;
				level(quads, cam, at.x, y, at.z, rightX, rightZ, toX, toZ, w, h, 0, 1, new int[]{18, 8, 8}, 170 * alpha, 0);
				level(quads, cam, at.x, y, at.z, rightX, rightZ, toX, toZ, w, h * 0.6, now, b.lag, new int[]{235, 225, 215}, 200 * alpha, 1);
				level(quads, cam, at.x, y, at.z, rightX, rightZ, toX, toZ, w, h * 0.6, 0, now, fill, 235 * alpha, 2);
			}
		}
		if (quads.isEmpty()) {
			return;
		}
		PoseStack poseStack = context.poseStack();
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, buffer) -> {
			for (float[] q : quads) {
				quad(buffer, pose, q);
			}
		});
	}

	/**
	 * A level bar's stretch from `from` to `to` (of its width, left to right as the camera sees it), centred on x, z;
	 * `layer` lifts it towards the camera so the fill lies over the backing.
	 */
	private static void level(List<float[]> quads, Vec3 cam, double x, double y, double z, double rx, double rz, double tx, double tz,
			double w, double h, float from, float to, int[] rgb, float alpha, int layer) {
		if (to - from <= 0.001f) {
			return;
		}
		double lift = 0.01 * layer;
		double a = -w / 2 + w * from;
		double c = -w / 2 + w * to;
		double bx = x + tx * lift - cam.x;
		double bz = z + tz * lift - cam.z;
		double y0 = y - h / 2 - cam.y;
		double y1 = y + h / 2 - cam.y;
		quads.add(new float[]{
				(float) (bx + rx * a), (float) y0, (float) (bz + rz * a),
				(float) (bx + rx * c), (float) y0, (float) (bz + rz * c),
				(float) (bx + rx * c), (float) y1, (float) (bz + rz * c),
				(float) (bx + rx * a), (float) y1, (float) (bz + rz * a),
				rgb[0], rgb[1], rgb[2], alpha});
	}

	/** An upright bar's stretch from `from` to `to` (of its height, bottom to top), its foot at y0. */
	private static void upright(List<float[]> quads, Vec3 cam, double x, double y0, double z, double rx, double rz, double tx, double tz,
			double w, double h, float from, float to, int[] rgb, float alpha, int layer) {
		if (to - from <= 0.001f) {
			return;
		}
		double lift = 0.01 * layer;
		double bx = x + tx * lift - cam.x;
		double bz = z + tz * lift - cam.z;
		double a = y0 + h * from - cam.y;
		double c = y0 + h * to - cam.y;
		quads.add(new float[]{
				(float) (bx - rx * w / 2), (float) a, (float) (bz - rz * w / 2),
				(float) (bx + rx * w / 2), (float) a, (float) (bz + rz * w / 2),
				(float) (bx + rx * w / 2), (float) c, (float) (bz + rz * w / 2),
				(float) (bx - rx * w / 2), (float) c, (float) (bz - rz * w / 2),
				rgb[0], rgb[1], rgb[2], alpha});
	}

	private static void quad(VertexConsumer buffer, PoseStack.Pose pose, float[] q) {
		int r = (int) q[12];
		int g = (int) q[13];
		int b = (int) q[14];
		int a = (int) q[15];
		// both windings, so it shows whichever way the quads cull
		for (int k : new int[]{0, 1, 2, 3, 3, 2, 1, 0}) {
			buffer.addVertex(pose, q[k * 3], q[k * 3 + 1], q[k * 3 + 2]).setColor(r, g, b, a);
		}
	}
}
