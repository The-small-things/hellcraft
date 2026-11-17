package net.thesmallthings.hellcraft.hazard;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.util.Feedback;

import java.util.Locale;

/**
 * Hellcraft's own cue sounds (synthesised by tools/gen_sounds.py, in assets/hellcraft/sounds/cue): the tells that let a
 * fight be read by ear. The danger marks' cues are timed on the client (client/danger/DangerMarks); these are the ones
 * the server plays: a parry, a broken guard, a slipped blow, a boss's posture, Lucifer's attacks.
 */
public enum Cue {
	PARRY, GUARD_BREAK, DODGE, POSTURE_CRACK, POSTURE_BREAK,
	FLURRY, FEINT, DIVE, CRESCENT, COUNTER, BLINK,
	HAND_RISE, SWAT, CLAP, SLUMP;

	private final Holder<SoundEvent> sound;

	Cue() {
		this.sound = Holder.direct(SoundEvent.createVariableRangeEvent(HellcraftMod.id("cue." + name().toLowerCase(Locale.ROOT))));
	}

	public Holder<SoundEvent> sound() {
		return sound;
	}

	/** Played in the world, for everyone near. */
	public void play(ServerLevel level, double x, double y, double z, float volume, float pitch) {
		level.playSound(null, x, y, z, sound, SoundSource.HOSTILE, volume, pitch);
	}

	/** Played to one player only. */
	public void playTo(ServerPlayer player, float volume, float pitch) {
		Feedback.sound(player, sound, SoundSource.PLAYERS, volume, pitch);
	}
}
