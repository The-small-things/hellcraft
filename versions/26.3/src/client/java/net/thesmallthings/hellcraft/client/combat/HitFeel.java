package net.thesmallthings.hellcraft.client.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.thesmallthings.hellcraft.client.combat.anim.Bone;
import net.thesmallthings.hellcraft.client.combat.anim.PlayerAnimator;
import net.thesmallthings.hellcraft.client.combat.anim.RigPose;
import net.thesmallthings.hellcraft.net.FeelPayload;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * What a fight feels like through the screen (combat/Combat.feel on the server): when your blow lands the swing holds
 * for a few frames and the view kicks; a parry rings and jolts; a broken guard throws the view back. Also the view's
 * share of a move: the small turns a swing or a roll gives the camera (the rig's "camera" bone). Off with View Bobbing,
 * like Hellcraft's other camera motion.
 */
public final class HitFeel {
	private HitFeel() {
	}

	/** The kick: pitch, yaw and roll (degrees), springing back to nothing. */
	private static final float[] kick = new float[3];
	private static final float[] lastKick = new float[3];
	private static final float[] velocity = new float[3];
	private static final RigPose scratch = new RigPose();

	/** Ticks left in which the server's word of a landed blow is already felt (the swing felt it as it landed). */
	private static int predicted;

	/**
	 * This player's own swing reaches something as its animation lands the blow (client/combat/SwingControl): the held frame
	 * and the kick come now, not a round trip later when the server confirms it; the server's HIT that follows is passed over.
	 */
	public static void predict(boolean heavy) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) {
			return;
		}
		predicted = 10;
		PlayerAnimator animator = PlayerAnimator.get(player.getId());
		feel(player, animator, heavy ? FeelPayload.Kind.HEAVY_HIT : FeelPayload.Kind.HIT, heavy ? 1.3f : 1.0f);
	}

	public static void receive(FeelPayload payload) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) {
			return;
		}
		if (predicted > 0 && (payload.what() == FeelPayload.Kind.HIT || payload.what() == FeelPayload.Kind.HEAVY_HIT)) {
			return;
		}
		feel(player, PlayerAnimator.get(player.getId()), payload.what(), payload.strength());
	}

	private static void feel(LocalPlayer player, @Nullable PlayerAnimator animator, FeelPayload.Kind what, float s) {
		switch (what) {
			case HIT -> {
				if (animator != null) {
					animator.freeze(1);
				}
				punch(0.5f * s, sign(player) * 0.3f * s, 0);
			}
			case HEAVY_HIT -> {
				if (animator != null) {
					animator.freeze(2);
				}
				punch(1.4f * s, sign(player) * 0.6f * s, sign(player) * 0.8f * s);
			}
			case PARRY -> punch(-1.6f * s, sign(player) * 1.2f * s, sign(player) * -1.5f * s);
			case GUARDED -> punch(-0.9f * s, 0, sign(player) * 0.5f * s);
			case GUARD_BREAK -> punch(-4.0f * s, sign(player) * -2.0f * s, sign(player) * 3.0f * s);
			case DODGED -> punch(0.3f, 0, 0);
		}
	}

	/** A random side, so repeated kicks don't all lean one way. */
	private static float sign(LocalPlayer player) {
		return player.getRandom().nextBoolean() ? 1 : -1;
	}

	/** Throws the view (degrees a tick): pitch down is positive, yaw right, roll clockwise. */
	public static void punch(float pitch, float yaw, float roll) {
		velocity[0] += pitch;
		velocity[1] += yaw;
		velocity[2] += roll;
	}

	public static void tick(Minecraft client) {
		if (client.isPaused()) {
			return;
		}
		if (predicted > 0) {
			predicted--;
		}
		for (int i = 0; i < 3; i++) {
			lastKick[i] = kick[i];
			// a damped spring back to rest
			velocity[i] += -kick[i] * 0.55f;
			velocity[i] *= 0.55f;
			kick[i] += velocity[i];
			if (Math.abs(kick[i]) < 0.005f && Math.abs(velocity[i]) < 0.005f) {
				kick[i] = 0;
				velocity[i] = 0;
			}
		}
	}

	public static void reset() {
		predicted = 0;
		for (int i = 0; i < 3; i++) {
			kick[i] = lastKick[i] = velocity[i] = 0;
		}
	}

	/** What to add to the camera this frame: {pitch, yaw, roll} in degrees. */
	public static float[] camera(float partial) {
		float[] out = new float[3];
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !mc.options.bobView().get()) {
			return out;
		}
		for (int i = 0; i < 3; i++) {
			out[i] = Mth.lerp(partial, lastKick[i], kick[i]);
		}
		// the move's own share (only in first person: seen from outside, the body shows it)
		if (mc.options.getCameraType().isFirstPerson()) {
			PlayerAnimator animator = PlayerAnimator.get(mc.player.getId());
			if (animator != null && animator.sample(partial, scratch)) {
				float w = scratch.weight(Bone.CAMERA);
				if (w > 0) {
					Vector3f angles = scratch.rotation[Bone.CAMERA.ordinal()].getEulerAnglesZYX(new Vector3f());
					float side = mc.player.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT ? -1 : 1;
					out[0] += angles.x * Mth.RAD_TO_DEG * w;
					out[1] += angles.y * Mth.RAD_TO_DEG * w * side;
					out[2] += angles.z * Mth.RAD_TO_DEG * w * side;
				}
			}
		}
		return out;
	}
}
