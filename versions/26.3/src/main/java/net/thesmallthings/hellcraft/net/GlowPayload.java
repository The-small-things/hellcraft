package net.thesmallthings.hellcraft.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.thesmallthings.hellcraft.HellcraftMod;

import java.util.List;

/** Which entities have a faint glow (hazard/Glow: the onlookers, the Keys of Peter's holder), for the client to light them (layer/Glow). */
public record GlowPayload(List<Integer> ids) implements CustomPacketPayload {
	public static final Type<GlowPayload> TYPE = new Type<>(HellcraftMod.id("glow"));
	public static final StreamCodec<ByteBuf, GlowPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), GlowPayload::ids,
			GlowPayload::new);

	@Override
	public Type<GlowPayload> type() {
		return TYPE;
	}
}
