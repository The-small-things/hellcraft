package net.thesmallthings.hellcraft.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.thesmallthings.hellcraft.HellcraftMod;

/**
 * One line for the dialogue box (Dialogue): who says it (empty for a plain notice), the colour of their name and
 * the box's edge, the line itself, and whether it is shouted (the box shakes).
 */
public record DialoguePayload(String speaker, int colour, Component line, boolean shout) implements CustomPacketPayload {
	public static final Type<DialoguePayload> TYPE = new Type<>(HellcraftMod.id("dialogue"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DialoguePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, DialoguePayload::speaker,
			ByteBufCodecs.INT, DialoguePayload::colour,
			ComponentSerialization.TRUSTED_STREAM_CODEC, DialoguePayload::line,
			ByteBufCodecs.BOOL, DialoguePayload::shout,
			DialoguePayload::new);

	@Override
	public Type<DialoguePayload> type() {
		return TYPE;
	}
}
