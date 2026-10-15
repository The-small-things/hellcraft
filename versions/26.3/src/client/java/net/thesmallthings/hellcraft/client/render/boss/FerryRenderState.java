package net.thesmallthings.hellcraft.client.render.boss;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.AnimationState;

public class FerryRenderState extends EntityRenderState {
	public final AnimationState bob = new AnimationState();
	public float yaw;
}
