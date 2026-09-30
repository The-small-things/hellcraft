package net.thesmallthings.hellcraft.mixin;

import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Item frames have no setter for "fixed" (unbreakable, unturnable): the white room needs it. */
@Mixin(ItemFrame.class)
public interface ItemFrameAccess {
	@Accessor("fixed")
	void hellcraft$setFixed(boolean fixed);
}
