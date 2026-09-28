package net.thesmallthings.hellcraft.blood;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Everything Hellcraft remembers about the world: hearts, ghosts, altar bindings, Lucifer. */
public class HellState extends SavedData {
	/** Stored as plain NBT (the same layout as the 1.21.1 version) through a pass-through codec. */
	private static final SavedDataType<HellState> TYPE = new SavedDataType<>(HellcraftMod.id("state"), HellState::new,
			CompoundTag.CODEC.xmap(HellState::load, HellState::save), null);

	private final Map<UUID, Soul> souls = new HashMap<>();
	public boolean landmarksBuilt;
	/** The Emperor's Spine has been laid (it is added to worlds made before it existed, too). */
	public boolean spineBuilt;
	/** The other three spines (north, south, west) have been laid too. */
	public boolean spinesBuilt;
	/** The circle guardians' lairs have been built. */
	public boolean lairsBuilt;
	/** Guardian id -> game time it wakes again after being slain. */
	public final Map<String, Long> guardianNext = new HashMap<>();
	/** The ice ring around Lucifer's pit is standing (so a crash mid-fight can be cleaned up). */
	public boolean arenaSealed;
	public int luciferDefeats;
	@Nullable
	public UUID luciferId;
	public long luciferNextSpawn;
	/** The Blood Altar Landmarks builds beside the Gate of Hell (pointed to in the revival instructions). */
	@Nullable
	public GlobalSpot starterAltar;

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
		/** Unclaimed Lucifer reward: 0 none, 1 first-victory choice, 2 repeat-victory choice. */
		public int pendingReward;
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
			list.add(st);
		}
		tag.put("souls", list);
		tag.putBoolean("landmarks", landmarksBuilt);
		tag.putBoolean("spine", spineBuilt);
		tag.putBoolean("spines", spinesBuilt);
		tag.putBoolean("lairs", lairsBuilt);
		CompoundTag guardians = new CompoundTag();
		guardianNext.forEach(guardians::putLong);
		tag.put("guardianNext", guardians);
		tag.putBoolean("arenaSealed", arenaSealed);
		tag.putInt("luciferDefeats", luciferDefeats);
		if (luciferId != null) {
			tag.store("lucifer", UUIDUtil.CODEC, luciferId);
		}
		tag.putLong("luciferNext", luciferNextSpawn);
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
			s.pendingReward = st.getIntOr("pendingReward", 0);
			s.deathSpot = GlobalSpot.load(st, "death");
			s.altar = GlobalSpot.load(st, "altar");
			s.reviveAt = GlobalSpot.load(st, "revive");
			state.souls.put(id.get(), s);
		}
		state.landmarksBuilt = tag.getBooleanOr("landmarks", false);
		state.spineBuilt = tag.getBooleanOr("spine", false);
		state.spinesBuilt = tag.getBooleanOr("spines", false);
		state.lairsBuilt = tag.getBooleanOr("lairs", false);
		CompoundTag guardians = tag.getCompoundOrEmpty("guardianNext");
		for (String key : guardians.keySet()) {
			state.guardianNext.put(key, guardians.getLongOr(key, 0L));
		}
		state.arenaSealed = tag.getBooleanOr("arenaSealed", false);
		state.luciferDefeats = tag.getIntOr("luciferDefeats", 0);
		state.luciferId = tag.read("lucifer", UUIDUtil.CODEC).orElse(null);
		state.luciferNextSpawn = tag.getLongOr("luciferNext", 0L);
		state.starterAltar = GlobalSpot.load(tag, "starterAltar");
		return state;
	}
}
