package net.thesmallthings.hellcraft.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.world.entity.HumanoidArm;
import net.thesmallthings.hellcraft.client.combat.anim.Bone;
import net.thesmallthings.hellcraft.client.combat.anim.PlayerAnimator;
import net.thesmallthings.hellcraft.client.combat.anim.RigPose;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hellcraft's combat moves in first person: once vanilla has put the held item at the hand's place on screen, the move's
 * "fp" bone (tools/art/player_moves.py) shifts and turns it from there: the same clip, at the same moment, that the body
 * plays for everyone else. Mirrored for a left-handed player.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonHandsMixin {
	@Unique
	private final RigPose hellcraft$pose = new RigPose();

	@Inject(method = "applyItemArmTransform", at = @At("TAIL"))
	private void hellcraft$combatMove(PoseStack poseStack, HumanoidArm arm, float inverseArmHeight, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || arm != mc.player.getMainArm()) {
			return;
		}
		PlayerAnimator animator = PlayerAnimator.get(mc.player.getId());
		if (animator == null) {
			return;
		}
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		if (!animator.sample(partial, hellcraft$pose)) {
			return;
		}
		float w = hellcraft$pose.weight(Bone.FP);
		if (w <= 0) {
			return;
		}
		int side = arm == HumanoidArm.RIGHT ? 1 : -1;
		Vector3f shift = hellcraft$pose.position[Bone.FP.ordinal()];
		poseStack.translate(side * shift.x * w / 16.0f, shift.y * w / 16.0f, shift.z * w / 16.0f);
		Quaternionf turn = new Quaternionf().slerp(hellcraft$pose.rotation[Bone.FP.ordinal()], w);
		if (side < 0) {
			turn.set(turn.x, -turn.y, -turn.z, turn.w);
		}
		poseStack.rotate(turn);
	}
}
