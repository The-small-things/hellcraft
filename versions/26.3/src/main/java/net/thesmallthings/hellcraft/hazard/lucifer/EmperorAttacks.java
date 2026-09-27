package net.thesmallthings.hellcraft.hazard.lucifer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.util.Feedback;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Lucifer's true form, as Dante found him (Inferno XXXIV): the Emperor frozen to the chest in the ice at
 * the centre of the pit, three faces on one head and six wings beating the wind that freezes Cocytus.
 * He can't leave the ice, so the whole pit is his reach. Each face has its own attack and turns toward
 * its victim when it strikes:
 * <ul>
 *     <li><b>Hatred</b> (red): rings of fire sweep out across the ice. Jump them.</li>
 *     <li><b>Impotence</b> (pale yellow): he weeps, and the tears fall frozen where the frost marks the ice.</li>
 *     <li><b>Ignorance</b> (black): darkness, and a spiral of jaws sweeping out of it.</li>
 *     <li><b>The wings</b>: a freezing gale that drives everyone out toward the wall. Sneak to brace against it.</li>
 *     <li><b>The mouths</b> (from two thirds health): he drags someone to his jaws and chews; the others free
 *     them by hurting him.</li>
 * </ul>
 * Below a third of his health all three faces rage together: attacks come faster, the rings of hatred
 * run under everything else, and Judas, Brutus and Cassius crawl out of his mouths.
 */
final class EmperorAttacks {
	enum Attack {HATRED, IMPOTENCE, IGNORANCE, WINGBEAT, MOUTHS}

	/** Model turns (degrees) that bring each face toward the target: see LuciferModel.showFace. */
	private static final float RED_FACE = 0.0f;
	private static final float YELLOW_FACE = -90.0f;
	private static final float BLACK_FACE = 90.0f;
	private static final DustParticleOptions BLOOD = new DustParticleOptions(0x8C0000, 1.8f);
	private static final double REACH = 30.0;

	private final LuciferFight fight;
	private final ServerLevel level;
	private final UUID bossId;
	private int stage = 1;
	private int cooldown = 80;
	private int cycle;
	private int windTicks;
	@Nullable
	private UUID grabbed;
	private int grabTicks;
	private float grabDamage;

	EmperorAttacks(LuciferFight fight, UUID bossId) {
		this.fight = fight;
		this.level = fight.level();
		this.bossId = bossId;
	}

	int stage() {
		return stage;
	}

	/** Every tick of the true form: keep him frozen in place, run his attack cycle, hold whoever he chews. */
	void tick(WitherBoss boss) {
		anchor(boss);
		float health = boss.getHealth() / Math.max(1.0f, boss.getMaxHealth());
		if (stage == 1 && health <= 0.66f) {
			stage = 2;
			crack(boss);
		} else if (stage == 2 && health <= 0.33f) {
			stage = 3;
			rage(boss);
		}
		wind(boss);
		chew(boss);
		if (--cooldown <= 0) {
			List<Attack> pool = stage == 1 ? List.of(Attack.HATRED, Attack.IMPOTENCE, Attack.WINGBEAT)
					: List.of(Attack.HATRED, Attack.MOUTHS, Attack.IMPOTENCE, Attack.IGNORANCE, Attack.WINGBEAT);
			Attack next = pool.get(cycle++ % pool.size());
			if (next == Attack.MOUTHS && grabbed != null) {
				next = Attack.HATRED;
			}
			if (!perform(next, boss)) {
				cooldown = 20;
			}
		}
	}

	/** He is frozen into the lake: whatever his wither body tries, it stays at the centre of the pit. */
	private void anchor(WitherBoss boss) {
		double y = fight.floorY();
		if (boss.distanceToSqr(0.5, y, 0.5) > 0.01) {
			boss.teleportTo(0.5, y, 0.5);
		}
		boss.setDeltaMovement(Vec3.ZERO);
	}

	boolean perform(Attack attack, WitherBoss boss) {
		List<LivingEntity> targets = fight.targets();
		if (targets.isEmpty()) {
			return false;
		}
		LivingEntity target = targets.get(level.getRandom().nextInt(targets.size()));
		double pace = (stage == 3 ? 0.7 : stage == 2 ? 0.85 : 1.0) * Math.max(0.6, 1.0 - 0.12 * fight.tier());
		switch (attack) {
			case HATRED -> {
				hatred(boss, stage == 3 ? 4 : 3, true);
				cooldown = (int) (110 * pace);
			}
			case IMPOTENCE -> {
				impotence(boss, targets);
				cooldown = (int) (80 * pace);
			}
			case IGNORANCE -> {
				ignorance(boss);
				cooldown = (int) (110 * pace);
			}
			case WINGBEAT -> {
				wingbeat(boss, targets);
				cooldown = (int) (100 * pace);
			}
			case MOUTHS -> {
				mouths(boss, target);
				cooldown = (int) (120 * pace);
			}
		}
		if (stage == 3 && attack != Attack.HATRED && level.getRandom().nextFloat() < 0.5f) {
			// all three faces at once: the rings of hatred run under the other attacks
			fight.schedule(40, () -> hatred(boss, 1, false));
		}
		return true;
	}

	// ------------------------------------------------------------------ stage changes

	private void crack(WitherBoss boss) {
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.EMPEROR_CRACK));
		level.playSound(null, boss.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 4.0f, 0.3f);
		level.sendParticles(ParticleTypes.SNOWFLAKE, boss.getX(), boss.getY() + 1, boss.getZ(), 300, 4, 1, 4, 0.3);
		tip("The ice cracks: his mouths are free. Hurt him to make him let go of anyone he grabs.");
		cooldown = 40;
	}

	private void rage(WitherBoss boss) {
		fight.say(LuciferDialogue.EMPEROR_RAGE[0]);
		fight.schedule(40, () -> fight.say(LuciferDialogue.EMPEROR_RAGE[1]));
		LuciferDialogue.nameCard(LuciferDialogue.audience(level, REACH + 60), "ALL THREE FACES", "The Emperor rages", ChatFormatting.DARK_RED);
		level.playSound(null, boss.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3.0f, 0.6f);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, boss.getX(), boss.getY() + 2, boss.getZ(), 1, 0, 0, 0, 0);
		fight.releaseTraitors(boss);
		cooldown = 60;
	}

	// ------------------------------------------------------------------ the red face: hatred

	/** Rings of fire sweep outward from him, one after another; anyone standing on the ice when one passes burns. */
	private void hatred(WitherBoss boss, int waves, boolean announce) {
		if (announce) {
			fight.model().showFace(bossId, RED_FACE);
			fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.HATRED));
			tip("Rings of hatred! Jump over them.");
		}
		level.playSound(null, boss.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 3.0f, 0.4f);
		for (int t = 0; t < 20; t += 2) {
			int step = t;
			fight.schedule(t, () -> level.sendParticles(ParticleTypes.FLAME, boss.getX(), boss.getY() + 0.2 + step * 0.1, boss.getZ(), 30, 1.5, 0.1, 1.5, 0.02));
		}
		for (int w = 0; w < waves; w++) {
			Set<UUID> burned = new HashSet<>();
			int start = 20 + w * 22;
			fight.schedule(start, () -> level.playSound(null, boss.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 3.0f, 0.6f));
			for (int r = 3; r <= (int) REACH; r++) {
				int radius = r;
				fight.schedule(start + (r - 3), () -> ring(boss, radius, burned));
			}
		}
	}

	private void ring(WitherBoss boss, double radius, Set<UUID> burned) {
		int points = Math.max(12, (int) (radius * 2 * Math.PI / 1.2));
		for (int i = 0; i < points; i++) {
			double a = i * 2 * Math.PI / points;
			double x = 0.5 + Math.cos(a) * radius;
			double z = 0.5 + Math.sin(a) * radius;
			double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
			level.sendParticles(i % 3 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, x, y + 0.15, z, 2, 0.1, 0.15, 0.1, 0.01);
		}
		for (LivingEntity e : victims(radius + 1.5)) {
			double d = Math.sqrt((e.getX() - 0.5) * (e.getX() - 0.5) + (e.getZ() - 0.5) * (e.getZ() - 0.5));
			if (Math.abs(d - radius) < 0.9 && e.onGround() && burned.add(e.getUUID())) {
				e.hurt(level.damageSources().mobAttack(boss), 7.0f * fight.damageMultiplier());
				e.igniteForSeconds(3.0f);
				e.push(0, 0.45, 0);
				Feedback.syncMotion(e);
			}
		}
	}

	// ------------------------------------------------------------------ the yellow face: impotence

	/** He weeps. Frost rings mark where the tears will land; a moment later they fall as ice. */
	private void impotence(WitherBoss boss, List<LivingEntity> targets) {
		fight.model().showFace(bossId, YELLOW_FACE);
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.IMPOTENCE));
		tip("His tears fall frozen: get out of the frost rings!");
		level.playSound(null, boss.blockPosition(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 1.5f, 1.4f);
		List<Vec3> marks = new ArrayList<>();
		int around = stage >= 2 ? 4 : 3;
		for (LivingEntity target : targets) {
			marks.add(ground(target.getX(), target.getZ()));
			for (int k = 0; k < around; k++) {
				marks.add(ground(target.getX() + (level.getRandom().nextDouble() - 0.5) * 12, target.getZ() + (level.getRandom().nextDouble() - 0.5) * 12));
			}
		}
		for (int k = 0; k < 4 + 2 * stage; k++) {
			double a = level.getRandom().nextDouble() * 2 * Math.PI;
			double r = 5 + level.getRandom().nextDouble() * (REACH - 7);
			marks.add(ground(0.5 + Math.cos(a) * r, 0.5 + Math.sin(a) * r));
		}
		for (int t = 0; t < 32; t += 3) {
			fight.schedule(t, () -> {
				for (Vec3 m : marks) {
					for (int i = 0; i < 10; i++) {
						double a = i * Math.PI / 5;
						level.sendParticles(ParticleTypes.SNOWFLAKE, m.x + Math.cos(a) * 2, m.y + 0.1, m.z + Math.sin(a) * 2, 1, 0, 0, 0, 0);
					}
					level.sendParticles(ParticleTypes.FALLING_WATER, m.x, m.y + 9, m.z, 3, 0.4, 0.5, 0.4, 0);
				}
			});
		}
		fight.schedule(35, () -> {
			for (Vec3 m : marks) {
				level.sendParticles(ParticleTypes.SNOWFLAKE, m.x, m.y + 0.5, m.z, 40, 1.2, 0.4, 1.2, 0.15);
				level.sendParticles(ParticleTypes.CLOUD, m.x, m.y + 0.3, m.z, 6, 0.8, 0.1, 0.8, 0.02);
				for (LivingEntity e : victims(REACH + 2)) {
					if (e.distanceToSqr(m) <= 2.3 * 2.3) {
						e.hurt(level.damageSources().freeze(), 6.0f * fight.damageMultiplier());
						e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
						e.setTicksFrozen(Math.min(e.getTicksRequiredToFreeze() + 100, e.getTicksFrozen() + 140));
					}
				}
			}
			level.playSound(null, boss.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 3.0f, 0.5f);
		});
	}

	// ------------------------------------------------------------------ the black face: ignorance

	/** The light goes out, and jaws of ice sweep out of the dark in a turning spiral. */
	private void ignorance(WitherBoss boss) {
		fight.model().showFace(bossId, BLACK_FACE);
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.IGNORANCE));
		tip("The dark has teeth: watch the ice for the spiral of jaws.");
		for (ServerPlayer p : LuciferDialogue.audience(level, REACH + 4)) {
			if (!p.isSpectator() && !p.isCreative()) {
				p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 160, 0));
			}
			Feedback.sound(p, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 1.0f, 0.5f);
		}
		int arms = stage == 3 ? 3 : 2;
		double turn = level.getRandom().nextBoolean() ? 0.09 : -0.09;
		double offset = level.getRandom().nextDouble() * 2 * Math.PI;
		for (int t = 0; t < 72; t++) {
			int step = t;
			fight.schedule(20 + t, () -> {
				double r = 3.0 + step * 0.37;
				for (int arm = 0; arm < arms; arm++) {
					double a = offset + arm * 2 * Math.PI / arms + step * turn;
					double x = 0.5 + Math.cos(a) * r;
					double z = 0.5 + Math.sin(a) * r;
					Vec3 g = ground(x, z);
					level.addFreshEntity(new EvokerFangs(level, x, g.y, z, (float) a, 6, boss));
				}
			});
		}
	}

	// ------------------------------------------------------------------ the six wings

	/** Six wings beat: a freezing gale drives everyone out toward the wall. Sneaking braces against it. */
	private void wingbeat(WitherBoss boss, List<LivingEntity> targets) {
		fight.model().showFace(bossId, RED_FACE);
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.WINGBEAT));
		tip("The wind of Cocytus! SNEAK to brace yourself.");
		for (int t = 0; t < 15; t += 5) {
			fight.schedule(t, () -> level.playSound(null, boss.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 4.0f, 0.4f));
		}
		windTicks = 15 + 70;
	}

	private void wind(WitherBoss boss) {
		if (windTicks <= 0) {
			return;
		}
		windTicks--;
		if (windTicks > 70) {
			return;
		}
		if (windTicks % 20 == 0) {
			level.playSound(null, boss.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 4.0f, 0.3f);
		}
		for (LivingEntity e : victims(REACH + 2)) {
			if (e.getUUID().equals(grabbed)) {
				continue;
			}
			Vec3 away = new Vec3(e.getX() - 0.5, 0, e.getZ() - 0.5);
			away = away.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : away.normalize();
			boolean braced = e.isShiftKeyDown();
			double strength = braced ? 0.025 : 0.11;
			e.push(away.x * strength, 0, away.z * strength);
			Feedback.syncMotion(e);
			e.setTicksFrozen(Math.min(e.getTicksRequiredToFreeze() + 60, e.getTicksFrozen() + (braced ? 1 : 4)));
			if (windTicks % 3 == 0) {
				level.sendParticles(ParticleTypes.SNOWFLAKE, e.getX() - away.x * 2, e.getY() + 1, e.getZ() - away.z * 2, 6, 0.4, 0.6, 0.4, 0.2);
			}
			if (windTicks == 0 && !braced) {
				e.hurt(level.damageSources().freeze(), 4.0f * fight.damageMultiplier());
			}
		}
		if (windTicks % 4 == 0) {
			level.sendParticles(ParticleTypes.CLOUD, boss.getX(), boss.getY() + 2, boss.getZ(), 30, 6, 1, 6, 0.3);
		}
	}

	// ------------------------------------------------------------------ the mouths

	/** He reaches for someone. If they are still in the pit a second later, he drags them to his jaws. */
	private void mouths(WitherBoss boss, LivingEntity target) {
		fight.model().showFace(bossId, RED_FACE);
		fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.MOUTHS));
		if (target instanceof ServerPlayer p) {
			p.sendOverlayMessage(Component.literal("The Emperor reaches for you!").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
		}
		for (int t = 0; t < 20; t += 2) {
			fight.schedule(t, () -> level.sendParticles(BLOOD, target.getX(), target.getY() + 1, target.getZ(), 12, 0.5, 0.8, 0.5, 0));
		}
		fight.schedule(20, () -> {
			if (grabbed != null || !target.isAlive() || !LuciferArena.inside(target.getX(), target.getZ()) || fight.wither() == null) {
				return;
			}
			grabbed = target.getUUID();
			grabTicks = 0;
			grabDamage = 0;
			level.playSound(null, boss.blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 3.0f, 0.5f);
			tip(target.getName().getString() + " is in his jaws! Hurt the Emperor to make him let go.");
		});
	}

	private void chew(WitherBoss boss) {
		if (grabbed == null) {
			return;
		}
		Entity found = level.getEntity(grabbed);
		if (!(found instanceof LivingEntity victim) || !victim.isAlive() || victim.isSpectator()) {
			grabbed = null;
			return;
		}
		grabTicks++;
		Vec3 toward = new Vec3(victim.getX() - boss.getX(), 0, victim.getZ() - boss.getZ());
		toward = toward.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, -1) : toward.normalize();
		Vec3 mouth = boss.position().add(toward.scale(1.6)).add(0, 2.1, 0);
		if (victim.position().distanceToSqr(mouth) > 36) {
			victim.teleportTo(mouth.x, mouth.y, mouth.z);
		} else {
			victim.setDeltaMovement(mouth.subtract(victim.position()).scale(0.4));
			Feedback.syncMotion(victim);
		}
		victim.resetFallDistance();
		if (grabTicks % 10 == 0) {
			victim.hurt(level.damageSources().mobAttack(boss), 2.0f * fight.damageMultiplier());
			level.sendParticles(BLOOD, victim.getX(), victim.getY() + 1, victim.getZ(), 20, 0.4, 0.6, 0.4, 0);
		}
		if (grabDamage >= 16.0f + 4.0f * fight.tier() || grabTicks >= 80) {
			boolean freed = grabDamage >= 16.0f + 4.0f * fight.tier();
			grabbed = null;
			victim.setDeltaMovement(toward.x * 1.4, 0.6, toward.z * 1.4);
			Feedback.syncMotion(victim);
			level.playSound(null, boss.blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 3.0f, 1.2f);
			if (freed) {
				fight.say("AAARGH! Take thy morsel, then!");
			} else {
				fight.say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.SPIT));
			}
		}
	}

	/** Damage anyone deals the Emperor counts toward making him drop whoever he is chewing. */
	void onHurt(float amount) {
		if (grabbed != null) {
			grabDamage += amount;
		}
	}

	// ------------------------------------------------------------------ helpers

	private Vec3 ground(double x, double z) {
		return new Vec3(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z)), z);
	}

	/** Everything in the pit that can be hurt, except Lucifer and his servants. */
	private List<LivingEntity> victims(double radius) {
		AABB box = new AABB(0.5 - radius, fight.floorY() - 12, 0.5 - radius, 0.5 + radius, fight.floorY() + 30, 0.5 + radius);
		return level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive()
				&& !LuciferManager.isLucifer(e)
				&& !e.isSpectator()
				&& !(e instanceof Player p && p.isCreative()));
	}

	/** A hint in the action bar of everyone in the pit (the attacks are new; nobody should die not knowing why). */
	private void tip(String text) {
		for (ServerPlayer p : LuciferDialogue.audience(level, REACH + 4)) {
			p.sendOverlayMessage(Component.literal(text).withStyle(ChatFormatting.AQUA));
		}
	}
}
