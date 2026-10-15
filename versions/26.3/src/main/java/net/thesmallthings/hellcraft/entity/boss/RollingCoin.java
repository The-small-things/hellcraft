package net.thesmallthings.hellcraft.entity.boss;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.hazard.Danger;
import net.thesmallthings.hellcraft.util.Feedback;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A great coin of the Mint, as tall as a house (tools/art/coin.py), rolling straight across the ground the way it
 * faces and flattening whoever it meets (unblockable, like every red mark). Plutus sends them across his lair in his
 * fury; each one goes after a while. It can't be hurt.
 */
public class RollingCoin extends Entity {
	/** Blocks a tick. */
	public static final double SPEED = 0.6;
	public static final double RADIUS = 2.5;
	private static final float DAMAGE = 10.0f;

	public final AnimationState roll = new AnimationState();
	private int life = 120;
	@Nullable
	private Entity owner;
	private final Set<UUID> struck = new HashSet<>();

	public RollingCoin(EntityType<? extends RollingCoin> type, Level level) {
		super(type, level);
		noPhysics = true;
	}

	/** Sets it rolling toward angle (radians, as Danger.facing) for so many ticks. */
	public void launch(@Nullable Entity owner, double angle, int ticks) {
		this.owner = owner;
		this.life = ticks;
		setYRot((float) Math.toDegrees(angle) - 90);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	private Vec3 direction() {
		double a = Math.toRadians(getYRot() + 90);
		return new Vec3(Math.cos(a), 0, Math.sin(a));
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			roll.startIfStopped(tickCount);
			return;
		}
		if (--life <= 0) {
			discard();
			return;
		}
		Vec3 next = position().add(direction().scale(SPEED));
		int ground = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(next.x), (int) Math.floor(next.z));
		setPos(next.x, ground, next.z);
		ServerLevel level = (ServerLevel) level();
		if (tickCount % 10 == 0) {
			level.playSound(null, BlockPos.containing(position()), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.6f, 0.4f);
		}
		DamageSource source = owner == null ? level.damageSources().generic() : Danger.unblockable(level, owner);
		Vec3 dir = direction();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(RADIUS + 0.5, 0, RADIUS + 0.5),
				e -> e.isAlive() && !(e instanceof BossEntity))) {
			// the disc stands along its path: it strikes whoever is within its radius ahead or behind, and close beside it
			Vec3 d = e.position().subtract(position());
			double along = d.x * dir.x + d.z * dir.z;
			double across = Math.abs(-d.x * dir.z + d.z * dir.x);
			if (Math.abs(along) > RADIUS || across > 1.2 || d.y > 2 * RADIUS) {
				continue;
			}
			if (struck.add(e.getUUID()) && e.hurtServer(level, source, DAMAGE)) {
				Vec3 away = e.position().subtract(position()).multiply(1, 0, 1);
				away = away.lengthSqr() < 1.0e-4 ? dir : away.normalize();
				e.setDeltaMovement(away.x * 1.4 + dir.x * 0.6, 0.6, away.z * 1.4 + dir.z * 0.6);
				Feedback.syncMotion(e);
			}
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		life = input.getIntOr("life", 0);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putInt("life", life);
	}
}
