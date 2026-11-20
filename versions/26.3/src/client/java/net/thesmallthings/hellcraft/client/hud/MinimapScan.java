package net.thesmallthings.hellcraft.client.hud;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * What the minimap shows of the ground: every loaded chunk near the player looked over a few at a time (nearest first, the
 * nearest again every second or so), each column's colour kept per chunk. On the surface a column is its top block's map
 * colour, lit by its slope against the column to the north and darkened by water's depth, as a map draws it; in the cave
 * view it is the floor under the player's height (the first block with a colour below a gap, darker the deeper it lies), a
 * wall where rock stands at the player's height, and black where there is no floor near. The cave view is kept for one band of
 * heights and looked over again as the player climbs or drops. Everything is thrown away on a change of dimension.
 */
public final class MinimapScan {
	private MinimapScan() {
	}

	/** Chunks looked over per tick. */
	private static final int BUDGET = 8;
	/** How old (ticks) a chunk's colours may get before it is looked over again: near the player, and further off. */
	private static final int NEAR_AGE = 30;
	private static final int FAR_AGE = 200;
	/** The cave view's band of heights: looked over again when the player moves this far up or down. */
	private static final int BAND = 3;
	/** How far down the cave view looks for a floor. */
	private static final int CAVE_DEPTH = 48;
	/** A column not known yet. */
	public static final int UNKNOWN = 0;

	private record Scanned(int cx, int cz, int[] colours, int at, boolean cave, int band) {
	}

	private static final Map<Long, Scanned> CHUNKS = new HashMap<>();
	private static ClientLevel level;
	private static int clock;
	private static boolean cave;
	private static int band;
	private static int changes;

	/** The colour of column (x, z) as last seen (ARGB), or {@link #UNKNOWN}. */
	public static int colour(int x, int z) {
		Scanned s = CHUNKS.get(ChunkPos.pack(x >> 4, z >> 4));
		return s == null ? UNKNOWN : s.colours[(z & 15) * 16 + (x & 15)];
	}

	/** Goes up each time any chunk's colours change, so the minimap knows to draw again. */
	public static int changes() {
		return changes;
	}

	public static void clear() {
		CHUNKS.clear();
		level = null;
		changes++;
	}

	/** Is the minimap in its cave view now? */
	public static boolean cave() {
		return cave;
	}

	/**
	 * Every client tick while the minimap shows: looks over the chunks within {@code radius} blocks, most needed first.
	 * {@code caveView}: draw the floor under the player rather than the surface.
	 */
	public static void tick(ClientLevel now, LocalPlayer player, int radius, boolean caveView) {
		clock++;
		if (now != level) {
			CHUNKS.clear();
			level = now;
			changes++;
		}
		int newBand = Math.floorDiv(player.getBlockY(), BAND);
		if (caveView != cave || caveView && newBand != band) {
			cave = caveView;
			band = newBand;
			changes++;
		}
		int pcx = player.getBlockX() >> 4;
		int pcz = player.getBlockZ() >> 4;
		int reach = Math.max(1, (radius >> 4) + 1);
		// what wants looking at, the most urgent first: never seen, then the wrong view, then stale, nearest first within each
		List<long[]> wanted = new ArrayList<>();
		for (int dz = -reach; dz <= reach; dz++) {
			for (int dx = -reach; dx <= reach; dx++) {
				int d = dx * dx + dz * dz;
				if (d > (reach + 1) * (reach + 1)) {
					continue;
				}
				long key = ChunkPos.pack(pcx + dx, pcz + dz);
				Scanned s = CHUNKS.get(key);
				int urgency;
				if (s == null) {
					urgency = 0;
				} else if (s.cave != cave || cave && s.band != band) {
					urgency = 1;
				} else if (clock - s.at > (d <= 2 ? NEAR_AGE : FAR_AGE)) {
					urgency = 2;
				} else {
					continue;
				}
				wanted.add(new long[] {urgency * 100000L + d, pcx + dx, pcz + dz});
			}
		}
		wanted.sort((a, b) -> Long.compare(a[0], b[0]));
		int done = 0;
		for (long[] w : wanted) {
			if (done >= BUDGET) {
				break;
			}
			LevelChunk chunk = now.getChunkSource().getChunk((int) w[1], (int) w[2], false);
			if (chunk == null || chunk.isEmpty()) {
				continue;
			}
			scan(now, chunk, player.getBlockY());
			done++;
		}
		// forget what is far behind
		if (clock % 100 == 0) {
			int keep = reach + 8;
			for (Iterator<Scanned> it = CHUNKS.values().iterator(); it.hasNext(); ) {
				Scanned s = it.next();
				if (Math.abs(s.cx - pcx) > keep || Math.abs(s.cz - pcz) > keep) {
					it.remove();
				}
			}
		}
	}

	/** Should the minimap be in its cave view, for the AUTO setting? Under a roof (the Forge of Dis), or with no sky above. */
	public static boolean underground(ClientLevel level, LocalPlayer player) {
		if (level.dimension() == net.minecraft.world.level.Level.NETHER) {
			return true;
		}
		BlockPos eye = BlockPos.containing(player.getEyePosition());
		return level.getBrightness(LightLayer.SKY, eye) <= 3 && level.getHeight(Heightmap.Types.WORLD_SURFACE, eye.getX(), eye.getZ()) > eye.getY() + 2;
	}

	private static void scan(ClientLevel level, LevelChunk chunk, int playerY) {
		int x0 = chunk.getPos().getMinBlockX();
		int z0 = chunk.getPos().getMinBlockZ();
		int[] colours = new int[256];
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		if (cave) {
			for (int dz = 0; dz < 16; dz++) {
				for (int dx = 0; dx < 16; dx++) {
					colours[dz * 16 + dx] = caveColumn(level, chunk, pos, x0 + dx, z0 + dz, playerY);
				}
			}
		} else {
			int[] north = new int[16];
			LevelChunk northChunk = level.getChunkSource().getChunk(chunk.getPos().x(), chunk.getPos().z() - 1, false);
			boolean northless = northChunk == null || northChunk.isEmpty();
			for (int dx = 0; dx < 16; dx++) {
				north[dx] = northless ? Integer.MIN_VALUE : top(level, northChunk, pos, x0 + dx, z0 - 1);
			}
			for (int dz = 0; dz < 16; dz++) {
				for (int dx = 0; dx < 16; dx++) {
					colours[dz * 16 + dx] = surfaceColumn(level, chunk, pos, x0 + dx, z0 + dz, north, dx);
				}
			}
		}
		long key = chunk.getPos().pack();
		Scanned before = CHUNKS.get(key);
		CHUNKS.put(key, new Scanned(chunk.getPos().x(), chunk.getPos().z(), colours, clock, cave, band));
		if (before == null || !java.util.Arrays.equals(before.colours, colours)) {
			changes++;
		}
	}

	/** The top of a column as a map sees it: the highest block with a colour. Leaves {@code pos} on it; returns its height. */
	private static int top(ClientLevel level, LevelChunk chunk, BlockPos.MutableBlockPos pos, int x, int z) {
		int minY = level.getMinY();
		int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 1;
		if (y <= minY) {
			pos.set(x, minY, z);
			return minY;
		}
		BlockState state;
		do {
			pos.set(x, --y, z);
			state = chunk.getBlockState(pos);
		} while (state.getMapColor(level, pos) == MapColor.NONE && y > minY);
		return y;
	}

	private static int surfaceColumn(ClientLevel level, LevelChunk chunk, BlockPos.MutableBlockPos pos, int x, int z, int[] north, int dx) {
		int minY = level.getMinY();
		int y = top(level, chunk, pos, x, z);
		int northY = north[dx] == Integer.MIN_VALUE ? y : north[dx];
		north[dx] = y;
		if (y <= minY) {
			return 0xFF000000;
		}
		BlockState state = chunk.getBlockState(pos);
		MapColor colour = state.getMapColor(level, pos);
		float light;
		if (!state.getFluidState().isEmpty()) {
			// how deep the water (or lava) is, for its shading
			int depth = 0;
			BlockPos.MutableBlockPos below = pos.mutable();
			BlockState under;
			int sy = y - 1;
			do {
				below.setY(sy--);
				under = chunk.getBlockState(below);
				depth++;
			} while (sy > minY && !under.getFluidState().isEmpty() && depth < 16);
			if (!state.isFaceSturdy(level, pos, Direction.UP)) {
				colour = state.getFluidState().createLegacyBlock().getMapColor(level, pos);
			}
			light = 1.05f - Math.min(depth, 12) * 0.035f;
		} else {
			// lit from the north-west like a map: a rise toward the south is bright, a drop dark
			light = 1.0f + Math.max(-0.32f, Math.min(0.28f, (y - northY) * 0.11f));
		}
		if (colour == MapColor.NONE) {
			colour = MapColor.STONE;
		}
		return shade(colour.col, light);
	}

	private static int caveColumn(ClientLevel level, LevelChunk chunk, BlockPos.MutableBlockPos pos, int x, int z, int playerY) {
		int minY = level.getMinY();
		int start = Math.min(level.getMaxY(), playerY + 1);
		pos.set(x, start, z);
		BlockState head = chunk.getBlockState(pos);
		pos.set(x, start - 1, z);
		BlockState feet = chunk.getBlockState(pos);
		if (solid(level, head, pos.setY(start)) && solid(level, feet, pos.setY(start - 1))) {
			// rock at the player's height: a wall, dark
			pos.setY(start);
			MapColor c = head.getMapColor(level, pos);
			return shade((c == MapColor.NONE ? MapColor.STONE : c).col, 0.38f);
		}
		boolean open = false;
		for (int y = start; y > Math.max(minY, start - CAVE_DEPTH); y--) {
			pos.set(x, y, z);
			BlockState state = chunk.getBlockState(pos);
			MapColor c = state.getMapColor(level, pos);
			if (c == MapColor.NONE) {
				open = true;
				continue;
			}
			if (!open && y >= start - 1) {
				continue;
			}
			// the floor: darker the further it lies under the player
			float light = 1.0f - Math.min(0.6f, Math.max(0, playerY - 1 - y) * 0.035f);
			if (!state.getFluidState().isEmpty()) {
				light *= 0.9f;
			}
			return shade(c.col, light);
		}
		return 0xFF0A0606;
	}

	private static boolean solid(ClientLevel level, BlockState state, BlockPos pos) {
		return state.getMapColor(level, pos) != MapColor.NONE && state.getFluidState().isEmpty();
	}

	private static int shade(int rgb, float light) {
		int r = Math.min(255, Math.round(((rgb >> 16) & 255) * light));
		int g = Math.min(255, Math.round(((rgb >> 8) & 255) * light));
		int b = Math.min(255, Math.round((rgb & 255) * light));
		return 0xFF000000 | r << 16 | g << 8 | b;
	}
}
