package net.thesmallthings.hellcraft.client.render.boss;

import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.client.render.gen.CerberusAnimations;
import net.thesmallthings.hellcraft.entity.boss.gen.CerberusAnim;

/**
 * Cerberus's jointed model (generated: CerberusMesh) moved by his keyframe animations (CerberusAnimations). He walks
 * by his speed while he isn't busy with a blow; his three heads follow his gaze a little.
 */
public class CerberusModel extends EntityModel<CerberusRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(HellcraftMod.id("cerberus"), "main");

	private final KeyframeAnimation[] animations = new KeyframeAnimation[CerberusAnim.values().length];
	private final ModelPart[] necks;

	public CerberusModel(ModelPart root) {
		super(root);
		for (CerberusAnim anim : CerberusAnim.values()) {
			animations[anim.ordinal()] = CerberusAnimations.of(anim).bake(root);
		}
		ModelPart body = root.getChild("body");
		this.necks = new ModelPart[]{body.getChild("neck_mid"), body.getChild("neck_right"), body.getChild("neck_left")};
	}

	@Override
	public void setupAnim(CerberusRenderState state) {
		super.setupAnim(state);
		for (ModelPart neck : necks) {
			neck.yRot += state.yRot * 0.4f * ((float) Math.PI / 180f);
		}
		if (!state.acting) {
			animations[CerberusAnim.WALK.ordinal()].applyWalk(state.walkAnimationPos, state.walkAnimationSpeed, 1.6f, 2.0f);
		}
		for (CerberusAnim anim : CerberusAnim.values()) {
			if (anim != CerberusAnim.WALK) {
				animations[anim.ordinal()].apply(state.states[anim.ordinal()], state.ageInTicks, state.speeds[anim.ordinal()]);
			}
		}
	}
}
