package net.thesmallthings.hellcraft.client;

import net.minecraft.resources.Identifier;
import net.thesmallthings.hellcraft.HellcraftMod;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * The Blood Heart HUD: while the server says so (HudSync), the health bar's heart sprites are swapped for
 * Hellcraft's (textures/gui/sprites/hud/heart, from tools/gen_textures.py), and the empty containers take
 * the rim of the player's prestige rank (hud/heart/rank_&lt;n&gt;).
 */
public final class BloodHud {
	private BloodHud() {
	}

	private static final String PREFIX = "hud/heart/";
	/** The vanilla heart sprites Hellcraft redraws. */
	private static final Set<String> DRAWN = Set.of(
			"container", "container_blinking", "container_hardcore", "container_hardcore_blinking",
			"full", "full_blinking", "half", "half_blinking",
			"hardcore_full", "hardcore_full_blinking", "hardcore_half", "hardcore_half_blinking");

	private static volatile boolean on;
	private static volatile int rank;

	public static void set(boolean bloodHearts, int prestige) {
		on = bloodHearts;
		rank = Math.max(0, Math.min(7, prestige));
	}

	public static void reset() {
		set(false, 0);
	}

	/** Hellcraft's sprite in place of a vanilla heart sprite, or null to keep the vanilla one. */
	@Nullable
	public static Identifier swap(Identifier vanilla) {
		if (!on || !vanilla.getNamespace().equals("minecraft") || !vanilla.getPath().startsWith(PREFIX)) {
			return null;
		}
		String name = vanilla.getPath().substring(PREFIX.length());
		if (!DRAWN.contains(name)) {
			return null;
		}
		int r = rank;
		if (r > 0 && name.startsWith("container")) {
			return HellcraftMod.id(PREFIX + "rank_" + r + "/" + name);
		}
		return HellcraftMod.id(PREFIX + name);
	}
}
