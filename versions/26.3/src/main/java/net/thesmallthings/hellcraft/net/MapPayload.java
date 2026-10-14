package net.thesmallthings.hellcraft.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.thesmallthings.hellcraft.HellcraftMod;

import java.util.List;

/**
 * What the compass, the circle cards and the Map of Hell need from the server: whether this overworld is the
 * Inferno, whether circle cards are on (circleTitles), the ids of the Virgil's Rests this player has found, and
 * the bosses they have beaten (guardian ids, "lucifer") and the Codex entries they have opened.
 */
public record MapPayload(boolean inferno, boolean cards, List<String> visited, List<String> beaten, List<String> codex)
		implements CustomPacketPayload {
	public static final Type<MapPayload> TYPE = new Type<>(HellcraftMod.id("map"));
	public static final StreamCodec<ByteBuf, MapPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, MapPayload::inferno,
			ByteBufCodecs.BOOL, MapPayload::cards,
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), MapPayload::visited,
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), MapPayload::beaten,
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), MapPayload::codex,
			MapPayload::new);

	@Override
	public Type<MapPayload> type() {
		return TYPE;
	}
}
