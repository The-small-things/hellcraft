package net.thesmallthings.hellcraft.hazard.lucifer;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.entity.HellEntities;
import net.thesmallthings.hellcraft.entity.boss.BossEntity;
import net.thesmallthings.hellcraft.entity.boss.EmperorEntity;
import net.thesmallthings.hellcraft.entity.boss.EmperorWeakPoint;
import net.thesmallthings.hellcraft.entity.boss.gen.EmperorAnim;
import net.thesmallthings.hellcraft.hazard.BossFx;
import net.thesmallthings.hellcraft.hazard.CircleHazards;
import net.thesmallthings.hellcraft.hazard.Cue;
import net.thesmallthings.hellcraft.hazard.Danger;
import net.thesmallthings.hellcraft.hazard.Wingbeat;
import net.thesmallthings.hellcraft.sound.HellSounds;
import net.thesmallthings.hellcraft.util.Feedback;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Emperor (Inferno XXXIV), the last of the fight: frozen to the chest in the ice at the centre of the pit, he
 * turns in the ice toward whoever he hunts and reaches across the whole arena. He can only be hurt where he lays
 * himself open, and those places glow:
 * <ul>
 *     <li><b>Slam</b> (red): a hand rises and comes down where the red circle is. Then it lies on the ice for a few
 *     seconds: hit the hand.</li>
 *     <li><b>Bite</b> (white): a face comes down, flashes white, and bites where the white circle is. Block the bite and
 *     he reels with the face left low for longer, and it takes twice the damage. After a bite, hit the face.</li>
 *     <li><b>Wingbeat</b> (purple): his wings glow and the whole pit turns purple: hide behind an ice pillar, or be
 *     thrown back and frozen.</li>
 *     <li><b>Both Hands</b> (red): both hands rise together and come down either side of him; both lie on the ice after.</li>
 *     <li><b>The Drag</b> (red): a hand laid on the ice and dragged round as he turns; red circles show its path.</li>
 *     <li><b>Frozen Breath</b> (red): a face thrust down, and the cold pours out as he turns: get out of the red wedge,
 *     or behind an ice pillar.</li>
 *     <li><b>Weep</b> (red, from half health): he weeps with six eyes, and the tears fall as frozen blood.</li>
 * </ul>
 *     <li><b>Swat</b> (white): a hand swung out to his side and swept across the ice in front of him. Parry it and the
 *     hand lies stunned on the ice, glowing, for twice the damage.</li>
 *     <li><b>Clap</b> (red): both hands raised wide and brought together on the ice in front of him, thunder. Roll out
 *     and both lie open.</li>
 *     <li>Every single slam sends a <b>shockwave</b> (a thin purple ring) running out over the ice: jump it or roll.</li>
 * </ul>
 * He picks his blows at random (never the same twice running), more often the ones that answer skill (parries,
 * rolls) than the ones you stand out of; after half health he strings them together. Like the Seraph he has a
 * <b>posture</b> (Posture): blows on his open hands and faces, parried bites and swats and blows slipped through a roll
 * wear it down, and when it breaks he <b>slumps</b> onto the ice, all three faces and both hands open. Icicles fall only
 * if everyone keeps far from him for a while (his answer to standing back).
 * 3.5 playtest: retimed quicker, the swat, the clap, the shockwave and the posture added; the constant icefall, most of
 * the tears and most of the wingbeats taken out.
 */
final class EmperorAttacks {
	enum Attack {SLAM, BITE, WINGBEAT, WEEP, BOTH_HANDS, DRAG, BREATH, SWAT, CLAP}

	private static final float SLAM_DAMAGE = 12.0f;
	private static final float BITE_DAMAGE = 14.0f;
	private static final float WINGBEAT_DAMAGE = 6.0f;
	private static final float TEAR_DAMAGE = 8.0f;
	private static final double SLAM_RADIUS = 6.0;
	private static final double BITE_RADIUS = 6.5;
	private static final float DRAG_DAMAGE = 10.0f;
	private static final float BREATH_DAMAGE = 3.0f;
	private static final float ICICLE_DAMAGE = 6.0f;
	private static final float SWAT_DAMAGE = 11.0f;
	private static final float CLAP_DAMAGE = 13.0f;
	private static final float SHOCK_DAMAGE = 6.0f;
	private static final double CLAP_RADIUS = 6.0;
	/** How far the slam's shockwave runs beyond the hand, and how fast (blocks a fight tick). */
	private static final double SHOCK_REACH = 10.0;
	private static final double SHOCK_SPEED = 1.0;
	/** His swat's reach from his middle, and half its wedge (radians). */
	private static final double SWAT_REACH = 22.0;
	private static final double SWAT_HALF = 0.95;
	/** His posture (points, about a blow's damage each after a weak point's multiplier), and the shares a parry and a slipped blow wear. */
	static final float POSTURE = 160.0f;
	private static final float PARRY_SHARE = 0.3f;
	private static final float DODGE_SHARE = 0.05f;
	/** Fight ticks everyone must keep beyond his reach before the icicles come. */
	private static final int STANDOFF = 100;
	/** How far round he turns while dragging a hand or breathing (degrees). */
	private static final float DRAG_TURN = 100.0f;
	private static final float BREATH_TURN = 80.0f;
	/** The breath's reach and its half-width (radians). */
	private static final double BREATH_REACH = 26.0;
	private static final double BREATH_HALF = 0.32;
	/** At least this long between icefalls (fight ticks). */
	private static final int ICEFALL_EVERY = 120;
	/** How far round he turns a tick (degrees), and how near facing his prey he must be to strike. */
	private static final float TURN = 1.6f;
	private static final float AIM = 22.0f;
	/** Beyond this far from him (his hands' reach and a little) a fighter counts as standing back. */
	private static final double SLAM_FORWARD_REACH = EmperorEntity.SLAM_FORWARD + 8;
	/** Hint bits: his first open hand, his first wingbeat. */
	private static final int HINT_HAND = 42;
	private static final int HINT_WINGS = 43;
	/** Hint bit: his frozen breath (61, after Paradiso's). */
	private static final int HINT_BREATH = 61;

	private final LuciferFight fight;
	private final ServerLevel level;
	private int cooldown = 40;
	private int busyUntil;
	private boolean halved;
	@Nullable
	private Attack last;
	private int sinceWingbeat;
	private int nextIcefall;
	/** Fight ticks everyone has stood beyond his reach. */
	private int standoff;
	/** Bumped when he is cut short (his posture broke): blows scheduled before it don't land. */
	private int epoch;
	/** The rest of the string he is in (after half health). */
	private final java.util.ArrayDeque<Attack> string = new java.util.ArrayDeque<>();
	private int sinceWeep;
	/** A swat is in the air: a parry now stuns its hand. */
	private boolean swatting;
	private boolean swatRight;
	final Posture posture;
	/** Plain tips told once a fight, the first time each new blow comes. */
	private final java.util.Set<String> told = new java.util.HashSet<>();
	@Nullable
	private LivingEntity prey;
	/** The weak point of the face now down, for a parry to keep it open longer. */
	@Nullable
	private EmperorWeakPoint face;
	private int faceOpenUntil;

	EmperorAttacks(LuciferFight fight) {
		this.fight = fight;
		this.level = fight.level();
		this.posture = new Posture(fight, "POSTURE", POSTURE, this::slumped);
	}

	/** A blow scheduled under this epoch may still land. */
	private boolean alive(EmperorEntity emperor, int e) {
		return e == epoch && emperor.isAlive();
	}

	boolean halved() {
		return halved;
	}

	/** Every fight tick of the true form. */
	void tick(EmperorEntity emperor, List<LivingEntity> targets) {
		if (!halved && emperor.getHealth() <= emperor.getMaxHealth() * 0.5f) {
			halved = true;
			fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.EMPEROR_HALF));
			fight.tip("He weeps now too: red circles of frozen tears. Keep hitting his hands and faces when they're down.");
		}
		posture.setFighters(targets.size());
		posture.tick();
		if (targets.isEmpty()) {
			return;
		}
		if (prey == null || !prey.isAlive() || !targets.contains(prey)) {
			prey = targets.get(level.getRandom().nextInt(targets.size()));
		}
		// icicles come only when everyone keeps beyond his reach a while: his answer to standing back
		boolean allFar = targets.stream().allMatch(t -> t.distanceTo(emperor) > SLAM_FORWARD_REACH);
		standoff = allFar ? standoff + 1 : 0;
		if (standoff >= STANDOFF && fight.tick() >= nextIcefall) {
			nextIcefall = fight.tick() + ICEFALL_EVERY;
			standoff = STANDOFF / 2;
			icefall(emperor);
		}
		if (fight.tick() < busyUntil) {
			return;
		}
		emperor.turnToward(prey.getX(), prey.getZ(), TURN * (halved ? 1.6f : 1.2f));
		if (--cooldown > 0 || emperor.angleTo(prey.getX(), prey.getZ()) > AIM) {
			return;
		}
		Attack attack = string.isEmpty() ? choose(emperor) : string.poll();
		if (string.isEmpty() && halved && (attack == Attack.SLAM || attack == Attack.SWAT) && level.getRandom().nextFloat() < 0.5f) {
			// after half health he strings blows together: the other hand, or a bite
			string.add(level.getRandom().nextBoolean() ? Attack.SWAT : Attack.BITE);
		}
		int length = perform(attack, emperor, prey);
		double faster = Math.max(0.6, 1.0 - 0.12 * fight.tier());
		cooldown = (int) Math.round((string.isEmpty() ? (halved ? 14 : 22) : 2) * faster);
		busyUntil = fight.tick() + (string.isEmpty() ? length : Math.max(20, length * 5 / 6));
		// the next blow goes to someone else as often as not
		prey = level.getRandom().nextBoolean() ? null : prey;
	}

	/**
	 * His next blow: at random, never the same twice running, weighted to the blows a skilled fighter answers (the bite and
	 * swat to parry, the slam and clap to roll out of); the wingbeat only every sixth blow or so, his tears rarely.
	 */
	private Attack choose(EmperorEntity emperor) {
		List<Attack> pool = new ArrayList<>(List.of(Attack.SLAM, Attack.SLAM, Attack.BITE, Attack.BITE, Attack.SWAT, Attack.SWAT, Attack.DRAG,
				Attack.BREATH, Attack.BOTH_HANDS));
		double d = prey == null ? 0 : prey.distanceTo(emperor);
		if (d > EmperorEntity.SLAM_FORWARD - CLAP_RADIUS && d < EmperorEntity.SLAM_FORWARD + CLAP_RADIUS) {
			pool.add(Attack.CLAP);
			pool.add(Attack.CLAP);
		}
		if (halved && sinceWeep >= 6) {
			pool.add(Attack.WEEP);
		}
		if (sinceWingbeat >= 6) {
			pool.add(Attack.WINGBEAT);
			pool.add(Attack.WINGBEAT);
		}
		pool.removeIf(a -> a == last);
		Attack attack = pool.get(level.getRandom().nextInt(pool.size()));
		last = attack;
		sinceWingbeat = attack == Attack.WINGBEAT ? 0 : sinceWingbeat + 1;
		sinceWeep = attack == Attack.WEEP ? 0 : sinceWeep + 1;
		return attack;
	}

	/** Runs one attack at a target; returns how long he is busy with it (fight ticks). */
	int perform(Attack attack, EmperorEntity emperor, LivingEntity target) {
		return switch (attack) {
			case SLAM -> slam(emperor, target);
			case BITE -> bite(emperor);
			case WINGBEAT -> wingbeat(emperor);
			case WEEP -> weep(emperor);
			case BOTH_HANDS -> bothHands(emperor);
			case DRAG -> drag(emperor, target);
			case BREATH -> breath(emperor, target);
			case SWAT -> swat(emperor, target);
			case CLAP -> clap(emperor, target);
		};
	}

	// ------------------------------------------------------------------ both hands, the drag

	private int bothHands(EmperorEntity emperor) {
		int e = epoch;
		EmperorAnim a = EmperorAnim.SLAM_BOTH;
		emperor.play(a);
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.SLAM));
		Vec3[] grounds = {fight.onFloor(emperor.slamPoint(true).x, emperor.slamPoint(true).z),
				fight.onFloor(emperor.slamPoint(false).x, emperor.slamPoint(false).z)};
		for (Vec3 g : grounds) {
			Danger.circle(level, Danger.Colour.RED, g, SLAM_RADIUS, a.at("slam"));
		}
		fight.schedule(a.at("slam"), () -> {
			if (!alive(emperor, e)) {
				return;
			}
			for (Vec3 g : grounds) {
				level.playSound(null, g.x, g.y, g.z, HellSounds.EMPEROR_SLAM.value(), SoundSource.HOSTILE, 3.0f, 0.88f);
				BossFx.send(level, ParticleTypes.EXPLOSION, g.x, g.y + 1, g.z, 6, 2.5, 0.5, 2.5, 0);
				BossFx.send(level, ParticleTypes.SNOWFLAKE, g.x, g.y + 1, g.z, 150, 4, 1, 4, 0.2);
				for (LivingEntity v : fight.victims(g, SLAM_RADIUS)) {
					if (v.hurtServer(level, Danger.unblockable(level, emperor), SLAM_DAMAGE * fight.damageMultiplier())) {
						v.push(0, 0.6, 0);
						Feedback.syncMotion(v);
					}
				}
				open(emperor, g.add(0, -1.0, 0), 1.0f, a.at("lift") - a.at("slam"));
			}
			for (ServerPlayer p : fight.fighters()) {
				Wingbeat.shake(p, 0.7f);
			}
		});
		return a.ticks;
	}

	/** A hand laid on the ice beside the prey and dragged round as he turns: red circles along its path. */
	private int drag(EmperorEntity emperor, LivingEntity target) {
		int e = epoch;
		double yaw = Math.toRadians(emperor.getYRot());
		boolean right = (target.getX() - emperor.getX()) * -Math.cos(yaw) + (target.getZ() - emperor.getZ()) * -Math.sin(yaw) >= 0;
		EmperorAnim a = right ? EmperorAnim.DRAG_RIGHT : EmperorAnim.DRAG_LEFT;
		emperor.play(a);
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.SLAM));
		// the hand sweeps from its side across his front: turning toward the other side
		float turn = right ? -DRAG_TURN : DRAG_TURN;
		Vec3 centre = emperor.position();
		Vec3 hand = emperor.slamPoint(right);
		double reach = Math.hypot(hand.x - centre.x, hand.z - centre.z);
		double start = Math.atan2(hand.z - centre.z, hand.x - centre.x);
		int down = a.at("down");
		int lift = a.at("lift");
		for (int k = 0; k <= 6; k++) {
			double ang = start + Math.toRadians(turn) * k / 6.0;
			Danger.circle(level, Danger.Colour.RED, fight.onFloor(centre.x + Math.cos(ang) * reach, centre.z + Math.sin(ang) * reach), 4.0,
					down + (lift - down) * k / 6);
		}
		List<LivingEntity> hit = new ArrayList<>();
		for (int t = down; t < lift; t++) {
			int at = t;
			fight.schedule(t, () -> {
				if (!alive(emperor, e)) {
					return;
				}
				emperor.turnBy(turn / (lift - down));
				Vec3 h = fight.onFloor(emperor.slamPoint(right).x, emperor.slamPoint(right).z);
				if (at % 3 == 0) {
					BossFx.send(level, ParticleTypes.SNOWFLAKE, h.x, h.y + 1, h.z, 30, 2.0, 0.6, 2.0, 0.1);
					level.playSound(null, h.x, h.y, h.z, HellSounds.EMPEROR_DRAG.value(), SoundSource.HOSTILE, 1.5f, 0.85f);
				}
				for (LivingEntity v : fight.victims(h, 4.5)) {
					if (!hit.contains(v) && v.hurtServer(level, Danger.unblockable(level, emperor), DRAG_DAMAGE * fight.damageMultiplier())) {
						hit.add(v);
						Vec3 away = v.position().subtract(centre).multiply(1, 0, 1);
						away = away.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : away.normalize();
						v.push(away.x * 1.4, 0.5, away.z * 1.4);
						Feedback.syncMotion(v);
					}
				}
			});
		}
		// the hand lies still a moment at the end of its path: a weak point
		fight.schedule(lift - 20, () -> {
			if (alive(emperor, e)) {
				Vec3 h = fight.onFloor(emperor.slamPoint(right).x, emperor.slamPoint(right).z);
				open(emperor, h.add(0, -1.0, 0), 1.0f, 30);
			}
		});
		return a.ticks;
	}

	// ------------------------------------------------------------------ his frozen breath

	/** A face thrust down, and the cold pours out as he turns: a red wedge over all the ice it will cross. */
	private int breath(EmperorEntity emperor, LivingEntity target) {
		int e = epoch;
		EmperorAnim a = EmperorAnim.BREATH;
		emperor.play(a);
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.WINGBEAT));
		float turn = level.getRandom().nextBoolean() ? BREATH_TURN : -BREATH_TURN;
		Vec3 centre = fight.onFloor(emperor.getX(), emperor.getZ());
		double facing = Danger.facing(emperor);
		// he starts a little to one side of his prey and turns across it
		double start = facing - Math.toRadians(turn) / 2;
		fight.schedule(0, () -> emperor.turnBy(-turn / 2));
		int breathe = a.at("breathe");
		int end = a.at("end");
		Danger.cone(level, Danger.Colour.RED, centre, facing, BREATH_REACH, Math.abs(Math.toRadians(turn)) / 2 + BREATH_HALF, breathe);
		fight.schedule(a.at("inhale"), () -> level.playSound(null, emperor.blockPosition(), HellSounds.EMPEROR_INHALE.value(), SoundSource.HOSTILE, 4.0f, 0.85f));
		for (int t = breathe; t < end; t++) {
			int at = t;
			fight.schedule(t, () -> {
				if (!alive(emperor, e)) {
					return;
				}
				emperor.turnBy(turn / (end - breathe));
				double now = Danger.facing(emperor);
				if (at % 2 == 0) {
					for (int k = 3; k < BREATH_REACH; k += 3) {
						BossFx.send(level, ParticleTypes.SNOWFLAKE, centre.x + Math.cos(now) * k, centre.y + 2, centre.z + Math.sin(now) * k,
								4, 0.3 + k * 0.08, 0.6, 0.3 + k * 0.08, 0.02);
					}
				}
				if (at % 6 == 0) {
					level.playSound(null, emperor.blockPosition(), HellSounds.EMPEROR_BREATH.value(), SoundSource.HOSTILE, 3.0f, 0.85f);
				}
				if (at % 4 != 0) {
					return;
				}
				Vec3 mouth = emperor.position().add(0, 12, 0);
				for (LivingEntity v : fight.victims(centre, BREATH_REACH)) {
					double off = Math.abs(Mth.wrapDegrees(Math.toDegrees(Math.atan2(v.getZ() - centre.z, v.getX() - centre.x) - now)));
					if (off <= Math.toDegrees(BREATH_HALF) && !sheltered(mouth, v)
							&& v.hurtServer(level, Danger.unblockable(level, emperor), BREATH_DAMAGE * fight.damageMultiplier())) {
						v.setTicksFrozen(Math.min(v.getTicksFrozen() + 40, v.getTicksRequiredToFreeze() + 100));
					}
				}
			});
		}
		// spent, the face hangs low a moment: a weak point
		fight.schedule(end, () -> {
			if (alive(emperor, e)) {
				open(emperor, emperor.bitePoint().add(0, -3.5, 0), 1.5f, 30);
			}
		});
		for (ServerPlayer p : fight.fighters()) {
			CircleHazards.hintNow(p, HINT_BREATH, "His frozen breath sweeps the red wedge: get out of it, or behind an ice pillar.");
		}
		return a.ticks;
	}

	// ------------------------------------------------------------------ icicles

	/** While he fights on, icicles shaken from his wings fall round everyone: red circles. */
	private void icefall(EmperorEntity emperor) {
		List<Vec3> marks = new ArrayList<>();
		for (LivingEntity t : fight.targets()) {
			for (int k = 0; k < 2 + fight.extraMarks(); k++) {
				double ang = level.getRandom().nextDouble() * Math.PI * 2;
				double d = 1.5 + level.getRandom().nextDouble() * 5;
				marks.add(fight.onFloor(t.getX() + Math.cos(ang) * d, t.getZ() + Math.sin(ang) * d));
			}
		}
		int warn = 30;
		for (Vec3 m : marks) {
			Danger.circle(level, Danger.Colour.RED, m, 2.0, warn);
		}
		fight.schedule(warn, () -> {
			if (!emperor.isAlive()) {
				return;
			}
			for (Vec3 m : marks) {
				BossFx.send(level, ParticleTypes.SNOWFLAKE, m.x, m.y + 6, m.z, 20, 0.2, 3, 0.2, 0.05);
				level.playSound(null, m.x, m.y, m.z, HellSounds.EMPEROR_ICICLE.value(), SoundSource.HOSTILE, 1.0f, 1.1f);
				for (LivingEntity v : fight.victims(m, 2.0)) {
					v.hurtServer(level, Danger.unblockable(level, emperor), ICICLE_DAMAGE * fight.damageMultiplier());
				}
			}
		});
	}

	// ------------------------------------------------------------------ his hands

	private int slam(EmperorEntity emperor, LivingEntity target) {
		int e = epoch;
		// the hand on the target's side
		double yaw = Math.toRadians(emperor.getYRot());
		double rx = -Math.cos(yaw);
		double rz = -Math.sin(yaw);
		boolean right = (target.getX() - emperor.getX()) * rx + (target.getZ() - emperor.getZ()) * rz >= 0;
		EmperorAnim a = right ? EmperorAnim.SLAM_RIGHT : EmperorAnim.SLAM_LEFT;
		emperor.play(a);
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.SLAM));
		Vec3 hand = emperor.slamPoint(right);
		Vec3 ground = fight.onFloor(hand.x, hand.z);
		Danger.circle(level, Danger.Colour.RED, ground, SLAM_RADIUS, a.at("slam"));
		Cue.HAND_RISE.play(level, hand.x, hand.y + 10, hand.z, 4.0f, 1.0f);
		fight.schedule(a.at("slam"), () -> {
			if (!alive(emperor, e)) {
				return;
			}
			level.playSound(null, ground.x, ground.y, ground.z, HellSounds.EMPEROR_SLAM.value(), SoundSource.HOSTILE, 3.0f, 0.88f);
			BossFx.send(level, ParticleTypes.EXPLOSION, ground.x, ground.y + 1, ground.z, 6, 2.5, 0.5, 2.5, 0);
			BossFx.send(level, ParticleTypes.SNOWFLAKE, ground.x, ground.y + 1, ground.z, 200, 4, 1, 4, 0.2);
			for (LivingEntity v : fight.victims(ground, SLAM_RADIUS)) {
				if (v.hurtServer(level, Danger.unblockable(level, emperor), SLAM_DAMAGE * fight.damageMultiplier())) {
					v.push(0, 0.6, 0);
					Feedback.syncMotion(v);
				}
			}
			for (ServerPlayer p : fight.fighters()) {
				Wingbeat.shake(p, 0.5f);
			}
			int open = a.at("lift") - a.at("slam");
			open(emperor, ground.add(0, -1.0, 0), 1.0f, open);
			shockwave(emperor, e, ground);
			for (ServerPlayer p : fight.fighters()) {
				CircleHazards.hintNow(p, HINT_HAND, "His hand lies on the ice: hit it now, while it glows! That is the only way to hurt him.");
			}
		});
		return a.ticks;
	}

	// ------------------------------------------------------------------ his three mouths

	private int bite(EmperorEntity emperor) {
		int e = epoch;
		EmperorAnim[] faces = {EmperorAnim.BITE, EmperorAnim.BITE_RIGHT, EmperorAnim.BITE_LEFT};
		EmperorAnim a = faces[level.getRandom().nextInt(faces.length)];
		emperor.play(a);
		Vec3 head = emperor.bitePoint();
		Vec3 ground = fight.onFloor(head.x, head.z);
		fight.schedule(a.at("flash"), () -> {
			if (!alive(emperor, e)) {
				return;
			}
			fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.BITE));
			emperor.warn(BossEntity.Warning.WHITE);
			fight.openParry(a.at("bite") - a.at("flash") + 5);
			Danger.circle(level, Danger.Colour.WHITE, ground, BITE_RADIUS, a.at("bite") - a.at("flash"));
			level.playSound(null, ground.x, ground.y, ground.z, HellSounds.EMPEROR_ROAR.value(), SoundSource.HOSTILE, 3.0f, 0.88f);
		});
		fight.schedule(a.at("bite"), () -> {
			emperor.warn(BossEntity.Warning.NONE);
			if (!alive(emperor, e)) {
				return;
			}
			level.playSound(null, ground.x, ground.y, ground.z, HellSounds.EMPEROR_SPIKES.value(), SoundSource.HOSTILE, 3.0f, 0.88f);
			for (LivingEntity v : fight.victims(ground, BITE_RADIUS)) {
				v.hurtServer(level, level.damageSources().mobAttack(emperor), BITE_DAMAGE * fight.damageMultiplier());
			}
			int open = a.at("rise") - a.at("bite");
			face = open(emperor, head.add(0, -3.5, 0), 1.5f, open);
			faceOpenUntil = fight.tick() + open;
		});
		return a.ticks;
	}

	/** Someone blocked his bite: he reels, the face stays down longer and takes twice the damage. */
	void parried(EmperorEntity emperor) {
		emperor.warn(BossEntity.Warning.NONE);
		posture.addShare(PARRY_SHARE, emperor);
		if (swatting) {
			swatting = false;
			level.playSound(null, emperor.blockPosition(), HellSounds.EMPEROR_SWAT_PARRIED.value(), SoundSource.HOSTILE, 3.0f, 0.85f);
			fight.tip("Parried! His hand lies stunned on the ice: hit it now, it takes twice the damage.");
			fight.schedule(2, () -> {
				Vec3 h = emperor.inFrame(EmperorEntity.SLAM_FORWARD * 0.85, EmperorEntity.SLAM_UP, (swatRight ? 1 : -1) * 2.0);
				open(emperor, fight.onFloor(h.x, h.z).add(0, -1.0, 0), 2.0f, 60);
			});
			busyUntil += 40;
			return;
		}
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.PARRIED));
		level.playSound(null, emperor.blockPosition(), HellSounds.EMPEROR_SWAT_PARRIED.value(), SoundSource.HOSTILE, 3.0f, 0.85f);
		fight.tip("Blocked! His face stays down: hit it now, it takes twice the damage.");
		// the face's weak point appears at the bite (a tick after this): stretch it then
		fight.schedule(2, () -> {
			if (face != null && !face.isRemoved()) {
				face.open(emperor, 3.0f, faceOpenUntil - fight.tick() + 50);
			}
		});
		busyUntil += 50;
		fight.schedule(1, () -> emperor.play(EmperorAnim.HURT));
	}

	// ------------------------------------------------------------------ his wings

	private int wingbeat(EmperorEntity emperor) {
		int e = epoch;
		EmperorAnim a = EmperorAnim.WINGBEAT;
		emperor.play(a);
		emperor.warn(BossEntity.Warning.PURPLE);
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.WINGBEAT));
		Vec3 centre = fight.onFloor(emperor.getX(), emperor.getZ());
		Danger.ring(level, Danger.Colour.PURPLE, centre, EmperorEntity.BODY_RADIUS, LuciferArena.RADIUS, a.at("beat"));
		for (ServerPlayer p : fight.fighters()) {
			CircleHazards.hintNow(p, HINT_WINGS, "His wings glow purple: get behind an ice pillar before they beat, or be thrown back and frozen.");
		}
		fight.schedule(a.at("beat"), () -> {
			emperor.warn(BossEntity.Warning.NONE);
			if (!alive(emperor, e)) {
				return;
			}
			level.playSound(null, emperor.blockPosition(), HellSounds.EMPEROR_WINGBEAT.value(), SoundSource.HOSTILE, 5.0f, 0.82f);
			BossFx.send(level, ParticleTypes.SNOWFLAKE, centre.x, centre.y + 2, centre.z, 600, 20, 2, 20, 0.4);
			Vec3 eyes = emperor.position().add(0, 20, 0);
			for (ServerPlayer p : fight.audience()) {
				Wingbeat.beat(p, 1.0f);
			}
			for (LivingEntity v : fight.victims(centre, LuciferArena.RADIUS + 2)) {
				if (sheltered(eyes, v)) {
					continue;
				}
				Vec3 away = v.position().subtract(centre).multiply(1, 0, 1);
				away = away.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : away.normalize();
				v.push(away.x * 1.8, 0.5, away.z * 1.8);
				Feedback.syncMotion(v);
				v.setTicksFrozen(Math.min(v.getTicksFrozen() + 160, v.getTicksRequiredToFreeze() + 120));
				v.hurtServer(level, Danger.unblockable(level, emperor), WINGBEAT_DAMAGE * fight.damageMultiplier());
			}
		});
		return a.ticks;
	}

	/** Behind an ice pillar (or anything solid) seen from his head. */
	private boolean sheltered(Vec3 from, LivingEntity v) {
		Vec3 to = v.getEyePosition();
		HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, v));
		return hit.getType() == HitResult.Type.BLOCK && hit.getLocation().distanceToSqr(to) > 1.0;
	}

	// ------------------------------------------------------------------ his tears

	private int weep(EmperorEntity emperor) {
		int e = epoch;
		EmperorAnim a = EmperorAnim.WEEP;
		emperor.play(a);
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.WEEP));
		fight.schedule(a.at("weep"), () -> {
			if (!alive(emperor, e)) {
				return;
			}
			List<Vec3> marks = new ArrayList<>();
			for (LivingEntity t : fight.targets()) {
				marks.add(fight.onFloor(t.getX(), t.getZ()));
			}
			int scattered = 4 + fight.extraMarks();
			for (int k = 0; k < scattered; k++) {
				double ang = level.getRandom().nextDouble() * Math.PI * 2;
				double r = EmperorEntity.BODY_RADIUS + 2 + level.getRandom().nextDouble() * (LuciferArena.RADIUS - EmperorEntity.BODY_RADIUS - 4);
				marks.add(fight.onFloor(Math.cos(ang) * r, Math.sin(ang) * r));
			}
			int warn = 32;
			for (Vec3 m : marks) {
				Danger.circle(level, Danger.Colour.RED, m, 2.5, warn);
			}
			fight.schedule(warn, () -> {
				for (Vec3 m : marks) {
					BossFx.send(level, ParticleTypes.FALLING_DRIPSTONE_LAVA, m.x, m.y + 8, m.z, 10, 0.4, 2, 0.4, 0);
					BossFx.send(level, ParticleTypes.SNOWFLAKE, m.x, m.y + 0.5, m.z, 30, 1.0, 0.2, 1.0, 0.05);
					level.playSound(null, m.x, m.y, m.z, HellSounds.EMPEROR_ICICLE.value(), SoundSource.HOSTILE, 1.2f, 0.9f);
					for (LivingEntity v : fight.victims(m, 2.5)) {
						if (v.hurtServer(level, Danger.unblockable(level, emperor), TEAR_DAMAGE * fight.damageMultiplier())) {
							v.setTicksFrozen(Math.min(v.getTicksFrozen() + 80, v.getTicksRequiredToFreeze() + 80));
						}
					}
				}
			});
		});
		return a.ticks;
	}

	// ------------------------------------------------------------------ the swat, the clap, the shockwave

	/** A hand swung out to his side, then swept across the ice in front of him: a white wedge. Parry it and the hand lies stunned. */
	private int swat(EmperorEntity emperor, LivingEntity target) {
		int e = epoch;
		double yaw = Math.toRadians(emperor.getYRot());
		double rx = -Math.cos(yaw);
		double rz = -Math.sin(yaw);
		boolean right = (target.getX() - emperor.getX()) * rx + (target.getZ() - emperor.getZ()) * rz >= 0;
		EmperorAnim a = right ? EmperorAnim.SWAT_RIGHT : EmperorAnim.SWAT_LEFT;
		emperor.play(a);
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.SWAT));
		Vec3 centre = fight.onFloor(emperor.getX(), emperor.getZ());
		double facing = Danger.facing(emperor);
		double side = Math.atan2(rz, rx);
		double toward = Mth.wrapDegrees(Math.toDegrees(side - facing));
		// the wedge leans a little toward the side the hand starts from
		double angle = facing + Math.toRadians(toward) * 0.15 * (right ? 1 : -1);
		fight.schedule(a.at("flash"), () -> {
			if (!alive(emperor, e)) {
				return;
			}
			swatting = true;
			swatRight = right;
			tellOnce("swat", "White flash, a hand swept across the ice: parry it as it rings and the hand lies stunned.");
			emperor.warn(BossEntity.Warning.WHITE);
			fight.openParry(a.at("hit") - a.at("flash") + 5);
			Danger.cone(level, Danger.Colour.WHITE, centre, angle, SWAT_REACH, SWAT_HALF, a.at("hit") - a.at("flash"));
			Cue.SWAT.play(level, centre.x, centre.y + 4, centre.z, 4.0f, 1.0f);
		});
		fight.schedule(a.at("hit"), () -> {
			emperor.warn(BossEntity.Warning.NONE);
			if (!alive(emperor, e)) {
				return;
			}
			BossFx.send(level, ParticleTypes.SNOWFLAKE, centre.x + Math.cos(angle) * 15, centre.y + 1, centre.z + Math.sin(angle) * 15, 120, 6, 0.6, 6, 0.2);
			for (LivingEntity v : fight.victims(centre, SWAT_REACH)) {
				double off = Math.abs(Mth.wrapDegrees(Math.toDegrees(Math.atan2(v.getZ() - centre.z, v.getX() - centre.x) - angle)));
				if (off <= Math.toDegrees(SWAT_HALF) && v.distanceTo(emperor) >= EmperorEntity.BODY_RADIUS - 2
						&& v.hurtServer(level, level.damageSources().mobAttack(emperor), SWAT_DAMAGE * fight.damageMultiplier())) {
					Vec3 away = v.position().subtract(centre).multiply(1, 0, 1);
					away = away.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : away.normalize();
					v.push(away.x * 1.3, 0.45, away.z * 1.3);
					Feedback.syncMotion(v);
				}
			}
		});
		fight.schedule(a.at("hit") + 6, () -> swatting = false);
		return a.ticks;
	}

	/** Both hands raised wide and brought together on the ice in front of him: a red circle; roll out and both lie open. */
	private int clap(EmperorEntity emperor, LivingEntity target) {
		int e = epoch;
		EmperorAnim a = EmperorAnim.CLAP;
		emperor.play(a);
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.CLAP));
		Vec3 meet = emperor.inFrame(EmperorEntity.SLAM_FORWARD, EmperorEntity.SLAM_UP, 0);
		Vec3 ground = fight.onFloor(meet.x, meet.z);
		Danger.circle(level, Danger.Colour.RED, ground, CLAP_RADIUS, a.at("clap"));
		tellOnce("clap", "Both hands raised wide: they close on the red circle. Roll out, then hit both hands while they lie open.");
		fight.schedule(a.at("rise"), () -> {
			if (alive(emperor, e)) {
				Cue.HAND_RISE.play(level, meet.x, meet.y + 12, meet.z, 4.0f, 0.9f);
			}
		});
		fight.schedule(a.at("clap"), () -> {
			if (!alive(emperor, e)) {
				return;
			}
			Cue.CLAP.play(level, ground.x, ground.y + 2, ground.z, 5.0f, 1.0f);
			BossFx.send(level, ParticleTypes.EXPLOSION, ground.x, ground.y + 1, ground.z, 4, 2.0, 0.6, 2.0, 0);
			BossFx.send(level, ParticleTypes.SNOWFLAKE, ground.x, ground.y + 1, ground.z, 200, 4, 1.5, 4, 0.25);
			for (LivingEntity v : fight.victims(ground, CLAP_RADIUS)) {
				if (v.hurtServer(level, Danger.unblockable(level, emperor), CLAP_DAMAGE * fight.damageMultiplier())) {
					v.push(0, 0.7, 0);
					Feedback.syncMotion(v);
				}
			}
			for (ServerPlayer p : fight.fighters()) {
				Wingbeat.shake(p, 0.8f);
			}
			int open = a.at("lift") - a.at("clap");
			for (int side = -1; side <= 1; side += 2) {
				Vec3 h = emperor.inFrame(EmperorEntity.SLAM_FORWARD, EmperorEntity.SLAM_UP, side * 3.0);
				open(emperor, fight.onFloor(h.x, h.z).add(0, -1.0, 0), 1.2f, open);
			}
		});
		return a.ticks;
	}

	/** A thin purple ring running out over the ice from where a hand came down: on the ground it hurts; jump it or roll. */
	private void shockwave(EmperorEntity emperor, int e, Vec3 from) {
		tellOnce("shock", "A slam sends a purple ring running out over the ice: jump it or roll through it.");
		java.util.Set<LivingEntity> hit = new java.util.HashSet<>();
		int steps = (int) Math.ceil(SHOCK_REACH / SHOCK_SPEED);
		Danger.ring(level, Danger.Colour.PURPLE, from, SLAM_RADIUS, SLAM_RADIUS + SHOCK_REACH, steps);
		for (int i = 1; i <= steps; i++) {
			double r = SLAM_RADIUS + i * SHOCK_SPEED;
			fight.schedule(i, () -> {
				if (!alive(emperor, e)) {
					return;
				}
				int points = (int) (r * 3);
				for (int k = 0; k < points; k++) {
					double ang = Math.PI * 2 * k / points;
					if (k % 2 == 0) {
						BossFx.send(level, ParticleTypes.SNOWFLAKE, from.x + Math.cos(ang) * r, from.y + 0.3, from.z + Math.sin(ang) * r, 1, 0.05, 0.05, 0.05, 0.01);
					}
				}
				for (LivingEntity v : fight.victims(from, r + 1)) {
					double d = Math.hypot(v.getX() - from.x, v.getZ() - from.z);
					if (Math.abs(d - r) <= 0.9 && v.onGround() && hit.add(v)) {
						v.hurtServer(level, Danger.unblockable(level, emperor), SHOCK_DAMAGE * fight.damageMultiplier());
					}
				}
			});
		}
	}

	// ------------------------------------------------------------------ his posture

	/** His posture broken: he slumps forward onto the ice, all three faces and both hands open, for a long while. */
	private void tellOnce(String key, String text) {
		if (told.add(key)) {
			fight.tip(text);
		}
	}

	private void slumped() {
		EmperorEntity emperor = fight.emperor();
		if (emperor == null) {
			return;
		}
		epoch++;
		string.clear();
		swatting = false;
		EmperorAnim a = EmperorAnim.SLUMP;
		emperor.warn(BossEntity.Warning.NONE);
		emperor.play(a);
		int open = a.at("rise") - a.at("down");
		posture.brokenFor(a.at("rise"));
		busyUntil = fight.tick() + a.ticks;
		cooldown = Math.max(cooldown, a.ticks + 10);
		Cue.SLUMP.play(level, emperor.getX(), emperor.getY() + 12, emperor.getZ(), 5.0f, 1.0f);
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.SLUMP));
		fight.tip("His posture is BROKEN: he's down on the ice. Hit his faces and hands now, they take far more damage.");
		fight.schedule(a.at("down"), () -> {
			if (!emperor.isAlive()) {
				return;
			}
			open(emperor, emperor.bitePoint().add(0, -4.0, 0), 2.5f, open);
			for (boolean right : new boolean[]{true, false}) {
				Vec3 h = emperor.slamPoint(right);
				open(emperor, fight.onFloor(h.x, h.z).add(0, -1.0, 0), 1.5f, open);
			}
		});
	}

	/** A fighter's blow landed on him (through a weak point): it wears his posture. */
	void hurtBy(EmperorEntity emperor, float dealt) {
		tellOnce("posture", "The bar under his health is his POSTURE: blows on his hands and faces, parries and blows you roll through fill it. Fill it and he slumps.");
		posture.add(dealt, emperor);
	}

	/** A blow slipped through a fighter's roll. */
	void dodged(EmperorEntity emperor) {
		posture.addShare(DODGE_SHARE, emperor);
	}

	boolean punishable() {
		return posture.broken();
	}

	// ------------------------------------------------------------------ his weak points

	/** Lays him open at a point for so many fight ticks (a hand on the ice, a face down low). */
	@Nullable
	private EmperorWeakPoint open(EmperorEntity emperor, Vec3 at, float multiplier, int fightTicks) {
		EmperorWeakPoint point = HellEntities.EMPEROR_WEAK_POINT.create(level, EntitySpawnReason.EVENT);
		if (point == null) {
			return null;
		}
		point.snapTo(at.x, at.y, at.z, 0, 0);
		point.addTag(LuciferManager.TAG);
		// the weak point's own life is in real ticks
		point.open(emperor, multiplier, fight.realTicks(fightTicks));
		level.addFreshEntity(point);
		fight.own(point.getUUID());
		return point;
	}
}
