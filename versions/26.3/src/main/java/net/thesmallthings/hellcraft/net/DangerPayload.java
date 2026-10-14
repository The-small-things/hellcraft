package net.thesmallthings.hellcraft.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.thesmallthings.hellcraft.HellcraftMod;

/**
 * A danger mark on the ground (see Danger): its colour and shape, where it is, how it is sized and turned, how long
 * it fills before the blow lands, and the entity it follows for a while (or -1).
 */
public record DangerPayload(int id, int colour, int shape, double x, double y, double z, float size, float width, float angle,
							int warnTicks, int follow, int followTicks) implements CustomPacketPayload {
	public static final Type<DangerPayload> TYPE = new Type<>(HellcraftMod.id("danger"));
	public static final StreamCodec<ByteBuf, DangerPayload> CODEC = StreamCodec.of((buf, p) -> {
		buf.writeInt(p.id);
		buf.writeByte(p.colour);
		buf.writeByte(p.shape);
		buf.writeDouble(p.x);
		buf.writeDouble(p.y);
		buf.writeDouble(p.z);
		buf.writeFloat(p.size);
		buf.writeFloat(p.width);
		buf.writeFloat(p.angle);
		buf.writeInt(p.warnTicks);
		buf.writeInt(p.follow);
		buf.writeInt(p.followTicks);
	}, buf -> new DangerPayload(buf.readInt(), buf.readByte(), buf.readByte(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
			buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readInt(), buf.readInt(), buf.readInt()));

	@Override
	public Type<DangerPayload> type() {
		return TYPE;
	}
}
