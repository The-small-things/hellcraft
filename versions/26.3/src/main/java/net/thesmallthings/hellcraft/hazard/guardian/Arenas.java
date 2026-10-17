package net.thesmallthings.hellcraft.hazard.guardian;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.thesmallthings.hellcraft.blood.Hearts;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.world.GreatForge;
import net.thesmallthings.hellcraft.world.HellWorldgen;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.Paradiso;
import net.thesmallthings.hellcraft.world.ParadisoGeometry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * /hellcraft arena &lt;boss&gt; [lair] [players]: takes players to the edge of a boss's arena, just outside it, facing in,
 * so a test starts when they step in (op only). The guardians of Hell go to the nearest of their four lairs (or the one
 * named, 1 to 4); Vulcan to the Great Forge, the Seraph to the Primum Mobile (letting them that far into Paradiso), and
 * Lucifer to the rim of his pit.
 */
public final class Arenas {
	private Arenas() {
	}

	/** How far from a lair's centre players are set down: past its braziers (14) and where it wakes (16). */
	private static final double EDGE = 19;

	/** Every boss the command knows: the guardians, then Lucifer. */
	public static List<String> ids() {
		List<String> out = new ArrayList<>();
		for (Guardian g : Guardian.values()) {
			out.add(g.id());
		}
		out.add("lucifer");
		return out;
	}

	/** Sends a player to a boss's arena; lair is 1 to 4 for a guardian of Hell, or 0 for the nearest. Returns what happened. */
	public static String send(ServerPlayer player, String boss, int lair) {
		MinecraftServer server = player.level().getServer();
		ServerLevel overworld = server.overworld();
		if (boss.equals("lucifer")) {
			if (!HellWorldgen.isInferno(overworld)) {
				return "This world is not an Inferno world.";
			}
			int x = (int) Math.round(InfernoGeometry.PIT_RADIUS + 10);
			BlockPos at = ground(overworld, x, 0);
			player.teleportTo(overworld, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), 90.0f, 20.0f, true);
			return "Sent " + name(player) + " to the rim of Lucifer's pit (step onto the ice floor to start the fight).";
		}
		Guardian g = Guardian.byId(boss);
		if (g == null) {
			return "Unknown boss \"" + boss + "\". Try: " + String.join(", ", ids());
		}
		if (g.inNether()) {
			ServerLevel nether = GreatForge.nether(server);
			if (nether == null) {
				return "This server has no Nether.";
			}
			GreatForge.build(server);
			BlockPos c = GreatForge.center();
			player.teleportTo(nether, c.getX() + 0.5, c.getY(), c.getZ() + 0.5 + EDGE, Set.of(), 180.0f, 0.0f, true);
			return "Sent " + name(player) + " to the Great Forge (walk to its middle to wake Vulcan).";
		}
		if (g.inParadiso()) {
			ServerLevel paradiso = Paradiso.level(server);
			if (paradiso == null) {
				return "This world has no Paradiso.";
			}
			// the spheres open one at a time: let them as far as the Primum Mobile, or Paradiso sends them back down
			HellState.Soul soul = Hearts.soul(player);
			soul.spheres |= (1 << ParadisoGeometry.Sphere.PRIMUM_MOBILE.ordinal()) - 1;
			HellState.get(server).setDirty();
			BlockPos c = GuardianManager.lair(paradiso, g, 0);
			player.teleportTo(paradiso, c.getX() + 0.5 - EDGE, c.getY(), c.getZ() + 0.5, Set.of(), -90.0f, 0.0f, true);
			return "Sent " + name(player) + " to the Primum Mobile (they may now climb that far; walk to the middle to wake the Seraph).";
		}
		if (!HellWorldgen.isInferno(overworld)) {
			return "This world is not an Inferno world.";
		}
		int k = lair >= 1 && lair <= g.lairs() ? lair - 1 : g.nearestLair(player.getX(), player.getZ());
		BlockPos c = GuardianManager.lair(overworld, g, k);
		// the edge on the side whose ground is nearest the arena's floor (not in a wall or a river)
		BlockPos best = null;
		for (int i = 0; i < 16; i++) {
			double a = Math.atan2(c.getZ(), c.getX()) + i * Math.PI / 8;
			BlockPos at = ground(overworld, (int) Math.floor(c.getX() + 0.5 + Math.cos(a) * EDGE), (int) Math.floor(c.getZ() + 0.5 + Math.sin(a) * EDGE));
			if (best == null || Math.abs(at.getY() - c.getY()) < Math.abs(best.getY() - c.getY())) {
				best = at;
			}
		}
		float yaw = (float) Math.toDegrees(Math.atan2(-(c.getX() - best.getX()), c.getZ() - best.getZ()));
		player.teleportTo(overworld, best.getX() + 0.5, best.getY(), best.getZ() + 0.5, Set.of(), yaw, 10.0f, true);
		return String.format(Locale.ROOT, "Sent %s to %s's lair %d of %d at %d, %d (walk in to wake it).", name(player), g.title, k + 1, g.lairs(),
				c.getX(), c.getZ());
	}

	private static BlockPos ground(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
	}

	private static String name(ServerPlayer player) {
		return player.getGameProfile().name();
	}
}
