package net.thesmallthings.hellcraft.hazard.lucifer;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.combat.Moveset;
import net.thesmallthings.hellcraft.combat.Swing;
import net.thesmallthings.hellcraft.entity.boss.BossEntity;
import net.thesmallthings.hellcraft.entity.boss.EmperorEntity;
import net.thesmallthings.hellcraft.entity.boss.FallenSeraphEntity;
import net.thesmallthings.hellcraft.entity.boss.gen.FallenSeraphAnim;
import net.thesmallthings.hellcraft.hazard.BossFx;
import net.thesmallthings.hellcraft.hazard.Cue;
import net.thesmallthings.hellcraft.hazard.Danger;
import net.thesmallthings.hellcraft.hazard.gen.CueTimes;
import net.thesmallthings.hellcraft.util.Feedback;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Fallen Seraph's duel (LuciferFight's first two phases): a swordsman, not a storm. He glides over the ice round the
 * sleeping Emperor and fights in short strings of blows picked by how far away his prey is, each blow with its own look,
 * its own sound and its own answer:
 * <ul>
 *     <li><b>Slash</b> (white): the blade up, a flash, down across you. Parry it.</li>
 *     <li><b>Flurry</b> (white, three times): cuts short, short, long, each on a chime. Parry all three and his posture breaks.</li>
 *     <li><b>Feint</b> (white, late): the blade goes up as for the slash and hangs, humming, before it falls on a ring.
 *     A guard raised at the slash's timing is too early to parry.</li>
 *     <li><b>Sweep</b> (purple ring): his wings sweep round him, close in. Jump, roll or be out of the ring.</li>
 *     <li><b>Lunge</b> (red line): across the ice like light. Step off the line.</li>
 *     <li><b>Dive</b> (red, following you): up into the dark, a circle that follows you to the last moment, then down blade
 *     first, to the falling whistle. Roll out late, and he is stuck in the ice: free hits that wear his posture double.</li>
 *     <li><b>Crescent</b> (purple line): a wave of light along the ice. Jump it or roll through it. Once he snaps, two.</li>
 *     <li><b>Counter</b> (once he snaps): wings folded, a low hum. Strike him then and he answers at once, unblockably;
 *     wait it out and he is open.</li>
 *     <li><b>Blink</b> (once he snaps): a pop, and he is behind you, the blade raised; the ring comes as it falls. Turn and parry.</li>
 *     <li><b>Stars</b> (red circles): now and then, and as he snaps; no longer falling all through the fight.</li>
 * </ul>
 * His <b>posture</b> (Posture, a bar under his health) wears down with every blow that lands and every parry; broken,
 * he kneels and takes half as much again from every blow for a few seconds. Blows always do their damage: there is no
 * guard to wait out. The fight runs on its own clock, so every delay here is in the fight's ticks (LuciferFight.schedule).
 */
final class SeraphAttacks {
	enum Attack {SLASH, SWEEP, STARS, LUNGE, FLURRY, FEINT, DIVE, CRESCENT, COUNTER, BLINK}

	private static final float SLASH_DAMAGE = 9.0f;
	private static final float FLURRY_DAMAGE = 6.0f;
	private static final float FEINT_DAMAGE = 11.0f;
	private static final float SWEEP_DAMAGE = 8.0f;
	private static final float STAR_DAMAGE = 7.0f;
	private static final float LUNGE_DAMAGE = 10.0f;
	private static final float DIVE_DAMAGE = 12.0f;
	private static final float CRESCENT_DAMAGE = 8.0f;
	private static final float COUNTER_DAMAGE = 10.0f;
	private static final float BLINK_DAMAGE = 9.0f;
	private static final double SLASH_REACH = 5.5;
	private static final double SLASH_ARC = 0.8;
	private static final double SWEEP_RADIUS = 6.0;
	private static final double DIVE_RADIUS = 2.4;
	private static final double CRESCENT_LENGTH = 22.0;
	private static final double CRESCENT_HALF = 0.9;
	/** How far from his prey he likes to hover, and how fast he glides (blocks a fight tick). */
	private static final double KEEP = 3.5;
	private static final double GLIDE = 0.26;
	/** Prey nearer than this gets his close strings; further, his far ones. */
	private static final double NEAR = 7.0;
	/** His posture (points: about a blow's damage each), and the shares a parry and a slipped blow wear down. */
	static final float POSTURE = 100.0f;
	private static final float PARRY_SHARE = 0.34f;
	private static final float FLURRY_PARRY_SHARE = 0.2f;
	private static final float DODGE_SHARE = 0.08f;
	/** How much more a blow does while his posture is broken, and while he is stuck or open. */
	static final float BROKEN = 1.5f;
	private static final float OPEN = 1.25f;
	/** How long he stays down once broken (fight ticks). */
	private static final int BROKEN_FOR = 80;
	/** How often the stars come down on their own (fight ticks), before and after he snaps. */
	private static final int STARS_EVERY = 800;
	private static final int STARS_EVERY_ENRAGED = 600;

	private final LuciferFight fight;
	private final ServerLevel level;
	final Posture posture;
	private int cooldown = 40;
	/** Until this fight tick he is busy with a blow (no gliding, no new blow). */
	private int busyUntil;
	/** Bumped whenever a blow is cut short (his posture broke, he answered a counter): blows scheduled before it don't land. */
	private int epoch;
	/** The rest of the string he is in. */
	private final Deque<Attack> string = new ArrayDeque<>();
	private String lastString = "";
	private int nextStars = STARS_EVERY / 2;
	/** Until this fight tick he is stuck in the ice (after a dive) or open (after a counter stance waited out). */
	private int openUntil = -1;
	/** While this fight tick lasts he holds his counter stance. */
	private int counterUntil = -1;
	/** Parries landed on the flurry in progress (three break his posture). */
	private int flurryParries;
	private boolean flurrying;
	private boolean snapped;
	private int stanceTips;

	SeraphAttacks(LuciferFight fight) {
		this.fight = fight;
		this.level = fight.level();
		this.posture = new Posture(fight, "POSTURE", POSTURE, this::postureBroken);
	}

	/** Every fight tick of the duel. */
	void tick(FallenSeraphEntity seraph, List<LivingEntity> targets) {
		posture.setFighters(targets.size());
		posture.tick();
		if (targets.isEmpty()) {
			return;
		}
		LivingEntity target = nearest(seraph, targets);
		if (fight.tick() >= busyUntil) {
			glide(seraph, target);
		}
		if (fight.enraged() && !snapped) {
			// the snap: the stars come down at once, and he fights on faster
			snapped = true;
			nextStars = fight.tick();
			string.clear();
		}
		if (--cooldown > 0 || fight.tick() < busyUntil) {
			return;
		}
		if (string.isEmpty()) {
			choose(seraph.distanceTo(target));
		}
		Attack attack = string.poll();
		int length = perform(attack, seraph, target);
		double faster = Math.max(0.6, 1.0 - 0.12 * fight.tier());
		int gap = string.isEmpty() ? (fight.enraged() ? 16 : 28) : (fight.enraged() ? 6 : 10);
		cooldown = length + (int) Math.round(gap * faster);
	}

	/**
	 * His next string of blows: close in, the blade (slash, flurry, feint, sweep at arm's length; once snapped, the counter
	 * stance and the blink too); far off, what crosses the ice (lunge, dive, crescent; the blink once snapped). Never the
	 * same string twice running; the stars when their time comes.
	 */
	private void choose(double distance) {
		List<Attack[]> pool = new ArrayList<>();
		boolean rage = fight.enraged();
		if (fight.tick() >= nextStars) {
			nextStars = fight.tick() + (rage ? STARS_EVERY_ENRAGED : STARS_EVERY);
			string.add(Attack.STARS);
			return;
		}
		if (distance < NEAR) {
			pool.add(new Attack[]{Attack.SLASH});
			pool.add(new Attack[]{Attack.FLURRY});
			pool.add(new Attack[]{Attack.FEINT});
			pool.add(new Attack[]{Attack.FEINT, Attack.SLASH});
			if (distance < 4.5) {
				pool.add(new Attack[]{Attack.SLASH, Attack.SWEEP});
			}
			if (rage) {
				pool.add(new Attack[]{Attack.COUNTER});
				pool.add(new Attack[]{Attack.FLURRY, Attack.SLASH});
				pool.add(new Attack[]{Attack.FEINT, Attack.FLURRY});
				pool.add(new Attack[]{Attack.SWEEP, Attack.BLINK});
			}
		} else {
			pool.add(new Attack[]{Attack.LUNGE});
			pool.add(new Attack[]{Attack.DIVE});
			pool.add(new Attack[]{Attack.CRESCENT});
			pool.add(new Attack[]{Attack.LUNGE, Attack.SLASH});
			if (rage) {
				pool.add(new Attack[]{Attack.BLINK});
				pool.add(new Attack[]{Attack.CRESCENT, Attack.LUNGE});
				pool.add(new Attack[]{Attack.DIVE, Attack.FLURRY});
			}
		}
		pool.removeIf(s -> key(s).equals(lastString));
		Attack[] pick = pool.get(level.getRandom().nextInt(pool.size()));
		lastString = key(pick);
		string.addAll(List.of(pick));
	}

	private static String key(Attack[] s) {
		StringBuilder b = new StringBuilder();
		for (Attack a : s) {
			b.append(a.name()).append(' ');
		}
		return b.toString();
	}

	/** Runs one attack; returns how long it takes (fight ticks). */
	int perform(Attack attack, FallenSeraphEntity seraph, LivingEntity target) {
		face(seraph, target);
		return switch (attack) {
			case SLASH -> slash(seraph);
			case SWEEP -> sweep(seraph);
			case STARS -> stars(seraph);
			case LUNGE -> lunge(seraph, target);
			case FLURRY -> flurry(seraph);
			case FEINT -> feint(seraph);
			case DIVE -> dive(seraph, target);
			case CRESCENT -> crescent(seraph);
			case COUNTER -> counter(seraph);
			case BLINK -> blink(seraph, target);
		};
	}

	/** The blow scheduled under this epoch may still land: he is alive, the same body, and nothing cut him short. */
	private boolean still(FallenSeraphEntity seraph, int e) {
		return e == epoch && seraph.isAlive() && fight.seraph() == seraph;
	}

	// ------------------------------------------------------------------ the blade

	/** A white cut in a wedge in front of him: the flash at `flash`, the blow at `hit`, ticks from now. */
	private void cut(FallenSeraphEntity seraph, int e, int flash, int hit, double reach, double arc, float damage, boolean quiet, Runnable beforeHit) {
		double[] angle = {Danger.facing(seraph)};
		Vec3[] at = {seraph.position()};
		fight.schedule(flash, () -> {
			if (!still(seraph, e)) {
				return;
			}
			angle[0] = Danger.facing(seraph);
			at[0] = seraph.position();
			seraph.warn(BossEntity.Warning.WHITE);
			fight.openParry(hit - flash + 4);
			Runnable mark = () -> Danger.cone(level, Danger.Colour.WHITE, at[0], angle[0], reach, arc, hit - flash);
			if (quiet) {
				Danger.quietly(mark);
			} else {
				mark.run();
			}
		});
		fight.schedule(hit, () -> {
			seraph.warn(BossEntity.Warning.NONE);
			if (!still(seraph, e)) {
				return;
			}
			if (beforeHit != null) {
				beforeHit.run();
			}
			level.playSound(null, seraph.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.0f, 0.7f);
			for (LivingEntity v : fight.victims(at[0], reach)) {
				double off = Math.abs(Mth.wrapDegrees(Math.toDegrees(Math.atan2(v.getZ() - at[0].z, v.getX() - at[0].x) - angle[0])));
				if (off <= Math.toDegrees(arc) + 8) {
					v.hurtServer(level, level.damageSources().mobAttack(seraph), damage * fight.damageMultiplier());
				}
			}
		});
	}

	private int slash(FallenSeraphEntity seraph) {
		FallenSeraphAnim a = FallenSeraphAnim.SLASH;
		seraph.play(a);
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.SLASH));
		cut(seraph, epoch, a.at("flash"), a.at("hit"), SLASH_REACH, SLASH_ARC, SLASH_DAMAGE, false, null);
		busyUntil = fight.tick() + a.ticks;
		return a.ticks;
	}

	private int flurry(FallenSeraphEntity seraph) {
		FallenSeraphAnim a = FallenSeraphAnim.FLURRY;
		seraph.play(a);
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.FLURRY));
		int e = epoch;
		flurrying = true;
		flurryParries = 0;
		fight.schedule(Math.max(0, a.at("hit1") - CueTimes.FLURRY), () -> {
			if (still(seraph, e)) {
				Cue.FLURRY.play(level, seraph.getX(), seraph.getY() + 3, seraph.getZ(), 2.5f, 1.0f);
			}
		});
		for (int k = 1; k <= 3; k++) {
			cut(seraph, e, a.at("flash" + k), a.at("hit" + k), 5.0, 0.9, FLURRY_DAMAGE, true, null);
		}
		fight.schedule(a.at("hit3") + 5, () -> flurrying = false);
		busyUntil = fight.tick() + a.ticks;
		return a.ticks;
	}

	private int feint(FallenSeraphEntity seraph) {
		FallenSeraphAnim a = FallenSeraphAnim.FEINT;
		seraph.play(a);
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.FEINT));
		int e = epoch;
		fight.schedule(Math.max(0, a.at("hit") - CueTimes.FEINT), () -> {
			if (still(seraph, e)) {
				Cue.FEINT.play(level, seraph.getX(), seraph.getY() + 3, seraph.getZ(), 2.5f, 1.0f);
			}
		});
		cut(seraph, e, a.at("flash"), a.at("hit"), SLASH_REACH, SLASH_ARC, FEINT_DAMAGE, true, null);
		busyUntil = fight.tick() + a.ticks;
		return a.ticks;
	}

	private int sweep(FallenSeraphEntity seraph) {
		FallenSeraphAnim a = FallenSeraphAnim.SWEEP;
		seraph.play(a);
		seraph.warn(BossEntity.Warning.PURPLE);
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.SWEEP));
		int e = epoch;
		Vec3 at = seraph.position();
		Danger.circle(level, Danger.Colour.PURPLE, at, SWEEP_RADIUS, a.at("swept"));
		fight.schedule(a.at("sweep"), () -> level.playSound(null, seraph.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 2.0f, 1.3f));
		fight.schedule(a.at("swept"), () -> {
			seraph.warn(BossEntity.Warning.NONE);
			if (!still(seraph, e)) {
				return;
			}
			BossFx.send(level, ParticleTypes.END_ROD, at.x, at.y + 1.5, at.z, 60, SWEEP_RADIUS / 2, 0.4, SWEEP_RADIUS / 2, 0.05);
			for (LivingEntity v : fight.victims(at, SWEEP_RADIUS)) {
				// in the air, the wings pass under you
				if (v.onGround() && v.hurtServer(level, Danger.unblockable(level, seraph), SWEEP_DAMAGE * fight.damageMultiplier())) {
					push(v, at, 1.2, 0.4);
				}
			}
		});
		busyUntil = fight.tick() + a.ticks;
		return a.ticks;
	}

	// ------------------------------------------------------------------ across the ice

	private int lunge(FallenSeraphEntity seraph, LivingEntity target) {
		FallenSeraphAnim a = FallenSeraphAnim.LUNGE;
		seraph.play(a);
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.LUNGE));
		int e = epoch;
		Vec3 from = seraph.position();
		double angle = Math.atan2(target.getZ() - from.z, target.getX() - from.x);
		double length = Math.min(16.0, Math.max(8.0, Math.hypot(target.getX() - from.x, target.getZ() - from.z) + 4));
		// never through the Emperor
		Vec3 to = fight.clampToRing(new Vec3(from.x + Math.cos(angle) * length, from.y, from.z + Math.sin(angle) * length));
		length = Math.hypot(to.x - from.x, to.z - from.z);
		Danger.line(level, Danger.Colour.RED, from, angle, length, 1.6, a.at("go"));
		fight.schedule(a.at("go"), () -> {
			if (!still(seraph, e)) {
				return;
			}
			level.playSound(null, seraph.blockPosition(), SoundEvents.TRIDENT_RIPTIDE_3.value(), SoundSource.HOSTILE, 2.0f, 1.2f);
			List<LivingEntity> hit = new ArrayList<>();
			for (int i = 1; i <= 6; i++) {
				double k = i / 6.0;
				fight.schedule(i, () -> {
					if (!still(seraph, e)) {
						return;
					}
					Vec3 p = from.add((to.x - from.x) * k, 0, (to.z - from.z) * k);
					seraph.setPos(p.x, fight.hover(p.x, p.z), p.z);
					BossFx.send(level, ParticleTypes.END_ROD, p.x, p.y + 2, p.z, 6, 0.3, 0.8, 0.3, 0.02);
					for (LivingEntity v : fight.victims(p, 2.2)) {
						if (!hit.contains(v)) {
							hit.add(v);
							v.hurtServer(level, Danger.unblockable(level, seraph), LUNGE_DAMAGE * fight.damageMultiplier());
						}
					}
				});
			}
		});
		busyUntil = fight.tick() + a.ticks + 6;
		return a.ticks + 6;
	}

	/** Up into the dark, following his prey, and down blade first; stuck in the ice a moment after. */
	private int dive(FallenSeraphEntity seraph, LivingEntity target) {
		FallenSeraphAnim a = FallenSeraphAnim.DIVE;
		seraph.play(a);
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.DIVE));
		int e = epoch;
		int drop = a.at("drop");
		int land = a.at("land");
		Danger.quietly(() -> Danger.follow(level, Danger.Colour.RED, target, DIVE_RADIUS, drop, land));
		fight.schedule(Math.max(0, land - 18), () -> {
			if (still(seraph, e)) {
				Cue.DIVE.play(level, target.getX(), target.getY() + 2, target.getZ(), 2.5f, 1.0f);
			}
		});
		// rising, he drifts over his prey; at the drop he is fixed on where the circle stopped
		for (int i = a.at("rise"); i < drop; i++) {
			fight.schedule(i, () -> {
				if (!still(seraph, e) || !target.isAlive()) {
					return;
				}
				Vec3 to = target.position().subtract(seraph.position()).multiply(1, 0, 1);
				double d = to.length();
				if (d > 0.05) {
					Vec3 next = fight.clampToRing(seraph.position().add(to.normalize().scale(Math.min(d, 0.9))));
					seraph.setPos(next.x, fight.hover(next.x, next.z), next.z);
				}
			});
		}
		Vec3[] spot = {seraph.position()};
		fight.schedule(drop, () -> {
			if (still(seraph, e)) {
				spot[0] = fight.clampToRing(new Vec3(target.getX(), seraph.getY(), target.getZ()));
				seraph.setPos(spot[0].x, fight.hover(spot[0].x, spot[0].z), spot[0].z);
			}
		});
		fight.schedule(land, () -> {
			if (!still(seraph, e)) {
				return;
			}
			Vec3 at = fight.onFloor(spot[0].x, spot[0].z);
			BossFx.send(level, ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 2, 0.5, 0.1, 0.5, 0);
			BossFx.send(level, ParticleTypes.END_ROD, at.x, at.y + 0.5, at.z, 40, 1.5, 0.2, 1.5, 0.08);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.5f, 1.4f);
			for (LivingEntity v : fight.victims(at, DIVE_RADIUS)) {
				if (v.hurtServer(level, Danger.unblockable(level, seraph), DIVE_DAMAGE * fight.damageMultiplier())) {
					push(v, at, 0.9, 0.45);
				}
			}
			// stuck in the ice: free hits
			openUntil = fight.tick() + (a.at("free") - land);
		});
		busyUntil = fight.tick() + a.ticks;
		return a.ticks;
	}

	/** A wave of light along the ice, from his blade: on the ground it cuts; jump it or roll through. Two once he snaps. */
	private int crescent(FallenSeraphEntity seraph) {
		FallenSeraphAnim a = FallenSeraphAnim.CRESCENT;
		seraph.play(a);
		int e = epoch;
		int release = a.at("release");
		double facing = Danger.facing(seraph);
		double[] angles = fight.enraged() ? new double[]{facing - 0.38, facing + 0.38} : new double[]{facing};
		Vec3 from = seraph.position();
		for (double angle : angles) {
			Danger.quietly(() -> Danger.line(level, Danger.Colour.PURPLE, from, angle, CRESCENT_LENGTH, CRESCENT_HALF, release));
		}
		fight.schedule(Math.max(0, release - 6), () -> {
			if (still(seraph, e)) {
				Cue.CRESCENT.play(level, seraph.getX(), seraph.getY() + 1, seraph.getZ(), 2.5f, 1.0f);
			}
		});
		Set<LivingEntity> cut = new HashSet<>();
		double speed = 1.6;
		int steps = (int) Math.ceil(CRESCENT_LENGTH / speed);
		for (int i = 0; i <= steps; i++) {
			double far = Math.min(CRESCENT_LENGTH, (i + 1) * speed);
			double near = i * speed;
			fight.schedule(release + i, () -> {
				if (!still(seraph, e)) {
					return;
				}
				for (double angle : angles) {
					double cx = Math.cos(angle);
					double cz = Math.sin(angle);
					Vec3 front = from.add(cx * far, 0, cz * far);
					BossFx.send(level, ParticleTypes.END_ROD, front.x, fight.onFloor(front.x, front.z).y + 0.3, front.z, 4, -cz * 0.6, 0.05, cx * 0.6, 0.0);
					for (LivingEntity v : fight.victims(from, far + 1)) {
						double along = (v.getX() - from.x) * cx + (v.getZ() - from.z) * cz;
						double across = Math.abs(-(v.getX() - from.x) * cz + (v.getZ() - from.z) * cx);
						if (along >= near - 0.6 && along <= far + 0.6 && across <= CRESCENT_HALF + 0.4 && v.onGround() && cut.add(v)) {
							v.hurtServer(level, Danger.unblockable(level, seraph), CRESCENT_DAMAGE * fight.damageMultiplier());
						}
					}
				}
			});
		}
		busyUntil = fight.tick() + a.ticks;
		return a.ticks + 4;
	}

	// ------------------------------------------------------------------ the stance and the blink

	/** Wings folded, a low hum: strike him now and he answers; wait, and he is open. */
	private int counter(FallenSeraphEntity seraph) {
		FallenSeraphAnim a = FallenSeraphAnim.COUNTER;
		seraph.play(a);
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.COUNTER));
		int e = epoch;
		fight.schedule(a.at("ready"), () -> {
			if (!still(seraph, e)) {
				return;
			}
			counterUntil = fight.tick() + (a.at("open") - a.at("ready"));
			Cue.COUNTER.play(level, seraph.getX(), seraph.getY() + 2, seraph.getZ(), 2.5f, 1.0f);
			if (stanceTips++ < 2) {
				fight.tip("His wings are folded and he's humming: DON'T strike him now. Wait, and he'll be open.");
			}
		});
		fight.schedule(a.at("open"), () -> {
			if (!still(seraph, e)) {
				return;
			}
			counterUntil = -1;
			seraph.play(FallenSeraphAnim.STAGGER);
			openUntil = fight.tick() + 36;
			busyUntil = fight.tick() + 36;
			level.playSound(null, seraph.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 2.0f, 0.7f);
		});
		busyUntil = fight.tick() + a.ticks;
		return a.ticks;
	}

	/** A blow struck into his counter stance: he answers at once, an unblockable cut (red-purple, short warning). */
	private void answer(FallenSeraphEntity seraph, Player by) {
		counterUntil = -1;
		epoch++;
		int e = epoch;
		face(seraph, by);
		FallenSeraphAnim a = FallenSeraphAnim.RIPOSTE;
		seraph.play(a);
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.ANSWER));
		double angle = Danger.facing(seraph);
		Vec3 at = seraph.position();
		Danger.cone(level, Danger.Colour.PURPLE, at, angle, SLASH_REACH, 1.0, a.at("hit"));
		fight.schedule(a.at("hit"), () -> {
			if (!still(seraph, e)) {
				return;
			}
			level.playSound(null, seraph.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.0f, 0.5f);
			for (LivingEntity v : fight.victims(at, SLASH_REACH)) {
				double off = Math.abs(Mth.wrapDegrees(Math.toDegrees(Math.atan2(v.getZ() - at.z, v.getX() - at.x) - angle)));
				if (off <= Math.toDegrees(1.0) + 8) {
					v.hurtServer(level, Danger.unblockable(level, seraph), COUNTER_DAMAGE * fight.damageMultiplier());
				}
			}
		});
		busyUntil = fight.tick() + a.ticks;
		cooldown = Math.max(cooldown, a.ticks + 10);
	}

	/** Gone with a pop, back behind his prey with the blade raised; the ring comes as it falls. */
	private int blink(FallenSeraphEntity seraph, LivingEntity target) {
		FallenSeraphAnim a = FallenSeraphAnim.BLINK;
		seraph.play(a);
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.BLINK));
		int e = epoch;
		fight.schedule(a.at("vanish"), () -> {
			if (!still(seraph, e)) {
				return;
			}
			Cue.BLINK.play(level, seraph.getX(), seraph.getY() + 2, seraph.getZ(), 2.5f, 1.0f);
			BossFx.send(level, ParticleTypes.END_ROD, seraph.getX(), seraph.getY() + 2, seraph.getZ(), 30, 0.4, 1.0, 0.4, 0.15);
		});
		fight.schedule(a.at("appear"), () -> {
			if (!still(seraph, e) || !target.isAlive()) {
				return;
			}
			Vec3 look = target.getLookAngle().multiply(1, 0, 1);
			look = look.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : look.normalize();
			Vec3 behind = fight.clampToRing(target.position().subtract(look.scale(2.6)));
			seraph.setPos(behind.x, fight.hover(behind.x, behind.z), behind.z);
			face(seraph, target);
			BossFx.send(level, ParticleTypes.END_ROD, behind.x, seraph.getY() + 2, behind.z, 30, 0.4, 1.0, 0.4, 0.15);
		});
		cut(seraph, e, a.at("flash"), a.at("hit"), 4.5, 0.9, BLINK_DAMAGE, true, null);
		busyUntil = fight.tick() + a.ticks;
		return a.ticks;
	}

	private int stars(FallenSeraphEntity seraph) {
		FallenSeraphAnim a = FallenSeraphAnim.STARS;
		seraph.play(a);
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.STARS));
		int e = epoch;
		fight.schedule(a.at("call"), () -> {
			if (!still(seraph, e)) {
				return;
			}
			level.playSound(null, seraph.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 2.0f, 1.8f);
			List<Vec3> marks = new ArrayList<>();
			int around = (fight.enraged() ? 2 : 1) + fight.extraMarks();
			for (LivingEntity t : fight.targets()) {
				marks.add(fight.onFloor(t.getX(), t.getZ()));
				for (int k = 0; k < around; k++) {
					double ang = level.getRandom().nextDouble() * Math.PI * 2;
					double d = 3 + level.getRandom().nextDouble() * 4;
					marks.add(fight.onFloor(t.getX() + Math.cos(ang) * d, t.getZ() + Math.sin(ang) * d));
				}
			}
			int warn = 30;
			for (Vec3 m : marks) {
				Danger.circle(level, Danger.Colour.RED, m, 2.5, warn);
			}
			fight.schedule(warn, () -> {
				for (Vec3 m : marks) {
					BossFx.send(level, ParticleTypes.END_ROD, m.x, m.y + 4, m.z, 20, 0.2, 2.0, 0.2, 0.1);
					BossFx.send(level, ParticleTypes.EXPLOSION, m.x, m.y + 0.5, m.z, 1, 0, 0, 0, 0);
					level.playSound(null, m.x, m.y, m.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0f, 1.6f);
					for (LivingEntity v : fight.victims(m, 2.5)) {
						v.hurtServer(level, Danger.unblockable(level, seraph), STAR_DAMAGE * fight.damageMultiplier());
					}
				}
			});
		});
		busyUntil = fight.tick() + a.ticks;
		return a.ticks + 30;
	}

	// ------------------------------------------------------------------ what the fighters do to him

	/** A blow blocked in his parry window: a parry. On the flurry he fights on (three parried break him); else he reels. */
	void parried(FallenSeraphEntity seraph) {
		level.playSound(null, seraph.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.HOSTILE, 1.5f, 0.6f);
		BossFx.send(level, ParticleTypes.CRIT, seraph.getX(), seraph.getY() + 3, seraph.getZ(), 20, 0.5, 0.8, 0.5, 0.2);
		if (flurrying) {
			flurryParries++;
			if (flurryParries >= 3) {
				flurrying = false;
				posture.breakNow(seraph);
			} else {
				posture.addShare(FLURRY_PARRY_SHARE, seraph);
			}
			return;
		}
		posture.addShare(PARRY_SHARE, seraph);
		if (posture.broken()) {
			return;
		}
		seraph.warn(BossEntity.Warning.NONE);
		epoch++;
		seraph.play(FallenSeraphAnim.STAGGER);
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.PARRIED));
		busyUntil = fight.tick() + FallenSeraphAnim.STAGGER.ticks / 2;
		cooldown = Math.max(cooldown, FallenSeraphAnim.STAGGER.ticks / 2 + 10);
		string.clear();
	}

	/** His posture is gone: he kneels, open, every blow on him doing half as much again for a while. */
	private void postureBroken() {
		FallenSeraphEntity seraph = fight.seraph();
		if (seraph == null) {
			return;
		}
		epoch++;
		flurrying = false;
		counterUntil = -1;
		string.clear();
		seraph.warn(BossEntity.Warning.NONE);
		seraph.play(FallenSeraphAnim.BROKEN);
		posture.brokenFor(BROKEN_FOR);
		busyUntil = fight.tick() + FallenSeraphAnim.BROKEN.ticks;
		cooldown = Math.max(cooldown, FallenSeraphAnim.BROKEN.ticks + 12);
		BossFx.send(level, ParticleTypes.CRIT, seraph.getX(), seraph.getY() + 2, seraph.getZ(), 60, 0.8, 1.0, 0.8, 0.3);
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.BROKEN));
		fight.tip("His posture is BROKEN: hit him now, every blow does half as much again for a few seconds.");
	}

	/** How much of a fighter's blow reaches him: more while he is broken or open; a blow into his counter stance draws his answer. */
	float damageTaken(FallenSeraphEntity seraph, Player by, float amount) {
		if (fight.tick() < counterUntil) {
			answer(seraph, by);
			return amount * 0.5f;
		}
		if (posture.broken()) {
			return amount * BROKEN;
		}
		return fight.tick() < openUntil ? amount * OPEN : amount;
	}

	/** A fighter's blow landed: it wears his posture (heavy blows and finishers more, double while he is stuck or open). */
	void hurtBy(FallenSeraphEntity seraph, ServerPlayer by, float dealt) {
		float k = 1.0f;
		Moveset.Move move = Swing.landingMove();
		if (move != null) {
			Moveset set = Moveset.of(by.getMainHandItem());
			if (move.heavy() || set != null && move.finisher(set)) {
				k = 1.5f;
			}
		}
		if (fight.tick() < openUntil) {
			k *= 2.0f;
		}
		posture.add(dealt * k, seraph);
	}

	/** A blow slipped through a fighter's roll: it costs him a little posture. */
	void dodged(FallenSeraphEntity seraph) {
		posture.addShare(DODGE_SHARE, seraph);
	}

	/** Whether a blow on him now is a punish (his posture broken). */
	boolean punishable() {
		return posture.broken();
	}

	// ------------------------------------------------------------------ moving

	private static void push(LivingEntity v, Vec3 from, double strength, double up) {
		Vec3 away = v.position().subtract(from).multiply(1, 0, 1);
		away = away.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : away.normalize();
		v.push(away.x * strength, up, away.z * strength);
		Feedback.syncMotion(v);
	}

	/** He glides toward a spot near his prey, never into the Emperor or out of the pit, hovering a little. */
	private void glide(FallenSeraphEntity seraph, LivingEntity target) {
		Vec3 to = target.position().subtract(seraph.position()).multiply(1, 0, 1);
		double d = to.length();
		face(seraph, target);
		if (d <= KEEP) {
			seraph.setPos(seraph.getX(), fight.hover(seraph.getX(), seraph.getZ()), seraph.getZ());
			return;
		}
		Vec3 step = to.normalize().scale(Math.min(GLIDE * (fight.enraged() ? 1.4 : 1.0), d - KEEP));
		Vec3 next = fight.clampToRing(seraph.position().add(step));
		seraph.setPos(next.x, fight.hover(next.x, next.z), next.z);
	}

	private static void face(FallenSeraphEntity seraph, LivingEntity target) {
		float yaw = (float) Math.toDegrees(Math.atan2(target.getZ() - seraph.getZ(), target.getX() - seraph.getX())) - 90.0f;
		seraph.setYRot(yaw);
		seraph.setYBodyRot(yaw);
		seraph.setYHeadRot(yaw);
	}

	private static LivingEntity nearest(FallenSeraphEntity seraph, List<LivingEntity> targets) {
		LivingEntity best = targets.getFirst();
		for (LivingEntity t : targets) {
			if (t.distanceToSqr(seraph) < best.distanceToSqr(seraph)) {
				best = t;
			}
		}
		return best;
	}

	/** The Emperor's body: the Seraph keeps out of it (checked by clampToRing). */
	static double bodyRadius() {
		return EmperorEntity.BODY_RADIUS + 2.0;
	}
}
