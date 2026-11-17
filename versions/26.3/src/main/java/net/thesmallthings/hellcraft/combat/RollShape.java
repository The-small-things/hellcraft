package net.thesmallthings.hellcraft.combat;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Player;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A rolling player is small: for most of the roll (from the dive to the landing) their hitbox is {@link #SHAPE}, under a
 * block high, so blows, arrows and the crosshair have less to find and a roll can pass under a one-block gap (come up in
 * one and vanilla has them crawl out). Their eyes go low with it, which the view eases down and back up.
 *
 * <p>Worked out the same on the server and every client (the server when it accepts a roll, combat/Dodge; the roller's
 * own client when it starts one, client/combat/RollControl; everyone else's when the roll is shown to them), each side
 * with its own players: mixin/AvatarMixin asks {@link #small}.
 */
public final class RollShape {
	private RollShape() {
	}

	/** The rolling hitbox: as wide as standing, 0.9 high, eyes at 0.75. */
	public static final EntityDimensions SHAPE = EntityDimensions.scalable(0.6f, 0.9f).withEyeHeight(0.75f);
	/** How long it lasts: the roll but its last two ticks, where the player is getting up. */
	public static final int TICKS = Dodge.TICKS - 2;

	private static final Map<Player, Long> CLIENT = new WeakHashMap<>();
	private static final Map<Player, Long> SERVER = new WeakHashMap<>();

	private static Map<Player, Long> side(Player player) {
		return player.level().isClientSide() ? CLIENT : SERVER;
	}

	/** A roll begins: small from now. */
	public static void start(Player player) {
		side(player).put(player, player.level().getGameTime() + TICKS);
		player.refreshDimensions();
	}

	public static boolean small(Player player) {
		Long until = side(player).get(player);
		return until != null && player.level().getGameTime() < until;
	}

	/** Each tick, on one side: rolls that are over give the player back their size. */
	public static void tick(boolean client) {
		Map<Player, Long> map = client ? CLIENT : SERVER;
		for (Iterator<Map.Entry<Player, Long>> it = map.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Player, Long> e = it.next();
			Player player = e.getKey();
			if (player.isRemoved() || player.level().getGameTime() >= e.getValue()) {
				it.remove();
				player.refreshDimensions();
			}
		}
	}

	public static String selfTest() {
		if (SHAPE.height() >= 1.0f || SHAPE.eyeHeight() > SHAPE.height()) {
			return "Roll shape wrong: a rolling player must fit under one block, eyes inside the box";
		}
		return "";
	}
}
