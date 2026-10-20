package net.thesmallthings.hellcraft.blood;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.config.HellConfig;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Everything Hellcraft remembers about the world: hearts, ghosts, altar bindings, Lucifer. */
public class HellState extends SavedData {
	/** Stored as plain NBT (the same layout as the 1.21.1 version) through a pass-through codec. */
	private static final SavedDataType<HellState> TYPE = new SavedDataType<>(HellcraftMod.id("state"), HellState::new,
			CompoundTag.CODEC.xmap(HellState::load, HellState::save), null);

	private final Map<UUID, Soul> souls = new HashMap<>();
	public boolean landmarksBuilt;
	/** Where Antaeus stands (x, y, z of his feet, then his facing), once AntaeusCleft has placed him; null before. */
	public double[] antaeus;
	/** The Emperor's Spine has been laid (it is added to worlds made before it existed, too). */
	public boolean spineBuilt;
	/** The other three spines (north, south, west) have been laid too. */
	public boolean spinesBuilt;
	/** The circle guardians' first lairs have been built. */
	public boolean lairsBuilt;
	/** Their other lairs have been built too (worlds from before there were four each get them later). */
	public boolean moreLairsBuilt;
	/** The Mountain of Purgatory has been raised in its sea (its own dimension since Phase 7). */
	public boolean purgatoryIsland;
	/** The Great Forge of Dis has been carved into the Nether. */
	public boolean forgeBuilt;
	/** The Celestial Rose (atop Paradiso) and the Ascent (Purgatory's summit) have been built. */
	public boolean roseBuilt;
	public boolean ascentBuilt;
	/** Guardian id -> game time it wakes again after being slain. */
	public final Map<String, Long> guardianNext = new HashMap<>();
	/** Virgil's Rests already built: site id -> the height of its floor. */
	public final Map<String, Integer> shrines = new HashMap<>();
	/** Malebolge's fake Rests already built: site id -> the height of its floor (Frauds). */
	public final Map<String, Integer> fakeRests = new HashMap<>();
	/** Malebolge's lies that are spent: mimics killed and fake Rests whose trap has sprung (FraudSites ids). */
	public final Set<String> fraudSpent = new HashSet<>();
	/** The ice ring around Lucifer's pit is standing (so a crash mid-fight can be cleaned up). */
	public boolean arenaSealed;
	public int luciferDefeats;
	@Nullable
	public UUID luciferId;
	public long luciferNextSpawn;
	/** The Blood Altar Landmarks builds beside the Gate of Hell (pointed to in the revival instructions). */
	@Nullable
	public GlobalSpot starterAltar;
	/** The white room: the blank white map its walls are papered with (-1 until first made). */
	public int whiteMap = -1;
	/** Players an operator has shut in the white room: UUID -> their cell, and where and how to put them back. */
	public final Map<UUID, Captive> captives = new HashMap<>();

	public record Captive(int cell, GlobalSpot from, String mode) {
	}

	/** Where the Hall of the Damned stands (its first sign column), once built. */
	@Nullable
	public GlobalSpot hall;

	// ---- the Pilgrimage (Pilgrimage, PilgrimStone)
	/** Where the Pilgrims' Stone stands (its lectern), once built. */
	@Nullable
	public GlobalSpot pilgrimStone;
	/** Pilgrimages that ended, kept for the Pilgrims' Stone: the fastest done and the deepest fallen. */
	public final java.util.List<Pilgrim> pilgrims = new java.util.ArrayList<>();

	/** A pilgrimage that ended: who, how long it took (ticks of play), the deepest circle reached, and whether it was done. */
	public record Pilgrim(String name, int ticks, int deepest, boolean done, String where) {
	}

	public static HellState get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	/** A player's standing in Hell. */
	public static class Soul {
		public String name = "";
		public int hearts;
		public boolean ghost;
		@Nullable
		public GlobalSpot deathSpot;
		@Nullable
		public GlobalSpot altar;
		/** Pending revival (ghost was offline when revived). */
		@Nullable
		public GlobalSpot reviveAt;
		public long wardUntil;
		/** Extra heart capacity earned (Lucifer's Bane). */
		public int maxBonus;
		public boolean slewLucifer;
		/** How many times this soul has cast Lucifer down. */
		public int luciferKills;
		/** Draughts of Eunoë owed (one per victory over Lucifer). */
		public int pendingEunoe;
		/** Unclaimed Lucifer reward: 0 none, 1 first-victory choice, 2 repeat-victory choice. */
		public int pendingReward;
		/** A lit Vigil Candle: the next death respawns here, once. */
		@Nullable
		public GlobalSpot vigil;
		/** Where a Soul Anchor will bring this soul back (set as they die, spent as they respawn). */
		@Nullable
		public GlobalSpot anchorAt;
		/** Virgil's one-time tips this soul has had (a bit per tip; see CircleHazards.hintOnce). */
		public long hints;
		/** Starter kit version this soul has received. */
		public int kit;
		/** Newcomer protection left, in ticks of play (Fairness). */
		public int graceTicks;
		/** Game time until which this soul bears Cain's Mark (Fairness), or 0. */
		public long cainUntil;
		/** Virgil's Rests this soul has reached (by site id): the places they can travel back to. */
		public final Set<String> visited = new HashSet<>();
		/** Codex entries this soul has opened (the bosses it has met). */
		public final Set<String> codex = new HashSet<>();
		/** Cutscenes this soul has seen (it may skip them next time). */
		public final Set<String> cutscenes = new HashSet<>();
		/** Guardian id -> game time this soul last took its spoils (absent: never slain it). */
		public final Map<String, Long> guardianSpoils = new HashMap<>();
		/** Boss id -> the best grade (Judgement.Grade ordinal) this soul has earned against it. */
		public final Map<String, Integer> bossBest = new HashMap<>();
		/** Has been given Beatrice's Rose at the Celestial Rose. */
		public boolean rose;
		/** Paradiso's trials this soul has passed: bit k for sphere k (ParadisoGeometry.Sphere), the Seraph's included. */
		public int spheres;
		/** The Seven P's: how many have been burned from this soul's brow (0-7), each by an ascent at an altar. */
		public int prestige;
		/** For the Hall of the Damned. */
		public int deaths;
		public int guardiansSlain;
		// Minos's judgement (Sins): the verdict ("heresy", "" before he has judged), what gave it away, and Treachery's
		// own counts (tamed animals killed, far weaker souls killed)
		public String judged = "";
		public String judgedFact = "";
		public int tamedKilled;
		public int weakKills;

		// ---- secret ways and the Pilgrimage (SecretWayLift, Pilgrimage)
		/** The secret ways down this soul has found (SecretWays ids). */
		public final Set<String> secrets = new HashSet<>();
		/** On a pilgrimage: its ticks of play so far; -1 when not on one. */
		public int pilgrimTicks = -1;
		/** The deepest circle (Circle.depth) this pilgrimage has reached. */
		public int pilgrimDeepest;
		/** Pilgrimages this soul has finished, and its fastest (ticks; 0 before the first). */
		public int pilgrimages;
		public int pilgrimBest;
	}

	public record GlobalSpot(ResourceKey<Level> dimension, BlockPos pos) {
		CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putString("dim", dimension.identifier().toString());
			tag.store("pos", BlockPos.CODEC, pos);
			return tag;
		}

		@Nullable
		static GlobalSpot load(CompoundTag parent, String key) {
			Optional<CompoundTag> found = parent.getCompound(key);
			if (found.isEmpty()) {
				return null;
			}
			CompoundTag tag = found.get();
			Identifier dim = Identifier.tryParse(tag.getStringOr("dim", ""));
			Optional<BlockPos> pos = tag.read("pos", BlockPos.CODEC);
			if (dim == null || pos.isEmpty()) {
				return null;
			}
			return new GlobalSpot(ResourceKey.create(Registries.DIMENSION, dim), pos.get());
		}
	}

	public Soul soul(UUID id, String name) {
		Soul soul = souls.computeIfAbsent(id, k -> {
			Soul s = new Soul();
			s.hearts = HellConfig.get().startHearts;
			setDirty();
			return s;
		});
		if (name != null && !name.equals(soul.name)) {
			soul.name = name;
			setDirty();
		}
		return soul;
	}

	@Nullable
	public Soul existing(UUID id) {
		return souls.get(id);
	}

	@Nullable
	public Map.Entry<UUID, Soul> findByName(String name) {
		String wanted = name.trim().toLowerCase(Locale.ROOT);
		for (Map.Entry<UUID, Soul> e : souls.entrySet()) {
			if (e.getValue().name.toLowerCase(Locale.ROOT).equals(wanted)) {
				return e;
			}
		}
		return null;
	}

	public Map<UUID, Soul> souls() {
		return souls;
	}

	private CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		ListTag list = new ListTag();
		for (Map.Entry<UUID, Soul> e : souls.entrySet()) {
			Soul s = e.getValue();
			CompoundTag st = new CompoundTag();
			st.store("id", UUIDUtil.CODEC, e.getKey());
			st.putString("name", s.name);
			st.putInt("hearts", s.hearts);
			st.putBoolean("ghost", s.ghost);
			st.putLong("ward", s.wardUntil);
			st.putInt("maxBonus", s.maxBonus);
			st.putBoolean("slewLucifer", s.slewLucifer);
			st.putInt("luciferKills", s.luciferKills);
			st.putInt("pendingEunoe", s.pendingEunoe);
			st.putInt("pendingReward", s.pendingReward);
			if (s.deathSpot != null) {
				st.put("death", s.deathSpot.save());
			}
			if (s.altar != null) {
				st.put("altar", s.altar.save());
			}
			if (s.reviveAt != null) {
				st.put("revive", s.reviveAt.save());
			}
			if (s.vigil != null) {
				st.put("vigil", s.vigil.save());
			}
			if (s.anchorAt != null) {
				st.put("anchor", s.anchorAt.save());
			}
			st.putLong("hints", s.hints);
			st.putInt("kit", s.kit);
			st.putInt("grace", s.graceTicks);
			st.putLong("cain", s.cainUntil);
			st.putBoolean("rose", s.rose);
			st.putInt("spheres", s.spheres);
			st.putInt("prestige", s.prestige);
			st.putInt("deaths", s.deaths);
			st.putInt("guardiansSlain", s.guardiansSlain);
			st.putString("judged", s.judged);
			st.putString("judgedFact", s.judgedFact);
			st.putInt("tamedKilled", s.tamedKilled);
			st.putInt("weakKills", s.weakKills);
			CompoundTag visited = new CompoundTag();
			s.visited.forEach(v -> visited.putBoolean(v, true));
			st.put("visited", visited);
			CompoundTag cutscenes = new CompoundTag();
			s.cutscenes.forEach(c -> cutscenes.putBoolean(c, true));
			st.put("cutscenes", cutscenes);
			CompoundTag codex = new CompoundTag();
			s.codex.forEach(c -> codex.putBoolean(c, true));
			st.put("codex", codex);
			CompoundTag spoils = new CompoundTag();
			s.guardianSpoils.forEach(spoils::putLong);
			st.put("guardianSpoils", spoils);
			CompoundTag best = new CompoundTag();
			s.bossBest.forEach(best::putInt);
			st.put("bossBest", best);
			CompoundTag secrets = new CompoundTag();
			s.secrets.forEach(id -> secrets.putBoolean(id, true));
			st.put("secrets", secrets);
			st.putInt("pilgrimTicks", s.pilgrimTicks);
			st.putInt("pilgrimDeepest", s.pilgrimDeepest);
			st.putInt("pilgrimages", s.pilgrimages);
			st.putInt("pilgrimBest", s.pilgrimBest);
			list.add(st);
		}
		tag.put("souls", list);
		tag.putBoolean("landmarks", landmarksBuilt);
		if (antaeus != null) {
			ListTag place = new ListTag();
			for (double v : antaeus) {
				place.add(DoubleTag.valueOf(v));
			}
			tag.put("antaeus", place);
		}
		tag.putBoolean("spine", spineBuilt);
		tag.putBoolean("spines", spinesBuilt);
		tag.putBoolean("lairs", lairsBuilt);
		tag.putBoolean("moreLairs", moreLairsBuilt);
		tag.putBoolean("purgatoryIsland", purgatoryIsland);
		tag.putBoolean("forge", forgeBuilt);
		tag.putBoolean("paradisoRose", roseBuilt);
		tag.putBoolean("purgatoryAscent", ascentBuilt);
		CompoundTag guardians = new CompoundTag();
		guardianNext.forEach(guardians::putLong);
		tag.put("guardianNext", guardians);
		CompoundTag shrineTag = new CompoundTag();
		shrines.forEach(shrineTag::putInt);
		tag.put("shrines", shrineTag);
		CompoundTag fakeTag = new CompoundTag();
		fakeRests.forEach(fakeTag::putInt);
		tag.put("fakeRests", fakeTag);
		CompoundTag spentTag = new CompoundTag();
		fraudSpent.forEach(id -> spentTag.putBoolean(id, true));
		tag.put("fraudSpent", spentTag);
		tag.putBoolean("arenaSealed", arenaSealed);
		tag.putInt("luciferDefeats", luciferDefeats);
		if (luciferId != null) {
			tag.store("lucifer", UUIDUtil.CODEC, luciferId);
		}
		tag.putLong("luciferNext", luciferNextSpawn);
		tag.putInt("whiteMap", whiteMap);
		ListTag captiveList = new ListTag();
		captives.forEach((id, c) -> {
			CompoundTag ct = new CompoundTag();
			ct.store("id", UUIDUtil.CODEC, id);
			ct.putInt("cell", c.cell());
			ct.put("from", c.from().save());
			ct.putString("mode", c.mode());
			captiveList.add(ct);
		});
		tag.put("captives", captiveList);
		if (hall != null) {
			tag.put("hall", hall.save());
		}
		if (pilgrimStone != null) {
			tag.put("pilgrimStone", pilgrimStone.save());
		}
		ListTag pilgrimList = new ListTag();
		for (Pilgrim p : pilgrims) {
			CompoundTag pt = new CompoundTag();
			pt.putString("name", p.name());
			pt.putInt("ticks", p.ticks());
			pt.putInt("deepest", p.deepest());
			pt.putBoolean("done", p.done());
			pt.putString("where", p.where());
			pilgrimList.add(pt);
		}
		tag.put("pilgrims", pilgrimList);
		if (starterAltar != null) {
			tag.put("starterAltar", starterAltar.save());
		}
		return tag;
	}

	private static HellState load(CompoundTag tag) {
		HellState state = new HellState();
		ListTag list = tag.getListOrEmpty("souls");
		for (int i = 0; i < list.size(); i++) {
			CompoundTag st = list.getCompoundOrEmpty(i);
			Optional<UUID> id = st.read("id", UUIDUtil.CODEC);
			if (id.isEmpty()) {
				continue;
			}
			Soul s = new Soul();
			s.name = st.getStringOr("name", "");
			s.hearts = st.getIntOr("hearts", 0);
			s.ghost = st.getBooleanOr("ghost", false);
			s.wardUntil = st.getLongOr("ward", 0L);
			s.maxBonus = st.getIntOr("maxBonus", 0);
			s.slewLucifer = st.getBooleanOr("slewLucifer", false);
			s.luciferKills = st.getIntOr("luciferKills", s.slewLucifer ? 1 : 0);
			s.pendingEunoe = st.getIntOr("pendingEunoe", 0);
			s.pendingReward = st.getIntOr("pendingReward", 0);
			s.deathSpot = GlobalSpot.load(st, "death");
			s.altar = GlobalSpot.load(st, "altar");
			s.reviveAt = GlobalSpot.load(st, "revive");
			s.vigil = GlobalSpot.load(st, "vigil");
			s.anchorAt = GlobalSpot.load(st, "anchor");
			s.hints = st.getLongOr("hints", 0L);
			s.kit = st.getIntOr("kit", 0);
			s.graceTicks = st.getIntOr("grace", 0);
			s.cainUntil = st.getLongOr("cain", 0L);
			s.rose = st.getBooleanOr("rose", false);
			s.spheres = st.getIntOr("spheres", 0);
			s.prestige = st.getIntOr("prestige", 0);
			s.deaths = st.getIntOr("deaths", 0);
			s.guardiansSlain = st.getIntOr("guardiansSlain", 0);
			s.judged = st.getStringOr("judged", "");
			s.judgedFact = st.getStringOr("judgedFact", "");
			s.tamedKilled = st.getIntOr("tamedKilled", 0);
			s.weakKills = st.getIntOr("weakKills", 0);
			s.visited.addAll(st.getCompoundOrEmpty("visited").keySet());
			s.cutscenes.addAll(st.getCompoundOrEmpty("cutscenes").keySet());
			s.codex.addAll(st.getCompoundOrEmpty("codex").keySet());
			CompoundTag spoils = st.getCompoundOrEmpty("guardianSpoils");
			for (String key : spoils.keySet()) {
				s.guardianSpoils.put(key, spoils.getLongOr(key, 0L));
			}
			CompoundTag best = st.getCompoundOrEmpty("bossBest");
			for (String key : best.keySet()) {
				s.bossBest.put(key, best.getIntOr(key, 0));
			}
			s.secrets.addAll(st.getCompoundOrEmpty("secrets").keySet());
			s.pilgrimTicks = st.getIntOr("pilgrimTicks", -1);
			s.pilgrimDeepest = st.getIntOr("pilgrimDeepest", 0);
			s.pilgrimages = st.getIntOr("pilgrimages", 0);
			s.pilgrimBest = st.getIntOr("pilgrimBest", 0);
			state.souls.put(id.get(), s);
		}
		state.landmarksBuilt = tag.getBooleanOr("landmarks", false);
		ListTag place = tag.getListOrEmpty("antaeus");
		if (place.size() == 4) {
			state.antaeus = new double[]{place.getDoubleOr(0, 0), place.getDoubleOr(1, 0), place.getDoubleOr(2, 0), place.getDoubleOr(3, 0)};
		}
		state.spineBuilt = tag.getBooleanOr("spine", false);
		state.spinesBuilt = tag.getBooleanOr("spines", false);
		state.lairsBuilt = tag.getBooleanOr("lairs", false);
		state.moreLairsBuilt = tag.getBooleanOr("moreLairs", false);
		state.purgatoryIsland = tag.getBooleanOr("purgatoryIsland", false);
		state.forgeBuilt = tag.getBooleanOr("forge", false);
		state.roseBuilt = tag.getBooleanOr("paradisoRose", false);
		state.ascentBuilt = tag.getBooleanOr("purgatoryAscent", false);
		CompoundTag guardians = tag.getCompoundOrEmpty("guardianNext");
		for (String key : guardians.keySet()) {
			state.guardianNext.put(key, guardians.getLongOr(key, 0L));
		}
		CompoundTag shrineTag = tag.getCompoundOrEmpty("shrines");
		for (String key : shrineTag.keySet()) {
			state.shrines.put(key, shrineTag.getIntOr(key, 0));
		}
		CompoundTag fakeTag = tag.getCompoundOrEmpty("fakeRests");
		for (String key : fakeTag.keySet()) {
			state.fakeRests.put(key, fakeTag.getIntOr(key, 0));
		}
		state.fraudSpent.addAll(tag.getCompoundOrEmpty("fraudSpent").keySet());
		state.arenaSealed = tag.getBooleanOr("arenaSealed", false);
		state.luciferDefeats = tag.getIntOr("luciferDefeats", 0);
		state.luciferId = tag.read("lucifer", UUIDUtil.CODEC).orElse(null);
		state.luciferNextSpawn = tag.getLongOr("luciferNext", 0L);
		state.starterAltar = GlobalSpot.load(tag, "starterAltar");
		state.hall = GlobalSpot.load(tag, "hall");
		state.pilgrimStone = GlobalSpot.load(tag, "pilgrimStone");
		ListTag pilgrimList = tag.getListOrEmpty("pilgrims");
		for (int i = 0; i < pilgrimList.size(); i++) {
			CompoundTag pt = pilgrimList.getCompoundOrEmpty(i);
			state.pilgrims.add(new Pilgrim(pt.getStringOr("name", ""), pt.getIntOr("ticks", 0), pt.getIntOr("deepest", 0),
					pt.getBooleanOr("done", false), pt.getStringOr("where", "")));
		}
		state.whiteMap = tag.getIntOr("whiteMap", -1);
		ListTag captiveList = tag.getListOrEmpty("captives");
		for (int i = 0; i < captiveList.size(); i++) {
			CompoundTag ct = captiveList.getCompoundOrEmpty(i);
			Optional<UUID> id = ct.read("id", UUIDUtil.CODEC);
			GlobalSpot from = GlobalSpot.load(ct, "from");
			if (id.isPresent() && from != null) {
				state.captives.put(id.get(), new Captive(ct.getIntOr("cell", 0), from, ct.getStringOr("mode", "survival")));
			}
		}
		return state;
	}
}
