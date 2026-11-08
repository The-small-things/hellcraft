package net.thesmallthings.hellcraft.client.nav;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * The ground you've seen, for the Map of Hell: every column of the Inferno's overworld the client has had loaded, as the
 * colour of its top block (its vanilla map colour, shaded by the slope like a vanilla map). Kept in tiles of
 * {@link #SIZE} blocks, saved on this computer per world under {@code hellcraft/maps/<world>/}, and drawn over the
 * painted map (MapOfHellScreen), so where you've been the map shows the real ground, buildings and all.
 */
public final class MapTiles {
	private MapTiles() {
	}

	/** A tile's width in blocks (and in pixels: a block a pixel). */
	public static final int SIZE = 256;
	/** The small copy drawn when zoomed out: a pixel for every {@link #SHRINK} blocks. */
	public static final int SHRINK = 8;
	public static final int SMALL = SIZE / SHRINK;
	private static final int FILE_VERSION = 1;

	/** One tile: each column's packed map colour (0: not seen) and height; its textures once drawn. */
	public static final class Tile {
		final int tx;
		final int tz;
		final byte[] colours = new byte[SIZE * SIZE];
		final short[] heights = new short[SIZE * SIZE];
		boolean dirty;
		boolean unsaved;
		DynamicTexture texture;
		DynamicTexture small;
		Identifier id;
		Identifier smallId;

		Tile(int tx, int tz) {
			this.tx = tx;
			this.tz = tz;
		}

		public int minX() {
			return tx * SIZE;
		}

		public int minZ() {
			return tz * SIZE;
		}
	}

	private static final Map<Long, Tile> TILES = new HashMap<>();
	/** Tiles saved on disk but not read yet. */
	private static final Set<Long> ON_DISK = new HashSet<>();
	/** Chunks scanned since they were last loaded. */
	private static final Set<Long> SCANNED = new HashSet<>();
	private static final ExecutorService SAVER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Hellcraft map saver");
		t.setDaemon(true);
		return t;
	});
	@Nullable
	private static Path folder;
	private static int ticks;
	private static long lastSave;

	public static void register() {
		ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> SCANNED.remove(chunk.getPos().pack()));
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> open(client));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> close());
		ClientTickEvents.END_CLIENT_TICK.register(MapTiles::tick);
	}

	private static long key(int tx, int tz) {
		return ((long) tx << 32) ^ (tz & 0xFFFFFFFFL);
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
		folder = mc.gameDirectory.toPath().resolve("hellcraft").resolve("maps").resolve(worldKey(mc));
		try {
			if (Files.isDirectory(folder)) {
				try (var files = Files.list(folder)) {
					files.forEach(f -> {
						String n = f.getFileName().toString();
						String[] parts = n.split("\\.");
						if (parts.length == 4 && parts[0].equals("r") && parts[3].equals("bin")) {
							try {
								ON_DISK.add(key(Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
							} catch (NumberFormatException ignored) {
								// not one of ours
							}
						}
					});
				}
			}
		} catch (IOException e) {
			HellcraftMod.LOGGER.warn("Couldn't read the explored map: " + e);
		}
	}

	private static void close() {
		saveAll();
		for (Tile t : TILES.values()) {
			if (t.texture != null) {
				Minecraft.getInstance().getTextureManager().release(t.id);
				Minecraft.getInstance().getTextureManager().release(t.smallId);
			}
		}
		TILES.clear();
		ON_DISK.clear();
		SCANNED.clear();
		folder = null;
	}

	// ------------------------------------------------------------------ looking up tiles

	/** The tile holding this block column, read from disk if it was saved, made empty if {@code create}; else null. */
	@Nullable
	private static Tile tile(int tx, int tz, boolean create) {
		long k = key(tx, tz);
		Tile t = TILES.get(k);
		if (t != null) {
			return t;
		}
		if (ON_DISK.remove(k)) {
			t = load(tx, tz);
			if (t != null) {
				TILES.put(k, t);
				return t;
			}
		}
		if (!create) {
			return null;
		}
		t = new Tile(tx, tz);
		TILES.put(k, t);
		return t;
	}

	/** Every tile that has anything in it and overlaps this area (blocks). */
	public static List<Tile> tilesIn(double minX, double minZ, double maxX, double maxZ) {
		List<Tile> out = new ArrayList<>();
		int tx0 = Math.floorDiv((int) Math.floor(minX), SIZE);
		int tz0 = Math.floorDiv((int) Math.floor(minZ), SIZE);
		int tx1 = Math.floorDiv((int) Math.floor(maxX), SIZE);
		int tz1 = Math.floorDiv((int) Math.floor(maxZ), SIZE);
		for (int tx = tx0; tx <= tx1; tx++) {
			for (int tz = tz0; tz <= tz1; tz++) {
				if (TILES.containsKey(key(tx, tz)) || ON_DISK.contains(key(tx, tz))) {
					Tile t = tile(tx, tz, false);
					if (t != null) {
						out.add(t);
					}
				}
			}
		}
		return out;
	}

	/** True if the column at x, z has been seen. */
	public static boolean seen(int x, int z) {
		Tile t = tile(Math.floorDiv(x, SIZE), Math.floorDiv(z, SIZE), false);
		return t != null && t.colours[Math.floorMod(z, SIZE) * SIZE + Math.floorMod(x, SIZE)] != 0;
	}

	/** How many tiles hold explored ground (for the tests). */
	public static int count() {
		return TILES.size() + ON_DISK.size();
	}

	// ------------------------------------------------------------------ scanning

	private static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (folder == null || !ClientNav.inHell(mc) || level == null) {
			return;
		}
		ticks++;
		int pcx = mc.player.chunkPosition().x();
		int pcz = mc.player.chunkPosition().z();
		// every second the chunks round you again (building, digging), and a few more you haven't scanned yet
		if (ticks % 20 == 0) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					LevelChunk chunk = level.getChunkSource().getChunk(pcx + dx, pcz + dz, false);
					if (chunk != null) {
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

	/** Records the top of every column of a chunk. */
	public static void scan(ClientLevel level, LevelChunk chunk) {
		SCANNED.add(chunk.getPos().pack());
		int x0 = chunk.getPos().getMinBlockX();
		int z0 = chunk.getPos().getMinBlockZ();
		Tile t = tile(Math.floorDiv(x0, SIZE), Math.floorDiv(z0, SIZE), true);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
		int minY = level.getMinY();
		boolean changed = false;
		for (int dz = 0; dz < 16; dz++) {
			for (int dx = 0; dx < 16; dx++) {
				int x = x0 + dx;
				int z = z0 + dz;
				int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 1;
				BlockState state;
				int depth = 0;
				if (y <= minY) {
					state = Blocks.BEDROCK.defaultBlockState();
				} else {
					do {
						pos.set(x, --y, z);
						state = chunk.getBlockState(pos);
					} while (state.getMapColor(level, pos) == MapColor.NONE && y > minY);
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
				}
				MapColor colour = state.getMapColor(level, pos);
				int i = Math.floorMod(z, SIZE) * SIZE + Math.floorMod(x, SIZE);
				short height = (short) y;
				// shaded by the slope, against the column to the north (vanilla's way), or by depth for water
				int northHeight = heightAt(t, x, z - 1, height);
				int checker = (x + z) & 1;
				MapColor.Brightness b;
				if (colour == MapColor.WATER) {
					double diff = depth * 0.1 + checker * 0.2;
					b = diff < 0.5 ? MapColor.Brightness.HIGH : diff > 0.9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
				} else {
					double diff = (height - northHeight) * 0.8 + (checker - 0.5) * 0.4;
					b = diff > 0.6 ? MapColor.Brightness.HIGH : diff < -0.6 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
				}
				byte packed = colour == MapColor.NONE ? MapColor.STONE.getPackedId(b) : colour.getPackedId(b);
				if (t.colours[i] != packed || t.heights[i] != height) {
					t.colours[i] = packed;
					t.heights[i] = height;
					changed = true;
				}
			}
		}
		if (changed) {
			t.dirty = true;
			t.unsaved = true;
		}
	}

	/** The height recorded at x, z (the tile may differ from t), or {@code fallback} if it hasn't been seen. */
	private static int heightAt(Tile t, int x, int z, int fallback) {
		Tile at = Math.floorDiv(x, SIZE) == t.tx && Math.floorDiv(z, SIZE) == t.tz ? t : tile(Math.floorDiv(x, SIZE), Math.floorDiv(z, SIZE), false);
		if (at == null) {
			return fallback;
		}
		int i = Math.floorMod(z, SIZE) * SIZE + Math.floorMod(x, SIZE);
		return at.colours[i] == 0 ? fallback : at.heights[i];
	}

	// ------------------------------------------------------------------ textures

	/** The tile's texture (full, or the small copy when zoomed out), uploaded afresh if it changed. Render thread only. */
	public static Identifier texture(Tile t, boolean small) {
		if (t.texture == null) {
			t.id = HellcraftMod.id("map_tile/" + Integer.toString(t.tx).replace('-', 'm') + "_" + Integer.toString(t.tz).replace('-', 'm'));
			t.smallId = HellcraftMod.id("map_tile_small/" + Integer.toString(t.tx).replace('-', 'm') + "_" + Integer.toString(t.tz).replace('-', 'm'));
			t.texture = new DynamicTexture(() -> "Hellcraft map tile", SIZE, SIZE, true);
			t.small = new DynamicTexture(() -> "Hellcraft map tile (small)", SMALL, SMALL, true);
			Minecraft.getInstance().getTextureManager().register(t.id, t.texture);
			Minecraft.getInstance().getTextureManager().register(t.smallId, t.small);
			t.dirty = true;
		}
		if (t.dirty) {
			t.dirty = false;
			NativeImage full = t.texture.getPixels();
			for (int i = 0; i < SIZE * SIZE; i++) {
				full.setPixel(i % SIZE, i / SIZE, t.colours[i] == 0 ? 0 : 0xFF000000 | MapColor.getColorFromPackedId(t.colours[i]));
			}
			t.texture.upload();
			// the small copy: each pixel the average of its square, as see-through as the share of it not yet seen
			NativeImage little = t.small.getPixels();
			for (int sy = 0; sy < SMALL; sy++) {
				for (int sx = 0; sx < SMALL; sx++) {
					long r = 0;
					long g = 0;
					long b = 0;
					int n = 0;
					for (int y = sy * SHRINK; y < sy * SHRINK + SHRINK; y++) {
						for (int x = sx * SHRINK; x < sx * SHRINK + SHRINK; x++) {
							byte c = t.colours[y * SIZE + x];
							if (c != 0) {
								int rgb = MapColor.getColorFromPackedId(c);
								r += (rgb >> 16) & 0xFF;
								g += (rgb >> 8) & 0xFF;
								b += rgb & 0xFF;
								n++;
							}
						}
					}
					int argb = n == 0 ? 0 : ((n * 255 / (SHRINK * SHRINK)) << 24) | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
					little.setPixel(sx, sy, argb);
				}
			}
			t.small.upload();
		}
		return small ? t.smallId : t.id;
	}

	// ------------------------------------------------------------------ saving

	private static Path file(int tx, int tz) {
		return folder.resolve("r." + tx + "." + tz + ".bin");
	}

	private static void saveAll() {
		if (folder == null) {
			return;
		}
		Path dir = folder;
		for (Tile t : TILES.values()) {
			if (!t.unsaved) {
				continue;
			}
			t.unsaved = false;
			byte[] colours = t.colours.clone();
			short[] heights = t.heights.clone();
			Path to = file(t.tx, t.tz);
			SAVER.execute(() -> {
				try {
					Files.createDirectories(dir);
					Path tmp = to.resolveSibling(to.getFileName() + ".tmp");
					try (OutputStream raw = Files.newOutputStream(tmp); DataOutputStream out = new DataOutputStream(new GZIPOutputStream(raw))) {
						out.writeInt(FILE_VERSION);
						out.write(colours);
						for (short h : heights) {
							out.writeShort(h);
						}
					}
					Files.move(tmp, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException e) {
					HellcraftMod.LOGGER.warn("Couldn't save the explored map: " + e);
				}
			});
		}
	}

	@Nullable
	private static Tile load(int tx, int tz) {
		try (InputStream raw = Files.newInputStream(file(tx, tz)); DataInputStream in = new DataInputStream(new GZIPInputStream(raw))) {
			if (in.readInt() != FILE_VERSION) {
				return null;
			}
			Tile t = new Tile(tx, tz);
			in.readFully(t.colours);
			for (int i = 0; i < t.heights.length; i++) {
				t.heights[i] = in.readShort();
			}
			return t;
		} catch (IOException e) {
			HellcraftMod.LOGGER.warn("Couldn't read the explored map tile " + tx + ", " + tz + ": " + e);
			return null;
		}
	}
}
