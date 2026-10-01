package net.thesmallthings.hellcraft.hazard;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.thesmallthings.hellcraft.blood.Judgement.Grade;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * One fighter's style in one boss fight, after ULTRAKILL: a meter shown in their own boss bar that fills
 * as they fight well and drains when they hold back or get hit. Spoils follow the average style over the
 * fight (Judgement), so they reward fighting well, not grinding.
 * <ul>
 *     <li>Every hit on the boss scores, more for a strong hit than a spammed one.</li>
 *     <li>Freshness: hitting with the same weapon again and again goes stale and scores less; the others
 *     freshen up meanwhile. Swap between sword, axe, mace, spear, trident and bow.</li>
 *     <li>Extra for a critical hit, a mace smash, hitting while airborne, a shield parry, killing the boss's
 *     summons, and every 10 s close to the boss without being hit.</li>
 *     <li>Getting hit takes a big bite out of the meter, and it drains faster the higher the rank.</li>
 * </ul>
 */
public final class StyleMeter {
	private static final float MAX = 400.0f;
	private static final float FRESH_DROP = 0.15f;
	private static final float FRESH_GAIN = 0.08f;
	private static final float FRESH_MIN = 0.2f;
	private static final float FRESH_RECOVERY = 0.02f;
	private static final int UNTOUCHED_TICKS = 200;
	private static final double NEAR = 12.0;
	private static final List<String> WEAPONS = List.of("sword", "axe", "mace", "spear", "trident", "fists",
			"arrows", "thrown trident", "wind charge", "projectiles");

	private final ServerBossEvent bar;
	private float style;
	private Grade peak = Grade.D;
	private double sum;
	private int samples;
	private int ticks;
	private boolean started;
	private boolean finished;
	private boolean died;
	private int hits;
	private int hitsTaken;
	private int untouched;
	private final Map<String, Float> freshness = new HashMap<>();
	private final Map<String, Integer> uses = new HashMap<>();
	private String feed = "";
	private int feedTicks;

	public StyleMeter(ServerPlayer player) {
		bar = new ServerBossEvent(UUID.randomUUID(), Component.literal("STYLE"), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
		bar.setProgress(0.0f);
		bar.addPlayer(player);
		updateBar();
	}

	// ------------------------------------------------------------------ what the fight reports

	/** The fighter hit the boss for {@code dealt} damage. */
	public void onHit(ServerPlayer player, DamageSource source, float dealt) {
		if (finished) {
			return;
		}
		String weapon = weapon(player, source);
		float fresh = freshness.getOrDefault(weapon, 1.0f);
		float points = 10.0f * fresh * Math.min(1.0f, dealt / 5.0f);
		List<String> tags = new ArrayList<>();
		tags.add(fresh > 0.75f ? "FRESH" : fresh > 0.5f ? "USED" : fresh > 0.3f ? "STALE" : "DULL");
		boolean melee = source.getDirectEntity() == player;
		if (melee && !player.onGround() && player.fallDistance > 0 && !player.isInWater() && !player.onClimbable() && !player.isPassenger()) {
			points *= 1.5f;
			tags.add("CRITICAL");
		}
		if (melee && weapon.equals("mace") && player.fallDistance >= 1.5) {
			points += 20.0f;
			tags.add("SMASH");
		}
		if (!player.onGround() && player.getY() - player.level().getHeight(Heightmap.Types.MOTION_BLOCKING,
				player.getBlockX(), player.getBlockZ()) > 2.0) {
			points += 5.0f;
			tags.add("AIRBORNE");
		}
		for (String w : WEAPONS) {
			float f = freshness.getOrDefault(w, 1.0f);
			freshness.put(w, w.equals(weapon) ? Math.max(FRESH_MIN, f - FRESH_DROP) : Math.min(1.0f, f + FRESH_GAIN));
		}
		uses.merge(weapon, 1, Integer::sum);
		hits++;
		add(points, String.join(" ", tags));
	}

	/** The fighter was hurt by the fight, losing {@code share} of their health. */
	public void onHurt(float share) {
		if (finished) {
			return;
		}
		style = Math.max(0.0f, style - (25.0f + 120.0f * share));
		hitsTaken++;
		untouched = 0;
		show("- HIT");
	}

	/** The fighter blocked a boss's blow with a shield. */
	public void onParry() {
		add(15.0f, "PARRY");
	}

	/** The fighter killed one of the boss's summons. */
	public void onCleanup() {
		add(20.0f, "CLEANUP");
	}

	public void onDeath() {
		if (finished) {
			return;
		}
		died = true;
		style = 0.0f;
		show("- DEATH");
	}

	/** Every tick of the fight; {@code boss} is the body to stay close to (null if it is gone for now). */
	public void tick(ServerPlayer player, @Nullable Entity boss) {
		if (finished) {
			return;
		}
		ticks++;
		if (!bar.getPlayers().contains(player)) {
			bar.addPlayer(player);
		}
		if (started) {
			style = Math.max(0.0f, style - (1.5f + 1.2f * Grade.of(style).ordinal()) / 20.0f);
			if (boss != null && player.distanceTo(boss) < NEAR && ++untouched >= UNTOUCHED_TICKS) {
				untouched = 0;
				add(15.0f, "UNTOUCHED");
			}
			if (ticks % 20 == 0) {
				sum += style;
				samples++;
				freshness.replaceAll((w, f) -> Math.min(1.0f, f + FRESH_RECOVERY));
			}
		}
		if (feedTicks > 0) {
			feedTicks--;
		}
		if (ticks % 5 == 0) {
			updateBar();
		}
	}

	/** The fight is over: the meter stops and its bar goes. */
	public void finish() {
		finished = true;
		bar.removeAllPlayers();
	}

	// ------------------------------------------------------------------ the verdict

	public float average() {
		return samples == 0 ? style : (float) (sum / samples);
	}

	/** The rank of the average style over the fight (no higher than B for anyone who died). */
	public Grade grade() {
		Grade grade = Grade.of(average());
		return died && grade.atLeast(Grade.B) ? Grade.B : grade;
	}

	public String summary() {
		return String.format(Locale.ROOT, "average style %d, peak %s, %d %s on the boss, hit %d %s.", Math.round(average()), peak.name(),
				hits, hits == 1 ? "hit" : "hits", hitsTaken, hitsTaken == 1 ? "time" : "times");
	}

	/** One line on how to rank higher next time. */
	public String advice() {
		Grade grade = grade();
		if (died) {
			return "You died: that resets your style, and your rank can't go above B.";
		}
		String favourite = null;
		int most = 0;
		for (Map.Entry<String, Integer> e : uses.entrySet()) {
			if (e.getValue() > most) {
				most = e.getValue();
				favourite = e.getKey();
			}
		}
		if (favourite != null && hits >= 6 && most > hits * 0.6) {
			String with = switch (favourite) {
				case "arrows", "projectiles" -> favourite;
				case "thrown trident", "wind charge" -> favourite + "s";
				default -> "your " + favourite;
			};
			return "Most of your hits were with " + with + ": swap weapons, because repeating one goes stale.";
		}
		if (hitsTaken >= 3) {
			return "You were hit " + hitsTaken + " times, and every hit drains your style. Dodge the telegraphed attacks.";
		}
		if (grade == Grade.SSS) {
			return "";
		}
		return "For " + grade.next().withArticle() + ": keep attacking, since style drains while you hold back. Crits, mace smashes, parries and killing summons score extra.";
	}

	// ------------------------------------------------------------------ inside

	private void add(float points, String tag) {
		if (finished) {
			return;
		}
		started = true;
		style = Math.min(MAX, style + points);
		Grade now = Grade.of(style);
		if (now.ordinal() > peak.ordinal()) {
			peak = now;
		}
		show("+ " + tag);
	}

	private void show(String text) {
		feed = text;
		feedTicks = 30;
		updateBar();
	}

	private void updateBar() {
		Grade grade = Grade.of(style);
		Grade next = grade.next();
		float top = grade == Grade.SSS ? MAX : next.threshold;
		bar.setProgress(Math.max(0.0f, Math.min(1.0f, (style - grade.threshold) / Math.max(1.0f, top - grade.threshold))));
		bar.setColor(grade.barColor);
		bar.setName(Component.literal("STYLE ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(grade.name() + " " + grade.title).withStyle(grade.color, ChatFormatting.BOLD))
				.append(Component.literal(feedTicks > 0 ? "   " + feed : "").withStyle(ChatFormatting.WHITE)));
	}

	/** What the fighter hit with. */
	static String weapon(ServerPlayer player, DamageSource source) {
		Entity direct = source.getDirectEntity();
		if (direct != null && direct != player) {
			String id = BuiltInRegistries.ENTITY_TYPE.getKey(direct.getType()).getPath();
			if (id.contains("trident")) {
				return "thrown trident";
			}
			if (id.contains("arrow")) {
				return "arrows";
			}
			return id.contains("wind_charge") ? "wind charge" : "projectiles";
		}
		String item = BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).getPath();
		if (item.endsWith("sword")) {
			return "sword";
		}
		if (item.endsWith("_axe")) {
			return "axe";
		}
		if (item.equals("mace")) {
			return "mace";
		}
		if (item.endsWith("spear")) {
			return "spear";
		}
		return item.equals("trident") ? "trident" : "fists";
	}

	/** Headless self-test for the smoke test: the rank for a few amounts of style. */
	public static String selfTest() {
		return "Style test: " + Grade.of(0) + " " + Grade.of(75) + " " + Grade.of(200) + " " + Grade.of(400);
	}
}
