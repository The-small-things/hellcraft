package net.thesmallthings.hellcraft.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.client.cutscene.CutscenePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** During a cutscene the camera follows its path (CutscenePlayer), seeing the player from outside. */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private boolean detached;

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	public abstract Vec3 position();

	@Shadow
	public abstract float yRot();

	@Shadow
	public abstract float xRot();

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void hellcraft$cutscene(float partialTicks, CallbackInfo ci) {
		CutscenePlayer.Frame frame = CutscenePlayer.frame(partialTicks, position(), yRot(), xRot());
		if (frame != null) {
			setPosition(frame.pos());
			setRotation(frame.yaw(), frame.pitch());
			detached = true;
		}
	}
}
