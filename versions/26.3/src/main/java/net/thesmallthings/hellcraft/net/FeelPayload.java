package net.thesmallthings.hellcraft.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.thesmallthings.hellcraft.HellcraftMod;

/**
 * Something the player should feel in a fight (combat/Combat.feel; client combat/HitFeel): their blow landed, they
 * parried, their guard broke, a blow passed through their roll. The kind is a {@link Kind} ordinal; strength scales the
 * pause and the kick of the view (1 = an ordinary blow).
 */
public record FeelPayload(int kind, float strength) implements CustomPacketPayload {
	public enum Kind {
		/** Your blow landed. */
		HIT,
		/** Your heavy blow landed. */
		HEAVY_HIT,
		/** You parried a blow. */
		PARRY,
		/** A blow landed on your guard. */
		GUARDED,
		/** Your guard broke. */
		GUARD_BREAK,
		/** A blow passed through your roll. */
		DODGED
	}

	public static final Type<FeelPayload> TYPE = new Type<>(HellcraftMod.id("feel"));
	public static final StreamCodec<ByteBuf, FeelPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, FeelPayload::kind,
			ByteBufCodecs.FLOAT, FeelPayload::strength,
			FeelPayload::new);

	public FeelPayload(Kind kind, float strength) {
		this(kind.ordinal(), strength);
	}

	public Kind what() {
		return Kind.values()[Math.floorMod(kind, Kind.values().length)];
	}

	@Override
	public Type<FeelPayload> type() {
		return TYPE;
	}
}
