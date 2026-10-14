package net.thesmallthings.hellcraft.client.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.thesmallthings.hellcraft.client.cutscene.CutscenePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The cutscene's letterbox, captions and name card, drawn even while the rest of the HUD is hidden. */
@Mixin(Hud.class)
public abstract class HudMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void hellcraft$cutsceneOverlay(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		CutscenePlayer.drawOverlay(graphics, deltaTracker);
	}
}
