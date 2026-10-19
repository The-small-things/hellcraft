package net.thesmallthings.hellcraft.client.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import net.thesmallthings.hellcraft.client.layer.LimboGrey;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Hellcraft's own screen effects, after the game's: Limbo's grey (client/layer/LimboGrey). */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Shadow
	@Final
	private List<Identifier> requestedPostEffects;

	@Inject(method = "update", at = @At("TAIL"))
	private void hellcraft$postEffects(DeltaTracker deltaTracker, CallbackInfo ci) {
		Identifier limbo = LimboGrey.effect(Minecraft.getInstance());
		if (limbo != null) {
			requestedPostEffects.add(limbo);
		}
	}
}
