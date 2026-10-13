package net.thesmallthings.hellcraft.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.thesmallthings.hellcraft.HellcraftMod;

/**
 * How a player's health bar looks: Hellcraft's blood hearts (the heartHud setting) and the rim colour of
 * their prestige rank, 0 to 7 (see {@link HudSync}).
 */
public record HudPayload(boolean bloodHearts, int rank) implements CustomPacketPayload {
	public static final Type<HudPayload> TYPE = new Type<>(HellcraftMod.id("hud"));
	public static final StreamCodec<ByteBuf, HudPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, HudPayload::bloodHearts,
			ByteBufCodecs.VAR_INT, HudPayload::rank,
			HudPayload::new);

	@Override
	public Type<HudPayload> type() {
		return TYPE;
	}
}
