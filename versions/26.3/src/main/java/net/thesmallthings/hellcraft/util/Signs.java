package net.thesmallthings.hellcraft.util;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;

import java.util.List;

/** The signs Hellcraft puts up at its landmarks. */
public final class Signs {
	private Signs() {
	}

	/** A waxed standing sign in glowing red, the same text on both sides; rotation 0 faces south, 4 west, 8 north, 12 east. */
	public static void place(ServerLevel level, BlockPos pos, int rotation, List<Component> lines) {
		BlockState state = Blocks.DARK_OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, rotation);
		level.setBlock(pos, state, 3);
		if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
			CompoundTag tag = new CompoundTag();
			tag.put("front_text", SignText.CODEC.encodeStart(NbtOps.INSTANCE, new SignText(lines, lines, DyeColor.RED, true)).getOrThrow());
			tag.putBoolean("is_waxed", true);
			sign.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
			sign.setChanged();
			level.sendBlockUpdated(pos, state, state, 3);
		}
	}
}
