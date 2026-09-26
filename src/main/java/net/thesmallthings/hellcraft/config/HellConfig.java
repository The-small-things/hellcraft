package net.thesmallthings.hellcraft.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.thesmallthings.hellcraft.HellcraftMod;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Server config, stored as {@code config/hellcraft.json}. Missing keys fall back to these defaults. */
public class HellConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static HellConfig instance = new HellConfig();

	// --- Lifesteal
	/** Hearts a brand-new player starts with. */
	public int startHearts = 10;
	/** Most hearts anyone can hold. */
	public int maxHearts = 20;
	/** When you die to anything but a player, your lost heart drops where you fell. */
	public boolean naturalDeathDropsHeart = true;
	/** Blood Hearts consumed at an altar to bring a ghost back. */
	public int reviveCostHearts = 4;
	/** Hearts a revived player comes back with. */
	public int reviveHearts = 3;
	/** Blood Fragments that clot into one Blood Heart. */
	public int fragmentsPerHeart = 8;
	/** Chance a hostile mob killed by a player drops a fragment in the outermost circles... */
	public double fragmentChanceBase = 0.02;
	/** ...plus this much per circle of depth (Cocytus is depth 9). */
	public double fragmentChancePerDepth = 0.011;

	// --- Hell is full
	/** Multiplier on the hostile mob cap. */
	public double monsterCapMultiplier = 1.75;
	/** Eliminated players rise as named undead wearing their gear. */
	public boolean revenants = true;
	/** Ghosts may not wander further than this from where they died. */
	public int ghostTetherRadius = 48;

	// --- Altars & hazards
	public int wardMinutes = 30;
	public boolean circleHazards = true;
	public boolean circleTitles = true;
	/** Extra max health per circle of depth for hostile mobs (0.06 = +6% per circle). */
	public double mobHealthPerDepth = 0.06;

	// --- Lucifer
	public boolean lucifer = true;
	/** Minutes before Lucifer can be fought again after he is defeated. */
	public int luciferCooldownMinutes = 120;
	/** Minutes before he returns after everyone in the arena died or fled. */
	public int luciferRetryMinutes = 5;
	/** Health of his first form (the fallen seraph). */
	public double luciferAvatarHealth = 500;
	/** Health of his true, three-faced form. */
	public double luciferHealth = 600;
	/** Extra heart capacity each player earns the first time they help defeat him. */
	public int luciferMaxHeartBonus = 2;

	public static HellConfig get() {
		return instance;
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("hellcraft.json");
	}

	public static void load() {
		Path path = path();
		HellConfig loaded = null;
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				loaded = GSON.fromJson(reader, HellConfig.class);
			} catch (Exception e) {
				HellcraftMod.LOGGER.error("Could not read {}, using defaults", path, e);
			}
		}
		instance = loaded != null ? loaded : new HellConfig();
		instance.sanitize();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			HellcraftMod.LOGGER.warn("Could not write {}", path, e);
		}
	}

	private void sanitize() {
		maxHearts = Math.max(1, maxHearts);
		startHearts = Math.max(1, Math.min(startHearts, maxHearts));
		reviveHearts = Math.max(1, Math.min(reviveHearts, maxHearts));
		reviveCostHearts = Math.max(0, reviveCostHearts);
		fragmentsPerHeart = Math.max(1, fragmentsPerHeart);
		monsterCapMultiplier = Math.max(0.1, monsterCapMultiplier);
		ghostTetherRadius = Math.max(4, ghostTetherRadius);
	}
}
