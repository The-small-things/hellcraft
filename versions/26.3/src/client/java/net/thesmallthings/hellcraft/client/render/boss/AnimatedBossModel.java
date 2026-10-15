package net.thesmallthings.hellcraft.client.render.boss;

import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An AnimatedBoss's jointed model (its generated mesh) moved by its keyframe animations: it walks by its speed while it
 * isn't busy with a blow, and its head follows its gaze by `gaze` (0: not at all, 1: fully).
 */
public class AnimatedBossModel extends EntityModel<AnimatedBossRenderState> {
	private final KeyframeAnimation[] animations;
	private final int walk;
	private final float gaze;
	@Nullable
	private final ModelPart head;

	public AnimatedBossModel(ModelPart root, List<AnimationDefinition> anims, int walk, float gaze, String... headPath) {
		super(root);
		this.animations = new KeyframeAnimation[anims.size()];
		for (int i = 0; i < animations.length; i++) {
			animations[i] = anims.get(i).bake(root);
		}
		this.walk = walk;
		this.gaze = gaze;
		ModelPart h = headPath.length == 0 ? null : root;
		for (String bone : headPath) {
			h = h.getChild(bone);
		}
		this.head = h;
	}

	/** A part drawn only while one animation plays (Medusa's open eyes), or null. */
	@Nullable
	private ModelPart onlyPart;
	private int onlyAnim = -1;

	/** Draws the part at this path only while animation `anim` plays (keyframes add up, so they can't hide it alone). */
	public AnimatedBossModel onlyDuring(int anim, String... path) {
		ModelPart p = root();
		for (String bone : path) {
			p = p.getChild(bone);
		}
		this.onlyPart = p;
		this.onlyAnim = anim;
		return this;
	}

	@Override
	public void setupAnim(AnimatedBossRenderState state) {
		super.setupAnim(state);
		if (onlyPart != null) {
			onlyPart.visible = state.states[onlyAnim].isStarted();
		}
		if (head != null) {
			head.yRot += state.yRot * gaze * ((float) Math.PI / 180f);
			head.xRot += state.xRot * gaze * ((float) Math.PI / 180f);
		}
		if (walk >= 0 && !state.acting && state.deathTime <= 0) {
			animations[walk].applyWalk(state.walkAnimationPos, state.walkAnimationSpeed, 1.6f, 2.0f);
		}
		for (int i = 0; i < animations.length; i++) {
			if (i != walk) {
				animations[i].apply(state.states[i], state.ageInTicks);
			}
		}
	}
}
