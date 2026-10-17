package net.thesmallthings.hellcraft.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.thesmallthings.hellcraft.HellcraftMod;

/**
 * A fighter's style in a boss fight, for the style panel (StyleMeter on the server, StylePanel on the client):
 * whether a fight is on, the rank (Judgement.Grade ordinal), how far into it the meter is (0 to 1), and what just
 * scored or cost ("" when nothing new).
 */
public record StylePayload(boolean active, int grade, float progress, String event) implements CustomPacketPayload {
	public static final Type<StylePayload> TYPE = new Type<>(HellcraftMod.id("style"));
	public static final StreamCodec<ByteBuf, StylePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, StylePayload::active,
			ByteBufCodecs.VAR_INT, StylePayload::grade,
			ByteBufCodecs.FLOAT, StylePayload::progress,
			ByteBufCodecs.STRING_UTF8, StylePayload::event,
			StylePayload::new);

	@Override
	public Type<StylePayload> type() {
		return TYPE;
	}
}
