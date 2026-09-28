package net.thesmallthings.hellcraft.hazard.guardian;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The Minotaur (Inferno XII), the infamy of Crete, raging on the ruined slope into the seventh circle.
 * <ul>
 *     <li><b>Charge</b>: he paws the ground, a line of fire marks his path, and he tramples everything
 *     on it.</li>
 *     <li><b>Stomp</b>: he rears and slams down; a shockwave rolls out along the ground. Jump it.</li>
 *     <li>Below a third of his health he goes berserk: faster, stronger, charging more often.</li>
 * </ul>
 */
final class MinotaurFight extends GuardianFight {
	private int cycle;
	private boolean berserk;

	MinotaurFight(GuardianContext ctx) {
		super(ctx);
	}

	@Override
	protected Mob createBody() {
		Mob body = EntityTypes.HOGLIN.create(level, EntitySpawnReason.EVENT);
		if (body == null) {
			throw new IllegalStateException("could not create the Minotaur");
		}
		setBase(body, Attributes.SCALE, 2.2);
		setBase(body, Attributes.ATTACK_DAMAGE, 12.0);
		return body;
	}

	@Override
	public List<String> attacks() {
		return List.of("charge", "stomp");
	}

	@Override
	protected String introLine() {
		return "*The infamy of Crete bellows, and bites itself in its rage* HHRRRAAAAGH!";
	}

	@Override
	protected String deathLine() {
		return "*Like a bull that breaks loose at the moment it takes the mortal blow, it plunges this way and that, and falls.*";
	}

	@Override
	protected int nextAttack(Mob body, List<LivingEntity> targets) {
		String attack = berserk && cycle % 3 != 2 ? "charge" : attacks().get(cycle % 2);
		cycle++;
		int cd = perform(attack, body, targets.get(level.getRandom().nextInt(targets.size())));
		return berserk ? (int) (cd * 0.65) : cd;
	}

	@Override
	protected int perform(String attack, Mob body, LivingEntity target) {
		return switch (attack) {
			case "charge" -> charge(body, target);
			case "stomp" -> stomp(body);
			default -> -1;
		};
	}

	private int charge(Mob body, LivingEntity target) {
		Vec3 from = body.position();
		Vec3 dir = target.position().subtract(from).multiply(1, 0, 1);
		if (dir.lengthSqr() < 1.0e-4) {
			dir = new Vec3(1, 0, 0);
		}
		Vec3 line = dir.normalize();
		say("HRRR!");
		tip("He paws the ground... get off the burning line!");
		level.playSound(null, body.blockPosition(), SoundEvents.HOGLIN_ANGRY, SoundSource.HOSTILE, 2.5f, 0.5f);
		body.setNoAi(true);
		for (int t = 0; t < 24; t += 3) {
			schedule(t, () -> {
				for (int r = 1; r <= 18; r++) {
					Vec3 p = from.add(line.scale(r));
					level.sendParticles(ParticleTypes.FLAME, p.x, groundY(p.x, p.z) + 0.15, p.z, 1, 0.15, 0, 0.15, 0);
				}
			});
		}
		Set<UUID> hit = new HashSet<>();
		for (int step = 0; step < 10; step++) {
			int s = step;
			schedule(24 + step, () -> {
				Mob b = body();
				if (b == null) {
					return;
				}
				Vec3 p = from.add(line.scale(2 + s * 1.8));
				b.teleportTo(p.x, groundY(p.x, p.z), p.z);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y + 0.5, p.z, 6, 0.6, 0.2, 0.6, 0.02);
				for (LivingEntity e : victims(p, 2.8)) {
					if (hit.add(e.getUUID())) {
						e.hurt(level.damageSources().mobAttack(b), 10.0f);
						fling(e, p, 2.0, 0.7);
					}
				}
			});
		}
		schedule(35, () -> {
			Mob b = body();
			if (b != null) {
				b.setNoAi(false);
			}
		});
		return 70;
	}

	private int stomp(Mob body) {
		say("*It rears up on its hind legs*");
		tip("A stomp is coming: jump the shockwave!");
		level.playSound(null, body.blockPosition(), SoundEvents.HOGLIN_ATTACK, SoundSource.HOSTILE, 2.5f, 0.4f);
		body.push(0, 0.9, 0);
		schedule(18, () -> {
			Mob b = body();
			if (b == null) {
				return;
			}
			level.playSound(null, b.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.5f, 0.6f);
			level.sendParticles(ParticleTypes.EXPLOSION, b.getX(), b.getY() + 0.5, b.getZ(), 3, 1, 0.2, 1, 0);
			shockwave(b, b.position(), 14, 8.0f, ParticleTypes.CAMPFIRE_COSY_SMOKE);
		});
		return 80;
	}

	@Override
	protected void everyTick(Mob body) {
		if (!berserk && body.getHealth() < body.getMaxHealth() / 3) {
			berserk = true;
			say("*Blood in its eyes, the beast goes berserk!*");
			body.addEffect(new MobEffectInstance(MobEffects.SPEED, MobEffectInstance.INFINITE_DURATION, 1, false, false));
			body.addEffect(new MobEffectInstance(MobEffects.STRENGTH, MobEffectInstance.INFINITE_DURATION, 0, false, false));
		}
	}
}
