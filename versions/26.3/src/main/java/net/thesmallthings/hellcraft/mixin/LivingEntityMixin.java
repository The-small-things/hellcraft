package net.thesmallthings.hellcraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.thesmallthings.hellcraft.combat.Guard;
import net.thesmallthings.hellcraft.hazard.BossRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

	/** A weapon's guard (combat/Guard) turns aside its share of the blow; shields are left to vanilla. */
	@Inject(method = "applyItemBlocking", at = @At("HEAD"), cancellable = true)
	private void hellcraft$weaponGuard(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Float> cir) {
		float blocked = Guard.block((LivingEntity) (Object) this, level, source, damage);
		if (blocked >= 0) {
			cir.setReturnValue(blocked);
		}
	}

	/** A blow taken on a shield (not a weapon's guard, which returns early above): those watching see the player rock back. */
	@Inject(method = "applyItemBlocking", at = @At("RETURN"))
	private void hellcraft$shieldHit(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Float> cir) {
		if (cir.getReturnValueF() > 0 && (Object) this instanceof net.minecraft.server.level.ServerPlayer player) {
			net.thesmallthings.hellcraft.combat.Combat.show(player, net.thesmallthings.hellcraft.entity.boss.gen.PlayerMovesAnim.SHIELD_HIT, 0, 0, 1.0f, true);
		}
	}

	/** A parry lets nothing through, so nothing shows: no red flash and tilted view... */
	@WrapOperation(method = "hurtServer", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerLevel;broadcastDamageEvent(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;)V"))
	private void hellcraft$parryNoFlinch(ServerLevel level, Entity entity, DamageSource source, Operation<Void> original) {
		if (!Guard.turnedWhole((LivingEntity) (Object) this)) {
			original.call(level, entity, source);
		}
	}

	/** ...and no cry of pain. */
	@WrapOperation(method = "hurtServer", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;playHurtSound(Lnet/minecraft/world/damagesource/DamageSource;)V"))
	private void hellcraft$parryNoCry(LivingEntity self, DamageSource source, Operation<Void> original) {
		if (!Guard.turnedWhole(self)) {
			original.call(self, source);
		}
	}

	@WrapOperation(method = "applyItemBlocking", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;getItemBlockingWith()Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack hellcraft$quickBlock(LivingEntity self, Operation<ItemStack> original, @Local(argsOnly = true) DamageSource source) {
		ItemStack blocking = original.call(self);
		return blocking != null ? blocking : BossRules.raisingShieldAgainst(self, source);
	}
}
