package net.thesmallthings.hellcraft.client.render.monster;

import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.jetbrains.annotations.Nullable;

/**
 * The model of one of Hell's own monsters (entity/monster/SoulMonster): its generated mesh moved by its keyframe
 * animations (idle, walk by its speed, attack, hurt, death and its special), its head following its gaze.
 */
public class SoulMonsterModel extends EntityModel<SoulMonsterRenderState> {
	/** A monster's animations, as generated (tools/gen_bosses.py); walk and special may be missing. */
	public record Anims(AnimationDefinition idle, @Nullable AnimationDefinition walk, AnimationDefinition attack, AnimationDefinition hurt,
						AnimationDefinition death, @Nullable AnimationDefinition special) {
	}

	@Nullable
	private final ModelPart head;
	private final KeyframeAnimation idle;
	@Nullable
	private final KeyframeAnimation walk;
	private final KeyframeAnimation attack;
	private final KeyframeAnimation hurt;
	private final KeyframeAnimation death;
	@Nullable
	private final KeyframeAnimation special;

	/** headPath: the bones from the root down to the head (none for no head to turn); translucent: see-through. */
	public SoulMonsterModel(ModelPart root, Anims anims, boolean translucent, String... headPath) {
		super(root, translucent ? RenderTypes::entityTranslucent : RenderTypes::entityCutout);
		ModelPart h = headPath.length == 0 ? null : root;
		for (String bone : headPath) {
			h = h.getChild(bone);
		}
		this.head = h;
		this.idle = anims.idle().bake(root);
		this.walk = anims.walk() == null ? null : anims.walk().bake(root);
		this.attack = anims.attack().bake(root);
		this.hurt = anims.hurt().bake(root);
		this.death = anims.death().bake(root);
		this.special = anims.special() == null ? null : anims.special().bake(root);
	}

	@Override
	public void setupAnim(SoulMonsterRenderState state) {
		super.setupAnim(state);
		if (head != null) {
			head.yRot += state.yRot * ((float) Math.PI / 180f);
			head.xRot += state.xRot * ((float) Math.PI / 180f);
		}
		if (state.deathTime > 0) {
			death.apply(state.death, state.ageInTicks);
			return;
		}
		if (walk != null) {
			walk.applyWalk(state.walkAnimationPos, state.walkAnimationSpeed, 1.5f, 2.5f);
		}
		idle.apply(state.idle, state.ageInTicks);
		if (special != null) {
			special.apply(state.special, state.ageInTicks);
		}
		attack.apply(state.attack, state.ageInTicks);
		hurt.apply(state.hurt, state.ageInTicks);
	}
}
