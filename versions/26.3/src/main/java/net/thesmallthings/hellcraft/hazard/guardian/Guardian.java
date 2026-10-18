package net.thesmallthings.hellcraft.hazard.guardian;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.Function;

/**
 * The circle guardians Dante meets on the way down, each waiting in its own circle in any of its four lairs (one in each
 * quarter of Hell, InfernoGeometry.LAIRS), off the roads and the ways down, so none of them blocks the way.
 */
public enum Guardian {
	MINOS("Minos", "Judge of the Damned", ChatFormatting.DARK_PURPLE, 300, 7.0f, MinosFight::new),
	CERBERUS("Cerberus", "the Great Worm", ChatFormatting.DARK_GREEN, 350, 5.0f, CerberusFight::new),
	PLUTUS("Plutus", "the Great Enemy", ChatFormatting.GOLD, 300, 5.0f, PlutusFight::new),
	/** In the Fen of Styx, at the landings beside the watchtowers: lighting the east tower's twin beacons calls him (Fen). */
	PHLEGYAS("Phlegyas", "the Ferryman of the Styx", ChatFormatting.DARK_AQUA, 320, 7.0f, PhlegyasFight::new),
	/** Inside the Walls of Dis: the three Furies, who call up Medusa when hard pressed (FuriesFight). */
	FURIES("The Furies", "the Erinyes of Dis", ChatFormatting.DARK_RED, 420, 2.5f, FuriesFight::new),
	/** In Heresy, standing in his burning tomb (FarinataFight). */
	FARINATA("Farinata", "the Proud Heretic", ChatFormatting.GOLD, 360, 3.0f, FarinataFight::new),
	/** In a clearing of the Wood of Suicides, in Violence. */
	MINOTAUR("The Minotaur", "Infamy of Crete", ChatFormatting.RED, 400, 5.5f, MinotaurFight::new),
	GERYON("Geryon", "Image of Fraud", ChatFormatting.DARK_AQUA, 250, 5.0f, GeryonFight::new),
	/** Not on the road: at the Great Forge of Dis, in the Nether (GreatForge). */
	VULCAN("Vulcan", "Smith of the Gods", ChatFormatting.GOLD, 600, 6.5f, VulcanFight::new),
	/** Not in Hell: on the crystal floor of the Primum Mobile, Paradiso's ninth sphere; the last trial (SeraphFight). */
	SERAPH("The Seraph", "Burning One of the Primum Mobile", ChatFormatting.YELLOW, 700, 6.5f, SeraphFight::new);

	public final String title;
	public final String subtitle;
	public final ChatFormatting color;
	final double health;
	final float modelHeight;
	final Function<GuardianContext, GuardianFight> factory;

	Guardian(String title, String subtitle, ChatFormatting color, double health, float modelHeight,
			 Function<GuardianContext, GuardianFight> factory) {
		this.title = title;
		this.subtitle = subtitle;
		this.color = color;
		this.health = health;
		this.modelHeight = modelHeight;
		this.factory = factory;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The item model drawn for this guardian (assets/hellcraft/models/item/guardian_*.json). */
	String model() {
		return "guardian_" + id();
	}

	/** True for Phlegyas, who also comes when the east watchtower's beacons are lit (as well as when someone walks in). */
	public boolean wakesByBeacon() {
		return this == PHLEGYAS;
	}

	/** True for the guardian of the Nether (Vulcan), whose lair is the Great Forge. */
	public boolean inNether() {
		return this == VULCAN;
	}

	/** True for the Seraph, whose lair is the Primum Mobile's great island in Paradiso. */
	public boolean inParadiso() {
		return this == SERAPH;
	}

	/** True for the guardians whose lairs aren't in Hell (Vulcan, the Seraph): they have no lair on the map. */
	public boolean offMap() {
		return inNether() || inParadiso();
	}

	/** The first lair's centre column (y is filled in from the terrain). */
	public BlockPos lairColumn() {
		return lairColumn(0);
	}

	/** Lair k's centre column (y is filled in from the terrain). */
	public BlockPos lairColumn(int k) {
		int[] lair = InfernoGeometry.LAIRS[ordinal() * InfernoGeometry.LAIRS_EACH + k];
		return new BlockPos(lair[0], 0, lair[1]);
	}

	/** How many lairs this guardian has on the map (none for Vulcan and the Seraph, whose lairs are elsewhere). */
	public int lairs() {
		return offMap() ? 0 : InfernoGeometry.LAIRS_EACH;
	}

	/** The lair nearest a point, or 0. */
	public int nearestLair(double x, double z) {
		int best = 0;
		double bestD = Double.MAX_VALUE;
		for (int k = 0; k < lairs(); k++) {
			BlockPos c = lairColumn(k);
			double d = (c.getX() - x) * (c.getX() - x) + (c.getZ() - z) * (c.getZ() - z);
			if (d < bestD) {
				bestD = d;
				best = k;
			}
		}
		return best;
	}

	@Nullable
	public static Guardian byId(String id) {
		for (Guardian g : values()) {
			if (g.id().equals(id)) {
				return g;
			}
		}
		return null;
	}
}
