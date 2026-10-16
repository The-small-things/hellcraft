package net.thesmallthings.hellcraft.cutscene;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A scripted camera shot (T4), played on each watching client (client/cutscene/CutscenePlayer) while the server
 * keeps them from harm (CutsceneManager). Positions are in the anchor's frame: blocks to its right, up, and forward
 * (the way it faces), so the same scene plays wherever the boss stands and whichever way it turns.
 *
 * @param ticks    how long it runs
 * @param shots    where the camera is and what it looks at, at given ticks (smoothly curved between)
 * @param captions lines shown at the bottom (who speaks, what they say)
 * @param shakes   the camera shakes (a roar, a blow)
 * @param card     the name card (a boss's name and title), or null
 * @param fadeIn   ticks of fading in from black at the start
 * @param blendOut ticks over which the camera glides back to the player's eyes at the end
 */
public record Cutscene(String id, int ticks, List<Shot> shots, List<Caption> captions, List<Shake> shakes, @Nullable Card card,
					   int fadeIn, int blendOut) {
	/** roll: how far the camera is turned over (degrees; 180 is upside down), for the climb out of Hell. */
	public record Shot(int tick, double right, double up, double forward, double lookRight, double lookUp, double lookForward, double roll) {
	}

	public record Caption(int from, int to, String speaker, int colour, String line) {
	}

	public record Shake(int from, int to, float strength) {
	}

	public record Card(int from, int to, String title, String subtitle, int colour) {
	}

	public static Builder builder(String id, int ticks) {
		return new Builder(id, ticks);
	}

	public static final class Builder {
		private final String id;
		private final int ticks;
		private final List<Shot> shots = new ArrayList<>();
		private final List<Caption> captions = new ArrayList<>();
		private final List<Shake> shakes = new ArrayList<>();
		private Card card;
		private int fadeIn;
		private int blendOut;

		private Builder(String id, int ticks) {
			this.id = id;
			this.ticks = ticks;
		}

		/** The camera at a tick: where (right, up, forward of the anchor) and what it looks at. */
		public Builder shot(int tick, double right, double up, double forward, double lookRight, double lookUp, double lookForward) {
			return shot(tick, right, up, forward, lookRight, lookUp, lookForward, 0);
		}

		/** The same, turned over by roll degrees. */
		public Builder shot(int tick, double right, double up, double forward, double lookRight, double lookUp, double lookForward, double roll) {
			shots.add(new Shot(tick, right, up, forward, lookRight, lookUp, lookForward, roll));
			return this;
		}

		public Builder caption(int from, int to, String speaker, int colour, String line) {
			captions.add(new Caption(from, to, speaker, colour, line));
			return this;
		}

		public Builder shake(int from, int to, float strength) {
			shakes.add(new Shake(from, to, strength));
			return this;
		}

		public Builder card(int from, int to, String title, String subtitle, int colour) {
			card = new Card(from, to, title, subtitle, colour);
			return this;
		}

		public Builder fadeIn(int ticks) {
			fadeIn = ticks;
			return this;
		}

		public Builder blendOut(int ticks) {
			blendOut = ticks;
			return this;
		}

		public Cutscene build() {
			shots.sort((a, b) -> Integer.compare(a.tick(), b.tick()));
			return new Cutscene(id, ticks, List.copyOf(shots), List.copyOf(captions), List.copyOf(shakes), card, fadeIn, blendOut);
		}
	}
}
