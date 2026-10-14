package net.thesmallthings.hellcraft.client.mixin;

import net.minecraft.client.MouseHandler;
import net.thesmallthings.hellcraft.client.cutscene.CutscenePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The mouse doesn't turn the player while a cutscene plays (the movement is dropped, not saved up). */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
	private void hellcraft$holdLook(double mousea, CallbackInfo ci) {
		if (CutscenePlayer.active()) {
			ci.cancel();
		}
	}
}
