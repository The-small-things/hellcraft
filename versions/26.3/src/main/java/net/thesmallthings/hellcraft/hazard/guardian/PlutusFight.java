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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Plutus (Inferno VII), the great enemy, a swollen wolf that guards the hoarders and the wasters.
 * <ul>
 *     <li><b>Weight of Gold</b>: gold rains where he points, and the more gold you carry, the harder
 *     it pins you.</li>
 *     <li><b>Lunge</b>: a line of gold dust marks his path; a moment later he hurls himself along it.</li>
 *     <li><b>Pape Satàn</b>: his gibbering shriek scatters and sickens everyone close.</li>
 * </ul>
 */
final class PlutusFight extends GuardianFight {
	private static final DustParticleOptions GOLD = new DustParticleOptions(0xE8B923, 1.6f);
	private int cycle;

	PlutusFight(GuardianContext ctx) {
		super(ctx);
	}

	@Override
	protected Mob createBody() {
		Mob body = EntityTypes.PIGLIN_BRUTE.create(level, EntitySpawnReason.EVENT);
		if (body == null) {
			throw new IllegalStateException("could not create Plutus");
		}
		setBase(body, Attributes.SCALE, 2.2);
		setBase(body, Attributes.ATTACK_DAMAGE, 9.0);
		body.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_AXE));
		return body;
	}

	@Override
	public List<String> attacks() {
		return List.of("gold", "lunge", "pape");
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
	protected int nextAttack(Mob body, List<LivingEntity> targets) {
		String attack = attacks().get(cycle++ % 3);
		return perform(attack, body, targets.get(level.getRandom().nextInt(targets.size())));
	}

	@Override
	protected int perform(String attack, Mob body, LivingEntity target) {
		return switch (attack) {
			case "gold" -> gold(body);
			case "lunge" -> lunge(body, target);
			case "pape" -> pape(body);
			default -> -1;
		};
	}

	/** Gold carried, in ingots (blocks count nine). */
	private static int goldCarried(LivingEntity e) {
		if (!(e instanceof Player p)) {
			return 0;
		}
		int gold = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (s.is(Items.GOLD_INGOT) || s.is(Items.RAW_GOLD)) {
				gold += s.getCount();
			} else if (s.is(Items.GOLD_BLOCK) || s.is(Items.RAW_GOLD_BLOCK)) {
				gold += s.getCount() * 9;
			}
		}
		return gold;
	}

	private int gold(Mob body) {
		say("MINE! All of it, MINE!");
		tip("Gold rains down! Leave the glittering rings (the more gold you carry, the worse).");
		level.playSound(null, body.blockPosition(), SoundEvents.ARMOR_EQUIP_GOLD.value(), SoundSource.HOSTILE, 2.0f, 0.5f);
		List<Vec3> marks = new ArrayList<>();
		for (LivingEntity t : targets()) {
			marks.add(t.position());
			for (int k = 0; k < 2; k++) {
				double x = t.getX() + (level.getRandom().nextDouble() - 0.5) * 8;
				double z = t.getZ() + (level.getRandom().nextDouble() - 0.5) * 8;
				marks.add(new Vec3(x, groundY(x, z), z));
			}
		}
		for (int t = 0; t < 30; t += 3) {
			schedule(t, () -> {
				for (Vec3 m : marks) {
					ringParticles(GOLD, m, 2.0, 10);
					level.sendParticles(GOLD, m.x, m.y + 8, m.z, 4, 0.6, 0.5, 0.6, 0);
				}
			});
		}
		schedule(32, () -> {
			for (Vec3 m : marks) {
				level.sendParticles(ParticleTypes.CRIT, m.x, m.y + 0.5, m.z, 20, 1, 0.3, 1, 0.2);
				for (LivingEntity e : victims(m, 2.3)) {
					int weight = goldCarried(e);
					e.hurt(level.damageSources().mobAttack(body), 5.0f + Math.min(6, weight / 16));
					e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, Math.min(4, 1 + weight / 32)));
				}
			}
			level.playSound(null, body.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.5f, 1.4f);
		});
		return 80;
	}

	private int lunge(Mob body, LivingEntity target) {
		Vec3 from = body.position();
		Vec3 dir = target.position().subtract(from).multiply(1, 0, 1);
		if (dir.lengthSqr() < 1.0e-4) {
			dir = new Vec3(1, 0, 0);
		}
		Vec3 line = dir.normalize();
		say("Hoarders! Wasters! I'll have it back!");
		tip("He is about to lunge: step off the golden line!");
		for (int t = 0; t < 20; t += 3) {
			schedule(t, () -> {
				for (int r = 1; r <= 14; r++) {
					Vec3 p = from.add(line.scale(r));
					level.sendParticles(GOLD, p.x, groundY(p.x, p.z) + 0.2, p.z, 1, 0.1, 0, 0.1, 0);
				}
			});
		}
		Set<UUID> hit = new HashSet<>();
		for (int step = 0; step < 8; step++) {
			int s = step;
			schedule(20 + step, () -> {
				Mob b = body();
				if (b == null) {
					return;
				}
				Vec3 p = from.add(line.scale(2 + s * 1.6));
				b.teleportTo(p.x, groundY(p.x, p.z), p.z);
				level.sendParticles(ParticleTypes.CLOUD, p.x, p.y + 0.5, p.z, 6, 0.5, 0.2, 0.5, 0.02);
				for (LivingEntity e : victims(p, 2.5)) {
					if (hit.add(e.getUUID())) {
						e.hurt(level.damageSources().mobAttack(b), 9.0f);
						fling(e, p, 1.4, 0.5);
					}
				}
			});
		}
		return 70;
	}

	private int pape(Mob body) {
		say("PAPE SATÀN, PAPE SATÀN ALEPPE!");
		for (int t = 0; t < 15; t += 3) {
			schedule(t, () -> ringParticles(ParticleTypes.ANGRY_VILLAGER, body.position(), 8, 12));
		}
		schedule(15, () -> {
			level.playSound(null, body.blockPosition(), SoundEvents.PIGLIN_BRUTE_ANGRY, SoundSource.HOSTILE, 3.0f, 0.5f);
			for (LivingEntity e : victims(body.position(), 8.0)) {
				e.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 120, 0));
				e.hurt(level.damageSources().mobAttack(body), 4.0f);
				fling(e, body.position(), 1.6, 0.4);
				if (e instanceof ServerPlayer p) {
					p.sendOverlayMessage(Component.literal("The gibbering fills your head...").withStyle(ChatFormatting.GOLD));
				}
			}
		});
		return 70;
	}
}
