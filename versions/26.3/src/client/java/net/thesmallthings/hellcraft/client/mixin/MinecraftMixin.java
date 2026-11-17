package net.thesmallthings.hellcraft.client.mixin;

import net.minecraft.client.Minecraft;
import net.thesmallthings.hellcraft.client.combat.CombatClient;
import net.thesmallthings.hellcraft.client.combat.SwingControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hellcraft's combat reads the attack key before vanilla does each tick, and takes right-clicks vanilla has no use for. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	/** A right-click nothing used raises a weapon's guard (combat/CombatClient.afterUse). */
	@Inject(method = "startUseItem", at = @At("TAIL"))
	private void hellcraft$guard(CallbackInfo ci) {
		CombatClient.afterUse((Minecraft) (Object) this);
	}

	/** With a weapon that has moves, a click of the attack key is a swing (combat/SwingControl), not vanilla's poke. */
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void hellcraft$swing(CallbackInfoReturnable<Boolean> cir) {
		if (SwingControl.click((Minecraft) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	/** Mid-swing the held key doesn't dig at the block behind the target. */
	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void hellcraft$noDigging(boolean down, CallbackInfo ci) {
		if (SwingControl.busy()) {
			ci.cancel();
		}
	}

	@Inject(method = "handleKeybinds", at = @At("HEAD"))
	private void hellcraft$combatKeys(CallbackInfo ci) {
		CombatClient.beforeKeys((Minecraft) (Object) this);
	}
}
