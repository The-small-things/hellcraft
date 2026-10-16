package net.thesmallthings.hellcraft.client.render.boss;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.entity.boss.AnimatedBoss;
import net.thesmallthings.hellcraft.entity.boss.BossEntity;

import java.util.List;

/**
 * Draws an AnimatedBoss: its model at `scale` times its Blockbench size (textures/entity/name.png), its glowing parts
 * lit in the dark (name_glow.png, if it has one), a white flash while its blow can be blocked and a purple pulse
 * while it can't. It plays its own death.
 */
public class AnimatedBossRenderer<T extends AnimatedBoss> extends MobRenderer<T, AnimatedBossRenderState, AnimatedBossModel> {
	private final Identifier texture;
	private final float scale;
	private final int animations;
	/** The least block light it's drawn with, however dark its spot (the Fallen Seraph shines; the Emperor looms). */
	private int minLight;

	public AnimatedBossRenderer(EntityRendererProvider.Context context, String name, List<AnimationDefinition> anims, int walk, float scale,
								float shadow, float gaze, String... headPath) {
		super(context, new AnimatedBossModel(context.bakeLayer(layer(name)), anims, walk, gaze, headPath), shadow);
		this.texture = HellcraftMod.id("textures/entity/" + name + ".png");
		this.scale = scale;
		this.animations = anims.size();
		Identifier glowTexture = HellcraftMod.id("textures/entity/" + name + "_glow.png");
		if (context.getResourceManager().getResource(glowTexture).isPresent()) {
			RenderType glow = RenderTypes.eyes(glowTexture);
			addLayer(new EyesLayer<>(this) {
				@Override
				public RenderType renderType() {
					return glow;
				}
			});
		}
	}

	/** Draws it at least this lit (block light 0 to 15). */
	public AnimatedBossRenderer<T> minLight(int light) {
		this.minLight = light;
		return this;
	}

	@Override
	protected int getBlockLightLevel(T boss, net.minecraft.core.BlockPos pos) {
		return Math.max(minLight, super.getBlockLightLevel(boss, pos));
	}

	public static ModelLayerLocation layer(String name) {
		return new ModelLayerLocation(HellcraftMod.id(name), "main");
	}

	@Override
	public AnimatedBossRenderState createRenderState() {
		return new AnimatedBossRenderState(animations);
	}

	@Override
	public void extractRenderState(T boss, AnimatedBossRenderState state, float partialTick) {
		super.extractRenderState(boss, state, partialTick);
		for (int i = 0; i < state.states.length; i++) {
			state.states[i].copyFrom(boss.states[i]);
			state.speeds[i] = boss.speed(i);
		}
		state.warning = boss.warning();
		state.acting = boss.acting();
		state.partHidden = boss.partHidden();
		state.hasRedOverlay = boss.hurtTime > 0;
	}

	@Override
	protected void scale(AnimatedBossRenderState state, PoseStack poseStack) {
		poseStack.scale(scale, scale, scale);
	}

	@Override
	protected float getFlipDegrees() {
		return 0;
	}

	/** A fast white pulse: block now. */
	@Override
	protected float getWhiteOverlayProgress(AnimatedBossRenderState state) {
		return state.warning == BossEntity.Warning.WHITE ? 0.3f + 0.22f * Mth.sin(state.ageInTicks * 1.6f) : 0;
	}

	/** A purple pulse: this one can't be blocked. */
	@Override
	protected int getModelTint(AnimatedBossRenderState state) {
		if (state.warning != BossEntity.Warning.PURPLE) {
			return -1;
		}
		float t = 0.5f + 0.5f * Mth.sin(state.ageInTicks * 1.2f);
		return ARGB.colorFromFloat(1, 0.75f + 0.15f * t, 0.35f + 0.2f * t, 1);
	}

	@Override
	public Identifier getTextureLocation(AnimatedBossRenderState state) {
		return texture;
	}
}
