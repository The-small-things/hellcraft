package net.thesmallthings.hellcraft.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.Guide;

import java.util.List;

/** The Pilgrim's Guide, page by page (with this server's numbers), for the Journal screen. */
public record JournalPayload(List<Guide.Page> pages) implements CustomPacketPayload {
	public static final Type<JournalPayload> TYPE = new Type<>(HellcraftMod.id("journal"));
	private static final StreamCodec<ByteBuf, Guide.Page> PAGE = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, Guide.Page::title,
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Guide.Page::lines,
			Guide.Page::new);
	public static final StreamCodec<ByteBuf, JournalPayload> CODEC = PAGE.apply(ByteBufCodecs.list()).map(JournalPayload::new, JournalPayload::pages);

	@Override
	public Type<JournalPayload> type() {
		return TYPE;
	}
}
