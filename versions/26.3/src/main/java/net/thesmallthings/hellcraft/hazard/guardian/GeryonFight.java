package net.thesmallthings.hellcraft.hazard.guardian;

import net.minecraft.ChatFormatting;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.cutscene.Cutscene;
import net.thesmallthings.hellcraft.cutscene.Cutscenes;
import net.thesmallthings.hellcraft.entity.HellEntities;
import net.thesmallthings.hellcraft.entity.boss.BossEntity;
import net.thesmallthings.hellcraft.entity.boss.GeryonCarrier;
import net.thesmallthings.hellcraft.entity.boss.GeryonEntity;
import net.thesmallthings.hellcraft.entity.boss.gen.GeryonAnim;
import net.thesmallthings.hellcraft.hazard.Danger;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Geryon (Inferno XVII), the Image of Fraud, swimming in the air at the brink of the great cliff down to Malebolge:
 * the kindly face of a just man on a serpent's painted body, with a scorpion's sting. In the danger language
 * (hazard/Danger):
 * <ul>
 *     <li><b>The Sting</b> (a white circle that follows you): he hangs over you and his tail arches, flashes white,
 *     and strikes. Block it and he sags to the ground, dazed, open to blows.</li>
 *     <li><b>The False Face</b> (purple lines): he smiles, and two more of him shimmer into the air; all three dive
 *     along purple lines. It can't be blocked: get off the lines. A blow pops a false one.</li>
 *     <li><b>The Coils</b> (a red ring round the lair's edge): he wraps his body round the lair and squeezes. Get to
 *     the middle.</li>
 *     <li><b>The Dive</b> (a red line): he swoops low across the lair. Step aside.</li>
 * </ul>
 * At half his health every knot and circlet on him blazes; from then on venom drips where you stand (red circles).
 * Beaten, he doesn't die: he bows his head, and from then on he'll fly any soul down the cliff (GeryonCarrier), as he
 * carried Dante and Virgil.
 */
final class GeryonFight extends GuardianFight {
	private static final double HEIGHT = 4.5;
	/** After each blow he sinks to just above the ground for this long, within sword's reach. */
	private static final int REST = 50;
	private static final double SWIM = 7.0;
	private static final double STING_RADIUS = 2.4;
	private static final float STING_DAMAGE = 11.0f;
	private static final double FALSE_REACH = 22.0;
	private static final double FALSE_WIDTH = 1.6;
	private static final float FALSE_DAMAGE = 8.0f;
	private static final double COIL_INNER = 9.0;
	private static final double COIL_OUTER = 21.0;
	private static final float COIL_DAMAGE = 6.0f;
	private static final double DIVE_REACH = 26.0;
	private static final double DIVE_WIDTH = 2.0;
	private static final float DIVE_DAMAGE = 9.0f;
	private static final double VENOM_RADIUS = 2.0;

	private int cycle;
	private boolean enraged;
	private int lockedUntil;
	private int staggerUntil = -1;
	/** Where he's flying to for his blow, until when; null: he swims his circle. */
	@Nullable
	private Vec3 goal;
	private int goalUntil;
	private int restUntil = -1;
	private boolean toldRest;
	private Vec3 diveFrom;
	private Vec3 diveTo;
	private int diveStart = -1;
	private int diveEnd;
	private final List<UUID> falses = new ArrayList<>();
	private final Set<UUID> struck = new HashSet<>();

	GeryonFight(GuardianContext ctx) {
		super(ctx);
	}

	@Override
	protected Mob createBody() {
		GeryonEntity body = HellEntities.GERYON.create(level, EntitySpawnReason.EVENT);
		if (body == null) {
			throw new IllegalStateException("could not create Geryon");
		}
		// a tamed Geryon from an earlier fight goes back to the abyss while he's awake
		for (GeryonCarrier carrier : level.getEntitiesOfClass(GeryonCarrier.class, new AABB(lair).inflate(64))) {
			carrier.discard();
		}
		return body;
	}

	private static GeryonEntity geryon(Mob body) {
		return (GeryonEntity) body;
	}

	@Override
	protected boolean flies() {
		return true;
	}

	@Override
	public List<String> attacks() {
		return List.of("sting", "falseface", "coil", "dive");
	}

	@Override
	protected String introLine() {
		return "Peace, pilgrims. I mean you no harm. Come closer.";
	}

	@Override
	protected String deathLine() {
		return "*Geryon bows his kindly head. He will carry you down.*";
	}

	@Override
	protected Cutscene introCutscene() {
		return Cutscenes.GERYON_INTRO;
	}

	private Vec3 centre() {
		return new Vec3(lair.getX() + 0.5, lair.getY(), lair.getZ() + 0.5);
	}

	@Override
	protected int intro(Mob body) {
		Vec3 c = centre();
		body.setPos(c.x, c.y + HEIGHT, c.z);
		LivingEntity first = nearest(body, targets());
		if (first != null) {
			face(body, first);
		}
		geryon(body).play(GeryonAnim.INTRO);
		lockedUntil = tick + GeryonAnim.INTRO.ticks;
		schedule(GeryonAnim.INTRO.at("roar"), () -> level.playSound(null, body.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 2.5f, 1.4f));
		return GeryonAnim.INTRO.ticks;
	}

	// ------------------------------------------------------------------ choosing

	@Override
	protected int nextAttack(Mob body, List<LivingEntity> targets) {
		String[] order = enraged ? new String[]{"sting", "dive", "falseface", "sting", "coil", "dive"} : new String[]{"sting", "dive", "sting", "falseface", "coil"};
		String attack = order[cycle++ % order.length];
		LivingEntity target = targets.get(level.getRandom().nextInt(targets.size()));
		int cooldown = perform(attack, body, target);
		// spent, he sinks low for a while: the time to hit him
		int rest = Math.max(lockedUntil, tick + 20);
		schedule(rest - tick, () -> {
			restUntil = tick + REST;
			if (!toldRest) {
				toldRest = true;
				tip("Geryon sinks low after his attacks: that's your chance to hit him.");
			}
		});
		cooldown = Math.max(cooldown, rest - tick + REST);
		return enraged ? Math.max(cooldown * 3 / 4, 25) : cooldown;
	}

	@Override
	protected int perform(String attack, Mob body, LivingEntity target) {
		return switch (attack) {
			case "sting" -> sting(body, target);
			case "falseface" -> falseFace(body);
			case "coil" -> coil(body);
			case "dive" -> dive(body, target);
			default -> -1;
		};
	}

	// ------------------------------------------------------------------ the attacks

	/** The Sting: a white circle follows a soul; his tail strikes down on it. Block it and he sags to the ground. */
	private int sting(Mob body, LivingEntity target) {
		GeryonEntity geryon = geryon(body);
		int flash = GeryonAnim.STING.at("flash");
		int hit = GeryonAnim.STING.at("hit");
		lockedUntil = tick + GeryonAnim.STING.ticks;
		goal = target.position().add(0, 3.2, 0);
		goalUntil = tick + hit;
		geryon.play(GeryonAnim.STING);
		say("Be still. It will only sting once.");
		Danger.follow(level, Danger.Colour.WHITE, target, STING_RADIUS, hit - 6, hit);
		Vec3[] at = new Vec3[1];
		schedule(flash, () -> {
			geryon.warn(BossEntity.Warning.WHITE);
			openParry(hit - flash + 3);
		});
		schedule(hit - 6, () -> at[0] = target.position());
		schedule(hit, () -> {
			geryon.warn(BossEntity.Warning.NONE);
			if (at[0] == null) {
				return;
			}
			level.playSound(null, body.blockPosition(), SoundEvents.BEE_STING, SoundSource.HOSTILE, 2.5f, 0.5f);
			for (LivingEntity e : victims(at[0], STING_RADIUS + 0.3)) {
				if (e.hurtServer(level, level.damageSources().mobAttack(body), STING_DAMAGE)) {
					e.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
				}
			}
		});
		return GeryonAnim.STING.ticks + 20;
	}

	/** The False Face: two more of him shimmer up; all three dive along purple lines. A blow pops a false one. */
	private int falseFace(Mob body) {
		GeryonEntity geryon = geryon(body);
		int split = GeryonAnim.FALSEFACE.at("split");
		lockedUntil = tick + GeryonAnim.FALSEFACE.ticks + 40;
		geryon.play(GeryonAnim.FALSEFACE);
		geryon.warn(BossEntity.Warning.PURPLE);
		say("Which face is mine? Which is thine?");
		tip("False Geryons! All of them dive along the purple lines: get off them. Hit a false one and it pops.");
		schedule(split, () -> {
			Vec3 c = centre();
			for (int i = 0; i < 2; i++) {
				GeryonEntity copy = HellEntities.GERYON.create(level, EntitySpawnReason.EVENT);
				if (copy == null) {
					continue;
				}
				double a = Danger.facing(body) + (i == 0 ? 2.1 : -2.1);
				copy.snapTo(c.x + Math.cos(a) * SWIM, body.getY(), c.z + Math.sin(a) * SWIM, body.getYRot(), 0);
				copy.makeFalse();
				copy.addTag(TAG);
				level.addFreshEntity(copy);
				falses.add(copy.getUUID());
				level.sendParticles(ParticleTypes.WITCH, copy.getX(), copy.getY() + 1, copy.getZ(), 40, 1.5, 1, 1.5, 0.1);
			}
			level.playSound(null, body.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 2.5f, 0.6f);
		});
		int warn = 30;
		schedule(split + 10, () -> {
			List<Mob> divers = new ArrayList<>();
			divers.add(body);
			for (UUID id : falses) {
				if (level.getEntity(id) instanceof Mob m && m.isAlive()) {
					divers.add(m);
				}
			}
			List<LivingEntity> targets = targets();
			for (int i = 0; i < divers.size(); i++) {
				Mob m = divers.get(i);
				LivingEntity t = targets.get(i % targets.size());
				Vec3 from = new Vec3(m.getX(), groundY(m.getX(), m.getZ()), m.getZ());
				double angle = Math.atan2(t.getZ() - from.z, t.getX() - from.x);
				Danger.line(level, Danger.Colour.PURPLE, from, angle, FALSE_REACH, FALSE_WIDTH, warn);
				schedule(warn, () -> {
					for (LivingEntity e : inBand(from, angle, FALSE_REACH, FALSE_WIDTH)) {
						e.hurtServer(level, Danger.unblockable(level, body), FALSE_DAMAGE);
					}
					for (double r = 1; r <= FALSE_REACH; r += 2) {
						level.sendParticles(ParticleTypes.WITCH, from.x + Math.cos(angle) * r, from.y + 1, from.z + Math.sin(angle) * r, 4, 0.3, 0.3, 0.3, 0.02);
					}
					m.setPos(from.x + Math.cos(angle) * FALSE_REACH * 0.6, m.getY(), from.z + Math.sin(angle) * FALSE_REACH * 0.6);
				});
			}
		});
		schedule(split + 10 + warn + 30, () -> {
			geryon.warn(BossEntity.Warning.NONE);
			clearFalses();
		});
		return GeryonAnim.FALSEFACE.ticks + warn + 50;
	}

	/** The Coils: a red ring round the lair's edge. Get to the middle. */
	private int coil(Mob body) {
		GeryonEntity geryon = geryon(body);
		int squeeze = GeryonAnim.COIL.at("squeeze");
		lockedUntil = tick + GeryonAnim.COIL.ticks;
		Vec3 c = centre();
		goal = c.add(0, 2, 0);
		goalUntil = tick + GeryonAnim.COIL.ticks;
		geryon.play(GeryonAnim.COIL);
		tip("He's coiling round the lair: get to the MIDDLE, out of the red ring!");
		Danger.ring(level, Danger.Colour.RED, c, COIL_INNER, COIL_OUTER, squeeze);
		schedule(squeeze, () -> {
			level.playSound(null, body.blockPosition(), SoundEvents.ELDER_GUARDIAN_HURT, SoundSource.HOSTILE, 2.5f, 0.5f);
			for (LivingEntity e : inRing(c, COIL_INNER, COIL_OUTER)) {
				if (e.hurtServer(level, Danger.unblockable(level, body), COIL_DAMAGE)) {
					e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2));
				}
			}
		});
		return GeryonAnim.COIL.ticks + 20;
	}

	/** The Dive: a red line across the lair; he swoops low along it. */
	private int dive(Mob body, LivingEntity target) {
		GeryonEntity geryon = geryon(body);
		int go = GeryonAnim.DIVE.at("go");
		int warn = go + 10;
		Vec3 from = new Vec3(body.getX(), groundY(body.getX(), body.getZ()), body.getZ());
		double angle = Math.atan2(target.getZ() - from.z, target.getX() - from.x);
		lockedUntil = tick + warn + 30;
		geryon.play(GeryonAnim.DIVE);
		face(body, target);
		Danger.line(level, Danger.Colour.RED, from, angle, DIVE_REACH, DIVE_WIDTH, warn);
		schedule(warn, () -> {
			diveFrom = body.position();
			diveTo = new Vec3(from.x + Math.cos(angle) * DIVE_REACH, from.y + 1.5, from.z + Math.sin(angle) * DIVE_REACH);
			diveStart = tick;
			diveEnd = tick + 18;
			struck.clear();
			level.playSound(null, body.blockPosition(), SoundEvents.PHANTOM_SWOOP, SoundSource.HOSTILE, 3.0f, 0.5f);
		});
		return warn + 45;
	}

	/** In his fury: venom drips where every soul stands (red circles). */
	private void venom(Mob body) {
		List<Vec3> spots = new ArrayList<>();
		for (LivingEntity e : targets()) {
			spots.add(e.position());
		}
		int land = 30;
		for (Vec3 s : spots) {
			Danger.circle(level, Danger.Colour.RED, s, VENOM_RADIUS, land);
		}
		schedule(land, () -> {
			for (Vec3 s : spots) {
				level.sendParticles(ParticleTypes.ITEM_SLIME, s.x, s.y + 1.5, s.z, 20, 0.6, 1.0, 0.6, 0.05);
				for (LivingEntity e : victims(s, VENOM_RADIUS + 0.3)) {
					if (e.hurtServer(level, Danger.unblockable(level, body), 4.0f)) {
						e.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
					}
				}
			}
		});
	}

	private void clearFalses() {
		for (UUID id : falses) {
			if (level.getEntity(id) instanceof Mob m) {
				level.sendParticles(ParticleTypes.WITCH, m.getX(), m.getY() + 1, m.getZ(), 30, 1.2, 0.8, 1.2, 0.05);
				m.discard();
			}
		}
		falses.clear();
	}

	// ------------------------------------------------------------------ every tick

	@Override
	protected void everyTick(Mob body) {
		GeryonEntity geryon = geryon(body);
		if (!enraged && body.getHealth() <= body.getMaxHealth() / 2) {
			rage(geryon);
		}
		if (staggerUntil >= 0 && tick >= staggerUntil) {
			staggerUntil = -1;
			setBase(body, Attributes.ARMOR, 8.0);
		}
		if (enraged && tick % 160 == 80 && staggerUntil < 0) {
			venom(body);
		}
		Vec3 c = centre();
		if (diveStart >= 0) {
			double t = Math.min(1.0, (tick - diveStart) / (double) (diveEnd - diveStart));
			body.setPos(diveFrom.lerp(diveTo, t));
			for (LivingEntity e : victims(body.position(), 3.0)) {
				if (struck.add(e.getUUID()) && e.hurtServer(level, Danger.unblockable(level, body), DIVE_DAMAGE)) {
					fling(e, body.position(), 1.3, 0.6);
				}
			}
			if (tick >= diveEnd) {
				diveStart = -1;
			}
			return;
		}
		Vec3 want;
		if (staggerUntil >= 0) {
			want = new Vec3(body.getX(), groundY(body.getX(), body.getZ()) + 0.3, body.getZ());
		} else if (goal != null && tick < goalUntil) {
			want = goal;
		} else if (tick < restUntil) {
			// resting, low over the ground, drifting toward the nearest soul
			LivingEntity near = nearest(body, targets());
			Vec3 to = near == null || near.distanceTo(body) < 4 ? body.position() : body.position().add(near.position().subtract(body.position()).normalize().scale(0.3));
			want = new Vec3(to.x, groundY(to.x, to.z) + 0.6, to.z);
		} else {
			goal = null;
			// swimming his slow circle over the lair
			double a = tick * 0.02;
			want = new Vec3(c.x + Math.cos(a) * SWIM, c.y + HEIGHT + Math.sin(tick * 0.05), c.z + Math.sin(a) * SWIM);
		}
		Vec3 step = want.subtract(body.position()).scale(0.1);
		if (step.length() > 0.7) {
			step = step.normalize().scale(0.7);
		}
		body.setPos(body.position().add(step));
		if (tick >= lockedUntil && staggerUntil < 0) {
			LivingEntity nearest = nearest(body, targets());
			if (nearest != null) {
				turnToward(body, nearest, 5.0f);
			}
		}
	}

	private void rage(GeryonEntity geryon) {
		enraged = true;
		geryon.warn(BossEntity.Warning.NONE);
		geryon.play(GeryonAnim.PHASE);
		shield(geryon, GeryonAnim.PHASE.ticks);
		delayNextAttack(GeryonAnim.PHASE.ticks + 10);
		lockedUntil = tick + GeryonAnim.PHASE.ticks;
		say("Enough of courtesy! See my true colours!");
		tip("Geryon is furious: venom will drip where you stand (red circles). Keep moving.");
		schedule(GeryonAnim.PHASE.at("roar"), () -> level.playSound(null, geryon.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 3.0f, 0.8f));
	}

	@Override
	protected void onParried(Mob body, ServerPlayer by) {
		GeryonEntity geryon = geryon(body);
		geryon.warn(BossEntity.Warning.NONE);
		geryon.play(GeryonAnim.STAGGER);
		staggerUntil = tick + GeryonAnim.STAGGER.ticks;
		lockedUntil = staggerUntil;
		goal = null;
		setBase(body, Attributes.ARMOR, 0.0);
		delayNextAttack(GeryonAnim.STAGGER.ticks + 15);
		level.playSound(null, body.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.5f, 0.5f);
		level.sendParticles(ParticleTypes.CRIT, body.getX(), body.getY() + 2, body.getZ(), 40, 1.5, 1.0, 1.5, 0.4);
		by.sendOverlayMessage(Component.literal("Parried! Geryon sags to the ground: hit him now!").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
	}

	/** Beaten, he bows his head and stays at the cliff to carry souls down (GeryonCarrier). */
	@Override
	protected void onEnd(boolean won) {
		clearFalses();
		if (!won) {
			return;
		}
		Mob body = body();
		Vec3 at = body != null ? body.position() : centre().add(0, 2, 0);
		GeryonCarrier.perch(level, lair, at);
		for (ServerPlayer p : audience()) {
			p.sendOverlayMessage(Component.literal("Geryon is tamed: right-click him to fly down the cliff to Malebolge.").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
		}
	}
}
