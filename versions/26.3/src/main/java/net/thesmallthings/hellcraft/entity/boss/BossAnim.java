package net.thesmallthings.hellcraft.entity.boss;


/**
 * One of a boss's (or a monster's) animations as the server sees them: every generated *Anim enum (tools/gen_bosses.py)
 * is one. AnimatedBoss plays them by their ordinal.
 */
public interface BossAnim {
	/** How long it runs, in ticks. */
	int ticks();

	boolean looping();

	/** The tick into the animation at which the named event happens. */
	int at(String event);

	int ordinal();

	String name();
}
