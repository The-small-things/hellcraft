package net.thesmallthings.hellcraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.thesmallthings.hellcraft.hazard.BossRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Boss hits grow with the victim's hearts; arrows do less to bosses; and a shield raised against a boss's (or one of
 * Hell's monsters') blow blocks it at once, without vanilla's quarter-second wait (BossRules).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float hellcraft$bossRules(float amount, @Local(argsOnly = true) DamageSource source) {
		return BossRules.adjust((LivingEntity) (Object) this, source, amount);
	}

	@WrapOperation(method = "applyItemBlocking", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;getItemBlockingWith()Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack hellcraft$quickBlock(LivingEntity self, Operation<ItemStack> original, @Local(argsOnly = true) DamageSource source) {
		ItemStack blocking = original.call(self);
		return blocking != null ? blocking : BossRules.raisingShieldAgainst(self, source);
	}
}
