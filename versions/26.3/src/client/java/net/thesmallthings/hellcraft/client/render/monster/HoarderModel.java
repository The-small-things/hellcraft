package net.thesmallthings.hellcraft.client.render.monster;

import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.client.render.gen.HoarderAnimations;

/** A hoarder's jointed model (generated: HoarderMesh) moved by its keyframe animations (HoarderAnimations). */
public class HoarderModel extends EntityModel<HoarderRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(HellcraftMod.id("hoarder"), "main");

	private final ModelPart head;
	private final KeyframeAnimation idle;
	private final KeyframeAnimation walk;
	private final KeyframeAnimation attack;
	private final KeyframeAnimation hurt;
	private final KeyframeAnimation death;

	public HoarderModel(ModelPart root) {
		super(root);
		this.head = root.getChild("body").getChild("head");
		this.idle = HoarderAnimations.IDLE.bake(root);
		this.walk = HoarderAnimations.WALK.bake(root);
		this.attack = HoarderAnimations.ATTACK.bake(root);
		this.hurt = HoarderAnimations.HURT.bake(root);
		this.death = HoarderAnimations.DEATH.bake(root);
	}

	@Override
	public void setupAnim(HoarderRenderState state) {
		super.setupAnim(state);
		head.yRot += state.yRot * ((float) Math.PI / 180f);
		head.xRot += state.xRot * ((float) Math.PI / 180f);
		if (state.deathTime > 0) {
			death.apply(state.death, state.ageInTicks);
			return;
		}
		walk.applyWalk(state.walkAnimationPos, state.walkAnimationSpeed, 1.5f, 2.5f);
		idle.apply(state.idle, state.ageInTicks);
		attack.apply(state.attack, state.ageInTicks);
		hurt.apply(state.hurt, state.ageInTicks);
	}
}
