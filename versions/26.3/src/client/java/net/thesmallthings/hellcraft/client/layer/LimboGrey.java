package net.thesmallthings.hellcraft.client.layer;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.client.nav.ClientNav;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.NobleCastle;
import net.thesmallthings.hellcraft.world.Zone;
import org.jetbrains.annotations.Nullable;

/**
 * Limbo is without colour (Inferno IV: a place of sighs, not of torment): as you walk in from the circle's edge the
 * world drains to grey, and it comes back only in the light of the Noble Castle, on its green. Five steps of the
 * hellcraft:limbo_<n> screen effect (tools/gen_post.py), added to the game's own (mixin/GameRendererMixin).
 */
public final class LimboGrey {
	private LimboGrey() {
	}

	private static final int STEPS = 5;
	/** Over how many blocks the colour drains, at the circle's edges and round the castle's green. */
	private static final double FADE = 40.0;
	private static final Identifier[] EFFECTS = new Identifier[STEPS];

	static {
		for (int i = 0; i < STEPS; i++) {
			EFFECTS[i] = HellcraftMod.id("limbo_" + (i + 1));
		}
	}

	/** The effect to draw this frame, or null. */
	public static @Nullable Identifier effect(Minecraft mc) {
		if (!ClientNav.inHell(mc) || mc.player == null) {
			return null;
		}
		double x = mc.player.getX();
		double z = mc.player.getZ();
		double r = Math.hypot(x, z);
		double outer = InfernoGeometry.outerRadius(Zone.LIMBO);
		double inner = InfernoGeometry.outerRadius(Zone.LUST);
		double into = Math.min(outer - r, r - inner);
		if (into <= 0) {
			return null;
		}
		double grey = Math.min(1, into / FADE);
		// the castle's light keeps its green in colour
		double castle = NobleCastle.distance(x, z) - NobleCastle.GREEN;
		grey = Math.min(grey, Math.max(0, castle / (FADE / 2)));
		int step = (int) Math.ceil(grey * STEPS);
		return step <= 0 ? null : EFFECTS[Math.min(STEPS, step) - 1];
	}
}
