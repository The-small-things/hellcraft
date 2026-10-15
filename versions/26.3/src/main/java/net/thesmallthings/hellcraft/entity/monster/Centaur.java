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
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.entity.boss.gen.CentaurAnim;
import net.thesmallthings.hellcraft.hazard.Danger;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.Zone;

/**
 * The centaurs (the river of blood's monster, tools/art/centaur.py), posted on the banks beside the fords
 * (LowerMonsters). Each paces up and down its bank, then stops and watches the river for a few seconds: a red cone on
 * the ground shows where it's looking. Anyone it sees in the river or on the ford while it watches gets a volley of
 * burning arrows. So: wait on the bank, and cross while it looks away. Hurt one and it shoots back regardless.
 */
public class Centaur extends SoulMonster {
	/** How far it sees across the river, and how wide its gaze is (radians either side). */
	public static final double SIGHT = 30.0;
	public static final double GAZE = 0.55;
	/** Ticks spent pacing, then watching. */
	public static final int PACE = 140;
	public static final int WATCH = CentaurAnim.LOOK.ticks;
	private static final int SHOT_EVERY = 30;
	private static final int ALERT = 140;

	private Vec3 post;
	private int clock;
	private int alertUntil;
	private int nextShot;
	private int looseAt = -1;
	private int paceSide = 1;

	public Centaur(EntityType<? extends Monster> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 34.0)
				.add(Attributes.MOVEMENT_SPEED, 0.25)
				.add(Attributes.ARMOR, 4.0)
				.add(Attributes.ATTACK_DAMAGE, 5.0)
				.add(Attributes.FOLLOW_RANGE, 40.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.5)
				.add(Attributes.STEP_HEIGHT, 1.1);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
	}

	/** Sets its post (the bank beside a ford) and keeps it there for good. */
	public void post(Vec3 at) {
		post = at;
		setPersistenceRequired();
	}

	/** True while it stands watching the river (its gaze is the red cone). */
	public boolean watching() {
		return clock >= PACE;
	}

	/** The way it looks while watching: toward the middle of the river of blood. */
	public double riverAngle() {
		double r = Math.hypot(getX(), getZ());
		double middle = (InfernoGeometry.outerRadius(Zone.PHLEGETHON) + InfernoGeometry.outerRadius(Zone.WOOD_OF_SUICIDES)) / 2;
		double out = Math.atan2(getZ(), getX());
		return r > middle ? out + Math.PI : out;
	}

	/** True when this soul is where it would see them now. */
	public boolean sees(LivingEntity who) {
		if (!watching() || who.isInvisible()) {
			return false;
		}
		Vec3 d = who.position().subtract(position());
		double dist = Math.hypot(d.x, d.z);
		if (dist > SIGHT) {
			return false;
		}
		double off = InfernoGeometry.angularDistance(Math.atan2(d.z, d.x), riverAngle());
		return off <= GAZE && hasLineOfSight(who);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (post == null) {
			post = position();
		}
		if (looseAt >= 0 && tickCount >= looseAt) {
			looseAt = -1;
			LivingEntity target = getTarget();
			if (target != null && target.isAlive()) {
				loose(level, target);
			}
		}
		LivingEntity target = getTarget();
		if (target != null && (!target.isAlive() || distanceTo(target) > SIGHT + 12 || tickCount > alertUntil && !hasLineOfSight(target))) {
			setTarget(null);
			target = null;
		}
		if (target != null) {
			// shooting: stand, face, and loose every second and a half
			getNavigation().stop();
			getLookControl().setLookAt(target, 30, 30);
			setYRot(yHeadRot);
			yBodyRot = yHeadRot;
			if (tickCount >= nextShot) {
				nextShot = tickCount + SHOT_EVERY;
				level.broadcastEntityEvent(this, EntityEvent.START_ATTACKING);
				looseAt = tickCount + CentaurAnim.ATTACK.at("loose");
			}
			return;
		}
		clock++;
		if (clock == PACE) {
			// stop and watch the river
			getNavigation().stop();
			setYRot((float) Math.toDegrees(riverAngle()) - 90);
			yHeadRot = getYRot();
			yBodyRot = getYRot();
			playSpecial();
			Danger.cone(level, Danger.Colour.RED, position(), riverAngle(), SIGHT, GAZE, WATCH);
		}
		if (watching()) {
			getNavigation().stop();
			for (ServerPlayer p : level.players()) {
				if (!p.isSpectator() && !p.isCreative() && inRiver(p) && sees(p)) {
					spot(p);
					break;
				}
			}
			if (clock >= PACE + WATCH) {
				clock = 0;
				paceSide = -paceSide;
			}
			return;
		}
		// pacing along the bank, back and forth past its post
		if (getNavigation().isDone() && clock % 20 == 0) {
			double along = riverAngle() + Math.PI / 2;
			Vec3 to = post.add(Math.cos(along) * 9 * paceSide, 0, Math.sin(along) * 9 * paceSide);
			getNavigation().moveTo(to.x, to.y, to.z, 0.8);
		}
	}

	/** In the river of blood or on one of its fords. */
	public static boolean inRiver(LivingEntity who) {
		return InfernoGeometry.zoneAt(who.getX(), who.getZ()) == Zone.PHLEGETHON
				&& (who.isInLava() || InfernoGeometry.onFord(who.getX(), who.getZ()) || who.getY() < InfernoGeometry.fluidLevel(who.getX(), who.getZ()) + 3);
	}

	private void spot(ServerPlayer p) {
		setTarget(p);
		alertUntil = tickCount + ALERT;
		nextShot = tickCount + 6;
		playSound(SoundEvents.HORSE_ANGRY, 1.5f, 0.8f);
		p.sendOverlayMessage(Component.literal("A centaur has seen you! Get out of its sight.").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
	}

	private void loose(ServerLevel level, LivingEntity target) {
		ItemStack arrowStack = new ItemStack(Items.ARROW);
		AbstractArrow arrow = ProjectileUtil.getMobArrow(this, arrowStack, 1.0f, new ItemStack(Items.BOW));
		arrow.igniteForSeconds(100);
		double dx = target.getX() - getX();
		double dy = target.getY(0.33) - arrow.getY();
		double dz = target.getZ() - getZ();
		double dist = Math.sqrt(dx * dx + dz * dz);
		Projectile.spawnProjectileUsingShoot(arrow, level, arrowStack, dx, dy + dist * 0.18, dz, 2.0f, 3.0f);
		playSound(SoundEvents.SKELETON_SHOOT, 1.2f, 0.7f);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && source.getEntity() instanceof LivingEntity attacker) {
			setTarget(attacker);
			alertUntil = tickCount + ALERT;
		}
		return hurt;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (post != null) {
			output.putDouble("post_x", post.x);
			output.putDouble("post_y", post.y);
			output.putDouble("post_z", post.z);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		double x = input.getDoubleOr("post_x", Double.NaN);
		if (!Double.isNaN(x)) {
			post = new Vec3(x, input.getDoubleOr("post_y", getY()), input.getDoubleOr("post_z", getZ()));
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.HORSE_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.HORSE_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.HORSE_DEATH;
	}

	@Override
	protected int deathTicks() {
		return CentaurAnim.DEATH.ticks + 4;
	}
}
