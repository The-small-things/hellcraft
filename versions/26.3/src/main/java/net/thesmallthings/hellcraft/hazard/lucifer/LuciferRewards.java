package net.thesmallthings.hellcraft.hazard.lucifer;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.thesmallthings.hellcraft.blood.BloodItems;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.blood.Scoreboards;
import net.thesmallthings.hellcraft.util.Feedback;
import net.thesmallthings.hellcraft.util.Journey;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Lucifer's spoils. Every victor picks exactly one reward. A first victory offers the treasures of
 * the Morning Star; a returning champion may only choose between a totem, a golden apple and hearts.
 * The pick is made in a chest-style menu that vanilla clients can use.
 */
public final class LuciferRewards {
	private LuciferRewards() {
	}

	public static final int NONE = 0;
	public static final int FIRST = 1;
	public static final int REPEAT = 2;

	private record Option(String label, Function<HolderLookup.RegistryLookup<Enchantment>, ItemStack> make) {
	}

	private static final List<Option> FIRST_CHOICES = List.of(
			new Option("Wings of the Morning Star", e -> relic(Items.ELYTRA, "Wings of the Morning Star",
					"Torn from the Emperor as he fell a second time.", e, Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1)),
			new Option("Morning Star", e -> relic(Items.NETHERITE_SWORD, "Morning Star",
					"The blade of the brightest angel, dimmed by ice.", e, Enchantments.SHARPNESS, 5, Enchantments.FIRE_ASPECT, 2,
					Enchantments.LOOTING, 3, Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1)),
			new Option("2 Totems of Undying", e -> new ItemStack(Items.TOTEM_OF_UNDYING, 2)),
			new Option("2 Enchanted Golden Apples", e -> new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 2)),
			new Option("4 Blood Hearts", e -> BloodItems.heart(4)),
			new Option("Lucifer's Bane", e -> BloodItems.bane(1)));

	private static final List<Option> REPEAT_CHOICES = List.of(
			new Option("a Totem of Undying", e -> new ItemStack(Items.TOTEM_OF_UNDYING)),
			new Option("an Enchanted Golden Apple", e -> new ItemStack(Items.ENCHANTED_GOLDEN_APPLE)),
			new Option("2 Blood Hearts", e -> BloodItems.heart(2)));

	public static void register() {
		ServerPlayerEvents.JOIN.register(player -> {
			HellState.Soul soul = HellState.get(player.level().getServer()).existing(player.getUUID());
			if (soul != null && soul.pendingReward != NONE) {
				remind(player);
			}
		});
	}

	/** Marks a victor's reward as pending (never downgrading an unclaimed first-victory choice). */
	static void grant(HellState state, HellState.Soul soul) {
		soul.luciferKills++;
		soul.pendingEunoe++;
		if (!soul.slewLucifer) {
			soul.slewLucifer = true;
			soul.pendingReward = FIRST;
		} else if (soul.pendingReward != FIRST) {
			soul.pendingReward = REPEAT;
		}
		state.setDirty();
	}

	/**
	 * An operator's remedy (/hellcraft lucifer grant): gives a player a victory over Lucifer as the fight
	 * would have, for when a fight was lost to a bug.
	 */
	public static void grantVictory(ServerPlayer player) {
		HellState state = HellState.get(player.level().getServer());
		HellState.Soul soul = state.soul(player.getUUID(), player.getGameProfile().name());
		grant(state, soul);
		Scoreboards.update(player.level().getServer(), soul);
		Journey.award(player, "journey/lucifer");
		remind(player);
		open(player);
	}

	static void remind(ServerPlayer player) {
		player.sendSystemMessage(Component.literal("Your reward for beating Lucifer is waiting. ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal("[Choose your reward]").withStyle(s -> s.withColor(ChatFormatting.YELLOW).withBold(true).withUnderlined(true)
						.withClickEvent(new ClickEvent.RunCommand("/lucifer reward"))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal("Open the reward chooser"))))));
	}

	/** Opens the chooser for a player with a pending reward. Returns false if there is nothing to claim. */
	public static boolean open(ServerPlayer player) {
		HellState.Soul soul = HellState.get(player.level().getServer()).existing(player.getUUID());
		if (soul == null || soul.pendingReward == NONE) {
			return false;
		}
		boolean first = soul.pendingReward == FIRST;
		List<Option> options = first ? FIRST_CHOICES : REPEAT_CHOICES;
		HolderLookup.RegistryLookup<Enchantment> enchantments = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		SimpleContainer container = new SimpleContainer(9);
		int[] slots = first ? new int[]{1, 2, 3, 5, 6, 7} : new int[]{2, 4, 6};
		for (int i = 0; i < options.size(); i++) {
			ItemStack display = options.get(i).make().apply(enchantments);
			List<Component> lore = new ArrayList<>();
			ItemLore existing = display.get(DataComponents.LORE);
			if (existing != null) {
				lore.addAll(existing.lines());
			}
			lore.add(Component.literal("▶ Click to claim").withStyle(s -> s.withColor(ChatFormatting.YELLOW).withItalic(false)));
			display.set(DataComponents.LORE, new ItemLore(lore));
			container.setItem(slots[i], display);
		}
		Component title = Component.literal(first ? "Choose ONE reward" : "Choose ONE (you've beaten him before)");
		player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new RewardMenu(id, inventory, container, slots, first), title));
		return true;
	}

	private static void claim(ServerPlayer player, int optionIndex, boolean first) {
		HellState state = HellState.get(player.level().getServer());
		HellState.Soul soul = state.existing(player.getUUID());
		if (soul == null || soul.pendingReward != (first ? FIRST : REPEAT)) {
			player.closeContainer();
			return;
		}
		Option option = (first ? FIRST_CHOICES : REPEAT_CHOICES).get(optionIndex);
		ItemStack reward = option.make().apply(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT));
		soul.pendingReward = NONE;
		state.setDirty();
		player.closeContainer();
		BloodItems.give(player, reward);
		Feedback.sound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0f, 1.0f);
		player.level().getServer().getPlayerList().broadcastSystemMessage(Component.literal(player.getGameProfile().name()
				+ " claimed " + option.label() + " as their reward for beating Lucifer.").withStyle(ChatFormatting.GOLD), false);
	}

	/** A read-only chest menu: clicking an option claims it; nothing can be taken or moved. */
	private static final class RewardMenu extends ChestMenu {
		private final int[] slots;
		private final boolean first;

		RewardMenu(int id, Inventory inventory, SimpleContainer container, int[] slots, boolean first) {
			super(MenuType.GENERIC_9x1, id, inventory, container, 1);
			this.slots = slots;
			this.first = first;
		}

		@Override
		public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
			if (player instanceof ServerPlayer serverPlayer) {
				for (int i = 0; i < slots.length; i++) {
					if (slots[i] == slotId) {
						claim(serverPlayer, i, first);
						return;
					}
				}
			}
			// undo whatever the client predicted
			setCarried(ItemStack.EMPTY);
			sendAllDataToRemote();
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

	static ItemStack relic(Item item, String name, String lore, HolderLookup.RegistryLookup<Enchantment> lookup, Object... enchants) {
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
