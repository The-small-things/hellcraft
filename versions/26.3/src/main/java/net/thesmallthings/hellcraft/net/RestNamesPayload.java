package net.thesmallthings.hellcraft.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.thesmallthings.hellcraft.HellcraftMod;

import java.util.List;

/** The names this player gave Virgil's Rests (world/RestNames), for their Map of Hell and Journal: Rest ids and names, side by side. */
public record RestNamesPayload(List<String> ids, List<String> names) implements CustomPacketPayload {
	public static final Type<RestNamesPayload> TYPE = new Type<>(HellcraftMod.id("rest_names"));
	public static final StreamCodec<ByteBuf, RestNamesPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), RestNamesPayload::ids,
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), RestNamesPayload::names,
			RestNamesPayload::new);

	@Override
	public Type<RestNamesPayload> type() {
		return TYPE;
	}
}
