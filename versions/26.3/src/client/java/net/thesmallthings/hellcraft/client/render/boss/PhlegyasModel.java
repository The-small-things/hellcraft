package net.thesmallthings.hellcraft.client.render.boss;

import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.client.render.gen.PhlegyasAnimations;
import net.thesmallthings.hellcraft.entity.boss.gen.PhlegyasAnim;

/** Phlegyas's jointed model (generated: PhlegyasMesh) moved by his keyframe animations (PhlegyasAnimations) and his gaze. */
public class PhlegyasModel extends EntityModel<PhlegyasRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(HellcraftMod.id("phlegyas"), "main");

	private final KeyframeAnimation[] animations = new KeyframeAnimation[PhlegyasAnim.values().length];
	private final ModelPart head;

	public PhlegyasModel(ModelPart root) {
		super(root);
		for (PhlegyasAnim anim : PhlegyasAnim.values()) {
			animations[anim.ordinal()] = PhlegyasAnimations.of(anim).bake(root);
		}
		this.head = root.getChild("hips").getChild("torso").getChild("head");
	}

	@Override
	public void setupAnim(PhlegyasRenderState state) {
		super.setupAnim(state);
		// he glares at his prey (a little: his whole body turns toward them too)
		head.yRot += state.yRot * 0.6f * ((float) Math.PI / 180f);
		head.xRot += state.xRot * 0.6f * ((float) Math.PI / 180f);
		for (PhlegyasAnim anim : PhlegyasAnim.values()) {
			animations[anim.ordinal()].apply(state.states[anim.ordinal()], state.ageInTicks, state.speeds[anim.ordinal()]);
		}
	}
}
