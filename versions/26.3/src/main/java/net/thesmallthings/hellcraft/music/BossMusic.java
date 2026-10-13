package net.thesmallthings.hellcraft.music;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.thesmallthings.hellcraft.HellcraftMod;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

/**
 * Lucifer's music, one track per phase. A track ships in the mod as
 * {@code assets/hellcraft/sounds/music/<file>.ogg} with an entry for {@code lucifer.<file>} in
 * {@code assets/hellcraft/sounds.json}; a phase without one plays a vanilla music disc instead (the
 * fallbackMusic settings). Every player runs the same Hellcraft, so a track in the server's jar is in theirs too.
 */
public final class BossMusic {
	private BossMusic() {
	}

	public enum Track {
		DUEL("duel"),
		ENRAGED("enraged"),
		TRUE_FORM("true_form");

		private final String file;

		Track(String file) {
			this.file = file;
		}

		public Identifier soundId() {
			return HellcraftMod.id("lucifer." + file);
		}
	}

	/** The length in seconds of each track the mod ships. */
	private static final Map<Track, Double> SHIPPED = new EnumMap<>(Track.class);

	static {
		for (Track track : Track.values()) {
			try (InputStream in = BossMusic.class.getResourceAsStream("/assets/hellcraft/sounds/music/" + track.file + ".ogg")) {
				if (in != null) {
					SHIPPED.put(track, OggLength.seconds(in.readAllBytes()));
				}
			} catch (IOException e) {
				HellcraftMod.LOGGER.warn("Could not read Lucifer's {} track", track.file, e);
			}
		}
	}

	public static boolean shipped(Track track) {
		return SHIPPED.containsKey(track);
	}

	public static Holder<SoundEvent> sound(Track track) {
		return Holder.direct(SoundEvent.createVariableRangeEvent(track.soundId()));
	}

	/** Length of a shipped track in seconds (0 if unknown, so it doesn't loop). */
	public static double length(Track track) {
		return SHIPPED.getOrDefault(track, 0.0);
	}

	public static void log() {
		if (SHIPPED.isEmpty()) {
			HellcraftMod.LOGGER.info("Boss music: no tracks in the mod; Lucifer's fight plays vanilla music discs");
		} else {
			HellcraftMod.LOGGER.info("Boss music: {} track(s) in the mod ({})", SHIPPED.size(), SHIPPED.keySet());
		}
	}
}
