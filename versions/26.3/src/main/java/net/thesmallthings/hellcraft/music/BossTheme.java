package net.thesmallthings.hellcraft.music;

import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.sound.HellSounds;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A guardian's fight music: one of the mod's tracks ({@code music.<track>}, from tools/audio) played to everyone near
 * the lair, following the listener and looping, and switched when the fight changes phase (the {@code _1} track until
 * half health, {@code _2} after). Whoever walks off stops hearing it; whoever walks in starts. The biome's music is
 * silenced while it plays. Lucifer's fight has its own (hazard/lucifer/LuciferMusic).
 */
public final class BossTheme {
	/** Track name -> its length in seconds, read once from the jar (0 if unknown: then it plays once). */
	private static final Map<String, Double> LENGTHS = new ConcurrentHashMap<>();

	private final ServerLevel level;
	@Nullable
	private String track;
	/** Per listener: the game tick at which the track should start again. */
	private final Map<UUID, Long> restartAt = new HashMap<>();

	public BossTheme(ServerLevel level) {
		this.level = level;
	}

	/** Plays this track (a name in tools/audio/hellsounds.py's MUSIC) to the audience, unless it's already playing. */
	public void play(String next, List<ServerPlayer> audience) {
		if (next.equals(track)) {
			return;
		}
		if (HellSounds.music(next) == null) {
			HellcraftMod.LOGGER.warn("No boss music called {}", next);
			return;
		}
		stopAll();
		track = next;
		HellcraftMod.LOGGER.info("Boss music: {} ({} s)", next, Math.round(length(next)));
		tick(audience);
	}

	/** Starts the track for newcomers, loops it, and stops it for whoever left. Call it every second or so. */
	public void tick(List<ServerPlayer> audience) {
		if (track == null) {
			return;
		}
		long now = level.getGameTime();
		Set<UUID> here = new HashSet<>();
		for (ServerPlayer p : audience) {
			here.add(p.getUUID());
			Long at = restartAt.get(p.getUUID());
			if (at == null || now >= at) {
				start(p, now);
			}
		}
		for (Iterator<UUID> it = restartAt.keySet().iterator(); it.hasNext(); ) {
			UUID id = it.next();
			if (!here.contains(id)) {
				ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
				if (p != null) {
					stop(p);
				}
				it.remove();
			}
		}
	}

	public void stopAll() {
		for (UUID id : restartAt.keySet()) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			if (p != null) {
				stop(p);
			}
		}
		restartAt.clear();
		track = null;
	}

	@Nullable
	public String track() {
		return track;
	}

	private void start(ServerPlayer player, long now) {
		Holder<SoundEvent> sound = HellSounds.music(track);
		// the biome's music and this track's last loop
		player.connection.send(new ClientboundStopSoundPacket(null, SoundSource.MUSIC));
		player.connection.send(new ClientboundSoundEntityPacket(sound, SoundSource.MUSIC, player, 1.0f, 1.0f, level.getRandom().nextLong()));
		double seconds = length(track);
		restartAt.put(player.getUUID(), seconds > 0 ? now + Math.round(seconds * 20) + 10 : Long.MAX_VALUE);
	}

	private void stop(ServerPlayer player) {
		if (track != null) {
			player.connection.send(new ClientboundStopSoundPacket(HellSounds.music(track).value().location(), SoundSource.MUSIC));
		}
	}

	/** The length of one of the mod's music tracks in seconds (0 if it can't be read). */
	public static double length(String track) {
		return LENGTHS.computeIfAbsent(track, name -> {
			try (InputStream in = BossTheme.class.getResourceAsStream("/assets/hellcraft/sounds/music/" + name + ".ogg")) {
				return in == null ? 0.0 : Math.max(0.0, OggLength.seconds(in.readAllBytes()));
			} catch (IOException e) {
				HellcraftMod.LOGGER.warn("Could not read the music track {}", name, e);
				return 0.0;
			}
		});
	}
}
