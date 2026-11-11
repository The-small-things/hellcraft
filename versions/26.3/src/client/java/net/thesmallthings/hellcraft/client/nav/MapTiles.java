package net.thesmallthings.hellcraft.client.nav;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.LevelResource;
import net.thesmallthings.hellcraft.HellcraftMod;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * The ground you've seen, for the Map of Hell: every column of the Inferno's overworld the client has had loaded, as the
 * colour of its top block (its vanilla map colour, shaded by the slope like a vanilla map), saved on this computer per
 * world under {@code hellcraft/maps/<world>/} and drawn over the painted map (MapOfHellScreen).
 *
 * <p>It is kept at two sizes, so a well-explored world costs little memory and the map opens at once:
 * <ul>
 * <li><b>the overview</b>: a cell for every {@link #CELL} blocks, in regions of {@link #REGION} blocks ({@code o.<rx>.<rz>.bin}),
 * all of them in memory (a few MB for the whole Inferno); drawn when the map is zoomed out, and under the detail;</li>
 * <li><b>the detail</b>: a block a pixel, in tiles of {@link #SIZE} blocks ({@code r.<tx>.<tz>.bin}), read in the
 * background only for what is in view or being scanned, and dropped again (least lately used first) past a cap.</li>
 * </ul>
 */
public final class MapTiles {
	private MapTiles() {
	}

	/** A detail tile's width in blocks (and in pixels: a block a pixel). */
	public static final int SIZE = 256;
	/** The overview: a cell for every CELL x CELL blocks, in regions REGION blocks wide. */
	public static final int CELL = 4;
	public static final int REGION = 1024;
	private static final int REGION_CELLS = REGION / CELL;
	/** Detail is drawn up to this many blocks a (GUI) pixel, where no more than {@link #MAX_VIEW} tiles are in view. */
	public static final double DETAIL_SCALE = 3.0;
	private static final int MAX_VIEW = 200;
	/** How many detail tiles stay in memory: at least MIN_CAP, and what's in view plus some to spare. */
	private static final int MIN_CAP = 96;
	private static final int SPARE = 40;
	private static final int TILE_VERSION = 2;
	private static final int REGION_VERSION = 1;

	/** One detail tile: each column's packed map colour (0: not seen); its texture once drawn. */
	public static final class Tile {
		final int tx;
		final int tz;
		final byte[] colours;
		boolean dirty = true;
		boolean unsaved;
		DynamicTexture texture;
		Identifier id;

		Tile(int tx, int tz, byte[] colours) {
			this.tx = tx;
			this.tz = tz;
			this.colours = colours;
		}

		public int minX() {
			return tx * SIZE;
		}

		public int minZ() {
			return tz * SIZE;
		}
	}

	/** One region of the overview: each cell's packed map colour (0: not seen); its texture once drawn. */
	public static final class Region {
		final int rx;
		final int rz;
		final byte[] cells;
		volatile boolean dirty = true;
		volatile boolean unsaved;
		DynamicTexture texture;
		Identifier id;

		Region(int rx, int rz, byte[] cells) {
			this.rx = rx;
			this.rz = rz;
			this.cells = cells;
		}

		public int minX() {
			return rx * REGION;
		}

		public int minZ() {
			return rz * REGION;
		}
	}

	private record Arrived(Path dir, long key, @Nullable Tile tile) {
	}

	/** The detail tiles in memory, least lately used first. Client thread only. */
	private static final LinkedHashMap<Long, Tile> TILES = new LinkedHashMap<>(128, 0.75f, true);
	/** Tiles with a file on disk. */
	private static final Set<Long> ON_DISK = new HashSet<>();
	/** Tiles being read in the background, and those that have arrived. */
	private static final Set<Long> LOADING = new HashSet<>();
	private static final ConcurrentLinkedQueue<Arrived> ARRIVED = new ConcurrentLinkedQueue<>();
	private static final Map<Long, Region> REGIONS = new ConcurrentHashMap<>();
	/** Chunks scanned since they were last loaded, and those scanned before the chunk north of them had loaded. */
	private static final Set<Long> SCANNED = new HashSet<>();
	private static final Set<Long> NORTHLESS = new HashSet<>();
	/** One thread reads and writes the files, in the order asked (so a tile saved and asked for again is read whole). */
	private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Hellcraft map files");
		t.setDaemon(true);
		return t;
	});
	@Nullable
	private static volatile Path folder;
	private static int cap = MIN_CAP;
	private static int ticks;
	private static long lastSave;

	public static void register() {
		ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
			SCANNED.remove(chunk.getPos().pack());
			NORTHLESS.remove(chunk.getPos().pack());
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> open(client));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> close());
		ClientTickEvents.END_CLIENT_TICK.register(MapTiles::tick);
		// leaving the game: wait (a few seconds at most) for what's still to be written
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			close();
			Future<?> written = IO.submit(() -> {
			});
			try {
				written.get(5, TimeUnit.SECONDS);
			} catch (Exception e) {
				HellcraftMod.LOGGER.warn("The explored map wasn't all saved: " + e);
			}
		});
	}

	private static long key(int x, int z) {
		return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
	}

	// ------------------------------------------------------------------ the world's folder

	/** Which world this is, as a folder name: the server's address, or the single-player save's folder. */
	private static String worldKey(Minecraft mc) {
		String name;
		if (mc.getSingleplayerServer() != null) {
			name = "local_" + mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
		} else {
			ServerData server = mc.getCurrentServer();
			name = "server_" + (server == null ? "unknown" : server.ip);
		}
		return name.replaceAll("[^A-Za-z0-9._-]", "_");
	}

	private static void open(Minecraft mc) {
		close();
		Path dir = mc.gameDirectory.toPath().resolve("hellcraft").resolve("maps").resolve(worldKey(mc));
		folder = dir;
		List<int[]> regionFiles = new ArrayList<>();
		List<int[]> tileFiles = new ArrayList<>();
		try {
			if (Files.isDirectory(dir)) {
				try (var files = Files.list(dir)) {
					files.forEach(f -> {
						String[] parts = f.getFileName().toString().split("\\.");
						if (parts.length != 4 || !parts[3].equals("bin")) {
							return;
						}
						try {
							int[] at = {Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
							if (parts[0].equals("r")) {
								tileFiles.add(at);
								ON_DISK.add(key(at[0], at[1]));
							} else if (parts[0].equals("o")) {
								regionFiles.add(at);
							}
						} catch (NumberFormatException ignored) {
							// not one of ours
						}
					});
				}
			}
		} catch (IOException e) {
			HellcraftMod.LOGGER.warn("Couldn't read the explored map: " + e);
		}
		// the overview is read whole, in the background; a map explored before 3.4 has none, so it is made from its tiles
		IO.execute(() -> {
			for (int[] at : regionFiles) {
				Region r = loadRegion(dir, at[0], at[1]);
				if (r != null && dir.equals(folder)) {
					REGIONS.putIfAbsent(key(at[0], at[1]), r);
				}
			}
			if (regionFiles.isEmpty() && !tileFiles.isEmpty()) {
				HellcraftMod.LOGGER.info("Making the Map of Hell's overview from {} explored tiles", tileFiles.size());
				for (int[] at : tileFiles) {
					Tile t = loadTile(dir, at[0], at[1]);
					if (t == null || !dir.equals(folder)) {
						continue;
					}
					for (int cz = 0; cz < SIZE / 16; cz++) {
						for (int cx = 0; cx < SIZE / 16; cx++) {
							overview(t, t.minX() + cx * 16, t.minZ() + cz * 16);
						}
					}
				}
			}
		});
	}

	private static void close() {
		saveAll();
		for (Tile t : TILES.values()) {
			release(t);
		}
		for (Region r : REGIONS.values()) {
			if (r.texture != null) {
				Minecraft.getInstance().getTextureManager().release(r.id);
				r.texture = null;
			}
		}
		folder = null;
		TILES.clear();
		REGIONS.clear();
		ON_DISK.clear();
		LOADING.clear();
		ARRIVED.clear();
		SCANNED.clear();
		NORTHLESS.clear();
		cap = MIN_CAP;
	}

	private static void release(Tile t) {
		if (t.texture != null) {
			Minecraft.getInstance().getTextureManager().release(t.id);
			t.texture = null;
		}
	}

	// ------------------------------------------------------------------ the detail tiles in memory

	/** Asks for a tile on disk to be read in the background (it arrives in a later tick). */
	private static void request(int tx, int tz) {
		long k = key(tx, tz);
		Path dir = folder;
		if (dir == null || !LOADING.add(k)) {
			return;
		}
		IO.execute(() -> ARRIVED.add(new Arrived(dir, k, loadTile(dir, tx, tz))));
	}

	/** Takes in the tiles that have been read. */
	private static void arrivals() {
		for (Arrived a = ARRIVED.poll(); a != null; a = ARRIVED.poll()) {
			if (!a.dir().equals(folder)) {
				continue;
			}
			LOADING.remove(a.key());
			if (a.tile() == null) {
				// unreadable: forget the file, the ground will be scanned afresh
				ON_DISK.remove(a.key());
			} else if (!TILES.containsKey(a.key())) {
				TILES.put(a.key(), a.tile());
			}
		}
	}

	/** Drops the least lately used tiles past the cap (saving them first if they've changed). */
	private static void evict() {
		Iterator<Map.Entry<Long, Tile>> it = TILES.entrySet().iterator();
		while (TILES.size() > cap && it.hasNext()) {
			Map.Entry<Long, Tile> e = it.next();
			Tile t = e.getValue();
			if (t.unsaved) {
				save(t);
			}
			release(t);
			it.remove();
		}
	}

	/** The tile to scan a chunk into: null while it is still being read from disk. */
	@Nullable
	private static Tile tileForScan(int tx, int tz) {
		long k = key(tx, tz);
		Tile t = TILES.get(k);
		if (t != null) {
			return t;
		}
		if (ON_DISK.contains(k)) {
			request(tx, tz);
			return null;
		}
		t = new Tile(tx, tz, new byte[SIZE * SIZE]);
		TILES.put(k, t);
		return t;
	}

	/** True if detail should be drawn for this view: zoomed in enough, and no more tiles in view than may be kept. */
	public static boolean detail(double minX, double minZ, double maxX, double maxZ, double scale) {
		return scale <= DETAIL_SCALE && inView(minX, minZ, maxX, maxZ) <= MAX_VIEW;
	}

	private static int inView(double minX, double minZ, double maxX, double maxZ) {
		int nx = Math.floorDiv((int) Math.floor(maxX), SIZE) - Math.floorDiv((int) Math.floor(minX), SIZE) + 1;
		int nz = Math.floorDiv((int) Math.floor(maxZ), SIZE) - Math.floorDiv((int) Math.floor(minZ), SIZE) + 1;
		return nx * nz;
	}

	/**
	 * The detail tiles in memory that overlap this area (blocks); those on disk are asked for and arrive in a tick or
	 * two (the overview shows meanwhile). Keeps the cap wide enough for the view.
	 */
	public static List<Tile> detailIn(double minX, double minZ, double maxX, double maxZ) {
		arrivals();
		cap = Math.max(MIN_CAP, inView(minX, minZ, maxX, maxZ) + SPARE);
		List<Tile> out = new ArrayList<>();
		int tx0 = Math.floorDiv((int) Math.floor(minX), SIZE);
		int tz0 = Math.floorDiv((int) Math.floor(minZ), SIZE);
		int tx1 = Math.floorDiv((int) Math.floor(maxX), SIZE);
		int tz1 = Math.floorDiv((int) Math.floor(maxZ), SIZE);
		for (int tx = tx0; tx <= tx1; tx++) {
			for (int tz = tz0; tz <= tz1; tz++) {
				long k = key(tx, tz);
				Tile t = TILES.get(k);
				if (t != null) {
					out.add(t);
				} else if (ON_DISK.contains(k)) {
					request(tx, tz);
				}
			}
		}
		return out;
	}

	/** The overview's regions that overlap this area (blocks). */
	public static List<Region> regionsIn(double minX, double minZ, double maxX, double maxZ) {
		List<Region> out = new ArrayList<>();
		int rx0 = Math.floorDiv((int) Math.floor(minX), REGION);
		int rz0 = Math.floorDiv((int) Math.floor(minZ), REGION);
		int rx1 = Math.floorDiv((int) Math.floor(maxX), REGION);
		int rz1 = Math.floorDiv((int) Math.floor(maxZ), REGION);
		for (int rx = rx0; rx <= rx1; rx++) {
			for (int rz = rz0; rz <= rz1; rz++) {
				Region r = REGIONS.get(key(rx, rz));
				if (r != null) {
					out.add(r);
				}
			}
		}
		return out;
	}

	/** True if the column at x, z has been seen. */
	public static boolean seen(int x, int z) {
		Region r = REGIONS.get(key(Math.floorDiv(x, REGION), Math.floorDiv(z, REGION)));
		return r != null && r.cells[cell(x, z)] != 0;
	}

	private static int cell(int x, int z) {
		return Math.floorMod(z, REGION) / CELL * REGION_CELLS + Math.floorMod(x, REGION) / CELL;
	}

	/** How many detail tiles are in memory, and how many may be (for the tests). */
	public static int loaded() {
		return TILES.size();
	}

	public static int cap() {
		return cap;
	}

	/** How many detail tiles this world has on disk or in memory (for the tests). */
	public static int explored() {
		Set<Long> all = new HashSet<>(ON_DISK);
		all.addAll(TILES.keySet());
		return all.size();
	}

	/**
	 * For the tests: marks a square of tiles explored, in broad stripes of colour, as if someone had walked the lot (the
	 * tiles are saved and dropped like any others).
	 */
	public static void pretendExplored(int tx0, int tz0, int tx1, int tz1) {
		MapColor[] stripes = {MapColor.GRASS, MapColor.STONE, MapColor.SAND, MapColor.NETHER, MapColor.COLOR_PURPLE, MapColor.WATER};
		for (int tx = tx0; tx <= tx1; tx++) {
			for (int tz = tz0; tz <= tz1; tz++) {
				long k = key(tx, tz);
				if (TILES.containsKey(k) || ON_DISK.contains(k)) {
					continue;
				}
				byte[] colours = new byte[SIZE * SIZE];
				for (int i = 0; i < colours.length; i++) {
					int x = i % SIZE;
					int z = i / SIZE;
					MapColor c = stripes[Math.floorMod(tx + tz + (x + z) / 64, stripes.length)];
					colours[i] = c.getPackedId((x / 4 + z / 4) % 3 == 0 ? MapColor.Brightness.HIGH : MapColor.Brightness.NORMAL);
				}
				Tile t = new Tile(tx, tz, colours);
				t.unsaved = true;
				TILES.put(k, t);
				for (int cz = 0; cz < SIZE / 16; cz++) {
					for (int cx = 0; cx < SIZE / 16; cx++) {
						overview(t, t.minX() + cx * 16, t.minZ() + cz * 16);
					}
				}
				evict();
			}
		}
	}

	// ------------------------------------------------------------------ scanning

	private static void tick(Minecraft mc) {
		arrivals();
		ClientLevel level = mc.level;
		if (folder == null || level == null || !ClientNav.inHell(mc)) {
			evict();
			return;
		}
		if (!(mc.gui.screen() instanceof MapOfHellScreen)) {
			cap = MIN_CAP;
		}
		ticks++;
		int pcx = mc.player.chunkPosition().x();
		int pcz = mc.player.chunkPosition().z();
		// every second the chunks round you again (building, digging), and a few more you haven't scanned yet
		if (ticks % 20 == 0) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					LevelChunk chunk = level.getChunkSource().getChunk(pcx + dx, pcz + dz, false);
					if (chunk != null && !chunk.isEmpty()) {
						scan(level, chunk);
					}
				}
			}
		}
		if (ticks % 2 == 0) {
			int budget = 6;
			int reach = Math.min(mc.options.getEffectiveRenderDistance(), 32);
			for (int[] off : spiral(reach)) {
				int cx = pcx + off[0];
				int cz = pcz + off[1];
				if (SCANNED.contains(ChunkPos.pack(cx, cz))) {
					continue;
				}
				LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
				if (chunk == null || chunk.isEmpty()) {
					continue;
				}
				scan(level, chunk);
				if (--budget == 0) {
					break;
				}
			}
		}
		evict();
		long now = System.currentTimeMillis();
		if (now - lastSave > 10_000) {
			lastSave = now;
			saveAll();
		}
	}

	private static List<int[]> spiralCache = List.of();
	private static int spiralReach = -1;

	/** Chunk offsets within reach, nearest first. */
	private static List<int[]> spiral(int reach) {
		if (reach != spiralReach) {
			List<int[]> out = new ArrayList<>();
			for (int dx = -reach; dx <= reach; dx++) {
				for (int dz = -reach; dz <= reach; dz++) {
					if (dx * dx + dz * dz <= reach * reach) {
						out.add(new int[]{dx, dz});
					}
				}
			}
			out.sort(Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1]));
			spiralCache = out;
			spiralReach = reach;
		}
		return spiralCache;
	}

	/**
	 * The top of a column as a map sees it: the highest block with a map colour. Leaves {@code pos} on it and returns its
	 * height (the world's floor if there is none).
	 */
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

	/**
	 * Records the top of every column of a chunk (unless its tile is still being read from disk: then it is left for a
	 * later tick). Each column is shaded by its slope, against the column to the north, as vanilla maps are.
	 */
	public static void scan(ClientLevel level, LevelChunk chunk) {
		int x0 = chunk.getPos().getMinBlockX();
		int z0 = chunk.getPos().getMinBlockZ();
		Tile t = tileForScan(Math.floorDiv(x0, SIZE), Math.floorDiv(z0, SIZE));
		if (t == null) {
			return;
		}
		long chunkKey = chunk.getPos().pack();
		SCANNED.add(chunkKey);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
		int minY = level.getMinY();
		// the row north of this chunk, from the chunk north of it if that is loaded
		int[] north = new int[16];
		LevelChunk northChunk = level.getChunkSource().getChunk(chunk.getPos().x(), chunk.getPos().z() - 1, false);
		boolean northless = northChunk == null || northChunk.isEmpty();
		if (!northless) {
			for (int dx = 0; dx < 16; dx++) {
				north[dx] = top(level, northChunk, pos, x0 + dx, z0 - 1);
			}
			NORTHLESS.remove(chunkKey);
		} else {
			NORTHLESS.add(chunkKey);
		}
		boolean changed = false;
		for (int dz = 0; dz < 16; dz++) {
			for (int dx = 0; dx < 16; dx++) {
				int x = x0 + dx;
				int z = z0 + dz;
				int y = top(level, chunk, pos, x, z);
				BlockState state = y <= minY ? Blocks.BEDROCK.defaultBlockState() : chunk.getBlockState(pos);
				int depth = 0;
				if (y > minY && !state.getFluidState().isEmpty()) {
					// how deep the water (or lava) is, for its shading
					int sy = y - 1;
					below.set(pos);
					BlockState under;
					do {
						below.setY(sy--);
						under = chunk.getBlockState(below);
						depth++;
					} while (sy > minY && !under.getFluidState().isEmpty() && depth < 16);
					if (!state.isFaceSturdy(level, pos, Direction.UP)) {
						state = state.getFluidState().createLegacyBlock();
					}
				}
				MapColor colour = state.getMapColor(level, pos);
				int northHeight = dz == 0 && northless ? y : north[dx];
				north[dx] = y;
				int checker = (x + z) & 1;
				MapColor.Brightness b;
				if (colour == MapColor.WATER) {
					double diff = depth * 0.1 + checker * 0.2;
					b = diff < 0.5 ? MapColor.Brightness.HIGH : diff > 0.9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
				} else {
					double diff = (y - northHeight) * 0.8 + (checker - 0.5) * 0.4;
					b = diff > 0.6 ? MapColor.Brightness.HIGH : diff < -0.6 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
				}
				byte packed = colour == MapColor.NONE ? MapColor.STONE.getPackedId(b) : colour.getPackedId(b);
				int i = Math.floorMod(z, SIZE) * SIZE + Math.floorMod(x, SIZE);
				if (t.colours[i] != packed) {
					t.colours[i] = packed;
					changed = true;
				}
			}
		}
		if (changed) {
			t.dirty = true;
			t.unsaved = true;
			overview(t, x0, z0);
		}
		// the chunk south of this one was shaded without it: do it again now
		long southKey = ChunkPos.pack(chunk.getPos().x(), chunk.getPos().z() + 1);
		if (NORTHLESS.contains(southKey)) {
			LevelChunk south = level.getChunkSource().getChunk(chunk.getPos().x(), chunk.getPos().z() + 1, false);
			if (south != null && !south.isEmpty()) {
				scan(level, south);
			} else {
				NORTHLESS.remove(southKey);
			}
		}
	}

	/** Brings the overview up to date for the chunk at x0, z0 of a tile: each cell takes its columns' commonest colour. */
	private static void overview(Tile t, int x0, int z0) {
		long k = key(Math.floorDiv(x0, REGION), Math.floorDiv(z0, REGION));
		Region r = REGIONS.computeIfAbsent(k, kk -> new Region(Math.floorDiv(x0, REGION), Math.floorDiv(z0, REGION), new byte[REGION_CELLS * REGION_CELLS]));
		byte[] in = new byte[CELL * CELL];
		boolean changed = false;
		for (int cz = 0; cz < 16; cz += CELL) {
			for (int cx = 0; cx < 16; cx += CELL) {
				int n = 0;
				for (int dz = 0; dz < CELL; dz++) {
					for (int dx = 0; dx < CELL; dx++) {
						byte c = t.colours[Math.floorMod(z0 + cz + dz, SIZE) * SIZE + Math.floorMod(x0 + cx + dx, SIZE)];
						if (c != 0) {
							in[n++] = c;
						}
					}
				}
				byte best = 0;
				int bestCount = 0;
				for (int a = 0; a < n && bestCount <= n / 2; a++) {
					int count = 0;
					for (int b = 0; b < n; b++) {
						if (in[b] == in[a]) {
							count++;
						}
					}
					if (count > bestCount) {
						bestCount = count;
						best = in[a];
					}
				}
				int i = cell(x0 + cx, z0 + cz);
				if (r.cells[i] != best) {
					r.cells[i] = best;
					changed = true;
				}
			}
		}
		if (changed) {
			r.dirty = true;
			r.unsaved = true;
		}
	}

	// ------------------------------------------------------------------ textures

	/** The tile's texture, uploaded afresh if it changed. Render thread only. */
	public static Identifier texture(Tile t) {
		if (t.texture == null) {
			t.id = HellcraftMod.id("map_tile/" + name(t.tx) + "_" + name(t.tz));
			t.texture = new DynamicTexture(() -> "Hellcraft map tile", SIZE, SIZE, true);
			Minecraft.getInstance().getTextureManager().register(t.id, t.texture);
			t.dirty = true;
		}
		if (t.dirty) {
			t.dirty = false;
			fill(t.texture, t.colours, SIZE);
		}
		return t.id;
	}

	/** The overview region's texture, uploaded afresh if it changed. Render thread only. */
	public static Identifier texture(Region r) {
		if (r.texture == null) {
			r.id = HellcraftMod.id("map_region/" + name(r.rx) + "_" + name(r.rz));
			r.texture = new DynamicTexture(() -> "Hellcraft map overview", REGION_CELLS, REGION_CELLS, true);
			Minecraft.getInstance().getTextureManager().register(r.id, r.texture);
			r.dirty = true;
		}
		if (r.dirty) {
			r.dirty = false;
			fill(r.texture, r.cells, REGION_CELLS);
		}
		return r.id;
	}

	private static String name(int n) {
		return Integer.toString(n).replace('-', 'm');
	}

	private static void fill(DynamicTexture texture, byte[] packed, int size) {
		NativeImage image = texture.getPixels();
		for (int i = 0; i < size * size; i++) {
			image.setPixel(i % size, i / size, packed[i] == 0 ? 0 : 0xFF000000 | MapColor.getColorFromPackedId(packed[i]));
		}
		texture.upload();
	}

	// ------------------------------------------------------------------ files

	private static void saveAll() {
		Path dir = folder;
		if (dir == null) {
			return;
		}
		for (Tile t : TILES.values()) {
			if (t.unsaved) {
				save(t);
			}
		}
		for (Region r : REGIONS.values()) {
			if (r.unsaved) {
				r.unsaved = false;
				byte[] cells = r.cells.clone();
				IO.execute(() -> write(dir, dir.resolve("o." + r.rx + "." + r.rz + ".bin"), REGION_VERSION, cells));
			}
		}
	}

	private static void save(Tile t) {
		Path dir = folder;
		if (dir == null) {
			return;
		}
		t.unsaved = false;
		ON_DISK.add(key(t.tx, t.tz));
		byte[] colours = t.colours.clone();
		IO.execute(() -> write(dir, dir.resolve("r." + t.tx + "." + t.tz + ".bin"), TILE_VERSION, colours));
	}

	private static void write(Path dir, Path to, int version, byte[] bytes) {
		try {
			Files.createDirectories(dir);
			Path tmp = to.resolveSibling(to.getFileName() + ".tmp");
			try (OutputStream raw = Files.newOutputStream(tmp); DataOutputStream out = new DataOutputStream(new GZIPOutputStream(raw))) {
				out.writeInt(version);
				out.write(bytes);
			}
			Files.move(tmp, to, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			HellcraftMod.LOGGER.warn("Couldn't save the explored map: " + e);
		}
	}

	/** Reads a detail tile (3.3's files held each column's height as well: that is skipped). */
	@Nullable
	private static Tile loadTile(Path dir, int tx, int tz) {
		byte[] colours = read(dir.resolve("r." + tx + "." + tz + ".bin"), SIZE * SIZE, 1, TILE_VERSION);
		return colours == null ? null : new Tile(tx, tz, colours);
	}

	@Nullable
	private static Region loadRegion(Path dir, int rx, int rz) {
		byte[] cells = read(dir.resolve("o." + rx + "." + rz + ".bin"), REGION_CELLS * REGION_CELLS, REGION_VERSION, REGION_VERSION);
		return cells == null ? null : new Region(rx, rz, cells);
	}

	@Nullable
	private static byte[] read(Path file, int length, int oldest, int newest) {
		try (InputStream raw = Files.newInputStream(file); DataInputStream in = new DataInputStream(new GZIPInputStream(raw))) {
			int version = in.readInt();
			if (version < oldest || version > newest) {
				return null;
			}
			byte[] bytes = new byte[length];
			in.readFully(bytes);
			return bytes;
		} catch (IOException e) {
			HellcraftMod.LOGGER.warn("Couldn't read the explored map file " + file.getFileName() + ": " + e);
			return null;
		}
	}
}
