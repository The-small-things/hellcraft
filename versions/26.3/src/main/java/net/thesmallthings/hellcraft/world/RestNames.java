package net.thesmallthings.hellcraft.world;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.blood.Hearts;
import net.thesmallthings.hellcraft.net.RestNamesPayload;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Names for Virgil's Rests, each player's own: stand in a Rest and type {@code /rest name <name>}, and that Rest goes by
 * your name in your travel menu, on your Map of Hell and in your Journal (nobody else sees it). {@code /rest name} alone
 * gives it back its own name; {@code /rest list} shows the names you've given. Kept in {@code HellState.Soul.restNames}
 * and sent to the player's game as {@code RestNamesPayload}.
 */
public final class RestNames {
	private RestNames() {
	}

	/** The longest name a Rest can be given. */
	public static final int MAX_LENGTH = 32;

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(RestNamesPayload.TYPE, RestNamesPayload.CODEC);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.getPlayer()));
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> commands(dispatcher));
	}

	/** What this soul calls a Rest: the name they gave it, or the Rest's own ("Circle II — Lust, east road"). */
	public static String of(HellState.Soul soul, ShrineSites.Site site) {
		String name = soul == null ? null : soul.restNames.get(site.id());
		return name != null ? name : site.label();
	}

	/** The name this soul gave a Rest, or null. */
	@Nullable
	public static String given(HellState.Soul soul, ShrineSites.Site site) {
		return soul == null ? null : soul.restNames.get(site.id());
	}

	/** A name as it is kept: trimmed, without formatting codes or line breaks, at most MAX_LENGTH long; empty means none. */
	public static String clean(String raw) {
		String name = raw.replaceAll("§.?", "").replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
		return name.length() > MAX_LENGTH ? name.substring(0, MAX_LENGTH).trim() : name;
	}

	/** The built Rest whose ring this player stands in, or null. */
	@Nullable
	public static ShrineSites.Site standingIn(ServerPlayer player) {
		if (player.level().dimension() != Level.OVERWORLD) {
			return null;
		}
		ShrineSites.Site site = ShrineSites.nearest(player.getX(), player.getZ());
		if (site == null || Math.hypot(site.x() + 0.5 - player.getX(), site.z() + 0.5 - player.getZ()) > Shrines.SAFE_RADIUS) {
			return null;
		}
		Integer floor = Shrines.floor(player.level().getServer(), site);
		return floor != null && Math.abs(player.getY() - floor) < 24 ? site : null;
	}

	/** Names (or, with an empty name, un-names) a Rest for this soul. Returns what happened, in plain words. */
	public static String name(HellState.Soul soul, ShrineSites.Site site, String raw) {
		String name = clean(raw);
		if (name.isEmpty()) {
			return soul.restNames.remove(site.id()) != null
					? "This Rest has its own name again: " + site.label() + "."
					: "This Rest already goes by its own name: " + site.label() + ". To name it, type /rest name and then the name.";
		}
		soul.restNames.put(site.id(), name);
		return "You named this Rest \"" + name + "\". It goes by that name in your travel menu, on your Map of Hell and in your Journal (only you see it).";
	}

	public static void send(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, RestNamesPayload.TYPE)) {
			return;
		}
		HellState.Soul soul = HellState.get(player.level().getServer()).existing(player.getUUID());
		List<String> ids = new ArrayList<>();
		List<String> names = new ArrayList<>();
		if (soul != null) {
			for (Map.Entry<String, String> e : soul.restNames.entrySet()) {
				ids.add(e.getKey());
				names.add(e.getValue());
			}
		}
		ServerPlayNetworking.send(player, new RestNamesPayload(ids, names));
	}

	private static void commands(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("rest")
				.then(Commands.literal("name")
						.executes(ctx -> rename(ctx.getSource(), ""))
						.then(Commands.argument("name", StringArgumentType.greedyString())
								.executes(ctx -> rename(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
				.then(Commands.literal("list").executes(ctx -> list(ctx.getSource()))));
		dispatcher.register(Commands.literal("hellcraft")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("rests").then(Commands.literal("names").then(Commands.literal("test").executes(ctx -> {
					String result = selfTest();
					HellcraftMod.LOGGER.info(result);
					ctx.getSource().sendSuccess(() -> Component.literal(result), false);
					return 1;
				})))));
	}

	private static int rename(CommandSourceStack source, String raw) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ShrineSites.Site site = standingIn(player);
		if (site == null) {
			source.sendFailure(Component.literal("Stand inside a Virgil's Rest (the ring of soul fire round its altar) to name it."));
			return 0;
		}
		HellState.Soul soul = Hearts.soul(player);
		String result = name(soul, site, raw);
		HellState.get(player.level().getServer()).setDirty();
		send(player);
		source.sendSuccess(() -> Component.literal(result).withStyle(ChatFormatting.GOLD), false);
		return 1;
	}

	private static int list(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		HellState.Soul soul = Hearts.soul(source.getPlayerOrException());
		if (soul.restNames.isEmpty()) {
			source.sendSuccess(() -> Component.literal("You haven't named any Rests. Stand in one and type /rest name and then a name.")
					.withStyle(ChatFormatting.GRAY), false);
			return 0;
		}
		source.sendSuccess(() -> Component.literal("The Rests you've named:").withStyle(ChatFormatting.GOLD), false);
		for (ShrineSites.Site site : ShrineSites.all()) {
			String name = soul.restNames.get(site.id());
			if (name != null) {
				source.sendSuccess(() -> Component.literal("  " + name).withStyle(ChatFormatting.YELLOW)
						.append(Component.literal(" (" + site.label() + ")").withStyle(ChatFormatting.GRAY)), false);
			}
		}
		return soul.restNames.size();
	}

	/** For the smoke test: every Rest's own name is different, and a name is kept, cleaned, private and given back. */
	public static String selfTest() {
		java.util.Set<String> labels = new java.util.HashSet<>();
		for (ShrineSites.Site site : ShrineSites.all()) {
			labels.add(site.label());
		}
		boolean unique = labels.size() == ShrineSites.all().size();
		ShrineSites.Site site = ShrineSites.byId("lust_southeast");
		HellState.Soul alice = new HellState.Soul();
		HellState.Soul bob = new HellState.Soul();
		name(alice, site, "  §cHome  sweet\nhome ");
		boolean kept = "Home sweet home".equals(of(alice, site));
		boolean privately = of(bob, site).equals(site.label());
		name(alice, site, "x".repeat(50));
		boolean shortened = of(alice, site).length() == MAX_LENGTH;
		name(alice, site, "");
		boolean undone = of(alice, site).equals(site.label());
		return "Rest names test: " + ShrineSites.all().size() + " Rests, all named apart=" + unique + " (" + site.label() + "); kept=" + kept
				+ " private=" + privately + " shortened=" + shortened + " undone=" + undone;
	}
}
