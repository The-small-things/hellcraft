package net.thesmallthings.hellcraft.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.thesmallthings.hellcraft.HellcraftMod;

/** A player's stamina as the server has it (combat/Stamina), for the stamina bar; sent when it changes. */
public record StaminaPayload(float stamina) implements CustomPacketPayload {
	public static final Type<StaminaPayload> TYPE = new Type<>(HellcraftMod.id("stamina"));
	public static final StreamCodec<ByteBuf, StaminaPayload> CODEC = StreamCodec.composite(ByteBufCodecs.FLOAT, StaminaPayload::stamina,
			StaminaPayload::new);

	@Override
	public Type<StaminaPayload> type() {
		return TYPE;
	}
}
