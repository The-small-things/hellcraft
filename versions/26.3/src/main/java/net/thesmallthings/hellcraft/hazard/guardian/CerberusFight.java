package net.thesmallthings.hellcraft.hazard.guardian;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Cerberus (Inferno VI), the great worm of the third circle, who barks with three throats over the
 * gluttons in the rain. He hunts like a ravager, and:
 * <ul>
 *     <li><b>Three Maws</b>: a growl, then three bites in the cone in front of him. Get behind him.</li>
 *     <li><b>Filth</b>: he flings the mud of the circle; where it lands, it clings and slows.</li>
 *     <li><b>Howl</b>: three throats at once, stunning everyone near.</li>
 * </ul>
 */
final class CerberusFight extends GuardianFight {
	private static final DustParticleOptions MUD = new DustParticleOptions(0x4A3A1A, 1.8f);
	private static final DustParticleOptions MAW = new DustParticleOptions(0xB01010, 1.4f);
	private int cycle;
	/** Puddles of filth: centre and the tick they dry up. */
	private final List<Puddle> puddles = new ArrayList<>();

	private record Puddle(Vec3 at, int until) {
	}

	CerberusFight(GuardianContext ctx) {
		super(ctx);
	}

	@Override
	protected Mob createBody() {
		Mob body = EntityTypes.RAVAGER.create(level, EntitySpawnReason.EVENT);
		if (body == null) {
			throw new IllegalStateException("could not create Cerberus");
		}
		setBase(body, Attributes.SCALE, 1.5);
		setBase(body, Attributes.ATTACK_DAMAGE, 10.0);
		return body;
	}

	@Override
	public List<String> attacks() {
		return List.of("maws", "filth", "howl");
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
	protected int nextAttack(Mob body, List<LivingEntity> targets) {
		String attack = attacks().get(cycle++ % 3);
		return perform(attack, body, targets.get(level.getRandom().nextInt(targets.size())));
	}

	@Override
	protected int perform(String attack, Mob body, LivingEntity target) {
		return switch (attack) {
			case "maws" -> maws(body, target);
			case "filth" -> filth(body, target);
			case "howl" -> howl(body);
			default -> -1;
		};
	}

	private int maws(Mob body, LivingEntity target) {
		say("GRRR...");
		tip("Three heads bite in front of him: get behind him!");
		body.getLookControl().setLookAt(target);
		level.playSound(null, body.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.5f, 1.4f);
		for (int bite = 0; bite < 3; bite++) {
			int delay = 15 + bite * 6;
			schedule(delay - 10, () -> cone(body, MAW));
			schedule(delay, () -> {
				level.playSound(null, body.blockPosition(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 2.0f, 0.8f);
				cone(body, ParticleTypes.CRIT);
				Vec3 facing = Vec3.directionFromRotation(0, body.getYRot()).multiply(1, 0, 1).normalize();
				for (LivingEntity e : victims(body.position(), 6.0)) {
					Vec3 to = e.position().subtract(body.position()).multiply(1, 0, 1);
					if (to.lengthSqr() < 1.0e-4 || to.normalize().dot(facing) > 0.5) {
						e.hurt(level.damageSources().mobAttack(body), 6.0f);
					}
				}
			});
		}
		return 70;
	}

	private void cone(Mob body, ParticleOptions particle) {
		Vec3 facing = Vec3.directionFromRotation(0, body.getYRot()).multiply(1, 0, 1).normalize();
		for (int i = -3; i <= 3; i++) {
			double a = Math.atan2(facing.z, facing.x) + i * 0.17;
			for (int r = 2; r <= 5; r++) {
				level.sendParticles(particle, body.getX() + Math.cos(a) * r, body.getY() + 1, body.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
			}
		}
	}

	private int filth(Mob body, LivingEntity target) {
		say("*He shakes the filth of the circle from his hide*");
		tip("Filth! Stay out of the mud.");
		level.playSound(null, body.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 2.0f, 0.5f);
		List<Vec3> spots = new ArrayList<>();
		spots.add(target.position());
		for (int i = 0; i < 5; i++) {
			double a = level.getRandom().nextDouble() * Math.PI * 2;
			double r = 3 + level.getRandom().nextDouble() * 12;
			double x = lair.getX() + 0.5 + Math.cos(a) * r;
			double z = lair.getZ() + 0.5 + Math.sin(a) * r;
			spots.add(new Vec3(x, groundY(x, z), z));
		}
		for (Vec3 s : spots) {
			schedule(20, () -> {
				level.sendParticles(ParticleTypes.ITEM_SLIME, s.x, s.y + 0.5, s.z, 30, 1.2, 0.3, 1.2, 0.1);
				puddles.add(new Puddle(s, tick + 120));
			});
		}
		return 80;
	}

	private int howl(Mob body) {
		say("AROOOOOOO!");
		tip("He's about to roar: get away from him!");
		for (int t = 0; t < 20; t += 4) {
			schedule(t, () -> ringParticles(ParticleTypes.SONIC_BOOM, body.position(), 10, 12));
		}
		schedule(20, () -> {
			level.playSound(null, body.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0f, 0.6f);
			for (LivingEntity e : victims(body.position(), 10.0)) {
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 5));
				e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
				e.hurt(level.damageSources().mobAttack(body), 3.0f);
			}
		});
		return 80;
	}

	@Override
	protected void everyTick(Mob body) {
		puddles.removeIf(p -> p.until() < tick);
		if (tick % 5 != 0) {
			return;
		}
		for (Puddle p : puddles) {
			level.sendParticles(MUD, p.at().x, p.at().y + 0.1, p.at().z, 8, 1.2, 0.05, 1.2, 0);
			for (LivingEntity e : victims(p.at(), 2.2)) {
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 2));
				e.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 80, 0));
			}
		}
	}
}
