package net.thesmallthings.hellcraft.hazard.guardian;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.Function;

/**
 * The circle guardians Dante meets on the way down, each waiting in a lair beside the east road (the +X
 * axis), {@link #LAIR_SIDE} blocks to its south, so the road itself never runs through a fight.
 */
public enum Guardian {
	MINOS("Minos", "Judge of the Damned", 2425, ChatFormatting.DARK_PURPLE, 300, 7.0f, MinosFight::new),
	CERBERUS("Cerberus", "the Great Worm", 2100, ChatFormatting.DARK_GREEN, 350, 5.0f, CerberusFight::new),
	PLUTUS("Plutus", "the Great Enemy", 1800, ChatFormatting.GOLD, 300, 5.0f, PlutusFight::new),
	MINOTAUR("The Minotaur", "Infamy of Crete", 885, ChatFormatting.RED, 400, 5.5f, MinotaurFight::new),
	GERYON("Geryon", "Image of Fraud", 830, ChatFormatting.DARK_AQUA, 250, 5.0f, GeryonFight::new),
	/** Not on the road: at the Great Forge of Dis, in the Nether (GreatForge). */
	VULCAN("Vulcan", "Smith of the Gods", 0, ChatFormatting.GOLD, 600, 6.5f, VulcanFight::new);

	public final String title;
	public final String subtitle;
	/** Distance of the lair from the centre of Hell, along +X. */
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

	/** True for the guardian of the Nether (Vulcan), whose lair is the Great Forge. */
	public boolean inNether() {
		return this == VULCAN;
	}

	/** How far south of the east road the lairs lie (see ShrineSites, which keeps the Rests clear of them). */
	public static final int LAIR_SIDE = 40;

	/** The lair's centre column (y is filled in from the terrain). */
	public BlockPos lairColumn() {
		return new BlockPos(radius, 0, LAIR_SIDE);
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
