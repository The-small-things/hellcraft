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
import net.thesmallthings.hellcraft.entity.boss.PlutusEntity;

/**
 * Draws Plutus: his model at 1.3 times its Blockbench size, his greedy eyes and rubies lit in the dark
 * (plutus_glow.png), a white flash while his fist can be blocked and a purple pulse while his shriek can't. He plays
 * his own death.
 */
public class PlutusRenderer extends MobRenderer<PlutusEntity, PlutusRenderState, PlutusModel> {
	public static final float SCALE = 1.3f;
	private static final Identifier TEXTURE = HellcraftMod.id("textures/entity/plutus.png");
	private static final RenderType GLOW = RenderTypes.eyes(HellcraftMod.id("textures/entity/plutus_glow.png"));

	public PlutusRenderer(EntityRendererProvider.Context context) {
		super(context, new PlutusModel(context.bakeLayer(PlutusModel.LAYER)), 2.6f);
		addLayer(new EyesLayer<>(this) {
			@Override
			public RenderType renderType() {
				return GLOW;
			}
		});
	}

	@Override
	public PlutusRenderState createRenderState() {
		return new PlutusRenderState();
	}

	@Override
	public void extractRenderState(PlutusEntity plutus, PlutusRenderState state, float partialTick) {
		super.extractRenderState(plutus, state, partialTick);
		for (int i = 0; i < state.states.length; i++) {
			state.states[i].copyFrom(plutus.states[i]);
		}
		state.warning = plutus.warning();
		state.acting = plutus.acting();
		state.hasRedOverlay = plutus.hurtTime > 0;
	}

	@Override
	protected void scale(PlutusRenderState state, PoseStack poseStack) {
		poseStack.scale(SCALE, SCALE, SCALE);
	}

	@Override
	protected float getFlipDegrees() {
		return 0;
	}

	@Override
	protected float getWhiteOverlayProgress(PlutusRenderState state) {
		return state.warning == BossEntity.Warning.WHITE ? 0.3f + 0.22f * Mth.sin(state.ageInTicks * 1.6f) : 0;
	}

	@Override
	protected int getModelTint(PlutusRenderState state) {
		if (state.warning != BossEntity.Warning.PURPLE) {
			return -1;
		}
		float t = 0.5f + 0.5f * Mth.sin(state.ageInTicks * 1.2f);
		return ARGB.colorFromFloat(1, 0.75f + 0.15f * t, 0.35f + 0.2f * t, 1);
	}

	@Override
	public Identifier getTextureLocation(PlutusRenderState state) {
		return TEXTURE;
	}
}
