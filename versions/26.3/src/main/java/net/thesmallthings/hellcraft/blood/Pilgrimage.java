package net.thesmallthings.hellcraft.blood;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.net.Dialogue;
import net.thesmallthings.hellcraft.net.PilgrimPayload;
import net.thesmallthings.hellcraft.util.Feedback;
import net.thesmallthings.hellcraft.util.Journey;
import net.thesmallthings.hellcraft.world.Circle;
import net.thesmallthings.hellcraft.world.HellWorldgen;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.Purgatory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The Pilgrimage: an optional challenge, to walk from the Gate of Hell to Purgatory without dying once. It is begun at the
 * Pilgrims' Stone by the Gate (PilgrimStone; right-click its lectern twice) or with /pilgrimage start there, and given up
 * with /pilgrimage abandon. While it lasts its time (ticks of play) and the deepest circle reached are kept
 * (HellState.Soul.pilgrimTicks, pilgrimDeepest) and shown on the pilgrim's screen (PilgrimPayload); Blood Altars won't
 * carry a pilgrim. Climbing out to Purgatory finishes it (a Pilgrim's Shell, an advancement, a line on the Stone); dying
 * ends it, and nothing more is lost unless the server makes it hardcore (pilgrimageHardcore: the pilgrim becomes a ghost).
 */
public final class Pilgrimage {
	private Pilgrimage() {
	}

	/** Seconds after the warning in which a second right-click (or /pilgrimage start) begins it. */
	private static final int CONFIRM_SECONDS = 30;
	/** How many ended pilgrimages the Stone remembers. */
	private static final int REMEMBERED = 50;
	/** From this circle down, the whole server hears when a pilgrim gets there (Heresy: inside the Walls of Dis). */
	private static final int ANNOUNCE_FROM = 6;

	private static final Map<UUID, Long> ASKED = new HashMap<>();

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(PilgrimPayload.TYPE, PilgrimPayload.CODEC);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ASKED.remove(handler.getPlayer().getUUID()));
		ServerTickEvents.END_SERVER_TICK.register(Pilgrimage::tick);
		// after DeathHandler (registered earlier), so a hardcore pilgrim's last heart has already been counted
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				onDeath(player);
			}
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> commands(dispatcher));
	}

	public static boolean on(HellState.Soul soul) {
		return soul.pilgrimTicks >= 0;
	}

	// ------------------------------------------------------------------ setting out

	/** Why this player can't set out now, or null if they can. */
	private static String cannotStart(ServerPlayer player) {
		HellState.Soul soul = Hearts.soul(player);
		if (!HellConfig.get().pilgrimage) {
			return "Pilgrimages are turned off on this server.";
		}
		if (on(soul)) {
			return "You're already on a pilgrimage (" + time(soul.pilgrimTicks) + " so far). /pilgrimage abandon gives it up.";
		}
		if (soul.ghost) {
			return "Ghosts can't set out on a pilgrimage. Come back to life first.";
		}
		if (player.isCreative() || player.isSpectator()) {
			return "A pilgrimage is walked in survival mode.";
		}
		if (!atTheGate(player)) {
			return "A pilgrimage starts at the Gate of Hell: go to the Pilgrims' Stone by the spawn, outside the Gate.";
		}
		return null;
	}

	/** At the Gate of Hell or outside it, in an Inferno world. */
	static boolean atTheGate(ServerPlayer player) {
		return player.level().dimension() == Level.OVERWORLD && HellWorldgen.isInferno(player.level())
				&& InfernoGeometry.effectiveRadius(player.getX(), player.getZ()) >= InfernoGeometry.GATE_WALL_RADIUS - 30;
	}

	/** The lectern at the Stone, or /pilgrimage start: the first time a warning, the second (within 30 s) the start. */
	public static void ask(ServerPlayer player, boolean typed) {
		String no = cannotStart(player);
		if (no != null) {
			Dialogue.notice(player, Component.literal(no).withStyle(ChatFormatting.GRAY));
			return;
		}
		long now = player.level().getGameTime();
		Long asked = ASKED.get(player.getUUID());
		if (asked != null && now - asked <= CONFIRM_SECONDS * 20L) {
			ASKED.remove(player.getUUID());
			begin(player);
			return;
		}
		ASKED.put(player.getUUID(), now);
		String loss = HellConfig.get().pilgrimageHardcore
				? "Die and the pilgrimage ends, and on this server you also lose every heart and become a ghost until someone revives you."
				: "Die and the pilgrimage ends (you lose nothing more than usual).";
		Dialogue.notice(player, Component.literal("A pilgrimage: reach Purgatory without dying once. " + loss + " Blood Altars won't carry you while "
				+ "you walk it. " + (typed ? "Type /pilgrimage start again" : "Right-click again") + " to begin.").withStyle(ChatFormatting.GOLD));
	}

	private static void begin(ServerPlayer player) {
		HellState.Soul soul = Hearts.soul(player);
		start(soul);
		HellState.get(player.level().getServer()).setDirty();
		String name = player.getGameProfile().name();
		player.level().getServer().getPlayerList().broadcastSystemMessage(Component.literal("✦ " + name
				+ " sets out on a pilgrimage from the Gate of Hell to Purgatory.").withStyle(ChatFormatting.GOLD), false);
		Dialogue.notice(player, Component.literal("Your pilgrimage has begun: reach Purgatory without dying. Your time and deepest circle show at the "
				+ "top left of your screen. /pilgrimage abandon gives it up.").withStyle(ChatFormatting.GOLD));
		Feedback.sound(player, SoundEvents.BELL_BLOCK, SoundSource.MASTER, 1.0f, 0.7f);
		HellcraftMod.LOGGER.info("Pilgrimage begun by {}", name);
		send(player);
	}

	static void start(HellState.Soul soul) {
		soul.pilgrimTicks = 0;
		soul.pilgrimDeepest = 0;
	}

	// ------------------------------------------------------------------ on the way

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		HellState state = HellState.get(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			HellState.Soul soul = state.existing(player.getUUID());
			if (soul == null || !on(soul)) {
				continue;
			}
			if (player.isCreative() || player.isSpectator()) {
				stop(soul);
				state.setDirty();
				Dialogue.notice(player, Component.literal("Your pilgrimage ended: it can't be walked in creative or spectator mode.").withStyle(ChatFormatting.GRAY));
				send(player);
				continue;
			}
			if (player.level().dimension() == Purgatory.LEVEL) {
				finish(player, soul);
				continue;
			}
			soul.pilgrimTicks += 20;
			state.setDirty();
			if (player.level().dimension() == Level.OVERWORLD && HellWorldgen.isInferno(player.level())) {
				Circle circle = InfernoGeometry.circleAt(player.getX(), player.getZ());
				if (reach(soul, circle.depth())) {
					String line = player.getGameProfile().name() + ", on pilgrimage, has reached " + circle.title() + " (" + time(soul.pilgrimTicks) + ").";
					if (circle.depth() >= ANNOUNCE_FROM) {
						server.getPlayerList().broadcastSystemMessage(Component.literal("✦ " + line).withStyle(ChatFormatting.GOLD), false);
					} else {
						player.sendOverlayMessage(Component.literal("Pilgrimage: " + circle.title() + " reached (" + time(soul.pilgrimTicks) + ").")
								.withStyle(ChatFormatting.GOLD));
					}
				}
			}
			send(player);
		}
	}

	/** Notes a circle reached; true if it is deeper than any before on this pilgrimage. */
	static boolean reach(HellState.Soul soul, int depth) {
		if (depth > soul.pilgrimDeepest) {
			soul.pilgrimDeepest = depth;
			return true;
		}
		return false;
	}

	static void stop(HellState.Soul soul) {
		soul.pilgrimTicks = -1;
		soul.pilgrimDeepest = 0;
	}

	// ------------------------------------------------------------------ the end of it

	private static void finish(ServerPlayer player, HellState.Soul soul) {
		MinecraftServer server = player.level().getServer();
		HellState state = HellState.get(server);
		String name = player.getGameProfile().name();
		int ticks = soul.pilgrimTicks;
		end(state, soul, name, true, "Purgatory");
		server.getPlayerList().broadcastSystemMessage(Component.literal("✦ " + name + " finished the Pilgrimage: from the Gate of Hell to Purgatory "
				+ "without dying, in " + time(ticks) + ".").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		BloodItems.give(player, new ItemStack(HellItems.PILGRIM_SHELL));
		Journey.award(player, "journey/pilgrim");
		player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 30));
		player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("PILGRIM").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
		player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(time(ticks) + ", without dying")
				.withStyle(ChatFormatting.YELLOW)));
		Dialogue.notice(player, Component.literal("Pilgrimage complete, in " + time(ticks) + ". You were given a Pilgrim's Shell, and your name is on "
				+ "the Pilgrims' Stone by the Gate of Hell.").withStyle(ChatFormatting.GOLD));
		HellcraftMod.LOGGER.info("Pilgrimage finished by {} in {}", name, time(ticks));
		send(player);
	}

	private static void onDeath(ServerPlayer player) {
		HellState state = HellState.get(player.level().getServer());
		HellState.Soul soul = state.existing(player.getUUID());
		if (soul == null || !on(soul)) {
			return;
		}
		String name = player.getGameProfile().name();
		String where = where(player);
		int ticks = soul.pilgrimTicks;
		end(state, soul, name, false, where);
		MinecraftServer server = player.level().getServer();
		server.getPlayerList().broadcastSystemMessage(Component.literal("✦ " + name + "'s pilgrimage ended in " + where + " after " + time(ticks) + ".")
				.withStyle(ChatFormatting.GOLD), false);
		HellcraftMod.LOGGER.info("Pilgrimage ended: {} in {} after {}", name, where, time(ticks));
		if (HellConfig.get().pilgrimageHardcore && hardcore(soul)) {
			soul.deathSpot = new HellState.GlobalSpot(player.level().dimension(), player.blockPosition());
			Journey.award(player, "journey/hell_is_full");
			server.getPlayerList().broadcastSystemMessage(Component.literal("Hell is full. ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
					.append(Component.literal(name + " died on a hardcore pilgrimage and is now a ghost. Revive them at a Blood Altar.")
							.withStyle(ChatFormatting.RED)), false);
		}
		player.sendSystemMessage(Component.literal("Your pilgrimage is over: you died. Start a new one at the Pilgrims' Stone by the Gate of Hell "
				+ "whenever you like.").withStyle(ChatFormatting.GRAY));
		state.setDirty();
		send(player);
	}

	/** The hardcore rule: every heart lost, a ghost until revived. False if the soul was a ghost already. */
	static boolean hardcore(HellState.Soul soul) {
		if (soul.ghost) {
			return false;
		}
		soul.hearts = 0;
		soul.ghost = true;
		return true;
	}

	/** Ends a pilgrimage and remembers it on the Stone. */
	static HellState.Pilgrim end(HellState state, HellState.Soul soul, String name, boolean done, String where) {
		HellState.Pilgrim record = new HellState.Pilgrim(name, Math.max(0, soul.pilgrimTicks), soul.pilgrimDeepest, done, where);
		if (done) {
			soul.pilgrimages++;
			soul.pilgrimBest = soul.pilgrimBest == 0 ? record.ticks() : Math.min(soul.pilgrimBest, record.ticks());
		}
		stop(soul);
		state.pilgrims.add(record);
		if (state.pilgrims.size() > REMEMBERED) {
			// forget the slowest done or shallowest fallen first, never a record holder
			List<HellState.Pilgrim> keep = new ArrayList<>(fastest(state, 10));
			keep.addAll(deepest(state, 10));
			for (int i = 0; i < state.pilgrims.size() && state.pilgrims.size() > REMEMBERED; i++) {
				if (!keep.contains(state.pilgrims.get(i))) {
					state.pilgrims.remove(i--);
				}
			}
		}
		state.setDirty();
		return record;
	}

	/** The /pilgrimage abandon: no record, nothing lost. */
	private static int abandon(ServerPlayer player) {
		HellState.Soul soul = Hearts.soul(player);
		if (!on(soul)) {
			Dialogue.notice(player, Component.literal("You're not on a pilgrimage.").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		String time = time(soul.pilgrimTicks);
		stop(soul);
		HellState.get(player.level().getServer()).setDirty();
		Dialogue.notice(player, Component.literal("You gave up your pilgrimage after " + time + ". Nothing else changes.").withStyle(ChatFormatting.GRAY));
		send(player);
		return 1;
	}

	/** The fastest finished pilgrimages, quickest first. */
	public static List<HellState.Pilgrim> fastest(HellState state, int n) {
		return state.pilgrims.stream().filter(HellState.Pilgrim::done).sorted(Comparator.comparingInt(HellState.Pilgrim::ticks)).limit(n).toList();
	}

	/** The pilgrimages that ended deepest, deepest first (the longer walk first among equals). */
	public static List<HellState.Pilgrim> deepest(HellState state, int n) {
		return state.pilgrims.stream().filter(p -> !p.done())
				.sorted(Comparator.comparingInt(HellState.Pilgrim::deepest).reversed().thenComparing(Comparator.comparingInt(HellState.Pilgrim::ticks).reversed()))
				.limit(n).toList();
	}

	/** Where a player is, as the end of a pilgrimage is told ("Wrath", "the Forge of Dis"). */
	static String where(ServerPlayer player) {
		if (player.level().dimension() == Level.OVERWORLD && HellWorldgen.isInferno(player.level())) {
			return shortName(InfernoGeometry.circleAt(player.getX(), player.getZ()));
		}
		if (player.level().dimension() == Level.NETHER) {
			return "the Forge of Dis";
		}
		return "a strange place";
	}

	/** "Wrath" for Circle V — Wrath, "the Dark Wood" for The Dark Wood. */
	public static String shortName(Circle circle) {
		String title = circle.title();
		int dash = title.indexOf("— ");
		if (dash >= 0) {
			return title.substring(dash + 2);
		}
		return title.startsWith("The ") ? "the " + title.substring(4) : title;
	}

	/** The circle of this depth (0: the Gate), by its short name. */
	public static String depthName(int depth) {
		for (Circle c : Circle.values()) {
			if (c.depth() == depth && depth > 0) {
				return shortName(c);
			}
		}
		return "the Gate";
	}

	/** "2h 14m", "14m 05s". */
	public static String time(int ticks) {
		int seconds = Math.max(0, ticks) / 20;
		int h = seconds / 3600;
		int m = seconds / 60 % 60;
		return h > 0 ? String.format(Locale.ROOT, "%dh %02dm", h, m) : String.format(Locale.ROOT, "%dm %02ds", m, seconds % 60);
	}

	/** The pilgrim's mark on their screen. */
	public static void send(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, PilgrimPayload.TYPE)) {
			return;
		}
		HellState.Soul soul = HellState.get(player.level().getServer()).existing(player.getUUID());
		boolean on = soul != null && on(soul);
		ServerPlayNetworking.send(player, new PilgrimPayload(on, on ? soul.pilgrimTicks : 0, on ? soul.pilgrimDeepest : 0, HellConfig.get().pilgrimageHardcore));
	}

	// ------------------------------------------------------------------ commands and the self-test

	private static void commands(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("pilgrimage")
				.executes(ctx -> status(ctx.getSource()))
				.then(Commands.literal("start").executes(ctx -> {
					ask(ctx.getSource().getPlayerOrException(), true);
					return 1;
				}))
				.then(Commands.literal("abandon").executes(ctx -> abandon(ctx.getSource().getPlayerOrException()))));
		dispatcher.register(Commands.literal("hellcraft")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("pilgrimage")
						.then(Commands.literal("test").executes(ctx -> {
							String result = selfTest(ctx.getSource().getServer());
							ctx.getSource().sendSuccess(() -> Component.literal(result), false);
							return 1;
						}))
						.then(Commands.literal("start").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
							ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
							if (on(Hearts.soul(p))) {
								ctx.getSource().sendFailure(Component.literal(p.getGameProfile().name() + " is already on a pilgrimage."));
								return 0;
							}
							begin(p);
							return 1;
						})))
						.then(Commands.literal("abandon").then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> abandon(EntityArgument.getPlayer(ctx, "player")))))));
	}

	private static int status(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		HellState.Soul soul = Hearts.soul(player);
		if (on(soul)) {
			source.sendSuccess(() -> Component.literal("On pilgrimage: " + time(soul.pilgrimTicks) + ", deepest " + depthName(soul.pilgrimDeepest)
					+ ". Reach Purgatory without dying. /pilgrimage abandon gives it up.").withStyle(ChatFormatting.GOLD), false);
		} else {
			source.sendSuccess(() -> Component.literal("Not on a pilgrimage. Start one at the Pilgrims' Stone by the Gate of Hell (right-click its lectern), "
					+ "or with /pilgrimage start there." + (soul.pilgrimages > 0 ? " You have finished " + soul.pilgrimages + ", the fastest in "
					+ time(soul.pilgrimBest) + "." : "")).withStyle(ChatFormatting.GRAY), false);
		}
		return 1;
	}

	/**
	 * /hellcraft pilgrimage test: a pilgrim (a soul made for the test) sets out, reaches Gluttony, dies there (the Stone
	 * remembers how deep), sets out again and reaches Purgatory (the Stone remembers how fast); the hardcore rule makes a ghost.
	 */
	public static String selfTest(MinecraftServer server) {
		HellState state = HellState.get(server);
		int before = state.pilgrims.size();
		HellState.Soul soul = new HellState.Soul();
		soul.hearts = 10;
		String name = "PilgrimTest";
		StringBuilder out = new StringBuilder();
		boolean ok = !on(soul);
		start(soul);
		ok &= on(soul) && soul.pilgrimTicks == 0;
		soul.pilgrimTicks += 20 * 60 * 5;
		ok &= reach(soul, 1) & reach(soul, 3) & !reach(soul, 2);
		out.append("set out; ").append(depthName(soul.pilgrimDeepest)).append(" reached (").append(time(soul.pilgrimTicks)).append("); ");
		HellState.Pilgrim fell = end(state, soul, name, false, "Gluttony");
		ok &= !on(soul) && !fell.done() && fell.deepest() == 3 && deepest(state, 50).contains(fell);
		out.append("ended in ").append(fell.where()).append(" after ").append(time(fell.ticks())).append("; ");
		start(soul);
		soul.pilgrimTicks = 20 * 3600 + 20 * 60 * 5;
		reach(soul, 9);
		HellState.Pilgrim done = end(state, soul, name, true, "Purgatory");
		ok &= done.done() && soul.pilgrimages == 1 && soul.pilgrimBest == done.ticks() && fastest(state, 50).contains(done);
		out.append("done in ").append(time(done.ticks())).append("; ");
		ok &= hardcore(soul) && soul.ghost && soul.hearts == 0 && !hardcore(soul);
		out.append("hardcore ghost=").append(soul.ghost);
		ok &= shortName(Circle.WRATH).equals("Wrath") && time(20 * (2 * 3600 + 14 * 60)).equals("2h 14m");
		state.pilgrims.removeIf(p -> p.name().equals(name));
		ok &= state.pilgrims.size() == before;
		return (ok ? "Pilgrimage test OK: " : "Pilgrimage test FAILED: ") + out;
	}
}
