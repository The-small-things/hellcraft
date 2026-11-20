package net.thesmallthings.hellcraft.client.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.thesmallthings.hellcraft.HellcraftMod;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Heads for the minimap, as Xaero's shows them: each creature's own head seen from the front, drawn from its model and its
 * texture. The model's head part (by name) is looked at once at rest: every cube face turned toward the viewer, placed where it
 * sits, nearer faces over farther ones (so a player's hat layer covers the face and a pig's snout sits on it). Each model's
 * faces are worked out once; which texture a creature wears (its variant, a player's skin) is asked of its renderer, again
 * every two seconds. A creature whose model has no head gets no head (the minimap keeps its dot).
 */
final class MobHeads {
	private MobHeads() {
	}

	/** One face of the head: where it sits (model units, the icon's top left at 0, 0) and its texture's corners (0 to 1). */
	private record Face(float x0, float y0, float x1, float y1, float u0, float u1, float v0, float v1, float z) {
	}

	/** A head's faces, nearest last, and its size in model units. */
	private record Head(List<Face> faces, float w, float h) {
	}

	private record Seen(Identifier texture, Head head, int at) {
	}

	/** Head parts by name, most likely first (Cerberus shows his middle head, the lovers the first soul's). */
	private static final String[] NAMES = {"head", "Head", "head_mid", "soul_a_head", "skull"};
	private static final Map<Object, Head> HEADS = new IdentityHashMap<>();
	private static final Map<Integer, Seen> SEEN = new HashMap<>();
	private static boolean broken;

	static void clear() {
		HEADS.clear();
		SEEN.clear();
	}

	/** Forgets creatures not drawn for a while. */
	static void tidy(int now) {
		for (Iterator<Seen> it = SEEN.values().iterator(); it.hasNext(); ) {
			if (now - it.next().at > 200) {
				it.remove();
			}
		}
	}

	/**
	 * Draws {@code e}'s head centred on (cx, cy), at most {@code size} GUI pixels across, on a dark backing; false if it has
	 * none (the caller draws a dot instead).
	 */
	static boolean draw(GuiGraphicsExtractor g, LivingEntity e, int cx, int cy, int size, float partial, int now, int backing) {
		Seen seen = SEEN.get(e.getId());
		if (seen == null || now - seen.at > 40) {
			Seen fresh = look(e, partial, now);
			if (fresh == null && seen != null && now - seen.at <= 200) {
				fresh = seen;
			}
			if (fresh == null) {
				return false;
			}
			seen = fresh;
			SEEN.put(e.getId(), seen);
		}
		Head head = seen.head;
		float scale = size / Math.max(head.w, head.h);
		int w = Math.max(1, Math.round(head.w * scale));
		int h = Math.max(1, Math.round(head.h * scale));
		int left = cx - w / 2;
		int top = cy - h / 2;
		g.fill(left - 1, top - 1, left + w + 1, top + h + 1, backing);
		for (Face f : head.faces) {
			int x0 = left + Math.round(f.x0 * scale);
			int y0 = top + Math.round(f.y0 * scale);
			int x1 = left + Math.round(f.x1 * scale);
			int y1 = top + Math.round(f.y1 * scale);
			if (x1 > x0 && y1 > y0) {
				g.blit(seen.texture, x0, y0, x1, y1, f.u0, f.u1, f.v0, f.v1);
			}
		}
		return true;
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	@Nullable
	private static Seen look(LivingEntity e, float partial, int now) {
		if (broken) {
			return null;
		}
		try {
			EntityRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(e);
			if (!(renderer instanceof LivingEntityRenderer living)) {
				return null;
			}
			Model model = living.getModel();
			Head head;
			if (HEADS.containsKey(model)) {
				head = HEADS.get(model);
			} else {
				head = build(model);
				HEADS.put(model, head);
			}
			if (head == null) {
				return null;
			}
			EntityRenderState state = renderer.createRenderState(e, partial);
			Identifier texture = living.getTextureLocation((LivingEntityRenderState) state);
			return texture == null ? null : new Seen(texture, head, now);
		} catch (RuntimeException | LinkageError ex) {
			HellcraftMod.LOGGER.warn("Minimap: heads are off ({})", ex.toString());
			broken = true;
			return null;
		}
	}

	/** The head of a model seen from the front, at rest; null if it has no head part. */
	@Nullable
	private static Head build(Model<?> model) {
		ModelPart root = model.root();
		ModelPart part = null;
		Function<String, ModelPart> lookup = root.createPartLookup();
		for (String name : NAMES) {
			try {
				part = lookup.apply(name);
			} catch (RuntimeException ignored) {
				part = null;
			}
			if (part != null) {
				break;
			}
		}
		if (part == null) {
			return null;
		}
		// look at it at rest, then put every part back exactly as the renderer left it
		List<ModelPart> all = root.getAllParts();
		List<PartPose> poses = new ArrayList<>(all.size());
		for (ModelPart p : all) {
			poses.add(p.storePose());
		}
		List<Face> faces = new ArrayList<>();
		try {
			for (ModelPart p : all) {
				p.resetPose();
			}
			part.visit(new PoseStack(), (pose, path, index, cube) -> {
				for (ModelPart.Polygon polygon : cube.polygons) {
					if (polygon.normal().z() > -0.7f) {
						continue;
					}
					ModelPart.Vertex[] vs = polygon.vertices();
					float[] px = new float[vs.length];
					float[] py = new float[vs.length];
					float z = 0;
					for (int i = 0; i < vs.length; i++) {
						Vector3f at = pose.pose().transformPosition(new Vector3f(vs[i].worldX(), vs[i].worldY(), vs[i].worldZ()));
						px[i] = at.x;
						py[i] = at.y;
						z += at.z / vs.length;
					}
					int lo = 0;
					int hi = 0;
					for (int i = 1; i < vs.length; i++) {
						if (px[i] + py[i] < px[lo] + py[lo]) {
							lo = i;
						}
						if (px[i] + py[i] > px[hi] + py[hi]) {
							hi = i;
						}
					}
					float x0 = Math.min(px[lo], px[hi]);
					float x1 = Math.max(px[lo], px[hi]);
					float y0 = Math.min(py[lo], py[hi]);
					float y1 = Math.max(py[lo], py[hi]);
					if (x1 - x0 < 1e-4f || y1 - y0 < 1e-4f) {
						continue;
					}
					faces.add(new Face(x0, y0, x1, y1, vs[lo].u(), vs[hi].u(), vs[lo].v(), vs[hi].v(), z));
				}
			});
		} finally {
			for (int i = 0; i < all.size(); i++) {
				all.get(i).loadPose(poses.get(i));
			}
		}
		if (faces.isEmpty()) {
			return null;
		}
		float minX = Float.MAX_VALUE;
		float minY = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE;
		float maxY = -Float.MAX_VALUE;
		for (Face f : faces) {
			minX = Math.min(minX, f.x0);
			minY = Math.min(minY, f.y0);
			maxX = Math.max(maxX, f.x1);
			maxY = Math.max(maxY, f.y1);
		}
		List<Face> placed = new ArrayList<>(faces.size());
		for (Face f : faces) {
			placed.add(new Face(f.x0 - minX, f.y0 - minY, f.x1 - minX, f.y1 - minY, f.u0, f.u1, f.v0, f.v1, f.z));
		}
		// farthest first, so nearer faces (a hat, a snout, eyes) land on top
		placed.sort(Comparator.comparingDouble(Face::z).reversed());
		return new Head(List.copyOf(placed), maxX - minX, maxY - minY);
	}
}
