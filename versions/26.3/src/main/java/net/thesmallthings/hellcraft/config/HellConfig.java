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
	/** Bumped when default balance values change; see {@link #migrate()}. */
	private static final int CURRENT_VERSION = 2;

	/** Which defaults this file was written with (0: before versioning). Don't edit. */
	public int configVersion;

	// --- Lifesteal
	/** Hearts a brand-new player starts with. */
	public int startHearts = 10;
	/** Most hearts anyone can hold. */
	public int maxHearts = 20;
	/** The absolute ceiling: no Lucifer's Bane or burned P can raise anyone's heart capacity past it. */
	public int heartCeiling = 40;
	/** Dying to monsters, lava, falls or the circles costs a heart too (off: only players take hearts). */
	public boolean pveDeathsCostHearts = false;
	/** With pveDeathsCostHearts: the heart lost to a monster or the world drops where you fell. */
	public boolean naturalDeathDropsHeart = true;
	/** Blood Hearts it costs to bind your respawn to a Blood Altar. */
	public int bindCostHearts = 0;
	/** Blood Hearts consumed at an altar to bring a ghost back. */
	public int reviveCostHearts = 4;
	/** Hearts a revived player comes back with. */
	public int reviveHearts = 3;
	/** Blood Fragments that clot into one Blood Heart. */
	public int fragmentsPerHeart = 8;
	/** Chance a hostile mob killed by a player drops a fragment in the outermost circles... */
	public double fragmentChanceBase = 0.05;
	/** ...plus this much per circle of depth (Cocytus is depth 9). */
	public double fragmentChancePerDepth = 0.015;

	// --- Hell is full
	/** Multiplier on the hostile mob cap. */
	public double monsterCapMultiplier = 1.0;
	/** Eliminated players rise as named undead wearing their gear. */
	public boolean revenants = true;
	/** Ghosts may not wander further than this from where they died. */
	public int ghostTetherRadius = 48;

	// --- Altars & hazards
	public int wardMinutes = 30;
	public boolean circleHazards = true;
	public boolean circleTitles = true;
	/** Extra max health per circle of depth for hostile mobs (0.03 = +3% per circle). */
	public double mobHealthPerDepth = 0.03;

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
	/**
	 * Send players the copy of the resource pack published on GitHub with each release: "auto" for players
	 * who join through a tunnel such as playit.gg (which can't reach port 25566), "always", or "never".
	 * The GitHub copy has no custom boss music.
	 */
	public String packFromGitHub = "auto";
	/** Everyone's hearts next to their name in the player list. */
	public boolean tabListHearts = true;
	/** The "Hall of the Damned" sidebar: everyone who has cast down Lucifer, and how often. */
	public boolean sidebarHall = true;
	/** Per-circle ambient sounds and particles around players. */
	public boolean circleAmbience = true;
	/** Give new players "The Pilgrim's Guide" on their first join. */
	public boolean guideBook = true;
	/** The circle guardians (Minos, Cerberus, Plutus, the Minotaur, Geryon) wake when someone enters their lair. */
	public boolean guardians = true;
	/** How long a slain guardian sleeps before it can be fought again. */
	public int guardianRespawnMinutes = 30;
	/** Scales every guardian's health (each extra player in the lair adds half again). */
	public double guardianHealthMultiplier = 1.0;
	/** Blood Hearts a guardian gives each player who slays it for the first time (plus 3-6 Blood Fragments and a Soul Anchor). */
	public int guardianHearts = 2;
	/** Blood Hearts for slaying a guardian again, once its spoils have returned for you. */
	public int guardianRepeatHearts = 1;
	/** Minutes before a guardian you have slain gives you spoils again (in between it only bleeds a few fragments). */
	public int guardianSpoilsCooldownMinutes = 180;
	/** Minutes before the Wither, the Warden or the Ender Dragon gives the same player Blood Hearts again. */
	public int bossSpoilsCooldownMinutes = 180;
	/** Minutes before a victory over Lucifer earns the same player spoils again (the first victory always does). */
	public int luciferSpoilsCooldownMinutes = 360;
	/** The bounty: every so often the strongest soul online is marked, and whoever kills them is paid in blood. */
	public boolean bounty = true;
	/** Minutes between bounties. */
	public int bountyIntervalMinutes = 20;
	/** Only a soul holding at least this many hearts can carry a bounty. */
	public int bountyMinHearts = 25;
	/** Blood Hearts paid to whoever kills the marked soul (on top of the heart a kill always takes). */
	public int bountyRewardHearts = 3;
	/** Blood Fragments (with a netherite ingot) the Hellforge takes to make a piece of blood gear infernal. */
	public int hellforgeCostFragments = 8;
	/** Blood Altars can send you to any Virgil's Rest you have reached (and the Gate of Hell). */
	public boolean travel = true;
	/** Seconds between one player's trips. */
	public int travelCooldownSeconds = 120;
	/** The Seven P's: a soul whose veins are full can ascend at a Blood Altar for more heart capacity and a virtue. */
	public boolean prestige = true;
	/** Heart capacity each burned P adds (seven in all). */
	public int prestigeHeartBonus = 2;
	/** Hearts a soul is left with after ascending. */
	public int prestigeResetHearts = 10;
	/** The Blood Heart HUD: Hellcraft's own hearts in the health bar, rimmed in the colour of each player's rank. */
	public boolean heartHud = true;
	/** The Hall of the Damned: a monument of signs and heads beside the spawn, naming the Inferno's best and most damned. */
	public boolean hallMonument = true;
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
		save();
	}

	/** Writes the current settings back to config/hellcraft.json. */
	public static void save() {
		Path path = path();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			HellcraftMod.LOGGER.warn("Could not write {}", path, e);
		}
	}

	/** For /hellcraft config: a setting's current value, or null if there is no such setting. */
	@org.jetbrains.annotations.Nullable
	public static String getValue(String key) {
		try {
			java.lang.reflect.Field f = HellConfig.class.getField(key);
			if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
				return null;
			}
			return String.valueOf(f.get(instance));
		} catch (ReflectiveOperationException e) {
			return null;
		}
	}

	/** The names of every setting, for command suggestions. */
	public static java.util.List<String> keys() {
		java.util.List<String> keys = new java.util.ArrayList<>();
		for (java.lang.reflect.Field f : HellConfig.class.getFields()) {
			if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
				keys.add(f.getName());
			}
		}
		return keys;
	}

	/** For /hellcraft config: changes a setting live and saves it. Returns what happened. */
	public static String setValue(String key, String value) {
		try {
			java.lang.reflect.Field f = HellConfig.class.getField(key);
			if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || key.equals("configVersion")) {
				return "No such setting: " + key;
			}
			Class<?> type = f.getType();
			if (type == int.class) {
				f.setInt(instance, Integer.parseInt(value));
			} else if (type == double.class) {
				f.setDouble(instance, Double.parseDouble(value));
			} else if (type == float.class) {
				f.setFloat(instance, Float.parseFloat(value));
			} else if (type == boolean.class) {
				if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
					return key + " must be true or false.";
				}
				f.setBoolean(instance, Boolean.parseBoolean(value));
			} else if (type == String.class) {
				f.set(instance, value);
			} else {
				return key + " can't be set from a command.";
			}
			instance.sanitize();
			save();
			return key + " = " + f.get(instance);
		} catch (NumberFormatException e) {
			return value + " is not a number.";
		} catch (ReflectiveOperationException e) {
			return "No such setting: " + key;
		}
	}

	private void sanitize() {
		migrate();
		bindCostHearts = Math.max(0, bindCostHearts);
		travelCooldownSeconds = Math.max(0, travelCooldownSeconds);
		guardianRepeatHearts = Math.max(0, guardianRepeatHearts);
		heartCeiling = Math.max(1, heartCeiling);
		bossSpoilsCooldownMinutes = Math.max(0, bossSpoilsCooldownMinutes);
		luciferSpoilsCooldownMinutes = Math.max(0, luciferSpoilsCooldownMinutes);
		bountyIntervalMinutes = Math.max(1, bountyIntervalMinutes);
		bountyRewardHearts = Math.max(0, bountyRewardHearts);
		guardianSpoilsCooldownMinutes = Math.max(0, guardianSpoilsCooldownMinutes);
		prestigeHeartBonus = Math.max(0, prestigeHeartBonus);
		prestigeResetHearts = Math.max(1, prestigeResetHearts);
		maxHearts = Math.max(1, maxHearts);
		startHearts = Math.max(1, Math.min(startHearts, maxHearts));
		reviveHearts = Math.max(1, Math.min(reviveHearts, maxHearts));
		reviveCostHearts = Math.max(0, reviveCostHearts);
		fragmentsPerHeart = Math.max(1, fragmentsPerHeart);
		monsterCapMultiplier = Math.max(0.1, monsterCapMultiplier);
		ghostTetherRadius = Math.max(4, ghostTetherRadius);
	}

	/**
	 * Moves settings that still hold an old default onto the current one (values an admin chose
	 * themselves are kept). Version 2 made the circles gentler and blood easier to find.
	 */
	private void migrate() {
		if (configVersion < 2) {
			monsterCapMultiplier = moved(monsterCapMultiplier, 1.75, 1.0);
			mobHealthPerDepth = moved(mobHealthPerDepth, 0.06, 0.03);
			fragmentChanceBase = moved(fragmentChanceBase, 0.02, 0.05);
			fragmentChancePerDepth = moved(fragmentChancePerDepth, 0.011, 0.015);
		}
		if (configVersion < CURRENT_VERSION) {
			HellcraftMod.LOGGER.info("Hellcraft config updated from version {} to {}", configVersion, CURRENT_VERSION);
			configVersion = CURRENT_VERSION;
		}
	}

	private static double moved(double value, double oldDefault, double newDefault) {
		return Math.abs(value - oldDefault) < 1.0e-9 ? newDefault : value;
	}
}
