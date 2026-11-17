package net.thesmallthings.hellcraft.client.combat.anim;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Puts a {@link RigPose} on the vanilla player model, over the pose vanilla gave it (mixin/HumanoidModelMixin, at the end
 * of setupAnim, so armour, held items and everything else that poses with the model follow).
 *
 * <p>The vanilla model is flat: head, body, arms and legs all hang off the root, so leaning the body leaves the head and
 * arms behind. The rig is a skeleton instead (root at the hips; the body turns about the hips and carries the head and
 * arms; the legs hang off the root), worked out here each frame: the root's and body's turns become matrices, each part's
 * own pose is put through its parents', and the result is written back as the flat position and Euler turn a ModelPart
 * wants. A bone the pose doesn't claim keeps vanilla's own turn inside that skeleton, so a walk's arm swing or a look
 * round carries on, on top of a leaning, twisting body. The head mostly keeps looking where the player looks.
 */
public final class PlayerRig {
	private PlayerRig() {
	}

	/** The pose a player is drawn with this frame, carried on their render state (mixin/AvatarRendererMixin sets it). */
	public static final RenderStateDataKey<RigPose> POSE = RenderStateDataKey.create(() -> "hellcraft:combat_pose");
	/** The player's animator, when their sudden pose changes are to be eased (PoseSmoother), and their crouch eased (0 to 1). */
	public static final RenderStateDataKey<PlayerAnimator> SMOOTH = RenderStateDataKey.create(() -> "hellcraft:pose_smoother");
	public static final RenderStateDataKey<Float> CROUCH = RenderStateDataKey.create(() -> "hellcraft:crouch");

	/** The hips, in the model's own space (y runs down from the shoulders at 0 to the feet at 24). */
	private static final Vector3f HIPS = new Vector3f(0, 12, 0);
	/** How much of the body's twist the head ignores, to keep looking where the player looks. */
	private static final float HEAD_STEADY = 0.75f;

	public static void apply(HumanoidModel<?> model, HumanoidRenderState state, RigPose pose) {
		if (state.mainArm == HumanoidArm.LEFT) {
			pose = new RigPose().set(pose).mirror();
		}
		float rootW = pose.weight(Bone.ROOT);
		float bodyW = pose.weight(Bone.BODY);

		// the root: a turn about the hips and a shift, in the direction the move goes
		Quaternionf rootRot = new Quaternionf();
		Vector3f rootShift = new Vector3f();
		if (rootW > 0) {
			rootRot.slerp(pose.rotation[Bone.ROOT.ordinal()], rootW);
			rootShift.set(pose.position[Bone.ROOT.ordinal()]).mul(rootW);
			if (!Float.isNaN(pose.heading)) {
				// a roll made facing forward, turned to the way the player really goes: the body's yaw is state.bodyRot
				float rel = Mth.wrapDegrees(pose.heading - state.bodyRot) * Mth.DEG_TO_RAD;
				Quaternionf turn = new Quaternionf().rotationY(rel);
				rootRot = new Quaternionf(turn).mul(rootRot).mul(turn.conjugate(new Quaternionf()));
				turn.transform(rootShift);
			}
		}
		Matrix4f root = new Matrix4f().translate(rootShift).translate(HIPS).rotate(rootRot).translate(-HIPS.x, -HIPS.y, -HIPS.z);

		Quaternionf bodyRot = new Quaternionf();
		Vector3f bodyShift = new Vector3f();
		if (bodyW > 0) {
			bodyRot.slerp(pose.rotation[Bone.BODY.ordinal()], bodyW);
			bodyShift.set(pose.position[Bone.BODY.ordinal()]).mul(bodyW);
		}
		Matrix4f torso = new Matrix4f(root).translate(bodyShift).translate(HIPS).rotate(bodyRot).translate(-HIPS.x, -HIPS.y, -HIPS.z);
		// the head rides the body but turns with only part of its twist
		Quaternionf steadied = new Quaternionf().slerp(bodyRot, 1 - HEAD_STEADY);
		Matrix4f neck = new Matrix4f(root).translate(HIPS).rotate(steadied).translate(-HIPS.x, -HIPS.y, -HIPS.z);

		// legs: while the player walks the walk shows through, unless the pose insists (a roll)
		float walk = Mth.clamp(state.walkAnimationSpeed, 0, 1);
		float legHold = 1 - (1 - pose.legs) * walk * 0.85f;

		place(model.body, torso, torso, null, null, 0, false);
		place(model.head, torso, neck, pose, Bone.HEAD, 1, true);
		place(model.rightArm, torso, torso, pose, Bone.RIGHT_ARM, 1, false);
		place(model.leftArm, torso, torso, pose, Bone.LEFT_ARM, 1, false);
		place(model.rightLeg, root, root, pose, Bone.RIGHT_LEG, legHold, false);
		place(model.leftLeg, root, root, pose, Bone.LEFT_LEG, legHold, false);
	}

	/**
	 * One part: its vanilla place goes through `carry` (where its parent has taken it) and its turn through `turn`'s
	 * rotation; its own turn is vanilla's, moved towards the pose's by the pose's weight (or, additive, vanilla's with
	 * the pose's on top: the head, which must keep its look).
	 */
	private static void place(ModelPart part, Matrix4f carry, Matrix4f turn, RigPose pose, Bone bone, float hold, boolean additive) {
		Quaternionf local = new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot);
		Vector3f at = new Vector3f(part.x, part.y, part.z);
		if (pose != null) {
			float w = pose.weight(bone) * hold;
			if (w > 0) {
				Quaternionf target = pose.rotation[bone.ordinal()];
				if (additive || pose.additive[bone.ordinal()]) {
					local.mul(new Quaternionf().slerp(target, w));
				} else {
					local.slerp(target, w);
				}
				Vector3f shift = pose.position[bone.ordinal()];
				at.add(shift.x * w, shift.y * w, shift.z * w);
			}
		}
		carry.transformPosition(at);
		Quaternionf world = turn.getNormalizedRotation(new Quaternionf()).mul(local);
		Vector3f angles = world.getEulerAnglesZYX(new Vector3f());
		part.x = at.x;
		part.y = at.y;
		part.z = at.z;
		part.xRot = angles.x;
		part.yRot = angles.y;
		part.zRot = angles.z;
	}
}
