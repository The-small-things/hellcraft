package net.thesmallthings.hellcraft.blood;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.world.ShrineSites;
import net.thesmallthings.hellcraft.world.Shrines;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Travel between Blood Altars: from any altar, back to the Gate of Hell or to any Virgil's Rest the
 * player has already reached. Free, with a cooldown. A read-only chest menu, so vanilla clients can use it.
 */
public final class TravelMenu {
	private TravelMenu() {
	}

	/** Where a trip can end: "gate" or a Rest id, with the altar there. */
	public record Destination(String id, String title, HellState.GlobalSpot altar) {
	}

	private static final Map<UUID, Long> READY = new HashMap<>();

	/** The Gate (if built) and every built Rest this player has reached, from the rim inward. */
	public static List<Destination> destinations(MinecraftServer server, HellState.Soul soul) {
		List<Destination> out = new ArrayList<>();
		HellState state = HellState.get(server);
		if (state.starterAltar != null) {
			out.add(new Destination("gate", "The Gate of Hell", state.starterAltar));
		}
		for (ShrineSites.Site site : ShrineSites.all()) {
			Integer floor = Shrines.floor(server, site);
			if (floor != null && soul.visited.contains(site.id())) {
				out.add(new Destination(site.id(), "Virgil's Rest: " + site.title(),
						new HellState.GlobalSpot(server.overworld().dimension(), new BlockPos(site.x(), floor, site.z()))));
			}
		}
		return out;
	}

	@Nullable
	public static Destination find(MinecraftServer server, HellState.Soul soul, String id) {
		HellState state = HellState.get(server);
		if (id.equals("gate") && state.starterAltar != null) {
			return new Destination("gate", "The Gate of Hell", state.starterAltar);
		}
		ShrineSites.Site site = ShrineSites.byId(id);
		Integer floor = site != null ? Shrines.floor(server, site) : null;
		if (site == null || floor == null) {
			return null;
		}
		return new Destination(site.id(), "Virgil's Rest: " + site.title(),
				new HellState.GlobalSpot(server.overworld().dimension(), new BlockPos(site.x(), floor, site.z())));
	}

	/** Seconds until this player may travel again (0 = now). */
	public static long cooldownLeft(ServerPlayer player) {
		long now = player.level().getServer().overworld().getGameTime();
		return Math.max(0, (READY.getOrDefault(player.getUUID(), 0L) - now + 19) / 20);
	}

	public static void open(ServerPlayer player, ServerLevel level, BlockPos altar) {
		HellState.Soul soul = Hearts.soul(player);
		List<Destination> places = destinations(level.getServer(), soul);
		SimpleContainer container = new SimpleContainer(27);
		Map<Integer, Destination> slots = new HashMap<>();
		long wait = cooldownLeft(player);
		for (int i = 0; i < Math.min(27, places.size()); i++) {
			Destination d = places.get(i);
			BlockPos p = d.altar().pos();
			int dist = (int) Math.sqrt(p.distSqr(player.blockPosition()));
			ItemStack icon = new ItemStack(d.id().equals("gate") ? Items.RESPAWN_ANCHOR : Items.LODESTONE);
			icon.set(DataComponents.ITEM_NAME, Component.literal(d.title()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
			List<Component> lore = new ArrayList<>();
			lore.add(line(p.getX() + ", " + p.getY() + ", " + p.getZ() + " (" + dist + " blocks)", ChatFormatting.GRAY));
			lore.add(wait > 0 ? line("✖ You can travel again in " + wait + " s", ChatFormatting.RED) : line("▶ Click to travel", ChatFormatting.YELLOW));
			icon.set(DataComponents.LORE, new ItemLore(lore));
			container.setItem(i, icon);
			slots.put(i, d);
		}
		player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new Menu(id, inventory, container, level, altar.immutable(), slots),
				Component.literal("Travel: places you have found")));
		if (places.size() <= 1) {
			player.sendSystemMessage(Component.literal("Find a Virgil's Rest (the safe camp where the ramp comes down into each circle) to unlock teleporting there from any Blood Altar.")
					.withStyle(ChatFormatting.GRAY));
		}
	}

	private static Component line(String text, ChatFormatting color) {
		return Component.literal(text).withStyle(s -> s.withColor(color).withItalic(false));
	}

	/** Sends a player to a destination. Returns what happened, for chat. */
	public static String travel(ServerPlayer player, Destination to, boolean ignoreCooldown) {
		HellState.Soul soul = Hearts.soul(player);
		if (soul.ghost) {
			return "Ghosts can't travel between altars.";
		}
		long wait = cooldownLeft(player);
		if (!ignoreCooldown && wait > 0) {
			return "Travel is on cooldown: you can travel again in " + wait + " s.";
		}
		ServerLevel from = player.level();
		from.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 1, player.getZ(), 40, 0.4, 0.8, 0.4, 0.03);
		from.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 0.6f);
		BlockPos altar = to.altar().pos();
		Ghosts.teleport(player, new HellState.GlobalSpot(to.altar().dimension(), altar.offset(0, 0, 2)));
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 1));
		ServerLevel at = player.level();
		at.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 1, player.getZ(), 40, 0.4, 0.8, 0.4, 0.03);
		at.playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.8f, 1.2f);
		READY.put(player.getUUID(), player.level().getServer().overworld().getGameTime() + HellConfig.get().travelCooldownSeconds * 20L);
		player.sendSystemMessage(Component.literal("Teleported to " + to.title() + ".").withStyle(ChatFormatting.GOLD));
		return "Sent " + player.getGameProfile().name() + " to " + to.title() + ".";
	}

	private static final class Menu extends ChestMenu {
		private final ServerLevel level;
		private final BlockPos altar;
		private final Map<Integer, Destination> slots;

		Menu(int id, Inventory inventory, SimpleContainer container, ServerLevel level, BlockPos altar, Map<Integer, Destination> slots) {
			super(MenuType.GENERIC_9x3, id, inventory, container, 3);
			this.level = level;
			this.altar = altar;
			this.slots = slots;
		}

		@Override
		public void clicked(int slotId, int button, ContainerInput input, Player player) {
			// undo whatever the client predicted: nothing here can be taken or moved
			setCarried(ItemStack.EMPTY);
			sendAllDataToRemote();
			Destination to = slots.get(slotId);
			if (to == null || !(player instanceof ServerPlayer sp) || !BloodAltar.isAltar(level, altar)
					|| sp.distanceToSqr(altar.getX() + 0.5, altar.getY() + 0.5, altar.getZ() + 0.5) > 64) {
				return;
			}
			sp.closeContainer();
			String result = travel(sp, to, false);
			if (result.startsWith("Ghosts") || result.startsWith("Travel is on cooldown")) {
				sp.sendSystemMessage(Component.literal(result).withStyle(ChatFormatting.RED));
			}
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
