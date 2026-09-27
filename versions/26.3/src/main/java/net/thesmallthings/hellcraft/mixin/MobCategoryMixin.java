package net.thesmallthings.hellcraft.mixin;

import net.minecraft.world.entity.MobCategory;
import net.thesmallthings.hellcraft.config.HellConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hell is full: raises the hostile mob cap. */
@Mixin(MobCategory.class)
public abstract class MobCategoryMixin {
	@Inject(method = "getMaxInstancesPerChunk", at = @At("RETURN"), cancellable = true)
	private void hellcraft$hellIsFull(CallbackInfoReturnable<Integer> cir) {
		if ((Object) this == MobCategory.MONSTER) {
			cir.setReturnValue((int) Math.round(cir.getReturnValueI() * HellConfig.get().monsterCapMultiplier));
		}
	}
}
