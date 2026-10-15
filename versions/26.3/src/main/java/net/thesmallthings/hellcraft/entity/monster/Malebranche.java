package net.thesmallthings.hellcraft.entity.monster;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.entity.boss.gen.MalebrancheAnim;
import net.thesmallthings.hellcraft.hazard.Danger;
import net.thesmallthings.hellcraft.util.Feedback;
import org.jetbrains.annotations.Nullable;

/**
 * One of the Malebranche, the "Evil Claws" (Inferno XXI-XXII, tools/art/malebranche.py): winged devils with long
 * hooks who keep the ditches of boiling pitch. From a few blocks off one lines up its hook (a red line on the ground,
 * from it to you), then throws it and drags whoever is still on the line to its feet, and jeers. Step off the line.
 */
public class Malebranche extends SoulMonster {
	/** How far its hook reaches, how long it aims first, and how long until it throws again. */
	private static final double HOOK_REACH = 11.0;
	private static final double HOOK_WIDTH = 1.0;
	private static final int AIM = 18;
	private static final int HOOK_COOLDOWN = 120;

	private int nextHook;
	private int throwAt = -1;
	@Nullable
	private Vec3 aimFrom;
	private double aimAngle;

	public Malebranche(EntityType<? extends Monster> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 26.0)
				.add(Attributes.ARMOR, 4.0)
				.add(Attributes.MOVEMENT_SPEED, 0.28)
				.add(Attributes.ATTACK_DAMAGE, 5.0)
				.add(Attributes.FOLLOW_RANGE, 28.0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, false));
		goalSelector.addGoal(6, new RandomStrollGoal(this, 0.7));
		goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0f));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		LivingEntity target = getTarget();
		if (throwAt >= 0) {
			getNavigation().stop();
			if (tickCount >= throwAt) {
				hook(level);
			}
			return;
		}
		if (target == null || tickCount < nextHook || !hasLineOfSight(target)) {
			return;
		}
		double d = distanceTo(target);
		if (d < 4.0 || d > HOOK_REACH - 1) {
			return;
		}
		// line up the hook: a red line from it to where you stand now
		aimFrom = position();
		aimAngle = Math.atan2(target.getZ() - getZ(), target.getX() - getX());
		throwAt = tickCount + AIM;
		setYRot((float) Math.toDegrees(aimAngle) - 90);
		yBodyRot = getYRot();
		yHeadRot = getYRot();
		Danger.line(level, Danger.Colour.RED, aimFrom, aimAngle, HOOK_REACH, HOOK_WIDTH, AIM);
		playSound(SoundEvents.CHAIN_PLACE, 1.0f, 0.6f);
	}

	/** The hook flies along the line: whoever is still on it is dragged in. */
	private void hook(ServerLevel level) {
		throwAt = -1;
		nextHook = tickCount + HOOK_COOLDOWN + random.nextInt(60);
		level.broadcastEntityEvent(this, EntityEvent.START_ATTACKING);
		playSound(SoundEvents.FISHING_BOBBER_THROW, 1.2f, 0.5f);
		if (aimFrom == null) {
			return;
		}
		double dx = Math.cos(aimAngle);
		double dz = Math.sin(aimAngle);
		for (ServerPlayer p : level.getPlayers(p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && p.distanceToSqr(aimFrom) < HOOK_REACH * HOOK_REACH)) {
			double rx = p.getX() - aimFrom.x;
			double rz = p.getZ() - aimFrom.z;
			double along = rx * dx + rz * dz;
			double off = Math.abs(-rx * dz + rz * dx);
			if (along < 0 || along > HOOK_REACH || off > HOOK_WIDTH + 0.3) {
				continue;
			}
			Vec3 pull = position().subtract(p.position()).multiply(1, 0, 1);
			double len = pull.length();
			if (len < 1.0e-3) {
				continue;
			}
			Vec3 yank = pull.scale(Math.min(1.6, 0.25 * len) / len);
			p.push(yank.x, 0.45, yank.z);
			Feedback.syncMotion(p);
			p.hurtServer(level, damageSources().mobAttack(this), 3.0f);
			p.sendOverlayMessage(Component.literal("A Malebranche hooked you! When its red line is under you, step off it.")
					.withStyle(ChatFormatting.RED));
			playSpecial();
			playSound(SoundEvents.PIGLIN_BRUTE_ANGRY, 1.0f, 1.4f);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		// a blow spoils its aim
		if (throwAt >= 0 && source.getEntity() instanceof Player) {
			throwAt = -1;
			nextHook = tickCount + 40;
		}
		return super.hurtServer(level, source, amount);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.PIGLIN_BRUTE_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.PIGLIN_BRUTE_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.PIGLIN_BRUTE_DEATH;
	}

	@Override
	protected int deathTicks() {
		return MalebrancheAnim.DEATH.ticks + 4;
	}
}
