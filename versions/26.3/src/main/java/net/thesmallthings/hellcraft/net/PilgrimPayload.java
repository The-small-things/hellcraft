package net.thesmallthings.hellcraft.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.thesmallthings.hellcraft.HellcraftMod;

/**
 * A player's pilgrimage, for the pilgrim's mark on their screen (see Pilgrimage): whether they are on one, its time so far
 * (ticks of play) and the deepest circle it has reached (Circle.depth); and whether the server makes it hardcore.
 */
public record PilgrimPayload(boolean on, int ticks, int deepest, boolean hardcore) implements CustomPacketPayload {
	public static final Type<PilgrimPayload> TYPE = new Type<>(HellcraftMod.id("pilgrim"));
	public static final StreamCodec<ByteBuf, PilgrimPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, PilgrimPayload::on,
			ByteBufCodecs.VAR_INT, PilgrimPayload::ticks,
			ByteBufCodecs.VAR_INT, PilgrimPayload::deepest,
			ByteBufCodecs.BOOL, PilgrimPayload::hardcore,
			PilgrimPayload::new);

	@Override
	public Type<PilgrimPayload> type() {
		return TYPE;
	}
}
