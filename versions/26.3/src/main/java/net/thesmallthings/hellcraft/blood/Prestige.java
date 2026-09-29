package net.thesmallthings.hellcraft.blood;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodData;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.music.MusicPack;
import net.thesmallthings.hellcraft.util.Journey;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The Seven P's. On Purgatory's gate an angel carves seven P's (peccata, sins) on every brow, and each
 * terrace burns one away. Here a soul whose veins are full can ascend at a Blood Altar: its hearts fall
 * back to the start, one P is burned away, it may hold two more hearts for good, and it keeps that
 * terrace's virtue. The rank shows as a cross before the name.
 */
public final class Prestige {
	private Prestige() {
	}

	public static final int MAX = 7;

	/** The terraces in the order the pilgrim climbs them: the sin purged and the virtue it leaves. */
	public enum Terrace {
		PRIDE("Pride", "Humility", "you fall more lightly (-30% fall damage)", 0x8C5A2B),
		ENVY("Envy", "Kindness", "monsters bleed Blood Fragments more often (+10%)", 0xA8743A),
		WRATH("Wrath", "Meekness", "blows barely move you (+25% knockback resistance)", 0xC0913F),
		SLOTH("Sloth", "Zeal", "you walk faster (+5% speed)", 0xD4AF37),
		GREED("Greed", "Liberality", "fortune favours you (+1 luck: better loot)", 0xE8C75A),
		GLUTTONY("Gluttony", "Temperance", "hunger gnaws more slowly", 0xF4E08A),
		LUST("Lust", "Purity", "the torments of the circles no longer touch you", 0xFFFFFF);

		public final String sin;
		public final String virtue;
		public final String perk;
		public final int colour;

		Terrace(String sin, String virtue, String perk, int colour) {
			this.sin = sin;
			this.virtue = virtue;
			this.perk = perk;
			this.colour = colour;
		}
	}

	private static final String[] NUMERALS = {"", "I", "II", "III", "IV", "V", "VI", "VII"};

	public static String numeral(int prestige) {
		return NUMERALS[Math.max(0, Math.min(MAX, prestige))];
	}

	/** Heart capacity the burned P's add. */
	public static int capBonus(HellState.Soul soul) {
		return Math.max(0, Math.min(MAX, soul.prestige)) * HellConfig.get().prestigeHeartBonus;
	}

	public static boolean has(HellState.Soul soul, Terrace terrace) {
		return HellConfig.get().prestige && soul.prestige > terrace.ordinal();
	}

	public enum Result {
		ASCENDED, NOT_FULL, PURIFIED, DISABLED
	}

	/** The ascent itself, on the saved soul only (so it can be tested with nobody online). */
	public static Result ascend(HellState.Soul soul) {
		HellConfig config = HellConfig.get();
		if (!config.prestige) {
			return Result.DISABLED;
		}
		if (soul.prestige >= MAX) {
			return Result.PURIFIED;
		}
		if (soul.hearts < Hearts.cap(soul)) {
			return Result.NOT_FULL;
		}
		soul.prestige++;
		soul.hearts = Math.max(1, Math.min(config.prestigeResetHearts, Hearts.cap(soul)));
		return Result.ASCENDED;
	}

	/** Clicked at an altar: ascend, or say why not. */
	public static void ascend(ServerPlayer player, ServerLevel level, BlockPos altar) {
		HellState.Soul soul = Hearts.soul(player);
		Result result = ascend(soul);
		switch (result) {
			case DISABLED -> player.sendSystemMessage(Component.literal("The terraces are closed on this server.").withStyle(ChatFormatting.GRAY));
			case PURIFIED -> player.sendSystemMessage(Component.literal("No P remains on your brow. You are pure, and ready to rise to the stars.")
					.withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC));
			case NOT_FULL -> player.sendSystemMessage(Component.literal("Only a soul whose veins are full may climb the next terrace ("
					+ soul.hearts + " / " + Hearts.cap(soul) + " hearts).").withStyle(ChatFormatting.RED));
			case ASCENDED -> {
				HellState.get(level.getServer()).setDirty();
				Hearts.apply(player);
				player.setHealth(player.getMaxHealth());
				celebrate(player, level, altar, soul);
			}
		}
	}

	private static void celebrate(ServerPlayer player, ServerLevel level, BlockPos altar, HellState.Soul soul) {
		Terrace terrace = Terrace.values()[soul.prestige - 1];
		int left = MAX - soul.prestige;
		player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 30));
		player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("A P is burned from your brow")
				.withStyle(s -> s.withColor(terrace.colour))));
		player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(
				(left == 0 ? "None remain" : left + " remain") + " · " + terrace.virtue).withStyle(ChatFormatting.GRAY)));
		level.playSound(null, altar, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2f, 1.3f);
		level.playSound(null, altar, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 2.0f, 0.8f);
		for (int i = 0; i < 48; i++) {
			// a rising spiral, like the angel's wing sweeping the brow
			double a = i * 0.45;
			double r = 1.2 - i * 0.018;
			level.sendParticles(ParticleTypes.END_ROD, player.getX() + Math.cos(a) * r, player.getY() + i * 0.06, player.getZ() + Math.sin(a) * r,
					1, 0, 0, 0, 0);
		}
		level.sendParticles(ParticleTypes.WAX_OFF, player.getX(), player.getY() + 1.9, player.getZ(), 30, 0.3, 0.2, 0.3, 0.1);
		player.sendSystemMessage(Component.literal("The terrace of " + terrace.sin + " is behind you. " + terrace.virtue + ": " + terrace.perk + ".")
				.withStyle(s -> s.withColor(terrace.colour)));
		player.sendSystemMessage(Component.literal("Your veins can now hold " + Hearts.cap(soul) + " hearts. Fill them again to climb on.")
				.withStyle(ChatFormatting.GRAY));
		level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(player.getGameProfile().name() + " has climbed the terrace of "
				+ terrace.sin + " (" + numeral(soul.prestige) + ")").withStyle(s -> s.withColor(terrace.colour)), false);
		HellcraftMod.LOGGER.info("{} ascended: P {} ({})", player.getGameProfile().name(), soul.prestige, terrace.virtue);
		Journey.award(player, "journey/first_p");
		if (soul.prestige >= MAX) {
			Journey.award(player, "journey/purified");
		}
		MusicPack.sendRank(player, soul.prestige);
	}

	// ---- the virtues --------------------------------------------------------------------------

	private record Perk(Terrace terrace, Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {
		Identifier id() {
			return HellcraftMod.id("virtue_" + terrace.virtue.toLowerCase(Locale.ROOT));
		}
	}

	private static final Perk[] PERKS = {
			new Perk(Terrace.PRIDE, Attributes.FALL_DAMAGE_MULTIPLIER, -0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
			new Perk(Terrace.WRATH, Attributes.KNOCKBACK_RESISTANCE, 0.25, AttributeModifier.Operation.ADD_VALUE),
			new Perk(Terrace.SLOTH, Attributes.MOVEMENT_SPEED, 0.05, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
			new Perk(Terrace.GREED, Attributes.LUCK, 1.0, AttributeModifier.Operation.ADD_VALUE),
	};

	/** Puts this soul's virtues on its body (called whenever its hearts are applied). */
	public static void applyVirtues(ServerPlayer player, HellState.Soul soul) {
		for (Perk perk : PERKS) {
			AttributeInstance instance = player.getAttribute(perk.attribute());
			if (instance == null) {
				continue;
			}
			instance.removeModifier(perk.id());
			if (has(soul, perk.terrace())) {
				instance.addPermanentModifier(new AttributeModifier(perk.id(), perk.amount(), perk.operation()));
			}
		}
		updateTeam(player.level().getServer(), player.getGameProfile().name(), soul);
	}

	/** Temperance: every 30 s a little of what hunger took is given back. */
	public static void tick(MinecraftServer server) {
		if (!HellConfig.get().prestige || server.getTickCount() % 600 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			HellState.Soul soul = HellState.get(server).existing(player.getUUID());
			if (soul == null || !has(soul, Terrace.GLUTTONY)) {
				continue;
			}
			FoodData food = player.getFoodData();
			if (food.getSaturationLevel() < food.getFoodLevel()) {
				food.setSaturation(Math.min(food.getFoodLevel(), food.getSaturationLevel() + 1.5f));
			} else if (food.getFoodLevel() < 20) {
				food.setFoodLevel(food.getFoodLevel() + 1);
			}
		}
	}

	// ---- the cross before the name --------------------------------------------------------------

	/** Player name -> the rank their team shows (so the team command only runs when it changes). */
	private static final Map<String, Integer> SHOWN = new HashMap<>();

	private static String team(int prestige) {
		return "hellcraft_p" + prestige;
	}

	/** Creates the seven teams (tab list and name tag prefixes). */
	public static void setUp(MinecraftServer server) {
		SHOWN.clear();
		for (int p = 1; p <= MAX; p++) {
			run(server, "team add " + team(p));
			String colour = String.format(Locale.ROOT, "#%06X", Terrace.values()[p - 1].colour);
			run(server, "team modify " + team(p) + " prefix {text:\"✝" + numeral(p) + " \",color:\"" + colour + "\"" + (p == MAX ? ",bold:true" : "") + "}");
		}
	}

	private static void updateTeam(MinecraftServer server, String name, HellState.Soul soul) {
		if (name.isEmpty() || !name.matches("[A-Za-z0-9_]{1,16}")) {
			return;
		}
		int rank = HellConfig.get().prestige ? Math.min(MAX, soul.prestige) : 0;
		Integer shown = SHOWN.get(name);
		if (shown != null && shown == rank) {
			return;
		}
		SHOWN.put(name, rank);
		if (rank == 0) {
			run(server, "team leave " + name);
			return;
		}
		run(server, "team join " + team(rank) + " " + name);
	}

	private static void run(MinecraftServer server, String command) {
		CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
		server.getCommands().performPrefixedCommand(source, command);
	}

	// ---- tools ------------------------------------------------------------------------------------

	/** A description for /hellcraft prestige and the guide. */
	public static String describe(HellState.Soul soul) {
		StringBuilder sb = new StringBuilder(soul.name + ": " + soul.prestige + " of 7 P's burned, cap " + Hearts.cap(soul) + " hearts");
		for (Terrace t : Terrace.values()) {
			if (soul.prestige > t.ordinal()) {
				sb.append("; ").append(t.virtue);
			}
		}
		return sb.toString();
	}

	/** Headless self-test: a throwaway soul climbs all seven terraces, and the eighth is refused. */
	public static String selfTest() {
		HellState.Soul soul = new HellState.Soul();
		soul.name = "test";
		int startCap = Hearts.cap(soul);
		int burned = 0;
		for (int i = 0; i < MAX; i++) {
			Result early = ascend(soul);
			if (early != Result.NOT_FULL) {
				return "FAILED: a soul with " + soul.hearts + " hearts was not refused (" + early + ")";
			}
			soul.hearts = Hearts.cap(soul);
			if (ascend(soul) == Result.ASCENDED) {
				burned++;
			}
		}
		int left = soul.hearts;
		soul.hearts = Hearts.cap(soul);
		Result eighth = ascend(soul);
		return String.format(Locale.ROOT, "Prestige test: %d P's burned, cap %d -> %d, hearts after the last ascent %d, the eighth %s",
				burned, startCap, Hearts.cap(soul), left, eighth == Result.PURIFIED ? "refused" : "ALLOWED (" + eighth + ")");
	}
}
