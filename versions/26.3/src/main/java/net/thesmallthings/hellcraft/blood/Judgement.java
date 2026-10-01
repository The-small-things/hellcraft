package net.thesmallthings.hellcraft.blood;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.List;

/**
 * Boss spoils reward style, not play time. Every fighter's style is ranked through the fight (StyleMeter)
 * and their spoils follow their average rank: every victory pays fragments by rank, S and better add a
 * treasure roll, and the first S and the first SSS against each boss are worth a Blood Heart each.
 */
public final class Judgement {
	private Judgement() {
	}

	/** Style ranks, lowest first, with the style needed to reach each. */
	public enum Grade {
		D("DAMNED", 0, ChatFormatting.DARK_GRAY, BossEvent.BossBarColor.WHITE, 1),
		C("CRUEL", 30, ChatFormatting.GRAY, BossEvent.BossBarColor.WHITE, 2),
		B("BRUTAL", 70, ChatFormatting.GREEN, BossEvent.BossBarColor.GREEN, 3),
		A("ANARCHIC", 120, ChatFormatting.AQUA, BossEvent.BossBarColor.BLUE, 4),
		S("SAVAGE", 180, ChatFormatting.LIGHT_PURPLE, BossEvent.BossBarColor.PURPLE, 6),
		SS("SINFUL", 250, ChatFormatting.YELLOW, BossEvent.BossBarColor.YELLOW, 8),
		SSS("SATANIC", 330, ChatFormatting.RED, BossEvent.BossBarColor.RED, 10);

		public final String title;
		/** Style points needed for this rank. */
		public final float threshold;
		public final ChatFormatting color;
		public final BossEvent.BossBarColor barColor;
		/** Blood Fragments a victory pays at this rank. */
		public final int fragments;

		Grade(String title, float threshold, ChatFormatting color, BossEvent.BossBarColor barColor, int fragments) {
			this.title = title;
			this.threshold = threshold;
			this.color = color;
			this.barColor = barColor;
			this.fragments = fragments;
		}

		public boolean atLeast(Grade other) {
			return ordinal() >= other.ordinal();
		}

		public static Grade of(float style) {
			Grade grade = D;
			for (Grade g : values()) {
				if (style >= g.threshold) {
					grade = g;
				}
			}
			return grade;
		}

		/** The rank above, or this one at the top. */
		public Grade next() {
			return this == SSS ? SSS : values()[ordinal() + 1];
		}

		/** "an S", "a B" */
		public String withArticle() {
			return (this == A || this == S || this == SS || this == SSS ? "an " : "a ") + name();
		}
	}

	/**
	 * Records the rank as this soul's best against the boss. Returns the Blood Hearts it earns: one for the
	 * first S (or better) against this boss, one for the first SSS.
	 */
	public static int marks(HellState.Soul soul, String boss, Grade grade) {
		int best = soul.bossBest.getOrDefault(boss, -1);
		int hearts = 0;
		if (grade.atLeast(Grade.S) && best < Grade.S.ordinal()) {
			hearts++;
		}
		if (grade == Grade.SSS && best < Grade.SSS.ordinal()) {
			hearts++;
		}
		if (grade.ordinal() > best) {
			soul.bossBest.put(boss, grade.ordinal());
		}
		return hearts;
	}

	/** Why the marks were earned, for the spoils line. */
	public static String marksReason(Grade grade, int marks) {
		return marks >= 2 ? "your first S and first SSS" : grade == Grade.SSS ? "your first SSS" : "your first S";
	}

	/** The boss's treasure (enchanted books and more), straight into the player's inventory. */
	public static void treasure(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
				"loot give " + player.getStringUUID() + " loot hellcraft:gameplay/guardian_spoils");
	}

	/** Tells a fighter their style rank, what it paid, and how to do better. */
	public static void report(ServerPlayer player, String boss, Grade grade, String summary, List<String> spoils, String advice, String note) {
		player.sendSystemMessage(Component.literal("Style " + grade.name() + " (" + grade.title + ") against " + boss)
				.withStyle(grade.color, ChatFormatting.BOLD)
				.append(Component.literal(": " + summary).withStyle(ChatFormatting.WHITE)));
		player.sendSystemMessage(Component.literal("Spoils: " + String.join(", ", spoils) + ".").withStyle(ChatFormatting.GOLD));
		if (!advice.isEmpty()) {
			player.sendSystemMessage(Component.literal(advice).withStyle(ChatFormatting.GRAY));
		}
		if (!note.isEmpty()) {
			player.sendSystemMessage(Component.literal(note).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
	}

	/** "1 Blood Heart" / "3 Blood Hearts" */
	public static String hearts(int n, String why) {
		return n + (n == 1 ? " Blood Heart" : " Blood Hearts") + (why.isEmpty() ? "" : " (" + why + ")");
	}
}
