package net.thesmallthings.hellcraft.client.render.boss;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.AnimationState;
import net.thesmallthings.hellcraft.entity.boss.BossEntity;

/** What AnimatedBossRenderer draws from: every animation's state, the danger warning, and whether it's busy. */
public class AnimatedBossRenderState extends LivingEntityRenderState {
	public final AnimationState[] states;
	public BossEntity.Warning warning = BossEntity.Warning.NONE;
	public boolean acting;
	/** How fast each animation plays (BossEntity.speedOf). */
	public final float[] speeds;

	public AnimatedBossRenderState(int animations) {
		states = new AnimationState[animations];
		speeds = new float[animations];
		for (int i = 0; i < states.length; i++) {
			states[i] = new AnimationState();
		}
	}
}
