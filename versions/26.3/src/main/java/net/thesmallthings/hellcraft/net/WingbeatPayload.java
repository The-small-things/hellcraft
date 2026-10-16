package net.thesmallthings.hellcraft.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.thesmallthings.hellcraft.HellcraftMod;

/** Lucifer's wings beat (hazard/Wingbeat): how hard it reaches this player, 0 to 1 (the client shakes and frosts). */
public record WingbeatPayload(float strength) implements CustomPacketPayload {
	public static final Type<WingbeatPayload> TYPE = new Type<>(HellcraftMod.id("wingbeat"));
	public static final StreamCodec<ByteBuf, WingbeatPayload> CODEC = StreamCodec.composite(ByteBufCodecs.FLOAT, WingbeatPayload::strength,
			WingbeatPayload::new);

	@Override
	public Type<WingbeatPayload> type() {
		return TYPE;
	}
}
