package net.thesmallthings.hellcraft.mixin;

import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.component.ResolvableProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Mannequins keep their skin, stillness and label behind private setters: the onlookers (hazard/Onlookers) need them. */
@Mixin(Mannequin.class)
public interface MannequinAccess {
	@Invoker("setProfile")
	void hellcraft$setProfile(ResolvableProfile profile);

	@Invoker("setImmovable")
	void hellcraft$setImmovable(boolean immovable);

	@Invoker("setHideDescription")
	void hellcraft$setHideDescription(boolean hide);
}
