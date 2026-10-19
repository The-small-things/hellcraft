package net.thesmallthings.hellcraft.client.danger;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.thesmallthings.hellcraft.hazard.Danger;
import net.thesmallthings.hellcraft.net.DangerPayload;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Draws the danger language's marks on the ground (hazard/Danger): block by block on top of the terrain, the
 * outline bright from the start and the inside filling from the middle out until the blow lands, then a last bright
 * flash, and the blow's eruption (Impacts). Each colour has its own sound: a low horn for red, a bell for white, a chime
 * for purple.
 */
public final class DangerMarks {
	private DangerMarks() {
	}

	private static final int[] COLOURS = {0xFF2020, 0xFFFFFF, 0xB040FF};
	/** How long a mark flashes after its blow lands. */
	private static final int AFTERGLOW = 6;
	private static final List<Mark> MARKS = new ArrayList<>();

	private static final class Mark {
		final DangerPayload p;
		double x;
		double z;
		int age;

		Mark(DangerPayload p) {
			this.p = p;
			this.x = p.x();
			this.z = p.z();
		}

		Danger.Shape shape() {
			return Danger.Shape.values()[p.shape()];
		}

		/** How far from the start of the mark a column lies (0 at its heart, 1 at its far edge), or -1 if outside. */
		double reach(double cx, double cz) {
			double dx = cx - x;
			double dz = cz - z;
			double d = Math.sqrt(dx * dx + dz * dz);
			return switch (shape()) {
				case CIRCLE -> d <= p.size() ? d / p.size() : -1;
				case RING -> d <= p.size() && d >= p.width() ? (d - p.width()) / Math.max(0.01, p.size() - p.width()) : -1;
				case LINE -> {
					double along = dx * Math.cos(p.angle()) + dz * Math.sin(p.angle());
					double across = Math.abs(-dx * Math.sin(p.angle()) + dz * Math.cos(p.angle()));
					yield along >= 0 && along <= p.size() && across <= p.width() ? along / p.size() : -1;
				}
				case CONE -> {
					if (d > p.size() || d < 0.5) {
						yield d < 0.5 ? 0 : -1;
					}
					double off = Math.abs(Math.atan2(Math.sin(Math.atan2(dz, dx) - p.angle()), Math.cos(Math.atan2(dz, dx) - p.angle())));
					yield off <= p.width() ? d / p.size() : -1;
				}
				case CLEAR -> -1;
			};
		}

		double extent() {
			return p.size() + 1;
		}
	}

	public static void receive(DangerPayload payload) {
		if (payload.shape() == Danger.Shape.CLEAR.ordinal()) {
			MARKS.removeIf(m -> m.p.id() == payload.id());
			return;
		}
		MARKS.add(new Mark(payload));
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null) {
			SoundEvent sound = switch (payload.colour()) {
				case 1 -> SoundEvents.BELL_BLOCK;
				case 2 -> SoundEvents.AMETHYST_BLOCK_RESONATE;
				default -> SoundEvents.NOTE_BLOCK_DIDGERIDOO.value();
			};
			float pitch = payload.colour() == 0 ? 0.6f : payload.colour() == 1 ? 1.4f : 0.8f;
			level.playLocalSound(payload.x(), payload.y(), payload.z(), sound, SoundSource.HOSTILE, 1.6f, pitch, false);
		}
	}

	public static void tick(Minecraft mc) {
		Impacts.tick();
		if (mc.level == null) {
			MARKS.clear();
			return;
		}
		if (mc.isPaused()) {
			return;
		}
		for (Iterator<Mark> it = MARKS.iterator(); it.hasNext(); ) {
			Mark m = it.next();
			m.age++;
			if (m.age == Math.max(1, m.p.warnTicks())) {
				Impacts.land(m.p, m.x, m.z);
			}
			if (m.p.follow() >= 0 && m.age <= m.p.followTicks()) {
				Entity e = mc.level.getEntity(m.p.follow());
				if (e != null) {
					m.x = e.getX();
					m.z = e.getZ();
				}
			}
			if (m.age > m.p.warnTicks() + AFTERGLOW) {
				it.remove();
			}
		}
	}

	public static void clear() {
		MARKS.clear();
	}

	public static void render(LevelRenderContext context) {
		if (MARKS.isEmpty()) {
			return;
		}
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		Vec3 camera = context.levelState().cameraRenderState.pos;
		float partial = context.levelState().worldPartialTicks;
		PoseStack poseStack = context.poseStack();
		List<float[]> quads = new ArrayList<>();
		for (Mark m : MARKS) {
			float time = m.age + partial;
			float fill = Math.min(1f, time / Math.max(1, m.p.warnTicks()));
			boolean landed = time >= m.p.warnTicks();
			int rgb = COLOURS[Math.min(COLOURS.length - 1, m.p.colour())];
			int r = (rgb >> 16) & 0xFF;
			int g = (rgb >> 8) & 0xFF;
			int b = rgb & 0xFF;
			// the last quarter second before it lands, the mark pulses
			float pulse = !landed && m.p.warnTicks() - time < 5 ? 0.5f + 0.5f * (float) Math.sin(time * 2.2) : 0f;
			double e = m.extent();
			int x0 = (int) Math.floor(m.x - e);
			int x1 = (int) Math.floor(m.x + e);
			int z0 = (int) Math.floor(m.z - e);
			int z1 = (int) Math.floor(m.z + e);
			for (int bx = x0; bx <= x1; bx++) {
				for (int bz = z0; bz <= z1; bz++) {
					double reach = m.reach(bx + 0.5, bz + 0.5);
					if (reach < 0) {
						continue;
					}
					boolean edge = m.reach(bx + 1.5, bz + 0.5) < 0 || m.reach(bx - 0.5, bz + 0.5) < 0
							|| m.reach(bx + 0.5, bz + 1.5) < 0 || m.reach(bx + 0.5, bz - 0.5) < 0;
					float alpha;
					if (landed) {
						alpha = 0.75f * (1f - (time - m.p.warnTicks()) / AFTERGLOW);
					} else if (edge) {
						alpha = 0.7f + 0.25f * pulse;
					} else if (reach <= fill) {
						alpha = 0.42f + 0.25f * pulse;
					} else {
						alpha = 0.14f;
					}
					if (alpha <= 0.01f) {
						continue;
					}
					double top = ground(level, bx, m.p.y(), bz);
					quads.add(new float[]{(float) (bx - camera.x), (float) (top + 0.03 - camera.y), (float) (bz - camera.z), r, g, b, alpha * 255});
				}
			}
		}
		if (quads.isEmpty()) {
			return;
		}
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, buffer) -> {
			for (float[] q : quads) {
				quad(buffer, pose, q);
			}
		});
	}

	/**
	 * The top of the ground under a mark: the first solid block from just above the mark's own height down to a few
	 * below (the heightmap would find the roof of the Nether, or Purgatory over Lucifer's pit), else the heightmap's;
	 * lifted over anything thin lying on it (Cocytus's snow, a carpet), so the mark isn't hidden under it.
	 */
	static double ground(ClientLevel level, int x, double y, int z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int start = (int) Math.floor(y) + 2;
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		for (int yy = start; yy > start - 12; yy--) {
			if (level.getBlockState(pos.set(x, yy, z)).isSolid() && !level.getBlockState(pos.set(x, yy + 1, z)).isSolid()) {
				top = yy + 1;
				break;
			}
		}
		BlockState on = level.getBlockState(pos.set(x, top, z));
		if (!on.isAir()) {
			VoxelShape shape = on.getShape(level, pos);
			if (!shape.isEmpty() && shape.max(Direction.Axis.Y) <= 0.5) {
				return top + shape.max(Direction.Axis.Y);
			}
		}
		return top;
	}

	private static void quad(VertexConsumer buffer, PoseStack.Pose pose, float[] q) {
		int r = (int) q[3];
		int g = (int) q[4];
		int b = (int) q[5];
		int a = (int) q[6];
		buffer.addVertex(pose, q[0], q[1], q[2]).setColor(r, g, b, a);
		buffer.addVertex(pose, q[0], q[1], q[2] + 1).setColor(r, g, b, a);
		buffer.addVertex(pose, q[0] + 1, q[1], q[2] + 1).setColor(r, g, b, a);
		buffer.addVertex(pose, q[0] + 1, q[1], q[2]).setColor(r, g, b, a);
	}
}
