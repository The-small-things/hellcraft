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
import net.thesmallthings.hellcraft.entity.boss.CerberusEntity;
import net.thesmallthings.hellcraft.entity.boss.gen.CerberusAnim;

/**
 * Draws Cerberus: his model at 1.5 times its Blockbench size, his six red eyes lit in the dark (cerberus_glow.png), a
 * white flash while a bite can be blocked and a purple pulse while his howl can't. He plays his own death.
 */
public class CerberusRenderer extends MobRenderer<CerberusEntity, CerberusRenderState, CerberusModel> {
	public static final float SCALE = 1.5f;
	private static final Identifier TEXTURE = HellcraftMod.id("textures/entity/cerberus.png");
	private static final RenderType GLOW = RenderTypes.eyes(HellcraftMod.id("textures/entity/cerberus_glow.png"));

	public CerberusRenderer(EntityRendererProvider.Context context) {
		super(context, new CerberusModel(context.bakeLayer(CerberusModel.LAYER)), 3.0f);
		addLayer(new EyesLayer<>(this) {
			@Override
			public RenderType renderType() {
				return GLOW;
			}
		});
	}

	@Override
	public CerberusRenderState createRenderState() {
		return new CerberusRenderState();
	}

	@Override
	public void extractRenderState(CerberusEntity cerberus, CerberusRenderState state, float partialTick) {
		super.extractRenderState(cerberus, state, partialTick);
		for (int i = 0; i < state.states.length; i++) {
			state.states[i].copyFrom(cerberus.states[i]);
			state.speeds[i] = cerberus.speedOf(CerberusAnim.values()[i]);
		}
		state.warning = cerberus.warning();
		state.acting = cerberus.acting();
		state.hasRedOverlay = cerberus.hurtTime > 0;
	}

	@Override
	protected void scale(CerberusRenderState state, PoseStack poseStack) {
		poseStack.scale(SCALE, SCALE, SCALE);
	}

	@Override
	protected float getFlipDegrees() {
		return 0;
	}

	@Override
	protected float getWhiteOverlayProgress(CerberusRenderState state) {
		return state.warning == BossEntity.Warning.WHITE ? 0.3f + 0.22f * Mth.sin(state.ageInTicks * 1.6f) : 0;
	}

	@Override
	protected int getModelTint(CerberusRenderState state) {
		if (state.warning != BossEntity.Warning.PURPLE) {
			return -1;
		}
		float t = 0.5f + 0.5f * Mth.sin(state.ageInTicks * 1.2f);
		return ARGB.colorFromFloat(1, 0.75f + 0.15f * t, 0.35f + 0.2f * t, 1);
	}

	@Override
	public Identifier getTextureLocation(CerberusRenderState state) {
		return TEXTURE;
	}
}
