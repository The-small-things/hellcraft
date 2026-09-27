package net.thesmallthings.hellcraft.blood;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import net.thesmallthings.hellcraft.config.HellConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * What right-clicking a Blood Altar shows: the head of every ghost waiting to be revived (click to
 * pay their blood price), plus the Ward and respawn binding. A read-only chest menu, so vanilla
 * clients can use it.
 */
public final class AltarMenu {
	private AltarMenu() {
	}

	private static final int WARD_SLOT = 3;
	private static final int BIND_SLOT = 5;
	private static final int FIRST_GHOST_SLOT = 9;
	private static final int MAX_GHOSTS = 9;

	public static void open(ServerPlayer player, ServerLevel level, BlockPos altar) {
		HellConfig config = HellConfig.get();
		int carried = BloodItems.countHearts(player);
		int cost = config.reviveCostHearts;
		SimpleContainer container = new SimpleContainer(18);
		Map<Integer, UUID> ghostSlots = new HashMap<>();

		container.setItem(WARD_SLOT, button(Items.SHIELD, "Blood Ward", ChatFormatting.DARK_RED,
				"Costs 1 Blood Heart",
				"Shields you from the torments of the circles",
				"for " + config.wardMinutes + " minutes",
				carried >= 1 ? "▶ Click to offer" : "✖ You carry no Blood Hearts"));
		container.setItem(BIND_SLOT, button(Items.RESPAWN_ANCHOR, "Bind your respawn", ChatFormatting.DARK_RED,
				"Costs 1 Blood Heart",
				"When you die you return to this altar",
				"(beds explode in Hell)",
				carried >= 1 ? "▶ Click to offer" : "✖ You carry no Blood Hearts"));

		List<Map.Entry<UUID, HellState.Soul>> ghosts = new ArrayList<>();
		for (Map.Entry<UUID, HellState.Soul> e : HellState.get(level.getServer()).souls().entrySet()) {
			if (e.getValue().ghost) {
				ghosts.add(e);
			}
		}
		ghosts.sort(Comparator.comparing(e -> e.getValue().name.toLowerCase(Locale.ROOT)));
		if (ghosts.isEmpty()) {
			container.setItem(13, button(Items.SKELETON_SKULL, "No souls await revival", ChatFormatting.GRAY,
					"Players who lose their last heart become ghosts.",
					"Their heads appear here: click one and pay",
					cost + " Blood Hearts to bring them back."));
		}
		for (int i = 0; i < Math.min(MAX_GHOSTS, ghosts.size()); i++) {
			Map.Entry<UUID, HellState.Soul> ghost = ghosts.get(i);
			ItemStack head = button(Items.PLAYER_HEAD, "Revive " + ghost.getValue().name, ChatFormatting.GOLD,
					"Costs " + cost + " Blood Hearts (you carry " + carried + ")",
					"They rise on top of this altar with " + config.reviveHearts + " hearts",
					carried >= cost ? "▶ Click to pay the blood price" : "✖ Not enough blood");
			head.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(ghost.getKey()));
			container.setItem(FIRST_GHOST_SLOT + i, head);
			ghostSlots.put(FIRST_GHOST_SLOT + i, ghost.getKey());
		}

		player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new Menu(id, inventory, container, level, altar.immutable(), ghostSlots),
				Component.literal("Blood Altar")));
	}

	private static ItemStack button(Item item, String name, ChatFormatting color, String... lines) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.ITEM_NAME, Component.literal(name).withStyle(color, ChatFormatting.BOLD));
		List<Component> lore = new ArrayList<>();
		for (String line : lines) {
			ChatFormatting style = line.startsWith("▶") ? ChatFormatting.YELLOW : line.startsWith("✖") ? ChatFormatting.RED : ChatFormatting.GRAY;
			lore.add(Component.literal(line).withStyle(s -> s.withColor(style).withItalic(false)));
		}
		stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}

	private static final class Menu extends ChestMenu {
		private final ServerLevel level;
		private final BlockPos altar;
		private final Map<Integer, UUID> ghostSlots;

		Menu(int id, Inventory inventory, SimpleContainer container, ServerLevel level, BlockPos altar, Map<Integer, UUID> ghostSlots) {
			super(MenuType.GENERIC_9x2, id, inventory, container, 2);
			this.level = level;
			this.altar = altar;
			this.ghostSlots = ghostSlots;
		}

		@Override
		public void clicked(int slotId, int button, ContainerInput input, Player player) {
			// undo whatever the client predicted: nothing here can be taken or moved
			setCarried(ItemStack.EMPTY);
			sendAllDataToRemote();
			if (!(player instanceof ServerPlayer sp) || !BloodAltar.isAltar(level, altar)
					|| sp.distanceToSqr(altar.getX() + 0.5, altar.getY() + 0.5, altar.getZ() + 0.5) > 64) {
				return;
			}
			if (slotId == WARD_SLOT) {
				BloodAltar.ward(sp, level, altar);
			} else if (slotId == BIND_SLOT) {
				BloodAltar.bind(sp, level, altar);
			} else if (ghostSlots.containsKey(slotId)) {
				BloodAltar.revive(sp, level, altar, ghostSlots.get(slotId));
			} else {
				return;
			}
			// the outcome is in chat either way
			sp.closeContainer();
		}

		@Override
		public ItemStack quickMoveStack(Player player, int index) {
			return ItemStack.EMPTY;
		}

		@Override
		public boolean stillValid(Player player) {
			return true;
		}
	}
}
