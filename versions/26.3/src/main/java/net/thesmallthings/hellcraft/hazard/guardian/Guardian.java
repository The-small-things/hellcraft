package net.thesmallthings.hellcraft.hazard.guardian;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.Function;

/**
 * The circle guardians Dante meets on the way down, each waiting in a lair on the straight road in
 * from the Gate of Hell (the +X axis), in the middle of its circle.
 */
public enum Guardian {
	MINOS("Minos", "Judge of the Damned", 4050, ChatFormatting.DARK_PURPLE, 300, 7.0f, MinosFight::new),
	CERBERUS("Cerberus", "the Great Worm", 3575, ChatFormatting.DARK_GREEN, 350, 5.0f, CerberusFight::new),
	PLUTUS("Plutus", "the Great Enemy", 3125, ChatFormatting.GOLD, 300, 5.0f, PlutusFight::new),
	MINOTAUR("The Minotaur", "Infamy of Crete", 1600, ChatFormatting.RED, 400, 5.5f, MinotaurFight::new),
	GERYON("Geryon", "Image of Fraud", 1480, ChatFormatting.DARK_AQUA, 250, 5.0f, GeryonFight::new);

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

	/** The lair's centre column (y is filled in from the terrain). */
	public BlockPos lairColumn() {
		return new BlockPos(radius, 0, 0);
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
