package net.thesmallthings.hellcraft.client.render.boss;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.entity.Ferry;

/** Draws Phlegyas's ferry, turned the way it sails, its lantern lit (ferry_glow.png). */
public class FerryRenderer extends EntityRenderer<Ferry, FerryRenderState> {
	private static final Identifier TEXTURE = HellcraftMod.id("textures/entity/ferry.png");
	private static final Identifier GLOW = HellcraftMod.id("textures/entity/ferry_glow.png");
	private final FerryModel model;

	public FerryRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new FerryModel(context.bakeLayer(FerryModel.LAYER));
		this.shadowRadius = 0f;
	}

	@Override
	public FerryRenderState createRenderState() {
		return new FerryRenderState();
	}

	@Override
	public void extractRenderState(Ferry ferry, FerryRenderState state, float partialTick) {
		super.extractRenderState(ferry, state, partialTick);
		state.bob.copyFrom(ferry.bob);
		state.yaw = ferry.getYRot(partialTick);
	}

	@Override
	public void submit(FerryRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, 180.0f - state.yaw);
		poseStack.scale(-1.0f, -1.0f, 1.0f);
		poseStack.translate(0.0f, -1.501f, 0.0f);
		model.setupAnim(state);
		collector.submitModel(model, state, poseStack, model.renderType(TEXTURE), state.lightCoords, OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor);
		collector.submitModel(model, state, poseStack, RenderTypes.eyes(GLOW), state.lightCoords, OverlayTexture.NO_OVERLAY, -1, null, 0);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
