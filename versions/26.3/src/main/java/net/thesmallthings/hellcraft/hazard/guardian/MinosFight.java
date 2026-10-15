package net.thesmallthings.hellcraft.hazard.guardian;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.cutscene.Cutscene;
import net.thesmallthings.hellcraft.cutscene.Cutscenes;
import net.thesmallthings.hellcraft.entity.HellEntities;
import net.thesmallthings.hellcraft.entity.boss.BossEntity;
import net.thesmallthings.hellcraft.entity.boss.MinosEntity;
import net.thesmallthings.hellcraft.entity.boss.gen.MinosAnim;
import net.thesmallthings.hellcraft.hazard.Danger;
import net.thesmallthings.hellcraft.util.Feedback;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Minos (Inferno V), who judges each soul and wraps his tail around himself once for every circle it must descend.
 * He never leaves his coils, and every blow speaks the danger language (hazard/Danger):
 * <ul>
 *     <li><b>The Claw</b> (white): he raises a claw and flashes white, and the ground before him shows where it will
 *     fall. Block it with a shield as it lands and he reels: open to blows, his armour gone, for two seconds.</li>
 *     <li><b>Tail of Judgement</b> (purple): his tail sweeps a full circle round him along the ground. It can't be
 *     blocked: jump it as it passes, or stand off past its tip.</li>
 *     <li><b>Sentence</b> (red): he points out a soul and names its circle; a red circle follows them, stops, and
 *     the hurricane of Lust tears up the ground there. Keep moving.</li>
 *     <li><b>The Coil</b> (a red line): his tail lunges along the line and closes on whoever is still in it, and
 *     squeezes until the others hurt him enough to make him let go.</li>
 * </ul>
 * At half his health he rises in fury: every soul in the lair is sentenced at once, and he attacks faster.
 */
final class MinosFight extends GuardianFight {
	private static final DustParticleOptions JUDGEMENT = new DustParticleOptions(0x6A2A9A, 1.6f);
	private static final double CLAW_REACH = 7.5;
	private static final double CLAW_ARC = Math.toRadians(40);
	private static final float CLAW_DAMAGE = 9.0f;
	/** The ring the tail sweeps, from his coils to its tip. */
	private static final double TAIL_INNER = 2.5;
	private static final double TAIL_OUTER = 9.5;
	private static final float TAIL_DAMAGE = 8.0f;
	/** The tail's turn during the sweep, in degrees from straight behind him (as tools/art/minos.py animates it). */
	private static final double SWEEP_FROM = -50;
	private static final double SWEEP_TO = 330;
	private static final double SENTENCE_RADIUS = 3.0;
	private static final float SENTENCE_DAMAGE = 7.0f;
	private static final double COIL_REACH = 11.0;
	private static final double COIL_WIDTH = 1.6;
	/** How hard the others must hit him to free a coiled soul, and how long he holds on otherwise. */
	private static final float COIL_BREAK = 15.0f;
	private static final int COIL_TICKS = 80;

	private int cycle;
	private boolean enraged;
	/** Until this tick he keeps the direction he chose (a telegraphed blow doesn't turn to follow you). */
	private int lockedUntil;
	private int staggerUntil = -1;
	@Nullable
	private UUID coiled;
	private int coilTicks;
	private float coilDamage;

	MinosFight(GuardianContext ctx) {
		super(ctx);
	}

	@Override
	protected Mob createBody() {
		MinosEntity body = HellEntities.MINOS.create(level, EntitySpawnReason.EVENT);
		if (body == null) {
			throw new IllegalStateException("could not create Minos");
		}
		return body;
	}

	private static MinosEntity minos(Mob body) {
		return (MinosEntity) body;
	}

	@Override
	public List<String> attacks() {
		return List.of("strike", "tail", "sentence", "coil");
	}

	@Override
	protected String introLine() {
		return "O thou who comest to the dolorous hospice, beware how thou enterest, and in whom thou trustest!";
	}

	@Override
	protected String deathLine() {
		return "Judged... by the judged. Go down, then. The circles are waiting.";
	}

	@Override
	protected Cutscene introCutscene() {
		return Cutscenes.MINOS_INTRO;
	}

	@Override
	protected int intro(Mob body) {
		// he rises facing whoever woke him
		LivingEntity first = nearest(body, targets());
		if (first != null) {
			face(body, first);
		}
		minos(body).play(MinosAnim.INTRO);
		schedule(MinosAnim.INTRO.at("rise"), () -> level.playSound(null, body.blockPosition(), SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 2.0f, 0.7f));
		schedule(MinosAnim.INTRO.at("roar"), () -> level.playSound(null, body.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0f, 0.6f));
		return MinosAnim.INTRO.ticks;
	}

	// ------------------------------------------------------------------ choosing

	@Override
	protected int nextAttack(Mob body, List<LivingEntity> targets) {
		LivingEntity nearest = nearest(body, targets);
		String[] order = coiled != null ? new String[]{"sentence", "strike"} : new String[]{"strike", "tail", "sentence", "coil"};
		String attack = order[cycle++ % order.length];
		double reach = nearest == null ? Double.MAX_VALUE : nearest.distanceTo(body);
		if (attack.equals("strike") && reach > CLAW_REACH + 1.5) {
			attack = "sentence";
		} else if (attack.equals("coil") && reach > COIL_REACH) {
			attack = "sentence";
		}
		LivingEntity target = attack.equals("sentence") ? targets.get(level.getRandom().nextInt(targets.size())) : nearest;
		int cooldown = perform(attack, body, target == null ? targets.get(0) : target);
		return enraged ? Math.max(cooldown * 7 / 10, 30) : cooldown;
	}

	@Override
	protected int perform(String attack, Mob body, LivingEntity target) {
		return switch (attack) {
			case "strike" -> strike(body, target);
			case "tail" -> tail(body);
			case "sentence" -> sentence(body, target);
			case "coil" -> coil(body, target);
			default -> -1;
		};
	}

	// ------------------------------------------------------------------ the attacks

	/** The Claw: white. The one blow a shield can turn, and turning it in time staggers him. */
	private int strike(Mob body, LivingEntity target) {
		MinosEntity minos = minos(body);
		face(body, target);
		lockedUntil = tick + MinosAnim.STRIKE.ticks;
		double angle = Danger.facing(body);
		Vec3 c = body.position();
		int flash = MinosAnim.STRIKE.at("flash");
		int hit = MinosAnim.STRIKE.at("hit");
		minos.play(MinosAnim.STRIKE);
		say("Thy sentence is MINE to give!");
		schedule(flash, () -> {
			minos.warn(BossEntity.Warning.WHITE);
			openParry(hit - flash + 3);
			Danger.cone(level, Danger.Colour.WHITE, c, angle, CLAW_REACH, CLAW_ARC, hit - flash);
		});
		schedule(hit, () -> {
			minos.warn(BossEntity.Warning.NONE);
			level.playSound(null, body.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.0f, 0.5f);
			for (LivingEntity e : victims(c, CLAW_REACH + 0.5)) {
				Vec3 d = e.position().subtract(c);
				double off = Math.abs(wrap(Math.atan2(d.z, d.x) - angle));
				if (off <= CLAW_ARC + 0.15 && e.hurtServer(level, level.damageSources().mobAttack(body), CLAW_DAMAGE)) {
					fling(e, c, 1.0, 0.35);
				}
			}
		});
		return MinosAnim.STRIKE.ticks + 30;
	}

	/** Tail of Judgement: purple. A full circle along the ground; jump it as it passes. */
	private int tail(Mob body) {
		MinosEntity minos = minos(body);
		lockedUntil = tick + MinosAnim.TAIL.ticks;
		Vec3 c = body.position();
		int sweep = MinosAnim.TAIL.at("sweep");
		int swept = MinosAnim.TAIL.at("swept");
		minos.play(MinosAnim.TAIL);
		minos.warn(BossEntity.Warning.PURPLE);
		say("Stand for judgement!");
		tip("His tail sweeps the ground: JUMP as it reaches you!");
		Danger.ring(level, Danger.Colour.PURPLE, c, TAIL_INNER, TAIL_OUTER, sweep);
		// the tail points straight behind him at a turn of 0, and turns toward his right as the turn grows
		double behind = Danger.facing(body) + Math.PI;
		Set<UUID> passed = new HashSet<>();
		for (int t = sweep; t <= swept; t++) {
			double from = SWEEP_FROM + (SWEEP_TO - SWEEP_FROM) * (t - 1 - sweep) / (double) (swept - sweep);
			double to = SWEEP_FROM + (SWEEP_TO - SWEEP_FROM) * (t - sweep) / (double) (swept - sweep);
			schedule(t, () -> {
				for (LivingEntity e : victims(c, TAIL_OUTER + 0.5)) {
					Vec3 d = e.position().subtract(c);
					double r = Math.sqrt(d.x * d.x + d.z * d.z);
					if (r < TAIL_INNER - 0.5 || passed.contains(e.getUUID())) {
						continue;
					}
					// the turn at which the tail is over this soul
					double turn = Math.toDegrees(wrap(behind - Math.atan2(d.z, d.x)));
					while (turn < SWEEP_FROM) {
						turn += 360;
					}
					if (turn >= from && turn <= to) {
						passed.add(e.getUUID());
						if (e.onGround() && e.hurtServer(level, Danger.unblockable(level, body), TAIL_DAMAGE)) {
							fling(e, c, 1.2, 0.4);
						}
					}
				}
				double a = behind - Math.toRadians(to);
				for (double rr = TAIL_INNER; rr <= TAIL_OUTER; rr += 1.5) {
					double x = c.x + Math.cos(a) * rr;
					double z = c.z + Math.sin(a) * rr;
					level.sendParticles(ParticleTypes.SWEEP_ATTACK, x, groundY(x, z) + 0.4, z, 1, 0, 0, 0, 0);
				}
			});
		}
		schedule(sweep, () -> level.playSound(null, body.blockPosition(), SoundEvents.BREEZE_WHIRL, SoundSource.HOSTILE, 2.5f, 0.6f));
		schedule(swept + 2, () -> minos.warn(BossEntity.Warning.NONE));
		return MinosAnim.TAIL.ticks + 25;
	}

	/** Sentence: red. A circle follows the condemned, stops, and the storm falls there. In his fury, on everyone. */
	private int sentence(Mob body, LivingEntity target) {
		MinosEntity minos = minos(body);
		face(body, target);
		minos.play(MinosAnim.SENTENCE);
		int lock = MinosAnim.SENTENCE.at("lock");
		int hit = MinosAnim.SENTENCE.at("hit");
		List<LivingEntity> condemned = enraged ? targets() : List.of(target);
		say(enraged ? "ALL of you! Down, down, DOWN!" : "Circle " + (2 + level.getRandom().nextInt(8)) + "! Down with thee!");
		Map<UUID, Vec3> where = new HashMap<>();
		for (LivingEntity e : condemned) {
			Danger.follow(level, Danger.Colour.RED, e, SENTENCE_RADIUS, lock, hit);
			if (e instanceof ServerPlayer p) {
				p.sendOverlayMessage(Component.literal("Minos sentenced you: get out of the red circle!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
			}
			schedule(lock, () -> where.put(e.getUUID(), e.position()));
		}
		schedule(hit, () -> {
			for (Vec3 at : where.values()) {
				level.playSound(null, at.x, at.y, at.z, SoundEvents.ELYTRA_FLYING, SoundSource.HOSTILE, 3.0f, 0.5f);
				level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 1, at.z, 80, 1.2, 2, 1.2, 0.2);
				for (LivingEntity e : victims(at, SENTENCE_RADIUS + 0.3)) {
					if (e.hurtServer(level, Danger.unblockable(level, body), SENTENCE_DAMAGE)) {
						e.push((level.getRandom().nextDouble() - 0.5) * 1.6, 1.2, (level.getRandom().nextDouble() - 0.5) * 1.6);
						Feedback.syncMotion(e);
					}
				}
			}
		});
		return MinosAnim.SENTENCE.ticks + 20;
	}

	/** The Coil: a red line. The tail lunges along it and closes on whoever is still there. */
	private int coil(Mob body, LivingEntity target) {
		MinosEntity minos = minos(body);
		face(body, target);
		lockedUntil = tick + MinosAnim.GRAB.ticks;
		double angle = Danger.facing(body);
		Vec3 c = body.position();
		int lunge = MinosAnim.GRAB.at("lunge");
		int caught = MinosAnim.GRAB.at("catch");
		minos.play(MinosAnim.GRAB);
		say("Around and around... how many circles for thee?");
		Danger.line(level, Danger.Colour.RED, c, angle, COIL_REACH, COIL_WIDTH, caught);
		schedule(lunge, () -> level.playSound(null, body.blockPosition(), SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.HOSTILE, 2.0f, 0.5f));
		schedule(caught, () -> {
			LivingEntity victim = null;
			double best = Double.MAX_VALUE;
			for (LivingEntity e : victims(c, COIL_REACH + 1)) {
				Vec3 d = e.position().subtract(c);
				double along = d.x * Math.cos(angle) + d.z * Math.sin(angle);
				double across = Math.abs(-d.x * Math.sin(angle) + d.z * Math.cos(angle));
				if (along > 0 && along <= COIL_REACH && across <= COIL_WIDTH + 0.4 && along < best) {
					best = along;
					victim = e;
				}
			}
			if (victim == null) {
				minos.play(MinosAnim.RELEASE);
				return;
			}
			coiled = victim.getUUID();
			coilTicks = 0;
			coilDamage = 0;
			minos.play(MinosAnim.HOLD);
			tip(victim.getName().getString() + " is in Minos's coils! Hit him hard to make him let go.");
		});
		return MinosAnim.GRAB.ticks + 40;
	}

	// ------------------------------------------------------------------ every tick

	@Override
	protected void everyTick(Mob body) {
		MinosEntity minos = minos(body);
		if (!enraged && body.getHealth() <= body.getMaxHealth() / 2) {
			rage(minos);
		}
		if (staggerUntil >= 0 && tick >= staggerUntil) {
			staggerUntil = -1;
			setBase(body, Attributes.ARMOR, 10.0);
		}
		if (tick >= lockedUntil && staggerUntil < 0) {
			LivingEntity nearest = nearest(body, targets());
			if (nearest != null) {
				turnToward(body, nearest, 4.0f);
			}
		}
		if (coiled == null) {
			return;
		}
		if (!(level.getEntity(coiled) instanceof LivingEntity victim) || !victim.isAlive() || victim.isSpectator()) {
			letGo(minos, null, false);
			return;
		}
		coilTicks++;
		// held in the hoop of his tail, in front of him and a little to his right
		double facing = Danger.facing(body);
		Vec3 hold = body.position().add(Math.cos(facing) * 3.4 + Math.cos(facing - Math.PI / 2) * 1.2, 1.0,
				Math.sin(facing) * 3.4 + Math.sin(facing - Math.PI / 2) * 1.2);
		victim.setDeltaMovement(hold.subtract(victim.position()).scale(0.35));
		Feedback.syncMotion(victim);
		victim.resetFallDistance();
		if (coilTicks % 10 == 0) {
			victim.hurtServer(level, Danger.unblockable(level, body), 2.0f);
			victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 3));
			level.sendParticles(JUDGEMENT, victim.getX(), victim.getY() + 1, victim.getZ(), 16, 0.5, 0.6, 0.5, 0);
		}
		if (coilDamage >= COIL_BREAK) {
			letGo(minos, victim, true);
		} else if (coilTicks >= COIL_TICKS) {
			letGo(minos, victim, false);
		}
	}

	private void letGo(MinosEntity minos, @Nullable LivingEntity victim, boolean forced) {
		coiled = null;
		minos.play(MinosAnim.RELEASE);
		if (victim != null) {
			fling(victim, minos.position(), 1.3, 0.5);
			say(forced ? "AAH! Insolent!" : "Go, then. Thy circle awaits.");
		}
	}

	/** At half his health: he rises in fury. */
	private void rage(MinosEntity minos) {
		enraged = true;
		if (coiled != null && level.getEntity(coiled) instanceof LivingEntity victim) {
			letGo(minos, victim, true);
		}
		minos.warn(BossEntity.Warning.NONE);
		minos.play(MinosAnim.PHASE);
		shield(minos, MinosAnim.PHASE.ticks);
		delayNextAttack(MinosAnim.PHASE.ticks + 10);
		lockedUntil = tick + MinosAnim.PHASE.ticks;
		say("ENOUGH! I have judged a thousand thousand souls. I will judge you ALL!");
		tip("Minos is furious: he sentences everyone at once now, and strikes faster.");
		schedule(MinosAnim.PHASE.at("roar"), () -> {
			level.playSound(null, minos.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0f, 0.5f);
			level.sendParticles(JUDGEMENT, minos.getX(), minos.getY() + 4, minos.getZ(), 120, 2.5, 2.5, 2.5, 0.05);
		});
	}

	/** Blocked in the white flash: he reels, his guard down, open to blows. */
	@Override
	protected void onParried(Mob body, ServerPlayer by) {
		MinosEntity minos = minos(body);
		minos.warn(BossEntity.Warning.NONE);
		minos.play(MinosAnim.STAGGER);
		staggerUntil = tick + MinosAnim.STAGGER.ticks;
		lockedUntil = staggerUntil;
		setBase(body, Attributes.ARMOR, 0.0);
		delayNextAttack(MinosAnim.STAGGER.ticks + 15);
		level.playSound(null, body.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.5f, 0.6f);
		level.sendParticles(ParticleTypes.CRIT, body.getX(), body.getY() + 4, body.getZ(), 40, 1.0, 1.5, 1.0, 0.4);
		say("Thou... DARE?!");
		by.sendOverlayMessage(Component.literal("Parried! Minos is staggered: hit him now!").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
	}

	@Override
	protected void onHurt(float amount) {
		if (coiled != null) {
			coilDamage += amount;
		}
	}
}
