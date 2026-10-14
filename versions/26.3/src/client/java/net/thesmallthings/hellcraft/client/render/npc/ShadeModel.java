package net.thesmallthings.hellcraft.client.render.npc;

import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.client.render.gen.ShadeAnimations;

/** A shade's jointed model (generated: ShadeMesh) moved by its keyframe animations (ShadeAnimations), drawn see-through. */
public class ShadeModel extends EntityModel<ShadeRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(HellcraftMod.id("shade"), "main");

	private final ModelPart head;
	private final KeyframeAnimation idle;
	private final KeyframeAnimation walk;
	private final KeyframeAnimation talk;

	public ShadeModel(ModelPart root) {
		super(root, RenderTypes::entityTranslucent);
		this.head = root.getChild("body").getChild("head");
		this.idle = ShadeAnimations.IDLE.bake(root);
		this.walk = ShadeAnimations.WALK.bake(root);
		this.talk = ShadeAnimations.TALK.bake(root);
	}

	@Override
	public void setupAnim(ShadeRenderState state) {
		super.setupAnim(state);
		head.yRot += state.yRot * ((float) Math.PI / 180f);
		head.xRot += state.xRot * ((float) Math.PI / 180f);
		walk.applyWalk(state.walkAnimationPos, state.walkAnimationSpeed, 1.2f, 2.5f);
		idle.apply(state.idle, state.ageInTicks);
		talk.apply(state.talk, state.ageInTicks);
	}
}
