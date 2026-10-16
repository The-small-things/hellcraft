package net.thesmallthings.hellcraft.client.render.boss;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.entity.boss.BossEntity;
import net.thesmallthings.hellcraft.entity.boss.MinosEntity;
import net.thesmallthings.hellcraft.entity.boss.gen.MinosAnim;

/**
 * Draws Minos: his model at 1.35 times its Blockbench size, his eyes lit even in the dark (minos_glow.png), a white
 * flash while a blow can be blocked and a purple pulse while one can't (the danger language). He plays his own death
 * instead of tipping over.
 */
public class MinosRenderer extends MobRenderer<MinosEntity, MinosRenderState, MinosModel> {
	public static final float SCALE = 1.35f;
	private static final Identifier TEXTURE = HellcraftMod.id("textures/entity/minos.png");
	private static final RenderType GLOW = RenderTypes.eyes(HellcraftMod.id("textures/entity/minos_glow.png"));

	public MinosRenderer(EntityRendererProvider.Context context) {
		super(context, new MinosModel(context.bakeLayer(MinosModel.LAYER)), 2.2f);
		addLayer(new EyesLayer<>(this) {
			@Override
			public RenderType renderType() {
				return GLOW;
			}
		});
	}

	@Override
	public MinosRenderState createRenderState() {
		return new MinosRenderState();
	}

	@Override
	public void extractRenderState(MinosEntity minos, MinosRenderState state, float partialTick) {
		super.extractRenderState(minos, state, partialTick);
		for (int i = 0; i < state.states.length; i++) {
			state.states[i].copyFrom(minos.states[i]);
			state.speeds[i] = minos.speedOf(MinosAnim.values()[i]);
		}
		state.warning = minos.warning();
		state.hasRedOverlay = minos.hurtTime > 0;
	}

	@Override
	protected void scale(MinosRenderState state, PoseStack poseStack) {
		poseStack.scale(SCALE, SCALE, SCALE);
	}

	@Override
	protected float getFlipDegrees() {
		return 0;
	}

	/** A fast white pulse: block now. */
	@Override
	protected float getWhiteOverlayProgress(MinosRenderState state) {
		return state.warning == BossEntity.Warning.WHITE ? 0.3f + 0.22f * Mth.sin(state.ageInTicks * 1.6f) : 0;
	}

	/** A purple pulse: this one can't be blocked. */
	@Override
	protected int getModelTint(MinosRenderState state) {
		if (state.warning != BossEntity.Warning.PURPLE) {
			return -1;
		}
		float t = 0.5f + 0.5f * Mth.sin(state.ageInTicks * 1.2f);
		return ARGB.colorFromFloat(1, 0.75f + 0.15f * t, 0.35f + 0.2f * t, 1);
	}

	@Override
	public Identifier getTextureLocation(MinosRenderState state) {
		return TEXTURE;
	}
}
