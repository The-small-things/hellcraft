package net.thesmallthings.hellcraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.thesmallthings.hellcraft.HellcraftMod;

/**
 * The Wood of the Suicides' undergrowth (Inferno XIII: "no fruit, but thorns with poison"): a tangle of black thorns
 * that holds back anything pushing through it. A player who walks or runs through it is torn (a heart every half
 * second); one who sneaks through only slows. The noise of pushing through counts toward the trees' scream (LowerRules).
 */
public class ThornsBlock extends VegetationBlock {
	public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, HellcraftMod.id("thorns"));
	private static final VoxelShape SHAPE = Block.column(14.0, 0.0, 13.0);

	public ThornsBlock(Properties properties) {
		super(properties);
	}

	/** Thorns grow on any firm ground (the Wood's soil is soul soil, coarse dirt and stone). */
	@Override
	protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
		return state.isFaceSturdy(level, pos, Direction.UP);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean precise) {
		if (!(entity instanceof LivingEntity)) {
			return;
		}
		entity.makeStuckInBlock(state, new Vec3(0.6, 0.75, 0.6));
		if (level instanceof ServerLevel server && entity instanceof ServerPlayer player && !player.isShiftKeyDown()
				&& !player.isCreative() && !player.isSpectator()) {
			Vec3 moved = player.getKnownMovement();
			if (Math.abs(moved.x) >= 0.003 || Math.abs(moved.z) >= 0.003) {
				player.hurtServer(server, damage(server), 1.0f);
			}
		}
	}

	public static DamageSource damage(ServerLevel level) {
		return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAMAGE));
	}
}
