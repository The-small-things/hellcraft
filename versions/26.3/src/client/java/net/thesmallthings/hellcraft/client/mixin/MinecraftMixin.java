package net.thesmallthings.hellcraft.client.mixin;

import net.minecraft.client.Minecraft;
import net.thesmallthings.hellcraft.client.combat.CombatClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hellcraft's combat reads the attack key before vanilla does each tick, and takes right-clicks vanilla has no use for. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	/** A right-click nothing used raises a weapon's guard (combat/CombatClient.afterUse). */
	@Inject(method = "startUseItem", at = @At("TAIL"))
	private void hellcraft$guard(CallbackInfo ci) {
		CombatClient.afterUse((Minecraft) (Object) this);
	}

	@Inject(method = "handleKeybinds", at = @At("HEAD"))
	private void hellcraft$combatKeys(CallbackInfo ci) {
		CombatClient.beforeKeys((Minecraft) (Object) this);
	}
}
