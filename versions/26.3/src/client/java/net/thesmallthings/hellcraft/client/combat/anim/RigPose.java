package net.thesmallthings.hellcraft.client.combat.anim;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A pose of the combat rig: for each bone a turn, a shift and how strongly the pose claims that bone (0: leave it to
 * whatever is underneath, in the end the vanilla pose; 1: fully this). Poses are laid over one another and cross-faded
 * with these weights, so a move can come in over a walk, hand over to the next move mid-swing and fade back out
 * without a jump anywhere.
 */
public final class RigPose {
	public final Quaternionf[] rotation = new Quaternionf[Bone.COUNT];
	public final Vector3f[] position = new Vector3f[Bone.COUNT];
	public final float[] weight = new float[Bone.COUNT];
	/** The way a roll goes, as a yaw in the world (degrees); NaN when the pose has no direction of its own. */
	public float heading = Float.NaN;
	/** How much the pose keeps its own legs while the player walks (0: the walk shows through; 1: the pose's legs, as in a roll). */
	public float legs;

	public RigPose() {
		for (int i = 0; i < Bone.COUNT; i++) {
			rotation[i] = new Quaternionf();
			position[i] = new Vector3f();
		}
	}

	public RigPose clear() {
		for (int i = 0; i < Bone.COUNT; i++) {
			rotation[i].identity();
			position[i].zero();
			weight[i] = 0;
		}
		heading = Float.NaN;
		legs = 0;
		return this;
	}

	public RigPose set(RigPose other) {
		for (int i = 0; i < Bone.COUNT; i++) {
			rotation[i].set(other.rotation[i]);
			position[i].set(other.position[i]);
			weight[i] = other.weight[i];
		}
		heading = other.heading;
		legs = other.legs;
		return this;
	}

	public boolean empty() {
		for (float w : weight) {
			if (w > 1.0e-4f) {
				return false;
			}
		}
		return true;
	}

	public float weight(Bone bone) {
		return weight[bone.ordinal()];
	}

	/** Lays `over` on this pose at strength k (0 to 1): where it claims a bone, this pose turns towards it. */
	public RigPose layer(RigPose over, float k) {
		for (int i = 0; i < Bone.COUNT; i++) {
			float o = over.weight[i] * k;
			if (o <= 1.0e-5f) {
				continue;
			}
			float u = weight[i];
			if (u <= 1.0e-5f) {
				rotation[i].set(over.rotation[i]);
				position[i].set(over.position[i]);
				weight[i] = o;
			} else {
				rotation[i].slerp(over.rotation[i], o);
				position[i].lerp(over.position[i], o);
				weight[i] = u + (1 - u) * o;
			}
		}
		if (!Float.isNaN(over.heading) && k > 0) {
			heading = over.heading;
		}
		legs = legs + (over.legs - legs) * k;
		return this;
	}

	/** Fades this pose towards `to` by k (0: this, 1: `to`); a bone only one of them claims keeps its shape and fades in weight. */
	public RigPose crossfade(RigPose to, float k) {
		for (int i = 0; i < Bone.COUNT; i++) {
			float a = weight[i];
			float b = to.weight[i];
			if (a <= 1.0e-5f && b <= 1.0e-5f) {
				weight[i] = 0;
				continue;
			}
			if (a <= 1.0e-5f) {
				rotation[i].set(to.rotation[i]);
				position[i].set(to.position[i]);
			} else if (b > 1.0e-5f) {
				rotation[i].slerp(to.rotation[i], k);
				position[i].lerp(to.position[i], k);
			}
			weight[i] = a + (b - a) * k;
		}
		if (Float.isNaN(heading) || k >= 0.5f && !Float.isNaN(to.heading)) {
			heading = to.heading;
		}
		legs = legs + (to.legs - legs) * k;
		return this;
	}

	/** Scales how strongly the pose claims every bone. */
	public RigPose scale(float k) {
		for (int i = 0; i < Bone.COUNT; i++) {
			weight[i] *= k;
		}
		return this;
	}

	/** The same pose for a left-handed player: sides swapped, turns and shifts reflected. */
	public RigPose mirror() {
		for (Bone bone : Bone.ALL) {
			Bone other = bone.mirrored();
			if (other.ordinal() > bone.ordinal()) {
				int a = bone.ordinal();
				int b = other.ordinal();
				Quaternionf q = new Quaternionf(rotation[a]);
				rotation[a].set(rotation[b]);
				rotation[b].set(q);
				Vector3f p = new Vector3f(position[a]);
				position[a].set(position[b]);
				position[b].set(p);
				float w = weight[a];
				weight[a] = weight[b];
				weight[b] = w;
			}
		}
		for (int i = 0; i < Bone.COUNT; i++) {
			Quaternionf q = rotation[i];
			q.set(q.x, -q.y, -q.z, q.w);
			position[i].x = -position[i].x;
		}
		return this;
	}
}
