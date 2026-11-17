package net.thesmallthings.hellcraft.client.mixin;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.client.combat.anim.PlayerAnimator;
import net.thesmallthings.hellcraft.client.combat.anim.PlayerRig;
import net.thesmallthings.hellcraft.client.combat.anim.RigPose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Each frame a player is drawn, their pose at that very moment goes onto their render state (PlayerRig.POSE: getting about,
 * item stances, combat moves), with their animator for the PoseSmoother and their eased crouch.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("TAIL"))
	private void hellcraft$combatPose(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		RigPose pose = null;
		PlayerAnimator animator = PlayerAnimator.get(entity.getId());
		boolean moving = animator != null && animator.moving();
		if (animator != null) {
			RigPose sampled = new RigPose();
			if (animator.sampleBody(partialTicks, state, sampled)) {
				pose = sampled;
			}
		}
		state.setData(PlayerRig.POSE, pose);
		state.setData(PlayerRig.SMOOTH, moving ? animator : null);
		state.setData(PlayerRig.CROUCH, moving ? animator.motion.crouch(partialTicks) : null);
	}

	/** Crouching lowers the body by an eighth of a block: eased with the crouch instead of at once. */
	@Inject(method = "getRenderOffset(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Lnet/minecraft/world/phys/Vec3;",
			at = @At("RETURN"), cancellable = true)
	private void hellcraft$easedCrouch(AvatarRenderState state, CallbackInfoReturnable<Vec3> cir) {
		Float crouch = state.getData(PlayerRig.CROUCH);
		if (crouch != null) {
			float vanilla = state.isCrouching ? 1 : 0;
			cir.setReturnValue(cir.getReturnValue().add(0.0, state.scale * -2.0F / 16.0 * (crouch - vanilla), 0.0));
		}
	}
}
