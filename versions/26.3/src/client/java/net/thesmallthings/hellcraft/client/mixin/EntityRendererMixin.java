package net.thesmallthings.hellcraft.client.mixin;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.thesmallthings.hellcraft.client.layer.OnlookerGlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Onlookers are drawn with a faint light of their own (layer/OnlookerGlow). */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
	@Inject(method = "getBlockLightLevel", at = @At("RETURN"), cancellable = true)
	private void hellcraft$onlookerGlow(Entity entity, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		if (entity instanceof Mannequin && cir.getReturnValueI() < OnlookerGlow.LIGHT && OnlookerGlow.glows(entity.getId())) {
			cir.setReturnValue(OnlookerGlow.LIGHT);
		}
	}
}
