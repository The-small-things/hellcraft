package net.thesmallthings.hellcraft.client.render;

import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.thesmallthings.hellcraft.HellcraftMod;

/**
 * A humanoid of jointed parts (body > head and arms; legs on the root), laid out for a 64x64 humanoid skin.
 * Each part turns about its own pivot, which is what lets a boss move a jaw, an arm or a wing on its own.
 */
public class TestRigModel extends EntityModel<TestRigRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(HellcraftMod.id("test_rig"), "main");

	private final KeyframeAnimation wave;

	public TestRigModel(ModelPart root) {
		super(root);
		this.wave = TestRigAnimation.WAVE.bake(root);
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 16).addBox(-4, 0, -2, 8, 12, 4), PartPose.ZERO);
		body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8), PartPose.ZERO);
		body.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-3, -2, -2, 4, 12, 4), PartPose.offset(-5, 2, 0));
		body.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(40, 16).mirror().addBox(-1, -2, -2, 4, 12, 4), PartPose.offset(5, 2, 0));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-2, 0, -2, 4, 12, 4), PartPose.offset(-1.9f, 12, 0));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-2, 0, -2, 4, 12, 4), PartPose.offset(1.9f, 12, 0));
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(TestRigRenderState state) {
		super.setupAnim(state);
		wave.apply(state.wave, state.ageInTicks);
	}
}
