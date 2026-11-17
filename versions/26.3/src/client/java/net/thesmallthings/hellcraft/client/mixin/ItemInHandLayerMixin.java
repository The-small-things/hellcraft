package net.thesmallthings.hellcraft.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.thesmallthings.hellcraft.client.combat.anim.Bone;
import net.thesmallthings.hellcraft.client.combat.anim.PlayerRig;
import net.thesmallthings.hellcraft.client.combat.anim.RigPose;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The weapon turns in the hand during Hellcraft's combat moves (the rig's "item" bone, tools/art/player_moves.py): a
 * blade laid in line with the arm for a thrust, trailing behind it in a cut. Applied where the hand is, once the pose
 * stack has been carried to the arm and before vanilla lays the item in it.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/vertex/PoseStack;rotateDegrees(Lcom/mojang/math/Axis;F)V", ordinal = 0))
	private void hellcraft$grip(ArmedEntityRenderState state, ItemStackRenderState item, ItemStack stack, HumanoidArm arm, PoseStack poseStack,
								SubmitNodeCollector collector, int light, CallbackInfo ci) {
		RigPose pose = state.getData(PlayerRig.POSE);
		if (pose == null || arm != state.mainArm) {
			return;
		}
		float w = pose.weight(Bone.ITEM);
		if (w <= 0) {
			return;
		}
		Quaternionf turn = new Quaternionf().slerp(pose.rotation[Bone.ITEM.ordinal()], w);
		// the grip, in the arm's own space: a pixel in from the arm's outer side, nine down from the shoulder
		float side = arm == HumanoidArm.RIGHT ? -1 : 1;
		if (arm == HumanoidArm.LEFT) {
			turn.set(turn.x, -turn.y, -turn.z, turn.w);
		}
		poseStack.rotateAround(turn, side / 16.0f, 9.0f / 16.0f, 0.0f);
	}
}
