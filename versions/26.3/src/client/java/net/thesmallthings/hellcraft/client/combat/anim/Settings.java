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
 */
public final class Settings {
	private static boolean loaded;
	private static boolean smooth = true;
	private static HealthBars bars = HealthBars.ABOVE;

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

	private static void save() {
		JsonObject json = new JsonObject();
		json.addProperty("smoothAnimations", smooth);
		json.addProperty("healthBars", bars.name().toLowerCase(java.util.Locale.ROOT));
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), json + "\n");
		} catch (IOException e) {
			// not saved: it still holds for this session
		}
	}
}
