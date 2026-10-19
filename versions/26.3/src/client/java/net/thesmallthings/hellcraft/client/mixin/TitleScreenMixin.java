package net.thesmallthings.hellcraft.client.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.thesmallthings.hellcraft.client.title.HellTitle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hellcraft's title screen (client/title/HellTitle): its logo, its splash and its version; Minecraft's buttons stay. */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
	@Shadow
	private SplashRenderer splash;

	protected TitleScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At("HEAD"))
	private void hellcraft$pickSplash(CallbackInfo ci) {
		if (splash == null) {
			splash = new SplashRenderer(HellTitle.splash());
		}
	}

	@Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/LogoRenderer;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IF)V"))
	private void hellcraft$logo(LogoRenderer logo, GuiGraphicsExtractor graphics, int width, float alpha) {
		HellTitle.logo(graphics, width, logo.keepLogoThroughFade() ? 1.0F : alpha);
	}

	/** The splash sits a little higher, clear of the gate's words under the logo. */
	@Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/SplashRenderer;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;ILnet/minecraft/client/gui/Font;F)V"))
	private void hellcraft$splash(SplashRenderer splash, GuiGraphicsExtractor graphics, int width, Font font, float alpha) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(8, -22);
		splash.extractRenderState(graphics, width, font, alpha);
		graphics.pose().popMatrix();
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void hellcraft$version(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		HellTitle.version(graphics, height, 1.0F);
	}
}
