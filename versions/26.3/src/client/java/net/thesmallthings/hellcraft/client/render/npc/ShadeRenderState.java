package net.thesmallthings.hellcraft.client.render.npc;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.AnimationState;

public class ShadeRenderState extends LivingEntityRenderState {
	public final AnimationState idle = new AnimationState();
	public final AnimationState talk = new AnimationState();
}
