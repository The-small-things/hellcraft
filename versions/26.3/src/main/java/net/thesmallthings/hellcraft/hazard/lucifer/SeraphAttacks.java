package net.thesmallthings.hellcraft.hazard.lucifer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.entity.boss.BossEntity;
import net.thesmallthings.hellcraft.entity.boss.EmperorEntity;
import net.thesmallthings.hellcraft.entity.boss.FallenSeraphEntity;
import net.thesmallthings.hellcraft.entity.boss.gen.FallenSeraphAnim;
import net.thesmallthings.hellcraft.hazard.Danger;
import net.thesmallthings.hellcraft.util.Feedback;

import java.util.ArrayList;
import java.util.List;

/**
 * The Fallen Seraph's duel (LuciferFight's first two phases), in the danger language. He glides over the ice round
 * the sleeping Emperor, keeping close to whoever he hunts, and between glides he:
 * <ul>
 *     <li><b>Slash</b> (white): his blade of light flashes white, and a white wedge in front of him shows where it
 *     lands. Block it in time and he reels.</li>
 *     <li><b>Sweep</b> (purple): his six wings sweep round him. It can't be blocked: be out of the circle, or in the air.</li>
 *     <li><b>Stars</b> (red): he calls the stars down; red circles show where they fall.</li>
 *     <li><b>Lunge</b> (red): a red line, then he is at its end.</li>
 * </ul>
 * Once he snaps (half health) he comes faster and often strikes twice, and the stars keep falling while he fights.
 * <p>
 * His wings guard him: until it is broken a blow does only {@link #GUARDED} of its damage, and three blows at his guard
 * in quick succession draw a riposte, his white slash at once. Block a white slash and his guard breaks: for a few
 * seconds he takes {@link #BROKEN} times the damage. So he can't be beaten by standing and trading blows.
 * The fight runs on its own clock, so every delay here is in the fight's ticks (LuciferFight.schedule).
 */
final class SeraphAttacks {
	enum Attack {SLASH, SWEEP, STARS, LUNGE}

	private static final float SLASH_DAMAGE = 9.0f;
	private static final float SWEEP_DAMAGE = 8.0f;
	private static final float STAR_DAMAGE = 7.0f;
	private static final float LUNGE_DAMAGE = 10.0f;
	private static final double SLASH_REACH = 5.5;
	private static final double SLASH_ARC = 0.8;
	private static final double SWEEP_RADIUS = 6.0;
	/** How far from his prey he likes to hover, and how fast he glides (blocks a fight tick). */
	private static final double KEEP = 3.5;
	private static final double GLIDE = 0.24;
	/** The share of a blow that gets through his guard, and how much more a blow does while it's broken. */
	static final float GUARDED = 0.3f;
	static final float BROKEN = 1.6f;
	/** How long his guard stays broken after a parry (fight ticks, after his stagger). */
	private static final int BROKEN_FOR = 60;
	/** Blows at his guard within this many fight ticks that draw a riposte. */
	private static final int RIPOSTE_BLOWS = 3;
	private static final int RIPOSTE_WINDOW = 50;
	/** In the snapped half, how often the stars fall while he fights on (fight ticks). */
	private static final int STARFALL_EVERY = 150;

	private final LuciferFight fight;
	private final ServerLevel level;
	private int cooldown = 40;
	/** Until this fight tick he is busy with a blow (no gliding). */
	private int busyUntil;
	/** Until this fight tick his guard is broken. */
	private int brokenUntil = -1;
	/** When the latest blows at his guard landed (fight ticks). */
	private final List<Integer> blows = new ArrayList<>();
	private int nextStarfall = STARFALL_EVERY;
	private int guardTips;

	SeraphAttacks(LuciferFight fight) {
		this.fight = fight;
		this.level = fight.level();
	}

	/** Every fight tick of the duel. */
	void tick(FallenSeraphEntity seraph, List<LivingEntity> targets) {
		if (targets.isEmpty()) {
			return;
		}
		LivingEntity target = nearest(seraph, targets);
		if (fight.tick() >= busyUntil) {
			glide(seraph, target);
		}
		if (fight.enraged() && fight.tick() >= nextStarfall) {
			// unbound, he calls the stars down on his own while he fights on
			nextStarfall = fight.tick() + STARFALL_EVERY;
			starfall(seraph);
		}
		if (--cooldown > 0 || fight.tick() < busyUntil) {
			return;
		}
		Attack[] all = Attack.values();
		Attack attack = all[level.getRandom().nextInt(all.length)];
		int length = perform(attack, seraph, target);
		if (fight.enraged() && level.getRandom().nextFloat() < 0.5f) {
			// unbound, he follows one blow with another
			Attack next = all[level.getRandom().nextInt(all.length)];
			fight.schedule(length + 4, () -> {
				FallenSeraphEntity s = fight.seraph();
				if (s != null && target.isAlive() && fight.enraged()) {
					perform(next, s, target);
				}
			});
			length += 40;
		}
		double faster = Math.max(0.6, 1.0 - 0.12 * fight.tier());
		cooldown = length + (int) Math.round((fight.enraged() ? 26 : 44) * faster);
	}

	/** Runs one attack; returns how long it takes (fight ticks). */
	int perform(Attack attack, FallenSeraphEntity seraph, LivingEntity target) {
		face(seraph, target);
		return switch (attack) {
			case SLASH -> slash(seraph);
			case SWEEP -> sweep(seraph);
			case STARS -> stars(seraph);
			case LUNGE -> lunge(seraph, target);
		};
	}

	// ------------------------------------------------------------------ the blows

	private int slash(FallenSeraphEntity seraph) {
		FallenSeraphAnim a = FallenSeraphAnim.SLASH;
		seraph.play(a);
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.SLASH));
		double angle = Danger.facing(seraph);
		Vec3 at = seraph.position();
		fight.schedule(a.at("flash"), () -> {
			seraph.warn(BossEntity.Warning.WHITE);
			fight.openParry(a.at("hit") - a.at("flash") + 5);
			Danger.cone(level, Danger.Colour.WHITE, at, angle, SLASH_REACH, SLASH_ARC, a.at("hit") - a.at("flash"));
			level.playSound(null, seraph.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 2.0f, 1.6f);
		});
		fight.schedule(a.at("hit"), () -> {
			seraph.warn(BossEntity.Warning.NONE);
			if (!seraph.isAlive() || fight.seraph() != seraph) {
				return;
			}
			level.playSound(null, seraph.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.0f, 0.7f);
			for (LivingEntity e : fight.victims(at, SLASH_REACH)) {
				double off = Math.abs(Mth.wrapDegrees(Math.toDegrees(Math.atan2(e.getZ() - at.z, e.getX() - at.x) - angle)));
				if (off <= Math.toDegrees(SLASH_ARC) + 8) {
					e.hurtServer(level, level.damageSources().mobAttack(seraph), SLASH_DAMAGE * fight.damageMultiplier());
				}
			}
		});
		busyUntil = fight.tick() + a.ticks;
		return a.ticks;
	}

	private int sweep(FallenSeraphEntity seraph) {
		FallenSeraphAnim a = FallenSeraphAnim.SWEEP;
		seraph.play(a);
		seraph.warn(BossEntity.Warning.PURPLE);
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.SWEEP));
		Vec3 at = seraph.position();
		Danger.circle(level, Danger.Colour.PURPLE, at, SWEEP_RADIUS, a.at("swept"));
		fight.schedule(a.at("sweep"), () -> level.playSound(null, seraph.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 2.0f, 1.3f));
		fight.schedule(a.at("swept"), () -> {
			seraph.warn(BossEntity.Warning.NONE);
			if (!seraph.isAlive() || fight.seraph() != seraph) {
				return;
			}
			level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.5, at.z, 60, SWEEP_RADIUS / 2, 0.4, SWEEP_RADIUS / 2, 0.05);
			for (LivingEntity e : fight.victims(at, SWEEP_RADIUS)) {
				// in the air, the wings pass under you
				if (e.onGround() && e.hurtServer(level, Danger.unblockable(level, seraph), SWEEP_DAMAGE * fight.damageMultiplier())) {
					Vec3 away = e.position().subtract(at).multiply(1, 0, 1);
					away = away.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : away.normalize();
					e.push(away.x * 1.2, 0.4, away.z * 1.2);
					Feedback.syncMotion(e);
				}
			}
		});
		busyUntil = fight.tick() + a.ticks;
		return a.ticks;
	}

	private int stars(FallenSeraphEntity seraph) {
		FallenSeraphAnim a = FallenSeraphAnim.STARS;
		seraph.play(a);
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.STARS));
		fight.schedule(a.at("call"), () -> {
			if (!seraph.isAlive() || fight.seraph() != seraph) {
				return;
			}
			level.playSound(null, seraph.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 2.0f, 1.8f);
			List<Vec3> marks = new ArrayList<>();
			int around = (fight.enraged() ? 4 : 3) + fight.extraMarks();
			for (LivingEntity t : fight.targets()) {
				marks.add(fight.onFloor(t.getX(), t.getZ()));
				for (int k = 0; k < around; k++) {
					double ang = level.getRandom().nextDouble() * Math.PI * 2;
					double d = 3 + level.getRandom().nextDouble() * 5;
					marks.add(fight.onFloor(t.getX() + Math.cos(ang) * d, t.getZ() + Math.sin(ang) * d));
				}
			}
			int warn = 30;
			for (Vec3 m : marks) {
				Danger.circle(level, Danger.Colour.RED, m, 2.5, warn);
			}
			fight.schedule(warn, () -> {
				for (Vec3 m : marks) {
					level.sendParticles(ParticleTypes.END_ROD, m.x, m.y + 4, m.z, 20, 0.2, 2.0, 0.2, 0.1);
					level.sendParticles(ParticleTypes.EXPLOSION, m.x, m.y + 0.5, m.z, 1, 0, 0, 0, 0);
					level.playSound(null, m.x, m.y, m.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0f, 1.6f);
					for (LivingEntity e : fight.victims(m, 2.5)) {
						e.hurtServer(level, Danger.unblockable(level, seraph), STAR_DAMAGE * fight.damageMultiplier());
					}
				}
			});
		});
		busyUntil = fight.tick() + a.ticks;
		return a.ticks + 30;
	}

	private int lunge(FallenSeraphEntity seraph, LivingEntity target) {
		FallenSeraphAnim a = FallenSeraphAnim.LUNGE;
		seraph.play(a);
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.LUNGE));
		Vec3 from = seraph.position();
		double angle = Math.atan2(target.getZ() - from.z, target.getX() - from.x);
		double length = Math.min(16.0, Math.max(8.0, Math.hypot(target.getX() - from.x, target.getZ() - from.z) + 4));
		// never through the Emperor
		Vec3 to = fight.clampToRing(new Vec3(from.x + Math.cos(angle) * length, from.y, from.z + Math.sin(angle) * length));
		length = Math.hypot(to.x - from.x, to.z - from.z);
		Danger.line(level, Danger.Colour.RED, from, angle, length, 1.6, a.at("go"));
		double len = length;
		fight.schedule(a.at("go"), () -> {
			if (!seraph.isAlive() || fight.seraph() != seraph) {
				return;
			}
			level.playSound(null, seraph.blockPosition(), SoundEvents.TRIDENT_RIPTIDE_3.value(), SoundSource.HOSTILE, 2.0f, 1.2f);
			List<LivingEntity> hit = new ArrayList<>();
			for (int i = 1; i <= 6; i++) {
				double k = i / 6.0;
				fight.schedule(i, () -> {
					Vec3 p = from.add((to.x - from.x) * k, 0, (to.z - from.z) * k);
					seraph.setPos(p.x, fight.hover(p.x, p.z), p.z);
					level.sendParticles(ParticleTypes.END_ROD, p.x, p.y + 2, p.z, 6, 0.3, 0.8, 0.3, 0.02);
					for (LivingEntity e : fight.victims(p, 2.2)) {
						if (!hit.contains(e)) {
							hit.add(e);
							e.hurtServer(level, Danger.unblockable(level, seraph), LUNGE_DAMAGE * fight.damageMultiplier());
						}
					}
				});
			}
		});
		busyUntil = fight.tick() + a.ticks + 6;
		return a.ticks + 6;
	}

	/** Blocked: he reels, his guard breaks, and the next attack waits. */
	void parried(FallenSeraphEntity seraph) {
		seraph.warn(BossEntity.Warning.NONE);
		seraph.play(FallenSeraphAnim.STAGGER);
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.PARRIED));
		level.playSound(null, seraph.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.HOSTILE, 2.0f, 0.6f);
		level.playSound(null, seraph.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.0f, 0.8f);
		level.sendParticles(ParticleTypes.CRIT, seraph.getX(), seraph.getY() + 3, seraph.getZ(), 30, 0.5, 0.8, 0.5, 0.2);
		busyUntil = fight.tick() + FallenSeraphAnim.STAGGER.ticks;
		cooldown = Math.max(cooldown, FallenSeraphAnim.STAGGER.ticks + 20);
		brokenUntil = fight.tick() + FallenSeraphAnim.STAGGER.ticks + BROKEN_FOR;
		blows.clear();
		fight.tip("Blocked! His guard is broken: hit him NOW, your blows do full damage and more for a few seconds.");
	}

	/**
	 * A player's blow: most of it turned aside by his wings, unless his guard is broken. Blows at his guard in quick
	 * succession draw a riposte.
	 */
	float guard(FallenSeraphEntity seraph, Player by, float amount) {
		if (fight.tick() < brokenUntil) {
			level.sendParticles(ParticleTypes.CRIT, seraph.getX(), seraph.getY() + 2.5, seraph.getZ(), 8, 0.4, 0.6, 0.4, 0.2);
			return amount * BROKEN;
		}
		level.sendParticles(ParticleTypes.END_ROD, seraph.getX(), seraph.getY() + 2.5, seraph.getZ(), 6, 0.6, 0.6, 0.6, 0.05);
		level.playSound(null, seraph.blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.HOSTILE, 1.2f, 1.4f);
		if (guardTips++ % 12 == 0) {
			by.sendOverlayMessage(Component.literal("His wings turn your blows aside. Block his WHITE slash to break his guard.")
					.withStyle(ChatFormatting.WHITE));
		}
		blows.removeIf(t -> fight.tick() - t > RIPOSTE_WINDOW);
		blows.add(fight.tick());
		if (blows.size() >= RIPOSTE_BLOWS && fight.tick() >= busyUntil) {
			// he answers at once: the slash, white (block it to break his guard)
			blows.clear();
			face(seraph, by);
			fight.now(() -> cooldown = Math.max(cooldown, slash(seraph) + 10));
		}
		return amount * GUARDED;
	}

	/** In the snapped half, every so often: the stars fall round everyone while he fights on. */
	private void starfall(FallenSeraphEntity seraph) {
		level.playSound(null, seraph.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 2.0f, 1.8f);
		List<Vec3> marks = new ArrayList<>();
		for (LivingEntity t : fight.targets()) {
			for (int k = 0; k < 2 + fight.extraMarks(); k++) {
				double ang = level.getRandom().nextDouble() * Math.PI * 2;
				double d = level.getRandom().nextDouble() * 6;
				marks.add(fight.onFloor(t.getX() + Math.cos(ang) * d, t.getZ() + Math.sin(ang) * d));
			}
		}
		int warn = 34;
		for (Vec3 m : marks) {
			Danger.circle(level, Danger.Colour.RED, m, 2.0, warn);
		}
		fight.schedule(warn, () -> {
			for (Vec3 m : marks) {
				level.sendParticles(ParticleTypes.END_ROD, m.x, m.y + 4, m.z, 16, 0.2, 2.0, 0.2, 0.1);
				level.playSound(null, m.x, m.y, m.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.8f, 1.7f);
				for (LivingEntity e : fight.victims(m, 2.0)) {
					e.hurtServer(level, Danger.unblockable(level, seraph), STAR_DAMAGE * fight.damageMultiplier());
				}
			}
		});
	}

	// ------------------------------------------------------------------ moving

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
