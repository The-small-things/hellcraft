package net.thesmallthings.hellcraft.client.render.boss;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.AnimationState;

public class CoinRenderState extends EntityRenderState {
	public final AnimationState roll = new AnimationState();
	public float yaw;
}
