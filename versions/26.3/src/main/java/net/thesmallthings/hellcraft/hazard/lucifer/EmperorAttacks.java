package net.thesmallthings.hellcraft.hazard.lucifer;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
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
import net.thesmallthings.hellcraft.hazard.CircleHazards;
import net.thesmallthings.hellcraft.hazard.Danger;
import net.thesmallthings.hellcraft.hazard.Wingbeat;
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
 * He picks his blows at random (never the same twice running, and the wingbeat only now and then), and while he
 * fights icicles keep falling from his wings round everyone (red circles), so there is always more than one thing to
 * watch.
 */
final class EmperorAttacks {
	enum Attack {SLAM, BITE, WINGBEAT, WEEP, BOTH_HANDS, DRAG, BREATH}

	private static final float SLAM_DAMAGE = 12.0f;
	private static final float BITE_DAMAGE = 14.0f;
	private static final float WINGBEAT_DAMAGE = 6.0f;
	private static final float TEAR_DAMAGE = 8.0f;
	private static final double SLAM_RADIUS = 6.0;
	private static final double BITE_RADIUS = 6.5;
	private static final float DRAG_DAMAGE = 10.0f;
	private static final float BREATH_DAMAGE = 3.0f;
	private static final float ICICLE_DAMAGE = 6.0f;
	/** How far round he turns while dragging a hand or breathing (degrees). */
	private static final float DRAG_TURN = 100.0f;
	private static final float BREATH_TURN = 80.0f;
	/** The breath's reach and its half-width (radians). */
	private static final double BREATH_REACH = 26.0;
	private static final double BREATH_HALF = 0.32;
	/** How often icicles fall while he fights (fight ticks), and after half health. */
	private static final int ICEFALL_EVERY = 120;
	private static final int ICEFALL_EVERY_HALVED = 80;
	/** How far round he turns a tick (degrees), and how near facing his prey he must be to strike. */
	private static final float TURN = 1.6f;
	private static final float AIM = 22.0f;
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
	private int nextIcefall = ICEFALL_EVERY / 2;
	@Nullable
	private LivingEntity prey;
	/** The weak point of the face now down, for a parry to keep it open longer. */
	@Nullable
	private EmperorWeakPoint face;
	private int faceOpenUntil;

	EmperorAttacks(LuciferFight fight) {
		this.fight = fight;
		this.level = fight.level();
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
		if (targets.isEmpty()) {
			return;
		}
		if (prey == null || !prey.isAlive() || !targets.contains(prey)) {
			prey = targets.get(level.getRandom().nextInt(targets.size()));
		}
		// icicles fall from his wings whatever else he is doing
		if (fight.tick() >= nextIcefall) {
			nextIcefall = fight.tick() + (halved ? ICEFALL_EVERY_HALVED : ICEFALL_EVERY);
			icefall(emperor);
		}
		if (fight.tick() < busyUntil) {
			return;
		}
		emperor.turnToward(prey.getX(), prey.getZ(), TURN * (halved ? 1.6f : 1.2f));
		if (--cooldown > 0 || emperor.angleTo(prey.getX(), prey.getZ()) > AIM) {
			return;
		}
		Attack attack = choose();
		int length = perform(attack, emperor, prey);
		double faster = Math.max(0.6, 1.0 - 0.12 * fight.tier());
		cooldown = (int) Math.round((halved ? 16 : 26) * faster);
		busyUntil = fight.tick() + length;
		// the next blow goes to someone else as often as not
		prey = level.getRandom().nextBoolean() ? null : prey;
	}

	/** His next blow: at random, never the same twice running, the wingbeat only every fourth blow or so. */
	private Attack choose() {
		List<Attack> pool = new ArrayList<>(List.of(Attack.SLAM, Attack.SLAM, Attack.BITE, Attack.BITE, Attack.DRAG, Attack.BREATH, Attack.BOTH_HANDS));
		if (halved) {
			pool.addAll(List.of(Attack.WEEP, Attack.BOTH_HANDS, Attack.BREATH));
		}
		if (sinceWingbeat >= 3) {
			pool.add(Attack.WINGBEAT);
			pool.add(Attack.WINGBEAT);
		}
		pool.removeIf(a -> a == last);
		Attack attack = pool.get(level.getRandom().nextInt(pool.size()));
		last = attack;
		sinceWingbeat = attack == Attack.WINGBEAT ? 0 : sinceWingbeat + 1;
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
		};
	}

	// ------------------------------------------------------------------ both hands, the drag

	private int bothHands(EmperorEntity emperor) {
		EmperorAnim a = EmperorAnim.SLAM_BOTH;
		emperor.play(a);
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.SLAM));
		Vec3[] grounds = {fight.onFloor(emperor.slamPoint(true).x, emperor.slamPoint(true).z),
				fight.onFloor(emperor.slamPoint(false).x, emperor.slamPoint(false).z)};
		for (Vec3 g : grounds) {
			Danger.circle(level, Danger.Colour.RED, g, SLAM_RADIUS, a.at("slam"));
		}
		fight.schedule(a.at("slam"), () -> {
			if (!emperor.isAlive()) {
				return;
			}
			for (Vec3 g : grounds) {
				level.playSound(null, g.x, g.y, g.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0f, 0.5f);
				level.sendParticles(ParticleTypes.EXPLOSION, g.x, g.y + 1, g.z, 6, 2.5, 0.5, 2.5, 0);
				level.sendParticles(ParticleTypes.SNOWFLAKE, g.x, g.y + 1, g.z, 150, 4, 1, 4, 0.2);
				for (LivingEntity e : fight.victims(g, SLAM_RADIUS)) {
					if (e.hurtServer(level, Danger.unblockable(level, emperor), SLAM_DAMAGE * fight.damageMultiplier())) {
						e.push(0, 0.6, 0);
						Feedback.syncMotion(e);
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
				if (!emperor.isAlive()) {
					return;
				}
				emperor.turnBy(turn / (lift - down));
				Vec3 h = fight.onFloor(emperor.slamPoint(right).x, emperor.slamPoint(right).z);
				if (at % 3 == 0) {
					level.sendParticles(ParticleTypes.SNOWFLAKE, h.x, h.y + 1, h.z, 30, 2.0, 0.6, 2.0, 0.1);
					level.playSound(null, h.x, h.y, h.z, SoundEvents.GRINDSTONE_USE, SoundSource.HOSTILE, 1.5f, 0.4f);
				}
				for (LivingEntity e : fight.victims(h, 4.5)) {
					if (!hit.contains(e) && e.hurtServer(level, Danger.unblockable(level, emperor), DRAG_DAMAGE * fight.damageMultiplier())) {
						hit.add(e);
						Vec3 away = e.position().subtract(centre).multiply(1, 0, 1);
						away = away.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : away.normalize();
						e.push(away.x * 1.4, 0.5, away.z * 1.4);
						Feedback.syncMotion(e);
					}
				}
			});
		}
		// the hand lies still a moment at the end of its path: a weak point
		fight.schedule(lift - 20, () -> {
			if (emperor.isAlive()) {
				Vec3 h = fight.onFloor(emperor.slamPoint(right).x, emperor.slamPoint(right).z);
				open(emperor, h.add(0, -1.0, 0), 1.0f, 30);
			}
		});
		return a.ticks;
	}

	// ------------------------------------------------------------------ his frozen breath

	/** A face thrust down, and the cold pours out as he turns: a red wedge over all the ice it will cross. */
	private int breath(EmperorEntity emperor, LivingEntity target) {
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
		fight.schedule(a.at("inhale"), () -> level.playSound(null, emperor.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 4.0f, 0.4f));
		for (int t = breathe; t < end; t++) {
			int at = t;
			fight.schedule(t, () -> {
				if (!emperor.isAlive()) {
					return;
				}
				emperor.turnBy(turn / (end - breathe));
				double now = Danger.facing(emperor);
				if (at % 2 == 0) {
					for (int k = 3; k < BREATH_REACH; k += 3) {
						level.sendParticles(ParticleTypes.SNOWFLAKE, centre.x + Math.cos(now) * k, centre.y + 2, centre.z + Math.sin(now) * k,
								4, 0.3 + k * 0.08, 0.6, 0.3 + k * 0.08, 0.02);
					}
				}
				if (at % 6 == 0) {
					level.playSound(null, emperor.blockPosition(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 3.0f, 0.4f);
				}
				if (at % 4 != 0) {
					return;
				}
				Vec3 mouth = emperor.position().add(0, 12, 0);
				for (LivingEntity e : fight.victims(centre, BREATH_REACH)) {
					double off = Math.abs(Mth.wrapDegrees(Math.toDegrees(Math.atan2(e.getZ() - centre.z, e.getX() - centre.x) - now)));
					if (off <= Math.toDegrees(BREATH_HALF) && !sheltered(mouth, e)
							&& e.hurtServer(level, Danger.unblockable(level, emperor), BREATH_DAMAGE * fight.damageMultiplier())) {
						e.setTicksFrozen(Math.min(e.getTicksFrozen() + 40, e.getTicksRequiredToFreeze() + 100));
					}
				}
			});
		}
		// spent, the face hangs low a moment: a weak point
		fight.schedule(end, () -> {
			if (emperor.isAlive()) {
				open(emperor, emperor.bitePoint().add(0, -3.5, 0), 1.5f, 30);
			}
		});
		for (ServerPlayer p : fight.fighters()) {
			CircleHazards.hintOnce(p, HINT_BREATH, "His frozen breath sweeps the red wedge: get out of it, or behind an ice pillar.");
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
				level.sendParticles(ParticleTypes.SNOWFLAKE, m.x, m.y + 6, m.z, 20, 0.2, 3, 0.2, 0.05);
				level.playSound(null, m.x, m.y, m.z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.0f, 1.4f);
				for (LivingEntity e : fight.victims(m, 2.0)) {
					e.hurtServer(level, Danger.unblockable(level, emperor), ICICLE_DAMAGE * fight.damageMultiplier());
				}
			}
		});
	}

	// ------------------------------------------------------------------ his hands

	private int slam(EmperorEntity emperor, LivingEntity target) {
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
		fight.schedule(a.at("slam"), () -> {
			if (!emperor.isAlive()) {
				return;
			}
			level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0f, 0.5f);
			level.sendParticles(ParticleTypes.EXPLOSION, ground.x, ground.y + 1, ground.z, 6, 2.5, 0.5, 2.5, 0);
			level.sendParticles(ParticleTypes.SNOWFLAKE, ground.x, ground.y + 1, ground.z, 200, 4, 1, 4, 0.2);
			for (LivingEntity e : fight.victims(ground, SLAM_RADIUS)) {
				if (e.hurtServer(level, Danger.unblockable(level, emperor), SLAM_DAMAGE * fight.damageMultiplier())) {
					e.push(0, 0.6, 0);
					Feedback.syncMotion(e);
				}
			}
			for (ServerPlayer p : fight.fighters()) {
				Wingbeat.shake(p, 0.5f);
			}
			int open = a.at("lift") - a.at("slam");
			open(emperor, ground.add(0, -1.0, 0), 1.0f, open);
			for (ServerPlayer p : fight.fighters()) {
				CircleHazards.hintOnce(p, HINT_HAND, "His hand lies on the ice: hit it now, while it glows! That is the only way to hurt him.");
			}
		});
		return a.ticks;
	}

	// ------------------------------------------------------------------ his three mouths

	private int bite(EmperorEntity emperor) {
		EmperorAnim[] faces = {EmperorAnim.BITE, EmperorAnim.BITE_RIGHT, EmperorAnim.BITE_LEFT};
		EmperorAnim a = faces[level.getRandom().nextInt(faces.length)];
		emperor.play(a);
		Vec3 head = emperor.bitePoint();
		Vec3 ground = fight.onFloor(head.x, head.z);
		fight.schedule(a.at("flash"), () -> {
			if (!emperor.isAlive()) {
				return;
			}
			fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.BITE));
			emperor.warn(BossEntity.Warning.WHITE);
			fight.openParry(a.at("bite") - a.at("flash") + 5);
			Danger.circle(level, Danger.Colour.WHITE, ground, BITE_RADIUS, a.at("bite") - a.at("flash"));
			level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0f, 0.5f);
		});
		fight.schedule(a.at("bite"), () -> {
			emperor.warn(BossEntity.Warning.NONE);
			if (!emperor.isAlive()) {
				return;
			}
			level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.HOSTILE, 3.0f, 0.5f);
			for (LivingEntity e : fight.victims(ground, BITE_RADIUS)) {
				e.hurtServer(level, level.damageSources().mobAttack(emperor), BITE_DAMAGE * fight.damageMultiplier());
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
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.PARRIED));
		level.playSound(null, emperor.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.HOSTILE, 3.0f, 0.4f);
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
		EmperorAnim a = EmperorAnim.WINGBEAT;
		emperor.play(a);
		emperor.warn(BossEntity.Warning.PURPLE);
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.WINGBEAT));
		Vec3 centre = fight.onFloor(emperor.getX(), emperor.getZ());
		Danger.ring(level, Danger.Colour.PURPLE, centre, EmperorEntity.BODY_RADIUS, LuciferArena.RADIUS, a.at("beat"));
		for (ServerPlayer p : fight.fighters()) {
			CircleHazards.hintOnce(p, HINT_WINGS, "His wings glow purple: get behind an ice pillar before they beat, or be thrown back and frozen.");
		}
		fight.schedule(a.at("beat"), () -> {
			emperor.warn(BossEntity.Warning.NONE);
			if (!emperor.isAlive()) {
				return;
			}
			level.playSound(null, emperor.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 5.0f, 0.3f);
			level.sendParticles(ParticleTypes.SNOWFLAKE, centre.x, centre.y + 2, centre.z, 600, 20, 2, 20, 0.4);
			Vec3 eyes = emperor.position().add(0, 20, 0);
			for (ServerPlayer p : fight.audience()) {
				Wingbeat.beat(p, 1.0f);
			}
			for (LivingEntity e : fight.victims(centre, LuciferArena.RADIUS + 2)) {
				if (sheltered(eyes, e)) {
					continue;
				}
				Vec3 away = e.position().subtract(centre).multiply(1, 0, 1);
				away = away.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : away.normalize();
				e.push(away.x * 1.8, 0.5, away.z * 1.8);
				Feedback.syncMotion(e);
				e.setTicksFrozen(Math.min(e.getTicksFrozen() + 160, e.getTicksRequiredToFreeze() + 120));
				e.hurtServer(level, Danger.unblockable(level, emperor), WINGBEAT_DAMAGE * fight.damageMultiplier());
			}
		});
		return a.ticks;
	}

	/** Behind an ice pillar (or anything solid) seen from his head. */
	private boolean sheltered(Vec3 from, LivingEntity e) {
		Vec3 to = e.getEyePosition();
		HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
		return hit.getType() == HitResult.Type.BLOCK && hit.getLocation().distanceToSqr(to) > 1.0;
	}

	// ------------------------------------------------------------------ his tears

	private int weep(EmperorEntity emperor) {
		EmperorAnim a = EmperorAnim.WEEP;
		emperor.play(a);
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.WEEP));
		fight.schedule(a.at("weep"), () -> {
			if (!emperor.isAlive()) {
				return;
			}
			List<Vec3> marks = new ArrayList<>();
			for (LivingEntity t : fight.targets()) {
				marks.add(fight.onFloor(t.getX(), t.getZ()));
			}
			int scattered = 8 + 2 * fight.extraMarks();
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
					level.sendParticles(ParticleTypes.FALLING_DRIPSTONE_LAVA, m.x, m.y + 8, m.z, 10, 0.4, 2, 0.4, 0);
					level.sendParticles(ParticleTypes.SNOWFLAKE, m.x, m.y + 0.5, m.z, 30, 1.0, 0.2, 1.0, 0.05);
					level.playSound(null, m.x, m.y, m.z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.2f, 0.6f);
					for (LivingEntity e : fight.victims(m, 2.5)) {
						if (e.hurtServer(level, Danger.unblockable(level, emperor), TEAR_DAMAGE * fight.damageMultiplier())) {
							e.setTicksFrozen(Math.min(e.getTicksFrozen() + 80, e.getTicksRequiredToFreeze() + 80));
						}
					}
				}
			});
		});
		return a.ticks;
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
