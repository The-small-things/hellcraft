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
 * ({@link PoseSmoother}). Changed in game with /hellcraft-client animations on|off.
 */
public final class Settings {
	private static boolean loaded;
	private static boolean smooth = true;

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
		} catch (IOException | RuntimeException e) {
			// a broken file: keep the defaults, and the next save writes a good one
		}
	}

	private static void save() {
		JsonObject json = new JsonObject();
		json.addProperty("smoothAnimations", smooth);
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), json + "\n");
		} catch (IOException e) {
			// not saved: it still holds for this session
		}
	}
}
