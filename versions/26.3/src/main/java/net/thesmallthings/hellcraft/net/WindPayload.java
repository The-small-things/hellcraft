package net.thesmallthings.hellcraft.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.thesmallthings.hellcraft.HellcraftMod;

/** The seed of this world's wind streams in Lust (world/WindStreams), so the client blows the same ones as the server. */
public record WindPayload(long seed) implements CustomPacketPayload {
	public static final Type<WindPayload> TYPE = new Type<>(HellcraftMod.id("wind"));
	public static final StreamCodec<ByteBuf, WindPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.LONG, WindPayload::seed,
			WindPayload::new);

	@Override
	public Type<WindPayload> type() {
		return TYPE;
	}
}
