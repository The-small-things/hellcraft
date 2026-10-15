package net.thesmallthings.hellcraft.client.render.boss;

import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.client.render.gen.PlutusAnimations;
import net.thesmallthings.hellcraft.entity.boss.gen.PlutusAnim;

/** Plutus's jointed model (generated: PlutusMesh) moved by his keyframe animations (PlutusAnimations) and his gaze. */
public class PlutusModel extends EntityModel<PlutusRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(HellcraftMod.id("plutus"), "main");

	private final KeyframeAnimation[] animations = new KeyframeAnimation[PlutusAnim.values().length];
	private final ModelPart head;

	public PlutusModel(ModelPart root) {
		super(root);
		for (PlutusAnim anim : PlutusAnim.values()) {
			animations[anim.ordinal()] = PlutusAnimations.of(anim).bake(root);
		}
		this.head = root.getChild("body").getChild("head");
	}

	@Override
	public void setupAnim(PlutusRenderState state) {
		super.setupAnim(state);
		head.yRot += state.yRot * 0.6f * ((float) Math.PI / 180f);
		head.xRot += state.xRot * 0.6f * ((float) Math.PI / 180f);
		if (!state.acting) {
			animations[PlutusAnim.WALK.ordinal()].applyWalk(state.walkAnimationPos, state.walkAnimationSpeed, 1.4f, 2.0f);
		}
		for (PlutusAnim anim : PlutusAnim.values()) {
			if (anim != PlutusAnim.WALK) {
				animations[anim.ordinal()].apply(state.states[anim.ordinal()], state.ageInTicks);
			}
		}
	}
}
