package net.thesmallthings.hellcraft.client.render.monster;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.entity.monster.Hoarder;

/** Draws a hoarder: its gold-greedy eyes lit in the dark (hoarder_glow.png); it plays its own death. */
public class HoarderRenderer extends MobRenderer<Hoarder, HoarderRenderState, HoarderModel> {
	private static final Identifier TEXTURE = HellcraftMod.id("textures/entity/hoarder.png");
	private static final RenderType GLOW = RenderTypes.eyes(HellcraftMod.id("textures/entity/hoarder_glow.png"));

	public HoarderRenderer(EntityRendererProvider.Context context) {
		super(context, new HoarderModel(context.bakeLayer(HoarderModel.LAYER)), 0.6f);
		addLayer(new EyesLayer<>(this) {
			@Override
			public RenderType renderType() {
				return GLOW;
			}
		});
	}

	@Override
	public HoarderRenderState createRenderState() {
		return new HoarderRenderState();
	}

	@Override
	public void extractRenderState(Hoarder hoarder, HoarderRenderState state, float partialTick) {
		super.extractRenderState(hoarder, state, partialTick);
		state.idle.copyFrom(hoarder.idle);
		state.attack.copyFrom(hoarder.attack);
		state.hurt.copyFrom(hoarder.hurt);
		state.death.copyFrom(hoarder.death);
	}

	@Override
	protected float getFlipDegrees() {
		return 0;
	}

	@Override
	public Identifier getTextureLocation(HoarderRenderState state) {
		return TEXTURE;
	}
}
