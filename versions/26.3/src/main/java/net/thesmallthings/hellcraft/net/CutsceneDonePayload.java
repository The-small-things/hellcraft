package net.thesmallthings.hellcraft.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.thesmallthings.hellcraft.HellcraftMod;

/** The client finished (or skipped) a cutscene: the player can be hurt again. */
public record CutsceneDonePayload(String id) implements CustomPacketPayload {
	public static final Type<CutsceneDonePayload> TYPE = new Type<>(HellcraftMod.id("cutscene_done"));
	public static final StreamCodec<ByteBuf, CutsceneDonePayload> CODEC = ByteBufCodecs.STRING_UTF8.map(CutsceneDonePayload::new, CutsceneDonePayload::id);

	@Override
	public Type<CutsceneDonePayload> type() {
		return TYPE;
	}
}
