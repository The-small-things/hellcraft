package net.thesmallthings.hellcraft.blood;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;

/**
 * Boss spoils reward skill, not play time. Every fighter is graded at the end of a boss fight on how much
 * of their health the fight took from them (boss hits grow with your hearts, see BossRules, so this is the
 * same test at 10 hearts as at 40):
 * <ul>
 *     <li><b>S</b>: lost less than a quarter of your health, over the whole fight.</li>
 *     <li><b>A</b>: less than three quarters. <b>B</b>: less than one and a half times. <b>C</b>: more.</li>
 *     <li><b>D</b>: you died.</li>
 * </ul>
 * Anyone who dealt less than half a fair share of the damage can't grade above C. Every victory pays
 * fragments by grade, A and S add a treasure roll, and the first A and the first S against each boss
 * are worth a Blood Heart each.
 */
public final class Judgement {
	private Judgement() {
	}

	public enum Grade {
		D(ChatFormatting.DARK_GRAY, 1.0f),
		C(ChatFormatting.GRAY, 1.5f),
		B(ChatFormatting.GREEN, 0.75f),
		A(ChatFormatting.AQUA, 0.25f),
		S(ChatFormatting.GOLD, 0.0f);

		public final ChatFormatting color;
		/** The share of your health you must lose less than to reach the grade above this one. */
		final float nextBelow;

		Grade(ChatFormatting color, float nextBelow) {
			this.color = color;
			this.nextBelow = nextBelow;
		}

		public boolean atLeast(Grade other) {
			return ordinal() >= other.ordinal();
		}
	}

	/** One fighter's record in one boss fight. */
	public static final class Tally {
		/** Damage taken during the fight, in whole healths (1.0 = their full health). */
		public float lost;
		/** Damage dealt to the boss. */
		public float dealt;
		public boolean died;
	}

	public static Grade grade(Tally tally, double bossHealth, int fighters) {
		if (tally.died) {
			return Grade.D;
		}
		Grade grade = tally.lost < 0.25f ? Grade.S : tally.lost < 0.75f ? Grade.A : tally.lost < 1.5f ? Grade.B : Grade.C;
		if (grade.atLeast(Grade.B) && slacked(tally, bossHealth, fighters)) {
			grade = Grade.C;
		}
		return grade;
	}

	private static boolean slacked(Tally tally, double bossHealth, int fighters) {
		return tally.dealt < bossHealth / (2.0 * Math.max(1, fighters));
	}

	/** Blood Fragments a victory pays at this grade. */
	public static int fragments(Grade grade) {
		return switch (grade) {
			case S -> 8;
			case A -> 6;
			case B -> 4;
			case C -> 2;
			case D -> 1;
		};
	}

	/**
	 * Records the grade as this soul's best against the boss. Returns the Blood Hearts it earns: one for the
	 * first A (or better) against this boss, one for the first S.
	 */
	public static int marks(HellState.Soul soul, String boss, Grade grade) {
		int best = soul.bossBest.getOrDefault(boss, -1);
		int hearts = 0;
		if (grade.atLeast(Grade.A) && best < Grade.A.ordinal()) {
			hearts++;
		}
		if (grade == Grade.S && best < Grade.S.ordinal()) {
			hearts++;
		}
		if (grade.ordinal() > best) {
			soul.bossBest.put(boss, grade.ordinal());
		}
		return hearts;
	}

	/** The boss's treasure (enchanted books and more), straight into the player's inventory. */
	public static void treasure(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
				"loot give " + player.getStringUUID() + " loot hellcraft:gameplay/guardian_spoils");
	}

	/** Tells a fighter their grade, why, what it paid, and how to do better. */
	public static void report(ServerPlayer player, String boss, Grade grade, Tally tally, double bossHealth, int fighters,
							  List<String> spoils, String note) {
		int lostPercent = Math.round(tally.lost * 100);
		player.sendSystemMessage(Component.literal("Grade " + grade.name() + " against " + boss).withStyle(grade.color, ChatFormatting.BOLD)
				.append(Component.literal(": you lost " + lostPercent + "% of your health.").withStyle(ChatFormatting.WHITE)));
		player.sendSystemMessage(Component.literal("Spoils: " + String.join(", ", spoils) + ".").withStyle(ChatFormatting.GOLD));
		String advice;
		if (grade == Grade.D) {
			advice = "You died, so your grade is D. Stay alive to the end for a better one.";
		} else if (grade == Grade.C && tally.lost < 1.5f && slacked(tally, bossHealth, fighters)) {
			advice = "You dealt too little damage to grade above C. Fight, don't just dodge.";
		} else if (grade == Grade.S) {
			advice = "A flawless fight.";
		} else {
			Grade next = Grade.values()[grade.ordinal() + 1];
			advice = String.format(Locale.ROOT, "For %s: lose less than %d%% of your health. Dodge the telegraphed attacks.",
					next == Grade.S ? "an S" : next == Grade.A ? "an A" : "a " + next.name(), Math.round(grade.nextBelow * 100));
		}
		player.sendSystemMessage(Component.literal(advice).withStyle(ChatFormatting.GRAY));
		if (!note.isEmpty()) {
			player.sendSystemMessage(Component.literal(note).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
	}

	/** "1 Blood Heart" / "3 Blood Hearts" */
	public static String hearts(int n, String why) {
		return n + (n == 1 ? " Blood Heart" : " Blood Hearts") + (why.isEmpty() ? "" : " (" + why + ")");
	}

	/** Headless self-test for the smoke test: grades and marks. */
	public static String selfTest() {
		Tally clean = new Tally();
		clean.lost = 0.1f;
		clean.dealt = 300;
		Tally hurt = new Tally();
		hurt.lost = 1.0f;
		hurt.dealt = 300;
		Tally idle = new Tally();
		idle.lost = 0.0f;
		idle.dealt = 10;
		Tally dead = new Tally();
		dead.died = true;
		dead.dealt = 300;
		HellState.Soul soul = new HellState.Soul();
		int first = marks(soul, "minos", Grade.S);
		int again = marks(soul, "minos", Grade.S);
		return "Judgement test: " + grade(clean, 300, 1) + grade(hurt, 300, 1) + grade(idle, 300, 2) + grade(dead, 300, 1)
				+ " marks " + first + "+" + again;
	}
}
