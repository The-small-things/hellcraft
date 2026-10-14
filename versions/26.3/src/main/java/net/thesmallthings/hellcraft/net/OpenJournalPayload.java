package net.thesmallthings.hellcraft.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.thesmallthings.hellcraft.HellcraftMod;

/** Asks the client to open the Pilgrim's Journal (/guide). */
public record OpenJournalPayload() implements CustomPacketPayload {
	public static final OpenJournalPayload INSTANCE = new OpenJournalPayload();
	public static final Type<OpenJournalPayload> TYPE = new Type<>(HellcraftMod.id("open_journal"));
	public static final StreamCodec<ByteBuf, OpenJournalPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<OpenJournalPayload> type() {
		return TYPE;
	}
}
