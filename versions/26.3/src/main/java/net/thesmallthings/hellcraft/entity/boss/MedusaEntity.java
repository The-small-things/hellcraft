package net.thesmallthings.hellcraft.entity.boss;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.thesmallthings.hellcraft.entity.boss.gen.MedusaAnim;

/**
 * Medusa (FuriesFight), modelled in tools/art/medusa.py: called up over the battlements of Dis when the Furies are
 * hard pressed. She can't be hurt. While her eyes are open (gazing, synced to the client, which greys the screen of
 * whoever looks at her: client/layer/GazeOverlay) anyone who looks at her is turning to stone.
 */
public class MedusaEntity extends AnimatedBoss {
	/** She is drawn this many times her Blockbench size. */
	public static final float SCALE = 2.5f;
	private static final EntityDataAccessor<Boolean> GAZING = SynchedEntityData.defineId(MedusaEntity.class, EntityDataSerializers.BOOLEAN);

	public MedusaEntity(EntityType<? extends Monster> type, Level level) {
		super(type, level, MedusaAnim.values());
		setNoAi(true);
		setNoGravity(true);
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 100.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.MOVEMENT_SPEED, 0.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(GAZING, false);
	}

	public boolean gazing() {
		return entityData.get(GAZING);
	}

	public void gaze(boolean open) {
		entityData.set(GAZING, open);
	}

	/** Where her eyes are (the gaze comes from there): 18 pixels up the model, scaled. */
	public double eyesY() {
		return getY() + 18 / 16.0 * SCALE;
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
	public boolean shouldBeSaved() {
		return false;
	}
}
