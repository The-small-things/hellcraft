package net.thesmallthings.hellcraft.client.mixin;

import net.minecraft.resources.Identifier;
import net.thesmallthings.hellcraft.client.BloodHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Draws the health bar with Hellcraft's blood hearts (see BloodHud). */
@Mixin(targets = "net.minecraft.client.gui.Hud$HeartType")
public abstract class HeartTypeMixin {
	@Inject(method = "getSprite", at = @At("RETURN"), cancellable = true)
	private void hellcraft$bloodHearts(boolean hardcore, boolean halfHeart, boolean blinking, CallbackInfoReturnable<Identifier> cir) {
		Identifier blood = BloodHud.swap(cir.getReturnValue());
		if (blood != null) {
			cir.setReturnValue(blood);
		}
	}
}
