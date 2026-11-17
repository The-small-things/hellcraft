package net.thesmallthings.hellcraft.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.thesmallthings.hellcraft.HellcraftMod;

/** The client pressed attack from a hell weapon's guard: loose its Blood Art (blood/HellWeapons.art). */
public record ArtPayload() implements CustomPacketPayload {
	public static final ArtPayload INSTANCE = new ArtPayload();
	public static final Type<ArtPayload> TYPE = new Type<>(HellcraftMod.id("art"));
	public static final StreamCodec<ByteBuf, ArtPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<ArtPayload> type() {
		return TYPE;
	}
}
