package net.thesmallthings.hellcraft.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.thesmallthings.hellcraft.hazard.BossRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Boss hits grow with the victim's hearts; arrows do less to bosses (BossRules). */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float hellcraft$bossRules(float amount, @Local(argsOnly = true) DamageSource source) {
		return BossRules.adjust((LivingEntity) (Object) this, source, amount);
	}
}
