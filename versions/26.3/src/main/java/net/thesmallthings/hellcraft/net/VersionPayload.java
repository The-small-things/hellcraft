package net.thesmallthings.hellcraft.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.thesmallthings.hellcraft.HellcraftMod;

/**
 * The Hellcraft version, sent both ways while a player joins: the server sends its own, and the client
 * answers with the version it has installed (see {@link JoinCheck}).
 */
public record VersionPayload(String version) implements CustomPacketPayload {
	public static final Type<VersionPayload> TYPE = new Type<>(HellcraftMod.id("version"));
	public static final StreamCodec<ByteBuf, VersionPayload> CODEC = ByteBufCodecs.STRING_UTF8.map(VersionPayload::new, VersionPayload::version);

	@Override
	public Type<VersionPayload> type() {
		return TYPE;
	}
}
