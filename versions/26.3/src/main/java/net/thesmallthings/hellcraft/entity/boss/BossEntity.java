package net.thesmallthings.hellcraft.entity.boss;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.thesmallthings.hellcraft.entity.HellEntities;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * A boss with a jointed, keyframe-animated model (client/render; made in Blockbench, see tools/art). The server says
 * which animation to play (one synched number, so players who arrive mid-fight see it too) and what danger the boss
 * shows (the danger language: a white flash means block now, purple means it can't be blocked); the fight
 * (hazard/guardian) decides when. Bosses never wander off, despawn, drown or take fall damage, can't be pushed or
 * leashed, and play their own death before they go.
 *
 * A boss whose body reaches past its main box has a {@link #shape()}: hit boxes that follow its bones (BossPart), placed
 * every tick on the server and on each client from the animation the server last started.
 */
public abstract class BossEntity extends Monster {
	private static final EntityDataAccessor<Integer> DATA_ANIMATION = SynchedEntityData.defineId(BossEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Byte> DATA_DANGER = SynchedEntityData.defineId(BossEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Float> DATA_TEMPO = SynchedEntityData.defineId(BossEntity.class, EntityDataSerializers.FLOAT);
	/** The animations a boss's tempo leaves alone: its breathing, walk, flinch, entrance (filmed by its cutscene) and death. */
	private static final Set<String> UNPACED = Set.of("IDLE", "WALK", "HURT", "INTRO", "DEATH", "CARRY", "LOWER", "LOW", "RAISE", "SNAP", "REVEAL",
			"DORMANT");

	/** What a boss is warning of (P6, the danger language). Red marks on the ground are drawn separately (Danger). */
	public enum Warning {
		NONE,
		/** A white flash: block with your shield now. A block in time staggers the boss. */
		WHITE,
		/** Purple: this can't be blocked. Jump or dodge. */
		PURPLE
	}

	private int sequence;
	/** The animation the server last started and the tick it started on (both sides): where the parts are. */
	private int poseAnim = -1;
	private int poseStart;
	/** Its hit boxes beyond the main one, by part (null without a shape, or before the first tick). */
	@Nullable
	private BossPart[] parts;

	protected BossEntity(EntityType<? extends Monster> type, Level level) {
		super(type, level);
		setPersistenceRequired();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ANIMATION, -1);
		builder.define(DATA_DANGER, (byte) 0);
		builder.define(DATA_TEMPO, 1.0f);
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

	/**
	 * How fast its blows play (1: as made; config bossTempo, set by its fight): the fight's clock runs at the same pace
	 * (GuardianFight), so the model, the danger marks and the blow stay together.
	 */
	public void tempo(float tempo) {
		entityData.set(DATA_TEMPO, tempo);
	}

	public float tempo() {
		return entityData.get(DATA_TEMPO);
	}

	/** How fast this animation plays: blows at the boss's tempo, everything else as made. */
	public float speedOf(BossAnim anim) {
		return speedOf(anim.name());
	}

	/** How fast the animation of this name plays. */
	public float speedOf(String anim) {
		return UNPACED.contains(anim) ? 1.0f : Math.max(0.1f, tempo());
	}

	/** How many ticks this animation lasts at its speed. */
	public int lengthOf(BossAnim anim) {
		return Math.round(anim.ticks() / speedOf(anim));
	}

	public Warning warning() {
		return Warning.values()[Math.min(Warning.values().length - 1, entityData.get(DATA_DANGER))];
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (DATA_ANIMATION.equals(accessor)) {
			poseAnim = animation();
			poseStart = tickCount;
			if (level().isClientSide() && animation() >= 0) {
				onAnimation(animation());
			}
		}
	}

	// ------------------------------------------------------------------ hit boxes beyond the main one

	/** Where it can be hit beyond its main box (entity/boss/gen/<Boss>Shape), or null: only its main box. */
	@Nullable
	public BossShape shape() {
		return null;
	}

	@Override
	public void tick() {
		super.tick();
		BossShape shape = shape();
		if (shape != null && !isRemoved()) {
			updateParts(shape);
		}
	}

	/** Spawns any part it lacks (server) and puts every part where the model has that bone now. */
	public void updateParts(BossShape shape) {
		if (parts == null || parts.length != shape.parts()) {
			parts = new BossPart[shape.parts()];
		}
		if (level() instanceof ServerLevel server && isAlive()) {
			for (int i = 0; i < parts.length; i++) {
				if (parts[i] == null || parts[i].isRemoved()) {
					BossPart part = HellEntities.BOSS_PART.create(server, EntitySpawnReason.EVENT);
					if (part == null) {
						continue;
					}
					part.attach(this, i);
					parts[i] = part;
					placePart(shape, i);
					server.addFreshEntity(part);
				}
			}
		}
		for (int i = 0; i < parts.length; i++) {
			if (parts[i] != null && !parts[i].isRemoved()) {
				placePart(shape, i);
			}
		}
	}

	private void placePart(BossShape shape, int i) {
		int anim = poseAnim;
		float age = tickCount - poseStart;
		if (anim >= 0 && anim < shape.anims()) {
			String name = shape.animName(anim);
			age *= speedOf(name);
			if (!shape.looping(anim) && age > shape.ticks(anim) && !"DEATH".equals(name)) {
				anim = -1;
			}
		} else {
			anim = -1;
		}
		if (anim < 0) {
			// nothing of its own playing: it breathes
			anim = shape.anim("IDLE");
			age = tickCount;
		}
		BossShape.Part part = shape.part(i);
		float headTurn = Mth.wrapDegrees(getYHeadRot() - yBodyRot);
		parts[i].place(position().add(shape.offset(i, anim, age, yBodyRot, headTurn)), part.width(), part.height());
	}

	/** A client's part found its boss. */
	void adopt(int index, BossPart part) {
		BossShape shape = shape();
		if (shape == null || index < 0 || index >= shape.parts()) {
			return;
		}
		if (parts == null || parts.length != shape.parts()) {
			parts = new BossPart[shape.parts()];
		}
		parts[index] = part;
	}

	/** Whether this is its part (a part it no longer knows is a stray, and goes). */
	boolean owns(BossPart part) {
		int i = part.index();
		return parts != null && i >= 0 && i < parts.length && parts[i] == part;
	}

	/** Its parts as they are now (for tests): none without a shape. */
	public java.util.List<BossPart> parts() {
		java.util.List<BossPart> out = new java.util.ArrayList<>();
		if (parts != null) {
			for (BossPart p : parts) {
				if (p != null && !p.isRemoved()) {
					out.add(p);
				}
			}
		}
		return out;
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
