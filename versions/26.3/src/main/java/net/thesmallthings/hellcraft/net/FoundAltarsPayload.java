package net.thesmallthings.hellcraft.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.thesmallthings.hellcraft.HellcraftMod;

import java.util.List;

/**
 * The Blood Altars outside the Rests this player has found (world/FoundAltars), for their Map of Hell: side by side,
 * each one's dimension, position (BlockPos.asLong) and name (its default name when they haven't given it one).
 */
public record FoundAltarsPayload(List<String> dimensions, List<Long> positions, List<String> names) implements CustomPacketPayload {
	public static final Type<FoundAltarsPayload> TYPE = new Type<>(HellcraftMod.id("found_altars"));
	public static final StreamCodec<ByteBuf, FoundAltarsPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), FoundAltarsPayload::dimensions,
			ByteBufCodecs.LONG.apply(ByteBufCodecs.list()), FoundAltarsPayload::positions,
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), FoundAltarsPayload::names,
			FoundAltarsPayload::new);

	@Override
	public Type<FoundAltarsPayload> type() {
		return TYPE;
	}
}
