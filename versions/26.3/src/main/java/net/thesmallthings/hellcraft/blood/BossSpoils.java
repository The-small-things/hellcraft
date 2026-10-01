package net.thesmallthings.hellcraft.blood;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * Boss spoils are personal and can't be farmed: each soul takes a boss's spoils once, then again only
 * after a cooldown. The first victory over each boss always pays. (The guardians, Vulcan, the Wither,
 * the Warden, the Ender Dragon and Lucifer all go through here.)
 */
public final class BossSpoils {
	private BossSpoils() {
	}

	public enum Claim {
		FIRST, AGAIN, TOO_SOON
	}

	/** Claims a boss's spoils for this player (recording it unless it is too soon). */
	public static Claim claim(ServerPlayer player, String boss, int cooldownMinutes) {
		Claim claim = peek(player, boss, cooldownMinutes);
		if (claim != Claim.TOO_SOON) {
			record(player, boss);
		}
		return claim;
	}

	/** Whether this boss would pay this player now, without taking the spoils. */
	public static Claim peek(ServerPlayer player, String boss, int cooldownMinutes) {
		return check(Hearts.soul(player), boss, cooldownMinutes, player.level().getServer().overworld().getGameTime());
	}

	/** Takes the spoils: the cooldown starts now. */
	public static void record(ServerPlayer player, String boss) {
		Hearts.soul(player).guardianSpoils.put(boss, player.level().getServer().overworld().getGameTime());
		HellState.get(player.level().getServer()).setDirty();
	}

	/** Minutes until this boss pays this player again (0 if it would now). */
	public static long minutesLeft(ServerPlayer player, String boss, int cooldownMinutes) {
		return minutesLeft(Hearts.soul(player), boss, cooldownMinutes, player.level().getServer().overworld().getGameTime());
	}

	static Claim check(HellState.Soul soul, String boss, int cooldownMinutes, long now) {
		Long last = soul.guardianSpoils.get(boss);
		if (last == null) {
			return Claim.FIRST;
		}
		return now - last >= cooldownMinutes * 60L * 20L ? Claim.AGAIN : Claim.TOO_SOON;
	}

	/** Minutes until this boss pays this soul again (0 if it would now). */
	public static long minutesLeft(HellState.Soul soul, String boss, int cooldownMinutes, long now) {
		Long last = soul.guardianSpoils.get(boss);
		if (last == null) {
			return 0;
		}
		return Math.max(0, (cooldownMinutes * 60L * 20L - (now - last) + 1199) / 1200);
	}

	/** Tells a player the boss has nothing more for them yet. */
	public static void tooSoon(ServerPlayer player, String title, String boss, int cooldownMinutes) {
		long now = player.level().getServer().overworld().getGameTime();
		long minutes = Math.max(1, minutesLeft(Hearts.soul(player), boss, cooldownMinutes, now));
		player.sendSystemMessage(Component.literal(title + "'s spoils are on cooldown for you: " + minutes + " min left.")
				.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}

	/** Every boss this soul is waiting on, for /hellcraft inspect. */
	public static String describe(HellState.Soul soul, long now, Map<String, Integer> cooldowns) {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, Long> e : soul.guardianSpoils.entrySet()) {
			int cd = cooldowns.getOrDefault(e.getKey(), 180);
			long left = minutesLeft(soul, e.getKey(), cd, now);
			sb.append(sb.isEmpty() ? "" : ", ").append(e.getKey()).append(left > 0 ? " (" + left + " min)" : " (ready)");
		}
		return sb.isEmpty() ? "none yet" : sb.toString();
	}

	/** Headless self-test: first claim pays, the next is too soon, after the cooldown it pays again. */
	public static String selfTest() {
		HellState.Soul soul = new HellState.Soul();
		Claim a = check(soul, "wither", 180, 1000);
		soul.guardianSpoils.put("wither", 1000L);
		Claim b = check(soul, "wither", 180, 1000 + 60 * 60 * 20);
		Claim c = check(soul, "wither", 180, 1000 + 180 * 60 * 20);
		return "Spoils test: " + a + " " + b + " " + c;
	}
}
