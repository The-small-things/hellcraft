package net.thesmallthings.hellcraft.world;


/**
 * The gameplay-level circles of Hell, outermost first. {@link #depth()} is 0 outside Hell proper
 * and 1..9 for the nine circles.
 */
public enum Circle {
	DARK_WOOD(0, "The Dark Wood", "Midway upon the journey of our life, I found myself within a forest dark", "The straight way was lost",
			"THE DARK WOOD", "Safe ground. Build, gather and get ready here.", "Anything you have"),
	VESTIBULE(0, "The Vestibule", "Through me the way into the suffering city", "Neither good nor evil",
			"THE SHORE", "Follow the road over the river into Limbo.", "Stone tools and food"),
	LIMBO(1, "Circle I — Limbo", "Sighs, not lamentations, made the everlasting air to tremble", "Sighs without hope",
			"CIRCLE I: LIMBO", "A quiet place: few monsters and no torment.", "Stone tools and food"),
	LUST(2, "Circle II — Lust", "The infernal hurricane that never rests", "The winds of passion",
			"CIRCLE II: LUST", "The wind is a road: step into a white stream and it carries you down the circle. Sneak to let go.", "Iron armour"),
	GLUTTONY(3, "Circle III — Gluttony", "Rain eternal, maledict, and cold, and heavy", "The eternal rain",
			"CIRCLE III: GLUTTONY", "The mud swallows you if you stand still. Keep moving, or stand on roots, stone or planks.", "Iron armour and plenty of food"),
	GREED(4, "Circle IV — Greed", "Why keepest? and why squanderest thou?", "The weight of gold",
			"CIRCLE IV: GREED", "Gold draws the hoarders: the more you carry, the more come. Stash it and they lose interest.", "Iron armour and a shield"),
	WRATH(5, "Circle V — Wrath", "Beneath the water people are who sigh", "The sullen and the wrathful",
			"CIRCLE V: WRATH", "The sullen lurk under the water where bubbles rise. Keep to the roads, or take a boat.", "Iron or diamond armour, a boat"),
	HERESY(6, "Circle VI — Heresy", "Their sepulchres were burning", "The burning tombs",
			"CIRCLE VI: HERESY", "The tombs blast fire in turn: a red circle warns which is next. Cross between the blasts.", "Diamond gear and Fire Resistance"),
	VIOLENCE(7, "Circle VII — Violence", "The river of blood, within which boiling is", "Blood, thorns and fire",
			"CIRCLE VII: VIOLENCE", "Ford the blood while the centaurs look away. Sneak in the wood. Hide under rocks when fire falls.", "Diamond armour, Fire Resistance"),
	FRAUD(8, "Circle VIII — Fraud", "There is a place in Hell called Malebolge", "The evil ditches",
			"CIRCLE VIII: FRAUD", "Some monsters are invisible. Watch for their swirl of particles.", "Diamond armour and a bow"),
	TREACHERY(9, "Circle IX — Treachery", "The Emperor of the kingdom dolorous", "Frozen betrayal",
			"CIRCLE IX: TREACHERY", "The ice freezes you. Wear leather, or stay near a fire.", "Your best gear, and leather to keep warm");

	private final int depth;
	private final String title;
	private final String quote;
	private final String tagline;
	private final String cardTitle;
	private final String rule;
	private final String gear;

	Circle(int depth, String title, String quote, String tagline, String cardTitle, String rule, String gear) {
		this.depth = depth;
		this.title = title;
		this.quote = quote;
		this.tagline = tagline;
		this.cardTitle = cardTitle;
		this.rule = rule;
		this.gear = gear;
	}

	/** The big line of the card shown on entering ("CIRCLE II: LUST"). */
	public String cardTitle() {
		return cardTitle;
	}

	/** What this circle does to you and what to do about it, in one plain sentence. */
	public String rule() {
		return rule;
	}

	/** The gear worth having here. */
	public String gear() {
		return gear;
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
