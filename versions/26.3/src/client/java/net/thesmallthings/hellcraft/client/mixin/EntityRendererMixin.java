package net.thesmallthings.hellcraft.client.mixin;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.Player;
import net.thesmallthings.hellcraft.client.layer.Glow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Onlookers and the Keys of Peter's holder are drawn with a faint light of their own (layer/Glow). */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
	@Inject(method = "getBlockLightLevel", at = @At("RETURN"), cancellable = true)
	private void hellcraft$onlookerGlow(Entity entity, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		if ((entity instanceof Mannequin || entity instanceof Player) && cir.getReturnValueI() < Glow.LIGHT && Glow.glows(entity.getId())) {
			cir.setReturnValue(Glow.LIGHT);
		}
	}
}
