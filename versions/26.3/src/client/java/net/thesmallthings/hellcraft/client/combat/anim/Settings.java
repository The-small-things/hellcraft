package net.thesmallthings.hellcraft.client.combat.anim;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * This player's own animation settings, kept in config/hellcraft-client.json (each player chooses for themselves; the
 * server has no say): `smoothAnimations` (on by default) draws every player's walking, running, jumping, landing and
 * item use with Hellcraft's animations ({@link Locomotion}, the item stances) and eases vanilla's sudden pose changes
 * ({@link PoseSmoother}). Changed in game with /hellcraft-client animations on|off. `healthBars` (`above` by default,
 * `side` or `off`) places the mobs' health bars (layer/MobHealthBars): /hellcraft-client healthbars above|side|off.
 * The minimap's settings (client hud/Minimap) live here too, under `minimap*`: /hellcraft-client minimap, or its settings
 * screen.
 */
public final class Settings {
	private static boolean loaded;
	private static boolean smooth = true;
	private static HealthBars bars = HealthBars.ABOVE;
	private static final MapPrefs minimap = new MapPrefs();

	/** The minimap's settings (client hud/Minimap). Change a field, then call {@link #save()}. */
	public static MapPrefs minimap() {
		load();
		return minimap;
	}

	/** The minimap's settings, after Xaero's Minimap: what each player chooses for their own screen. */
	public static final class MapPrefs {
		public boolean on = true;
		/** Turn with the player (forward is up), or keep north up. */
		public boolean rotate = true;
		public Shape shape = Shape.CIRCLE;
		/** Its width on screen, in GUI pixels. */
		public int size = 84;
		/** Which of {@link #ZOOMS} (blocks per screen pixel). */
		public int zoom = 3;
		public Caves caves = Caves.AUTO;
		public Corner corner = Corner.TOP_LEFT;
		public boolean coordinates = true;
		public boolean players = true;
		public boolean monsters = true;
		public boolean animals = true;
		public boolean waymarks = true;

		/**
		 * Blocks per screen pixel (not GUI pixel), so a block is always a whole number of the screen's pixels, every block the
		 * same size: 8, 6, 4, 3, 2 and 1 pixels a block, then 2 and 4 blocks a pixel (averaged, so nothing flickers).
		 */
		public static final float[] ZOOMS = {1 / 8f, 1 / 6f, 1 / 4f, 1 / 3f, 1 / 2f, 1f, 2f, 4f};
		public static final int MIN_SIZE = 48;
		public static final int MAX_SIZE = 160;

		/** Blocks per screen pixel. */
		public float blocksPerScreenPixel() {
			return ZOOMS[Math.max(0, Math.min(ZOOMS.length - 1, zoom))];
		}

		/** Blocks per GUI pixel, at the given GUI scale. */
		public float blocksPerPixel(int guiScale) {
			return blocksPerScreenPixel() * Math.max(1, guiScale);
		}
	}

	public enum Shape {
		CIRCLE, SQUARE
	}

	/** Cave view: AUTO (underground, or under the Forge of Dis's roof), always, or never. */
	public enum Caves {
		AUTO, ON, OFF
	}

	public enum Corner {
		TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
	}

	/** Where mobs' health bars go. */
	public enum HealthBars {
		ABOVE, SIDE, OFF
	}

	private Settings() {
	}

	public static boolean smoothAnimations() {
		load();
		return smooth;
	}

	public static void setSmoothAnimations(boolean on) {
		load();
		smooth = on;
		save();
	}

	public static HealthBars healthBars() {
		load();
		return bars;
	}

	public static void setHealthBars(HealthBars mode) {
		load();
		bars = mode;
		save();
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("hellcraft-client.json");
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		Path path = file();
		if (!Files.exists(path)) {
			return;
		}
		try {
			JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
			if (json.has("smoothAnimations")) {
				smooth = json.get("smoothAnimations").getAsBoolean();
			}
			if (json.has("minimap") && json.get("minimap").isJsonObject()) {
				JsonObject m = json.getAsJsonObject("minimap");
				minimap.on = bool(m, "on", minimap.on);
				minimap.rotate = bool(m, "rotate", minimap.rotate);
				minimap.coordinates = bool(m, "coordinates", minimap.coordinates);
				minimap.players = bool(m, "players", minimap.players);
				minimap.monsters = bool(m, "monsters", minimap.monsters);
				minimap.animals = bool(m, "animals", minimap.animals);
				minimap.waymarks = bool(m, "waymarks", minimap.waymarks);
				if (m.has("size")) {
					minimap.size = Math.max(MapPrefs.MIN_SIZE, Math.min(MapPrefs.MAX_SIZE, m.get("size").getAsInt()));
				}
				if (m.has("zoom")) {
					minimap.zoom = Math.max(0, Math.min(MapPrefs.ZOOMS.length - 1, m.get("zoom").getAsInt()));
				}
				minimap.shape = word(m, "shape", Shape.class, minimap.shape);
				minimap.caves = word(m, "caves", Caves.class, minimap.caves);
				minimap.corner = word(m, "corner", Corner.class, minimap.corner);
			}
			if (json.has("healthBars")) {
				try {
					bars = HealthBars.valueOf(json.get("healthBars").getAsString().toUpperCase(java.util.Locale.ROOT));
				} catch (IllegalArgumentException e) {
					// an unknown word: keep the default
				}
			}
		} catch (IOException | RuntimeException e) {
			// a broken file: keep the defaults, and the next save writes a good one
		}
	}

	private static boolean bool(JsonObject o, String key, boolean fallback) {
		return o.has(key) ? o.get(key).getAsBoolean() : fallback;
	}

	private static <E extends Enum<E>> E word(JsonObject o, String key, Class<E> type, E fallback) {
		if (!o.has(key)) {
			return fallback;
		}
		try {
			return Enum.valueOf(type, o.get(key).getAsString().toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return fallback;
		}
	}

	public static void save() {
		load();
		JsonObject json = new JsonObject();
		json.addProperty("smoothAnimations", smooth);
		json.addProperty("healthBars", bars.name().toLowerCase(java.util.Locale.ROOT));
		JsonObject m = new JsonObject();
		m.addProperty("on", minimap.on);
		m.addProperty("rotate", minimap.rotate);
		m.addProperty("shape", minimap.shape.name().toLowerCase(java.util.Locale.ROOT));
		m.addProperty("size", minimap.size);
		m.addProperty("zoom", minimap.zoom);
		m.addProperty("caves", minimap.caves.name().toLowerCase(java.util.Locale.ROOT));
		m.addProperty("corner", minimap.corner.name().toLowerCase(java.util.Locale.ROOT));
		m.addProperty("coordinates", minimap.coordinates);
		m.addProperty("players", minimap.players);
		m.addProperty("monsters", minimap.monsters);
		m.addProperty("animals", minimap.animals);
		m.addProperty("waymarks", minimap.waymarks);
		json.add("minimap", m);
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), json + "\n");
		} catch (IOException e) {
			// not saved: it still holds for this session
		}
	}
}
