package net.thesmallthings.hellcraft.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.thesmallthings.hellcraft.HellcraftMod;

/**
 * A player's blood (blood/HellWeapons), for their HUD: the blood charge (0 to 100; full means a hell weapon's Blood Art is
 * ready, which the hotbar shows as a blood aura on those slots), and their Blood Oath: ticks left of it and its full length,
 * then ticks left of its cooldown and the cooldown's length (the oath meter over the stamina bar). The client counts the
 * ticks down itself between updates, which come only when something changes.
 */
public record BloodPayload(int charge, int oathLeft, int oathLength, int cooldownLeft, int cooldownLength) implements CustomPacketPayload {
	public static final Type<BloodPayload> TYPE = new Type<>(HellcraftMod.id("blood"));
	public static final StreamCodec<ByteBuf, BloodPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, BloodPayload::charge,
			ByteBufCodecs.VAR_INT, BloodPayload::oathLeft,
			ByteBufCodecs.VAR_INT, BloodPayload::oathLength,
			ByteBufCodecs.VAR_INT, BloodPayload::cooldownLeft,
			ByteBufCodecs.VAR_INT, BloodPayload::cooldownLength,
			BloodPayload::new);

	@Override
	public Type<BloodPayload> type() {
		return TYPE;
	}
}
