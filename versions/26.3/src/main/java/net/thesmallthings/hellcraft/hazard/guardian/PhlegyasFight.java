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
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.cutscene.Cutscene;
import net.thesmallthings.hellcraft.cutscene.Cutscenes;
import net.thesmallthings.hellcraft.entity.HellEntities;
import net.thesmallthings.hellcraft.entity.boss.BossEntity;
import net.thesmallthings.hellcraft.entity.boss.PhlegyasEntity;
import net.thesmallthings.hellcraft.entity.boss.gen.PhlegyasAnim;
import net.thesmallthings.hellcraft.hazard.BossFx;
import net.thesmallthings.hellcraft.hazard.Danger;
import net.thesmallthings.hellcraft.util.Feedback;
import net.thesmallthings.hellcraft.world.Fen;
import net.thesmallthings.hellcraft.world.Ferries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Phlegyas (Inferno VIII), the ferryman of the Styx, who thinks every soul he carries is one more of the wrathful. The
 * east watchtower's twin beacons call him (Fen); he rises from the pool at the heart of his landing and fights from the
 * water, and every blow speaks the danger language (hazard/Danger):
 * <ul>
 *     <li><b>The Oar</b> (a white line): raised high, it flashes white, and the deck shows where it will fall. Block
 *     it with a shield as it lands and he reels, slumped over the deck, open to blows, for two seconds.</li>
 *     <li><b>The Sweep</b> (a purple ring): his oar skims round over the deck. It can't be blocked: jump it.</li>
 *     <li><b>The Wave</b> (a red ring round the deck's outer part): he beats the water and a wave washes over the
 *     landing's edge, throwing whoever is there into the Styx. Get in close to him.</li>
 *     <li><b>The Grab</b> (a red circle that follows a soul): he reaches for it and hurls it out into the marsh, where
 *     the sullen lurk. Keep moving.</li>
 * </ul>
 * At half his health he roars at the burning city, and from then on the sullen's hands burst up through the deck
 * (red circles) between his blows. Beaten, he sinks, and his ferry is free to carry pilgrims to Dis.
 */
final class PhlegyasFight extends GuardianFight {
	private static final double OAR_REACH = 18.0;
	private static final double OAR_WIDTH = 1.6;
	private static final float OAR_DAMAGE = 11.0f;
	private static final double SWEEP_INNER = Fen.POOL;
	/** The oar's reach, held out at arm's length (tools/art/phlegyas.py): the purple ring is exactly what it sweeps. */
	private static final double SWEEP_OUTER = 10.5;
	private static final float SWEEP_DAMAGE = 8.0f;
	private static final double WAVE_INNER = 10.0;
	private static final float WAVE_DAMAGE = 4.0f;
	private static final double GRAB_RADIUS = 2.5;
	private static final float GRAB_DAMAGE = 5.0f;
	private static final double HANDS_RADIUS = 2.0;
	private static final float HANDS_DAMAGE = 5.0f;

	private int cycle;
	private boolean enraged;
	private int lockedUntil;
	private int staggerUntil = -1;

	PhlegyasFight(GuardianContext ctx) {
		super(ctx);
	}

	@Override
	protected Mob createBody() {
		PhlegyasEntity body = HellEntities.PHLEGYAS.create(level, EntitySpawnReason.EVENT);
		if (body == null) {
			throw new IllegalStateException("could not create Phlegyas");
		}
		return body;
	}

	private static PhlegyasEntity phlegyas(Mob body) {
		return (PhlegyasEntity) body;
	}

	@Override
	public List<String> attacks() {
		return List.of("oar", "sweep", "wave", "grab", "hands");
	}

	@Override
	protected String introLine() {
		return "Now art thou arrived, fell soul!";
	}

	@Override
	protected String deathLine() {
		return "*The ferryman sinks into the mire, and his ferry drifts free.*";
	}

	@Override
	protected Cutscene introCutscene() {
		return Cutscenes.PHLEGYAS_INTRO;
	}

	@Override
	protected int intro(Mob body) {
		LivingEntity first = nearest(body, targets());
		if (first != null) {
			face(body, first);
		}
		phlegyas(body).play(PhlegyasAnim.INTRO);
		lockedUntil = tick + PhlegyasAnim.INTRO.ticks;
		schedule(PhlegyasAnim.INTRO.at("rise"), () -> {
			level.playSound(null, body.blockPosition(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 2.0f, 0.5f);
			BossFx.send(level, ParticleTypes.SPLASH, body.getX(), body.getY() + 3, body.getZ(), 300, 2.5, 1, 2.5, 0.3);
		});
		schedule(PhlegyasAnim.INTRO.at("roar"), () -> level.playSound(null, body.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0f, 0.4f));
		return PhlegyasAnim.INTRO.ticks;
	}

	// ------------------------------------------------------------------ choosing

	@Override
	protected int nextAttack(Mob body, List<LivingEntity> targets) {
		LivingEntity nearest = nearest(body, targets);
		String[] order = enraged ? new String[]{"oar", "hands", "sweep", "grab", "hands", "wave"} : new String[]{"oar", "sweep", "grab", "oar", "wave"};
		String attack = order[cycle++ % order.length];
		LivingEntity target = attack.equals("grab") ? targets.get(level.getRandom().nextInt(targets.size())) : nearest;
		int cooldown = perform(attack, body, target == null ? targets.get(0) : target);
		return enraged ? Math.max(cooldown * 7 / 10, 25) : cooldown;
	}

	@Override
	protected int perform(String attack, Mob body, LivingEntity target) {
		return switch (attack) {
			case "oar" -> oar(body, target);
			case "sweep" -> sweep(body);
			case "wave" -> wave(body);
			case "grab" -> grab(body, target);
			case "hands" -> hands(body);
			default -> -1;
		};
	}

	// ------------------------------------------------------------------ the attacks

	/** The Oar: a white line. Block it as it lands and he reels over the deck. */
	private int oar(Mob body, LivingEntity target) {
		PhlegyasEntity phlegyas = phlegyas(body);
		face(body, target);
		double angle = Danger.facing(body);
		Vec3 c = body.position();
		int flash = PhlegyasAnim.OAR.at("flash");
		int hit = PhlegyasAnim.OAR.at("hit");
		lockedUntil = tick + PhlegyasAnim.OAR.ticks;
		phlegyas.play(PhlegyasAnim.OAR);
		say("Into the mire with thee!");
		schedule(flash, () -> {
			phlegyas.warn(BossEntity.Warning.WHITE);
			openParry(hit - flash + 3);
			Danger.line(level, Danger.Colour.WHITE, c, angle, OAR_REACH, OAR_WIDTH, hit - flash);
		});
		schedule(hit, () -> {
			phlegyas.warn(BossEntity.Warning.NONE);
			level.playSound(null, body.blockPosition(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 2.5f, 0.5f);
			for (LivingEntity e : inLine(c, angle, OAR_REACH, OAR_WIDTH)) {
				if (e.hurtServer(level, level.damageSources().mobAttack(body), OAR_DAMAGE)) {
					fling(e, c, 0.8, 0.4);
				}
			}
			for (double r = 4; r <= OAR_REACH; r += 2) {
				BossFx.send(level, ParticleTypes.SPLASH, c.x + Math.cos(angle) * r, c.y + 3, c.z + Math.sin(angle) * r, 20, 0.6, 0.2, 0.6, 0.2);
			}
		});
		return PhlegyasAnim.OAR.ticks + 25;
	}

	/** The Sweep: a purple ring. His oar skims round over the deck: jump it as it passes. */
	private int sweep(Mob body) {
		PhlegyasEntity phlegyas = phlegyas(body);
		Vec3 c = body.position();
		int sweep = PhlegyasAnim.SWEEP.at("sweep");
		int swept = PhlegyasAnim.SWEEP.at("swept");
		lockedUntil = tick + PhlegyasAnim.SWEEP.ticks;
		phlegyas.play(PhlegyasAnim.SWEEP);
		phlegyas.warn(BossEntity.Warning.PURPLE);
		say("Row, row, ye wrathful!");
		tip("His oar sweeps the deck: JUMP as it reaches you!");
		Danger.ring(level, Danger.Colour.PURPLE, c, SWEEP_INNER, SWEEP_OUTER, sweep);
		double start = Danger.facing(body);
		Set<UUID> passed = new HashSet<>();
		for (int t = sweep; t <= swept; t++) {
			// how far round the oar has turned by the end of the tick before, and of this one
			double before = 2 * Math.PI * Math.max(0, t - 1 - sweep) / (swept - sweep);
			double now = 2 * Math.PI * (t - sweep) / (swept - sweep);
			boolean first = t == sweep;
			schedule(t, () -> {
				for (LivingEntity e : victims(c, SWEEP_OUTER + 0.5)) {
					Vec3 d = e.position().subtract(c);
					double r = Math.sqrt(d.x * d.x + d.z * d.z);
					if (r < SWEEP_INNER - 0.5 || passed.contains(e.getUUID())) {
						continue;
					}
					// the oar turns from where he faces, clockwise seen from above: it reaches this soul once it has
					// turned as far as the soul lies round from his front
					double needed = wrapPositive(start - Math.atan2(d.z, d.x));
					if ((first || needed > before) && needed <= now + 1e-6) {
						passed.add(e.getUUID());
						if (e.onGround() && e.hurtServer(level, Danger.unblockable(level, body), SWEEP_DAMAGE)) {
							fling(e, c, 1.1, 0.4);
						}
					}
				}
			});
		}
		schedule(sweep, () -> level.playSound(null, body.blockPosition(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.HOSTILE, 2.5f, 0.5f));
		schedule(swept + 2, () -> phlegyas.warn(BossEntity.Warning.NONE));
		return PhlegyasAnim.SWEEP.ticks + 25;
	}

	/** The Wave: a red ring over the deck's outer part. Get in close to him, or be thrown into the Styx. */
	private int wave(Mob body) {
		PhlegyasEntity phlegyas = phlegyas(body);
		Vec3 c = body.position();
		int slam = PhlegyasAnim.WAVE.at("slam");
		lockedUntil = tick + PhlegyasAnim.WAVE.ticks;
		phlegyas.play(PhlegyasAnim.WAVE);
		say("Drown in thy wrath!");
		tip("A wave will wash over the red ring: get in CLOSE to him!");
		Danger.ring(level, Danger.Colour.RED, c, WAVE_INNER, Fen.LANDING + 1, slam + 6);
		schedule(slam, () -> level.playSound(null, body.blockPosition(), SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 3.0f, 0.4f));
		schedule(slam + 6, () -> {
			level.playSound(null, body.blockPosition(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.HOSTILE, 3.0f, 0.6f);
			for (int i = 0; i < 24; i++) {
				double a = i * Math.PI / 12;
				double r = (WAVE_INNER + Fen.LANDING) / 2;
				BossFx.send(level, ParticleTypes.SPLASH, c.x + Math.cos(a) * r, c.y + 3, c.z + Math.sin(a) * r, 30, 2, 0.5, 2, 0.3);
			}
			for (LivingEntity e : victims(c, Fen.LANDING + 1.5)) {
				Vec3 d = e.position().subtract(c);
				if (Math.sqrt(d.x * d.x + d.z * d.z) >= WAVE_INNER - 0.3 && e.hurtServer(level, Danger.unblockable(level, body), WAVE_DAMAGE)) {
					// thrown out over the rail
					fling(e, c, 1.8, 0.6);
				}
			}
		});
		return PhlegyasAnim.WAVE.ticks + 25;
	}

	/** The Grab: a red circle follows a soul; he reaches for it and hurls it out into the marsh. */
	private int grab(Mob body, LivingEntity target) {
		PhlegyasEntity phlegyas = phlegyas(body);
		face(body, target);
		int grab = PhlegyasAnim.GRAB.at("grab");
		int throwAt = PhlegyasAnim.GRAB.at("throw");
		lockedUntil = tick + PhlegyasAnim.GRAB.ticks;
		phlegyas.play(PhlegyasAnim.GRAB);
		say("Thou! Thou art one of them!");
		Danger.follow(level, Danger.Colour.RED, target, GRAB_RADIUS, grab - 6, grab);
		if (target instanceof ServerPlayer p) {
			p.sendOverlayMessage(Component.literal("Phlegyas is reaching for you: get out of the red circle!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
		}
		Vec3[] at = new Vec3[1];
		schedule(grab - 6, () -> at[0] = target.position());
		List<LivingEntity> held = new ArrayList<>();
		schedule(grab, () -> {
			if (at[0] == null) {
				return;
			}
			for (LivingEntity e : victims(at[0], GRAB_RADIUS + 0.3)) {
				if (e.hurtServer(level, Danger.unblockable(level, body), GRAB_DAMAGE)) {
					held.add(e);
				}
			}
		});
		schedule(throwAt, () -> {
			// out over the rail, away from the road
			double a = Danger.facing(body) + Math.PI * (level.getRandom().nextBoolean() ? 0.6 : -0.6);
			for (LivingEntity e : held) {
				if (!e.isAlive()) {
					continue;
				}
				e.setDeltaMovement(Math.cos(a) * 2.2, 0.9, Math.sin(a) * 2.2);
				Feedback.syncMotion(e);
				level.playSound(null, e.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 2.0f, 0.6f);
				if (e instanceof ServerPlayer p) {
					p.sendOverlayMessage(Component.literal("Thrown into the Styx! Swim clear of the bubbles and get back on the planks.")
							.withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD));
				}
			}
		});
		return PhlegyasAnim.GRAB.ticks + 20;
	}

	/** In his fury: the sullen's hands burst up through the deck in red circles. */
	private int hands(Mob body) {
		if (!enraged) {
			return sweep(body);
		}
		say("Rise, ye sullen! Take them down!");
		tip("Hands are coming up through the red circles: get out of them!");
		List<Vec3> spots = new ArrayList<>();
		for (LivingEntity e : targets()) {
			spots.add(e.position());
		}
		for (int i = 0; i < 4; i++) {
			double a = level.getRandom().nextDouble() * Math.PI * 2;
			double r = Fen.POOL + 2 + level.getRandom().nextDouble() * (Fen.LANDING - Fen.POOL - 3);
			double x = lair.getX() + 0.5 + Math.cos(a) * r;
			double z = lair.getZ() + 0.5 + Math.sin(a) * r;
			spots.add(new Vec3(x, groundY(x, z), z));
		}
		int erupt = 30;
		for (Vec3 s : spots) {
			Danger.circle(level, Danger.Colour.RED, s, HANDS_RADIUS, erupt);
		}
		schedule(erupt, () -> {
			for (Vec3 s : spots) {
				level.playSound(null, s.x, s.y, s.z, SoundEvents.DROWNED_AMBIENT_WATER, SoundSource.HOSTILE, 1.5f, 0.5f);
				BossFx.send(level, ParticleTypes.BUBBLE_COLUMN_UP, s.x, s.y + 0.5, s.z, 30, 0.6, 0.6, 0.6, 0.1);
				for (LivingEntity e : victims(s, HANDS_RADIUS + 0.3)) {
					if (e.hurtServer(level, Danger.unblockable(level, body), HANDS_DAMAGE)) {
						e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 3));
					}
				}
			}
		});
		return erupt + 30;
	}

	// ------------------------------------------------------------------ every tick

	@Override
	protected void everyTick(Mob body) {
		PhlegyasEntity phlegyas = phlegyas(body);
		if (!enraged && body.getHealth() <= body.getMaxHealth() / 2) {
			rage(phlegyas);
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
		if (tick % 20 == 0) {
			BossFx.send(level, ParticleTypes.BUBBLE_POP, body.getX(), lair.getY() + 0.1, body.getZ(), 12, 2, 0, 2, 0);
		}
	}

	/** At half his health: he roars at the burning city and beats the water. */
	private void rage(PhlegyasEntity phlegyas) {
		enraged = true;
		phlegyas.warn(BossEntity.Warning.NONE);
		phlegyas.play(PhlegyasAnim.PHASE);
		shield(phlegyas, PhlegyasAnim.PHASE.ticks);
		delayNextAttack(PhlegyasAnim.PHASE.ticks + 10);
		lockedUntil = tick + PhlegyasAnim.PHASE.ticks;
		say("Dis! DIS! Thy gates shall never open for these!");
		tip("Phlegyas is furious: the sullen's hands will burst up through the deck in red circles.");
		schedule(PhlegyasAnim.PHASE.at("roar"), () -> level.playSound(null, phlegyas.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0f, 0.4f));
		schedule(PhlegyasAnim.PHASE.at("slam"), () -> {
			level.playSound(null, phlegyas.blockPosition(), SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 3.0f, 0.4f);
			BossFx.send(level, ParticleTypes.SPLASH, phlegyas.getX(), lair.getY() + 1, phlegyas.getZ(), 300, 6, 1, 6, 0.4);
		});
	}

	/** Blocked in the white flash: he reels, slumped over the deck. */
	@Override
	protected void onParried(Mob body, ServerPlayer by) {
		PhlegyasEntity phlegyas = phlegyas(body);
		phlegyas.warn(BossEntity.Warning.NONE);
		phlegyas.play(PhlegyasAnim.STAGGER);
		staggerUntil = tick + PhlegyasAnim.STAGGER.ticks;
		lockedUntil = staggerUntil;
		setBase(body, Attributes.ARMOR, 0.0);
		delayNextAttack(PhlegyasAnim.STAGGER.ticks + 15);
		level.playSound(null, body.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.5f, 0.5f);
		BossFx.send(level, ParticleTypes.CRIT, body.getX(), body.getY() + 6, body.getZ(), 40, 1.5, 1.5, 1.5, 0.4);
		say("Ngh... a pilgrim, then?");
		by.sendOverlayMessage(Component.literal("Parried! Phlegyas reels: hit him now!").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
	}

	/** The beacons go out; beaten, his ferry is left at the landing, free to carry pilgrims to Dis. */
	@Override
	protected void onEnd(boolean won) {
		Fen.douse(level, lairIndex);
		if (won) {
			Ferries.dockAtLanding(level, lairIndex);
		}
	}

	// ------------------------------------------------------------------ helpers

	private List<LivingEntity> inLine(Vec3 c, double angle, double reach, double halfWidth) {
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : victims(c, reach + 1)) {
			Vec3 d = e.position().subtract(c);
			double along = d.x * Math.cos(angle) + d.z * Math.sin(angle);
			double across = Math.abs(-d.x * Math.sin(angle) + d.z * Math.cos(angle));
			if (along > 0 && along <= reach && across <= halfWidth + 0.4) {
				out.add(e);
			}
		}
		return out;
	}

	/** An angle in [0, 2 PI). */
	private static double wrapPositive(double a) {
		double w = a % (2 * Math.PI);
		return w < 0 ? w + 2 * Math.PI : w;
	}
}
