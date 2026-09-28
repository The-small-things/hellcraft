package net.thesmallthings.hellcraft.mixin;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.server.network.ServerHandshakePacketListenerImpl;
import net.thesmallthings.hellcraft.music.MusicPack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Remembers the address each client typed to connect (it's in the handshake), so the Hellcraft
 * resource pack can be offered from that same address without any configuration.
 */
@Mixin(ServerHandshakePacketListenerImpl.class)
public abstract class HandshakeHostMixin {
	@Shadow
	@Final
	private Connection connection;

	@Inject(method = "handleIntention", at = @At("HEAD"))
	private void hellcraft$rememberHost(ClientIntentionPacket packet, CallbackInfo ci) {
		MusicPack.rememberHost(connection.getRemoteAddress(), packet.hostName());
	}
}
