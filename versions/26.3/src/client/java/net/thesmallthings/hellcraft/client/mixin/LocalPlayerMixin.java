package net.thesmallthings.hellcraft.client.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.thesmallthings.hellcraft.combat.Guard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A weapon's guard slows you less than a shield does: half speed instead of a fifth (vanilla slows whoever is "using an
 * item" by the item's own number, and a sword has none, so it would get the slowest).
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	@Inject(method = "itemUseSpeedMultiplier", at = @At("HEAD"), cancellable = true)
	private void hellcraft$guardPace(CallbackInfoReturnable<Float> cir) {
		if (Guard.guarding((LocalPlayer) (Object) this)) {
			cir.setReturnValue(0.5f);
		}
	}
}
