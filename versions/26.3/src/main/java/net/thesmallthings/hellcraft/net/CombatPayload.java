package net.thesmallthings.hellcraft.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.thesmallthings.hellcraft.HellcraftMod;

/**
 * This server's combat rules, for the client (combat/Combat): whether Hellcraft's combat is on, and the numbers the
 * client needs to act at once without asking (it rolls and swings first and the server checks): the most stamina, what
 * a dodge and a heavy attack cost, how long between dodges and how far one goes.
 */
public record CombatPayload(boolean on, int staminaMax, int dodgeCost, int dodgeCooldown, float dodgeDistance, int heavyCost)
		implements CustomPacketPayload {
	public static final Type<CombatPayload> TYPE = new Type<>(HellcraftMod.id("combat"));
	public static final StreamCodec<ByteBuf, CombatPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, CombatPayload::on,
			ByteBufCodecs.VAR_INT, CombatPayload::staminaMax,
			ByteBufCodecs.VAR_INT, CombatPayload::dodgeCost,
			ByteBufCodecs.VAR_INT, CombatPayload::dodgeCooldown,
			ByteBufCodecs.FLOAT, CombatPayload::dodgeDistance,
			ByteBufCodecs.VAR_INT, CombatPayload::heavyCost,
			CombatPayload::new);

	@Override
	public Type<CombatPayload> type() {
		return TYPE;
	}
}
