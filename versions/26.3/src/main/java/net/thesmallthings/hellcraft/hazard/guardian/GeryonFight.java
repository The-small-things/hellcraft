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
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Geryon (Inferno XVII), "the beast with the pointed tail, that passes mountains and breaks walls and
 * weapons": the face of a just man on a serpent's painted body, swimming through the air at the edge
 * of the great cliff.
 * <ul>
 *     <li><b>Sting</b>: a red ring follows its chosen victim; then Geryon drops out of the sky onto it,
 *     tail first. Poison and withering for whoever is still in the ring.</li>
 *     <li><b>False Face</b>: it vanishes and reappears elsewhere, leaving frauds (vexes) behind.</li>
 * </ul>
 */
final class GeryonFight extends GuardianFight {
	private static final DustParticleOptions STING = new DustParticleOptions(0xC02020, 1.6f);
	private final List<UUID> frauds = new ArrayList<>();
	private int cycle;

	GeryonFight(GuardianContext ctx) {
		super(ctx);
	}

	@Override
	protected Mob createBody() {
		Mob body = EntityTypes.PHANTOM.create(level, EntitySpawnReason.EVENT);
		if (body == null) {
			throw new IllegalStateException("could not create Geryon");
		}
		setBase(body, Attributes.SCALE, 3.0);
		setBase(body, Attributes.ATTACK_DAMAGE, 8.0);
		return body;
	}

	@Override
	protected boolean flies() {
		return true;
	}

	@Override
	public List<String> attacks() {
		return List.of("sting", "falseface");
	}

	@Override
	protected String introLine() {
		return "Behold the beast with the pointed tail, that passes mountains, and breaks walls and weapons!";
	}

	@Override
	protected String deathLine() {
		return "*The image of fraud unravels, and its painted hide is only paint.*";
	}

	@Override
	protected int nextAttack(Mob body, List<LivingEntity> targets) {
		String attack = cycle++ % 3 == 2 ? "falseface" : "sting";
		return perform(attack, body, targets.get(level.getRandom().nextInt(targets.size())));
	}

	@Override
	protected int perform(String attack, Mob body, LivingEntity target) {
		return switch (attack) {
			case "sting" -> sting(target);
			case "falseface" -> falseFace(body);
			default -> -1;
		};
	}

	private int sting(LivingEntity target) {
		say("Be still. It will only sting once.");
		if (target instanceof ServerPlayer p) {
			p.sendOverlayMessage(Component.literal("Geryon's tail is poised over you: MOVE!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
		}
		Vec3[] mark = {target.position()};
		for (int t = 0; t < 30; t += 2) {
			schedule(t, () -> {
				// the ring follows the victim until the last moment
				if (target.isAlive()) {
					mark[0] = target.position();
				}
				ringParticles(STING, mark[0], 2.5, 14);
			});
		}
		for (int step = 0; step <= 6; step++) {
			int s = step;
			schedule(30 + step, () -> {
				Mob b = body();
				if (b == null) {
					return;
				}
				Vec3 m = mark[0];
				b.teleportTo(m.x, m.y + 7 - s, m.z);
				if (s == 6) {
					level.playSound(null, b.blockPosition(), SoundEvents.PHANTOM_BITE, SoundSource.HOSTILE, 2.5f, 0.5f);
					level.sendParticles(STING, m.x, m.y + 0.5, m.z, 40, 1.2, 0.4, 1.2, 0);
					for (LivingEntity e : victims(m, 2.5)) {
						e.hurt(level.damageSources().mobAttack(b), 7.0f);
						e.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
						e.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
					}
				}
			});
		}
		return 80;
	}

	private int falseFace(Mob body) {
		say("Which face is mine? Which is thine?");
		tip("Frauds! Geryon hides among them.");
		level.playSound(null, body.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 2.0f, 0.8f);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, body.getX(), body.getY() + 1, body.getZ(), 60, 1.5, 1, 1.5, 0.05);
		for (int i = 0; i < 3; i++) {
			Mob fraud = EntityTypes.VEX.create(level, EntitySpawnReason.EVENT);
			if (fraud == null) {
				continue;
			}
			fraud.snapTo(body.getX() + (level.getRandom().nextDouble() - 0.5) * 4, body.getY() + 1, body.getZ() + (level.getRandom().nextDouble() - 0.5) * 4, 0, 0);
			fraud.addTag(TAG);
			fraud.setCustomName(Component.literal("Fraud").withStyle(ChatFormatting.DARK_AQUA));
			level.addFreshEntity(fraud);
			frauds.add(fraud.getUUID());
		}
		double a = level.getRandom().nextDouble() * Math.PI * 2;
		double r = 8 + level.getRandom().nextDouble() * 10;
		double x = lair.getX() + 0.5 + Math.cos(a) * r;
		double z = lair.getZ() + 0.5 + Math.sin(a) * r;
		body.teleportTo(x, groundY(x, z) + 6, z);
		schedule(300, () -> {
			for (UUID id : frauds) {
				if (level.getEntity(id) instanceof Mob m) {
					level.sendParticles(ParticleTypes.POOF, m.getX(), m.getY(), m.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
					m.discard();
				}
			}
			frauds.clear();
		});
		return 90;
	}
}
