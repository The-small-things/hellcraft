package net.thesmallthings.hellcraft.hazard.guardian;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.util.Feedback;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Minos (Inferno V), who judges each soul and wraps his tail around himself once for every circle it
 * must descend. He never leaves his seat:
 * <ul>
 *     <li><b>Tail of Judgement</b>: his tail sweeps a full circle along the ground. Jump it.</li>
 *     <li><b>Sentence</b>: he names a soul; three seconds later the hurricane of Lust tears up the
 *     ground where they stand. Keep moving.</li>
 *     <li><b>Coil</b>: he drags the nearest soul into his coils and squeezes, until the others hurt
 *     him enough to make him let go.</li>
 * </ul>
 */
final class MinosFight extends GuardianFight {
	private static final DustParticleOptions JUDGEMENT = new DustParticleOptions(0x6A2A9A, 1.6f);
	private static final int TAIL_LENGTH = 14;
	private int cycle;
	@Nullable
	private UUID coiled;
	private int coilTicks;
	private float coilDamage;

	MinosFight(GuardianContext ctx) {
		super(ctx);
	}

	@Override
	protected Mob createBody() {
		WitherSkeleton body = EntityTypes.WITHER_SKELETON.create(level, EntitySpawnReason.EVENT);
		if (body == null) {
			throw new IllegalStateException("could not create Minos");
		}
		// he sits in judgement: no wandering, every blow is his own
		body.setNoAi(true);
		setBase(body, Attributes.SCALE, 2.6);
		setBase(body, Attributes.ARMOR, 10.0);
		return body;
	}

	@Override
	public List<String> attacks() {
		return List.of("tail", "sentence", "coil");
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
	protected int nextAttack(Mob body, List<LivingEntity> targets) {
		String[] order = coiled == null ? new String[]{"tail", "sentence", "coil"} : new String[]{"tail", "sentence"};
		String attack = order[cycle++ % order.length];
		LivingEntity target = targets.get(level.getRandom().nextInt(targets.size()));
		return perform(attack, body, target);
	}

	@Override
	protected int perform(String attack, Mob body, LivingEntity target) {
		return switch (attack) {
			case "tail" -> tail(body);
			case "sentence" -> sentence(body, target);
			case "coil" -> coil(body);
			default -> -1;
		};
	}

	private int tail(Mob body) {
		say("Stand for judgement!");
		tip("His tail sweeps the ground: JUMP it!");
		Vec3 c = body.position();
		double start = level.getRandom().nextDouble() * Math.PI * 2;
		level.playSound(null, body.blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 2.0f, 0.5f);
		for (int t = 0; t < 20; t += 4) {
			schedule(t, () -> line(c, start, JUDGEMENT));
		}
		Set<UUID> hit = new HashSet<>();
		for (int step = 0; step <= 40; step++) {
			double angle = start + step * Math.PI * 2 / 40;
			schedule(20 + step, () -> {
				line(c, angle, ParticleTypes.SWEEP_ATTACK);
				for (LivingEntity e : victims(c, TAIL_LENGTH + 1)) {
					Vec3 d = e.position().subtract(c);
					double along = d.x * Math.cos(angle) + d.z * Math.sin(angle);
					double across = Math.abs(-d.x * Math.sin(angle) + d.z * Math.cos(angle));
					if (along > 0 && along < TAIL_LENGTH && across < 1.2 && e.onGround() && hit.add(e.getUUID())) {
						e.hurt(level.damageSources().mobAttack(body), 8.0f);
						fling(e, c, 1.2, 0.4);
					}
				}
			});
		}
		return 100;
	}

	private void line(Vec3 c, double angle, ParticleOptions particle) {
		for (int r = 2; r <= TAIL_LENGTH; r += 2) {
			double x = c.x + Math.cos(angle) * r;
			double z = c.z + Math.sin(angle) * r;
			level.sendParticles(particle, x, groundY(x, z) + 0.3, z, 1, 0, 0, 0, 0);
		}
	}

	private int sentence(Mob body, LivingEntity target) {
		int circle = 2 + level.getRandom().nextInt(8);
		say("Circle " + circle + "! Down with thee!");
		if (target instanceof ServerPlayer p) {
			p.sendOverlayMessage(Component.literal("Minos targeted you: MOVE!").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
		}
		for (int t = 0; t < 60; t += 3) {
			schedule(t, () -> ringParticles(JUDGEMENT, target.position(), 2.5, 14));
		}
		schedule(60, () -> {
			Vec3 at = target.position();
			level.playSound(null, target.blockPosition(), SoundEvents.ELYTRA_FLYING, SoundSource.HOSTILE, 3.0f, 0.5f);
			level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 1, at.z, 80, 1.2, 2, 1.2, 0.2);
			for (LivingEntity e : victims(at, 3.0)) {
				e.hurt(level.damageSources().mobAttack(body), 6.0f);
				e.push((level.getRandom().nextDouble() - 0.5) * 2.0, 1.3, (level.getRandom().nextDouble() - 0.5) * 2.0);
				Feedback.syncMotion(e);
			}
		});
		return 90;
	}

	private int coil(Mob body) {
		LivingEntity nearest = null;
		double best = 12 * 12;
		for (LivingEntity e : targets()) {
			double d = e.distanceToSqr(body);
			if (d < best) {
				best = d;
				nearest = e;
			}
		}
		if (nearest == null) {
			return 40;
		}
		say("Around and around... how many circles for thee?");
		tip(nearest.getName().getString() + " is in Minos's coils! Hurt him to break them.");
		coiled = nearest.getUUID();
		coilTicks = 0;
		coilDamage = 0;
		return 120;
	}

	@Override
	protected void everyTick(Mob body) {
		if (coiled == null) {
			return;
		}
		if (!(level.getEntity(coiled) instanceof LivingEntity victim) || !victim.isAlive() || victim.isSpectator()) {
			coiled = null;
			return;
		}
		coilTicks++;
		Vec3 side = victim.position().subtract(body.position()).multiply(1, 0, 1);
		side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
		Vec3 hold = body.position().add(side.scale(2.5)).add(0, 1.0, 0);
		victim.setDeltaMovement(hold.subtract(victim.position()).scale(0.35));
		Feedback.syncMotion(victim);
		victim.resetFallDistance();
		if (coilTicks % 10 == 0) {
			victim.hurt(level.damageSources().mobAttack(body), 2.0f);
			victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 3));
			level.sendParticles(JUDGEMENT, victim.getX(), victim.getY() + 1, victim.getZ(), 16, 0.5, 0.6, 0.5, 0);
		}
		if (coilDamage >= 15.0f || coilTicks >= 80) {
			coiled = null;
			fling(victim, body.position(), 1.3, 0.5);
			say(coilDamage >= 15.0f ? "AAH! Insolent!" : "Go, then. Thy circle awaits.");
		}
	}

	@Override
	protected void onHurt(float amount) {
		if (coiled != null) {
			coilDamage += amount;
		}
	}
}
