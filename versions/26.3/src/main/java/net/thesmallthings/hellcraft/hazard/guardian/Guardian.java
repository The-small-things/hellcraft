package net.thesmallthings.hellcraft.hazard.guardian;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.Function;

/**
 * The circle guardians Dante meets on the way down, each waiting in a lair in its own circle. The lairs are spread
 * round the funnel (InfernoGeometry.LAIRS), off the roads and the ways down, so none of them blocks the way.
 */
public enum Guardian {
	MINOS("Minos", "Judge of the Damned", 2425, ChatFormatting.DARK_PURPLE, 300, 7.0f, MinosFight::new),
	CERBERUS("Cerberus", "the Great Worm", 2100, ChatFormatting.DARK_GREEN, 350, 5.0f, CerberusFight::new),
	PLUTUS("Plutus", "the Great Enemy", 1800, ChatFormatting.GOLD, 300, 5.0f, PlutusFight::new),
	/** In the Fen of Styx, at a landing beside the east watchtower: lighting its twin beacons calls him (Fen). */
	PHLEGYAS("Phlegyas", "the Ferryman of the Styx", 1605, ChatFormatting.DARK_AQUA, 320, 7.0f, PhlegyasFight::new),
	/** Inside the Walls of Dis: the three Furies, who call up Medusa when hard pressed (FuriesFight). */
	FURIES("The Furies", "the Erinyes of Dis", 1360, ChatFormatting.DARK_RED, 420, 2.5f, FuriesFight::new),
	/** In Heresy, standing in his burning tomb (FarinataFight). */
	FARINATA("Farinata", "the Proud Heretic", 1260, ChatFormatting.GOLD, 360, 3.0f, FarinataFight::new),
	/** In a clearing of the Wood of Suicides, in Violence. */
	MINOTAUR("The Minotaur", "Infamy of Crete", 970, ChatFormatting.RED, 400, 5.5f, MinotaurFight::new),
	GERYON("Geryon", "Image of Fraud", 870, ChatFormatting.DARK_AQUA, 250, 5.0f, GeryonFight::new),
	/** Not on the road: at the Great Forge of Dis, in the Nether (GreatForge). */
	VULCAN("Vulcan", "Smith of the Gods", 0, ChatFormatting.GOLD, 600, 6.5f, VulcanFight::new);

	public final String title;
	public final String subtitle;
	/** Distance of the lair from the centre of Hell. */
	public final int radius;
	public final ChatFormatting color;
	final double health;
	final float modelHeight;
	final Function<GuardianContext, GuardianFight> factory;

	Guardian(String title, String subtitle, int radius, ChatFormatting color, double health, float modelHeight,
			 Function<GuardianContext, GuardianFight> factory) {
		this.title = title;
		this.subtitle = subtitle;
		this.radius = radius;
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

	/** The lair's centre column (y is filled in from the terrain). */
	public BlockPos lairColumn() {
		int[] lair = InfernoGeometry.LAIRS[ordinal()];
		return new BlockPos(lair[0], 0, lair[1]);
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
