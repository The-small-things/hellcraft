package net.thesmallthings.hellcraft.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.thesmallthings.hellcraft.HellcraftMod;

/** The client pressed Dash (Sidestep leggings): which way, flat in the world (blood/LayerEnchants). */
public record DashPayload(float x, float z) implements CustomPacketPayload {
	public static final Type<DashPayload> TYPE = new Type<>(HellcraftMod.id("dash"));
	public static final StreamCodec<ByteBuf, DashPayload> CODEC = StreamCodec.composite(ByteBufCodecs.FLOAT, DashPayload::x, ByteBufCodecs.FLOAT,
			DashPayload::z, DashPayload::new);

	@Override
	public Type<DashPayload> type() {
		return TYPE;
	}
}
