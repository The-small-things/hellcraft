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
	/** Extra heart capacity granted by each Lucifer's Bane consumed (they stack). */
	public int luciferMaxHeartBonus = 2;
	/** Per returning champion in the fight: extra boss health (0.35 = +35%). */
	public double luciferVeteranHealthBonus = 0.35;
	/** Per returning champion in the fight: extra attack damage (0.2 = +20%). */
	public double luciferVeteranDamageBonus = 0.2;
	/** Most returning champions that can stack difficulty. */
	public int luciferMaxVeteranTiers = 4;

	// --- The Hellcraft resource pack (models, textures, boss music; see README "Resource pack")
	/** Players must accept the pack to join (Lucifer's model and the hell weapons need it). */
	public boolean resourcePackRequired = true;
	/** Port of the built-in web server that hands the resource pack to players. */
	public int musicPackPort = 25566;
	/** Public address players use to reach this server (e.g. play.example.com). Required on dedicated servers
	 * (or set the HELLCRAFT_PACK_HOST environment variable). */
	public String musicPackHost = "";
	/** Or: a full URL where you host the pack yourself (overrides host/port and the built-in web server). */
	public String musicPackUrl = "";
	/** Show Lucifer as his own model (the invisible boss carries it). Off: the old vanilla look. */
	public boolean luciferModels = true;
	/** Degrees added to the model's facing, if it ever looks the wrong way in-game. */
	public float luciferModelYawOffset = 0.0f;
	/** Everyone's hearts next to their name in the player list. */
	public boolean tabListHearts = true;
	/** The "Hall of the Damned" sidebar: everyone who has cast down Lucifer, and how often. */
	public boolean sidebarHall = true;
	/** Per-circle ambient sounds and particles around players. */
	public boolean circleAmbience = true;
	/** Give new players "The Pilgrim's Guide" on their first join. */
	public boolean guideBook = true;
	/** Vanilla music discs used for players without the pack (jukebox song ids). */
	public String fallbackMusicDuel = "minecraft:creator";
	public String fallbackMusicEnraged = "minecraft:pigstep";
	public String fallbackMusicTrueForm = "minecraft:precipice";

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
