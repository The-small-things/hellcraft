package net.thesmallthings.hellcraft.blood;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.thesmallthings.hellcraft.config.HellConfig;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Everything Hellcraft remembers about the world: hearts, ghosts, altar bindings, Lucifer. */
public class HellState extends SavedData {
	private static final String NAME = "hellcraft";
	private static final SavedData.Factory<HellState> FACTORY = new SavedData.Factory<>(HellState::new, HellState::load, null);

	private final Map<UUID, Soul> souls = new HashMap<>();
	public boolean landmarksBuilt;
	@Nullable
	public UUID luciferId;
	public long luciferNextSpawn;

	public static HellState get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
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
	}

	public record GlobalSpot(ResourceKey<Level> dimension, BlockPos pos) {
		CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putString("dim", dimension.location().toString());
			tag.put("pos", NbtUtils.writeBlockPos(pos));
			return tag;
		}

		@Nullable
		static GlobalSpot load(CompoundTag parent, String key) {
			if (!parent.contains(key, Tag.TAG_COMPOUND)) {
				return null;
			}
			CompoundTag tag = parent.getCompound(key);
			ResourceLocation dim = ResourceLocation.tryParse(tag.getString("dim"));
			Optional<BlockPos> pos = NbtUtils.readBlockPos(tag, "pos");
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

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (Map.Entry<UUID, Soul> e : souls.entrySet()) {
			Soul s = e.getValue();
			CompoundTag st = new CompoundTag();
			st.putUUID("id", e.getKey());
			st.putString("name", s.name);
			st.putInt("hearts", s.hearts);
			st.putBoolean("ghost", s.ghost);
			st.putLong("ward", s.wardUntil);
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
		if (luciferId != null) {
			tag.putUUID("lucifer", luciferId);
		}
		tag.putLong("luciferNext", luciferNextSpawn);
		return tag;
	}

	public static HellState load(CompoundTag tag, HolderLookup.Provider registries) {
		HellState state = new HellState();
		ListTag list = tag.getList("souls", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag st = list.getCompound(i);
			Soul s = new Soul();
			s.name = st.getString("name");
			s.hearts = st.getInt("hearts");
			s.ghost = st.getBoolean("ghost");
			s.wardUntil = st.getLong("ward");
			s.deathSpot = GlobalSpot.load(st, "death");
			s.altar = GlobalSpot.load(st, "altar");
			s.reviveAt = GlobalSpot.load(st, "revive");
			state.souls.put(st.getUUID("id"), s);
		}
		state.landmarksBuilt = tag.getBoolean("landmarks");
		if (tag.hasUUID("lucifer")) {
			state.luciferId = tag.getUUID("lucifer");
		}
		state.luciferNextSpawn = tag.getLong("luciferNext");
		return state;
	}
}
