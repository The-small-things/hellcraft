package net.thesmallthings.hellcraft.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.thesmallthings.hellcraft.world.Paradiso;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Paradiso: the vanilla End keeps its dragon's island (the_end), but every outer island takes the biome of
 * its heavenly sphere, ring by ring (ParadisoGeometry). Works on existing worlds, for chunks not yet made.
 * Wraps the resolver rather than getNoiseBiome itself: Fabric API's End-biome hook answers that method early,
 * so a hook on its return never runs.
 */
@Mixin(TheEndBiomeSource.class)
public abstract class TheEndBiomeSourceMixin {
	@Inject(method = "createResolver", at = @At("RETURN"), cancellable = true)
	private void hellcraft$spheres(Climate.Sampler sampler, CallbackInfoReturnable<BiomeResolver> cir) {
		BiomeResolver vanilla = cir.getReturnValue();
		cir.setReturnValue((x, y, z) -> {
			Holder<Biome> biome = vanilla.getNoiseBiome(x, y, z);
			if (biome.is(Biomes.THE_END)) {
				return biome;
			}
			Holder<Biome> sphere = Paradiso.biome(QuartPos.toBlock(x), QuartPos.toBlock(z));
			return sphere != null ? sphere : biome;
		});
	}
}
