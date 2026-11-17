package net.thesmallthings.hellcraft.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.thesmallthings.hellcraft.HellcraftMod;

/**
 * The client raises its weapon's guard (combat/Guard): a right-click nothing else had a use for, with a weapon in the
 * main hand. Letting go is vanilla's own "stop using the item".
 */
public record GuardPayload() implements CustomPacketPayload {
	public static final GuardPayload INSTANCE = new GuardPayload();
	public static final Type<GuardPayload> TYPE = new Type<>(HellcraftMod.id("guard"));
	public static final StreamCodec<ByteBuf, GuardPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<GuardPayload> type() {
		return TYPE;
	}
}
