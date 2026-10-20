package net.thesmallthings.hellcraft.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.thesmallthings.hellcraft.HellcraftMod;

import java.util.ArrayList;
import java.util.List;

/** The waymarks a player can see on their Map of Hell: their own and everyone's shared ones (world/Waymarks). */
public record WaymarksPayload(List<Entry> marks) implements CustomPacketPayload {
	public static final Type<WaymarksPayload> TYPE = new Type<>(HellcraftMod.id("waymarks"));

	/** One waymark as its viewer sees it: mine is true for their own, owner names whoever placed it. */
	public record Entry(int id, String name, int x, int z, int colour, boolean shared, boolean mine, String owner) {
	}

	public static final StreamCodec<ByteBuf, WaymarksPayload> CODEC = StreamCodec.of((buf, payload) -> {
		ByteBufCodecs.VAR_INT.encode(buf, payload.marks.size());
		for (Entry e : payload.marks) {
			ByteBufCodecs.VAR_INT.encode(buf, e.id);
			ByteBufCodecs.STRING_UTF8.encode(buf, e.name);
			ByteBufCodecs.VAR_INT.encode(buf, e.x);
			ByteBufCodecs.VAR_INT.encode(buf, e.z);
			ByteBufCodecs.VAR_INT.encode(buf, e.colour);
			ByteBufCodecs.BOOL.encode(buf, e.shared);
			ByteBufCodecs.BOOL.encode(buf, e.mine);
			ByteBufCodecs.STRING_UTF8.encode(buf, e.owner);
		}
	}, buf -> {
		int n = ByteBufCodecs.VAR_INT.decode(buf);
		List<Entry> marks = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			marks.add(new Entry(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
					ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.BOOL.decode(buf),
					ByteBufCodecs.STRING_UTF8.decode(buf)));
		}
		return new WaymarksPayload(marks);
	});

	@Override
	public Type<WaymarksPayload> type() {
		return TYPE;
	}
}
