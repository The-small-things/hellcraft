package net.thesmallthings.hellcraft.mixin;

import net.minecraft.world.entity.player.Player;
import net.thesmallthings.hellcraft.combat.Swing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While one of Hellcraft's swings lands its blow (combat/Swing calls vanilla's own attack for each thing in the arc), the
 * blow is at full strength (the move, not the cooldown, decides what it does) and vanilla's sweep stays out of it (the
 * move's arc is the sweep).
 */
@Mixin(Player.class)
public abstract class PlayerMixin {
	@Inject(method = "getAttackStrengthScale", at = @At("HEAD"), cancellable = true)
	private void hellcraft$fullStrength(float a, CallbackInfoReturnable<Float> cir) {
		if (Swing.landing((Player) (Object) this)) {
			cir.setReturnValue(1.0f);
		}
	}

	@Inject(method = "isSweepAttack", at = @At("HEAD"), cancellable = true)
	private void hellcraft$noSweep(boolean fullStrengthAttack, boolean criticalAttack, boolean knockbackAttack, CallbackInfoReturnable<Boolean> cir) {
		if (Swing.landing((Player) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
