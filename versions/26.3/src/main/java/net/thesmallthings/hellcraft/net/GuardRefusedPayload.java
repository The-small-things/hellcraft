package net.thesmallthings.hellcraft.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.thesmallthings.hellcraft.HellcraftMod;

/**
 * The server wouldn't raise the guard the client asked for (combat/Guard: too early in a roll, a heavy weapon's recovery,
 * a cooldown). The client has already raised it on screen, so it lowers it again and tries once more while the key is held.
 */
public record GuardRefusedPayload() implements CustomPacketPayload {
	public static final GuardRefusedPayload INSTANCE = new GuardRefusedPayload();
	public static final Type<GuardRefusedPayload> TYPE = new Type<>(HellcraftMod.id("guard_refused"));
	public static final StreamCodec<ByteBuf, GuardRefusedPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<GuardRefusedPayload> type() {
		return TYPE;
	}
}
