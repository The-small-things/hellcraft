package net.thesmallthings.hellcraft.client.render.boss;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.AnimationState;
import net.thesmallthings.hellcraft.entity.boss.BossEntity;
import net.thesmallthings.hellcraft.entity.boss.gen.PlutusAnim;

public class PlutusRenderState extends LivingEntityRenderState {
	public final AnimationState[] states = new AnimationState[PlutusAnim.values().length];
	public BossEntity.Warning warning = BossEntity.Warning.NONE;
	public boolean acting;

	public PlutusRenderState() {
		for (int i = 0; i < states.length; i++) {
			states[i] = new AnimationState();
		}
	}
}
