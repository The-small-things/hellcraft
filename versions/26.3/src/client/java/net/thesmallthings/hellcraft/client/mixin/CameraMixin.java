package net.thesmallthings.hellcraft.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.client.cutscene.CutscenePlayer;
import net.thesmallthings.hellcraft.client.layer.WingbeatFx;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** During a cutscene the camera follows its path (CutscenePlayer), seeing the player from outside; a wingbeat shakes it. */
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
	public abstract Quaternionf rotation();

	@Shadow
	@Final
	private Vector3f up;

	@Shadow
	@Final
	private Vector3f left;

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
			if (frame.roll() != 0) {
				// turned over (the climb out of Hell, past the centre of the earth)
				rotation().rotateZ((float) Math.toRadians(frame.roll()));
				new Vector3f(0, 1, 0).rotate(rotation(), up);
				new Vector3f(-1, 0, 0).rotate(rotation(), left);
			}
			detached = true;
			return;
		}
		// Lucifer's wingbeat throws the view about for a moment (the player's own aim is untouched)
		float[] shake = WingbeatFx.shake(partialTicks);
		if (shake[0] != 0 || shake[1] != 0) {
			setRotation(yRot() + shake[0], xRot() + shake[1]);
		}
	}
}
