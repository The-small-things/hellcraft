package net.thesmallthings.hellcraft.hazard.guardian;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariant;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariants;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.cutscene.Cutscene;
import net.thesmallthings.hellcraft.cutscene.Cutscenes;
import net.thesmallthings.hellcraft.entity.HellEntities;
import net.thesmallthings.hellcraft.entity.boss.BossEntity;
import net.thesmallthings.hellcraft.entity.boss.CerberusEntity;
import net.thesmallthings.hellcraft.entity.boss.gen.CerberusAnim;
import net.thesmallthings.hellcraft.hazard.Danger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Cerberus (Inferno VI), the great worm of the third circle, who barks with three throats over the gluttons in the
 * rain. He hunts his prey round the lair (and the mire's mud takes whoever stands still), and every blow speaks the
 * danger language (hazard/Danger):
 * <ul>
 *     <li><b>Bite</b> (white): one head draws back and flashes white, and the ground before it shows where it will
 *     snap. Block it with a shield as it lands and he reels, open to blows, for two seconds.</li>
 *     <li><b>Three Maws</b> (a red cone): all three heads draw back together, then bite one after another in front of
 *     him. Get behind him.</li>
 *     <li><b>Filth</b> (red circles): the middle head retches the mire over the ground; the puddles slow and sicken
 *     whoever stands in them. Keep off them.</li>
 *     <li><b>Howl</b> (a purple ring): three throats at once. It can't be blocked: be out of the ring when it sounds,
 *     or be stunned.</li>
 * </ul>
 * At half his health he rears up and slams down (a red ring), and from then on he also charges along red lines.
 */
final class CerberusFight extends GuardianFight {
	private static final DustParticleOptions FILTH = new DustParticleOptions(0x3A2E14, 1.8f);
	private static final double BITE_REACH = 6.5;
	private static final double BITE_ARC = Math.toRadians(32);
	private static final float BITE_DAMAGE = 9.0f;
	/** How far to the side the side heads bite (radians from where he faces). */
	private static final double SIDE_HEAD = Math.toRadians(30);
	private static final double MAWS_REACH = 7.5;
	private static final double MAWS_ARC = Math.toRadians(55);
	private static final float MAW_DAMAGE = 5.0f;
	private static final double PUDDLE_RADIUS = 2.5;
	private static final int PUDDLE_TICKS = 140;
	private static final double HOWL_RADIUS = 9.0;
	private static final float HOWL_DAMAGE = 4.0f;
	private static final double SLAM_RADIUS = 8.0;
	private static final float SLAM_DAMAGE = 7.0f;
	private static final double CHARGE_REACH = 16.0;
	private static final double CHARGE_WIDTH = 2.0;
	private static final float CHARGE_DAMAGE = 9.0f;
	private static final int CHARGE_WARN = 22;
	/** He stalks his prey until it is this close. */
	private static final double CLOSE = 5.0;

	private int cycle;
	private boolean enraged;
	/** Until this tick he keeps the direction he chose (a telegraphed blow doesn't turn to follow you). */
	private int lockedUntil;
	private int staggerUntil = -1;
	private final List<Puddle> puddles = new ArrayList<>();
	/** A charge under way: its direction, and the tick it ends. */
	private Vec3 chargeDir;
	private int chargeUntil = -1;
	private final Set<UUID> trampled = new HashSet<>();

	private record Puddle(Vec3 at, int until) {
	}

	CerberusFight(GuardianContext ctx) {
		super(ctx);
	}

	@Override
	protected Mob createBody() {
		CerberusEntity body = HellEntities.CERBERUS.create(level, EntitySpawnReason.EVENT);
		if (body == null) {
			throw new IllegalStateException("could not create Cerberus");
		}
		return body;
	}

	private static CerberusEntity cerberus(Mob body) {
		return (CerberusEntity) body;
	}

	@Override
	public List<String> attacks() {
		return List.of("bite", "maws", "filth", "howl", "charge");
	}

	@Override
	protected String introLine() {
		return "*Three throats bay at once over the drowned and the rain* GRRRAAAAH!";
	}

	@Override
	protected String deathLine() {
		return "*The great worm falls, and its three mouths are stopped with earth.*";
	}

	@Override
	protected Cutscene introCutscene() {
		return Cutscenes.CERBERUS_INTRO;
	}

	@Override
	protected int intro(Mob body) {
		LivingEntity first = nearest(body, targets());
		if (first != null) {
			face(body, first);
		}
		cerberus(body).play(CerberusAnim.INTRO);
		lockedUntil = tick + CerberusAnim.INTRO.ticks;
		BlockParticleOption mud = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MUD.defaultBlockState());
		schedule(CerberusAnim.INTRO.at("rise"), () -> {
			level.playSound(null, body.blockPosition(), SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 2.0f, 0.6f);
			level.sendParticles(mud, body.getX(), body.getY() + 0.5, body.getZ(), 200, 2.5, 0.5, 2.5, 0.2);
		});
		schedule(CerberusAnim.INTRO.at("roar"), () -> level.playSound(null, body.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0f, 0.5f));
		return CerberusAnim.INTRO.ticks;
	}

	// ------------------------------------------------------------------ choosing

	@Override
	protected int nextAttack(Mob body, List<LivingEntity> targets) {
		LivingEntity nearest = nearest(body, targets);
		String[] order = enraged ? new String[]{"bite", "charge", "maws", "filth", "bite", "howl", "charge"}
				: new String[]{"bite", "maws", "filth", "bite", "howl"};
		String attack = order[cycle++ % order.length];
		double reach = nearest == null ? Double.MAX_VALUE : nearest.distanceTo(body);
		if ((attack.equals("bite") && reach > BITE_REACH + 1.5) || (attack.equals("maws") && reach > MAWS_REACH + 1)) {
			// too far to bite: he spits filth at them (or, in his fury, charges)
			attack = enraged ? "charge" : "filth";
		}
		int cooldown = perform(attack, body, nearest == null ? targets.get(0) : nearest);
		return enraged ? Math.max(cooldown * 7 / 10, 25) : cooldown;
	}

	@Override
	protected int perform(String attack, Mob body, LivingEntity target) {
		return switch (attack) {
			case "bite" -> bite(body, target);
			case "maws" -> maws(body, target);
			case "filth" -> filth(body, target);
			case "howl" -> howl(body);
			case "charge" -> charge(body, target);
			default -> -1;
		};
	}

	// ------------------------------------------------------------------ the attacks

	/** Bite: white. One head, the one nearest its prey; block it as it lands and he reels. */
	private int bite(Mob body, LivingEntity target) {
		CerberusEntity cerberus = cerberus(body);
		stop(body);
		face(body, target);
		// the head on the prey's side bites (a little to the left or right, or straight ahead)
		double facing = Danger.facing(body);
		Vec3 to = target.position().subtract(body.position());
		double off = wrap(Math.atan2(to.z, to.x) - facing);
		CerberusAnim anim = Math.abs(off) < SIDE_HEAD / 2 ? CerberusAnim.BITE_MID : off > 0 ? CerberusAnim.BITE_RIGHT : CerberusAnim.BITE_LEFT;
		double angle = facing + (anim == CerberusAnim.BITE_MID ? 0 : anim == CerberusAnim.BITE_RIGHT ? SIDE_HEAD : -SIDE_HEAD);
		Vec3 c = body.position();
		int flash = anim.at("flash");
		int bite = anim.at("bite");
		lockedUntil = tick + anim.ticks;
		cerberus.play(anim);
		level.playSound(null, body.blockPosition(), wolf(WolfSoundVariant.WolfSoundSet::growlSound), SoundSource.HOSTILE, 2.0f, 0.4f);
		schedule(flash, () -> {
			cerberus.warn(BossEntity.Warning.WHITE);
			openParry(bite - flash + 3);
			Danger.cone(level, Danger.Colour.WHITE, c, angle, BITE_REACH, BITE_ARC, bite - flash);
		});
		schedule(bite, () -> {
			cerberus.warn(BossEntity.Warning.NONE);
			level.playSound(null, body.blockPosition(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 2.0f, 0.6f);
			for (LivingEntity e : inCone(c, angle, BITE_REACH, BITE_ARC)) {
				if (e.hurtServer(level, level.damageSources().mobAttack(body), BITE_DAMAGE)) {
					fling(e, c, 0.9, 0.3);
				}
			}
		});
		return anim.ticks + 20;
	}

	/** Three Maws: a red cone. All three heads, one after another, in front of him: get behind him. */
	private int maws(Mob body, LivingEntity target) {
		CerberusEntity cerberus = cerberus(body);
		stop(body);
		face(body, target);
		double angle = Danger.facing(body);
		Vec3 c = body.position();
		lockedUntil = tick + CerberusAnim.MAWS.ticks;
		cerberus.play(CerberusAnim.MAWS);
		say("GRRR... GRRRR... GRRRRAH!");
		tip("Three Maws: all three heads bite in front of him. Get BEHIND him!");
		Danger.cone(level, Danger.Colour.RED, c, angle, MAWS_REACH, MAWS_ARC, CerberusAnim.MAWS.at("maw1"));
		for (String maw : new String[]{"maw1", "maw2", "maw3"}) {
			schedule(CerberusAnim.MAWS.at(maw), () -> {
				level.playSound(null, body.blockPosition(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 2.0f, 0.8f);
				for (LivingEntity e : inCone(c, angle, MAWS_REACH, MAWS_ARC)) {
					if (e.hurtServer(level, Danger.unblockable(level, body), MAW_DAMAGE)) {
						fling(e, c, 0.6, 0.25);
					}
				}
			});
		}
		return CerberusAnim.MAWS.ticks + 20;
	}

	/** Filth: red circles. The mire, retched over the ground: the puddles slow and sicken. */
	private int filth(Mob body, LivingEntity target) {
		CerberusEntity cerberus = cerberus(body);
		stop(body);
		face(body, target);
		lockedUntil = tick + CerberusAnim.FILTH.ticks;
		cerberus.play(CerberusAnim.FILTH);
		say("*The middle head retches up the filth of the circle*");
		tip("Filth! Get out of the red circles: the puddles slow and sicken you.");
		int land = CerberusAnim.FILTH.at("spew") + 12;
		List<Vec3> spots = new ArrayList<>();
		for (LivingEntity e : targets()) {
			spots.add(e.position());
		}
		for (int i = 0; i < 3 + (enraged ? 2 : 0); i++) {
			double a = level.getRandom().nextDouble() * Math.PI * 2;
			double r = 4 + level.getRandom().nextDouble() * 12;
			double x = lair.getX() + 0.5 + Math.cos(a) * r;
			double z = lair.getZ() + 0.5 + Math.sin(a) * r;
			spots.add(new Vec3(x, groundY(x, z), z));
		}
		for (Vec3 s : spots) {
			Danger.circle(level, Danger.Colour.RED, s, PUDDLE_RADIUS, land);
		}
		schedule(CerberusAnim.FILTH.at("spew"), () -> {
			level.playSound(null, body.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 2.5f, 0.4f);
			Vec3 mouth = body.position().add(Math.cos(Danger.facing(body)) * 4, 2.5, Math.sin(Danger.facing(body)) * 4);
			level.sendParticles(FILTH, mouth.x, mouth.y, mouth.z, 60, 0.6, 0.4, 0.6, 0.1);
		});
		schedule(land, () -> {
			for (Vec3 s : spots) {
				level.sendParticles(ParticleTypes.ITEM_SLIME, s.x, s.y + 0.5, s.z, 30, 1.2, 0.3, 1.2, 0.1);
				level.playSound(null, s.x, s.y, s.z, SoundEvents.MUD_BREAK, SoundSource.HOSTILE, 1.5f, 0.6f);
				puddles.add(new Puddle(s, tick + PUDDLE_TICKS));
			}
		});
		return CerberusAnim.FILTH.ticks + 20;
	}

	/** Howl: a purple ring. Three throats at once: it can't be blocked, so be out of reach. */
	private int howl(Mob body) {
		CerberusEntity cerberus = cerberus(body);
		stop(body);
		Vec3 c = body.position();
		int howl = CerberusAnim.HOWL.at("howl");
		lockedUntil = tick + CerberusAnim.HOWL.ticks;
		cerberus.play(CerberusAnim.HOWL);
		cerberus.warn(BossEntity.Warning.PURPLE);
		tip("He's about to HOWL: get out of the purple ring, it can't be blocked!");
		Danger.ring(level, Danger.Colour.PURPLE, c, 0, HOWL_RADIUS, howl);
		schedule(CerberusAnim.HOWL.at("breath"), () -> level.playSound(null, body.blockPosition(), wolf(WolfSoundVariant.WolfSoundSet::growlSound), SoundSource.HOSTILE, 2.5f, 0.3f));
		schedule(howl, () -> {
			cerberus.warn(BossEntity.Warning.NONE);
			say("AROOOOOOOOO!");
			level.playSound(null, body.blockPosition(), wolf(WolfSoundVariant.WolfSoundSet::ambientSound), SoundSource.HOSTILE, 4.0f, 0.4f);
			level.playSound(null, body.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0f, 0.6f);
			ringParticles(ParticleTypes.SONIC_BOOM, c, HOWL_RADIUS * 0.6, 10);
			for (LivingEntity e : victims(c, HOWL_RADIUS + 0.3)) {
				if (e.hurtServer(level, Danger.unblockable(level, body), HOWL_DAMAGE)) {
					e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 4));
					e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
					e.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 80, 0));
					fling(e, c, 1.0, 0.35);
				}
			}
		});
		return CerberusAnim.HOWL.ticks + 20;
	}

	/** In his fury: a red line, then he charges along it, trampling whoever is still there. */
	private int charge(Mob body, LivingEntity target) {
		if (!enraged) {
			return filth(body, target);
		}
		CerberusEntity cerberus = cerberus(body);
		stop(body);
		face(body, target);
		double angle = Danger.facing(body);
		Vec3 c = body.position();
		lockedUntil = tick + CHARGE_WARN + 20;
		say("*All three heads drop. He paws the mud.*");
		tip("A red line: he's about to charge along it. Step aside!");
		Danger.line(level, Danger.Colour.RED, c, angle, CHARGE_REACH, CHARGE_WIDTH, CHARGE_WARN);
		level.playSound(null, body.blockPosition(), SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 2.0f, 0.5f);
		schedule(CHARGE_WARN, () -> {
			cerberus.play(CerberusAnim.CHARGE);
			level.playSound(null, body.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.5f, 0.9f);
			chargeDir = new Vec3(Math.cos(angle), 0, Math.sin(angle));
			chargeUntil = tick + 16;
			trampled.clear();
		});
		return CHARGE_WARN + 45;
	}

	// ------------------------------------------------------------------ every tick

	@Override
	protected void everyTick(Mob body) {
		CerberusEntity cerberus = cerberus(body);
		if (!enraged && body.getHealth() <= body.getMaxHealth() / 2) {
			rage(cerberus);
		}
		if (staggerUntil >= 0 && tick >= staggerUntil) {
			staggerUntil = -1;
			setBase(body, Attributes.ARMOR, 8.0);
		}
		if (chargeUntil >= 0) {
			charging(cerberus);
		} else if (tick >= lockedUntil && staggerUntil < 0) {
			// he stalks the nearest of his prey
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
		puddles.removeIf(p -> p.until() < tick);
		if (tick % 5 == 0) {
			for (Puddle p : puddles) {
				level.sendParticles(FILTH, p.at().x, p.at().y + 0.1, p.at().z, 8, PUDDLE_RADIUS * 0.5, 0.05, PUDDLE_RADIUS * 0.5, 0);
				for (LivingEntity e : victims(p.at(), PUDDLE_RADIUS)) {
					e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 2));
					e.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 80, 0));
					if (tick % 20 == 0) {
						e.hurtServer(level, Danger.unblockable(level, body), 1.0f);
					}
				}
			}
		}
	}

	private void charging(CerberusEntity cerberus) {
		if (tick >= chargeUntil) {
			chargeUntil = -1;
			cerberus.setDeltaMovement(Vec3.ZERO);
			cerberus.play(CerberusAnim.IDLE);
			return;
		}
		cerberus.setDeltaMovement(chargeDir.x * 1.0, cerberus.getDeltaMovement().y, chargeDir.z * 1.0);
		for (LivingEntity e : victims(cerberus.position(), 3.0)) {
			if (trampled.add(e.getUUID()) && e.hurtServer(level, Danger.unblockable(level, cerberus), CHARGE_DAMAGE)) {
				fling(e, cerberus.position(), 1.4, 0.5);
			}
		}
		if (tick % 3 == 0) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MUD.defaultBlockState()), cerberus.getX(), cerberus.getY() + 0.2,
					cerberus.getZ(), 20, 1.5, 0.2, 1.5, 0.1);
		}
	}

	/** At half his health: he rears up, roars with all three throats and slams down (a red ring). */
	private void rage(CerberusEntity cerberus) {
		enraged = true;
		stop(cerberus);
		chargeUntil = -1;
		cerberus.warn(BossEntity.Warning.NONE);
		cerberus.play(CerberusAnim.PHASE);
		shield(cerberus, CerberusAnim.PHASE.ticks);
		delayNextAttack(CerberusAnim.PHASE.ticks + 10);
		lockedUntil = tick + CerberusAnim.PHASE.ticks;
		Vec3 c = cerberus.position();
		int roar = CerberusAnim.PHASE.at("roar");
		int slam = CerberusAnim.PHASE.at("slam");
		say("*He rears up out of the mire, and all three throats roar at once*");
		tip("Cerberus is furious: he slams the ground, and charges along red lines from now on.");
		schedule(roar, () -> {
			level.playSound(null, cerberus.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0f, 0.4f);
			Danger.ring(level, Danger.Colour.RED, c, 0, SLAM_RADIUS, slam - roar);
		});
		schedule(slam, () -> {
			level.playSound(null, cerberus.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.0f, 0.6f);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MUD.defaultBlockState()), c.x, c.y + 0.3, c.z, 200, SLAM_RADIUS * 0.5, 0.3,
					SLAM_RADIUS * 0.5, 0.2);
			for (LivingEntity e : victims(c, SLAM_RADIUS + 0.3)) {
				if (e.hurtServer(level, Danger.unblockable(level, cerberus), SLAM_DAMAGE)) {
					fling(e, c, 1.3, 0.6);
				}
			}
		});
	}

	/** Blocked in the white flash: the head that bit reels, and the beast with it, open to blows. */
	@Override
	protected void onParried(Mob body, ServerPlayer by) {
		CerberusEntity cerberus = cerberus(body);
		stop(body);
		cerberus.warn(BossEntity.Warning.NONE);
		cerberus.play(CerberusAnim.STAGGER);
		staggerUntil = tick + CerberusAnim.STAGGER.ticks;
		lockedUntil = staggerUntil;
		setBase(body, Attributes.ARMOR, 0.0);
		delayNextAttack(CerberusAnim.STAGGER.ticks + 15);
		level.playSound(null, body.blockPosition(), wolf(WolfSoundVariant.WolfSoundSet::hurtSound), SoundSource.HOSTILE, 2.0f, 0.4f);
		level.sendParticles(ParticleTypes.CRIT, body.getX(), body.getY() + 3, body.getZ(), 40, 1.5, 1.0, 1.5, 0.4);
		by.sendOverlayMessage(Component.literal("Parried! Cerberus reels: hit him now!").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
	}

	// ------------------------------------------------------------------ helpers

	/** The big wolf's voice, from three throats. */
	private static SoundEvent wolf(Function<WolfSoundVariant.WolfSoundSet, Holder<SoundEvent>> sound) {
		return sound.apply(SoundEvents.WOLF_SOUNDS.get(WolfSoundVariants.SoundSet.BIG).adultSounds()).value();
	}

	private static void stop(Mob body) {
		body.getNavigation().stop();
	}

	/** Who stands in the cone from c toward angle (with a little leeway). */
	private List<LivingEntity> inCone(Vec3 c, double angle, double reach, double arc) {
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : victims(c, reach + 0.5)) {
			Vec3 d = e.position().subtract(c);
			if (d.x * d.x + d.z * d.z < 2.0 || Math.abs(wrap(Math.atan2(d.z, d.x) - angle)) <= arc + 0.15) {
				out.add(e);
			}
		}
		return out;
	}
}
