package net.thesmallthings.hellcraft.blood;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.cutscene.CutsceneManager;
import net.thesmallthings.hellcraft.hazard.guardian.Guardian;
import net.thesmallthings.hellcraft.hazard.guardian.GuardianManager;
import net.thesmallthings.hellcraft.hazard.lucifer.LuciferManager;
import net.thesmallthings.hellcraft.net.Dialogue;
import net.thesmallthings.hellcraft.world.Gatehouses;
import net.thesmallthings.hellcraft.world.HellWorldgen;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.Zone;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/**
 * First steps: Virgil teaches a new soul the mod a little at a time, as they play, instead of all at once. Each lesson is
 * one short, plain tip with the Journal page to read more, given once. Some come with play time (the Journal, the
 * compass, the map, waymarks), most when the soul first meets the thing (a Blood Fragment, a Blood Altar, a lost heart, a
 * boss waking, a gatehouse). Lessons queue and come no closer together than {@code lessonSeconds}, never during a
 * cutscene and (but for the boss lesson) never mid-fight.
 *
 * Souls from before the lessons existed (half an hour of play or more) are taken to know the basics: their first load marks
 * every lesson learned but the ones about newer features. {@code /lessons} shows how far a soul has got and turns them off or on; config {@code lessons}
 * turns them off for everyone.
 */
public final class Lessons {
	private Lessons() {
	}

	/** A keybind in a lesson's text. */
	private record Key(String id) {
	}

	/**
	 * A lesson: its id, whether it's about a newer feature (so old souls get it too), whether it may come during a fight,
	 * when it's due (soul, player, online seconds) and its text.
	 */
	private record Lesson(String id, boolean feature, boolean inFight, BiPredicate<ServerPlayer, HellState.Soul> due, Object[] text) {
	}

	private static final List<Lesson> LESSONS = new ArrayList<>();
	/** Lessons waiting to be given, by soul, in the order they came due. */
	private static final Map<UUID, LinkedHashSet<String>> QUEUE = new HashMap<>();
	/** When each soul was last taught (game time), and when it joined. */
	private static final Map<UUID, Long> LAST = new HashMap<>();
	private static final Map<UUID, Long> JOINED = new HashMap<>();

	private static void lesson(String id, BiPredicate<ServerPlayer, HellState.Soul> due, Object... text) {
		LESSONS.add(new Lesson(id, false, false, due, text));
	}

	static {
		Key journal = new Key("key.hellcraft.journal");
		Key map = new Key("key.hellcraft.map");
		// with play time: how to find your way
		lesson("journal", (p, s) -> s.playSeconds >= 3,
				"Lost already? Press ", journal, " for your Pilgrim's Journal. Its first page says where you are and where to go next, "
						+ "and the rest explains everything down here.");
		lesson("compass", (p, s) -> s.playSeconds >= 120,
				"The compass at the top of the screen: the red arrow points to the nearest way down into the next circle, the gold "
						+ "diamond to the nearest Virgil's Rest. Walking toward 0, 0 always leads down. (Journal: FINDING THE WAY)");
		lesson("map", (p, s) -> s.playSeconds >= 300,
				"Press ", map, " for the Map of Hell: all nine circles, the four Pilgrim Roads, the Rests you've found and the bosses' lairs. "
						+ "Follow a road and you can't get lost.");
		lesson("vigil", (p, s) -> s.playSeconds >= 540 && p.getInventory().contains(BloodItems::isVigil),
				"You carry Vigil Candles. Right-click one to light it where you stand: the next time you die, you come back there "
						+ "instead of at the rim. (Journal: COMING BACK)");
		lesson("beds", (p, s) -> p.getInventory().contains(ItemTags.BEDS),
				"Beds don't work in Hell: you can't sleep, and one won't set where you come back. Light a Vigil Candle, or set your "
						+ "respawn at a Blood Altar. (Journal: BLOOD ALTARS)");
		lesson("waymarks", (p, s) -> s.playSeconds >= 1500,
				"On the Map of Hell (", map, "), right-click anywhere to set a waymark: name it, share it, and follow it, and the compass "
						+ "points you there.");
		lesson("supplies", (p, s) -> s.playSeconds >= 900,
				"Stock up before you go deep: villages and traders are in the Dark Wood and Limbo, and loot lies in ruined altars, "
						+ "Virgil's Rests and Heresy's tombs. Each circle's card says what to bring. (Journal: SUPPLIES)");
		// hearts and blood
		lesson("hearts", (p, s) -> p.getHealth() <= p.getMaxHealth() / 2 || s.deaths > 0,
				"Careful. Your hearts are your lives here: die to a player and you lose one, kill a player and you take one. "
						+ "At none, you're a ghost until a friend revives you. (Journal: HEARTS)");
		lesson("first_death", (p, s) -> s.deaths > 0 && !s.ghost,
				"You fell, and came back. Whether it cost a heart depends on what killed you: your health bar shows how many hearts "
						+ "you have left. A Blood Heart item gives one back. (Journal: HEARTS)");
		lesson("fragments", (p, s) -> p.getInventory().contains(BloodItems::isFragment),
				"A Blood Fragment: monsters killed by players drop them, more the deeper you go. Hold ",
				(Supplier<String>) () -> String.valueOf(HellConfig.get().fragmentsPerHeart), " in one stack and right-click to make a Blood Heart. They also craft hell weapons and blood armour. (Journal: BLOOD FRAGMENTS)");
		lesson("blood_heart", (p, s) -> p.getInventory().contains(BloodItems::isHeart),
				"A Blood Heart: right-click it to gain a heart (up to ", (Supplier<String>) () -> String.valueOf(HellConfig.get().maxHearts),
				"). /withdraw turns your own hearts "
						+ "into these, to trade or keep safe.");
		lesson("weapons", (p, s) -> p.getInventory().contains(BloodItems::isFragment) && s.playSeconds >= 2400,
				"Your recipe book has the hell weapons and blood armour: each is made from Blood Fragments and does something no "
						+ "vanilla gear does. (Journal: HELL WEAPONS, BLOOD ARMOUR)");
		lesson("altar", (p, s) -> nearAltar(p),
				"A Blood Altar (a respawn anchor on crying obsidian). Right-click it: set your respawn here, buy a Ward against the "
						+ "circles' hazards, revive a ghost, or travel to any Rest you've found. You can build your own. (Journal: BLOOD ALTARS)");
		lesson("lifesteal", (p, s) -> p.getStats().getValue(Stats.CUSTOM.get(Stats.PLAYER_KILLS)) > 0,
				"You took a heart from another soul. Kill someone with less than half your hearts and you take nothing but Cain's "
						+ "Mark: you glow, and whoever kills you is paid for it. No fighting in the Dark Wood, on the Shore or in Limbo. (Journal: FAIR PLAY)");
		lesson("prestige", (p, s) -> HellConfig.get().prestige && s.hearts >= HellConfig.get().maxHearts,
				"You hold all the hearts you can. At a Blood Altar you can Ascend: your hearts drop, but your most goes up for good, "
						+ "and each of the Seven P's gives a lasting bonus. (Journal: PRESTIGE)");
		// the way down
		lesson("gate", (p, s) -> nearGate(p),
				"The Gate of Hell. Through it, the Vestibule and the river Acheron, then Limbo, the first circle. Each circle lies on a "
						+ "terrace below the last; the ways down are cut into the cliffs.");
		lesson("circles", (p, s) -> zone(p) != null && zone(p).circle().depth() >= 1 && zone(p) != Zone.VESTIBULE && zone(p) != Zone.ACHERON,
				"Each circle has a rule of its own, and its card (shown as you enter) says what to do and what to bring. Read them all "
						+ "again in the Journal (THE CIRCLES). I'll warn you of what I can.");
		lesson("rests", (p, s) -> !s.visited.isEmpty(),
				"A Virgil's Rest: no monster comes near it, and setting your respawn at its altar is free. It remembers you: from any "
						+ "Blood Altar you can travel back to every Rest you've reached.");
		lesson("travel", (p, s) -> s.visited.size() >= 2,
				"You've found two Rests now. Right-click any Blood Altar and press Travel to go between them, or back to the Gate. "
						+ "(Journal: BLOOD ALTARS)");
		// bosses
		LESSONS.add(new Lesson("boss", false, true, (p, s) -> inFight(p), new Object[]{
				"A guardian! Watch the ground: red marks mean get out, white means block with your shield now, purple can't be blocked "
						+ "at all, so dodge. No boss stands in the way down: you never have to fight one. (the Codex, in your Journal)"}));
		lesson("spoils", (p, s) -> s.guardiansSlain > 0,
				"Well fought. Your spoils follow your style rank in the fight: hit often, swap weapons, parry, and don't get hit. "
						+ "Your first S and first SSS on each boss pay a Blood Heart. (Journal: STYLE)");
		// newer features: old souls hear of these too
		LESSONS.add(new Lesson("gatehouse", true, false, (p, s) -> Gatehouses.nearBuilt(p.level(), p.getX(), p.getZ(), 16), new Object[]{
				"The gate's towers stand open: go in by the door off the passage. A stair climbs through guard rooms and libraries to "
						+ "the walk over the gate, and on to the roof."}));
		LESSONS.add(new Lesson("walls", true, false, (p, s) -> p.level() instanceof net.minecraft.server.level.ServerLevel level
				&& net.thesmallthings.hellcraft.world.WallRooms.nearBuilt(level, p.getX(), p.getZ(), 12), new Object[]{
				"This wall is lived in: every few steps a door at its foot leads into rooms built into it, a stair winding up through "
						+ "them. The rarer ones hold an anvil, brewing stands, an enchanting table or a treasury. The tall towers along "
						+ "it climb to the walk on top of the wall."}));
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> commands(dispatcher));
	}

	/** On join: a soul from before the lessons (half an hour or more of play) is taken to know all but the newer features'. */
	public static void onJoin(ServerPlayer player, boolean firstJoin) {
		HellState state = HellState.get(player.level().getServer());
		HellState.Soul soul = Hearts.soul(player);
		JOINED.put(player.getUUID(), player.level().getGameTime());
		if (!soul.lessonsStarted) {
			soul.lessonsStarted = true;
			// a soul with real play time behind it (by the game's own statistics: other join handlers may have made its record
			// already) knows the basics
			boolean veteran = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) > 20 * 60 * 30 || soul.deaths > 0
					|| soul.guardiansSlain > 0 || soul.visited.size() > 1;
			if (veteran) {
				for (Lesson l : LESSONS) {
					if (!l.feature()) {
						soul.lessons.add(l.id());
					}
				}
			}
			state.setDirty();
		}
	}

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 7 || !HellConfig.get().lessons) {
			return;
		}
		HellState state = HellState.get(server);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			HellState.Soul soul = state.existing(p.getUUID());
			if (soul == null || soul.lessonsOff || p.isSpectator() || !HellWorldgen.isInferno(server.overworld())) {
				continue;
			}
			soul.playSeconds++;
			LinkedHashSet<String> queue = QUEUE.computeIfAbsent(p.getUUID(), k -> new LinkedHashSet<>());
			for (Lesson l : LESSONS) {
				if (!soul.lessons.contains(l.id()) && !queue.contains(l.id())) {
					try {
						if (l.due().test(p, soul)) {
							queue.add(l.id());
						}
					} catch (RuntimeException e) {
						HellcraftMod.LOGGER.warn("Lesson {} failed its check", l.id(), e);
						soul.lessons.add(l.id());
					}
				}
			}
			if (queue.isEmpty()) {
				continue;
			}
			long now = server.overworld().getGameTime();
			if (now - JOINED.getOrDefault(p.getUUID(), now) < 200 || CutsceneManager.watching(p)) {
				continue;
			}
			boolean fighting = inFight(p);
			String next = null;
			for (String id : queue) {
				Lesson l = find(id);
				if (l != null && (!fighting || l.inFight())) {
					next = id;
					break;
				}
			}
			if (next == null) {
				continue;
			}
			Lesson l = find(next);
			long gap = 20L * Math.max(10, HellConfig.get().lessonSeconds);
			if (!l.inFight() && now - LAST.getOrDefault(p.getUUID(), -gap) < gap) {
				continue;
			}
			queue.remove(next);
			teach(p, soul, l);
			LAST.put(p.getUUID(), now);
		}
	}

	private static void teach(ServerPlayer p, HellState.Soul soul, Lesson l) {
		soul.lessons.add(l.id());
		HellState.get(p.level().getServer()).setDirty();
		Dialogue.say(p, "Virgil", ChatFormatting.GOLD, text(l.text()), false);
		p.level().playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 0.9f);
	}

	private static MutableComponent text(Object[] parts) {
		MutableComponent out = Component.empty();
		for (Object part : parts) {
			if (part instanceof Key k) {
				out.append(Component.keybind(k.id()).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
			} else if (part instanceof Supplier<?> supplier) {
				out.append(Component.literal(String.valueOf(supplier.get())).withStyle(ChatFormatting.GRAY));
			} else {
				out.append(Component.literal(String.valueOf(part)).withStyle(ChatFormatting.GRAY));
			}
		}
		return out;
	}

	private static Lesson find(String id) {
		for (Lesson l : LESSONS) {
			if (l.id().equals(id)) {
				return l;
			}
		}
		return null;
	}

	public static void forget(ServerPlayer player) {
		QUEUE.remove(player.getUUID());
		LAST.remove(player.getUUID());
		JOINED.remove(player.getUUID());
	}

	// ------------------------------------------------------------------ what a soul has come to

	private static Zone zone(ServerPlayer p) {
		if (p.level().dimension() != Level.OVERWORLD) {
			return null;
		}
		return InfernoGeometry.zoneAt(p.getX(), p.getZ());
	}

	/** Within sight of the Gate of Hell's wall. */
	private static boolean nearGate(ServerPlayer p) {
		return p.level().dimension() == Level.OVERWORLD
				&& Math.abs(InfernoGeometry.effectiveRadius(p.getX(), p.getZ()) - InfernoGeometry.GATE_WALL_RADIUS) < 40;
	}

	/** A respawn anchor within a few blocks (a Blood Altar, or near enough one). */
	private static boolean nearAltar(ServerPlayer p) {
		BlockPos at = p.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-4, -2, -4), at.offset(4, 2, 4))) {
			if (p.level().getBlockState(pos).is(Blocks.RESPAWN_ANCHOR) && p.level().getBlockState(pos.below()).is(Blocks.CRYING_OBSIDIAN)) {
				return true;
			}
		}
		return false;
	}

	/** In a guardian's arena while it's awake, or in Lucifer's pit during his fight. */
	static boolean inFight(ServerPlayer p) {
		if (!(p.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
			return false;
		}
		if (LuciferManager.fighting() && LuciferManager.inPit(level, p.blockPosition())) {
			return true;
		}
		for (Guardian g : Guardian.values()) {
			if (g.offMap() || !GuardianManager.awake(g)) {
				continue;
			}
			BlockPos lair = g.lairColumn(GuardianManager.awakeIn(g));
			double dx = p.getX() - lair.getX();
			double dz = p.getZ() - lair.getZ();
			if (dx * dx + dz * dz < 32 * 32) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ commands and test

	/** For the smoke test: every lesson has a unique id and some text, and a new soul's first lesson is the Journal. */
	public static String selfTest() {
		List<String> wrong = new ArrayList<>();
		java.util.Set<String> ids = new java.util.HashSet<>();
		for (Lesson l : LESSONS) {
			if (!ids.add(l.id())) {
				wrong.add("two lessons called " + l.id());
			}
			if (text(l.text()).getString().length() < 40) {
				wrong.add(l.id() + " says too little");
			}
		}
		if (!LESSONS.get(0).id().equals("journal")) {
			wrong.add("the first lesson isn't the Journal");
		}
		return wrong.isEmpty() ? "Lessons test OK: " + LESSONS.size() + " lessons" : "Lessons test FAILED: " + String.join(", ", wrong);
	}

	private static void commands(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("lessons")
				.executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					HellState.Soul soul = Hearts.soul(p);
					long learned = LESSONS.stream().filter(l -> soul.lessons.contains(l.id())).count();
					ctx.getSource().sendSuccess(() -> Component.literal("Virgil has taught you " + learned + " of " + LESSONS.size() + " lessons"
							+ (soul.lessonsOff ? " (turned off: /lessons on)." : ". /lessons off stops them; /lessons again repeats the last."))
							.withStyle(ChatFormatting.GOLD), false);
					return 1;
				})
				.then(Commands.literal("off").executes(ctx -> toggle(ctx.getSource(), true)))
				.then(Commands.literal("on").executes(ctx -> toggle(ctx.getSource(), false)))
				.then(Commands.literal("again").executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					HellState.Soul soul = Hearts.soul(p);
					Lesson last = null;
					for (Lesson l : LESSONS) {
						if (soul.lessons.contains(l.id())) {
							last = l;
						}
					}
					if (last == null) {
						ctx.getSource().sendFailure(Component.literal("Virgil hasn't taught you anything yet."));
						return 0;
					}
					Dialogue.say(p, "Virgil", ChatFormatting.GOLD, text(last.text()), false);
					return 1;
				})));
		dispatcher.register(Commands.literal("hellcraft")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("lessons")
						.then(Commands.literal("test").executes(ctx -> {
							String result = selfTest();
							HellcraftMod.LOGGER.info(result);
							ctx.getSource().sendSuccess(() -> Component.literal(result), false);
							return 1;
						}))
						.then(Commands.literal("reset").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
							ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
							HellState.Soul soul = Hearts.soul(p);
							soul.lessons.clear();
							soul.playSeconds = 0;
							soul.lessonsOff = false;
							forget(p);
							JOINED.put(p.getUUID(), p.level().getGameTime());
							HellState.get(p.level().getServer()).setDirty();
							ctx.getSource().sendSuccess(() -> Component.literal("Virgil will teach " + p.getGameProfile().name() + " again from the start."), true);
							return 1;
						})))
						.then(Commands.literal("give").then(Commands.argument("player", EntityArgument.player())
								.then(Commands.argument("lesson", StringArgumentType.word()).executes(ctx -> {
									ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
									Lesson l = find(StringArgumentType.getString(ctx, "lesson"));
									if (l == null) {
										ctx.getSource().sendFailure(Component.literal("No such lesson."));
										return 0;
									}
									teach(p, Hearts.soul(p), l);
									return 1;
								}))))));
	}

	private static int toggle(CommandSourceStack source, boolean off) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = source.getPlayerOrException();
		HellState.Soul soul = Hearts.soul(p);
		soul.lessonsOff = off;
		HellState.get(p.level().getServer()).setDirty();
		source.sendSuccess(() -> Component.literal(off ? "Virgil will keep his lessons to himself. (/lessons on brings them back.)"
				: "Virgil will teach you as you go.").withStyle(ChatFormatting.GOLD), false);
		return 1;
	}
}
