package net.thesmallthings.hellcraft.world;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.net.WaymarkEditPayload;
import net.thesmallthings.hellcraft.net.WaymarksPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Waymarks on the Map of Hell: a player right-clicks the map to place one, names it, picks a colour, and may share it
 * with everyone on the server. They're kept with the world (HellState.waymarks), so they're there after a restart and on
 * any computer; each player is sent their own and the shared ones (WaymarksPayload). The client draws them on the map
 * (client/nav/MapOfHellScreen) and the one being followed on the compass (CompassHud, Waypoints).
 */
public final class Waymarks {
	private Waymarks() {
	}

	/** How many waymarks one player may have. */
	public static final int MAX_EACH = 48;
	public static final int MAX_NAME = 32;
	public static final int COLOURS = 8;

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(WaymarksPayload.TYPE, WaymarksPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(WaymarkEditPayload.TYPE, WaymarkEditPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(WaymarkEditPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			String result = edit(player.level().getServer(), player.getUUID(), player.getGameProfile().name(), payload);
			if (result != null) {
				player.sendOverlayMessage(Component.literal(result));
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.getPlayer()));
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> commands(dispatcher));
	}

	/** Places, changes or removes a waymark for a player; null if done, else a plain line saying why not. */
	static String edit(MinecraftServer server, UUID who, String whoName, WaymarkEditPayload p) {
		HellState state = HellState.get(server);
		if (p.remove()) {
			boolean removed = state.waymarks.removeIf(w -> w.id() == p.id() && w.owner().equals(who));
			if (!removed) {
				return "That waymark isn't yours to remove.";
			}
		} else {
			String name = clean(p.name());
			int x = Mth.clamp(p.x(), -(int) InfernoGeometry.BORDER_RADIUS, (int) InfernoGeometry.BORDER_RADIUS);
			int z = Mth.clamp(p.z(), -(int) InfernoGeometry.BORDER_RADIUS, (int) InfernoGeometry.BORDER_RADIUS);
			int colour = Math.floorMod(p.colour(), COLOURS);
			if (p.id() == 0) {
				long mine = state.waymarks.stream().filter(w -> w.owner().equals(who)).count();
				if (mine >= MAX_EACH) {
					return "You have " + MAX_EACH + " waymarks already: remove one first (click it on the map).";
				}
				state.waymarks.add(new HellState.Waymark(state.nextWaymark++, who, whoName, name, x, z, colour, p.shared()));
			} else {
				int i = indexOf(state, p.id());
				if (i < 0 || !state.waymarks.get(i).owner().equals(who)) {
					return "That waymark isn't yours to change.";
				}
				state.waymarks.set(i, new HellState.Waymark(p.id(), who, whoName, name, x, z, colour, p.shared()));
			}
		}
		state.setDirty();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			send(player);
		}
		return null;
	}

	private static int indexOf(HellState state, int id) {
		for (int i = 0; i < state.waymarks.size(); i++) {
			if (state.waymarks.get(i).id() == id) {
				return i;
			}
		}
		return -1;
	}

	/** A name kept short and plain (no formatting codes); "Waymark" if left empty. */
	static String clean(String name) {
		String out = name.replaceAll("§.?", "").strip();
		if (out.length() > MAX_NAME) {
			out = out.substring(0, MAX_NAME).strip();
		}
		return out.isEmpty() ? "Waymark" : out;
	}

	/** What one player sees: their own waymarks and everyone's shared ones. */
	static List<WaymarksPayload.Entry> visible(HellState state, UUID who) {
		List<WaymarksPayload.Entry> out = new ArrayList<>();
		for (HellState.Waymark w : state.waymarks) {
			boolean mine = w.owner().equals(who);
			if (mine || w.shared()) {
				out.add(new WaymarksPayload.Entry(w.id(), w.name(), w.x(), w.z(), w.colour(), w.shared(), mine, w.ownerName()));
			}
		}
		return out;
	}

	public static void send(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, WaymarksPayload.TYPE)) {
			ServerPlayNetworking.send(player, new WaymarksPayload(visible(HellState.get(player.level().getServer()), player.getUUID())));
		}
	}

	/** For the smoke test: place, rename, share and remove waymarks for two made-up souls, and check who sees what. */
	public static String selfTest(MinecraftServer server) {
		HellState state = HellState.get(server);
		UUID a = UUID.nameUUIDFromBytes("hellcraft-waymark-test-a".getBytes());
		UUID b = UUID.nameUUIDFromBytes("hellcraft-waymark-test-b".getBytes());
		List<String> wrong = new ArrayList<>();
		try {
			edit(server, a, "TestA", new WaymarkEditPayload(0, "  §cMy base  ", 100, 200, 3, false, false));
			HellState.Waymark placed = state.waymarks.stream().filter(w -> w.owner().equals(a)).findFirst().orElse(null);
			if (placed == null || !placed.name().equals("My base") || placed.colour() != 3) {
				wrong.add("placing");
			}
			if (!visible(state, b).isEmpty()) {
				wrong.add("a private waymark is seen by others");
			}
			int id = placed == null ? -1 : placed.id();
			if (edit(server, b, "TestB", new WaymarkEditPayload(id, "stolen", 0, 0, 0, true, false)) == null) {
				wrong.add("someone else could change it");
			}
			edit(server, a, "TestA", new WaymarkEditPayload(id, "Iron farm", 120, 210, 9, true, false));
			List<WaymarksPayload.Entry> seen = visible(state, b);
			if (seen.size() != 1 || !seen.get(0).name().equals("Iron farm") || seen.get(0).mine() || seen.get(0).colour() != 1) {
				wrong.add("sharing");
			}
			if (edit(server, b, "TestB", new WaymarkEditPayload(id, "", 0, 0, 0, false, true)) == null) {
				wrong.add("someone else could remove it");
			}
			edit(server, a, "TestA", new WaymarkEditPayload(id, "", 0, 0, 0, false, true));
			if (!visible(state, a).isEmpty()) {
				wrong.add("removing");
			}
		} finally {
			state.waymarks.removeIf(w -> w.owner().equals(a) || w.owner().equals(b));
			state.setDirty();
		}
		return wrong.isEmpty() ? "Waymarks test OK: placed, renamed, shared, guarded and removed" : "Waymarks test FAILED: " + String.join(", ", wrong);
	}

	private static void commands(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("hellcraft")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("waymarks")
						.then(Commands.literal("test").executes(ctx -> {
							String result = selfTest(ctx.getSource().getServer());
							HellcraftMod.LOGGER.info(result);
							ctx.getSource().sendSuccess(() -> Component.literal(result), false);
							return 1;
						}))));
	}
}
