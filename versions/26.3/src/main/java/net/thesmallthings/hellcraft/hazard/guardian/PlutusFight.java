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
import net.thesmallthings.hellcraft.entity.boss.PlutusEntity;
import net.thesmallthings.hellcraft.entity.boss.RollingCoin;
import net.thesmallthings.hellcraft.entity.boss.gen.PlutusAnim;
import net.thesmallthings.hellcraft.hazard.Danger;
import net.thesmallthings.hellcraft.hazard.Hoarders;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Plutus (Inferno VII), the great enemy, swollen with the gold of the fourth circle, who shrieks "Pape Satàn, pape
 * Satàn aleppe!" He waddles after his prey, and every blow speaks the danger language (hazard/Danger):
 * <ul>
 *     <li><b>The Fist</b> (white): raised high, it flashes white, and the ground before him shows where it will fall.
 *     Block it with a shield as it lands and he reels, his guard down, for two seconds.</li>
 *     <li><b>Weight of Gold</b> (red circles): he flings gold into the air and it comes down on the circles, harder on
 *     whoever carries more gold (Hoarders.weight). Leave your gold behind before you face him.</li>
 *     <li><b>The Lunge</b> (a red line): he hurls his bulk along it. Step aside.</li>
 *     <li><b>Pape Satàn</b> (a purple ring): a shriek that can't be blocked; it scatters and sickens. Keep away.</li>
 * </ul>
 * At half his health he beats his belly and the Mint opens: from then on great coins roll across the lair along red
 * lines, as the misers and the wasters roll their weights in the poem.
 */
final class PlutusFight extends GuardianFight {
	private static final DustParticleOptions GOLD = new DustParticleOptions(0xE8B923, 1.6f);
	private static final double FIST_REACH = 7.0;
	private static final double FIST_ARC = Math.toRadians(35);
	private static final float FIST_DAMAGE = 10.0f;
	private static final double RAIN_RADIUS = 2.5;
	private static final float RAIN_DAMAGE = 5.0f;
	private static final double LUNGE_REACH = 14.0;
	private static final double LUNGE_WIDTH = 2.0;
	private static final float LUNGE_DAMAGE = 9.0f;
	private static final double PAPE_RADIUS = 9.0;
	private static final float PAPE_DAMAGE = 4.0f;
	/** The great coins: how far from the lair's heart their lines start, and how long they roll. */
	private static final double COIN_FROM = 22.0;
	private static final int COIN_WARN = 30;
	private static final int COIN_TICKS = 75;
	private static final double CLOSE = 5.0;

	private int cycle;
	private boolean enraged;
	private int lockedUntil;
	private int staggerUntil = -1;

	PlutusFight(GuardianContext ctx) {
		super(ctx);
	}

	@Override
	protected Mob createBody() {
		PlutusEntity body = HellEntities.PLUTUS.create(level, EntitySpawnReason.EVENT);
		if (body == null) {
			throw new IllegalStateException("could not create Plutus");
		}
		return body;
	}

	private static PlutusEntity plutus(Mob body) {
		return (PlutusEntity) body;
	}

	@Override
	public List<String> attacks() {
		return List.of("fist", "gold", "lunge", "pape", "coins");
	}

	@Override
	protected String introLine() {
		return "Pape Satàn, pape Satàn aleppe!";
	}

	@Override
	protected String deathLine() {
		return "*As sails swollen by the wind fall in a heap when the mast breaks, so the cruel beast falls to earth.*";
	}

	@Override
	protected Cutscene introCutscene() {
		return Cutscenes.PLUTUS_INTRO;
	}

	@Override
	protected int intro(Mob body) {
		LivingEntity first = nearest(body, targets());
		if (first != null) {
			face(body, first);
		}
		plutus(body).play(PlutusAnim.INTRO);
		lockedUntil = tick + PlutusAnim.INTRO.ticks;
		schedule(PlutusAnim.INTRO.at("rise"), () -> level.playSound(null, body.blockPosition(), SoundEvents.ARMOR_EQUIP_GOLD.value(), SoundSource.HOSTILE, 3.0f, 0.4f));
		schedule(PlutusAnim.INTRO.at("roar"), () -> level.playSound(null, body.blockPosition(), SoundEvents.PIGLIN_BRUTE_ANGRY, SoundSource.HOSTILE, 3.0f, 0.4f));
		return PlutusAnim.INTRO.ticks;
	}

	// ------------------------------------------------------------------ choosing

	@Override
	protected int nextAttack(Mob body, List<LivingEntity> targets) {
		LivingEntity nearest = nearest(body, targets);
		String[] order = enraged ? new String[]{"fist", "coins", "gold", "lunge", "coins", "pape"} : new String[]{"fist", "gold", "lunge", "fist", "pape"};
		String attack = order[cycle++ % order.length];
		double reach = nearest == null ? Double.MAX_VALUE : nearest.distanceTo(body);
		if (attack.equals("fist") && reach > FIST_REACH + 1.5) {
			attack = reach < LUNGE_REACH ? "lunge" : "gold";
		}
		int cooldown = perform(attack, body, nearest == null ? targets.get(0) : nearest);
		return enraged ? Math.max(cooldown * 7 / 10, 25) : cooldown;
	}

	@Override
	protected int perform(String attack, Mob body, LivingEntity target) {
		return switch (attack) {
			case "fist" -> fist(body, target);
			case "gold" -> gold(body);
			case "lunge" -> lunge(body, target);
			case "pape" -> pape(body);
			case "coins" -> coins(body);
			default -> -1;
		};
	}

	// ------------------------------------------------------------------ the attacks

	/** The Fist: white. Block it as it lands and he reels. */
	private int fist(Mob body, LivingEntity target) {
		PlutusEntity plutus = plutus(body);
		stop(body);
		face(body, target);
		double angle = Danger.facing(body);
		Vec3 c = body.position();
		int flash = PlutusAnim.FIST.at("flash");
		int hit = PlutusAnim.FIST.at("hit");
		lockedUntil = tick + PlutusAnim.FIST.ticks;
		plutus.play(PlutusAnim.FIST);
		say("MINE!");
		schedule(flash, () -> {
			plutus.warn(BossEntity.Warning.WHITE);
			openParry(hit - flash + 3);
			Danger.cone(level, Danger.Colour.WHITE, c, angle, FIST_REACH, FIST_ARC, hit - flash);
		});
		schedule(hit, () -> {
			plutus.warn(BossEntity.Warning.NONE);
			level.playSound(null, body.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 2.0f, 0.5f);
			for (LivingEntity e : victims(c, FIST_REACH + 0.5)) {
				Vec3 d = e.position().subtract(c);
				if ((d.x * d.x + d.z * d.z < 2.0 || Math.abs(wrap(Math.atan2(d.z, d.x) - angle)) <= FIST_ARC + 0.15)
						&& e.hurtServer(level, level.damageSources().mobAttack(body), FIST_DAMAGE)) {
					fling(e, c, 1.1, 0.4);
				}
			}
		});
		return PlutusAnim.FIST.ticks + 25;
	}

	/** Weight of Gold: red circles. Gold falls on them, harder on whoever carries more. */
	private int gold(Mob body) {
		PlutusEntity plutus = plutus(body);
		stop(body);
		lockedUntil = tick + PlutusAnim.GOLD.ticks;
		plutus.play(PlutusAnim.GOLD);
		say("All of it, MINE! Every last coin!");
		tip("Gold falls on the red circles: get out of them. It hits harder the more gold you carry.");
		int rain = PlutusAnim.GOLD.at("rain");
		List<Vec3> marks = new ArrayList<>();
		for (LivingEntity t : targets()) {
			marks.add(t.position());
			for (int k = 0; k < (enraged ? 2 : 1); k++) {
				double x = t.getX() + (level.getRandom().nextDouble() - 0.5) * 9;
				double z = t.getZ() + (level.getRandom().nextDouble() - 0.5) * 9;
				marks.add(new Vec3(x, groundY(x, z), z));
			}
		}
		for (Vec3 m : marks) {
			Danger.circle(level, Danger.Colour.RED, m, RAIN_RADIUS, rain);
		}
		schedule(PlutusAnim.GOLD.at("throw"), () -> {
			level.playSound(null, body.blockPosition(), SoundEvents.ARMOR_EQUIP_GOLD.value(), SoundSource.HOSTILE, 2.5f, 0.5f);
			level.sendParticles(GOLD, body.getX(), body.getY() + 8, body.getZ(), 80, 2, 2, 2, 0.1);
		});
		for (int t = rain - 10; t < rain; t += 3) {
			schedule(t, () -> {
				for (Vec3 m : marks) {
					level.sendParticles(GOLD, m.x, m.y + 6, m.z, 6, 0.8, 0.8, 0.8, 0);
				}
			});
		}
		schedule(rain, () -> {
			level.playSound(null, body.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.5f, 1.4f);
			for (Vec3 m : marks) {
				level.sendParticles(ParticleTypes.CRIT, m.x, m.y + 0.5, m.z, 20, 1, 0.3, 1, 0.2);
				for (LivingEntity e : victims(m, RAIN_RADIUS + 0.3)) {
					int weight = Hoarders.weight(e);
					if (e.hurtServer(level, Danger.unblockable(level, body), RAIN_DAMAGE + Math.min(8, weight / 12))) {
						e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, Math.min(4, 1 + weight / 32)));
						if (weight >= 12 && e instanceof ServerPlayer p) {
							p.sendOverlayMessage(Component.literal("The gold you carry made that hurt more!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
						}
					}
				}
			}
		});
		return PlutusAnim.GOLD.ticks + 20;
	}

	/** The Lunge: a red line, then he hurls himself along it. */
	private int lunge(Mob body, LivingEntity target) {
		PlutusEntity plutus = plutus(body);
		stop(body);
		face(body, target);
		double angle = Danger.facing(body);
		Vec3 from = body.position();
		Vec3 line = new Vec3(Math.cos(angle), 0, Math.sin(angle));
		int leap = PlutusAnim.LUNGE.at("leap");
		int land = PlutusAnim.LUNGE.at("land");
		lockedUntil = tick + PlutusAnim.LUNGE.ticks;
		plutus.play(PlutusAnim.LUNGE);
		say("Hoarders! Wasters! I'll have it back!");
		tip("A red line: he's about to hurl himself along it. Step aside!");
		Danger.line(level, Danger.Colour.RED, from, angle, LUNGE_REACH, LUNGE_WIDTH, leap);
		Set<UUID> hit = new HashSet<>();
		int steps = land - leap;
		for (int step = 0; step <= steps; step++) {
			int s = step;
			schedule(leap + step, () -> {
				Mob b = body();
				if (b == null) {
					return;
				}
				Vec3 p = from.add(line.scale(LUNGE_REACH * (s + 1) / (steps + 1)));
				b.teleportTo(p.x, groundY(p.x, p.z), p.z);
				level.sendParticles(GOLD, p.x, p.y + 0.5, p.z, 8, 0.6, 0.3, 0.6, 0.02);
				for (LivingEntity e : victims(p, 2.8)) {
					if (hit.add(e.getUUID()) && e.hurtServer(level, Danger.unblockable(level, b), LUNGE_DAMAGE)) {
						fling(e, p, 1.4, 0.5);
					}
				}
			});
		}
		schedule(land, () -> level.playSound(null, body.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.5f, 0.7f));
		return PlutusAnim.LUNGE.ticks + 25;
	}

	/** Pape Satàn: a purple ring. A shriek that can't be blocked: keep away. */
	private int pape(Mob body) {
		PlutusEntity plutus = plutus(body);
		stop(body);
		Vec3 c = body.position();
		int shriek = PlutusAnim.PAPE.at("shriek");
		lockedUntil = tick + PlutusAnim.PAPE.ticks;
		plutus.play(PlutusAnim.PAPE);
		plutus.warn(BossEntity.Warning.PURPLE);
		tip("He's about to shriek: get out of the purple ring, it can't be blocked!");
		Danger.ring(level, Danger.Colour.PURPLE, c, 0, PAPE_RADIUS, shriek);
		schedule(shriek, () -> {
			plutus.warn(BossEntity.Warning.NONE);
			say("PAPE SATÀN, PAPE SATÀN ALEPPE!");
			level.playSound(null, body.blockPosition(), SoundEvents.PIGLIN_BRUTE_ANGRY, SoundSource.HOSTILE, 4.0f, 0.4f);
			level.playSound(null, body.blockPosition(), SoundEvents.GHAST_SCREAM, SoundSource.HOSTILE, 3.0f, 0.5f);
			ringParticles(ParticleTypes.SONIC_BOOM, c, PAPE_RADIUS * 0.6, 10);
			for (LivingEntity e : victims(c, PAPE_RADIUS + 0.3)) {
				if (e.hurtServer(level, Danger.unblockable(level, body), PAPE_DAMAGE)) {
					e.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 120, 0));
					fling(e, c, 1.6, 0.4);
					if (e instanceof ServerPlayer p) {
						p.sendOverlayMessage(Component.literal("His babbling makes you dizzy...").withStyle(ChatFormatting.GOLD));
					}
				}
			}
		});
		return PlutusAnim.PAPE.ticks + 20;
	}

	/** In his fury: red lines across the lair, and down them roll the great coins of the Mint. */
	private int coins(Mob body) {
		if (!enraged) {
			return gold(body);
		}
		say("Roll, wasters! Push, hoarders! Forever!");
		tip("Great coins roll along the red lines: get off them!");
		int count = 2 + level.getRandom().nextInt(2);
		double base = level.getRandom().nextDouble() * Math.PI * 2;
		for (int i = 0; i < count; i++) {
			// each line crosses the lair, offset a little from its heart
			double a = base + i * Math.PI / count + (level.getRandom().nextDouble() - 0.5) * 0.4;
			double offset = (level.getRandom().nextDouble() - 0.5) * 10;
			double sx = lair.getX() + 0.5 - Math.cos(a) * COIN_FROM - Math.sin(a) * offset;
			double sz = lair.getZ() + 0.5 - Math.sin(a) * COIN_FROM + Math.cos(a) * offset;
			Vec3 start = new Vec3(sx, groundY(sx, sz), sz);
			Danger.line(level, Danger.Colour.RED, start, a, COIN_FROM * 2, 1.4, COIN_WARN);
			schedule(COIN_WARN, () -> {
				RollingCoin coin = HellEntities.ROLLING_COIN.create(level, EntitySpawnReason.EVENT);
				if (coin == null) {
					return;
				}
				coin.snapTo(start.x, start.y, start.z, 0, 0);
				coin.launch(body(), a, COIN_TICKS);
				coin.addTag(TAG);
				level.addFreshEntity(coin);
			});
		}
		level.playSound(null, body.blockPosition(), SoundEvents.ARMOR_EQUIP_GOLD.value(), SoundSource.HOSTILE, 3.0f, 0.3f);
		return COIN_WARN + 30;
	}

	// ------------------------------------------------------------------ every tick

	@Override
	protected void everyTick(Mob body) {
		PlutusEntity plutus = plutus(body);
		if (!enraged && body.getHealth() <= body.getMaxHealth() / 2) {
			rage(plutus);
		}
		if (staggerUntil >= 0 && tick >= staggerUntil) {
			staggerUntil = -1;
			setBase(body, Attributes.ARMOR, 10.0);
		}
		if (tick >= lockedUntil && staggerUntil < 0) {
			LivingEntity nearest = nearest(body, targets());
			if (nearest != null) {
				if (nearest.distanceTo(body) > CLOSE) {
					body.getNavigation().moveTo(nearest, 1.0);
				} else {
					stop(body);
					turnToward(body, nearest, 5.0f);
				}
			}
		}
	}

	/** At half his health: he beats his belly, roars, and the Mint opens. */
	private void rage(PlutusEntity plutus) {
		enraged = true;
		stop(plutus);
		plutus.warn(BossEntity.Warning.NONE);
		plutus.play(PlutusAnim.PHASE);
		shield(plutus, PlutusAnim.PHASE.ticks);
		delayNextAttack(PlutusAnim.PHASE.ticks + 10);
		lockedUntil = tick + PlutusAnim.PHASE.ticks;
		say("THIEVES! You'd take what is MINE? Then take ALL of it!");
		tip("Plutus is furious: great coins will roll across the lair along red lines. Get off the lines!");
		schedule(PlutusAnim.PHASE.at("roar"), () -> level.playSound(null, plutus.blockPosition(), SoundEvents.PIGLIN_BRUTE_ANGRY, SoundSource.HOSTILE, 4.0f, 0.3f));
		schedule(PlutusAnim.PHASE.at("mint"), () -> {
			level.playSound(null, plutus.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 3.0f, 0.3f);
			level.sendParticles(GOLD, plutus.getX(), plutus.getY() + 3, plutus.getZ(), 200, 4, 3, 4, 0.2);
		});
	}

	/** Blocked in the white flash: he reels, his guard down. */
	@Override
	protected void onParried(Mob body, ServerPlayer by) {
		PlutusEntity plutus = plutus(body);
		stop(body);
		plutus.warn(BossEntity.Warning.NONE);
		plutus.play(PlutusAnim.STAGGER);
		staggerUntil = tick + PlutusAnim.STAGGER.ticks;
		lockedUntil = staggerUntil;
		setBase(body, Attributes.ARMOR, 0.0);
		delayNextAttack(PlutusAnim.STAGGER.ticks + 15);
		level.playSound(null, body.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.5f, 0.6f);
		level.sendParticles(ParticleTypes.CRIT, body.getX(), body.getY() + 4, body.getZ(), 40, 1.5, 1.5, 1.5, 0.4);
		say("My... my GOLD!");
		by.sendOverlayMessage(Component.literal("Parried! Plutus reels: hit him now!").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
	}

	private static void stop(Mob body) {
		body.getNavigation().stop();
	}
}
