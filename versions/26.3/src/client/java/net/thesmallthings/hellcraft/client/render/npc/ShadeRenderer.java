package net.thesmallthings.hellcraft.client.render.npc;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.entity.npc.Shade;
import org.jetbrains.annotations.Nullable;

/** Draws a shade of Limbo: pale and see-through, floating a little off the ground, its eyes and hem softly lit. */
public class ShadeRenderer extends MobRenderer<Shade, ShadeRenderState, ShadeModel> {
	private static final Identifier TEXTURE = HellcraftMod.id("textures/entity/shade.png");
	private static final RenderType GLOW = RenderTypes.eyes(HellcraftMod.id("textures/entity/shade_glow.png"));
	/** How solid a shade looks (of 255). */
	private static final int ALPHA = 170;

	public ShadeRenderer(EntityRendererProvider.Context context) {
		super(context, new ShadeModel(context.bakeLayer(ShadeModel.LAYER)), 0.3f);
		addLayer(new EyesLayer<>(this) {
			@Override
			public RenderType renderType() {
				return GLOW;
			}
		});
	}

	@Override
	public ShadeRenderState createRenderState() {
		return new ShadeRenderState();
	}

	@Override
	public void extractRenderState(Shade shade, ShadeRenderState state, float partialTick) {
		super.extractRenderState(shade, state, partialTick);
		state.idle.copyFrom(shade.idle);
		state.talk.copyFrom(shade.talk);
	}

	/** They float a hand's breadth above the meadow. */
	@Override
	protected void scale(ShadeRenderState state, PoseStack poseStack) {
		poseStack.translate(0, -0.2f, 0);
	}

	@Override
	protected @Nullable RenderType getRenderType(ShadeRenderState state, boolean bodyVisible, boolean forceTransparent, boolean glowing) {
		return RenderTypes.entityTranslucent(getTextureLocation(state));
	}

	@Override
	protected int getModelTint(ShadeRenderState state) {
		return ARGB.color(ALPHA, 0xFFFFFF);
	}

	@Override
	public Identifier getTextureLocation(ShadeRenderState state) {
		return TEXTURE;
	}
}
