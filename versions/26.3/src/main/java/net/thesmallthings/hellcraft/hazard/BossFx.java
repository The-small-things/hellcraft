package net.thesmallthings.hellcraft.hazard;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;

/**
 * The particles of bosses' blows, sent so they can't be missed: twice as many as asked for, seen from across the
 * arena (out to 512 blocks, not vanilla's 32) and shown whatever a player's particle setting. Each blow's mark also
 * erupts on the client as it lands (client/danger/Impacts).
 */
public final class BossFx {
	private BossFx() {
	}

	/** More than this many at once would only cost frames. */
	private static final int MAX = 600;

	public static <T extends ParticleOptions> void send(ServerLevel level, T particle, double x, double y, double z, int count, double dx, double dy, double dz,
														double speed) {
		int n = count == 0 ? 0 : Math.min(MAX, count * 2);
		level.sendParticles(particle, true, true, x, y, z, n, dx, dy, dz, speed);
	}
}
