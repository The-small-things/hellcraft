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
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.thesmallthings.hellcraft.blood.HellWeapons;
import net.thesmallthings.hellcraft.blood.Judgement.Grade;
import net.thesmallthings.hellcraft.combat.Combat;
import net.thesmallthings.hellcraft.combat.Guard;
import net.thesmallthings.hellcraft.combat.Moveset;
import net.thesmallthings.hellcraft.combat.Swing;
import net.thesmallthings.hellcraft.net.StylePayload;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * One fighter's style in one boss fight, after ULTRAKILL: a meter on the side of their screen (the style panel,
 * StylePayload; a boss bar for a game without Hellcraft) that fills as they fight well and drains when they hold
 * back or get hit. Spoils follow the average style over the fight (Judgement), so they reward fighting well, not
 * grinding.
 * <ul>
 *     <li>Every hit on the boss scores, more for a strong hit than a weak one.</li>
 *     <li><b>Repeats score less.</b> With Hellcraft's combat on, each move of a weapon's chain (and its heavy blow) has
 *     its own freshness, so playing the chain through stays fresh and mashing its first cut goes stale; with it off, each
 *     weapon has one, as before.</li>
 *     <li><b>Feats</b> score by name: a chain's finisher, a heavy blow, a timed parry, a riposte, a dodge through a blow,
 *     a counter soon after one, a punish while the boss is broken, a Blood Art, a critical hit, a mace smash, killing
 *     the boss's summons, and every 10 s close to the boss unhurt.</li>
 *     <li><b>Flow</b>: every feat raises a multiplier on all points, from x1 up to x2, faster for a feat unlike the last;
 *     being hit drops it back to x1, and it ebbs if no feat comes for a while. This is where skill shows.</li>
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
	/** The flow multiplier's range, how much a feat raises it (a different feat from the last, the same again), and its ebb. */
	static final float FLOW_MAX = 2.0f;
	static final float FLOW_NEW = 0.15f;
	static final float FLOW_REPEAT = 0.05f;
	private static final int FLOW_EBB_AFTER = 160;
	private static final float FLOW_EBB = 0.01f;
	/** Ticks after a parry or a dodge in which a hit is a counter. */
	private static final int COUNTER_TICKS = 40;

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
	private float flow = 1.0f;
	private String lastFeat = "";
	private int lastFeatTick = -1000;
	private int lastDefenceTick = -1000;
	private final Map<String, Float> freshness = new HashMap<>();
	private final Map<String, Integer> uses = new HashMap<>();
	private String feed = "";
	private int feedTicks;
	/** The fighter, and whether their game draws the style panel (else the boss bar shows it). */
	private final ServerPlayer player;
	private final boolean panel;
	private int sentGrade = -1;
	private float sentProgress = -1;
	private float sentFlow = -1;

	public StyleMeter(ServerPlayer player) {
		this.player = player;
		this.panel = ServerPlayNetworking.canSend(player, StylePayload.TYPE);
		bar = new ServerBossEvent(UUID.randomUUID(), Component.literal("STYLE"), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
		bar.setProgress(0.0f);
		if (!panel) {
			bar.addPlayer(player);
		}
		updateBar();
	}

	// ------------------------------------------------------------------ what the fight reports

	/** The fighter hit the boss for {@code dealt} damage. */
	public void onHit(ServerPlayer player, DamageSource source, float dealt) {
		onHit(player, source, dealt, false);
	}

	/** The fighter hit the boss; {@code punish}: while the boss lies broken (its posture gone). */
	public void onHit(ServerPlayer player, DamageSource source, float dealt, boolean punish) {
		if (finished) {
			return;
		}
		boolean melee = source.getDirectEntity() == player;
		Moveset.Move move = melee && Combat.on() ? Swing.landingMove() : null;
		String key = move != null ? move.anim().name() : weapon(player, source);
		float fresh = freshness.getOrDefault(key, 1.0f);
		float points = 10.0f * fresh * Math.min(1.0f, dealt / 5.0f);
		List<String> feats = new ArrayList<>();
		if (move != null) {
			Moveset set = Moveset.of(player.getMainHandItem());
			if (move.heavy()) {
				points += 8.0f;
				feats.add("HEAVY");
			} else if (set != null && move.finisher(set)) {
				points += 10.0f;
				feats.add("FINISHER");
			}
		}
		if (HellWeapons.unleashing()) {
			points += 20.0f;
			feats.add("BLOOD ART");
		}
		if (melee && Guard.riposting(player)) {
			points += 15.0f;
			feats.add("RIPOSTE");
		} else if (melee && ticks - lastDefenceTick <= COUNTER_TICKS) {
			points += 8.0f;
			feats.add("COUNTER");
		}
		if (punish) {
			points += 10.0f;
			feats.add("PUNISH");
		}
		if (melee && !player.onGround() && player.fallDistance > 0 && !player.isInWater() && !player.onClimbable() && !player.isPassenger()) {
			points *= 1.5f;
			feats.add("CRITICAL");
		}
		if (melee && key.equals("mace") && player.fallDistance >= 1.5) {
			points += 20.0f;
			feats.add("SMASH");
		}
		if (!player.onGround() && player.getY() - player.level().getHeight(Heightmap.Types.MOTION_BLOCKING,
				player.getBlockX(), player.getBlockZ()) > 2.0) {
			points += 5.0f;
			feats.add("AIRBORNE");
		}
		freshness.replaceAll((k, f) -> k.equals(key) ? f : Math.min(1.0f, f + FRESH_GAIN));
		freshness.put(key, Math.max(FRESH_MIN, fresh - FRESH_DROP));
		uses.merge(key, 1, Integer::sum);
		hits++;
		String name = move != null ? Moveset.label(move.anim()) : key.toUpperCase(Locale.ROOT);
		StringBuilder line = new StringBuilder(name);
		for (String f : feats) {
			line.append(' ').append(f);
		}
		if (fresh <= 0.5f) {
			line.append(" REPEATED");
		}
		if (feats.isEmpty()) {
			add(points, line.toString());
		} else {
			feat(points, line.toString(), feats.get(0));
		}
	}

	/** The fighter was hurt by the fight, losing {@code share} of their health. */
	public void onHurt(float share) {
		if (finished) {
			return;
		}
		style = Math.max(0.0f, style - (25.0f + 120.0f * share));
		flow = 1.0f;
		lastFeat = "";
		hitsTaken++;
		untouched = 0;
		show("- HIT");
	}

	/** The fighter blocked a boss's blow with a shield or a weapon. */
	public void onParry() {
		onParry(true);
	}

	/**
	 * The fighter blocked a boss's blow: {@code timed}, a parry (a weapon raised just in time, or a block on the boss's
	 * white flash), a feat; otherwise a guard held up, which scores a little and keeps no flow.
	 */
	public void onParry(boolean timed) {
		lastDefenceTick = ticks;
		if (timed) {
			feat(18.0f, "PARRY", "PARRY");
		} else {
			add(5.0f, "BLOCK");
		}
	}

	/** A boss's blow passed through the fighter's dodge roll. */
	public void onDodge() {
		lastDefenceTick = ticks;
		feat(12.0f, "DODGE", "DODGE");
	}

	/** The fighter killed one of the boss's summons. */
	public void onCleanup() {
		feat(20.0f, "CLEANUP", "CLEANUP");
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
		if (!panel && !bar.getPlayers().contains(player)) {
			bar.addPlayer(player);
		}
		if (started) {
			style = Math.max(0.0f, style - (1.5f + 1.2f * Grade.of(style).ordinal()) / 20.0f);
			if (boss != null && player.distanceTo(boss) < NEAR && ++untouched >= UNTOUCHED_TICKS) {
				untouched = 0;
				feat(15.0f, "UNTOUCHED", "UNTOUCHED");
			}
			if (flow > 1.0f && ticks - lastFeatTick > FLOW_EBB_AFTER) {
				flow = Math.max(1.0f, flow - FLOW_EBB);
				if (ticks % 10 == 0) {
					send("");
				}
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
		if (panel && player.connection != null) {
			ServerPlayNetworking.send(player, new StylePayload(false, Grade.of(style).ordinal(), 0, "", 1.0f));
		}
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
		if (Combat.on() && favourite != null && hits >= 6 && most > hits * 0.5) {
			return "Most of your hits were the same move: play your weapon's chain through, mix in heavy blows, and parry or roll "
					+ "through blows to build your multiplier.";
		}
		if (!Combat.on() && favourite != null && hits >= 6 && most > hits * 0.6) {
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
		if (Combat.on()) {
			return "For " + grade.next().withArticle() + ": keep attacking, since style drains while you hold back. Finishers, heavy blows, "
					+ "timed parries, ripostes and dodges through blows raise your multiplier; getting hit resets it.";
		}
		return "For " + grade.next().withArticle() + ": keep attacking, since style drains while you hold back. Crits, mace smashes, parries and killing summons score extra.";
	}

	// ------------------------------------------------------------------ inside

	/** A feat: it raises the flow (more for one unlike the last), then scores at the new multiplier. */
	private void feat(float points, String line, String kind) {
		if (finished) {
			return;
		}
		flow = raise(flow, kind.equals(lastFeat));
		lastFeat = kind;
		lastFeatTick = ticks;
		add(points, line + String.format(Locale.ROOT, " x%.1f", flow));
	}

	/** The flow after a feat: up by {@link #FLOW_NEW}, or {@link #FLOW_REPEAT} for the same feat again, to {@link #FLOW_MAX}. */
	static float raise(float flow, boolean repeat) {
		return Math.min(FLOW_MAX, flow + (repeat ? FLOW_REPEAT : FLOW_NEW));
	}

	/** The flow multiplier now (1 to 2). */
	public float flow() {
		return flow;
	}

	private void add(float points, String tag) {
		if (finished) {
			return;
		}
		started = true;
		style = Math.min(MAX, style + points * flow);
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
		send(text);
	}

	private float progress(Grade grade) {
		Grade next = grade.next();
		float top = grade == Grade.SSS ? MAX : next.threshold;
		return Math.max(0.0f, Math.min(1.0f, (style - grade.threshold) / Math.max(1.0f, top - grade.threshold)));
	}

	/** Tells the style panel, when the rank or meter moved enough to see, or something scored. */
	private void send(String event) {
		if (!panel || finished || player.connection == null) {
			return;
		}
		Grade grade = Grade.of(style);
		float progress = progress(grade);
		if (event.isEmpty() && grade.ordinal() == sentGrade && Math.abs(progress - sentProgress) < 0.02f && Math.abs(flow - sentFlow) < 0.05f) {
			return;
		}
		sentGrade = grade.ordinal();
		sentProgress = progress;
		sentFlow = flow;
		ServerPlayNetworking.send(player, new StylePayload(true, grade.ordinal(), progress, event, flow));
	}

	private void updateBar() {
		Grade grade = Grade.of(style);
		bar.setProgress(progress(grade));
		send("");
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
		HellWeapons.Weapon hell = HellWeapons.of(player.getMainHandItem());
		if (hell != null) {
			return hell.id;
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

	/** What a fighter is told as their first boss fight's style meter starts. */
	public static String openingTip() {
		return Combat.on()
				? "Your style is ranked in this fight (the STYLE panel). Chain your combos, parry, roll through blows and don't get hit: "
				+ "every feat raises your multiplier, a hit resets it. The better your style, the better your spoils."
				: "Your style is ranked in this fight (the STYLE bar). Hit hard, swap weapons and don't get hit: the better your style, "
				+ "the better your spoils.";
	}

	/** Headless self-test for the smoke test: the rank for a few amounts of style. */
	public static String selfTest() {
		// the flow: different feats climb faster than one repeated, and it stops at the top
		float varied = 1.0f;
		float same = 1.0f;
		for (int i = 0; i < 4; i++) {
			varied = raise(varied, false);
			same = raise(same, i > 0);
		}
		if (!(varied > same && same > 1.0f) || raise(FLOW_MAX, false) != FLOW_MAX) {
			return "Style test FAILED: the flow multiplier climbs wrong (" + varied + ", " + same + ")";
		}
		// a chain of three moves played through stays fresh; one move mashed goes stale
		float chained = 1.0f;
		float mashed = 1.0f;
		for (int i = 0; i < 9; i++) {
			mashed = Math.max(FRESH_MIN, mashed - FRESH_DROP);
			chained = i % 3 == 0 ? Math.max(FRESH_MIN, chained - FRESH_DROP) : Math.min(1.0f, chained + FRESH_GAIN);
		}
		if (chained < 0.75f || mashed > 0.5f) {
			return "Style test FAILED: freshness (" + chained + " chained, " + mashed + " mashed)";
		}
		return "Style test: " + Grade.of(0) + " " + Grade.of(75) + " " + Grade.of(200) + " " + Grade.of(400);
	}
}
