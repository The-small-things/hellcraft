package net.thesmallthings.hellcraft.hazard.lucifer;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.JukeboxSong;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.music.MusicPack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The fight's soundtrack. Each listener hears the operator's custom track if they loaded the music
 * pack, otherwise a vanilla music disc. Tracks follow the listener (entity-bound sound) and loop.
 */
final class LuciferMusic {
	private record Song(Holder<SoundEvent> sound, int lengthTicks) {
	}

	private final ServerLevel level;
	@Nullable
	private MusicPack.Track track;
	/** Per listener: the song they're hearing and the tick it should restart. */
	private final Map<UUID, Song> playing = new HashMap<>();
	private final Map<UUID, Integer> restartAt = new HashMap<>();

	LuciferMusic(ServerLevel level) {
		this.level = level;
	}

	void switchTo(MusicPack.Track next, List<ServerPlayer> audience, int now) {
		stopAll(audience);
		track = next;
		for (ServerPlayer p : audience) {
			// silence the biome's ambient music too
			p.connection.send(new ClientboundStopSoundPacket(null, SoundSource.MUSIC));
			start(p, now);
		}
	}

	void tick(List<ServerPlayer> audience, int now) {
		if (track == null) {
			return;
		}
		for (ServerPlayer p : audience) {
			Integer at = restartAt.get(p.getUUID());
			if (at == null || now >= at) {
				start(p, now);
			}
		}
	}

	void stopAll(List<ServerPlayer> audience) {
		for (ServerPlayer p : audience) {
			stop(p);
		}
		for (UUID id : playing.keySet()) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			if (p != null) {
				stop(p);
			}
		}
		playing.clear();
		restartAt.clear();
		track = null;
	}

	private void start(ServerPlayer player, int now) {
		if (track == null) {
			return;
		}
		Song song = songFor(player, track);
		if (song == null) {
			restartAt.put(player.getUUID(), Integer.MAX_VALUE);
			return;
		}
		stop(player);
		player.connection.send(new ClientboundSoundEntityPacket(song.sound(), SoundSource.MUSIC, player, 1.0f, 1.0f, level.getRandom().nextLong()));
		playing.put(player.getUUID(), song);
		restartAt.put(player.getUUID(), song.lengthTicks() > 0 ? now + song.lengthTicks() + 10 : Integer.MAX_VALUE);
	}

	private void stop(ServerPlayer player) {
		Song song = playing.remove(player.getUUID());
		if (song != null) {
			player.connection.send(new ClientboundStopSoundPacket(song.sound().value().location(), SoundSource.MUSIC));
		}
	}

	@Nullable
	private Song songFor(ServerPlayer player, MusicPack.Track track) {
		if (MusicPack.hasPack(player)) {
			return new Song(MusicPack.sound(track), (int) Math.round(MusicPack.length(track) * 20));
		}
		HellConfig config = HellConfig.get();
		String id = switch (track) {
			case DUEL -> config.fallbackMusicDuel;
			case ENRAGED -> config.fallbackMusicEnraged;
			case TRUE_FORM -> config.fallbackMusicTrueForm;
		};
		Identifier location = Identifier.tryParse(id);
		if (location == null) {
			return null;
		}
		Optional<Holder.Reference<JukeboxSong>> song = level.registryAccess().lookupOrThrow(Registries.JUKEBOX_SONG).get(location);
		return song.map(s -> new Song(s.value().soundEvent(), Math.round(s.value().lengthInSeconds() * 20))).orElse(null);
	}
}
