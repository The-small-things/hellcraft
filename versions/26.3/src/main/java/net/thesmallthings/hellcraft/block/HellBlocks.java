package net.thesmallthings.hellcraft.block;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.thesmallthings.hellcraft.HellcraftMod;

/** Hellcraft's own blocks (3.0). Their blockstates, models and textures come from tools/gen_models.py and gen_textures.py. */
public final class HellBlocks {
	private HellBlocks() {
	}

	/** The Slag Sea's cooling crust: cracks under you and melts (SlagCrustBlock). It drops nothing. */
	public static final Block SLAG_CRUST = Blocks.register(ResourceKey.create(Registries.BLOCK, HellcraftMod.id("slag_crust")), SlagCrustBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_ORANGE)
					.strength(0.6f)
					.sound(SoundType.BASALT)
					.lightLevel(SlagCrustBlock::light)
					.emissiveRendering(state -> state.getValue(SlagCrustBlock.STAGE) > 0)
					.isValidSpawn((state, level, pos, type) -> type.fireImmune())
					.pushReaction(PushReaction.IMMOVEABLE)
					.noLootTable());

	/** Loads the class (the blocks register as it does). */
	public static void register() {
	}
}
