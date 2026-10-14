package net.thesmallthings.hellcraft.client.render.boss;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.AnimationState;
import net.thesmallthings.hellcraft.entity.boss.BossEntity;
import net.thesmallthings.hellcraft.entity.boss.gen.MinosAnim;

public class MinosRenderState extends LivingEntityRenderState {
	public final AnimationState[] states = new AnimationState[MinosAnim.values().length];
	public BossEntity.Warning warning = BossEntity.Warning.NONE;

	public MinosRenderState() {
		for (int i = 0; i < states.length; i++) {
			states[i] = new AnimationState();
		}
	}
}
