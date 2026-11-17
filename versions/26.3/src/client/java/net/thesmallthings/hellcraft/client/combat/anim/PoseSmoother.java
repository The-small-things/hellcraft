package net.thesmallthings.hellcraft.client.combat.anim;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Eases vanilla's sudden pose changes for one player. Vanilla switches a pose in one frame: crouching folds the body at
 * once, a bow or shield jumps up to aim, a crossbow snaps from hanging to loaded, getting into a boat drops the legs.
 * Whenever one of those switches is seen (the {@link #key} of the frame changes), the parts start from where they were
 * drawn the frame before and ease into the new pose over {@link #LENGTH} ticks, by whatever path the pose itself takes
 * meanwhile (a turn of each part along the shortest arc, a shift in a straight line). A second switch mid-way starts
 * again from where the parts are, so nothing ever jumps.
 *
 * <p>Runs last on the model (mixin/HumanoidModelMixin, after PlayerRig), on the player model and on every armour model
 * posed with the same state, so they move together; only the first of those each frame records where the parts ended.
 */
public final class PoseSmoother {
	/** Ticks a switch takes to ease in. */
	private static final float LENGTH = 4.5f;
	private static final int PARTS = 6;

	private final float[] last = new float[PARTS * 6];
	private final float[] from = new float[PARTS * 6];
	private boolean haveLast;
	private float lastTime = -1;
	private float start = -1000;
	private long key = Long.MIN_VALUE;

	public void apply(HumanoidModel<?> model, HumanoidRenderState state) {
		ModelPart[] parts = {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
		float time = state.ageInTicks;
		boolean fresh = time > lastTime + 1.0e-4f || time < lastTime - 40;
		long now = key(state);
		if (fresh && now != key) {
			if (haveLast && key != Long.MIN_VALUE && Math.abs(time - lastTime) < 3) {
				System.arraycopy(last, 0, from, 0, last.length);
				start = time;
			}
			key = now;
		}
		float t = (time - start) / LENGTH;
		if (t >= 0 && t < 1) {
			float w = t * t * (3 - 2 * t);
			Quaternionf a = new Quaternionf();
			Quaternionf b = new Quaternionf();
			Vector3f angles = new Vector3f();
			for (int i = 0; i < PARTS; i++) {
				ModelPart part = parts[i];
				int o = i * 6;
				a.rotationZYX(from[o + 5], from[o + 4], from[o + 3]);
				b.rotationZYX(part.zRot, part.yRot, part.xRot);
				a.slerp(b, w).getEulerAnglesZYX(angles);
				part.x = Mth.lerp(w, from[o], part.x);
				part.y = Mth.lerp(w, from[o + 1], part.y);
				part.z = Mth.lerp(w, from[o + 2], part.z);
				part.xRot = angles.x;
				part.yRot = angles.y;
				part.zRot = angles.z;
			}
		}
		if (fresh) {
			for (int i = 0; i < PARTS; i++) {
				ModelPart part = parts[i];
				int o = i * 6;
				last[o] = part.x;
				last[o + 1] = part.y;
				last[o + 2] = part.z;
				last[o + 3] = part.xRot;
				last[o + 4] = part.yRot;
				last[o + 5] = part.zRot;
			}
			haveLast = true;
			lastTime = time;
		}
	}

	/** What vanilla switches poses by: crouching, each arm's pose, using an item and with which hand, riding, the main arm. */
	private static long key(HumanoidRenderState state) {
		long k = state.isCrouching ? 1 : 0;
		k |= (long) state.rightArmPose.ordinal() << 1;
		k |= (long) state.leftArmPose.ordinal() << 7;
		k |= (state.isUsingItem ? 1L : 0L) << 13;
		k |= (long) state.useItemHand.ordinal() << 14;
		k |= (state.isPassenger ? 1L : 0L) << 15;
		k |= (long) state.mainArm.ordinal() << 16;
		return k;
	}
}
