package net.thesmallthings.hellcraft.client.render.npc;

import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.client.render.gen.DamnedAnimations;
import net.thesmallthings.hellcraft.entity.npc.Damned;

import java.util.List;

/**
 * A damned soul's jointed model (generated: DamnedMesh) moved by its animations (DamnedAnimations): the punishment's
 * own loop with a scream, a sigh or a flinch played over it (keyframes add up). Its props show only where the
 * punishment calls for them: Greed's weight, the Vestibule leader's banner, Malebolge's gilded hood and cloak.
 */
public class DamnedModel extends EntityModel<DamnedRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(HellcraftMod.id("damned"), "main");

	private final KeyframeAnimation[] animations;
	private final ModelPart weight;
	private final ModelPart banner;
	private final ModelPart hood;
	private final ModelPart cloak;

	public DamnedModel(ModelPart root) {
		super(root, RenderTypes::entityTranslucent);
		List<net.minecraft.client.animation.AnimationDefinition> all = DamnedAnimations.all();
		this.animations = new KeyframeAnimation[all.size()];
		for (int i = 0; i < animations.length; i++) {
			animations[i] = all.get(i).bake(root);
		}
		ModelPart r = root.getChild("root");
		ModelPart body = r.getChild("body");
		this.weight = r.getChild("weight");
		this.banner = body.getChild("right_arm").getChild("right_forearm").getChild("banner");
		this.hood = body.getChild("head").getChild("hood");
		this.cloak = body.getChild("cloak");
	}

	@Override
	public void setupAnim(DamnedRenderState state) {
		super.setupAnim(state);
		weight.visible = state.punishment == Damned.Punishment.GREED;
		banner.visible = state.leader;
		boolean hypocrite = state.punishment == Damned.Punishment.MALEBOLGE;
		hood.visible = hypocrite;
		cloak.visible = hypocrite;
		for (int i = 0; i < animations.length; i++) {
			animations[i].apply(state.states[i], state.ageInTicks);
		}
	}
}
