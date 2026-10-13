package net.thesmallthings.hellcraft.entity;

import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

/**
 * A figure that only stands and waves: the test for the jointed, keyframe-animated models the bosses will use
 * (the client's TestRigModel and TestRigAnimation). Summon it with /summon hellcraft:test_rig; the client game
 * test RigTest screenshots it mid-animation.
 */
public class TestRig extends PathfinderMob {
	/** Runs on the client from the moment the rig is seen, and loops. */
	public final AnimationState wave = new AnimationState();

	public TestRig(EntityType<? extends TestRig> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder attributes() {
		return Mob.createMobAttributes();
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			wave.startIfStopped(tickCount);
		}
	}
}
