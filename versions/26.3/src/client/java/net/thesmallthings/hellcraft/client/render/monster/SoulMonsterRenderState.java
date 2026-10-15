package net.thesmallthings.hellcraft.client.render.monster;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.AnimationState;

public class SoulMonsterRenderState extends LivingEntityRenderState {
	public final AnimationState idle = new AnimationState();
	public final AnimationState attack = new AnimationState();
	public final AnimationState hurt = new AnimationState();
	public final AnimationState death = new AnimationState();
	public final AnimationState special = new AnimationState();
}
