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
import net.thesmallthings.hellcraft.blood.BloodAltar;
import net.thesmallthings.hellcraft.cutscene.Cutscene;
import net.thesmallthings.hellcraft.cutscene.Cutscenes;
import net.thesmallthings.hellcraft.entity.HellEntities;
import net.thesmallthings.hellcraft.entity.boss.BossEntity;
import net.thesmallthings.hellcraft.entity.boss.FuryEntity;
import net.thesmallthings.hellcraft.entity.boss.MedusaEntity;
import net.thesmallthings.hellcraft.entity.boss.gen.FuryAnim;
import net.thesmallthings.hellcraft.entity.boss.gen.MedusaAnim;
import net.thesmallthings.hellcraft.hazard.BossFx;
import net.thesmallthings.hellcraft.hazard.Danger;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Furies (Inferno IX), Megaera, Alecto and Tisiphone, who keep the gate of Dis. The three wheel over their lair
 * inside the wall and share one life (FuryEntity): a blow to any of them counts. One at a time they come down at you,
 * in the danger language (hazard/Danger):
 * <ul>
 *     <li><b>The Talons</b> (a white circle that follows you): she hovers over you, flashes white, and drops. Block it
 *     and she tumbles to the ground, open to blows.</li>
 *     <li><b>The Shriek</b> (a purple circle under her): it can't be blocked. Get out from under her.</li>
 *     <li><b>The Lash</b> (a red line): her serpent hair cracks like a scourge along it. Step aside.</li>
 *     <li><b>Tears of Blood</b> (red circles): they rend their breasts and blood rains where you stand. Move.</li>
 * </ul>
 * At half their life they cry "Venga Medusa!" and Medusa rises over the battlements of Dis. While her eyes are open,
 * look away from her: whoever looks at her turns to stone (the screen greys while you do).
 */
final class FuriesFight extends GuardianFight {
	private static final double ORBIT = 9.0;
	private static final double HEIGHT = 4.0;
	/** After her talons strike, a Fury crouches on the ground this long before she flies up: the time to hit her. */
	private static final int LANDED = 40;
	private static final double TALON_RADIUS = 2.4;
	private static final float TALON_DAMAGE = 10.0f;
	private static final double SHRIEK_RADIUS = 7.0;
	private static final float SHRIEK_DAMAGE = 7.0f;
	private static final double LASH_REACH = 14.0;
	private static final double LASH_WIDTH = 1.3;
	private static final float LASH_DAMAGE = 8.0f;
	private static final double TEAR_RADIUS = 2.2;
	private static final float TEAR_DAMAGE = 6.0f;
	/** Medusa: how wide her gaze catches you (radians off where you look), how long until you're stone. */
	private static final double GAZE_ARC = 0.55;
	private static final int STONE_AT = 24;
	private static final int GAZE_EVERY = 220;

	private final List<UUID> sisters = new ArrayList<>();
	@Nullable
	private UUID medusa;
	private int cycle;
	private boolean enraged;
	private int nextGaze;
	/** The Fury coming down on someone (she leaves the circle until her blow is done). */
	@Nullable
	private UUID striking;
	private int strikeUntil;
	private Vec3 diveTo;
	private int staggerUntil = -1;
	private final Map<UUID, Integer> stone = new HashMap<>();

	FuriesFight(GuardianContext ctx) {
		super(ctx);
	}

	@Override
	protected Mob createBody() {
		FuryEntity body = HellEntities.FURY.create(level, EntitySpawnReason.EVENT);
		if (body == null) {
			throw new IllegalStateException("could not create the Furies");
		}
		return body;
	}

	@Override
	protected boolean flies() {
		return true;
	}

	@Override
	public List<String> attacks() {
		return List.of("talons", "shriek", "lash", "tear", "medusa");
	}

	@Override
	protected String introLine() {
		return "Venga Medusa! We'll make him stone!";
	}

	@Override
	protected String deathLine() {
		return "*The Furies scatter shrieking into the smoke over Dis.*";
	}

	@Override
	protected Cutscene introCutscene() {
		return Cutscenes.FURIES_INTRO;
	}

	private Vec3 centre() {
		return new Vec3(lair.getX() + 0.5, lair.getY(), lair.getZ() + 0.5);
	}

	/** The three, leader first. */
	private List<FuryEntity> furies(Mob body) {
		List<FuryEntity> out = new ArrayList<>();
		out.add((FuryEntity) body);
		for (UUID id : sisters) {
			if (level.getEntity(id) instanceof FuryEntity f && f.isAlive()) {
				out.add(f);
			}
		}
		return out;
	}

	@Override
	protected int intro(Mob body) {
		FuryEntity leader = (FuryEntity) body;
		Vec3 c = centre();
		leader.setPos(c.x, c.y + HEIGHT, c.z);
		for (int i = 0; i < 2; i++) {
			FuryEntity sister = HellEntities.FURY.create(level, EntitySpawnReason.EVENT);
			if (sister == null) {
				continue;
			}
			double a = (i + 1) * Math.PI * 2 / 3;
			sister.snapTo(c.x + Math.cos(a) * ORBIT, c.y + HEIGHT, c.z + Math.sin(a) * ORBIT, 0, 0);
			sister.sisterOf(leader);
			sister.addTag(TAG);
			level.addFreshEntity(sister);
			sisters.add(sister.getUUID());
		}
		for (FuryEntity f : furies(body)) {
			f.play(FuryAnim.INTRO);
		}
		schedule(FuryAnim.INTRO.at("scream"), () -> level.playSound(null, body.blockPosition(), SoundEvents.GHAST_SCREAM, SoundSource.HOSTILE, 3.0f, 0.6f));
		return FuryAnim.INTRO.ticks;
	}

	// ------------------------------------------------------------------ choosing

	@Override
	protected int nextAttack(Mob body, List<LivingEntity> targets) {
		String[] order = enraged ? new String[]{"talons", "tear", "lash", "talons", "shriek", "lash"} : new String[]{"talons", "lash", "shriek", "talons", "tear"};
		String attack = order[cycle++ % order.length];
		LivingEntity target = targets.get(level.getRandom().nextInt(targets.size()));
		int cooldown = perform(attack, body, target);
		return enraged ? Math.max(cooldown * 7 / 10, 20) : cooldown;
	}

	@Override
	protected int perform(String attack, Mob body, LivingEntity target) {
		return switch (attack) {
			case "talons" -> talons(body, target);
			case "shriek" -> shriek(body, target);
			case "lash" -> lash(body, target);
			case "tear" -> tear(body);
			case "medusa" -> {
				if (medusa == null) {
					callMedusa(body);
				}
				yield 40;
			}
			default -> -1;
		};
	}

	/** The next Fury in turn (they take it in turns to come down). */
	private FuryEntity next(Mob body) {
		List<FuryEntity> all = furies(body);
		return all.get(cycle % all.size());
	}

	// ------------------------------------------------------------------ the attacks

	/** The Talons: a white circle follows a soul; she drops on it. Block and she tumbles to the ground. */
	private int talons(Mob body, LivingEntity target) {
		FuryEntity fury = next(body);
		int flash = FuryAnim.TALONS.at("flash");
		int hit = FuryAnim.TALONS.at("hit");
		striking = fury.getUUID();
		strikeUntil = tick + FuryAnim.TALONS.ticks + 10;
		diveTo = target.position().add(0, 2.5, 0);
		fury.play(FuryAnim.TALONS);
		Danger.follow(level, Danger.Colour.WHITE, target, TALON_RADIUS, hit - 6, hit);
		Vec3[] at = new Vec3[1];
		schedule(flash, () -> {
			fury.warn(BossEntity.Warning.WHITE);
			openParry(hit - flash + 3);
		});
		schedule(hit - 6, () -> {
			at[0] = target.position();
			diveTo = at[0].add(0, 1.0, 0);
		});
		schedule(hit, () -> {
			fury.warn(BossEntity.Warning.NONE);
			if (at[0] == null) {
				return;
			}
			level.playSound(null, fury.blockPosition(), SoundEvents.PHANTOM_BITE, SoundSource.HOSTILE, 2.5f, 0.6f);
			// she lands where she struck, and stays down a moment
			diveTo = new Vec3(at[0].x, groundY(at[0].x, at[0].z) + 0.2, at[0].z);
			strikeUntil = Math.max(strikeUntil, tick + LANDED);
			for (LivingEntity e : victims(at[0], TALON_RADIUS + 0.3)) {
				// the blow is the Furies' (the leader's), so a shield can parry it
				e.hurtServer(level, level.damageSources().mobAttack(body), TALON_DAMAGE);
			}
		});
		return FuryAnim.TALONS.ticks + LANDED;
	}

	/** The Shriek: a purple circle under her. It can't be blocked: get out from under her. */
	private int shriek(Mob body, LivingEntity target) {
		FuryEntity fury = next(body);
		int scream = FuryAnim.SHRIEK.at("scream");
		striking = fury.getUUID();
		strikeUntil = tick + FuryAnim.SHRIEK.ticks + 10;
		Vec3 ground = new Vec3(target.getX(), groundY(target.getX(), target.getZ()), target.getZ());
		diveTo = ground.add(0, 3.0, 0);
		fury.play(FuryAnim.SHRIEK);
		fury.warn(BossEntity.Warning.PURPLE);
		tip("A Fury shrieks over the purple circle: get out from under her!");
		Danger.circle(level, Danger.Colour.PURPLE, ground, SHRIEK_RADIUS, scream + 14);
		schedule(scream + 14, () -> {
			fury.warn(BossEntity.Warning.NONE);
			level.playSound(null, fury.blockPosition(), SoundEvents.GHAST_SCREAM, SoundSource.HOSTILE, 3.0f, 0.8f);
			BossFx.send(level, ParticleTypes.SONIC_BOOM, ground.x, ground.y + 1, ground.z, 1, 0, 0, 0, 0);
			for (LivingEntity e : victims(ground, SHRIEK_RADIUS + 0.3)) {
				if (e.hurtServer(level, Danger.unblockable(level, body), SHRIEK_DAMAGE)) {
					e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0));
				}
			}
		});
		return FuryAnim.SHRIEK.ticks + 20;
	}

	/** The Lash: a red line. Her serpent hair cracks along it: step aside. */
	private int lash(Mob body, LivingEntity target) {
		FuryEntity fury = next(body);
		int lash = FuryAnim.LASH.at("lash");
		Vec3 from = new Vec3(fury.getX(), groundY(fury.getX(), fury.getZ()), fury.getZ());
		double angle = Math.atan2(target.getZ() - from.z, target.getX() - from.x);
		fury.play(FuryAnim.LASH);
		Danger.line(level, Danger.Colour.RED, from, angle, LASH_REACH, LASH_WIDTH, lash + 10);
		schedule(lash + 10, () -> {
			level.playSound(null, fury.blockPosition(), SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.HOSTILE, 2.5f, 0.6f);
			for (double r = 1; r <= LASH_REACH; r += 1.5) {
				BossFx.send(level, ParticleTypes.ITEM_SLIME, from.x + Math.cos(angle) * r, from.y + 0.5, from.z + Math.sin(angle) * r, 3, 0.2, 0.2, 0.2, 0.05);
			}
			for (LivingEntity e : inBand(from, angle, LASH_REACH, LASH_WIDTH)) {
				if (e.hurtServer(level, Danger.unblockable(level, body), LASH_DAMAGE)) {
					e.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0));
				}
			}
		});
		return FuryAnim.LASH.ticks + 25;
	}

	/** Tears of Blood: red circles under every soul (and a few more). Move. */
	private int tear(Mob body) {
		int tear = FuryAnim.TEAR.at("tear");
		for (FuryEntity f : furies(body)) {
			f.play(FuryAnim.TEAR);
		}
		say("Tear! Rend! Let them drown in it!");
		List<Vec3> spots = new ArrayList<>();
		for (LivingEntity e : targets()) {
			spots.add(e.position());
		}
		Vec3 c = centre();
		for (int i = 0; i < 4; i++) {
			double a = level.getRandom().nextDouble() * Math.PI * 2;
			double r = 3 + level.getRandom().nextDouble() * 14;
			double x = c.x + Math.cos(a) * r;
			double z = c.z + Math.sin(a) * r;
			spots.add(new Vec3(x, groundY(x, z), z));
		}
		int land = tear + 20;
		for (Vec3 s : spots) {
			Danger.circle(level, Danger.Colour.RED, s, TEAR_RADIUS, land);
		}
		schedule(land, () -> {
			for (Vec3 s : spots) {
				BossFx.send(level, BloodAltar.BLOOD, s.x, s.y + 2, s.z, 40, 0.8, 1.5, 0.8, 0.0);
				for (LivingEntity e : victims(s, TEAR_RADIUS + 0.3)) {
					if (e.hurtServer(level, Danger.unblockable(level, body), TEAR_DAMAGE)) {
						e.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
					}
				}
			}
			level.playSound(null, body.blockPosition(), SoundEvents.HONEY_BLOCK_FALL, SoundSource.HOSTILE, 2.0f, 0.5f);
		});
		return land + 25;
	}

	// ------------------------------------------------------------------ Medusa

	/** "Venga Medusa!": she rises over the battlements of Dis behind the lair. */
	private void callMedusa(Mob body) {
		MedusaEntity m = HellEntities.MEDUSA.create(level, EntitySpawnReason.EVENT);
		if (m == null) {
			return;
		}
		// on the wall, straight out from the lair toward it
		double wall = InfernoGeometry.DIS_WALL_RADIUS - InfernoGeometry.DIS_WALL_HALF_THICKNESS - 1;
		double a = Math.atan2(lair.getZ(), lair.getX());
		double x = Math.cos(a) * wall;
		double z = Math.sin(a) * wall;
		double y = groundY(x, z) + InfernoGeometry.DIS_WALL_HEIGHT - 2;
		m.snapTo(x, y, z, (float) Math.toDegrees(Math.atan2(-(lair.getX() - x), lair.getZ() - z)), 0);
		m.addTag(TAG);
		level.addFreshEntity(m);
		m.play(MedusaAnim.INTRO);
		medusa = m.getUUID();
		nextGaze = tick + MedusaAnim.INTRO.ticks + 40;
		level.playSound(null, m.blockPosition(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 3.0f, 0.5f);
	}

	@Nullable
	private MedusaEntity medusa() {
		return medusa != null && level.getEntity(medusa) instanceof MedusaEntity m ? m : null;
	}

	private void gaze(MedusaEntity m) {
		int open = MedusaAnim.GAZE.at("open");
		int close = MedusaAnim.GAZE.at("close");
		m.play(MedusaAnim.GAZE);
		nextGaze = tick + GAZE_EVERY;
		schedule(open - 10, () -> {
			for (ServerPlayer p : audience()) {
				p.sendOverlayMessage(Component.literal("Medusa's eyes are opening: LOOK AWAY from her!").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
			}
			level.playSound(null, m.blockPosition(), SoundEvents.ELDER_GUARDIAN_AMBIENT, SoundSource.HOSTILE, 3.0f, 0.5f);
		});
		schedule(open, () -> m.gaze(true));
		schedule(close, () -> {
			m.gaze(false);
			stone.clear();
		});
	}

	/** Whoever looks at Medusa while her eyes are open turns to stone, a little more every tick. */
	private void petrify(MedusaEntity m) {
		Vec3 eyes = new Vec3(m.getX(), m.eyesY(), m.getZ());
		for (ServerPlayer p : audience()) {
			if (!p.isAlive() || p.isSpectator() || p.isCreative() || !inArena(p)) {
				continue;
			}
			Vec3 to = eyes.subtract(p.getEyePosition()).normalize();
			double off = Math.acos(Math.max(-1, Math.min(1, to.dot(p.getLookAngle()))));
			int s = stone.getOrDefault(p.getUUID(), 0);
			s = off < GAZE_ARC ? s + 1 : Math.max(0, s - 2);
			stone.put(p.getUUID(), s);
			if (s > 0 && s % 8 == 0) {
				p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, Math.min(4, s / 8)));
				level.playSound(null, p.blockPosition(), SoundEvents.STONE_PLACE, SoundSource.PLAYERS, 1.0f, 0.6f);
			}
			if (s >= STONE_AT) {
				stone.put(p.getUUID(), 0);
				// stone: held fast, and it hurts
				p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 9));
				p.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 60, 3));
				p.hurtServer(level, Danger.unblockable(level, m), 10.0f);
				p.sendOverlayMessage(Component.literal("You looked at Medusa and turned to stone! Look away while her eyes are open.")
						.withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD));
				BossFx.send(level, ParticleTypes.CLOUD, p.getX(), p.getY() + 1, p.getZ(), 30, 0.4, 0.8, 0.4, 0.02);
			}
		}
	}

	// ------------------------------------------------------------------ every tick

	@Override
	protected void everyTick(Mob body) {
		if (!enraged && body.getHealth() <= body.getMaxHealth() / 2) {
			rage(body);
		}
		if (staggerUntil >= 0 && tick >= staggerUntil) {
			staggerUntil = -1;
			setBase(body, Attributes.ARMOR, 6.0);
			striking = null;
		}
		if (striking != null && tick >= strikeUntil && staggerUntil < 0) {
			striking = null;
		}
		// the three wheel over the lair; the one coming down on someone flies to her mark
		Vec3 c = centre();
		List<FuryEntity> all = furies(body);
		for (int i = 0; i < all.size(); i++) {
			FuryEntity f = all.get(i);
			Vec3 want;
			if (f.getUUID().equals(striking) && diveTo != null) {
				want = staggerUntil >= 0 ? new Vec3(f.getX(), groundY(f.getX(), f.getZ()) + 0.5, f.getZ()) : diveTo;
			} else {
				double a = tick * 0.03 + i * Math.PI * 2 / 3;
				want = new Vec3(c.x + Math.cos(a) * ORBIT, c.y + HEIGHT + Math.sin(tick * 0.07 + i) * 1.2, c.z + Math.sin(a) * ORBIT);
			}
			Vec3 step = want.subtract(f.position()).scale(0.12);
			if (step.length() > 0.6) {
				step = step.normalize().scale(0.6);
			}
			f.setPos(f.position().add(step));
			LivingEntity look = nearest(f, targets());
			if (look != null) {
				turnToward(f, look, 8.0f);
			}
		}
		MedusaEntity m = medusa();
		if (m != null) {
			if (m.gazing()) {
				petrify(m);
			} else if (tick >= nextGaze) {
				gaze(m);
			}
		}
	}

	/** At half their life: "Venga Medusa!" */
	private void rage(Mob body) {
		enraged = true;
		for (FuryEntity f : furies(body)) {
			f.warn(BossEntity.Warning.NONE);
			f.play(FuryAnim.PHASE);
		}
		shield(body, FuryAnim.PHASE.ticks);
		delayNextAttack(FuryAnim.PHASE.ticks + 30);
		say("Venga Medusa! Sì 'l farem di smalto!");
		tip("The Furies call Medusa. When her eyes open, LOOK AWAY from her: whoever looks turns to stone.");
		schedule(FuryAnim.PHASE.at("call"), () -> callMedusa(body));
	}

	@Override
	protected void onParried(Mob body, ServerPlayer by) {
		FuryEntity fury = striking != null && level.getEntity(striking) instanceof FuryEntity f ? f : (FuryEntity) body;
		fury.warn(BossEntity.Warning.NONE);
		fury.play(FuryAnim.STAGGER);
		staggerUntil = tick + FuryAnim.STAGGER.ticks;
		strikeUntil = staggerUntil;
		setBase(body, Attributes.ARMOR, 0.0);
		delayNextAttack(FuryAnim.STAGGER.ticks + 10);
		level.playSound(null, fury.blockPosition(), SoundEvents.PHANTOM_HURT, SoundSource.HOSTILE, 2.0f, 0.5f);
		BossFx.send(level, ParticleTypes.CRIT, fury.getX(), fury.getY() + 1.5, fury.getZ(), 30, 0.8, 0.8, 0.8, 0.3);
		by.sendOverlayMessage(Component.literal("Parried! The Fury tumbles to the ground: hit her now!").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
	}

	/** The sisters and Medusa go with the fight. */
	@Override
	protected void onEnd(boolean won) {
		for (UUID id : sisters) {
			if (level.getEntity(id) instanceof FuryEntity f) {
				// they scatter into the smoke over Dis
				BossFx.send(level, ParticleTypes.LARGE_SMOKE, f.getX(), f.getY() + 1, f.getZ(), 30, 0.5, 0.8, 0.5, 0.05);
				f.discard();
			}
		}
		MedusaEntity m = medusa();
		if (m != null) {
			m.gaze(false);
			m.discard();
		}
	}
}
