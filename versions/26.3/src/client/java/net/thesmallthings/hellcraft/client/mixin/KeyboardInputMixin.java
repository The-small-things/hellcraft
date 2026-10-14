package net.thesmallthings.hellcraft.client.mixin;

import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.thesmallthings.hellcraft.client.cutscene.CutscenePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The player stands still while a cutscene plays. */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {
	@Inject(method = "tick", at = @At("TAIL"))
	private void hellcraft$holdStill(CallbackInfo ci) {
		if (CutscenePlayer.active()) {
			keyPresses = Input.EMPTY;
			moveVector = Vec2.ZERO;
		}
	}
}
