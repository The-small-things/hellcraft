package net.thesmallthings.hellcraft.client.render;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

/**
 * The test rig's wave, 2 seconds, looping: the right arm rises and waves, the left arm swings, the head looks
 * left and right, and the body bobs. Catmull-Rom curves through the keyframes, as the Warden's and the Breeze's.
 */
public final class TestRigAnimation {
	private TestRigAnimation() {
	}

	public static final AnimationDefinition WAVE = AnimationDefinition.Builder.withLength(2.0f).looping()
			.addAnimation("right_arm", new AnimationChannel(AnimationChannel.Targets.ROTATION,
					new Keyframe(0.0f, KeyframeAnimations.degreeVec(0, 0, 0), AnimationChannel.Interpolations.CATMULLROM),
					new Keyframe(0.5f, KeyframeAnimations.degreeVec(-160, 0, 15), AnimationChannel.Interpolations.CATMULLROM),
					new Keyframe(1.0f, KeyframeAnimations.degreeVec(-150, 0, 45), AnimationChannel.Interpolations.CATMULLROM),
					new Keyframe(1.5f, KeyframeAnimations.degreeVec(-160, 0, 15), AnimationChannel.Interpolations.CATMULLROM),
					new Keyframe(2.0f, KeyframeAnimations.degreeVec(0, 0, 0), AnimationChannel.Interpolations.CATMULLROM)))
			.addAnimation("left_arm", new AnimationChannel(AnimationChannel.Targets.ROTATION,
					new Keyframe(0.0f, KeyframeAnimations.degreeVec(0, 0, 0), AnimationChannel.Interpolations.CATMULLROM),
					new Keyframe(1.0f, KeyframeAnimations.degreeVec(-60, 0, -15), AnimationChannel.Interpolations.CATMULLROM),
					new Keyframe(2.0f, KeyframeAnimations.degreeVec(0, 0, 0), AnimationChannel.Interpolations.CATMULLROM)))
			.addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
					new Keyframe(0.0f, KeyframeAnimations.degreeVec(0, 0, 0), AnimationChannel.Interpolations.CATMULLROM),
					new Keyframe(0.5f, KeyframeAnimations.degreeVec(0, -35, 0), AnimationChannel.Interpolations.CATMULLROM),
					new Keyframe(1.0f, KeyframeAnimations.degreeVec(15, 0, 0), AnimationChannel.Interpolations.CATMULLROM),
					new Keyframe(1.5f, KeyframeAnimations.degreeVec(0, 35, 0), AnimationChannel.Interpolations.CATMULLROM),
					new Keyframe(2.0f, KeyframeAnimations.degreeVec(0, 0, 0), AnimationChannel.Interpolations.CATMULLROM)))
			.addAnimation("body", new AnimationChannel(AnimationChannel.Targets.POSITION,
					new Keyframe(0.0f, KeyframeAnimations.posVec(0, 0, 0), AnimationChannel.Interpolations.CATMULLROM),
					new Keyframe(1.0f, KeyframeAnimations.posVec(0, -1, 0), AnimationChannel.Interpolations.CATMULLROM),
					new Keyframe(2.0f, KeyframeAnimations.posVec(0, 0, 0), AnimationChannel.Interpolations.CATMULLROM)))
			.build();
}
