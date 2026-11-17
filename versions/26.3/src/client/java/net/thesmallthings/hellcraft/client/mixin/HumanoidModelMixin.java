package net.thesmallthings.hellcraft.client.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.thesmallthings.hellcraft.client.combat.anim.PlayerAnimator;
import net.thesmallthings.hellcraft.client.combat.anim.PlayerRig;
import net.thesmallthings.hellcraft.client.combat.anim.RigPose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hellcraft's combat moves on the player model: once vanilla has posed it, the pose carried on the render state (a swing,
 * a roll, a guard; combat/anim/PlayerRig) goes on top. Armour and held items pose through here too, so they follow.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
	private void hellcraft$combatPose(HumanoidRenderState state, CallbackInfo ci) {
		RigPose pose = state.getData(PlayerRig.POSE);
		if (pose != null) {
			PlayerRig.apply((HumanoidModel<?>) (Object) this, state, pose);
		}
		PlayerAnimator smoothing = state.getData(PlayerRig.SMOOTH);
		if (smoothing != null) {
			smoothing.smoother.apply((HumanoidModel<?>) (Object) this, state);
		}
	}
}
