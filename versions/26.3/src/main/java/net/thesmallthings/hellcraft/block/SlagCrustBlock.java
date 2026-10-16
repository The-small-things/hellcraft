package net.thesmallthings.hellcraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.thesmallthings.hellcraft.hazard.CircleHazards;

/**
 * The Slag Sea's cooling crust (Phase 6, the Forge of Dis's rule): it floats on the molten slag and holds you while
 * you keep moving. Stand on it and it cracks, glowing brighter at each of three cracks with a crack you can hear,
 * then melts away under you (no collision: you drop into the slag below). A melted crust cools and hardens again
 * after half a minute. The stages run on scheduled ticks, which the game saves with the chunk.
 */
public class SlagCrustBlock extends Block {
	/** 0 solid, 1 to 3 cracking, 4 molten. */
	public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 4);
	public static final int MOLTEN = 4;
	/** Ticks between cracks: from the first step to the melt takes three of these. */
	public static final int CRACK_TICKS = 9;
	/** How long a melted crust takes to harden again. */
	public static final int COOL_TICKS = 600;
	private static final VoxelShape MOLTEN_SHAPE = Shapes.empty();

	public SlagCrustBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(STAGE, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(STAGE);
	}

	/** Light grows as it cracks: a dull glow, then the seams, then molten. */
	public static int light(BlockState state) {
		return switch (state.getValue(STAGE)) {
			case 0 -> 3;
			case 1 -> 6;
			case 2 -> 9;
			case 3 -> 12;
			default -> 15;
		};
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(STAGE) == MOLTEN ? MOLTEN_SHAPE : Shapes.block();
	}

	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		super.stepOn(level, pos, state, entity);
		// striders belong on the slag; everything else that walks on the crust cracks it
		if (level instanceof ServerLevel server && state.getValue(STAGE) == 0 && entity instanceof LivingEntity
				&& entity.getType() != EntityTypes.STRIDER) {
			crack(server, pos, state);
			if (entity instanceof ServerPlayer player) {
				CircleHazards.hintOnce(player, 44, "The slag's crust cracks under you and melts after a second. Keep moving and it "
						+ "holds; stand still and you drop into the molten slag. A melted patch hardens again after half a minute.");
			}
		}
	}

	/** Starts the cracks (also used by tests). */
	public static void crack(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state.setValue(STAGE, 1), Block.UPDATE_CLIENTS);
		level.scheduleTick(pos, state.getBlock(), CRACK_TICKS);
		warn(level, pos, 1);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int stage = state.getValue(STAGE);
		if (stage == MOLTEN) {
			// cool again, unless something is standing in the molten patch
			if (!level.getEntitiesOfClass(Entity.class, new AABB(pos).inflate(0, 1, 0), e -> e instanceof LivingEntity).isEmpty()) {
				level.scheduleTick(pos, this, 40);
				return;
			}
			level.setBlock(pos, state.setValue(STAGE, 0), Block.UPDATE_CLIENTS);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 4, 0.3, 0.1, 0.3, 0.01);
			level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.3f, 1.4f);
			return;
		}
		if (stage == 0) {
			return;
		}
		int next = stage + 1;
		level.setBlock(pos, state.setValue(STAGE, next), Block.UPDATE_CLIENTS);
		level.scheduleTick(pos, this, next == MOLTEN ? COOL_TICKS : CRACK_TICKS);
		warn(level, pos, next);
	}

	private static void warn(ServerLevel level, BlockPos pos, int stage) {
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 1.0;
		double z = pos.getZ() + 0.5;
		if (stage == MOLTEN) {
			level.sendParticles(ParticleTypes.LAVA, x, y, z, 6, 0.3, 0.1, 0.3, 0.0);
			level.playSound(null, pos, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 1.0f, 0.8f);
			return;
		}
		level.sendParticles(ParticleTypes.SMOKE, x, y, z, 3 + 3 * stage, 0.35, 0.05, 0.35, 0.01);
		level.sendParticles(ParticleTypes.SMALL_FLAME, x, y, z, stage * 2, 0.35, 0.05, 0.35, 0.01);
		level.playSound(null, pos, SoundEvents.BASALT_BREAK, SoundSource.BLOCKS, 0.6f + 0.2f * stage, 0.6f + 0.25f * stage);
	}
}
