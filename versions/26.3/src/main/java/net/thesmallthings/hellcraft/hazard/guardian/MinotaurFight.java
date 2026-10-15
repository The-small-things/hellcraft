package net.thesmallthings.hellcraft.hazard.guardian;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.cutscene.Cutscene;
import net.thesmallthings.hellcraft.cutscene.Cutscenes;
import net.thesmallthings.hellcraft.entity.HellEntities;
import net.thesmallthings.hellcraft.entity.boss.BossEntity;
import net.thesmallthings.hellcraft.entity.boss.MinotaurEntity;
import net.thesmallthings.hellcraft.entity.boss.gen.MinotaurAnim;
import net.thesmallthings.hellcraft.hazard.Danger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The Minotaur (Inferno XII), the Infamy of Crete, on the shattered slope at the top of the seventh circle. He hunts
 * you round his lair, and every blow speaks the danger language (hazard/Danger):
 * <ul>
 *     <li><b>The Horns</b> (a white wedge): he draws his head back, flashes white, and thrusts. Block it as it comes
 *     and he reels onto one knee, open to blows.</li>
 *     <li><b>The Charge</b> (a red line): he paws the ground and charges along it. Step aside; if he hits rock at the
 *     end of it, he stuns himself.</li>
 *     <li><b>The Stomp</b> (a red circle round him): up on one hoof, then down. Get away from him.</li>
 *     <li><b>The Frenzy</b> (a purple ring round him): he whirls with his fists out. It can't be blocked: jump it, or
 *     keep out of the ring.</li>
 * </ul>
 * At half his health he bites his own arm in his rage and bellows; from then on he charges twice in a row.
 */
final class MinotaurFight extends GuardianFight {
	private static final double GORE_REACH = 6.5;
	private static final double GORE_ARC = 0.7;
	private static final float GORE_DAMAGE = 12.0f;
	private static final double CHARGE_REACH = 24.0;
	private static final double CHARGE_WIDTH = 1.8;
	private static final int CHARGE_WARN = 24;
	private static final float CHARGE_DAMAGE = 10.0f;
	private static final double STOMP_RADIUS = 7.0;
	private static final float STOMP_DAMAGE = 9.0f;
	private static final double FRENZY_INNER = 1.5;
	private static final double FRENZY_OUTER = 6.5;
	private static final float FRENZY_DAMAGE = 9.0f;
	/** Close enough to gore: he stops walking. */
	private static final double CLOSE = 4.5;

	private int cycle;
	private boolean enraged;
	private int lockedUntil;
	private int staggerUntil = -1;
	private Vec3 chargeDir;
	private int chargeUntil = -1;
	private final Set<UUID> trampled = new HashSet<>();

	MinotaurFight(GuardianContext ctx) {
		super(ctx);
	}

	@Override
	protected Mob createBody() {
		MinotaurEntity body = HellEntities.MINOTAUR.create(level, EntitySpawnReason.EVENT);
		if (body == null) {
			throw new IllegalStateException("could not create the Minotaur");
		}
		return body;
	}

	private static MinotaurEntity minotaur(Mob body) {
		return (MinotaurEntity) body;
	}

	@Override
	public List<String> attacks() {
		return List.of("gore", "charge", "stomp", "frenzy");
	}

	@Override
	protected String introLine() {
		return "HRRRAAAGH!";
	}

	@Override
	protected String deathLine() {
		return "*The Infamy of Crete crashes down on the rocks of the slope.*";
	}

	@Override
	protected Cutscene introCutscene() {
		return Cutscenes.MINOTAUR_INTRO;
	}

	@Override
	protected int intro(Mob body) {
		LivingEntity first = nearest(body, targets());
		if (first != null) {
			face(body, first);
		}
		minotaur(body).play(MinotaurAnim.INTRO);
		lockedUntil = tick + MinotaurAnim.INTRO.ticks;
		schedule(MinotaurAnim.INTRO.at("roar"), () -> level.playSound(null, body.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0f, 0.5f));
		return MinotaurAnim.INTRO.ticks;
	}

	// ------------------------------------------------------------------ choosing

	@Override
	protected int nextAttack(Mob body, List<LivingEntity> targets) {
		LivingEntity nearest = nearest(body, targets);
		if (nearest == null) {
			return 20;
		}
		String attack;
		if (nearest.distanceTo(body) > 9) {
			// out of reach: he charges at them
			attack = "charge";
		} else {
			String[] order = enraged ? new String[]{"gore", "stomp", "charge", "frenzy", "gore", "charge"} : new String[]{"gore", "stomp", "gore", "frenzy", "charge"};
			attack = order[cycle++ % order.length];
		}
		int cooldown = perform(attack, body, nearest);
		return enraged ? Math.max(cooldown * 3 / 4, 25) : cooldown;
	}

	@Override
	protected int perform(String attack, Mob body, LivingEntity target) {
		return switch (attack) {
			case "gore" -> gore(body, target);
			case "charge" -> charge(body, target, enraged);
			case "stomp" -> stomp(body);
			case "frenzy" -> frenzy(body);
			default -> -1;
		};
	}

	// ------------------------------------------------------------------ the attacks

	/** The Horns: a white wedge. Block the thrust and he reels onto one knee. */
	private int gore(Mob body, LivingEntity target) {
		MinotaurEntity minotaur = minotaur(body);
		stop(body);
		face(body, target);
		double angle = Danger.facing(body);
		Vec3 c = body.position();
		int flash = MinotaurAnim.GORE.at("flash");
		int hit = MinotaurAnim.GORE.at("hit");
		lockedUntil = tick + MinotaurAnim.GORE.ticks;
		minotaur.play(MinotaurAnim.GORE);
		schedule(flash, () -> {
			minotaur.warn(BossEntity.Warning.WHITE);
			openParry(hit - flash + 3);
			Danger.cone(level, Danger.Colour.WHITE, c, angle, GORE_REACH, GORE_ARC, hit - flash);
		});
		schedule(hit, () -> {
			minotaur.warn(BossEntity.Warning.NONE);
			level.playSound(null, body.blockPosition(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 2.5f, 0.6f);
			for (LivingEntity e : inWedge(c, angle, GORE_REACH, GORE_ARC)) {
				if (e.hurtServer(level, level.damageSources().mobAttack(body), GORE_DAMAGE)) {
					fling(e, c, 0.6, 0.9);
				}
			}
		});
		return MinotaurAnim.GORE.ticks + 20;
	}

	/** The Charge: a red line, then he charges along it (twice in his fury). */
	private int charge(Mob body, LivingEntity target, boolean again) {
		MinotaurEntity minotaur = minotaur(body);
		stop(body);
		face(body, target);
		double angle = Danger.facing(body);
		Vec3 c = body.position();
		int go = MinotaurAnim.CHARGE.at("go");
		lockedUntil = tick + go + 24;
		minotaur.play(MinotaurAnim.CHARGE);
		say("HRRR!");
		tip("He paws the ground: a red line. Step aside before he charges!");
		Danger.line(level, Danger.Colour.RED, c, angle, CHARGE_REACH, CHARGE_WIDTH, go);
		schedule(go, () -> {
			level.playSound(null, body.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.5f, 0.9f);
			chargeDir = new Vec3(Math.cos(angle), 0, Math.sin(angle));
			chargeUntil = tick + 20;
			trampled.clear();
		});
		if (again) {
			// in his fury he turns at the end of it and comes again
			schedule(go + 26, () -> {
				LivingEntity next = nearest(body, targets());
				if (next != null && staggerUntil < 0) {
					charge(body, next, false);
				}
			});
		}
		return go + (again ? 70 : 40);
	}

	/** The Stomp: a red circle round him. Get away from him before his hoof comes down. */
	private int stomp(Mob body) {
		MinotaurEntity minotaur = minotaur(body);
		stop(body);
		Vec3 c = body.position();
		int slam = MinotaurAnim.STOMP.at("slam");
		lockedUntil = tick + MinotaurAnim.STOMP.ticks;
		minotaur.play(MinotaurAnim.STOMP);
		tip("He rears onto one hoof: get out of the red circle!");
		Danger.circle(level, Danger.Colour.RED, c, STOMP_RADIUS, slam);
		schedule(slam, () -> {
			level.playSound(null, body.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.0f, 0.5f);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BLACKSTONE.defaultBlockState()), c.x, c.y + 0.3, c.z, 200,
					STOMP_RADIUS * 0.5, 0.3, STOMP_RADIUS * 0.5, 0.2);
			for (LivingEntity e : victims(c, STOMP_RADIUS + 0.3)) {
				if (e.hurtServer(level, Danger.unblockable(level, body), STOMP_DAMAGE)) {
					fling(e, c, 1.0, 0.7);
				}
			}
		});
		return MinotaurAnim.STOMP.ticks + 20;
	}

	/** The Frenzy: a purple ring. He whirls with his fists out: jump them, or keep out of the ring. */
	private int frenzy(Mob body) {
		MinotaurEntity minotaur = minotaur(body);
		stop(body);
		Vec3 c = body.position();
		int spin = MinotaurAnim.FRENZY.at("spin");
		int spun = MinotaurAnim.FRENZY.at("spun");
		lockedUntil = tick + MinotaurAnim.FRENZY.ticks;
		minotaur.play(MinotaurAnim.FRENZY);
		minotaur.warn(BossEntity.Warning.PURPLE);
		tip("He whirls with his fists out: JUMP as they reach you, or keep out of the purple ring!");
		Danger.ring(level, Danger.Colour.PURPLE, c, FRENZY_INNER, FRENZY_OUTER, spin);
		sweepRound(c, Danger.facing(body), FRENZY_INNER, FRENZY_OUTER, spin, spun, true, e -> {
			if (e.hurtServer(level, Danger.unblockable(level, body), FRENZY_DAMAGE)) {
				fling(e, c, 1.2, 0.4);
			}
		});
		schedule(spin, () -> level.playSound(null, body.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.5f, 0.5f));
		schedule(spun + 2, () -> minotaur.warn(BossEntity.Warning.NONE));
		return MinotaurAnim.FRENZY.ticks + 20;
	}

	// ------------------------------------------------------------------ every tick

	@Override
	protected void everyTick(Mob body) {
		MinotaurEntity minotaur = minotaur(body);
		if (!enraged && body.getHealth() <= body.getMaxHealth() / 2) {
			rage(minotaur);
		}
		if (staggerUntil >= 0 && tick >= staggerUntil) {
			staggerUntil = -1;
			setBase(body, Attributes.ARMOR, 10.0);
		}
		if (chargeUntil >= 0) {
			charging(minotaur);
		} else if (tick >= lockedUntil && staggerUntil < 0) {
			LivingEntity nearest = nearest(body, targets());
			if (nearest != null) {
				if (nearest.distanceTo(body) > CLOSE) {
					body.getNavigation().moveTo(nearest, 1.0);
				} else {
					stop(body);
					turnToward(body, nearest, 6.0f);
				}
			}
		}
	}

	private void charging(MinotaurEntity minotaur) {
		if (tick >= chargeUntil) {
			chargeUntil = -1;
			minotaur.setDeltaMovement(Vec3.ZERO);
			return;
		}
		minotaur.setDeltaMovement(chargeDir.x * 1.1, minotaur.getDeltaMovement().y, chargeDir.z * 1.1);
		for (LivingEntity e : victims(minotaur.position(), 2.6)) {
			if (trampled.add(e.getUUID()) && e.hurtServer(level, Danger.unblockable(level, minotaur), CHARGE_DAMAGE)) {
				fling(e, minotaur.position(), 1.5, 0.6);
			}
		}
		if (tick % 3 == 0) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BLACKSTONE.defaultBlockState()), minotaur.getX(), minotaur.getY() + 0.2,
					minotaur.getZ(), 20, 1.2, 0.2, 1.2, 0.1);
		}
		// into the rock: he stuns himself
		if (minotaur.horizontalCollision && tick > chargeUntil - 18) {
			chargeUntil = -1;
			minotaur.setDeltaMovement(Vec3.ZERO);
			level.playSound(null, minotaur.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 2.0f, 0.4f);
			stagger(minotaur, null);
		}
	}

	/** At half his health: he bites his own arm in his rage, then bellows. */
	private void rage(MinotaurEntity minotaur) {
		enraged = true;
		stop(minotaur);
		chargeUntil = -1;
		minotaur.warn(BossEntity.Warning.NONE);
		minotaur.play(MinotaurAnim.PHASE);
		shield(minotaur, MinotaurAnim.PHASE.ticks);
		delayNextAttack(MinotaurAnim.PHASE.ticks + 10);
		lockedUntil = tick + MinotaurAnim.PHASE.ticks;
		say("*He bit himself, like one whom anger inwardly consumes*");
		tip("The Minotaur is furious: from now on he charges twice in a row.");
		schedule(MinotaurAnim.PHASE.at("roar"), () -> level.playSound(null, minotaur.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0f, 0.4f));
	}

	@Override
	protected void onParried(Mob body, ServerPlayer by) {
		stagger(minotaur(body), by);
	}

	/** Blocked, or charged into the rock: he goes down on one knee, shaking his head, open to blows. */
	private void stagger(MinotaurEntity minotaur, ServerPlayer by) {
		stop(minotaur);
		minotaur.warn(BossEntity.Warning.NONE);
		minotaur.play(MinotaurAnim.STAGGER);
		staggerUntil = tick + MinotaurAnim.STAGGER.ticks;
		lockedUntil = staggerUntil;
		setBase(minotaur, Attributes.ARMOR, 0.0);
		delayNextAttack(MinotaurAnim.STAGGER.ticks + 15);
		minotaur.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, MinotaurAnim.STAGGER.ticks, 9, false, false));
		level.sendParticles(ParticleTypes.CRIT, minotaur.getX(), minotaur.getY() + 3, minotaur.getZ(), 40, 1.5, 1.0, 1.5, 0.4);
		if (by != null) {
			by.sendOverlayMessage(Component.literal("Parried! The Minotaur reels: hit him now!").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
		} else {
			tip("He ran into the rock and stunned himself: hit him now!");
		}
	}

	private static void stop(Mob body) {
		body.getNavigation().stop();
	}
}
