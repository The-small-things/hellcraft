package net.thesmallthings.hellcraft.client.mixin;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.thesmallthings.hellcraft.client.combat.anim.PlayerAnimator;
import net.thesmallthings.hellcraft.client.combat.anim.PlayerRig;
import net.thesmallthings.hellcraft.client.combat.anim.RigPose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Each frame a player is drawn, their combat pose at that very moment goes onto their render state (PlayerRig.POSE). */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("TAIL"))
	private void hellcraft$combatPose(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		RigPose pose = null;
		PlayerAnimator animator = PlayerAnimator.get(entity.getId());
		if (animator != null) {
			RigPose sampled = new RigPose();
			if (animator.sample(partialTicks, sampled)) {
				pose = sampled;
			}
		}
		state.setData(PlayerRig.POSE, pose);
	}
}
