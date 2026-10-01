package net.thesmallthings.hellcraft.world;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.BloodItems;
import net.thesmallthings.hellcraft.blood.Hearts;
import net.thesmallthings.hellcraft.blood.HellState;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Paradiso, the End as Dante's heaven. The biomes of the nine spheres (data/hellcraft/worldgen/biome/
 * paradiso_*.json) are handed to the vanilla End biome source by TheEndBiomeSourceMixin, ring by ring (see
 * {@link ParadisoGeometry}); this class holds them, tells players which ring they are in, and gives each a
 * compass to the Celestial Rose the first time they reach the outer islands.
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
			resolved = end.getBiome(new BlockPos(1300, 64, 0)).getRegisteredName();
		}
		return "Paradiso: " + BIOMES.size() + " spheres known, listed=" + listed + ", " + source + ", at 1300 0: "
				+ (at == null ? "vanilla" : at.getRegisteredName()) + ", the End says " + resolved;
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

	/** Names each ring as a player enters it, with how far the Celestial Rose is; and hands out the Rose Compass. */
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
				player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle(s, player)).withStyle(ChatFormatting.WHITE)));
			}
			if (s != ParadisoGeometry.Sphere.THRESHOLD && !player.isSpectator()) {
				HellState.Soul soul = Hearts.soul(player);
				if (!soul.roseCompass) {
					soul.roseCompass = true;
					HellState.get(server).setDirty();
					welcome(player);
				}
			}
		}
	}

	private static String subtitle(ParadisoGeometry.Sphere s, ServerPlayer player) {
		long rose = Math.round(Math.sqrt(Math.pow(player.getX() - ParadisoGeometry.ROSE_X, 2) + Math.pow(player.getZ() - ParadisoGeometry.ROSE_Z, 2)));
		return switch (s) {
			case THRESHOLD -> "The dragon's island. Its gateway portals lead to the outer islands.";
			case EMPYREAN -> "The last ring. The Celestial Rose is " + rose + " blocks away.";
			default -> "Ring " + s.ordinal() + " of 9. End cities and chorus here. Celestial Rose: " + rose + " blocks.";
		};
	}

	/** The first time a player reaches the outer islands: what Paradiso is, and a compass to the Rose. */
	private static void welcome(ServerPlayer player) {
		BloodItems.give(player, roseCompass());
		player.sendSystemMessage(Component.literal("Welcome to Paradiso, the End's outer islands.").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		player.sendSystemMessage(Component.literal("- Nine rings of islands go outward from the dragon's island. Each ring has End cities "
				+ "(their elytra are Seraph Wings) and chorus plants.").withStyle(ChatFormatting.WHITE));
		player.sendSystemMessage(Component.literal("- The Rose Compass you were given points to the Celestial Rose, past the ninth ring at x "
				+ ParadisoGeometry.ROSE_X + ", z " + ParadisoGeometry.ROSE_Z + ". Ring its bell for Beatrice's Rose.").withStyle(ChatFormatting.WHITE));
		HellcraftMod.LOGGER.info("Rose Compass given to {}", player.getGameProfile().name());
	}

	/** A lodestone compass that points to the Celestial Rose's bell. */
	public static ItemStack roseCompass() {
		ItemStack compass = new ItemStack(Items.COMPASS);
		compass.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(GlobalPos.of(Level.END, Heaven.bell())), false));
		compass.set(DataComponents.ITEM_NAME, Component.literal("Rose Compass").withStyle(ChatFormatting.LIGHT_PURPLE));
		compass.set(DataComponents.LORE, new ItemLore(List.of(
				Component.literal("Points to the Celestial Rose in the End").withStyle(st -> st.withColor(ChatFormatting.GRAY).withItalic(false)),
				Component.literal("(x " + ParadisoGeometry.ROSE_X + ", z " + ParadisoGeometry.ROSE_Z + ").").withStyle(st -> st.withColor(ChatFormatting.GRAY).withItalic(false)))));
		return compass;
	}
}
