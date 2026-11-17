package net.thesmallthings.hellcraft.client.combat.anim;

/**
 * The bones of the player's combat rig (tools/art/player_moves.py), by the names its animations use. The first seven are
 * the player's body; ITEM turns the held weapon in the hand, FP places it in first person, CAMERA nudges the view.
 */
public enum Bone {
	ROOT("root"),
	BODY("body"),
	HEAD("head"),
	RIGHT_ARM("right_arm"),
	LEFT_ARM("left_arm"),
	RIGHT_LEG("right_leg"),
	LEFT_LEG("left_leg"),
	ITEM("item"),
	FP("fp"),
	CAMERA("camera");

	public static final Bone[] ALL = values();
	public static final int COUNT = ALL.length;

	public final String id;

	Bone(String id) {
		this.id = id;
	}

	/** The bone on the other side of the body (itself for the middle ones). */
	public Bone mirrored() {
		return switch (this) {
			case RIGHT_ARM -> LEFT_ARM;
			case LEFT_ARM -> RIGHT_ARM;
			case RIGHT_LEG -> LEFT_LEG;
			case LEFT_LEG -> RIGHT_LEG;
			default -> this;
		};
	}
}
