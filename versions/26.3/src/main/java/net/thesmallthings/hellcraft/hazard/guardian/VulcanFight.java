package net.thesmallthings.hellcraft.hazard.guardian;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Vulcan (Inferno XIV), smith of the gods, at the Great Forge of Dis: "if he weary his smiths one after
 * another at the black forge of Mongibello..."
 * <ul>
 *     <li><b>Hammerfall</b>: a ring of sparks under his chosen victim; then the hammer comes down and a
 *     shockwave rolls out. Get out of the ring, then jump the wave.</li>
 *     <li><b>Molten Rain</b>: glowing marks on the floor around every player; molten iron pours onto them.</li>
 *     <li><b>Forge-born</b>: two smiths of Dis (wither skeletons in iron) step out of the fire.</li>
 *     <li><b>Chains</b>: he hauls his victim to the anvil. Break away before he swings.</li>
 * </ul>
 */
final class VulcanFight extends GuardianFight {
	private static final DustParticleOptions SPARK = new DustParticleOptions(0xFF8A1A, 1.5f);
	private int cycle;

	VulcanFight(GuardianContext ctx) {
		super(ctx);
	}

	@Override
	protected Mob createBody() {
		Mob body = EntityTypes.VINDICATOR.create(level, EntitySpawnReason.EVENT);
		if (body == null) {
			throw new IllegalStateException("could not create Vulcan");
		}
		setBase(body, Attributes.SCALE, 2.6);
		setBase(body, Attributes.ATTACK_DAMAGE, 12.0);
		setBase(body, Attributes.KNOCKBACK_RESISTANCE, 1.0);
		body.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
		return body;
	}

	@Override
	public List<String> attacks() {
		return List.of("hammerfall", "molten", "forgeborn", "chains");
	}

	@Override
	protected String introLine() {
		return "Who comes to my forge unbidden? I made the thunderbolts of Jove. I will unmake you.";
	}

	@Override
	protected String deathLine() {
		return "*The hammer falls from his hand, and the fires of the forge sink to embers.*";
	}

	/** The floor of the Great Forge (the Nether's heightmap would find the roof of the world). */
	@Override
	protected double groundY(double x, double z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos((int) Math.floor(x), lair.getY() + 6, (int) Math.floor(z));
		for (int i = 0; i < 14; i++) {
			if (!level.getBlockState(pos.below()).isAir()) {
				return pos.getY();
			}
			pos.move(0, -1, 0);
		}
		return lair.getY();
	}

	@Override
	protected int nextAttack(Mob body, List<LivingEntity> targets) {
		String attack = switch (cycle++ % 5) {
			case 0, 3 -> "hammerfall";
			case 1 -> "molten";
			case 2 -> "chains";
			default -> "forgeborn";
		};
		return perform(attack, body, targets.get(level.getRandom().nextInt(targets.size())));
	}

	@Override
	protected int perform(String attack, Mob body, LivingEntity target) {
		return switch (attack) {
			case "hammerfall" -> hammerfall(body, target);
			case "molten" -> molten(body);
			case "forgeborn" -> forgeborn(body);
			case "chains" -> chains(body, target);
			default -> -1;
		};
	}

	private int hammerfall(Mob body, LivingEntity target) {
		say("Stand still upon my anvil!");
		tip("Get out of the ring of sparks, then jump the shockwave!");
		Vec3 mark = target.position();
		for (int t = 0; t < 28; t += 2) {
			schedule(t, () -> ringParticles(SPARK, mark, 3.5, 20));
		}
		level.playSound(null, body.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.HOSTILE, 2.0f, 0.5f);
		schedule(30, () -> {
			Mob b = body();
			if (b == null) {
				return;
			}
			b.teleportTo(mark.x, groundY(mark.x, mark.z), mark.z);
			level.playSound(null, b.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 3.0f, 0.4f);
			level.sendParticles(ParticleTypes.LAVA, mark.x, mark.y + 0.5, mark.z, 30, 1.5, 0.3, 1.5, 0);
			for (LivingEntity e : victims(mark, 3.5)) {
				e.hurt(level.damageSources().mobAttack(b), 12.0f);
			}
			shockwave(b, mark, 12, 6.0f, ParticleTypes.FLAME);
		});
		return 80;
	}

	private int molten(Mob body) {
		say("Drink the iron of my crucible!");
		tip("Molten iron pours where the floor glows: move!");
		level.playSound(null, body.blockPosition(), SoundEvents.LAVA_AMBIENT, SoundSource.HOSTILE, 3.0f, 0.6f);
		List<Vec3> marks = new ArrayList<>();
		for (LivingEntity t : targets()) {
			marks.add(t.position());
			for (int i = 0; i < 2; i++) {
				double a = level.getRandom().nextDouble() * Math.PI * 2;
				marks.add(t.position().add(Math.cos(a) * 4, 0, Math.sin(a) * 4));
			}
		}
		for (int t = 0; t < 30; t += 3) {
			schedule(t, () -> {
				for (Vec3 m : marks) {
					level.sendParticles(ParticleTypes.FLAME, m.x, groundY(m.x, m.z) + 0.1, m.z, 6, 0.8, 0, 0.8, 0);
				}
			});
		}
		schedule(32, () -> {
			Mob b = body();
			for (Vec3 m : marks) {
				level.sendParticles(ParticleTypes.FALLING_LAVA, m.x, groundY(m.x, m.z) + 6, m.z, 40, 0.8, 0.5, 0.8, 0);
				level.sendParticles(ParticleTypes.LAVA, m.x, groundY(m.x, m.z) + 0.3, m.z, 12, 0.8, 0.2, 0.8, 0);
				for (LivingEntity e : victims(m, 2.0)) {
					e.hurt(b != null ? level.damageSources().mobAttack(b) : level.damageSources().lava(), 7.0f);
					e.igniteForSeconds(4.0f);
				}
			}
		});
		return 70;
	}

	private int forgeborn(Mob body) {
		say("Up, my smiths! There is work at the anvil!");
		tip("Smiths of Dis step out of the fire.");
		for (int i = 0; i < 2; i++) {
			Mob smith = EntityTypes.WITHER_SKELETON.create(level, EntitySpawnReason.EVENT);
			if (smith == null) {
				continue;
			}
			double a = level.getRandom().nextDouble() * Math.PI * 2;
			double x = lair.getX() + 0.5 + Math.cos(a) * 8;
			double z = lair.getZ() + 0.5 + Math.sin(a) * 8;
			smith.snapTo(x, groundY(x, z), z, 0, 0);
			smith.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			smith.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
			smith.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
			smith.addTag(TAG);
			smith.setCustomName(Component.literal("Smith of Dis").withStyle(ChatFormatting.GOLD));
			level.addFreshEntity(smith);
			level.sendParticles(ParticleTypes.FLAME, x, smith.getY() + 1, z, 40, 0.4, 0.8, 0.4, 0.05);
		}
		return 90;
	}

	private int chains(Mob body, LivingEntity target) {
		say("Come here, to the anvil.");
		if (target instanceof ServerPlayer p) {
			p.sendOverlayMessage(Component.literal("Vulcan's chains drag you in: break away!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		}
		level.playSound(null, target.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.HOSTILE, 1.5f, 1.6f);
		Vec3 to = body.position().subtract(target.position());
		fling(target, target.position().subtract(to), 1.6, 0.4);
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 2));
		for (int i = 0; i <= 10; i++) {
			Vec3 p = target.position().add(to.scale(i / 10.0));
			level.sendParticles(ParticleTypes.SMOKE, p.x, p.y + 1, p.z, 2, 0.05, 0.05, 0.05, 0);
		}
		return 60;
	}
}
