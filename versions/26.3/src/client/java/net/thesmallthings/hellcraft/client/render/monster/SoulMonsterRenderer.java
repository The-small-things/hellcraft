package net.thesmallthings.hellcraft.client.render.monster;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.entity.monster.SoulMonster;
import org.jetbrains.annotations.Nullable;

/**
 * Draws one of Hell's own monsters: its model (textures/entity/name.png), its glowing parts lit in the dark
 * (name_glow.png), see-through if it is a soul of the air; it plays its own death.
 */
public class SoulMonsterRenderer<T extends SoulMonster> extends MobRenderer<T, SoulMonsterRenderState, SoulMonsterModel> {
	private final Identifier texture;
	/** How solid it looks (255: solid). */
	private final int alpha;

	public SoulMonsterRenderer(EntityRendererProvider.Context context, ModelLayerLocation layer, SoulMonsterModel.Anims anims, String name,
							   float shadow, int alpha, String... headPath) {
		super(context, new SoulMonsterModel(context.bakeLayer(layer), anims, alpha < 255, headPath), shadow);
		this.texture = HellcraftMod.id("textures/entity/" + name + ".png");
		this.alpha = alpha;
		RenderType glow = RenderTypes.eyes(HellcraftMod.id("textures/entity/" + name + "_glow.png"));
		addLayer(new EyesLayer<>(this) {
			@Override
			public RenderType renderType() {
				return glow;
			}
		});
	}

	public static ModelLayerLocation layer(String name) {
		return new ModelLayerLocation(HellcraftMod.id(name), "main");
	}

	@Override
	public SoulMonsterRenderState createRenderState() {
		return new SoulMonsterRenderState();
	}

	@Override
	public void extractRenderState(T monster, SoulMonsterRenderState state, float partialTick) {
		super.extractRenderState(monster, state, partialTick);
		state.idle.copyFrom(monster.idle);
		state.attack.copyFrom(monster.attack);
		state.hurt.copyFrom(monster.hurt);
		state.death.copyFrom(monster.death);
		state.special.copyFrom(monster.special);
	}

	@Override
	protected float getFlipDegrees() {
		return 0;
	}

	@Override
	protected @Nullable RenderType getRenderType(SoulMonsterRenderState state, boolean bodyVisible, boolean forceTransparent, boolean glowing) {
		if (!bodyVisible) {
			return null;
		}
		return alpha < 255 ? RenderTypes.entityTranslucent(texture) : super.getRenderType(state, bodyVisible, forceTransparent, glowing);
	}

	@Override
	protected int getModelTint(SoulMonsterRenderState state) {
		return alpha < 255 ? ARGB.color(alpha, 0xFFFFFF) : -1;
	}

	@Override
	public Identifier getTextureLocation(SoulMonsterRenderState state) {
		return texture;
	}
}
