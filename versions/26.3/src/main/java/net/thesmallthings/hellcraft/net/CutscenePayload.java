package net.thesmallthings.hellcraft.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.thesmallthings.hellcraft.HellcraftMod;

/** Play this cutscene (by id) around this anchor, facing this way; skippable if the player has seen it before. */
public record CutscenePayload(String id, double x, double y, double z, float yaw, boolean skippable) implements CustomPacketPayload {
	public static final Type<CutscenePayload> TYPE = new Type<>(HellcraftMod.id("cutscene"));
	public static final StreamCodec<ByteBuf, CutscenePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, CutscenePayload::id,
			ByteBufCodecs.DOUBLE, CutscenePayload::x,
			ByteBufCodecs.DOUBLE, CutscenePayload::y,
			ByteBufCodecs.DOUBLE, CutscenePayload::z,
			ByteBufCodecs.FLOAT, CutscenePayload::yaw,
			ByteBufCodecs.BOOL, CutscenePayload::skippable,
			CutscenePayload::new);

	@Override
	public Type<CutscenePayload> type() {
		return TYPE;
	}
}
