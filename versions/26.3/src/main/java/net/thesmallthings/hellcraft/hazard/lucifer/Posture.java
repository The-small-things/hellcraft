package net.thesmallthings.hellcraft.hazard.lucifer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.thesmallthings.hellcraft.hazard.Cue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One of Lucifer's forms' posture (after Sekiro), shown as a bar under his health. Every blow that lands on him wears it
 * down (heavy blows and finishers more), a parry wears down a third of it, a blow slipped through a roll a little; left
 * alone for a moment it comes back. When it is all gone he is <b>broken</b>: down for a few seconds, taking more from
 * every blow, which the style meter scores as a punish. Then it starts again, empty.
 *
 * <p>Its numbers are shares of the whole, so it reads the same for any number of fighters (more fighters make it a little
 * larger: {@link #setFighters}). Time is the fight's own clock.
 */
final class Posture {
	/** Fight ticks after the last blow before it starts to come back, and how much of it comes back a tick then. */
	private static final int RECOVER_AFTER = 50;
	private static final float RECOVER = 0.004f;
	/** The share at which it creaks (once, until it recovers below). */
	private static final float CRACK_AT = 0.75f;

	private final LuciferFight fight;
	private final ServerBossEvent bar;
	private final float base;
	private float max;
	private float value;
	private int lastBlow = -1000;
	private int brokenUntil = -1;
	private boolean cracked;
	private final Runnable onBreak;

	Posture(LuciferFight fight, String name, float base, Runnable onBreak) {
		this.fight = fight;
		this.base = base;
		this.max = base;
		this.onBreak = onBreak;
		this.bar = new ServerBossEvent(UUID.randomUUID(), Component.literal(name).withStyle(ChatFormatting.GOLD), BossEvent.BossBarColor.YELLOW,
				BossEvent.BossBarOverlay.NOTCHED_10);
		bar.setProgress(0);
	}

	/** Each fighter beyond the first makes it a third larger. */
	void setFighters(int count) {
		max = base * (1 + 0.33f * Math.max(0, count - 1));
	}

	/** Wears it down by this many points (a blow's damage, say); at nothing left he breaks. */
	void add(float amount, Entity body) {
		if (broken() || amount <= 0) {
			return;
		}
		value += amount;
		lastBlow = fight.tick();
		if (!cracked && value >= max * CRACK_AT) {
			cracked = true;
			Cue.POSTURE_CRACK.play(fight.level(), body.getX(), body.getY() + 2, body.getZ(), 2.0f, 1.0f);
		}
		if (value >= max) {
			breakNow(body);
		}
	}

	/** Wears it down by a share of the whole (a parry: a third). */
	void addShare(float share, Entity body) {
		add(max * share, body);
	}

	/** Broken at once (all three cuts of a flurry parried). */
	void breakNow(Entity body) {
		if (broken()) {
			return;
		}
		value = max;
		Cue.POSTURE_BREAK.play(fight.level(), body.getX(), body.getY() + 2, body.getZ(), 3.0f, 1.0f);
		onBreak.run();
	}

	/** He lies broken until this many fight ticks from now. */
	void brokenFor(int ticks) {
		brokenUntil = fight.tick() + ticks;
	}

	boolean broken() {
		return fight.tick() < brokenUntil;
	}

	/** Every fight tick. */
	void tick() {
		if (brokenUntil >= 0 && !broken()) {
			// back on his feet: empty, and wearing down from nothing
			brokenUntil = -1;
			value = 0;
			cracked = false;
		}
		if (!broken() && value > 0 && fight.tick() - lastBlow > RECOVER_AFTER) {
			value = Math.max(0, value - max * RECOVER);
			if (value < max * CRACK_AT * 0.8f) {
				cracked = false;
			}
		}
		bar.setProgress(Math.max(0, Math.min(1, value / max)));
		bar.setColor(broken() ? BossEvent.BossBarColor.RED : value >= max * CRACK_AT ? BossEvent.BossBarColor.PINK : BossEvent.BossBarColor.YELLOW);
	}

	/** Shown to these players (and no others). */
	void show(List<ServerPlayer> audience) {
		for (ServerPlayer p : new ArrayList<>(bar.getPlayers())) {
			if (!audience.contains(p)) {
				bar.removePlayer(p);
			}
		}
		for (ServerPlayer p : audience) {
			bar.addPlayer(p);
		}
	}

	void hide() {
		bar.removeAllPlayers();
	}

	/** For tests and status: how worn it is (0 to 1). */
	float share() {
		return value / max;
	}
}
