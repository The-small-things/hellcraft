package net.thesmallthings.hellcraft.world;

/**
 * Every distinct region of the Inferno. Each zone maps 1:1 to a biome ({@code hellcraft:<id>})
 * and belongs to one {@link Circle} for gameplay purposes.
 */
public enum Zone {
	DARK_WOOD("dark_wood", Circle.DARK_WOOD),
	VESTIBULE("vestibule", Circle.VESTIBULE),
	ACHERON("acheron", Circle.VESTIBULE),
	LIMBO("limbo", Circle.LIMBO),
	LUST("lust", Circle.LUST),
	GLUTTONY("gluttony", Circle.GLUTTONY),
	GREED("greed", Circle.GREED),
	STYX("styx", Circle.WRATH),
	WALLS_OF_DIS("walls_of_dis", Circle.WRATH),
	HERESY("heresy", Circle.HERESY),
	PHLEGETHON("phlegethon", Circle.VIOLENCE),
	WOOD_OF_SUICIDES("wood_of_suicides", Circle.VIOLENCE),
	BURNING_SANDS("burning_sands", Circle.VIOLENCE),
	MALEBOLGE("malebolge", Circle.FRAUD),
	MALEBOLGE_PITCH("malebolge_pitch", Circle.FRAUD),
	MALEBOLGE_BLIGHT("malebolge_blight", Circle.FRAUD),
	WELL_OF_GIANTS("well_of_giants", Circle.FRAUD),
	COCYTUS("cocytus", Circle.TREACHERY),
	JUDECCA("judecca", Circle.TREACHERY);

	private final String id;
	private final Circle circle;

	Zone(String id, Circle circle) {
		this.id = id;
		this.circle = circle;
	}

	public String id() {
		return id;
	}

	public Circle circle() {
		return circle;
	}
}
