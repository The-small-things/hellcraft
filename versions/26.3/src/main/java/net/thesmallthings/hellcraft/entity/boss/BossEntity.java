package net.thesmallthings.hellcraft.entity.boss;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * A boss with a jointed, keyframe-animated model (client/render; made in Blockbench, see tools/art). The server says
 * which animation to play (one synched number, so players who arrive mid-fight see it too) and what danger the boss
 * shows (the danger language: a white flash means block now, purple means it can't be blocked); the fight
 * (hazard/guardian) decides when. Bosses never wander off, despawn, drown or take fall damage, can't be pushed or
 * leashed, and play their own death before they go.
 */
public abstract class BossEntity extends Monster {
	private static final EntityDataAccessor<Integer> DATA_ANIMATION = SynchedEntityData.defineId(BossEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Byte> DATA_DANGER = SynchedEntityData.defineId(BossEntity.class, EntityDataSerializers.BYTE);

	/** What a boss is warning of (P6, the danger language). Red marks on the ground are drawn separately (Danger). */
	public enum Warning {
		NONE,
		/** A white flash: block with your shield now. A block in time staggers the boss. */
		WHITE,
		/** Purple: this can't be blocked. Jump or dodge. */
		PURPLE
	}

	private int sequence;

	protected BossEntity(EntityType<? extends Monster> type, Level level) {
		super(type, level);
		setPersistenceRequired();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ANIMATION, -1);
		builder.define(DATA_DANGER, (byte) 0);
	}

	/** Starts an animation on every client watching (server side); the same one again restarts it. */
	protected void playAnimation(int id) {
		entityData.set(DATA_ANIMATION, (++sequence << 8) | id);
	}

	/** The last animation the server started, or -1. */
	public int animation() {
		int value = entityData.get(DATA_ANIMATION);
		return value < 0 ? -1 : value & 0xFF;
	}

	/** On the client, when the server starts an animation. */
	protected abstract void onAnimation(int id);

	public void warn(Warning warning) {
		entityData.set(DATA_DANGER, (byte) warning.ordinal());
	}

	public Warning warning() {
		return Warning.values()[Math.min(Warning.values().length - 1, entityData.get(DATA_DANGER))];
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (DATA_ANIMATION.equals(accessor) && level().isClientSide() && animation() >= 0) {
			onAnimation(animation());
		}
	}

	/** How long the death animation runs before the body goes. */
	protected abstract int deathTicks();

	@Override
	protected void tickDeath() {
		deathTime++;
		if (deathTime >= deathTicks() && !level().isClientSide() && !isRemoved()) {
			level().broadcastEntityEvent(this, EntityEvent.POOF);
			remove(RemovalReason.KILLED);
		}
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource source) {
		return false;
	}

	@Override
	public boolean canUsePortal(boolean ignorePassenger) {
		return false;
	}

	@Override
	public boolean isAffectedByPotions() {
		return false;
	}

	@Override
	protected int getBaseExperienceReward(ServerLevel level) {
		return 50;
	}
}
