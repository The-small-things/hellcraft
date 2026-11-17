package net.thesmallthings.hellcraft.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.thesmallthings.hellcraft.HellcraftMod;

/**
 * The client begins a swing (combat/Swing): which move (a PlayerMovesAnim ordinal). It has already started playing it;
 * the server checks it is a move the held weapon has and that it may follow the last one, and lands its blow when the
 * animation does.
 */
public record SwingPayload(int move) implements CustomPacketPayload {
	public static final Type<SwingPayload> TYPE = new Type<>(HellcraftMod.id("swing"));
	public static final StreamCodec<ByteBuf, SwingPayload> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, SwingPayload::move, SwingPayload::new);

	@Override
	public Type<SwingPayload> type() {
		return TYPE;
	}
}
