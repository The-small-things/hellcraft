package net.thesmallthings.hellcraft.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.thesmallthings.hellcraft.blood.KeysOfPeter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Keys of Peter can't be put away: no slot takes them but their holder's own inventory (KeysOfPeter). */
@Mixin(Slot.class)
public class SlotMixin {
	@Shadow
	@Final
	public Container container;

	@Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
	private void hellcraft$keysStayWithYou(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (!(container instanceof Inventory) && KeysOfPeter.isKeys(stack)) {
			cir.setReturnValue(false);
		}
	}
}
