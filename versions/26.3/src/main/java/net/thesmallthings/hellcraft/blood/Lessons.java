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
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.hazard.guardian.Guardian;
import net.thesmallthings.hellcraft.hazard.guardian.GuardianManager;
import net.thesmallthings.hellcraft.hazard.lucifer.LuciferManager;
import net.thesmallthings.hellcraft.net.Dialogue;
import net.thesmallthings.hellcraft.sound.HellSounds;
import net.thesmallthings.hellcraft.world.HellWorldgen;

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
 * guardian's lair ahead). Lessons queue and come no closer together than {@code lessonSeconds}, and like every tip
 * (Dialogue.mayTip) never during a cutscene or mid-fight.
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
	 * A lesson: its id, whether it's about a newer feature (so old souls get it too), when it's due (soul, player, online
	 * seconds) and its text.
	 */
	private record Lesson(String id, boolean feature, BiPredicate<ServerPlayer, HellState.Soul> due, Object[] text) {
	}

	private static final List<Lesson> LESSONS = new ArrayList<>();
	/** Lessons waiting to be given, by soul, in the order they came due. */
	private static final Map<UUID, LinkedHashSet<String>> QUEUE = new HashMap<>();
	/** When each soul was last taught (game time), and when it joined. */
	private static final Map<UUID, Long> LAST = new HashMap<>();
	private static final Map<UUID, Long> JOINED = new HashMap<>();

	private static void lesson(String id, BiPredicate<ServerPlayer, HellState.Soul> due, Object... text) {
		LESSONS.add(new Lesson(id, false, due, text));
	}

	static {
		Key journal = new Key("key.hellcraft.journal");
		Key map = new Key("key.hellcraft.map");
		// Short and only what the game doesn't show you itself: no lesson describes a place you are looking at.
		// with play time: how to find your way
		lesson("journal", (p, s) -> s.playSeconds >= 3,
				"Press ", journal, " for your Journal: where you are, where to go next, and how everything down here works.");
		lesson("compass", (p, s) -> s.playSeconds >= 120,
				"The compass up top: the red arrow points to the nearest way down, the gold diamond to the nearest Virgil's Rest.");
		lesson("map", (p, s) -> s.playSeconds >= 300,
				"Press ", map, " for the Map of Hell. Follow one of the four Pilgrim Roads and you can't get lost.");
		lesson("vigil", (p, s) -> s.playSeconds >= 540 && p.getInventory().contains(BloodItems::isVigil),
				"Right-click a Vigil Candle to light it: when you next die, you come back there instead of at the rim.");
		lesson("beds", (p, s) -> p.getInventory().contains(ItemTags.BEDS),
				"Beds don't work in Hell. To set where you come back, light a Vigil Candle or use a Blood Altar.");
		lesson("waymarks", (p, s) -> s.playSeconds >= 1500,
				"Right-click the Map of Hell (", map, ") to set a waymark: the compass will point to it.");
		lesson("supplies", (p, s) -> s.playSeconds >= 900,
				"Stock up before you go deep: villages are in the Dark Wood and Limbo, and loot lies in ruined altars and Rests.");
		// hearts and blood
		lesson("hearts", (p, s) -> s.deaths > 0 || p.getHealth() <= p.getMaxHealth() / 4,
				"Hearts are your lives here: a player who kills you takes one. At none you're a ghost until a friend revives you. "
						+ "(Journal: HEARTS)");
		lesson("fragments", (p, s) -> p.getInventory().contains(BloodItems::isFragment),
				"Blood Fragment: right-click a stack of ",
				(Supplier<String>) () -> String.valueOf(HellConfig.get().fragmentsPerHeart), " to make a Blood Heart. They also craft hell weapons and blood armour.");
		lesson("blood_heart", (p, s) -> p.getInventory().contains(BloodItems::isHeart),
				"Right-click a Blood Heart to gain a heart. /withdraw turns your own hearts into these, to trade or keep safe.");
		lesson("altar", (p, s) -> nearAltar(p),
				"Right-click a Blood Altar to set your respawn, buy a Ward, revive a ghost or travel to Rests you've found.");
		lesson("lifesteal", (p, s) -> p.getStats().getValue(Stats.CUSTOM.get(Stats.PLAYER_KILLS)) > 0,
				"Killing someone with under half your hearts takes nothing and gives you Cain's Mark. (Journal: FAIR PLAY)");
		lesson("prestige", (p, s) -> HellConfig.get().prestige && s.hearts >= HellConfig.get().maxHearts,
				"You're at max hearts: a Blood Altar can Prestige you, for a higher max and a lasting bonus. (Journal: PRESTIGE)");
		// the way down
		lesson("travel", (p, s) -> s.visited.size() >= 2,
				"You've found two Rests: travel between them from any Blood Altar.");
		// bosses: told on the way into a lair, never in the fight
		lesson("boss", (p, s) -> nearLair(p),
				"A guardian's lair is close. In its fight, red marks mean move, white means block, purple means dodge. "
						+ "Your style is ranked for spoils. You never have to fight one to go down.");
		lesson("spoils", (p, s) -> s.guardiansSlain > 0,
				"Your spoils follow your style rank: chain moves, parry, roll and don't get hit. (Journal: STYLE)");
		// newer features: old souls hear of these too
		Key dash = new Key("key.hellcraft.dash");
		LESSONS.add(new Lesson("roll", true, (p, s) -> net.thesmallthings.hellcraft.combat.Combat.on() && s.playSeconds >= 45, new Object[]{
				"Press ", dash, " to roll: blows pass through you mid-roll. It costs stamina, the gold bar over your hunger."}));
		LESSONS.add(new Lesson("swing", true, (p, s) -> net.thesmallthings.hellcraft.combat.Combat.on()
				&& net.thesmallthings.hellcraft.combat.Moveset.of(p.getMainHandItem()) != null && s.playSeconds >= 10, new Object[]{
				"Click again as a swing ends to chain the next, or hold for a heavy blow. (Journal: FIGHTING)"}));
		LESSONS.add(new Lesson("guard", true, (p, s) -> net.thesmallthings.hellcraft.combat.Combat.on()
				&& net.thesmallthings.hellcraft.combat.Guard.guards(p.getMainHandItem()) && s.playSeconds >= 20, new Object[]{
				"Hold right-click to guard. Raise it just as a blow lands to parry: nothing gets through."}));
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
			if (now - JOINED.getOrDefault(p.getUUID(), now) < 200 || !Dialogue.mayTip(p)) {
				continue;
			}
			long gap = 20L * Math.max(10, HellConfig.get().lessonSeconds);
			if (now - LAST.getOrDefault(p.getUUID(), -gap) < gap) {
				continue;
			}
			// the first queued lesson that still fits the moment (one about a lair is left waiting once you've walked off)
			Lesson l = null;
			for (java.util.Iterator<String> it = queue.iterator(); it.hasNext(); ) {
				Lesson candidate = find(it.next());
				if (candidate == null) {
					it.remove();
				} else if (stillDue(p, soul, candidate)) {
					l = candidate;
					it.remove();
					break;
				}
			}
			if (l == null) {
				continue;
			}
			teach(p, soul, l);
			LAST.put(p.getUUID(), now);
		}
	}

	private static boolean stillDue(ServerPlayer p, HellState.Soul soul, Lesson l) {
		try {
			return l.due().test(p, soul);
		} catch (RuntimeException e) {
			return false;
		}
	}

	private static void teach(ServerPlayer p, HellState.Soul soul, Lesson l) {
		soul.lessons.add(l.id());
		HellState.get(p.level().getServer()).setDirty();
		Dialogue.tip(p, "Virgil", ChatFormatting.GOLD, text(l.text()));
		p.level().playSound(null, p.blockPosition(), HellSounds.UI_LESSON.value(), SoundSource.PLAYERS, 0.8f, 0.97f);
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

	/** In a guardian's arena while it's awake (in any realm), or in Lucifer's pit during his fight. */
	public static boolean inFight(ServerPlayer p) {
		if (!(p.level() instanceof ServerLevel level)) {
			return false;
		}
		if (level.dimension() == Level.OVERWORLD && LuciferManager.fighting() && LuciferManager.inPit(level, p.blockPosition())) {
			return true;
		}
		return GuardianManager.fighting(p);
	}

	/** Near one of a sleeping guardian's lairs in the overworld: about to walk into a fight. */
	private static boolean nearLair(ServerPlayer p) {
		if (p.level().dimension() != Level.OVERWORLD) {
			return false;
		}
		for (Guardian g : Guardian.values()) {
			for (int k = 0; k < g.lairs(); k++) {
				BlockPos lair = g.lairColumn(k);
				double dx = p.getX() - lair.getX();
				double dz = p.getZ() - lair.getZ();
				if (dx * dx + dz * dz < 72 * 72) {
					return !GuardianManager.awake(g);
				}
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
					Dialogue.tip(p, "Virgil", ChatFormatting.GOLD, text(last.text()));
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
