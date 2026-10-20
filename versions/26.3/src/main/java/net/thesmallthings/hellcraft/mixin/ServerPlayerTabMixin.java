package net.thesmallthings.hellcraft.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.thesmallthings.hellcraft.blood.Sins;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Minos's verdict after each player's name in the tab list (grey "Heretic"; judgementTitles turns it off). */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerTabMixin {
	@Inject(method = "getTabListDisplayName", at = @At("HEAD"), cancellable = true)
	private void hellcraft$judgement(CallbackInfoReturnable<Component> cir) {
		Component name = Sins.tabName((ServerPlayer) (Object) this);
		if (name != null) {
			cir.setReturnValue(name);
		}
	}
}
