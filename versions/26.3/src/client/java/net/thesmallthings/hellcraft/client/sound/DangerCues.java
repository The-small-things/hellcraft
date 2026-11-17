package net.thesmallthings.hellcraft.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.hazard.gen.CueTimes;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The danger language by ear (sounds from tools/gen_sounds.py): red is a low horn that swells and thumps, white a bright
 * shimmer that rises to a hard ring, purple a sour rising whine. Each is timed so its moment (the thump, the ring, the
 * rush) falls exactly as the mark lands: started that many ticks early (CueTimes), or, for a mark too quick for that, a
 * short cue with the same landing. Many marks landing together (falling stars) sound once, and a quiet mark (one whose
 * boss plays a tell of its own) none.
 *
 * <p>A mark within earshot is heard from its direction but never far away: the sound is placed at most 6 blocks off,
 * towards the mark, so a warning is always loud enough to act on.
 */
public final class DangerCues {
	private DangerCues() {
	}

	private static final SoundEvent[] SOUNDS = {
			SoundEvent.createVariableRangeEvent(HellcraftMod.id("cue.red")),
			SoundEvent.createVariableRangeEvent(HellcraftMod.id("cue.white")),
			SoundEvent.createVariableRangeEvent(HellcraftMod.id("cue.purple")),
	};
	/** For a mark too quick for the full build-up: a short one with the same landing. */
	private static final SoundEvent[] SHORT = {
			SoundEvent.createVariableRangeEvent(HellcraftMod.id("cue.red_short")),
			SoundEvent.createVariableRangeEvent(HellcraftMod.id("cue.white_short")),
			SoundEvent.createVariableRangeEvent(HellcraftMod.id("cue.purple_short")),
	};
	private static final int[] LEADS = {CueTimes.RED, CueTimes.WHITE, CueTimes.PURPLE};
	private static final int[] SHORT_LEADS = {CueTimes.RED_SHORT, CueTimes.WHITE_SHORT, CueTimes.PURPLE_SHORT};
	/** How far off a mark is still heard, and how far the sound is placed at most. */
	private static final double HEARD = 48.0;
	private static final double NEAR = 6.0;

	private record Pending(SoundEvent sound, int colour, double x, double y, double z, long startAt) {
	}

	private static final List<Pending> PENDING = new ArrayList<>();
	/** The tick (of this client's count) each colour's last cue lands on, so marks landing together sound once. */
	private static final long[] LANDS = {-100, -100, -100};
	private static long ticks;

	/** A mark arrived: its cue is lined up to land with it. */
	public static void mark(int colour, boolean quiet, double x, double y, double z, int warnTicks) {
		if (quiet || colour < 0 || colour >= SOUNDS.length) {
			return;
		}
		long lands = ticks + warnTicks;
		if (Math.abs(lands - LANDS[colour]) <= 3) {
			return;
		}
		LANDS[colour] = lands;
		boolean full = warnTicks >= LEADS[colour];
		int lead = full ? LEADS[colour] : SHORT_LEADS[colour];
		PENDING.add(new Pending(full ? SOUNDS[colour] : SHORT[colour], colour, x, y, z, ticks + Math.max(0, warnTicks - lead)));
	}

	public static void tick(Minecraft mc) {
		ticks++;
		if (mc.level == null || mc.player == null) {
			PENDING.clear();
			return;
		}
		for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
			Pending p = it.next();
			if (ticks >= p.startAt) {
				it.remove();
				play(mc, p);
			}
		}
	}

	/** How many cues of each colour have played (for the client test, which may have no sound device). */
	public static final int[] PLAYED = new int[3];

	private static void play(Minecraft mc, Pending p) {
		PLAYED[p.colour]++;
		Vec3 ear = mc.player.getEyePosition();
		Vec3 to = new Vec3(p.x, p.y + 0.5, p.z).subtract(ear);
		double d = to.length();
		if (d > HEARD) {
			return;
		}
		Vec3 at = d > NEAR ? ear.add(to.scale(NEAR / d)) : ear.add(to);
		float volume = (float) (1.0 - 0.4 * Math.min(1.0, d / HEARD));
		mc.getSoundManager().play(new SimpleSoundInstance(p.sound.location(), SoundSource.HOSTILE, volume, 1.0f, RandomSource.create(),
				false, 0, SoundInstance.Attenuation.LINEAR, at.x, at.y, at.z, false));
	}

	public static void clear() {
		PENDING.clear();
	}
}
