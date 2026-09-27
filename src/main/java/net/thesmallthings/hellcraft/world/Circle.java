package net.thesmallthings.hellcraft.world;

/**
 * The gameplay-level circles of Hell, outermost first. {@link #depth()} is 0 outside Hell proper
 * and 1..9 for the nine circles.
 */
public enum Circle {
	DARK_WOOD(0, "The Dark Wood", "Midway upon the journey of our life, I found myself within a forest dark", "The straight way was lost"),
	VESTIBULE(0, "The Vestibule", "Through me the way into the suffering city", "Neither good nor evil"),
	LIMBO(1, "Circle I — Limbo", "Sighs, not lamentations, made the everlasting air to tremble", "Sighs without hope"),
	LUST(2, "Circle II — Lust", "The infernal hurricane that never rests", "The winds of passion"),
	GLUTTONY(3, "Circle III — Gluttony", "Rain eternal, maledict, and cold, and heavy", "The eternal rain"),
	GREED(4, "Circle IV — Greed", "Why keepest? and why squanderest thou?", "The weight of gold"),
	WRATH(5, "Circle V — Wrath", "Beneath the water people are who sigh", "The sullen and the wrathful"),
	HERESY(6, "Circle VI — Heresy", "Their sepulchres were burning", "The burning tombs"),
	VIOLENCE(7, "Circle VII — Violence", "The river of blood, within which boiling is", "Blood, thorns and fire"),
	FRAUD(8, "Circle VIII — Fraud", "There is a place in Hell called Malebolge", "The evil ditches"),
	TREACHERY(9, "Circle IX — Treachery", "The Emperor of the kingdom dolorous", "Frozen betrayal");

	private final int depth;
	private final String title;
	private final String quote;
	private final String tagline;

	Circle(int depth, String title, String quote, String tagline) {
		this.depth = depth;
		this.title = title;
		this.quote = quote;
		this.tagline = tagline;
	}

	/** A few words for the on-screen subtitle (the full quote is too long for a title). */
	public String tagline() {
		return tagline;
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
