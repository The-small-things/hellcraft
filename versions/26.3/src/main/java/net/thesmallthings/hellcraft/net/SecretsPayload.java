package net.thesmallthings.hellcraft.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.thesmallthings.hellcraft.HellcraftMod;

import java.util.List;

/** The secret ways down this player has found (SecretWays ids), for the Map of Hell and the Journal (see SecretWayLift). */
public record SecretsPayload(List<String> found) implements CustomPacketPayload {
	public static final Type<SecretsPayload> TYPE = new Type<>(HellcraftMod.id("secrets"));
	public static final StreamCodec<ByteBuf, SecretsPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), SecretsPayload::found,
			SecretsPayload::new);

	@Override
	public Type<SecretsPayload> type() {
		return TYPE;
	}
}
