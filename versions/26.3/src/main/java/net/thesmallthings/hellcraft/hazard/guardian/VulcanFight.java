package net.thesmallthings.hellcraft.hazard.guardian;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.cutscene.Cutscene;
import net.thesmallthings.hellcraft.cutscene.Cutscenes;
import net.thesmallthings.hellcraft.entity.HellEntities;
import net.thesmallthings.hellcraft.entity.boss.BossEntity;
import net.thesmallthings.hellcraft.entity.boss.VulcanEntity;
import net.thesmallthings.hellcraft.entity.boss.gen.VulcanAnim;
import net.thesmallthings.hellcraft.hazard.BossFx;
import net.thesmallthings.hellcraft.hazard.Danger;

import java.util.ArrayList;
import java.util.List;

/**
 * Vulcan (Inferno XIV), smith of the gods, at the Great Forge of Dis: "if he weary his smiths one after another at
 * the black forge of Mongibello..." He limps after you round his hall, and every blow speaks the danger language:
 * <ul>
 *     <li><b>The Strike</b> (a white wedge): the hammer goes up over his head, flashes white, and comes down in front of
 *     him. Block it as it lands and he goes down on one knee, open to blows.</li>
 *     <li><b>Hammerfall</b> (a red circle, then a purple ring): he leaps and comes down on his mark, and a shockwave rolls
 *     out across the floor. Get out of the circle, then jump the wave.</li>
 *     <li><b>Molten Rain</b> (red circles): he upends his crucible; iron pours where the floor glows round each of you.</li>
 *     <li><b>The Chains</b> (a red line): he flings his chain along it; whoever it catches is dragged to his feet.</li>
 *     <li><b>Forge-born</b>: his smiths step out of the fire. Kill them.</li>
 * </ul>
 * At half his health he drives his hammer into the floor: from then on the seams of the floor (red lines through the
 * middle) erupt between his blows.
 */
final class VulcanFight extends GuardianFight {
	private static final double STRIKE_REACH = 7.0;
	private static final double STRIKE_ARC = 0.7;
	private static final float STRIKE_DAMAGE = 14.0f;
	private static final double FALL_RADIUS = 3.5;
	private static final float FALL_DAMAGE = 12.0f;
	private static final int WAVE_RADIUS = 14;
	private static final float WAVE_DAMAGE = 7.0f;
	private static final double RAIN_RADIUS = 2.2;
	private static final float RAIN_DAMAGE = 8.0f;
	private static final double CHAIN_REACH = 18.0;
	private static final double CHAIN_WIDTH = 1.2;
	private static final double SEAM_REACH = 19.0;
	private static final double SEAM_WIDTH = 1.0;
	private static final int SEAM_WARN = 30;
	private static final float SEAM_DAMAGE = 9.0f;
	/** Close enough to strike: he stops walking. */
	private static final double CLOSE = 5.0;

	private int cycle;
	private boolean enraged;
	private int lockedUntil;
	private int staggerUntil = -1;
	private int nextSeams = -1;

	VulcanFight(GuardianContext ctx) {
		super(ctx);
	}

	@Override
	protected Mob createBody() {
		VulcanEntity body = HellEntities.VULCAN.create(level, EntitySpawnReason.EVENT);
		if (body == null) {
			throw new IllegalStateException("could not create Vulcan");
		}
		return body;
	}

	private static VulcanEntity vulcan(Mob body) {
		return (VulcanEntity) body;
	}

	@Override
	public List<String> attacks() {
		return List.of("strike", "hammerfall", "molten", "chains", "forgeborn", "seams");
	}

	@Override
	protected String introLine() {
		return "Who comes to my forge unbidden? I made the thunderbolts of Jove. I will unmake you.";
	}

	@Override
	protected String deathLine() {
		return "*The hammer falls from his hand, and the fires of the forge sink to embers.*";
	}

	@Override
	protected Cutscene introCutscene() {
		return Cutscenes.VULCAN_INTRO;
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
	protected int intro(Mob body) {
		LivingEntity first = nearest(body, targets());
		if (first != null) {
			face(body, first);
		}
		vulcan(body).play(VulcanAnim.INTRO);
		lockedUntil = tick + VulcanAnim.INTRO.ticks;
		for (String clang : new String[]{"clang", "clang2", "clang3"}) {
			schedule(VulcanAnim.INTRO.at(clang), () -> {
				level.playSound(null, body.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.HOSTILE, 2.5f, 0.6f);
				BossFx.send(level, ParticleTypes.LAVA, body.getX(), body.getY() + 1.5, body.getZ(), 8, 1.0, 0.3, 1.0, 0.0);
			});
		}
		schedule(VulcanAnim.INTRO.at("roar"), () -> level.playSound(null, body.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0f, 0.4f));
		return VulcanAnim.INTRO.ticks;
	}

	// ------------------------------------------------------------------ choosing

	@Override
	protected int nextAttack(Mob body, List<LivingEntity> targets) {
		LivingEntity nearest = nearest(body, targets);
		if (nearest == null) {
			return 20;
		}
		String attack;
		if (nearest.distanceTo(body) > 10) {
			// out of reach: he comes down on them, or drags them in
			attack = cycle++ % 2 == 0 ? "hammerfall" : "chains";
		} else {
			String[] order = enraged ? new String[]{"strike", "molten", "hammerfall", "strike", "chains", "forgeborn"}
					: new String[]{"strike", "hammerfall", "strike", "molten", "chains", "forgeborn"};
			attack = order[cycle++ % order.length];
		}
		int cooldown = perform(attack, body, nearest);
		return enraged ? Math.max(cooldown * 3 / 4, 25) : cooldown;
	}

	@Override
	protected int perform(String attack, Mob body, LivingEntity target) {
		return switch (attack) {
			case "strike" -> strike(body, target);
			case "hammerfall" -> hammerfall(body, target);
			case "molten" -> molten(body);
			case "chains" -> chains(body, target);
			case "forgeborn" -> forgeborn(body);
			case "seams" -> seams(body);
			default -> -1;
		};
	}

	// ------------------------------------------------------------------ the attacks

	/** The Strike: a white wedge. Block the hammer as it lands and he reels. */
	private int strike(Mob body, LivingEntity target) {
		VulcanEntity vulcan = vulcan(body);
		stop(body);
		face(body, target);
		double angle = Danger.facing(body);
		Vec3 c = body.position();
		int flash = VulcanAnim.STRIKE.at("flash");
		int hit = VulcanAnim.STRIKE.at("hit");
		lockedUntil = tick + VulcanAnim.STRIKE.ticks;
		vulcan.play(VulcanAnim.STRIKE);
		schedule(flash, () -> {
			vulcan.warn(BossEntity.Warning.WHITE);
			openParry(hit - flash + 3);
			Danger.cone(level, Danger.Colour.WHITE, c, angle, STRIKE_REACH, STRIKE_ARC, hit - flash);
		});
		schedule(hit, () -> {
			vulcan.warn(BossEntity.Warning.NONE);
			level.playSound(null, body.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 2.5f, 0.5f);
			Vec3 front = c.add(Math.cos(angle) * 3.5, 0, Math.sin(angle) * 3.5);
			BossFx.send(level, ParticleTypes.LAVA, front.x, front.y + 0.3, front.z, 16, 1.2, 0.2, 1.2, 0.0);
			for (LivingEntity e : inWedge(c, angle, STRIKE_REACH, STRIKE_ARC)) {
				if (e.hurtServer(level, level.damageSources().mobAttack(body), STRIKE_DAMAGE)) {
					fling(e, c, 0.8, 0.6);
				}
			}
		});
		return VulcanAnim.STRIKE.ticks + 20;
	}

	/** Hammerfall: a red circle where he'll land, then a purple ring: the shockwave rolls out, jump it. */
	private int hammerfall(Mob body, LivingEntity target) {
		VulcanEntity vulcan = vulcan(body);
		stop(body);
		face(body, target);
		Vec3 mark = new Vec3(target.getX(), groundY(target.getX(), target.getZ()), target.getZ());
		int leap = VulcanAnim.HAMMERFALL.at("leap");
		int land = VulcanAnim.HAMMERFALL.at("land");
		lockedUntil = tick + VulcanAnim.HAMMERFALL.ticks;
		vulcan.play(VulcanAnim.HAMMERFALL);
		say("Stand still upon my anvil!");
		tip("He'll land in the red circle: get out of it, then JUMP the shockwave as it reaches you.");
		Danger.circle(level, Danger.Colour.RED, mark, FALL_RADIUS, land);
		Danger.ring(level, Danger.Colour.PURPLE, mark, FALL_RADIUS, WAVE_RADIUS, land + 4);
		schedule(leap, () -> level.playSound(null, body.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0f, 0.7f));
		schedule(land, () -> {
			Mob b = body();
			if (b == null) {
				return;
			}
			b.teleportTo(mark.x, mark.y, mark.z);
			level.playSound(null, b.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 3.0f, 0.4f);
			level.playSound(null, b.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.0f, 0.6f);
			BossFx.send(level, ParticleTypes.LAVA, mark.x, mark.y + 0.5, mark.z, 30, 1.5, 0.3, 1.5, 0);
			for (LivingEntity e : victims(mark, FALL_RADIUS + 0.3)) {
				if (e.hurtServer(level, level.damageSources().mobAttack(b), FALL_DAMAGE)) {
					fling(e, mark, 1.0, 0.6);
				}
			}
			shockwave(b, mark, WAVE_RADIUS, WAVE_DAMAGE, ParticleTypes.FLAME);
		});
		return VulcanAnim.HAMMERFALL.ticks + 30;
	}

	/** Molten Rain: red circles round each of you; the iron pours there. */
	private int molten(Mob body) {
		VulcanEntity vulcan = vulcan(body);
		stop(body);
		int pour = VulcanAnim.POUR.at("pour");
		lockedUntil = tick + VulcanAnim.POUR.ticks;
		vulcan.play(VulcanAnim.POUR);
		say("Drink the iron of my crucible!");
		tip("Molten iron pours on the red circles: get out of them!");
		level.playSound(null, body.blockPosition(), SoundEvents.LAVA_AMBIENT, SoundSource.HOSTILE, 3.0f, 0.6f);
		List<Vec3> marks = new ArrayList<>();
		for (LivingEntity t : targets()) {
			marks.add(t.position());
			for (int i = 0; i < 2 + (enraged ? 1 : 0); i++) {
				double a = level.getRandom().nextDouble() * Math.PI * 2;
				Vec3 m = t.position().add(Math.cos(a) * 4, 0, Math.sin(a) * 4);
				marks.add(new Vec3(m.x, groundY(m.x, m.z), m.z));
			}
		}
		int fall = pour + 12;
		for (Vec3 m : marks) {
			Danger.circle(level, Danger.Colour.RED, m, RAIN_RADIUS, fall);
		}
		schedule(fall, () -> {
			Mob b = body();
			for (Vec3 m : marks) {
				BossFx.send(level, ParticleTypes.FALLING_LAVA, m.x, m.y + 6, m.z, 40, 0.8, 0.5, 0.8, 0);
				BossFx.send(level, ParticleTypes.LAVA, m.x, m.y + 0.3, m.z, 12, 0.8, 0.2, 0.8, 0);
				for (LivingEntity e : victims(m, RAIN_RADIUS + 0.2)) {
					if (e.hurtServer(level, b != null ? level.damageSources().mobAttack(b) : level.damageSources().lava(), RAIN_DAMAGE)) {
						e.igniteForSeconds(4.0f);
					}
				}
			}
		});
		return VulcanAnim.POUR.ticks + 26;
	}

	/** The Chains: a red line. Whoever is on it when the chain flies is dragged to his feet. */
	private int chains(Mob body, LivingEntity target) {
		VulcanEntity vulcan = vulcan(body);
		stop(body);
		face(body, target);
		double angle = Danger.facing(body);
		Vec3 c = body.position();
		int thrown = VulcanAnim.THROW.at("throw");
		lockedUntil = tick + VulcanAnim.THROW.ticks;
		vulcan.play(VulcanAnim.THROW);
		say("Come here, to the anvil.");
		tip("His chain flies along the red line: step off it, or it drags you to him.");
		Danger.line(level, Danger.Colour.RED, c, angle, CHAIN_REACH, CHAIN_WIDTH, thrown);
		schedule(thrown, () -> {
			level.playSound(null, body.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 2.0f, 0.5f);
			for (int i = 0; i <= 18; i++) {
				Vec3 p = c.add(Math.cos(angle) * i, 1.2, Math.sin(angle) * i);
				BossFx.send(level, ParticleTypes.SMOKE, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0);
			}
			for (LivingEntity e : inBand(c, angle, CHAIN_REACH, CHAIN_WIDTH)) {
				// dragged to his feet, and dazed a moment
				Vec3 to = c.subtract(e.position());
				fling(e, e.position().subtract(to), 0.18 * to.length(), 0.3);
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 2));
				if (e instanceof ServerPlayer p) {
					p.sendOverlayMessage(Component.literal("Vulcan's chain drags you in!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
				}
			}
		});
		return VulcanAnim.THROW.ticks + 20;
	}

	/** Forge-born: the anvil rings and two smiths of Dis step out of the fire. */
	private int forgeborn(Mob body) {
		VulcanEntity vulcan = vulcan(body);
		stop(body);
		int ring = VulcanAnim.SUMMON.at("ring");
		lockedUntil = tick + VulcanAnim.SUMMON.ticks;
		vulcan.play(VulcanAnim.SUMMON);
		say("Up, my smiths! There is work at the anvil!");
		schedule(ring, () -> {
			level.playSound(null, body.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.HOSTILE, 3.0f, 0.5f);
			tip("Vulcan called his smiths out of the fire: kill them!");
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
				BossFx.send(level, ParticleTypes.FLAME, x, smith.getY() + 1, z, 40, 0.4, 0.8, 0.4, 0.05);
			}
		});
		return VulcanAnim.SUMMON.ticks + 40;
	}

	/** The seams of the floor (after his rage): red lines out from the middle, then fire bursts along them. */
	private int seams(Mob body) {
		Vec3 c = Vec3.atBottomCenterOf(lair);
		double turn = level.getRandom().nextBoolean() ? 0 : Math.PI / 4;
		tip("The seams of the floor glow red: step off the lines before they burst!");
		level.playSound(null, lair, SoundEvents.LAVA_AMBIENT, SoundSource.HOSTILE, 3.0f, 0.5f);
		List<Double> angles = new ArrayList<>();
		for (int k = 0; k < 4; k++) {
			double a = turn + k * Math.PI / 2;
			angles.add(a);
			Danger.line(level, Danger.Colour.RED, c, a, SEAM_REACH, SEAM_WIDTH, SEAM_WARN);
		}
		schedule(SEAM_WARN, () -> {
			Mob b = body();
			level.playSound(null, lair, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.0f, 0.8f);
			for (double a : angles) {
				for (int i = 1; i <= SEAM_REACH; i += 2) {
					Vec3 p = c.add(Math.cos(a) * i, 0.2, Math.sin(a) * i);
					BossFx.send(level, ParticleTypes.FLAME, p.x, p.y, p.z, 6, 0.2, 0.6, 0.2, 0.05);
					BossFx.send(level, ParticleTypes.LAVA, p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0.0);
				}
				for (LivingEntity e : inBand(c, a, SEAM_REACH, SEAM_WIDTH)) {
					if (e.hurtServer(level, b != null ? Danger.unblockable(level, b) : level.damageSources().lava(), SEAM_DAMAGE)) {
						e.igniteForSeconds(3.0f);
						e.push(0, 0.6, 0);
					}
				}
			}
		});
		return SEAM_WARN + 20;
	}

	// ------------------------------------------------------------------ every tick

	@Override
	protected void everyTick(Mob body) {
		VulcanEntity vulcan = vulcan(body);
		if (!enraged && body.getHealth() <= body.getMaxHealth() / 2) {
			rage(vulcan);
		}
		if (staggerUntil >= 0 && tick >= staggerUntil) {
			staggerUntil = -1;
			setBase(body, Attributes.ARMOR, 12.0);
		}
		// in his rage the seams burst every so often, between his blows
		if (enraged && nextSeams >= 0 && tick >= nextSeams && staggerUntil < 0) {
			nextSeams = tick + 260;
			seams(body);
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

	/** At half his health: he drives his hammer into the floor and roars; the seams of the floor wake. */
	private void rage(VulcanEntity vulcan) {
		enraged = true;
		stop(vulcan);
		vulcan.warn(BossEntity.Warning.NONE);
		vulcan.play(VulcanAnim.PHASE);
		shield(vulcan, VulcanAnim.PHASE.ticks);
		delayNextAttack(VulcanAnim.PHASE.ticks + 10);
		lockedUntil = tick + VulcanAnim.PHASE.ticks;
		nextSeams = tick + VulcanAnim.PHASE.ticks + 60;
		say("ENOUGH! The forge itself will burn you!");
		schedule(VulcanAnim.PHASE.at("plunge"), () -> {
			level.playSound(null, vulcan.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 3.0f, 0.3f);
			BossFx.send(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MAGMA_BLOCK.defaultBlockState()), vulcan.getX(), vulcan.getY() + 0.3,
					vulcan.getZ(), 200, 3, 0.3, 3, 0.2);
		});
		schedule(VulcanAnim.PHASE.at("roar"), () -> {
			level.playSound(null, vulcan.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0f, 0.3f);
			tip("Vulcan is furious: from now on the seams of the floor burst between his blows. Watch for red lines.");
		});
	}

	@Override
	protected void onParried(Mob body, ServerPlayer by) {
		VulcanEntity vulcan = vulcan(body);
		stop(vulcan);
		vulcan.warn(BossEntity.Warning.NONE);
		vulcan.play(VulcanAnim.STAGGER);
		staggerUntil = tick + VulcanAnim.STAGGER.ticks;
		lockedUntil = staggerUntil;
		setBase(vulcan, Attributes.ARMOR, 0.0);
		delayNextAttack(VulcanAnim.STAGGER.ticks + 15);
		vulcan.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, VulcanAnim.STAGGER.ticks, 9, false, false));
		BossFx.send(level, ParticleTypes.CRIT, vulcan.getX(), vulcan.getY() + 3, vulcan.getZ(), 40, 1.5, 1.0, 1.5, 0.4);
		by.sendOverlayMessage(Component.literal("Parried! Vulcan reels: hit him now!").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
	}

	private static void stop(Mob body) {
		body.getNavigation().stop();
	}
}
