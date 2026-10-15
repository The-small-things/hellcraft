package net.thesmallthings.hellcraft.client.render.boss;

import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.client.render.gen.FerryAnimations;

/** Phlegyas's ferry (generated: FerryMesh), rocking on the water (FerryAnimations). */
public class FerryModel extends EntityModel<FerryRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(HellcraftMod.id("ferry"), "main");

	private final KeyframeAnimation bob;

	public FerryModel(ModelPart root) {
		super(root);
		this.bob = FerryAnimations.FLOAT.bake(root);
	}

	@Override
	public void setupAnim(FerryRenderState state) {
		super.setupAnim(state);
		bob.apply(state.bob, state.ageInTicks);
	}
}
