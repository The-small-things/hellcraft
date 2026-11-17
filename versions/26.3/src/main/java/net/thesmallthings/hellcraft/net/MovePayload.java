package net.thesmallthings.hellcraft.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.thesmallthings.hellcraft.HellcraftMod;

/**
 * A player starts a combat move, for everyone who can see them (the one who made it has already begun: the client plays
 * its own rolls and swings at once): the player's entity id, the move (a PlayerMovesAnim ordinal), the way it goes
 * (flat in the world; a roll's direction) and how fast it plays (1 = as made).
 */
public record MovePayload(int entity, int move, float x, float z, float speed) implements CustomPacketPayload {
	public static final Type<MovePayload> TYPE = new Type<>(HellcraftMod.id("move"));
	public static final StreamCodec<ByteBuf, MovePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, MovePayload::entity,
			ByteBufCodecs.VAR_INT, MovePayload::move,
			ByteBufCodecs.FLOAT, MovePayload::x,
			ByteBufCodecs.FLOAT, MovePayload::z,
			ByteBufCodecs.FLOAT, MovePayload::speed,
			MovePayload::new);

	@Override
	public Type<MovePayload> type() {
		return TYPE;
	}
}
