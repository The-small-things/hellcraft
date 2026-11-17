package net.thesmallthings.hellcraft.mixin;

import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.thesmallthings.hellcraft.combat.RollShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A rolling player's hitbox is small (combat/RollShape), whatever their pose. */
@Mixin(Avatar.class)
public abstract class AvatarMixin {
	@Inject(method = "getDefaultDimensions", at = @At("HEAD"), cancellable = true)
	private void hellcraft$rolling(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
		if ((Object) this instanceof Player player && pose != Pose.SLEEPING && pose != Pose.DYING && RollShape.small(player)) {
			cir.setReturnValue(RollShape.SHAPE);
		}
	}
}
