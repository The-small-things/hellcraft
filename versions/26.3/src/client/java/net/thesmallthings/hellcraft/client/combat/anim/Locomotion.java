package net.thesmallthings.hellcraft.client.combat.anim;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.thesmallthings.hellcraft.client.combat.HitFeel;
import net.thesmallthings.hellcraft.entity.boss.gen.PlayerMovesAnim;
import org.joml.Quaternionf;

/**
 * How one player gets about, drawn on the rig under everything else (tools/art/player_moves.py: walk, run, sneak, idle,
 * jump, fall, land). Every player seen gets it, combat or not.
 *
 * <p>The stride is read by vanilla's own walk position, so the feet keep pace with the ground and the stride stops when
 * the player does; the walk and the run are sampled at the same point of the stride and cross-faded by how much the player
 * sprints, and walking backwards plays the stride the other way. In the air a jump's tuck comes in, then the flailing of a
 * long fall; touching down the knees give, deeper the harder the landing. Running round a bend leans into it. None of it
 * ever switches: every part comes and goes by a weight eased a little each tick.
 *
 * <p>What vanilla does better is left to it: swimming, gliding, riding, climbing, sleeping and flying turn the layer off,
 * and an arm busy with something (a bow, a shield, a swing) is left alone.
 */
public final class Locomotion {
	/** Vanilla's stride, in walk-position units (its legs swing by cos(position * 0.6662)). */
	private static final float CYCLE = (float) (Math.PI * 2 / 0.6662);
	/** Vanilla holds an item 18 degrees forward of a hanging arm and swings that arm half as far. */
	private static final Quaternionf ITEM_ARM = new Quaternionf().rotationX((float) (-Math.PI / 10));

	private float active;
	private float lastActive;
	private float sprint;
	private float lastSprint;
	private float crouch;
	private float lastCrouch;
	private float back;
	private float lastBack;
	private float lean;
	private float lastLean;
	private float air;
	private float lastAir;
	private float fall;
	private float lastFall;

	private boolean wasOnGround = true;
	private boolean jumped;
	private int airTicks;
	private float jumpTime = -1;
	private float landTime = 100;
	private float landStrength;
	private float lastVy;
	private float bodyYaw = Float.NaN;

	private final RigPose a = new RigPose();
	private final RigPose b = new RigPose();
	private final RigPose c = new RigPose();

	/** Once a client tick. `self`: this client's own player (whose landings also dip the view). */
	void tick(Player player, boolean self) {
		lastActive = active;
		lastSprint = sprint;
		lastCrouch = crouch;
		lastBack = back;
		lastLean = lean;
		lastAir = air;
		lastFall = fall;

		boolean on = !(player.isSwimming() || player.isVisuallySwimming() || player.isFallFlying() || player.isPassenger() || player.isSleeping()
				|| player.isSpectator() || player.isAutoSpinAttack() || player.getAbilities().flying || player.onClimbable() && !player.onGround());
		active = approach(active, on ? 1 : 0, 0.2f);

		double dx = player.getX() - player.xo;
		double dz = player.getZ() - player.zo;
		float speed = (float) Math.sqrt(dx * dx + dz * dz);
		float vy = (float) (player.getY() - player.yo);
		boolean ground = player.onGround();

		sprint = approach(sprint, player.isSprinting() && speed > 0.1f ? 1 : 0, 0.15f);
		crouch = approach(crouch, player.isCrouching() ? 1 : 0, 0.3f);

		// backwards: moving against the way the body faces (vanilla turns the body to the way you go unless it's behind you)
		float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
		float along = speed < 1.0e-4f ? 0 : (float) ((dx * -Mth.sin(yaw) + dz * Mth.cos(yaw)) / speed);
		back = approach(back, speed > 0.03f && along < -0.3f ? 1 : 0, 0.2f);

		// leaning into a bend, by how fast the body turns and how fast the player goes
		float turn = Float.isNaN(bodyYaw) ? 0 : Mth.wrapDegrees(player.yBodyRot - bodyYaw);
		bodyYaw = player.yBodyRot;
		float pace = Mth.clamp(speed / 0.25f, 0, 1);
		lean += (Mth.clamp(-turn * 0.7f * pace, -11, 11) - lean) * 0.25f;

		// in the air and back down
		if (ground) {
			if (!wasOnGround && airTicks > 2) {
				landTime = 0;
				landStrength = Mth.clamp((-lastVy - 0.15f) / 0.7f, 0.2f, 1.0f) * (airTicks < 6 ? 0.5f : 1.0f);
				if (self && airTicks > 6 && Settings.smoothAnimations()) {
					HitFeel.punch(2.4f * landStrength, 0, 0);
				}
			}
			airTicks = 0;
			jumped = false;
			jumpTime = -1;
		} else {
			if (wasOnGround && vy > 0.05f) {
				jumped = true;
				jumpTime = 0;
			}
			airTicks++;
		}
		if (jumpTime >= 0) {
			jumpTime++;
		}
		landTime = Math.min(100, landTime + 1);
		air = approach(air, !ground && (jumped || airTicks > 4) ? 1 : 0, ground ? 0.5f : 0.3f);
		fall = approach(fall, !ground && vy < -0.45f && airTicks > 8 ? 1 : 0, 0.12f);
		wasOnGround = ground;
		lastVy = vy;
	}

	/** The crouch eased in and out (0 to 1), for where the body is drawn (mixin/AvatarRendererMixin). */
	public float crouch(float partial) {
		return Mth.lerp(partial, lastCrouch, crouch);
	}

	/** For the client tests: how far into the sprint, the air, a fall and a landing the player is now. */
	public float sprinting() {
		return sprint;
	}

	public float airborne() {
		return air;
	}

	public float falling() {
		return fall;
	}

	public float sinceLanding() {
		return landTime;
	}

	/** The pose at this frame, into `out` (cleared first). False when the layer is off. */
	boolean sample(float partial, HumanoidRenderState state, RigPose out) {
		out.clear();
		float act = smooth(Mth.lerp(partial, lastActive, active));
		if (act <= 0.001f) {
			return false;
		}
		float age = state.ageInTicks;
		Clips.of(PlayerMovesAnim.IDLE).sample(age, out);

		// the stride, by vanilla's walk position: the walk and the run at the same point of it; backwards plays it in reverse
		float moving = smooth(Mth.clamp(state.walkAnimationSpeed * 1.25f, 0, 1));
		if (moving > 0.001f) {
			float phase = state.walkAnimationPos / CYCLE;
			phase -= (float) Math.floor(phase);
			float bk = smooth(Mth.lerp(partial, lastBack, back));
			if (state.isCrouching) {
				stride(PlayerMovesAnim.SNEAK, phase, bk, a);
			} else {
				stride(PlayerMovesAnim.WALK, phase, bk, a);
				float sp = smooth(Mth.lerp(partial, lastSprint, sprint));
				if (sp > 0.001f) {
					stride(PlayerMovesAnim.RUN, phase, bk, b);
					a.crossfade(b, sp);
				}
			}
			out.layer(a, moving);
		}

		// up and down
		float up = smooth(Mth.lerp(partial, lastAir, air));
		if (up > 0.001f) {
			Clips.Clip jump = Clips.of(PlayerMovesAnim.JUMP);
			jump.sample(jumped && jumpTime >= 0 ? jumpTime - 1 + partial : jump.ticks, a.clear());
			out.layer(a, up);
			float down = smooth(Mth.lerp(partial, lastFall, fall)) * up;
			if (down > 0.001f) {
				Clips.of(PlayerMovesAnim.FALL).sample(age, a.clear());
				out.layer(a, down);
			}
		}
		float land = landing(landTime + partial) * landStrength * (1 - 0.5f * moving);
		if (land > 0.001f) {
			Clips.of(PlayerMovesAnim.LAND).sample(0, a.clear());
			out.layer(a, land);
		}

		// leaning into a bend (the body's roll)
		float leaning = Mth.lerp(partial, lastLean, lean);
		if (Math.abs(leaning) > 0.05f) {
			int body = Bone.BODY.ordinal();
			out.rotation[body].mul(new Quaternionf().rotationZ(leaning * Mth.DEG_TO_RAD));
			out.weight[body] = Math.max(out.weight[body], 1);
		}

		// arms: only those vanilla isn't using for something; one holding an item swings half as far, 18 degrees forward
		HumanoidArm usingArm = state.isUsingItem ? state.useItemHand.asArm(state.mainArm) : null;
		arm(out, Bone.RIGHT_ARM, state.getArmPose(state.mainArm), usingArm == state.mainArm, state);
		arm(out, Bone.LEFT_ARM, state.getArmPose(state.mainArm.getOpposite()), usingArm == state.mainArm.getOpposite(), state);
		out.scale(act);
		return true;
	}

	private final HumanoidRenderState firstPerson = new HumanoidRenderState();

	/**
	 * The first-person share (the "fp" bone only) for this client's own player, read from the player itself (there is no
	 * render state in first person): the hand's sway with the stride, the sprint's pumping, a jump's lift, a fall, a landing.
	 */
	boolean sampleFirstPerson(float partial, Player player, RigPose out) {
		HumanoidRenderState s = firstPerson;
		s.ageInTicks = player.tickCount + partial;
		s.walkAnimationPos = player.walkAnimation.position(partial);
		s.walkAnimationSpeed = player.walkAnimation.speed(partial);
		s.isCrouching = player.isCrouching();
		s.mainArm = player.getMainArm();
		s.rightArmPose = HumanoidModel.ArmPose.EMPTY;
		s.leftArmPose = HumanoidModel.ArmPose.EMPTY;
		s.isUsingItem = false;
		s.currentSwing = null;
		if (!sample(partial, s, out)) {
			return false;
		}
		for (Bone bone : Bone.ALL) {
			if (bone != Bone.FP) {
				out.weight[bone.ordinal()] = 0;
			}
		}
		return out.weight(Bone.FP) > 0.001f;
	}

	private void stride(PlayerMovesAnim move, float phase, float backwards, RigPose into) {
		Clips.Clip clip = Clips.of(move);
		clip.sample(phase * clip.ticks, into.clear());
		if (backwards > 0.001f) {
			clip.sample((1 - phase) * clip.ticks, c.clear());
			into.crossfade(c, backwards);
		}
	}

	private static void arm(RigPose out, Bone bone, HumanoidModel.ArmPose pose, boolean using, HumanoidRenderState state) {
		int i = bone.ordinal();
		if (out.weight[i] <= 0) {
			return;
		}
		if (pose != HumanoidModel.ArmPose.EMPTY && pose != HumanoidModel.ArmPose.ITEM || using) {
			out.weight[i] = 0;
			return;
		}
		if (pose == HumanoidModel.ArmPose.ITEM) {
			Quaternionf half = new Quaternionf().slerp(out.rotation[i], 0.6f);
			out.rotation[i].set(ITEM_ARM).mul(half);
		}
		// a vanilla swing (a punch, mining) takes both arms back for its length, quickly in and out
		if (state.currentSwing != null) {
			float s = Mth.clamp(state.swingAnimation, 0, 1);
			out.weight[i] *= 1 - Math.min(1, s / 0.12f) * Math.min(1, (1 - s) / 0.3f);
		}
	}

	/** A landing's weight over time: in at once, held a moment, eased out. */
	private static float landing(float t) {
		if (t < 1.5f) {
			return smooth(t / 1.5f);
		}
		if (t < 2.5f) {
			return 1;
		}
		return 1 - smooth((t - 2.5f) / 6.5f);
	}

	private static float approach(float value, float target, float step) {
		return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
	}

	static float smooth(float x) {
		x = Mth.clamp(x, 0, 1);
		return x * x * (3 - 2 * x);
	}
}
