package net.thesmallthings.hellcraft.entity.boss;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.entity.boss.gen.CyclopsAnim;
import net.thesmallthings.hellcraft.hazard.Danger;
import net.thesmallthings.hellcraft.net.Dialogue;
import net.thesmallthings.hellcraft.net.MapSync;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A Cyclops of the quarries (Phase 6): one of Vulcan's smiths, a mini-boss who keeps a camp in the Cyclops Quarries of the
 * Forge of Dis (CyclopsCampFeature), modelled in tools/art/cyclops.py. He sits by his fire until someone comes near, then
 * roars and fights on his own (no guardian lair), in the danger language:
 * <ul>
 *     <li><b>The Smash</b> (a white wedge): the maul over his head, a white flash, then down. Block it and he reels.</li>
 *     <li><b>The Boulder</b> (a red circle): he tears up a boulder and hurls it where you stand. Move.</li>
 *     <li><b>The Quake</b> (a purple ring round him): he stamps. It can't be blocked: jump it, or keep out of the ring.</li>
 * </ul>
 * At half his health he roars again and throws two boulders at a time. His blows keep the bosses' tempo (bossTempo).
 */
public class CyclopsEntity extends AnimatedBoss {
	private static final double WAKE = 16.0;
	private static final double LEASH = 40.0;
	private static final double SMASH_REACH = 6.0;
	private static final double SMASH_ARC = 0.7;
	private static final float SMASH_DAMAGE = 10.0f;
	private static final double BOULDER_RADIUS = 2.5;
	private static final int BOULDER_FLIGHT = 18;
	private static final float BOULDER_DAMAGE = 9.0f;
	private static final double QUAKE_INNER = 1.5;
	private static final double QUAKE_OUTER = 7.0;
	private static final float QUAKE_DAMAGE = 7.0f;

	private record Scheduled(int at, Runnable action) {
	}

	private final List<Scheduled> scheduled = new ArrayList<>();
	private final ServerBossEvent bar = new ServerBossEvent(UUID.randomUUID(),
			Component.literal("CYCLOPS — Smith of the Quarries").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
			BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_6);
	private boolean awake;
	private boolean enraged;
	private int clock;
	private double partial;
	private int nextAttack;
	private int lockedUntil;
	private int staggerUntil = -1;
	private int parryUntil = -1;
	private int cycle;
	private Vec3 home;

	public CyclopsEntity(EntityType<? extends Monster> type, Level level) {
		super(type, level, CyclopsAnim.values());
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 180.0)
				.add(Attributes.ARMOR, 6.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
				.add(Attributes.MOVEMENT_SPEED, 0.26)
				.add(Attributes.STEP_HEIGHT, 1.5)
				.add(Attributes.FOLLOW_RANGE, 40.0);
	}

	/** A blocked Smash staggers him (the shield must catch it while he flashes white). */
	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (blocked && source.getEntity() instanceof CyclopsEntity c && entity instanceof ServerPlayer player && c.clock <= c.parryUntil) {
				c.parryUntil = -1;
				c.stagger(player);
			}
		});
	}

	@Override
	protected void registerGoals() {
		// none: he fights on his own clock (customServerAiStep)
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (home == null) {
			home = position();
		}
		List<ServerPlayer> near = level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(LEASH),
				p -> p.isAlive() && !p.isSpectator() && !p.isCreative());
		updateBar(level, near);
		if (!awake) {
			ServerPlayer first = near.stream().filter(p -> p.distanceTo(this) < WAKE).findFirst().orElse(null);
			if (first != null) {
				wake(level, near);
			}
			return;
		}
		if (near.isEmpty()) {
			// everyone left: back to his fire
			awake = false;
			scheduled.clear();
			warn(Warning.NONE);
			getNavigation().moveTo(home.x, home.y, home.z, 0.8);
			return;
		}
		// his own clock, at the bosses' tempo
		double tempo = Math.max(0.2, Math.min(1.0, HellConfig.get().bossTempo));
		if (tempo() != (float) tempo) {
			tempo((float) tempo);
		}
		partial += tempo;
		while (partial >= 1.0) {
			partial -= 1.0;
			Danger.stretched(1.0 / tempo, () -> step(level, near));
		}
	}

	private void step(ServerLevel level, List<ServerPlayer> near) {
		clock++;
		runScheduled();
		if (!enraged && getHealth() <= getMaxHealth() / 2) {
			rage(level);
			return;
		}
		if (staggerUntil >= 0 && clock >= staggerUntil) {
			staggerUntil = -1;
		}
		ServerPlayer target = nearest(near);
		if (target == null || clock < lockedUntil || staggerUntil >= 0) {
			return;
		}
		double d = target.distanceTo(this);
		if (clock >= nextAttack) {
			String attack = d > SMASH_REACH + 2 ? "boulder" : new String[]{"smash", "quake", "smash", "boulder"}[cycle++ % 4];
			nextAttack = clock + perform(level, attack, target);
			return;
		}
		if (d > SMASH_REACH - 1) {
			getNavigation().moveTo(target, 1.0);
		} else {
			getNavigation().stop();
			getLookControl().setLookAt(target);
		}
	}

	/** Runs one blow at a target (also used by /hellcraft cyclops); returns the pause before the next. */
	public int perform(ServerLevel level, String attack, LivingEntity target) {
		awake = true;
		return switch (attack) {
			case "smash" -> smash(level, target);
			case "boulder" -> boulder(level, target);
			case "quake" -> quake(level);
			default -> -1;
		};
	}

	/** /hellcraft cyclops attack: the nearest Cyclops uses a blow on the nearest other creature. */
	public static String attackNear(ServerLevel level, Vec3 at, String attack) {
		net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(at, at).inflate(48);
		List<CyclopsEntity> all = level.getEntitiesOfClass(CyclopsEntity.class, box);
		List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box, e -> !(e instanceof CyclopsEntity) && e.isAlive());
		if (all.isEmpty() || targets.isEmpty()) {
			return "No Cyclops, or nothing for him to fight, near here.";
		}
		return all.getFirst().perform(level, attack, targets.getFirst()) < 0 ? "Unknown attack: " + attack : "The Cyclops uses " + attack + ".";
	}

	private void wake(ServerLevel level, List<ServerPlayer> near) {
		awake = true;
		play(CyclopsAnim.ROAR);
		lockedUntil = clock + CyclopsAnim.ROAR.ticks;
		nextAttack = lockedUntil + 10;
		schedule(CyclopsAnim.ROAR.at("roar"), () -> level.playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.5f, 0.6f));
		for (ServerPlayer p : near) {
			MapSync.unlockCodex(p, "cyclops");
			Dialogue.notice(p, Component.literal("A Cyclops! White flash: block his maul and he reels. Red circle: his boulder "
					+ "lands there. Purple ring: jump his stamp.").withStyle(ChatFormatting.GOLD));
		}
	}

	private void rage(ServerLevel level) {
		enraged = true;
		getNavigation().stop();
		warn(Warning.NONE);
		play(CyclopsAnim.ROAR);
		lockedUntil = clock + CyclopsAnim.ROAR.ticks;
		nextAttack = lockedUntil + 10;
		schedule(CyclopsAnim.ROAR.at("roar"), () -> level.playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0f, 0.4f));
	}

	/** The Smash: a white wedge. Block it as it lands and he reels. */
	private int smash(ServerLevel level, LivingEntity target) {
		getNavigation().stop();
		lookAtNow(target);
		double angle = Danger.facing(this);
		Vec3 c = position();
		int flash = CyclopsAnim.SMASH.at("flash");
		int hit = CyclopsAnim.SMASH.at("hit");
		lockedUntil = clock + CyclopsAnim.SMASH.ticks;
		play(CyclopsAnim.SMASH);
		schedule(flash, () -> {
			warn(Warning.WHITE);
			parryUntil = clock + hit - flash + 3;
			Danger.cone(level, Danger.Colour.WHITE, c, angle, SMASH_REACH, SMASH_ARC, hit - flash);
		});
		schedule(hit, () -> {
			warn(Warning.NONE);
			level.playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 2.0f, 0.6f);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(SMASH_REACH), e -> e != this && e.isAlive())) {
				Vec3 d = e.position().subtract(c);
				double r = Math.hypot(d.x, d.z);
				double off = Math.abs(Math.atan2(Math.sin(Math.atan2(d.z, d.x) - angle), Math.cos(Math.atan2(d.z, d.x) - angle)));
				if (r <= SMASH_REACH && off <= SMASH_ARC) {
					e.hurtServer(level, level.damageSources().mobAttack(this), SMASH_DAMAGE);
				}
			}
		});
		return CyclopsAnim.SMASH.ticks + 25;
	}

	/** The Boulder: a red circle where you stand; it lands there (two at once in his rage). */
	private int boulder(ServerLevel level, LivingEntity target) {
		getNavigation().stop();
		lookAtNow(target);
		int release = CyclopsAnim.THROW.at("throw");
		lockedUntil = clock + CyclopsAnim.THROW.ticks;
		play(CyclopsAnim.THROW);
		List<Vec3> marks = new ArrayList<>();
		marks.add(target.position());
		if (enraged) {
			double a = getRandom().nextDouble() * Math.PI * 2;
			marks.add(target.position().add(Math.cos(a) * 5, 0, Math.sin(a) * 5));
		}
		int land = release + BOULDER_FLIGHT;
		for (Vec3 m : marks) {
			Danger.circle(level, Danger.Colour.RED, m, BOULDER_RADIUS, land);
		}
		schedule(release, () -> level.playSound(null, blockPosition(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 2.0f, 0.5f));
		Vec3 from = position().add(0, 4.5, 0);
		for (int t = 0; t < BOULDER_FLIGHT; t += 2) {
			int step = t;
			schedule(release + t, () -> {
				for (Vec3 m : marks) {
					double f = step / (double) BOULDER_FLIGHT;
					Vec3 p = from.lerp(m, f).add(0, 6 * 4 * f * (1 - f), 0);
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BLACKSTONE.defaultBlockState()), p.x, p.y, p.z, 12, 0.4, 0.4,
							0.4, 0.05);
				}
			});
		}
		schedule(land, () -> {
			for (Vec3 m : marks) {
				level.playSound(null, net.minecraft.core.BlockPos.containing(m), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.5f, 0.7f);
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BLACKSTONE.defaultBlockState()), m.x, m.y + 0.5, m.z, 80, 1.2, 0.4,
						1.2, 0.2);
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(m, m).inflate(BOULDER_RADIUS + 0.3, 3,
						BOULDER_RADIUS + 0.3), e -> e != this && e.isAlive())) {
					e.hurtServer(level, Danger.unblockable(level, this), BOULDER_DAMAGE);
				}
			}
		});
		return CyclopsAnim.THROW.ticks + 25;
	}

	/** The Quake: a purple ring round him. Jump as the stamp comes, or keep out of it. */
	private int quake(ServerLevel level) {
		getNavigation().stop();
		Vec3 c = position();
		int stamp = CyclopsAnim.QUAKE.at("stamp");
		lockedUntil = clock + CyclopsAnim.QUAKE.ticks;
		play(CyclopsAnim.QUAKE);
		warn(Warning.PURPLE);
		Danger.ring(level, Danger.Colour.PURPLE, c, QUAKE_INNER, QUAKE_OUTER, stamp);
		schedule(stamp, () -> {
			warn(Warning.NONE);
			level.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.0f, 0.5f);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BLACKSTONE.defaultBlockState()), c.x, c.y + 0.2, c.z, 150, 3.5, 0.2,
					3.5, 0.2);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(QUAKE_OUTER + 0.5), e -> e != this && e.isAlive())) {
				double r = Math.hypot(e.getX() - c.x, e.getZ() - c.z);
				if (r >= QUAKE_INNER - 0.3 && r <= QUAKE_OUTER + 0.3 && e.onGround()) {
					e.hurtServer(level, Danger.unblockable(level, this), QUAKE_DAMAGE);
					e.push(0, 0.5, 0);
				}
			}
		});
		return CyclopsAnim.QUAKE.ticks + 25;
	}

	/** Blocked while he flashed white: down on one knee, open to blows. */
	void stagger(ServerPlayer by) {
		scheduled.clear();
		getNavigation().stop();
		warn(Warning.NONE);
		play(CyclopsAnim.STAGGER);
		staggerUntil = clock + CyclopsAnim.STAGGER.ticks;
		lockedUntil = staggerUntil;
		nextAttack = staggerUntil + 15;
		if (level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 3, getZ(), 30, 1.2, 0.8, 1.2, 0.4);
		}
		by.sendOverlayMessage(Component.literal("Parried! The Cyclops reels: hit him now!").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
	}

	private void lookAtNow(LivingEntity target) {
		float yaw = (float) Math.toDegrees(Math.atan2(target.getZ() - getZ(), target.getX() - getX())) - 90.0f;
		setYRot(yaw);
		setYBodyRot(yaw);
		setYHeadRot(yaw);
	}

	private ServerPlayer nearest(List<ServerPlayer> near) {
		ServerPlayer best = null;
		for (ServerPlayer p : near) {
			if (best == null || p.distanceToSqr(this) < best.distanceToSqr(this)) {
				best = p;
			}
		}
		return best;
	}

	private void schedule(int delay, Runnable action) {
		scheduled.add(new Scheduled(clock + delay, action));
	}

	private void runScheduled() {
		List<Runnable> due = new ArrayList<>();
		for (Iterator<Scheduled> it = scheduled.iterator(); it.hasNext(); ) {
			Scheduled s = it.next();
			if (s.at() <= clock) {
				due.add(s.action());
				it.remove();
			}
		}
		due.forEach(Runnable::run);
	}

	private void updateBar(ServerLevel level, List<ServerPlayer> near) {
		bar.setProgress(Math.max(0, getHealth() / getMaxHealth()));
		Set<ServerPlayer> show = new HashSet<>(awake && isAlive() ? near : List.of());
		for (ServerPlayer p : new ArrayList<>(bar.getPlayers())) {
			if (!show.contains(p)) {
				bar.removePlayer(p);
			}
		}
		for (ServerPlayer p : show) {
			bar.addPlayer(p);
		}
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		bar.removeAllPlayers();
	}

	@Override
	public void remove(RemovalReason reason) {
		bar.removeAllPlayers();
		super.remove(reason);
	}
}
