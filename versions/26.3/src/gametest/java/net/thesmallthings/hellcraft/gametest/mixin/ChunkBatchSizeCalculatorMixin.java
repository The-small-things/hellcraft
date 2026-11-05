package net.thesmallthings.hellcraft.gametest.mixin;

import net.minecraft.client.multiplayer.ChunkBatchSizeCalculator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * For the showcase pictures only (HELLCRAFT_SHOWCASE): the client asks the server for chunks as fast as it may send them.
 * The client times each batch of chunks to set that rate, and in a client test the time includes the pauses the test
 * framework holds the game in, so it asks for a trickle and a far view never fills in.
 */
@Mixin(ChunkBatchSizeCalculator.class)
public class ChunkBatchSizeCalculatorMixin {
	private static final boolean SHOWCASE = System.getenv("HELLCRAFT_SHOWCASE") != null;

	@Inject(method = "getDesiredChunksPerTick", at = @At("HEAD"), cancellable = true)
	private void hellcraft$fullSpeed(CallbackInfoReturnable<Float> cir) {
		if (SHOWCASE) {
			cir.setReturnValue(64.0f);
		}
	}
}
