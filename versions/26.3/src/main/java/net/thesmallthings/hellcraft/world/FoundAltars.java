package net.thesmallthings.hellcraft.world;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.thesmallthings.hellcraft.blood.BloodAltar;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.blood.Hearts;
import net.thesmallthings.hellcraft.blood.Pilgrimage;
import net.thesmallthings.hellcraft.net.Dialogue;
import net.thesmallthings.hellcraft.net.FoundAltarsPayload;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Blood Altars outside the Rests that a player has found: the ruined altars scattered through Hell
 * (feature/AltarRuinFeature) and the ones players build. Right-clicking one remembers it, for that player alone, and
 * it then shows in their travel menu (blood/TravelMenu) and on their Map of Hell; {@code /rest name <name>} beside it
 * names it (world/RestNames). Kept in {@code HellState.Soul.altars} as "dimension x y z" to the name given (empty for
 * none), sent to the player's game as {@code FoundAltarsPayload}. An altar broken since is forgotten when someone
 * tries to travel to it.
 */
public final class FoundAltars {
	private FoundAltars() {
	}

	/** The most altars a soul remembers (the oldest unnamed one makes room for a new one). */
	public static final int MAX = 64;
	/** How near an altar you stand to name it. */
	public static final double NAMING_REACH = 6.0;

	/** A found altar: where it is, and the name its finder gave it (or null). */
	public record Altar(String key, ResourceKey<Level> dimension, BlockPos pos, @Nullable String given) {
		/** The name the player knows it by. */
		public String name() {
			return given != null ? given : defaultName(dimension, pos);
		}
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(FoundAltarsPayload.TYPE, FoundAltarsPayload.CODEC);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.getPlayer()));
	}

	public static String key(ResourceKey<Level> dimension, BlockPos pos) {
		return dimension.identifier() + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
	}

	@Nullable
	public static Altar parse(String key, String name) {
		String[] parts = key.split(" ");
		Identifier dim = parts.length == 4 ? Identifier.tryParse(parts[0]) : null;
		if (dim == null) {
			return null;
		}
		try {
			BlockPos pos = new BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
			return new Altar(key, ResourceKey.create(Registries.DIMENSION, dim), pos, name == null || name.isEmpty() ? null : name);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** Every altar this soul has found, in the order found. */
	public static List<Altar> of(@Nullable HellState.Soul soul) {
		List<Altar> out = new ArrayList<>();
		if (soul != null) {
			for (Map.Entry<String, String> e : soul.altars.entrySet()) {
				Altar a = parse(e.getKey(), e.getValue());
				if (a != null) {
					out.add(a);
				}
			}
		}
		return out;
	}

	/** "Blood Altar in Limbo", "Blood Altar in the Forge of Dis": where it stands. */
	public static String defaultName(ResourceKey<Level> dimension, BlockPos pos) {
		String path = dimension.identifier().getPath();
		if (dimension == Level.OVERWORLD) {
			return "Blood Altar in " + Pilgrimage.shortName(InfernoGeometry.circleAt(pos.getX() + 0.5, pos.getZ() + 0.5));
		}
		if (dimension == Level.NETHER) {
			return "Blood Altar in the Forge of Dis";
		}
		if (path.equals("purgatory") || path.equals("paradiso")) {
			return "Blood Altar in " + Character.toUpperCase(path.charAt(0)) + path.substring(1);
		}
		return dimension == Level.END ? "Blood Altar in the End" : "Blood Altar";
	}

	/** True for the altars that are already places to travel to: the Gate's and the Rests'. */
	private static boolean alreadyAPlace(MinecraftServer server, ResourceKey<Level> dimension, BlockPos pos) {
		HellState state = HellState.get(server);
		if (state.starterAltar != null && state.starterAltar.dimension() == dimension && state.starterAltar.pos().equals(pos)) {
			return true;
		}
		if (dimension != Level.OVERWORLD) {
			return false;
		}
		ShrineSites.Site site = ShrineSites.nearest(pos.getX() + 0.5, pos.getZ() + 0.5);
		return site != null && Math.hypot(site.x() - pos.getX(), site.z() - pos.getZ()) < 3 && Shrines.floor(server, site) != null;
	}

	/** Called when a player uses a Blood Altar: remembers it for them if it's new. */
	public static void remember(ServerPlayer player, ServerLevel level, BlockPos pos) {
		if (alreadyAPlace(level.getServer(), level.dimension(), pos)) {
			return;
		}
		HellState.Soul soul = Hearts.soul(player);
		if (soul.ghost) {
			return;
		}
		String key = key(level.dimension(), pos);
		if (soul.altars.containsKey(key)) {
			return;
		}
		if (soul.altars.size() >= MAX) {
			String oldest = null;
			for (Map.Entry<String, String> e : soul.altars.entrySet()) {
				if (e.getValue().isEmpty()) {
					oldest = e.getKey();
					break;
				}
			}
			if (oldest == null) {
				player.sendSystemMessage(Component.literal("You remember " + MAX + " named altars already: this one can't be added to your travel menu.")
						.withStyle(ChatFormatting.GRAY));
				return;
			}
			soul.altars.remove(oldest);
		}
		soul.altars.put(key, "");
		HellState.get(level.getServer()).setDirty();
		Dialogue.notice(player, Component.literal("Blood Altar remembered (" + defaultName(level.dimension(), pos)
				+ "): you can travel back here from any Blood Altar. Type /rest name and a name beside it to name it.").withStyle(ChatFormatting.GOLD));
		level.playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.PLAYERS, 0.6f, 1.4f);
		send(player);
	}

	/** The found altar this player stands beside (within NAMING_REACH), or null. */
	@Nullable
	public static Altar beside(ServerPlayer player) {
		Altar best = null;
		double bestD = NAMING_REACH;
		for (Altar a : of(Hearts.soul(player))) {
			if (a.dimension() != player.level().dimension()) {
				continue;
			}
			double d = Math.sqrt(a.pos().distToCenterSqr(player.position()));
			if (d <= bestD) {
				best = a;
				bestD = d;
			}
		}
		return best;
	}

	/** Forgets a found altar (it was broken). */
	public static void forget(ServerPlayer player, String key) {
		if (Hearts.soul(player).altars.remove(key) != null) {
			HellState.get(player.level().getServer()).setDirty();
			send(player);
		}
	}

	/** True if the altar still stands (loading its chunk to look). */
	public static boolean stands(MinecraftServer server, Altar a) {
		ServerLevel level = server.getLevel(a.dimension());
		if (level == null) {
			return false;
		}
		level.getChunk(a.pos().getX() >> 4, a.pos().getZ() >> 4);
		return BloodAltar.isAltar(level, a.pos());
	}

	/**
	 * Where to set a traveller down beside an altar: two blocks off on whichever side has room to stand (a ruined altar's
	 * chest is on its south side), else on top of it.
	 */
	public static BlockPos arrival(ServerLevel level, BlockPos altar) {
		int[][] sides = {{0, -2}, {2, 0}, {-2, 0}, {0, 2}, {2, -2}, {-2, -2}, {2, 2}, {-2, 2}};
		for (int dy : new int[]{0, 1, -1}) {
			for (int[] s : sides) {
				BlockPos feet = altar.offset(s[0], dy, s[1]);
				if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
						&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
						&& level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)) {
					return feet;
				}
			}
		}
		return altar.above();
	}

	public static void send(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, FoundAltarsPayload.TYPE)) {
			return;
		}
		List<String> dims = new ArrayList<>();
		List<Long> positions = new ArrayList<>();
		List<String> names = new ArrayList<>();
		for (Altar a : of(HellState.get(player.level().getServer()).existing(player.getUUID()))) {
			dims.add(a.dimension().identifier().toString());
			positions.add(a.pos().asLong());
			names.add(a.name());
		}
		ServerPlayNetworking.send(player, new FoundAltarsPayload(dims, positions, names));
	}
}
