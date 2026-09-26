package net.thesmallthings.hellcraft.hazard.lucifer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.BloodItems;
import net.thesmallthings.hellcraft.blood.HellState;

import java.util.ArrayList;
import java.util.List;

/** The pit at 0,0: sealing it with a ring of ice, and the reliquary left behind when Lucifer falls. */
final class LuciferArena {
	private LuciferArena() {
	}

	/** Players and targets count as "in the arena" inside this horizontal radius. */
	static final double RADIUS = 30.0;
	private static final double WALL_INNER = 31.0;
	private static final double WALL_OUTER = 33.0;
	private static final int WALL_HEIGHT = 9;

	static int floorY(ServerLevel level) {
		level.getChunk(0, 0);
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
	}

	static boolean inside(double x, double z) {
		return x * x + z * z < RADIUS * RADIUS;
	}

	private static boolean wallColumn(int x, int z) {
		double d = Math.sqrt((x + 0.5) * (x + 0.5) + (z + 0.5) * (z + 0.5));
		return d >= WALL_INNER && d < WALL_OUTER;
	}

	static void seal(ServerLevel level) {
		int r = (int) WALL_OUTER + 1;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				if (!wallColumn(x, z)) {
					continue;
				}
				level.getChunk(x >> 4, z >> 4);
				int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				for (int y = ground; y < ground + WALL_HEIGHT; y++) {
					pos.set(x, y, z);
					BlockState state = level.getBlockState(pos);
					if (state.isAir() || state.canBeReplaced()) {
						level.setBlock(pos, Blocks.ICE.defaultBlockState(), 3);
					}
				}
			}
		}
		HellState state = HellState.get(level.getServer());
		state.arenaSealed = true;
		state.setDirty();
	}

	static void unseal(ServerLevel level) {
		int r = (int) WALL_OUTER + 1;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				if (!wallColumn(x, z)) {
					continue;
				}
				level.getChunk(x >> 4, z >> 4);
				for (int y = level.getMinBuildHeight() + 5; y < 40; y++) {
					pos.set(x, y, z);
					BlockState state = level.getBlockState(pos);
					if (state.is(Blocks.ICE) || state.is(Blocks.WATER)) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
					}
				}
			}
		}
		HellState state = HellState.get(level.getServer());
		state.arenaSealed = false;
		state.setDirty();
		HellcraftMod.LOGGER.info("Arena unsealed");
	}

	/** True for the ice of the seal while a fight is on (it can't be broken). */
	static boolean isSealBlock(ServerLevel level, BlockPos pos) {
		return HellState.get(level.getServer()).arenaSealed && wallColumn(pos.getX(), pos.getZ()) && level.getBlockState(pos).is(Blocks.ICE);
	}

	/** Puts a chest of relics at the bottom of the pit. The first victory on a server is the richest. */
	static void placeReliquary(ServerLevel level, boolean firstEver) {
		int y = floorY(level);
		BlockPos pos = new BlockPos(0, y, 0);
		level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST), 3);
		if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) {
			return;
		}
		HolderLookup.RegistryLookup<Enchantment> enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		List<ItemStack> loot = new ArrayList<>();
		if (firstEver) {
			loot.add(relic(Items.ELYTRA, "Wings of the Morning Star", "Torn from the Emperor as he fell a second time.",
					enchantments, Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));
			loot.add(relic(Items.NETHERITE_SWORD, "Morning Star", "The blade of the brightest angel, dimmed by ice.",
					enchantments, Enchantments.SHARPNESS, 5, Enchantments.FIRE_ASPECT, 2, Enchantments.LOOTING, 3,
					Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));
			loot.add(new ItemStack(Items.TOTEM_OF_UNDYING, 2));
			loot.add(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 2));
			loot.add(BloodItems.heart(4));
		} else {
			loot.add(new ItemStack(Items.TOTEM_OF_UNDYING));
			loot.add(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
			loot.add(BloodItems.heart(2));
		}
		int slot = 10;
		for (ItemStack stack : loot) {
			chest.setItem(slot, stack);
			slot += 2;
		}
		chest.setChanged();
		HellcraftMod.LOGGER.info("Reliquary placed ({})", firstEver ? "first victory" : "repeat victory");
	}

	private static ItemStack relic(Item item, String name, String lore, HolderLookup.RegistryLookup<Enchantment> lookup, Object... enchants) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.ITEM_NAME, Component.literal(name).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
				Component.literal(lore).withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(true)))));
		stack.set(DataComponents.RARITY, Rarity.EPIC);
		for (int i = 0; i + 1 < enchants.length; i += 2) {
			@SuppressWarnings("unchecked")
			ResourceKey<Enchantment> key = (ResourceKey<Enchantment>) enchants[i];
			Holder<Enchantment> holder = lookup.getOrThrow(key);
			stack.enchant(holder, (Integer) enchants[i + 1]);
		}
		return stack;
	}
}
