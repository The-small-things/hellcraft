package net.thesmallthings.hellcraft.mixin;

import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.thesmallthings.hellcraft.music.MusicPack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells {@link MusicPack} whether a player accepted the boss music pack (runs on the server thread). */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ResourcePackResponseMixin {
	@Inject(method = "handleResourcePackResponse", at = @At("TAIL"))
	private void hellcraft$trackMusicPack(ServerboundResourcePackPacket packet, CallbackInfo ci) {
		if ((Object) this instanceof ServerGamePacketListenerImpl game) {
			MusicPack.onResponse(game.player, packet.id(), packet.action());
		}
	}
}
