package net.thesmallthings.hellcraft.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.thesmallthings.hellcraft.client.title.HellTitle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every menu's panorama of Hell is darkened, lit red from below and full of rising embers (HellTitle.underworld). */
@Mixin(Screen.class)
public abstract class ScreenMixin {
	@Shadow
	public int width;
	@Shadow
	public int height;

	@Inject(method = "extractPanorama", at = @At("TAIL"))
	private void hellcraft$underworld(GuiGraphicsExtractor graphics, float a, CallbackInfo ci) {
		HellTitle.underworld(graphics, width, height);
	}
}
