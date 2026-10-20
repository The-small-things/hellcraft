package net.thesmallthings.hellcraft.client.render.npc;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.entity.npc.Damned;
import org.jetbrains.annotations.Nullable;

/**
 * Draws one of the damned: pale and see-through like Limbo's shades, each a little differently tinted (ash, warm,
 * cold, sickly), stained by its circle (the blood of Phlegethon, the mud of Gluttony), the faint light in its eye
 * sockets lit even in the dark. It is never quite lost in the dark itself, and casts no shadow where it is sunk in
 * something or blown about the sky.
 */
public class DamnedRenderer extends MobRenderer<Damned, DamnedRenderState, DamnedModel> {
	private static final Identifier TEXTURE = HellcraftMod.id("textures/entity/damned.png");
	private static final RenderType GLOW = RenderTypes.eyes(HellcraftMod.id("textures/entity/damned_glow.png"));
	/** How solid a soul looks (of 255). */
	private static final int ALPHA = 200;
	private static final int[] TINTS = {0xFFFFFF, 0xFFE8DA, 0xDAE4FF, 0xDDE8D4};

	public DamnedRenderer(EntityRendererProvider.Context context) {
		super(context, new DamnedModel(context.bakeLayer(DamnedModel.LAYER)), 0.35f);
		addLayer(new EyesLayer<>(this) {
			@Override
			public RenderType renderType() {
				return GLOW;
			}
		});
	}

	@Override
	public DamnedRenderState createRenderState() {
		return new DamnedRenderState();
	}

	@Override
	public void extractRenderState(Damned damned, DamnedRenderState state, float partialTick) {
		super.extractRenderState(damned, state, partialTick);
		for (int i = 0; i < state.states.length; i++) {
			state.states[i].copyFrom(damned.states[i]);
		}
		state.punishment = damned.punishment();
		state.tint = damned.tint();
		state.leader = damned.leader();
	}

	@Override
	protected int getBlockLightLevel(Damned damned, BlockPos pos) {
		return Math.max(5, super.getBlockLightLevel(damned, pos));
	}

	@Override
	protected float getShadowRadius(DamnedRenderState state) {
		return switch (state.punishment) {
			case LUST, GLUTTONY, WRATH, HERESY, PHLEGETHON, COCYTUS -> 0;
			default -> super.getShadowRadius(state);
		};
	}

	@Override
	protected @Nullable RenderType getRenderType(DamnedRenderState state, boolean bodyVisible, boolean forceTransparent, boolean glowing) {
		return RenderTypes.entityTranslucent(getTextureLocation(state));
	}

	@Override
	protected int getModelTint(DamnedRenderState state) {
		int tint = TINTS[state.tint & 3];
		int stain = switch (state.punishment) {
			case PHLEGETHON -> 0xFFB4AC;
			case GLUTTONY -> 0xDCCCB4;
			default -> 0xFFFFFF;
		};
		return ARGB.color(ALPHA, ARGB.multiply(tint, stain));
	}

	@Override
	protected float getFlipDegrees() {
		return 0;
	}

	@Override
	public Identifier getTextureLocation(DamnedRenderState state) {
		return TEXTURE;
	}
}
