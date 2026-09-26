package net.thesmallthings.hellcraft.world;

/**
 * The gameplay-level circles of Hell, outermost first. {@link #depth()} is 0 outside Hell proper
 * and 1..9 for the nine circles.
 */
public enum Circle {
	DARK_WOOD(0, "The Dark Wood", "Midway upon the journey of our life, I found myself within a forest dark"),
	VESTIBULE(0, "The Vestibule", "Through me the way into the suffering city"),
	LIMBO(1, "Circle I — Limbo", "Sighs, not lamentations, made the everlasting air to tremble"),
	LUST(2, "Circle II — Lust", "The infernal hurricane that never rests"),
	GLUTTONY(3, "Circle III — Gluttony", "Rain eternal, maledict, and cold, and heavy"),
	GREED(4, "Circle IV — Greed", "Why keepest? and why squanderest thou?"),
	WRATH(5, "Circle V — Wrath", "Beneath the water people are who sigh"),
	HERESY(6, "Circle VI — Heresy", "Their sepulchres were burning"),
	VIOLENCE(7, "Circle VII — Violence", "The river of blood, within which boiling is"),
	FRAUD(8, "Circle VIII — Fraud", "There is a place in Hell called Malebolge"),
	TREACHERY(9, "Circle IX — Treachery", "The Emperor of the kingdom dolorous");

	private final int depth;
	private final String title;
	private final String quote;

	Circle(int depth, String title, String quote) {
		this.depth = depth;
		this.title = title;
		this.quote = quote;
	}

	public int depth() {
		return depth;
	}

	public String title() {
		return title;
	}

	public String quote() {
		return quote;
	}
}
