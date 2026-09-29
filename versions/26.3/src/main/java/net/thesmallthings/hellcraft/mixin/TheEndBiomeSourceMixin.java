package net.thesmallthings.hellcraft.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.thesmallthings.hellcraft.world.Paradiso;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.stream.Stream;

/**
 * Paradiso: the vanilla End keeps its dragon's island (the_end), but every outer island takes the biome of
 * its heavenly sphere, ring by ring (ParadisoGeometry). Works on existing worlds, for chunks not yet made.
 */
@Mixin(TheEndBiomeSource.class)
public abstract class TheEndBiomeSourceMixin {
	@Shadow
	@Final
	private Holder<Biome> end;

	@Inject(method = "getNoiseBiome", at = @At("RETURN"), cancellable = true)
	private void hellcraft$sphere(int x, int y, int z, Climate.Sampler sampler, CallbackInfoReturnable<Holder<Biome>> cir) {
		if (cir.getReturnValue() == end) {
			return;
		}
		Holder<Biome> sphere = Paradiso.biome(QuartPos.toBlock(x), QuartPos.toBlock(z));
		if (sphere != null) {
			cir.setReturnValue(sphere);
		}
	}

	@Inject(method = "collectPossibleBiomes", at = @At("RETURN"), cancellable = true)
	private void hellcraft$spheres(CallbackInfoReturnable<Stream<Holder<Biome>>> cir) {
		cir.setReturnValue(Stream.concat(cir.getReturnValue(), Paradiso.biomes().stream()));
	}
}
