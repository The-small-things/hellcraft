package net.thesmallthings.hellcraft.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.thesmallthings.hellcraft.entity.TestRig;

public class TestRigRenderer extends MobRenderer<TestRig, TestRigRenderState, TestRigModel> {
	/** A vanilla skin will do for a test. */
	private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/zombie/husk.png");

	public TestRigRenderer(EntityRendererProvider.Context context) {
		super(context, new TestRigModel(context.bakeLayer(TestRigModel.LAYER)), 0.5f);
	}

	@Override
	public TestRigRenderState createRenderState() {
		return new TestRigRenderState();
	}

	@Override
	public void extractRenderState(TestRig rig, TestRigRenderState state, float partialTick) {
		super.extractRenderState(rig, state, partialTick);
		state.wave.copyFrom(rig.wave);
	}

	@Override
	public Identifier getTextureLocation(TestRigRenderState state) {
		return TEXTURE;
	}
}
