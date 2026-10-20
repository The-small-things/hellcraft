package net.thesmallthings.hellcraft.blood;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.PlayerTeam;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.hazard.guardian.Guardian;
import net.thesmallthings.hellcraft.hazard.guardian.GuardianManager;
import net.thesmallthings.hellcraft.net.Dialogue;
import net.thesmallthings.hellcraft.net.MapSync;
import net.thesmallthings.hellcraft.world.Circle;
import net.thesmallthings.hellcraft.world.HellWorldgen;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Minos's judgement (Inferno V: "he girds himself with his tail as many times as grades he wills the soul be thrust
 * down"). Each soul's worst sin is worked out from what it has actually done, from the game's own statistics and a
 * little of Hellcraft's (tamed animals killed, far weaker souls killed): a score per circle, each count divided by
 * what an ordinary pilgrim piles up of it, so that eating and fighting (which everyone does) don't always win. Too
 * little of anything is Limbo.
 * <p>
 * Minos judges a soul the first time it walks into one of his lairs (MinosFight wraps his tail once per circle), and
 * again each time it walks back in, saying so only when the verdict has changed. The verdict is a title, nothing more:
 * it shows after the name in the tab list (judgementTitles), on a page of the Journal, and with /judge.
 */
public final class Sins {
	private Sins() {
	}

	/** The nine verdicts, by the circle a soul is sent to (its depth is how many times the tail goes round). */
	public enum Sin {
		LIMBO(Circle.LIMBO, "Unbaptised", "Limbo", "Once, and no more. Limbo: too little done to be damned for any of it."),
		LUST(Circle.LUST, "Lustful", "Lust", "Twice the tail. Lust. The wind will carry thee."),
		GLUTTONY(Circle.GLUTTONY, "Glutton", "Gluttony", "Three times the tail. Gluttony. Lie down in the rain with the hogs."),
		GREED(Circle.GREED, "Hoarder", "Greed", "Four times the tail. Greed. Go and push thy weights, hoarder."),
		WRATH(Circle.WRATH, "Wrathful", "Wrath", "Five times the tail. Wrath. The Styx is thick with thy kind."),
		HERESY(Circle.HERESY, "Heretic", "Heresy", "Six times the tail. Heresy. A burning tomb is kept for thee."),
		VIOLENCE(Circle.VIOLENCE, "Violent", "Violence", "Seven times the tail. Violence. The river of blood boils for thee."),
		FRAUD(Circle.FRAUD, "Fraud", "Fraud", "Eight times the tail. Fraud. Malebolge will know thee."),
		TREACHERY(Circle.TREACHERY, "Traitor", "Treachery", "NINE TIMES THE TAIL! TREACHERY! THE ICE OF COCYTUS FOR THEE!");

		public final Circle circle;
		/** The title after the name ("Heretic"). */
		public final String title;
		/** The sin itself ("Heresy"). */
		public final String sin;
		/** What Minos says as the last turn of his tail comes home. */
		public final String line;

		Sin(Circle circle, String title, String sin, String line) {
			this.circle = circle;
			this.title = title;
			this.sin = sin;
			this.line = line;
		}

		/** How many times the tail goes round: the circle's number (Limbo 1 .. Treachery 9). */
		public int depth() {
			return circle.depth();
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		@Nullable
		public static Sin byId(String id) {
			for (Sin s : values()) {
				if (s.id().equals(id)) {
					return s;
				}
			}
			return null;
		}
	}

	/** What a soul has done, as Minos counts it. Damage is in tenths of a health point, as the game counts it. */
	public record Tally(int bred, int eaten, int cake, int treasure, int trades, int mobKills, int damageDealt, int enchanted, int brewed,
						int potions, int animalsKilled, int chests, int trappedChests, int playerKills, int villagersKilled, int golemsKilled,
						int tamedKilled, int weakKills) {
	}

	/** Minos's verdict: the sin, how far past an ordinary soul's share of it, and what gave it away. */
	public record Verdict(Sin sin, double score, String fact, String shortFact) {
	}

	// what an ordinary pilgrim piles up of each over a good few hours: a count this size scores 1, and a soul with no
	// score of 1 anywhere is too new to judge (Limbo)
	private static final double LUST_NORM = 30;
	private static final double GLUTTONY_NORM = 300;
	private static final double GREED_NORM = 150;
	private static final double WRATH_NORM = 500;
	private static final double HERESY_NORM = 40;
	private static final double VIOLENCE_NORM = 120;
	private static final double FRAUD_NORM = 600;
	private static final double TREACHERY_NORM = 15;

	/** Each sin's score: its counts over an ordinary soul's share (Limbo's is always 1, the bar the others must pass). */
	public static double[] scores(Tally t) {
		double[] s = new double[Sin.values().length];
		s[Sin.LIMBO.ordinal()] = 1.0;
		s[Sin.LUST.ordinal()] = t.bred() / LUST_NORM;
		s[Sin.GLUTTONY.ordinal()] = (t.eaten() + t.cake()) / GLUTTONY_NORM;
		s[Sin.GREED.ordinal()] = (t.treasure() + t.trades()) / GREED_NORM;
		s[Sin.WRATH.ordinal()] = (hostileKills(t) + t.damageDealt() / 200.0) / WRATH_NORM;
		s[Sin.HERESY.ordinal()] = (t.enchanted() + t.brewed() / 2.0 + t.potions() / 4.0) / HERESY_NORM;
		s[Sin.VIOLENCE.ordinal()] = t.animalsKilled() / VIOLENCE_NORM;
		s[Sin.FRAUD.ordinal()] = (t.chests() + 10.0 * t.trappedChests()) / FRAUD_NORM;
		s[Sin.TREACHERY.ordinal()] = (5.0 * t.playerKills() + 3.0 * (t.villagersKilled() + t.golemsKilled()) + 3.0 * t.tamedKilled()
				+ 5.0 * t.weakKills()) / TREACHERY_NORM;
		return s;
	}

	private static int hostileKills(Tally t) {
		return Math.max(0, t.mobKills() - t.animalsKilled() - t.villagersKilled() - t.golemsKilled() - t.tamedKilled());
	}

	/** The worst sin (on a tie, the deeper circle: Minos is not merciful). */
	public static Verdict judge(Tally t) {
		double[] s = scores(t);
		Sin worst = Sin.LIMBO;
		for (Sin sin : Sin.values()) {
			if (sin != Sin.LIMBO && s[sin.ordinal()] >= s[worst.ordinal()]) {
				worst = sin;
			}
		}
		return new Verdict(worst, s[worst.ordinal()], fact(worst, t), shortFact(worst, t));
	}

	private static String fact(Sin sin, Tally t) {
		return switch (sin) {
			case LIMBO -> "nothing much yet: you're new here, and haven't done enough of anything to be damned for it";
			case LUST -> "you've bred " + count(t.bred(), "animal");
			case GLUTTONY -> "you've eaten " + count(t.eaten(), "thing") + (t.cake() > 0 ? " and " + count(t.cake(), "slice") + " of cake" : "");
			case GREED -> "you've picked up " + t.treasure() + " gold, emeralds and diamonds" + (t.trades() > 0 ? " and traded with villagers "
					+ count(t.trades(), "time") : "");
			case WRATH -> "you've killed " + count(hostileKills(t), "monster") + " and dealt " + (t.damageDealt() / 20) + " hearts of damage";
			case HERESY -> "you've enchanted " + count(t.enchanted(), "item") + (t.brewed() > 0 ? " and brewed at a brewing stand "
					+ count(t.brewed(), "time") : "");
			case VIOLENCE -> "you've killed " + count(t.animalsKilled(), "animal");
			case FRAUD -> "you've opened " + count(t.chests(), "chest") + " and barrels" + (t.trappedChests() > 0 ? ", and sprung "
					+ count(t.trappedChests(), "trapped chest") : "");
			case TREACHERY -> "you've killed " + list(count(t.playerKills(), "player"), count(t.weakKills(), "far weaker soul"),
					count(t.villagersKilled(), "villager"), count(t.golemsKilled(), "iron golem"), count(t.tamedKilled(), "tamed animal"));
		};
	}

	private static String shortFact(Sin sin, Tally t) {
		return switch (sin) {
			case LIMBO -> "too little done to be damned for";
			case LUST -> count(t.bred(), "animal") + " bred";
			case GLUTTONY -> count(t.eaten() + t.cake(), "thing") + " eaten";
			case GREED -> (t.treasure() + t.trades()) + " gold, gems and trades";
			case WRATH -> count(hostileKills(t), "monster") + " killed";
			case HERESY -> count(t.enchanted(), "item") + " enchanted";
			case VIOLENCE -> count(t.animalsKilled(), "animal") + " killed";
			case FRAUD -> count(t.chests(), "chest") + " opened";
			case TREACHERY -> list(count(t.playerKills(), "player"), count(t.villagersKilled() + t.golemsKilled(), "villager or golem"),
					count(t.tamedKilled(), "tamed animal")) + " killed";
		};
	}

	private static String count(int n, String thing) {
		if (n <= 0) {
			return "";
		}
		return n + " " + thing + (n == 1 ? "" : "s");
	}

	/** "a, b and c", leaving out the empty ones. */
	private static String list(String... parts) {
		List<String> kept = new ArrayList<>();
		for (String p : parts) {
			if (!p.isEmpty()) {
				kept.add(p);
			}
		}
		if (kept.isEmpty()) {
			return "no one";
		}
		if (kept.size() == 1) {
			return kept.get(0);
		}
		return String.join(", ", kept.subList(0, kept.size() - 1)) + " and " + kept.get(kept.size() - 1);
	}

	// ------------------------------------------------------------------ reading a soul

	private static List<Item> foods;
	private static List<EntityType<?>> creatures;

	private static synchronized List<Item> foods() {
		if (foods == null) {
			foods = BuiltInRegistries.ITEM.stream().filter(i -> i.components().has(DataComponents.FOOD)).toList();
		}
		return foods;
	}

	private static synchronized List<EntityType<?>> creatures() {
		if (creatures == null) {
			creatures = BuiltInRegistries.ENTITY_TYPE.stream().filter(e -> e.getCategory() == MobCategory.CREATURE).toList();
		}
		return creatures;
	}

	/** What this soul has done, from the game's statistics and Hellcraft's own counts. */
	public static Tally tally(ServerPlayer p) {
		ServerStatsCounter st = p.getStats();
		HellState.Soul soul = Hearts.soul(p);
		int eaten = 0;
		for (Item food : foods()) {
			eaten += st.getValue(Stats.ITEM_USED.get(food));
		}
		int treasure = picked(st, Items.GOLD_INGOT, 1) + picked(st, Items.RAW_GOLD, 1) + picked(st, Items.GOLD_NUGGET, 1) / 9
				+ picked(st, Items.GOLD_BLOCK, 9) + picked(st, Items.RAW_GOLD_BLOCK, 9) + picked(st, Items.EMERALD, 1)
				+ picked(st, Items.EMERALD_BLOCK, 9) + picked(st, Items.DIAMOND, 2) + picked(st, Items.DIAMOND_BLOCK, 18);
		int animals = 0;
		for (EntityType<?> type : creatures()) {
			animals += st.getValue(Stats.ENTITY_KILLED.get(type));
		}
		int villagers = st.getValue(Stats.ENTITY_KILLED.get(EntityTypes.VILLAGER)) + st.getValue(Stats.ENTITY_KILLED.get(EntityTypes.WANDERING_TRADER));
		int potions = st.getValue(Stats.ITEM_USED.get(Items.POTION)) + st.getValue(Stats.ITEM_USED.get(Items.SPLASH_POTION))
				+ st.getValue(Stats.ITEM_USED.get(Items.LINGERING_POTION));
		return new Tally(custom(st, Stats.ANIMALS_BRED), eaten, custom(st, Stats.EAT_CAKE_SLICE), treasure, custom(st, Stats.TRADED_WITH_VILLAGER),
				custom(st, Stats.MOB_KILLS), custom(st, Stats.DAMAGE_DEALT), custom(st, Stats.ENCHANT_ITEM), custom(st, Stats.INTERACT_WITH_BREWINGSTAND),
				potions, animals, custom(st, Stats.OPEN_CHEST) + custom(st, Stats.OPEN_BARREL), custom(st, Stats.TRIGGER_TRAPPED_CHEST),
				custom(st, Stats.PLAYER_KILLS), villagers, st.getValue(Stats.ENTITY_KILLED.get(EntityTypes.IRON_GOLEM)), soul.tamedKilled, soul.weakKills);
	}

	private static int custom(ServerStatsCounter st, net.minecraft.resources.Identifier stat) {
		return st.getValue(Stats.CUSTOM.get(stat));
	}

	private static int picked(ServerStatsCounter st, Item item, int worth) {
		return st.getValue(Stats.ITEM_PICKED_UP.get(item)) * worth;
	}

	/** Minos judges this soul afresh and remembers the verdict; the previous one (or null) is in the result. */
	public static Judged rejudge(ServerPlayer p) {
		Verdict v = judge(tally(p));
		HellState.Soul soul = Hearts.soul(p);
		Sin before = Sin.byId(soul.judged);
		if (!v.sin().id().equals(soul.judged) || !v.shortFact().equals(soul.judgedFact)) {
			soul.judged = v.sin().id();
			soul.judgedFact = v.shortFact();
			HellState.get(p.level().getServer()).setDirty();
		}
		if (before != v.sin()) {
			HellcraftMod.LOGGER.info("Minos judges {}: {} ({})", p.getGameProfile().name(), v.sin().sin, v.shortFact());
		}
		return new Judged(v, before);
	}

	/** A fresh verdict and the one before it (null: never judged). */
	public record Judged(Verdict verdict, @Nullable Sin before) {
		public boolean first() {
			return before == null;
		}

		public boolean changed() {
			return before != verdict.sin();
		}
	}

	/** What Minos says to a soul as he pronounces (in his voice), and the plain line after it. */
	public static void pronounce(ServerPlayer p, Judged j, boolean present) {
		Verdict v = j.verdict();
		String line = j.first() || present ? v.sin().line
				: "Thou art changed, soul. " + v.sin().line;
		Dialogue.say(p, Guardian.MINOS.title, Guardian.MINOS.color, line);
		String prefix = present ? "" : "Minos's voice reaches you from his coils. ";
		Dialogue.notice(p, Component.literal(prefix + "Your worst sin, by what you've done: " + v.fact() + ". Minos's judgement is a title, nothing more"
				+ (HellConfig.get().judgementTitles ? " (it shows after your name in the player list)" : "") + ". /judge shows it any time.")
				.withStyle(ChatFormatting.GRAY));
		HellState.get(p.level().getServer()).setDirty();
		MapSync.sendJournal(p);
		refreshTab(p);
	}

	// ------------------------------------------------------------------ the lairs, the tab list, the Journal

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register(Sins::afterDeath);
		ServerTickEvents.END_SERVER_TICK.register(Sins::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			IN_LAIR.remove(handler.getPlayer().getUUID());
			SHOWN.remove(handler.getPlayer().getUUID());
		});
	}

	/** Treachery's own counts: a tamed animal killed by someone, and a far weaker soul killed (Cain's Mark). */
	private static void afterDeath(LivingEntity dead, net.minecraft.world.damagesource.DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer killer) || killer == dead) {
			return;
		}
		HellState.Soul soul = Hearts.soul(killer);
		if (dead instanceof OwnableEntity owned && owned.getOwnerReference() != null && !(dead instanceof ServerPlayer)) {
			soul.tamedKilled++;
		} else if (dead instanceof ServerPlayer && soul.cainUntil > killer.level().getServer().overworld().getGameTime()) {
			// Fairness has just marked them for it (its handler runs first), or they kill again while marked
			soul.weakKills++;
		} else {
			return;
		}
		HellState.get(killer.level().getServer()).setDirty();
	}

	/** player -> the Minos lair they stand in (-1: none), for "each time you walk in". */
	private static final Map<UUID, Integer> IN_LAIR = new HashMap<>();
	/** player -> the tab-list name last sent for them. */
	private static final Map<UUID, String> SHOWN = new HashMap<>();
	private static final double LAIR = 16.0;

	private static void tick(MinecraftServer server) {
		int t = server.getTickCount();
		if (t % 40 == 7) {
			refreshTabs(server);
		}
		if (t % 20 != 3) {
			return;
		}
		ServerLevel level = server.overworld();
		if (!HellWorldgen.isInferno(level)) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator()) {
				continue;
			}
			int lair = minosLairAt(p.getX(), p.getZ());
			Integer was = IN_LAIR.put(p.getUUID(), lair);
			if (lair < 0 || (was != null && was == lair)) {
				continue;
			}
			// walked in: when Minos is up (or will wake for them this second), his fight judges them, tail and all;
			// otherwise his voice carries from the coils, and only to say the verdict has changed
			boolean wakes = HellConfig.get().guardians && !GuardianManager.resting(level, Guardian.MINOS) && !GuardianManager.awake(Guardian.MINOS)
					&& !p.isCreative();
			if (GuardianManager.awakeIn(Guardian.MINOS) == lair || wakes) {
				continue;
			}
			Judged j = rejudge(p);
			if (j.changed()) {
				pronounce(p, j, false);
			}
		}
	}

	/** Which of Minos's lairs this spot is in, or -1. */
	public static int minosLairAt(double x, double z) {
		for (int k = 0; k < Guardian.MINOS.lairs(); k++) {
			BlockPos c = Guardian.MINOS.lairColumn(k);
			double dx = x - (c.getX() + 0.5);
			double dz = z - (c.getZ() + 0.5);
			if (dx * dx + dz * dz < LAIR * LAIR) {
				return k;
			}
		}
		return -1;
	}

	/** The title after a soul's name in the tab list (grey), or null to leave the name as the game draws it. */
	@Nullable
	public static Component tabName(ServerPlayer p) {
		if (!HellConfig.get().judgementTitles) {
			return null;
		}
		HellState.Soul soul = HellState.get(p.level().getServer()).existing(p.getUUID());
		Sin sin = soul == null ? null : Sin.byId(soul.judged);
		if (sin == null) {
			return null;
		}
		// as the game would draw it (a team's colour, prefix and suffix: "(new)"), then the title
		MutableComponent name = PlayerTeam.formatNameForTeam(p.getTeam(), Component.literal(p.getGameProfile().name())).copy();
		return name.append(Component.literal(" " + sin.title).withStyle(ChatFormatting.GRAY));
	}

	/** Tells everyone's tab list about this soul's title (when it changed, or its team did). */
	public static void refreshTab(ServerPlayer p) {
		Component name = tabName(p);
		String shown = name == null ? "" : name.getString();
		if (!shown.equals(SHOWN.get(p.getUUID()))) {
			SHOWN.put(p.getUUID(), shown);
			p.level().getServer().getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, p));
		}
	}

	private static void refreshTabs(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			refreshTab(p);
		}
	}

	/** The Journal's page for Minos's judgement, put in after the Guide's first page. */
	public static List<Guide.Page> withJudgement(ServerPlayer p, List<Guide.Page> pages) {
		List<Guide.Page> out = new ArrayList<>(pages);
		out.add(Math.min(1, out.size()), page(p));
		return out;
	}

	private static Guide.Page page(ServerPlayer p) {
		HellState.Soul soul = Hearts.soul(p);
		Sin sin = Sin.byId(soul.judged);
		List<String> lines = new ArrayList<>();
		if (sin == null) {
			lines.add("Minos has not judged you yet.");
			lines.add("");
			lines.add("He waits in his lairs in Circle II (Lust). Walk into one and he wraps his tail round himself once for every "
					+ "circle your worst sin would send you down, and names it.");
		} else {
			lines.add("Minos judged you: " + sin.title + ", " + soul.judgedFact + ".");
			lines.add("");
			lines.add("He wrapped his tail " + (sin.depth() == 1 ? "once" : sin.depth() + " times") + ": Circle " + roman(sin.depth()) + ", " + sin.sin + ".");
		}
		lines.add("");
		lines.add("Your worst sin comes from what you've done: animals bred (Lust), food eaten (Gluttony), gold and gems picked up and "
				+ "trades (Greed), monsters killed (Wrath), items enchanted and potions brewed (Heresy), animals killed (Violence), chests "
				+ "opened (Fraud), players, villagers, golems and pets killed (Treachery). Too little of anything is Limbo.");
		lines.add("");
		lines.add("It changes as you play: he judges you again each time you walk into his lair, and /judge shows it any time.");
		lines.add("The judgement is a title, nothing more.");
		return new Guide.Page("MINOS'S JUDGEMENT", lines);
	}

	private static String roman(int n) {
		return new String[]{"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"}[Math.max(0, Math.min(9, n))];
	}

	// ------------------------------------------------------------------ commands

	/** /judge for everyone (your own judgement, read afresh); /hellcraft judge [player | test] for operators. */
	public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("judge").executes(ctx -> show(ctx.getSource(), ctx.getSource().getPlayerOrException())));
		dispatcher.register(Commands.literal("hellcraft")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("judge")
						.executes(ctx -> show(ctx.getSource(), ctx.getSource().getPlayerOrException()))
						.then(Commands.literal("test").executes(ctx -> {
							String result = selfTest();
							ctx.getSource().sendSuccess(() -> Component.literal(result), false);
							return result.contains("Judgement test OK") ? 1 : 0;
						}))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> show(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))));
	}

	private static int show(CommandSourceStack source, ServerPlayer p) {
		Judged j = rejudge(p);
		Verdict v = j.verdict();
		boolean self = source.getEntity() == p;
		String who = self ? "You" : p.getGameProfile().name();
		source.sendSuccess(() -> Component.literal((self ? "Minos would judge you: " : "Minos would judge " + who + ": ") + v.sin().title + " (Circle "
				+ roman(v.sin().depth()) + ", " + v.sin().sin + "), " + v.shortFact() + ".").withStyle(ChatFormatting.DARK_PURPLE), false);
		source.sendSuccess(() -> Component.literal("Worst sin, by what " + (self ? "you've" : "they've") + " done: " + v.fact().replace("you've", self ? "you've" : "they've")
				+ ". It's a title, nothing more, and it changes as " + (self ? "you play" : "they play") + ".").withStyle(ChatFormatting.GRAY), false);
		if (j.changed() && !j.first()) {
			MapSync.sendJournal(p);
		}
		refreshTab(p);
		return v.sin().depth();
	}

	/** For the smoke test: verdicts for made-up souls, one of each kind. */
	public static String selfTest() {
		Map<String, Sin> want = new java.util.LinkedHashMap<>();
		Map<String, Tally> souls = new HashMap<>();
		souls.put("newcomer", new Fake().eaten(40).hostile(10).tally());
		want.put("newcomer", Sin.LIMBO);
		souls.put("breeder", ordinary().bred(60).tally());
		want.put("breeder", Sin.LUST);
		souls.put("eater", ordinary().eaten(600).tally());
		want.put("eater", Sin.GLUTTONY);
		souls.put("miner", ordinary().treasure(400).trades(20).tally());
		want.put("miner", Sin.GREED);
		souls.put("fighter", ordinary().hostile(900).damageHearts(2000).tally());
		want.put("fighter", Sin.WRATH);
		souls.put("enchanter", ordinary().enchanted(140).brewed(12).tally());
		want.put("enchanter", Sin.HERESY);
		souls.put("butcher", ordinary().animals(380).tally());
		want.put("butcher", Sin.VIOLENCE);
		souls.put("looter", ordinary().chests(1300).trapped(4).tally());
		want.put("looter", Sin.FRAUD);
		souls.put("traitor", ordinary().players(3).villagers(2).tamed(1).tally());
		want.put("traitor", Sin.TREACHERY);
		StringBuilder out = new StringBuilder("Judgement test:");
		boolean ok = true;
		for (Map.Entry<String, Sin> e : want.entrySet()) {
			Verdict v = judge(souls.get(e.getKey()));
			out.append(' ').append(e.getKey()).append('=').append(v.sin().name());
			ok &= v.sin() == e.getValue();
		}
		Verdict heretic = judge(souls.get("enchanter"));
		out.append("; tail turns for heresy=").append(heretic.sin().depth()).append(" (\"").append(heretic.shortFact()).append("\")");
		ok &= heretic.sin().depth() == 6 && heretic.shortFact().equals("140 items enchanted");
		out.append(ok ? "; Judgement test OK" : "; Judgement test FAILED");
		return out.toString();
	}

	/** A pilgrim a few hours in, with a little of everything and too little of any one thing to be judged for it. */
	private static Fake ordinary() {
		return new Fake().eaten(200).hostile(150).damageHearts(300).chests(200).animals(40).treasure(50);
	}

	/** A made-up soul for the self-test. */
	private static final class Fake {
		private int bred, eaten, treasure, trades, hostile, damageHearts, enchanted, brewed, animals, chests, trapped, players, villagers, tamed;

		Fake bred(int n) {
			bred = n;
			return this;
		}

		Fake eaten(int n) {
			eaten = n;
			return this;
		}

		Fake treasure(int n) {
			treasure = n;
			return this;
		}

		Fake trades(int n) {
			trades = n;
			return this;
		}

		Fake hostile(int n) {
			hostile = n;
			return this;
		}

		Fake damageHearts(int n) {
			damageHearts = n;
			return this;
		}

		Fake enchanted(int n) {
			enchanted = n;
			return this;
		}

		Fake brewed(int n) {
			brewed = n;
			return this;
		}

		Fake animals(int n) {
			animals = n;
			return this;
		}

		Fake chests(int n) {
			chests = n;
			return this;
		}

		Fake trapped(int n) {
			trapped = n;
			return this;
		}

		Fake players(int n) {
			players = n;
			return this;
		}

		Fake villagers(int n) {
			villagers = n;
			return this;
		}

		Fake tamed(int n) {
			tamed = n;
			return this;
		}

		Tally tally() {
			// the game counts every mob killed in mob_kills, and damage in tenths of a health point (a heart is 20)
			return new Tally(bred, eaten, 0, treasure, trades, hostile + animals + villagers + tamed, damageHearts * 20, enchanted, brewed, 0,
					animals, chests, trapped, players, villagers, 0, tamed, 0);
		}
	}
}
