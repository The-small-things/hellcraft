package net.thesmallthings.hellcraft.client.render.boss;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.AnimationState;
import net.thesmallthings.hellcraft.entity.boss.BossEntity;
import net.thesmallthings.hellcraft.entity.boss.gen.PhlegyasAnim;

public class PhlegyasRenderState extends LivingEntityRenderState {
	public final AnimationState[] states = new AnimationState[PhlegyasAnim.values().length];
	public BossEntity.Warning warning = BossEntity.Warning.NONE;
	/** How fast each animation plays (BossEntity.speedOf). */
	public final float[] speeds = new float[states.length];

	public PhlegyasRenderState() {
		for (int i = 0; i < states.length; i++) {
			states[i] = new AnimationState();
		}
	}
}
