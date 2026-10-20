package net.thesmallthings.hellcraft.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.thesmallthings.hellcraft.HellcraftMod;

/**
 * A player places, changes or removes one of their waymarks on the Map of Hell (world/Waymarks). For a new one the id
 * is 0; to remove one, remove is true and the rest is ignored.
 */
public record WaymarkEditPayload(int id, String name, int x, int z, int colour, boolean shared, boolean remove) implements CustomPacketPayload {
	public static final Type<WaymarkEditPayload> TYPE = new Type<>(HellcraftMod.id("waymark_edit"));

	public static final StreamCodec<ByteBuf, WaymarkEditPayload> CODEC = StreamCodec.of((buf, p) -> {
		ByteBufCodecs.VAR_INT.encode(buf, p.id);
		ByteBufCodecs.STRING_UTF8.encode(buf, p.name);
		ByteBufCodecs.VAR_INT.encode(buf, p.x);
		ByteBufCodecs.VAR_INT.encode(buf, p.z);
		ByteBufCodecs.VAR_INT.encode(buf, p.colour);
		ByteBufCodecs.BOOL.encode(buf, p.shared);
		ByteBufCodecs.BOOL.encode(buf, p.remove);
	}, buf -> new WaymarkEditPayload(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
			ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.BOOL.decode(buf)));

	@Override
	public Type<WaymarkEditPayload> type() {
		return TYPE;
	}
}
