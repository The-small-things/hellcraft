package net.thesmallthings.hellcraft.entity.boss;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.entity.boss.gen.EmperorAnim;

/**
 * Lucifer's true form, the Emperor of the kingdom dolorous (hazard/lucifer/EmperorAttacks), modelled in
 * tools/art/emperor.py: a colossus about sixty blocks tall to his wingtips, frozen to the chest in the ice of the pit.
 * His feet (this entity's position) are on the pit's floor; his chest goes down into the ice. He can't be hurt directly,
 * only where he lays himself open: a hand on the ice after a slam, a face low after a bite ({@link EmperorWeakPoint}).
 * Asleep, his six eyes are shut (the client hides them while he isn't awake). When no fight is on, the client draws
 * him asleep by itself (client/layer/Colossus), so he is there for everyone, near and far.
 */
public class EmperorEntity extends AnimatedBoss {
	/** He is drawn this many times his Blockbench size. */
	public static final float SCALE = 10.0f;
	/**
	 * Where a slammed hand and a biting face come to rest, in blocks from his feet (forward, up, to the side), from
	 * tools/art/emperor.py's reach(): keep them in step.
	 */
	public static final double SLAM_FORWARD = 15.42;
	public static final double SLAM_UP = 1.89;
	public static final double SLAM_SIDE = 15.0;
	public static final double BITE_FORWARD = 17.51;
	public static final double BITE_UP = 7.89;
	/** Nobody stands inside his body: they are pushed out to this far from his feet. */
	public static final double BODY_RADIUS = 13.0;
	private static final EntityDataAccessor<Boolean> AWAKE = SynchedEntityData.defineId(EmperorEntity.class, EntityDataSerializers.BOOLEAN);

	/** Set while a blow at a weak point passes through to him. */
	private boolean opened;

	public EmperorEntity(EntityType<? extends Monster> type, Level level) {
		super(type, level, EmperorAnim.values());
		setNoAi(true);
		setNoGravity(true);
		noPhysics = true;
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 1500.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.MOVEMENT_SPEED, 0.0);
	}

	@Override
	protected void registerGoals() {
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(AWAKE, false);
	}

	public boolean awake() {
		return entityData.get(AWAKE);
	}

	public void awake(boolean open) {
		entityData.set(AWAKE, open);
	}

	/** His eyes are shut while he sleeps. */
	@Override
	public boolean partHidden() {
		return !awake();
	}

	// ------------------------------------------------------------------ where he reaches

	/** Turns him toward a point, at most maxStep degrees (he grinds round in the ice). */
	public void turnToward(double x, double z, float maxStep) {
		float want = (float) Math.toDegrees(Math.atan2(z - getZ(), x - getX())) - 90.0f;
		float yaw = Mth.approachDegrees(getYRot(), want, maxStep);
		setYRot(yaw);
		setYBodyRot(yaw);
		setYHeadRot(yaw);
	}

	/** How far (degrees) he has yet to turn to face a point. */
	public float angleTo(double x, double z) {
		float want = (float) Math.toDegrees(Math.atan2(z - getZ(), x - getX())) - 90.0f;
		return Math.abs(Mth.wrapDegrees(want - getYRot()));
	}

	/** A point in his frame (forward, up, to his right) in the world. */
	public Vec3 inFrame(double forward, double up, double right) {
		double yaw = Math.toRadians(getYRot());
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double rx = -Math.cos(yaw);
		double rz = -Math.sin(yaw);
		return new Vec3(getX() + fx * forward + rx * right, getY() + up, getZ() + fz * forward + rz * right);
	}

	/** Where a slammed hand lies on the ice. */
	public Vec3 slamPoint(boolean right) {
		return inFrame(SLAM_FORWARD, SLAM_UP, right ? SLAM_SIDE : -SLAM_SIDE);
	}

	/** Where a biting face comes down. */
	public Vec3 bitePoint() {
		return inFrame(BITE_FORWARD, BITE_UP, 0);
	}

	// ------------------------------------------------------------------ only through his weak points

	/** A blow at one of his weak points (EmperorWeakPoint) reaches him. */
	public boolean hurtThrough(ServerLevel level, DamageSource source, float amount) {
		opened = true;
		try {
			return hurtServer(level, source, amount);
		} finally {
			opened = false;
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (!opened && !source.isCreativePlayer() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		return super.hurtServer(level, source, amount);
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean isInWall() {
		return false;
	}
}
