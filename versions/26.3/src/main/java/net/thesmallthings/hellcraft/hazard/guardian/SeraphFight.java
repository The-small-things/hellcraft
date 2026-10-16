package net.thesmallthings.hellcraft.hazard.guardian;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.thesmallthings.hellcraft.blood.Relics;
import net.thesmallthings.hellcraft.cutscene.Cutscene;
import net.thesmallthings.hellcraft.cutscene.Cutscenes;
import net.thesmallthings.hellcraft.entity.HellEntities;
import net.thesmallthings.hellcraft.entity.boss.BossEntity;
import net.thesmallthings.hellcraft.entity.boss.SeraphEntity;
import net.thesmallthings.hellcraft.entity.boss.gen.SeraphAnim;
import net.thesmallthings.hellcraft.hazard.Danger;
import net.thesmallthings.hellcraft.net.Dialogue;
import net.thesmallthings.hellcraft.util.Journey;
import net.thesmallthings.hellcraft.world.Heaven;
import net.thesmallthings.hellcraft.world.ParadisoGeometry;
import net.thesmallthings.hellcraft.world.ParadisoTrials;

import java.util.ArrayList;
import java.util.List;

/**
 * The Seraph (Isaiah VI; Paradiso XXVIII), on the crystal floor of the Primum Mobile: Paradiso's guardian and the last
 * trial. It is not your enemy but your examiner, and it doesn't die: beaten, it bows to you and goes up into the light,
 * and the column to the Empyrean opens. Its blows speak the danger language:
 * <ul>
 *     <li><b>The Wing-blow</b> (a white wedge): its great wings drawn back, a white flash, then swept forward. Block it
 *     as it lands and the Seraph sinks, reeling.</li>
 *     <li><b>Fire from Above</b> (red circles): fire falls on the circles round each of you.</li>
 *     <li><b>The Live Coal</b> (a red circle on you): it hurls the coal from the altar; it bursts there.</li>
 *     <li><b>The Gloria</b> (a red ring round it): it unveils its face, and everything not close beside it burns. Run IN.</li>
 *     <li><b>The Angelic Guard</b>: angels come down to test you. Kill them.</li>
 * </ul>
 * At half its strength all six wings flare: from then on <b>the turning heavens</b> (a purple ring) sweep round the floor
 * between its blows; jump the beam as it passes.
 */
final class SeraphFight extends GuardianFight {
	private static final double WING_REACH = 8.0;
	private static final double WING_ARC = 0.85;
	private static final float WING_DAMAGE = 14.0f;
	private static final double FIRE_RADIUS = 2.4;
	private static final float FIRE_DAMAGE = 9.0f;
	private static final double COAL_RADIUS = 3.2;
	private static final float COAL_DAMAGE = 12.0f;
	/** The Gloria spares only those within this distance of the Seraph. */
	private static final double GLORIA_SAFE = 5.0;
	private static final double GLORIA_REACH = 26.0;
	private static final float GLORIA_DAMAGE = 16.0f;
	private static final double TURN_INNER = 3.0;
	private static final double TURN_OUTER = 22.0;
	private static final float TURN_DAMAGE = 8.0f;
	/** Close enough to strike with its wings: it stops drifting. */
	private static final double CLOSE = 5.5;

	private int cycle;
	private boolean enraged;
	private int lockedUntil;
	private int staggerUntil = -1;
	private int nextTurn = -1;

	SeraphFight(GuardianContext ctx) {
		super(ctx);
	}

	@Override
	protected Mob createBody() {
		SeraphEntity body = HellEntities.SERAPH.create(level, EntitySpawnReason.EVENT);
		if (body == null) {
			throw new IllegalStateException("could not create the Seraph");
		}
		return body;
	}

	private static SeraphEntity seraph(Mob body) {
		return (SeraphEntity) body;
	}

	@Override
	public List<String> attacks() {
		return List.of("wings", "fire", "coal", "gloria", "host", "turning");
	}

	@Override
	protected String introLine() {
		return "Holy, holy, holy. You who came up out of the dark: show me what you have become.";
	}

	@Override
	protected String deathLine() {
		return "*The Seraph bows to you and rises into the light. The column to the Empyrean is open.*";
	}

	@Override
	protected Cutscene introCutscene() {
		return Cutscenes.SERAPH_INTRO;
	}

	/** The crystal floor (the heightmap would find whatever floats higher). */
	@Override
	protected double groundY(double x, double z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos((int) Math.floor(x), lair.getY() + 4, (int) Math.floor(z));
		for (int i = 0; i < 10; i++) {
			if (!level.getBlockState(pos.below()).isAir()) {
				return pos.getY();
			}
			pos.move(0, -1, 0);
		}
		return lair.getY();
	}

	@Override
	protected int intro(Mob body) {
		LivingEntity first = nearest(body, targets());
		if (first != null) {
			face(body, first);
		}
		seraph(body).play(SeraphAnim.INTRO);
		lockedUntil = tick + SeraphAnim.INTRO.ticks;
		schedule(SeraphAnim.INTRO.at("land"), () -> {
			level.playSound(null, body.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 3.0f, 0.8f);
			level.sendParticles(ParticleTypes.END_ROD, body.getX(), body.getY() + 1, body.getZ(), 80, 3, 0.5, 3, 0.05);
		});
		schedule(SeraphAnim.INTRO.at("cry"), () -> level.playSound(null, body.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 3.0f, 0.6f));
		return SeraphAnim.INTRO.ticks;
	}

	// ------------------------------------------------------------------ choosing

	@Override
	protected int nextAttack(Mob body, List<LivingEntity> targets) {
		LivingEntity nearest = nearest(body, targets);
		if (nearest == null) {
			return 20;
		}
		String attack;
		if (nearest.distanceTo(body) > 11) {
			// out of reach of its wings: fire and the coal find them
			attack = cycle++ % 2 == 0 ? "coal" : "fire";
		} else {
			String[] order = enraged ? new String[]{"wings", "gloria", "coal", "wings", "fire", "host"}
					: new String[]{"wings", "fire", "wings", "coal", "gloria", "host"};
			attack = order[cycle++ % order.length];
		}
		int cooldown = perform(attack, body, nearest);
		return enraged ? Math.max(cooldown * 3 / 4, 25) : cooldown;
	}

	@Override
	protected int perform(String attack, Mob body, LivingEntity target) {
		return switch (attack) {
			case "wings" -> wings(body, target);
			case "fire" -> fire(body);
			case "coal" -> coal(body, target);
			case "gloria" -> gloria(body);
			case "host" -> host(body);
			case "turning" -> turning(body);
			default -> -1;
		};
	}

	// ------------------------------------------------------------------ the attacks

	/** The Wing-blow: a white wedge. Block it as it lands and the Seraph reels. */
	private int wings(Mob body, LivingEntity target) {
		SeraphEntity seraph = seraph(body);
		stop(body);
		face(body, target);
		double angle = Danger.facing(body);
		Vec3 c = body.position();
		int flash = SeraphAnim.WINGS.at("flash");
		int hit = SeraphAnim.WINGS.at("hit");
		lockedUntil = tick + SeraphAnim.WINGS.ticks;
		seraph.play(SeraphAnim.WINGS);
		schedule(flash, () -> {
			seraph.warn(BossEntity.Warning.WHITE);
			openParry(hit - flash + 3);
			Danger.cone(level, Danger.Colour.WHITE, c, angle, WING_REACH, WING_ARC, hit - flash);
		});
		schedule(hit, () -> {
			seraph.warn(BossEntity.Warning.NONE);
			level.playSound(null, body.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 3.0f, 0.8f);
			for (int i = 2; i <= 7; i++) {
				Vec3 p = c.add(Math.cos(angle) * i, 1.0, Math.sin(angle) * i);
				level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 4, 1.0, 0.4, 1.0, 0.02);
			}
			for (LivingEntity e : inWedge(c, angle, WING_REACH, WING_ARC)) {
				if (e.hurtServer(level, level.damageSources().mobAttack(body), WING_DAMAGE)) {
					fling(e, c, 1.0, 0.5);
				}
			}
		});
		return SeraphAnim.WINGS.ticks + 20;
	}

	/** Fire from Above: red circles round each of you; fire falls there. */
	private int fire(Mob body) {
		SeraphEntity seraph = seraph(body);
		stop(body);
		int call = SeraphAnim.FIRE.at("call");
		lockedUntil = tick + SeraphAnim.FIRE.ticks;
		seraph.play(SeraphAnim.FIRE);
		say("Burn, and be made clean.");
		tip("Fire falls on the red circles: get out of them!");
		List<Vec3> marks = new ArrayList<>();
		for (LivingEntity t : targets()) {
			marks.add(new Vec3(t.getX(), groundY(t.getX(), t.getZ()), t.getZ()));
			for (int i = 0; i < 2 + (enraged ? 1 : 0); i++) {
				double a = level.getRandom().nextDouble() * Math.PI * 2;
				Vec3 m = t.position().add(Math.cos(a) * 4.5, 0, Math.sin(a) * 4.5);
				marks.add(new Vec3(m.x, groundY(m.x, m.z), m.z));
			}
		}
		int fall = call + 16;
		for (Vec3 m : marks) {
			Danger.circle(level, Danger.Colour.RED, m, FIRE_RADIUS, fall);
		}
		schedule(call, () -> level.playSound(null, body.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 3.0f, 0.5f));
		schedule(fall, () -> {
			Mob b = body();
			for (Vec3 m : marks) {
				level.sendParticles(ParticleTypes.FLAME, m.x, m.y + 4, m.z, 40, 0.6, 2.0, 0.6, 0.02);
				level.sendParticles(ParticleTypes.END_ROD, m.x, m.y + 0.3, m.z, 10, 0.8, 0.2, 0.8, 0.05);
				for (LivingEntity e : victims(m, FIRE_RADIUS + 0.2)) {
					if (e.hurtServer(level, b != null ? level.damageSources().mobAttack(b) : level.damageSources().inFire(), FIRE_DAMAGE)) {
						e.igniteForSeconds(3.0f);
					}
				}
			}
		});
		return SeraphAnim.FIRE.ticks + 24;
	}

	/** The Live Coal: a red circle on its target; the coal bursts there. */
	private int coal(Mob body, LivingEntity target) {
		SeraphEntity seraph = seraph(body);
		stop(body);
		face(body, target);
		Vec3 mark = new Vec3(target.getX(), groundY(target.getX(), target.getZ()), target.getZ());
		int thrown = SeraphAnim.COAL.at("throw");
		int lands = thrown + 10;
		lockedUntil = tick + SeraphAnim.COAL.ticks;
		seraph.play(SeraphAnim.COAL);
		say("Lo, this hath touched thy lips.");
		tip("It throws the live coal at the red circle: get out of it!");
		Danger.circle(level, Danger.Colour.RED, mark, COAL_RADIUS, lands);
		schedule(thrown, () -> {
			Vec3 from = body.position().add(0, 4, 0);
			for (int i = 0; i <= 10; i++) {
				double t = i / 10.0;
				Vec3 p = from.lerp(mark, t).add(0, Math.sin(t * Math.PI) * 3, 0);
				level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0);
			}
			level.playSound(null, body.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 2.5f, 0.6f);
		});
		schedule(lands, () -> {
			Mob b = body();
			level.playSound(null, BlockPos.containing(mark), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.0f, 1.1f);
			level.sendParticles(ParticleTypes.FLAME, mark.x, mark.y + 0.5, mark.z, 60, 1.5, 0.5, 1.5, 0.1);
			level.sendParticles(ParticleTypes.LAVA, mark.x, mark.y + 0.5, mark.z, 10, 1.0, 0.2, 1.0, 0);
			for (LivingEntity e : victims(mark, COAL_RADIUS + 0.3)) {
				if (e.hurtServer(level, b != null ? level.damageSources().mobAttack(b) : level.damageSources().inFire(), COAL_DAMAGE)) {
					e.igniteForSeconds(4.0f);
					fling(e, mark, 0.9, 0.6);
				}
			}
		});
		return SeraphAnim.COAL.ticks + 20;
	}

	/** The Gloria: a red ring round it, everywhere but close beside it. Run in. */
	private int gloria(Mob body) {
		SeraphEntity seraph = seraph(body);
		stop(body);
		Vec3 c = body.position();
		int unveil = SeraphAnim.GLORIA.at("unveil");
		int blaze = SeraphAnim.GLORIA.at("blaze");
		lockedUntil = tick + SeraphAnim.GLORIA.ticks;
		seraph.play(SeraphAnim.GLORIA);
		say("Look upon me, if you can.");
		tip("It unveils its face: everything outside the small circle round it will burn. Run IN, close to it!");
		Danger.ring(level, Danger.Colour.RED, c, GLORIA_SAFE, GLORIA_REACH, blaze);
		schedule(unveil, () -> level.playSound(null, body.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 3.0f, 0.5f));
		schedule(blaze, () -> {
			Mob b = body();
			level.playSound(null, body.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.HOSTILE, 2.0f, 0.5f);
			ringParticles(ParticleTypes.END_ROD, c, 10, 40);
			ringParticles(ParticleTypes.FLAME, c, 18, 60);
			for (LivingEntity e : inRing(c, GLORIA_SAFE, GLORIA_REACH)) {
				if (e.hurtServer(level, b != null ? Danger.unblockable(level, b) : level.damageSources().inFire(), GLORIA_DAMAGE)) {
					e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
				}
			}
		});
		return SeraphAnim.GLORIA.ticks + 24;
	}

	/** The Angelic Guard: an angel for each of you comes down to test you. */
	private int host(Mob body) {
		SeraphEntity seraph = seraph(body);
		stop(body);
		lockedUntil = tick + SeraphAnim.HOST.ticks;
		seraph.play(SeraphAnim.HOST);
		say("Come down, you who never fell.");
		schedule(SeraphAnim.HOST.at("host"), () -> {
			level.playSound(null, body.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 2.5f, 1.4f);
			tip("The Seraph called down its Angelic Guard: kill them!");
			Heaven.angels(level, lair, enraged ? 2 : 1, TAG);
		});
		return SeraphAnim.HOST.ticks + 40;
	}

	/** The turning heavens (after its flare): a purple ring; a beam sweeps round the floor. Jump it as it passes. */
	private int turning(Mob body) {
		SeraphEntity seraph = seraph(body);
		stop(body);
		Vec3 c = body.position();
		int turn = SeraphAnim.TURNING.at("turn");
		int done = SeraphAnim.TURNING.ticks - 6;
		lockedUntil = tick + SeraphAnim.TURNING.ticks;
		seraph.play(SeraphAnim.TURNING);
		seraph.warn(BossEntity.Warning.PURPLE);
		tip("The heavens turn: a beam sweeps round the floor. JUMP as it reaches you!");
		Danger.ring(level, Danger.Colour.PURPLE, c, TURN_INNER, TURN_OUTER, turn);
		double start = Danger.facing(body);
		for (int t = turn; t <= done; t += 2) {
			double a = start + 2 * Math.PI * (t - turn) / (done - turn);
			schedule(t, () -> {
				for (int r = (int) TURN_INNER; r <= TURN_OUTER; r += 2) {
					level.sendParticles(ParticleTypes.END_ROD, c.x + Math.cos(a) * r, c.y + 0.6, c.z + Math.sin(a) * r, 1, 0.05, 0.1, 0.05, 0);
				}
			});
		}
		sweepRound(c, start, TURN_INNER, TURN_OUTER, turn, done, true, e -> {
			Mob b = body();
			if (e.hurtServer(level, b != null ? Danger.unblockable(level, b) : level.damageSources().magic(), TURN_DAMAGE)) {
				fling(e, c, 0.8, 0.4);
			}
		});
		schedule(done + 2, () -> seraph.warn(BossEntity.Warning.NONE));
		return SeraphAnim.TURNING.ticks + 20;
	}

	// ------------------------------------------------------------------ every tick

	@Override
	protected void everyTick(Mob body) {
		SeraphEntity seraph = seraph(body);
		if (!enraged && body.getHealth() <= body.getMaxHealth() / 2) {
			flare(seraph);
		}
		if (staggerUntil >= 0 && tick >= staggerUntil) {
			staggerUntil = -1;
			setBase(body, Attributes.ARMOR, 10.0);
		}
		if (enraged && nextTurn >= 0 && tick >= nextTurn && tick >= lockedUntil && staggerUntil < 0) {
			nextTurn = tick + 280;
			delayNextAttack(SeraphAnim.TURNING.ticks + 10);
			turning(body);
		}
		if (tick >= lockedUntil && staggerUntil < 0) {
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

	/** At half its strength: all six wings flare and the ring of fire spins; the heavens begin to turn. */
	private void flare(SeraphEntity seraph) {
		enraged = true;
		stop(seraph);
		seraph.warn(BossEntity.Warning.NONE);
		seraph.play(SeraphAnim.PHASE);
		shield(seraph, SeraphAnim.PHASE.ticks);
		delayNextAttack(SeraphAnim.PHASE.ticks + 10);
		lockedUntil = tick + SeraphAnim.PHASE.ticks;
		nextTurn = tick + SeraphAnim.PHASE.ticks + 60;
		say("You have climbed far. Now see the heavens turn.");
		schedule(SeraphAnim.PHASE.at("flare"), () -> {
			level.playSound(null, seraph.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 3.0f, 1.4f);
			level.sendParticles(ParticleTypes.END_ROD, seraph.getX(), seraph.getY() + 4, seraph.getZ(), 200, 4, 3, 4, 0.1);
			tip("From now on the heavens turn between its blows: a beam sweeps the floor. Watch for the purple ring, and jump the beam.");
		});
	}

	@Override
	protected void onParried(Mob body, ServerPlayer by) {
		SeraphEntity seraph = seraph(body);
		stop(seraph);
		seraph.warn(BossEntity.Warning.NONE);
		seraph.play(SeraphAnim.STAGGER);
		staggerUntil = tick + SeraphAnim.STAGGER.ticks;
		lockedUntil = staggerUntil;
		setBase(seraph, Attributes.ARMOR, 0.0);
		delayNextAttack(SeraphAnim.STAGGER.ticks + 15);
		seraph.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SeraphAnim.STAGGER.ticks, 9, false, false));
		level.sendParticles(ParticleTypes.CRIT, seraph.getX(), seraph.getY() + 3, seraph.getZ(), 40, 1.5, 1.0, 1.5, 0.4);
		by.sendOverlayMessage(Component.literal("Parried! The Seraph reels: hit it now!").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
	}

	/** Each victor passes the Primum Mobile's trial and is given a Halo. */
	@Override
	protected void onVictor(ServerPlayer player) {
		ParadisoTrials.pass(player, ParadisoGeometry.Sphere.PRIMUM_MOBILE);
		Relics.give(player, Relics.Relic.HALO);
		Journey.award(player, "journey/seraph");
		Dialogue.notice(player, Component.literal("The Seraph gave you a Halo. The column of light from the Primum Mobile now lifts you "
				+ "to the Empyrean and the Celestial Rose.").withStyle(ChatFormatting.GOLD));
	}

	private static void stop(Mob body) {
		body.getNavigation().stop();
	}
}
