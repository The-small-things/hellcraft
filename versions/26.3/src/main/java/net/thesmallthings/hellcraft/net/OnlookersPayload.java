package net.thesmallthings.hellcraft.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.thesmallthings.hellcraft.HellcraftMod;

import java.util.List;

/** Which entities are onlookers (hazard/Onlookers), so the client can light them with a faint glow (layer/OnlookerGlow). */
public record OnlookersPayload(List<Integer> ids) implements CustomPacketPayload {
	public static final Type<OnlookersPayload> TYPE = new Type<>(HellcraftMod.id("onlookers"));
	public static final StreamCodec<ByteBuf, OnlookersPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), OnlookersPayload::ids,
			OnlookersPayload::new);

	@Override
	public Type<OnlookersPayload> type() {
		return TYPE;
	}
}
