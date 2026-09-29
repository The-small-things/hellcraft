package net.thesmallthings.hellcraft.world;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.thesmallthings.hellcraft.HellcraftMod;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Paradiso, the End as Dante's heaven. The biomes of the nine spheres (data/hellcraft/worldgen/biome/
 * paradiso_*.json) are handed to the vanilla End biome source by TheEndBiomeSourceMixin, ring by ring (see
 * {@link ParadisoGeometry}); this class holds them and names each heaven as a pilgrim enters it.
 */
public final class Paradiso {
	private Paradiso() {
	}

	private static final Map<ParadisoGeometry.Sphere, Holder<Biome>> BIOMES = new EnumMap<>(ParadisoGeometry.Sphere.class);
	private static final Map<UUID, ParadisoGeometry.Sphere> LAST = new HashMap<>();
	/** True once the End's biome source lists the spheres (only then may it hand them out). */
	private static volatile boolean listed;
	private static int ticks;

	/** Looks the spheres' biomes up, before any world (and so the End's biome source) is loaded. */
	public static void init(MinecraftServer server) {
		BIOMES.clear();
		union = null;
		listed = false;
		var lookup = server.registryAccess().lookupOrThrow(Registries.BIOME);
		for (ParadisoGeometry.Sphere s : ParadisoGeometry.Sphere.values()) {
			if (s == ParadisoGeometry.Sphere.THRESHOLD) {
				continue;
			}
			lookup.get(ResourceKey.create(Registries.BIOME, HellcraftMod.id("paradiso_" + s.id))).ifPresent(h -> BIOMES.put(s, h));
		}
		HellcraftMod.LOGGER.info("Paradiso: {} spheres ready", BIOMES.size());
	}

	private static Set<Holder<Biome>> base;
	private static Set<Holder<Biome>> union;

	/** The End's possible biomes plus the spheres (once they have been looked up; the same set each time). */
	public static synchronized Set<Holder<Biome>> withSpheres(Set<Holder<Biome>> vanilla) {
		if (BIOMES.isEmpty()) {
			return vanilla;
		}
		if (union == null || base != vanilla) {
			Set<Holder<Biome>> all = new LinkedHashSet<>(vanilla);
			all.addAll(BIOMES.values());
			base = vanilla;
			union = Collections.unmodifiableSet(all);
			listed = true;
		}
		return union;
	}

	/** For /hellcraft paradiso status: what the End's biome source knows. */
	public static String status(MinecraftServer server) {
		ServerLevel end = server.getLevel(Level.END);
		String source = end == null ? "no End" : end.getChunkSource().getGenerator().getBiomeSource().getClass().getSimpleName()
				+ " lists " + end.getChunkSource().getGenerator().getBiomeSource().possibleBiomes().size() + " biomes";
		Holder<Biome> at = biome(1300, 0);
		String resolved = "?";
		if (end != null) {
			var gen = end.getChunkSource().getGenerator();
			var sampler = end.getChunkSource().randomState().sampler();
			resolved = gen.getBiomeSource().createResolver(sampler).getNoiseBiome(1300 >> 2, 16, 0, sampler).getRegisteredName();
		}
		return "Paradiso: " + BIOMES.size() + " spheres known, listed=" + listed + ", " + source + ", at 1300 0: "
				+ (at == null ? "vanilla" : at.getRegisteredName()) + ", the End's resolver says " + resolved;
	}

	/** The sphere biome for an outer-island column (block coordinates), or null to keep vanilla's. */
	@Nullable
	public static Holder<Biome> biome(int x, int z) {
		if (!listed) {
			return null;
		}
		ParadisoGeometry.Sphere s = ParadisoGeometry.sphereAt(x, z);
		return s == ParadisoGeometry.Sphere.THRESHOLD ? null : BIOMES.get(s);
	}

	/** Names each heaven as a player enters it. */
	public static void tick(MinecraftServer server) {
		if (++ticks % 20 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.level().dimension() != Level.END) {
				LAST.remove(player.getUUID());
				continue;
			}
			ParadisoGeometry.Sphere s = ParadisoGeometry.sphereAt(player.getX(), player.getZ());
			if (LAST.put(player.getUUID(), s) != s) {
				player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 25));
				player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(s.title).withStyle(ChatFormatting.GOLD)));
				player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(s.tagline).withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC)));
			}
		}
	}
}
