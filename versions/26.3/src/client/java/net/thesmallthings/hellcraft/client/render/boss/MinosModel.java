package net.thesmallthings.hellcraft.client.render.boss;

import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.client.render.gen.MinosAnimations;
import net.thesmallthings.hellcraft.entity.boss.gen.MinosAnim;

/** Minos's jointed model (generated: MinosMesh) moved by his keyframe animations (MinosAnimations) and his gaze. */
public class MinosModel extends EntityModel<MinosRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(HellcraftMod.id("minos"), "main");

	private final KeyframeAnimation[] animations = new KeyframeAnimation[MinosAnim.values().length];
	private final ModelPart head;

	public MinosModel(ModelPart root) {
		super(root);
		for (MinosAnim anim : MinosAnim.values()) {
			animations[anim.ordinal()] = MinosAnimations.of(anim).bake(root);
		}
		this.head = root.getChild("waist").getChild("torso").getChild("head");
	}

	@Override
	public void setupAnim(MinosRenderState state) {
		super.setupAnim(state);
		// he looks at whoever he is judging (a little: his whole body turns toward them too)
		head.yRot += state.yRot * 0.6f * ((float) Math.PI / 180f);
		head.xRot += state.xRot * 0.6f * ((float) Math.PI / 180f);
		for (MinosAnim anim : MinosAnim.values()) {
			animations[anim.ordinal()].apply(state.states[anim.ordinal()], state.ageInTicks);
		}
	}
}
