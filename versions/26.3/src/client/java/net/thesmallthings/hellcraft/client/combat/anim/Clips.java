package net.thesmallthings.hellcraft.client.combat.anim;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.util.Mth;
import net.thesmallthings.hellcraft.client.render.gen.PlayerMovesAnimations;
import net.thesmallthings.hellcraft.entity.boss.gen.PlayerMovesAnim;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The player's combat moves as curves to read at any moment: the generated keyframes (client/render/gen/
 * PlayerMovesAnimations, a key every tick from tools/art/player_moves.py) joined by Catmull-Rom curves, exactly as
 * Minecraft's own animations are, but sampled by hand so they can be blended, slowed, frozen and shown in first person.
 */
public final class Clips {
	private Clips() {
	}

	private record Track(float[] times, Vector3fc[] values) {
		void sample(float seconds, Vector3f out) {
			int n = times.length;
			if (n == 1 || seconds <= times[0]) {
				out.set(values[0]);
				return;
			}
			if (seconds >= times[n - 1]) {
				out.set(values[n - 1]);
				return;
			}
			int lo = 0;
			int hi = n - 1;
			while (hi - lo > 1) {
				int mid = (lo + hi) >>> 1;
				if (times[mid] <= seconds) {
					lo = mid;
				} else {
					hi = mid;
				}
			}
			float span = times[hi] - times[lo];
			float a = span <= 0 ? 0 : (seconds - times[lo]) / span;
			Vector3fc p0 = values[Math.max(0, lo - 1)];
			Vector3fc p1 = values[lo];
			Vector3fc p2 = values[hi];
			Vector3fc p3 = values[Math.min(n - 1, hi + 1)];
			out.set(Mth.catmullrom(a, p0.x(), p1.x(), p2.x(), p3.x()), Mth.catmullrom(a, p0.y(), p1.y(), p2.y(), p3.y()),
					Mth.catmullrom(a, p0.z(), p1.z(), p2.z(), p3.z()));
		}
	}

	/** One move: its length in ticks, whether it loops, and a rotation and a position curve per bone (null: not moved). */
	public static final class Clip {
		public final PlayerMovesAnim move;
		public final float ticks;
		public final boolean looping;
		private final Track[] rotation = new Track[Bone.COUNT];
		private final Track[] position = new Track[Bone.COUNT];
		/** Its arms are added to vanilla's (ADDITIVE). */
		public final boolean additiveArms;

		private Clip(PlayerMovesAnim move, AnimationDefinition def) {
			this.move = move;
			this.ticks = def.lengthInSeconds() * 20.0f;
			this.looping = def.looping();
			this.additiveArms = ADDITIVE.contains(move);
			for (Bone bone : Bone.ALL) {
				List<AnimationChannel> channels = def.boneAnimations().get(bone.id);
				if (channels == null) {
					continue;
				}
				for (AnimationChannel channel : channels) {
					Keyframe[] keys = channel.keyframes();
					float[] times = new float[keys.length];
					Vector3fc[] values = new Vector3fc[keys.length];
					for (int i = 0; i < keys.length; i++) {
						times[i] = keys[i].timestamp();
						values[i] = keys[i].postTarget();
					}
					if (channel.target() == AnimationChannel.Targets.ROTATION) {
						rotation[bone.ordinal()] = new Track(times, values);
					} else if (channel.target() == AnimationChannel.Targets.POSITION) {
						position[bone.ordinal()] = new Track(times, values);
					}
				}
			}
		}

		public boolean moves(Bone bone) {
			return rotation[bone.ordinal()] != null || position[bone.ordinal()] != null;
		}

		/** Writes the pose at `tick` (a looping clip wraps) into `out`, each bone it moves at full weight. */
		public void sample(float tick, RigPose out) {
			float t = looping && ticks > 0 ? tick % ticks : Math.min(tick, ticks);
			float seconds = Math.max(0, t) / 20.0f;
			Vector3f v = new Vector3f();
			for (int i = 0; i < Bone.COUNT; i++) {
				Track r = rotation[i];
				Track p = position[i];
				if (r == null && p == null) {
					out.weight[i] = 0;
					out.additive[i] = false;
					continue;
				}
				out.weight[i] = 1;
				out.additive[i] = additiveArms && (i == Bone.RIGHT_ARM.ordinal() || i == Bone.LEFT_ARM.ordinal());
				if (r != null) {
					r.sample(seconds, v);
					// Minecraft's part rotation: about Z, then Y, then X (the keys are radians)
					out.rotation[i].rotationZYX(v.z, v.y, v.x);
				} else {
					out.rotation[i].identity();
				}
				if (p != null) {
					p.sample(seconds, out.position[i]);
				} else {
					out.position[i].zero();
				}
			}
		}
	}

	/** Moves whose arms lie over vanilla's aiming arms, adding to them (tools/art/player_moves.py: the bow and crossbow). */
	private static final java.util.Set<PlayerMovesAnim> ADDITIVE = java.util.EnumSet.of(PlayerMovesAnim.BOW_DRAW, PlayerMovesAnim.BOW_RELEASE,
			PlayerMovesAnim.CROSSBOW_FIRE);

	private static final Map<PlayerMovesAnim, Clip> CLIPS = new EnumMap<>(PlayerMovesAnim.class);

	public static Clip of(PlayerMovesAnim move) {
		return CLIPS.computeIfAbsent(move, m -> new Clip(m, PlayerMovesAnimations.of(m)));
	}

	/** Used by the pose blends: the quaternion of nothing. */
	static final Quaternionf IDENTITY = new Quaternionf();
}
