package net.thesmallthings.hellcraft.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.thesmallthings.hellcraft.world.Paradiso;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * Paradiso: the End's biome source also lists the nine spheres (so the chunk generator knows their features
 * and /locate finds them). Added whenever the list is read, not when it is first built: the End's source is
 * made before the server can look the spheres up.
 */
@Mixin(BiomeSource.class)
public abstract class BiomeSourceMixin {
	@Inject(method = "possibleBiomes", at = @At("RETURN"), cancellable = true)
	private void hellcraft$spheres(CallbackInfoReturnable<Set<Holder<Biome>>> cir) {
		if ((Object) this instanceof TheEndBiomeSource) {
			cir.setReturnValue(Paradiso.withSpheres(cir.getReturnValue()));
		}
	}
}
