package net.thesmallthings.hellcraft.util;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/** Small helpers for things 26.x no longer exposes directly on players and entities. */
public final class Feedback {
	private Feedback() {
	}

	/** Plays a sound only this player hears (the old Player.playNotifySound). */
	public static void sound(ServerPlayer player, Holder<SoundEvent> sound, SoundSource source, float volume, float pitch) {
		player.connection.send(new ClientboundSoundPacket(sound, source, player.getX(), player.getY(), player.getZ(), volume, pitch,
				player.getRandom().nextLong()));
	}

	public static void sound(ServerPlayer player, SoundEvent sound, SoundSource source, float volume, float pitch) {
		sound(player, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), source, volume, pitch);
	}

	/** Players move themselves, so a push has to be sent to their client (the old hurtMarked). */
	public static void syncMotion(Entity entity) {
		if (entity instanceof ServerPlayer player) {
			player.connection.send(new ClientboundSetEntityMotionPacket(player));
		}
	}
}
