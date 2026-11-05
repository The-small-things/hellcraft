package net.thesmallthings.hellcraft.client.layer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.client.nav.ClientNav;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.Zone;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Each circle's own sound: a bed under everything where the circle has no ambient loop of its own (the Vestibule's wail,
 * Limbo's hush, Lust's storm, Gluttony's rain, the Styx's murk, the wind over Cocytus), and now and then a sound off to
 * one side (insects in the Vestibule, gold clinking in Greed, creaking trees in the Wood, ice cracking, a vast heart beating
 * under Judecca, a hammer in the Forge of Dis, chimes in Paradiso). The sounds are Minecraft's own, pitched and mixed in
 * sounds.json (hellcraft:ambient.<circle>.bed and .detail), and follow the Ambient/Environment volume slider. Beds fade in
 * and out as you cross from one circle to the next.
 */
public final class Soundscape {
	private Soundscape() {
	}

	/** The circles with a bed of their own (the others already have the Nether's ambient loops from their biomes). */
	public static final Set<String> BEDS = Set.of("vestibule", "limbo", "lust", "gluttony", "styx", "cocytus", "judecca");
	/** How long a bed takes to fade in or out (ticks), and the least and most time between two detail sounds. */
	private static final int FADE = 60;
	private static final int DETAIL_MIN = 120;
	private static final int DETAIL_MAX = 420;

	@Nullable
	private static Bed bed;
	@Nullable
	private static String playing;
	private static int nextDetail = DETAIL_MIN;

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(Soundscape::tick);
	}

	/** Which soundscape plays where the player is, or null for none. */
	@Nullable
	public static String where(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			return null;
		}
		if (ClientNav.inForge(mc)) {
			return "forge";
		}
		if (ClientNav.inParadiso(mc)) {
			return "paradiso";
		}
		if (!ClientNav.inHell(mc)) {
			return null;
		}
		Zone zone = InfernoGeometry.zoneAt(mc.player.getX(), mc.player.getZ());
		return switch (zone) {
			case VESTIBULE, ACHERON -> "vestibule";
			case LIMBO -> "limbo";
			case LUST -> "lust";
			case GLUTTONY -> "gluttony";
			case GREED -> "greed";
			case STYX, WALLS_OF_DIS -> "styx";
			case HERESY -> "heresy";
			case PHLEGETHON -> "phlegethon";
			case WOOD_OF_SUICIDES -> "suicides";
			case BURNING_SANDS -> "sands";
			case MALEBOLGE, MALEBOLGE_BLIGHT -> "malebolge";
			case MALEBOLGE_PITCH -> "pitch";
			case WELL_OF_GIANTS -> "giants";
			case COCYTUS -> "cocytus";
			case JUDECCA -> "judecca";
			default -> null;
		};
	}

	private static void tick(Minecraft mc) {
		if (mc.isPaused()) {
			return;
		}
		String here = where(mc);
		String wantBed = here != null && BEDS.contains(here) ? here : null;
		if (!java.util.Objects.equals(wantBed, playing)) {
			if (bed != null) {
				bed.fadeOut();
			}
			bed = null;
			playing = wantBed;
			if (wantBed != null) {
				bed = new Bed(event(wantBed + ".bed"), mc.level.getRandom());
				mc.getSoundManager().play(bed);
			}
		}
		if (playing != null && (bed == null || bed.isStopped())) {
			// the sound engine dropped it (a world change, a reload): start it again
			bed = new Bed(event(playing + ".bed"), mc.level.getRandom());
			mc.getSoundManager().play(bed);
		}
		if (here == null || --nextDetail > 0) {
			return;
		}
		RandomSource random = mc.level.getRandom();
		nextDetail = DETAIL_MIN + random.nextInt(DETAIL_MAX - DETAIL_MIN);
		// off to one side, a little way away, so it's heard from somewhere
		double a = random.nextDouble() * Math.PI * 2;
		double d = 6 + random.nextDouble() * 12;
		mc.getSoundManager().play(new SimpleSoundInstance(event(here + ".detail"), SoundSource.AMBIENT, 1.0f, 1.0f, random,
				mc.player.getX() + Math.cos(a) * d, mc.player.getEyeY() + random.nextDouble() * 6 - 2, mc.player.getZ() + Math.sin(a) * d));
	}

	private static SoundEvent event(String name) {
		return SoundEvent.createVariableRangeEvent(HellcraftMod.id("ambient." + name));
	}

	/** A circle's bed: loops round the listener, fading in, and out when its circle is left. */
	private static final class Bed extends AbstractTickableSoundInstance {
		private int age;
		private int fading = -1;

		Bed(SoundEvent event, RandomSource random) {
			super(event, SoundSource.AMBIENT, random);
			this.looping = true;
			this.delay = 0;
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
			this.volume = 0.01f;
		}

		void fadeOut() {
			if (fading < 0) {
				fading = 0;
			}
		}

		@Override
		public void tick() {
			age++;
			if (fading >= 0) {
				fading++;
				volume = Math.max(0.0f, 1.0f - fading / (float) FADE) * Math.min(1.0f, age / (float) FADE);
				if (fading >= FADE) {
					stop();
				}
				return;
			}
			volume = Math.max(0.01f, Math.min(1.0f, age / (float) FADE));
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}
	}
}
