package net.thesmallthings.hellcraft.entity.boss;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Where the Emperor lays himself open for a moment (EmperorAttacks): a hand lying on the ice after a slam, or a face
 * down low after a bite. Invisible itself (the model's hand or face is there, and it glows); blows and arrows that hit it
 * hurt him, a face more than a hand. It goes when the hand or face rises, or when he is gone.
 */
public class EmperorWeakPoint extends Entity {
	@Nullable
	private UUID emperor;
	private float multiplier = 1.0f;
	private int life = 200;

	public EmperorWeakPoint(EntityType<?> type, Level level) {
		super(type, level);
		noPhysics = true;
		setNoGravity(true);
	}

	/** Belongs to this Emperor; blows here hurt him `multiplier` times as much; it lasts at most `life` ticks. */
	public void open(EmperorEntity owner, float multiplier, int life) {
		this.emperor = owner.getUUID();
		this.multiplier = multiplier;
		this.life = life;
	}

	@Nullable
	private EmperorEntity owner(ServerLevel level) {
		return emperor != null && level.getEntity(emperor) instanceof EmperorEntity e && e.isAlive() ? e : null;
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		if (owner(level) == null || --life <= 0) {
			discard();
			return;
		}
		if (tickCount % 4 == 0) {
			// it glows: cold light round the hand or face
			level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + getBbHeight() / 2, getZ(), 6, getBbWidth() / 3, getBbHeight() / 3,
					getBbWidth() / 3, 0.01);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		EmperorEntity owner = owner(level);
		if (owner == null || isRemoved()) {
			return false;
		}
		level.sendParticles(ParticleTypes.CRIT, getX(), getY() + getBbHeight() / 2, getZ(), 12, 0.6, 0.6, 0.6, 0.3);
		return owner.hurtThrough(level, source, damage * multiplier);
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public boolean isAttackable() {
		return true;
	}

	@Override
	public PushReaction getPistonPushReaction() {
		return PushReaction.IGNORE_ENTITY;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}
}
