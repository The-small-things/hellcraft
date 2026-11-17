package net.thesmallthings.hellcraft.client.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.ShieldItem;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Eases vanilla's first-person snaps. When a hand starts or stops using its item (a bow drawn or loosed, food raised or
 * finished, a shield brought up or lowered, a crossbow loaded, a trident cocked), vanilla moves the item to its new place in
 * one frame. Here every hand's final place on screen is remembered each frame (mixin/FirstPersonHandsMixin, just before the
 * item is drawn) and, at a switch, the item starts from where it was drawn and eases to its new place over {@link #LENGTH}
 * ticks: its shift, turn and size each blended, so it travels instead of jumping.
 *
 * <p>A shield is a different model while blocking (vanilla's shield_blocking, with its own first-person display), so for a
 * shield the blend is of where the model really ends up: the display of each state is folded in ({@link #display}).
 */
public final class FirstPersonEase {
	private FirstPersonEase() {
	}

	/** Ticks a switch takes. */
	private static final float LENGTH = 4.0f;

	private static final Hand[] HANDS = {new Hand(), new Hand()};

	private static final class Hand {
		final Matrix4f last = new Matrix4f();
		final Matrix4f from = new Matrix4f();
		boolean haveLast;
		float lastTime = -1000;
		float start = -1000;
		int key = Integer.MIN_VALUE;
		net.minecraft.world.item.Item item;
	}

	public static void reset() {
		for (Hand h : HANDS) {
			h.haveLast = false;
			h.key = Integer.MIN_VALUE;
			h.start = -1000;
			h.item = null;
		}
	}

	/** Called with the pose the item is about to be drawn at; eases it if this hand just switched. */
	public static void apply(PoseStack poseStack, InteractionHand hand, HumanoidArm arm, ItemStack stack, boolean using, float time) {
		Hand h = HANDS[hand.ordinal()];
		ItemUseAnimation anim = using ? stack.getUseAnimation() : ItemUseAnimation.NONE;
		int key = using ? anim.ordinal() + 1 : 0;
		Matrix4f d = display(stack, using, arm);
		Matrix4f pose = poseStack.last().pose();
		Matrix4f effective = new Matrix4f(pose).mul(d);
		boolean fresh = time > h.lastTime + 1.0e-4f || time < h.lastTime - 40;
		if (fresh && (key != h.key || stack.getItem() != h.item)) {
			// only a switch of use with the same item (a new item in the hand has vanilla's own lowering and raising)
			if (h.haveLast && Math.abs(time - h.lastTime) < 3 && stack.getItem() == h.item && h.key != Integer.MIN_VALUE) {
				h.from.set(h.last);
				h.start = time;
			}
			h.key = key;
			h.item = stack.getItem();
		}
		float t = (time - h.start) / LENGTH;
		if (t >= 0 && t < 1) {
			float w = t * t * (3 - 2 * t);
			effective = blend(h.from, effective, w);
			pose.set(new Matrix4f(effective).mul(new Matrix4f(d).invert()));
		}
		if (fresh) {
			h.last.set(effective);
			h.haveLast = true;
			h.lastTime = time;
		}
	}

	private static Matrix4f blend(Matrix4f a, Matrix4f b, float w) {
		Vector3f ta = a.getTranslation(new Vector3f());
		Vector3f tb = b.getTranslation(new Vector3f());
		Vector3f sa = a.getScale(new Vector3f());
		Vector3f sb = b.getScale(new Vector3f());
		Quaternionf ra = a.getUnnormalizedRotation(new Quaternionf()).normalize();
		Quaternionf rb = b.getUnnormalizedRotation(new Quaternionf()).normalize();
		return new Matrix4f().translationRotateScale(ta.lerp(tb, w), ra.slerp(rb, w), sa.lerp(sb, w));
	}

	/** The part of the item's first-person display that differs with its state: only a shield's (blocking or not). */
	private static Matrix4f display(ItemStack stack, boolean using, HumanoidArm arm) {
		if (!(stack.getItem() instanceof ShieldItem)) {
			return new Matrix4f();
		}
		boolean left = arm == HumanoidArm.LEFT;
		// vanilla's models/item/shield.json and shield_blocking.json, firstperson_righthand / _lefthand (the left hand's
		// mirrored as vanilla does: x shift, y and z turns negated)
		float tx;
		float ty;
		float tz;
		float rz;
		if (!left) {
			tx = using ? -15 : -10;
			ty = using ? 3.25f : 1.75f;
			tz = using ? -11 : -10;
			rz = using ? -5 : 5;
		} else {
			tx = using ? -5 : -10;
			ty = using ? 5 : 0;
			tz = using ? -11 : -10;
			rz = using ? 5 : -5;
		}
		float ry = left ? -180 : 180;
		return new Matrix4f().translate(tx / 16, ty / 16, tz / 16)
				.rotate(new Quaternionf().rotationXYZ(0, ry * (float) (Math.PI / 180), rz * (float) (Math.PI / 180)));
	}
}
