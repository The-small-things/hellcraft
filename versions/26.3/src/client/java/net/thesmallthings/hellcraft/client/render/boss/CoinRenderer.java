package net.thesmallthings.hellcraft.client.render.boss;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.entity.boss.RollingCoin;

/** Draws a great coin of the Mint standing on its edge, turned the way it rolls. */
public class CoinRenderer extends EntityRenderer<RollingCoin, CoinRenderState> {
	private static final Identifier TEXTURE = HellcraftMod.id("textures/entity/coin.png");
	private final CoinModel model;

	public CoinRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new CoinModel(context.bakeLayer(CoinModel.LAYER));
		this.shadowRadius = 1.5f;
	}

	@Override
	public CoinRenderState createRenderState() {
		return new CoinRenderState();
	}

	@Override
	public void extractRenderState(RollingCoin coin, CoinRenderState state, float partialTick) {
		super.extractRenderState(coin, state, partialTick);
		state.roll.copyFrom(coin.roll);
		state.yaw = coin.getYRot(partialTick);
	}

	@Override
	public void submit(CoinRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, 180.0f - state.yaw);
		poseStack.scale(-1.0f, -1.0f, 1.0f);
		poseStack.translate(0.0f, -1.501f, 0.0f);
		model.setupAnim(state);
		collector.submitModel(model, state, poseStack, model.renderType(TEXTURE), state.lightCoords, OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
