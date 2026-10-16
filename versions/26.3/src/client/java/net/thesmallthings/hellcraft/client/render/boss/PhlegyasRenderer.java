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
import net.thesmallthings.hellcraft.entity.boss.PhlegyasEntity;
import net.thesmallthings.hellcraft.entity.boss.gen.PhlegyasAnim;

/**
 * Draws Phlegyas: his model at 1.3 times its Blockbench size, his eyes burning like the beacons that call him
 * (phlegyas_glow.png), a white flash while his oar can be blocked and a purple pulse while it can't. He sinks for his
 * death.
 */
public class PhlegyasRenderer extends MobRenderer<PhlegyasEntity, PhlegyasRenderState, PhlegyasModel> {
	public static final float SCALE = 1.3f;
	private static final Identifier TEXTURE = HellcraftMod.id("textures/entity/phlegyas.png");
	private static final RenderType GLOW = RenderTypes.eyes(HellcraftMod.id("textures/entity/phlegyas_glow.png"));

	public PhlegyasRenderer(EntityRendererProvider.Context context) {
		super(context, new PhlegyasModel(context.bakeLayer(PhlegyasModel.LAYER)), 2.2f);
		addLayer(new EyesLayer<>(this) {
			@Override
			public RenderType renderType() {
				return GLOW;
			}
		});
	}

	@Override
	public PhlegyasRenderState createRenderState() {
		return new PhlegyasRenderState();
	}

	@Override
	public void extractRenderState(PhlegyasEntity phlegyas, PhlegyasRenderState state, float partialTick) {
		super.extractRenderState(phlegyas, state, partialTick);
		for (int i = 0; i < state.states.length; i++) {
			state.states[i].copyFrom(phlegyas.states[i]);
			state.speeds[i] = phlegyas.speedOf(PhlegyasAnim.values()[i]);
		}
		state.warning = phlegyas.warning();
		state.hasRedOverlay = phlegyas.hurtTime > 0;
	}

	@Override
	protected void scale(PhlegyasRenderState state, PoseStack poseStack) {
		poseStack.scale(SCALE, SCALE, SCALE);
	}

	@Override
	protected float getFlipDegrees() {
		return 0;
	}

	/** A fast white pulse: block now. */
	@Override
	protected float getWhiteOverlayProgress(PhlegyasRenderState state) {
		return state.warning == BossEntity.Warning.WHITE ? 0.3f + 0.22f * Mth.sin(state.ageInTicks * 1.6f) : 0;
	}

	/** A purple pulse: this one can't be blocked. */
	@Override
	protected int getModelTint(PhlegyasRenderState state) {
		if (state.warning != BossEntity.Warning.PURPLE) {
			return -1;
		}
		float t = 0.5f + 0.5f * Mth.sin(state.ageInTicks * 1.2f);
		return ARGB.colorFromFloat(1, 0.75f + 0.15f * t, 0.35f + 0.2f * t, 1);
	}

	@Override
	public Identifier getTextureLocation(PhlegyasRenderState state) {
		return TEXTURE;
	}
}
