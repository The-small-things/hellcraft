package net.thesmallthings.hellcraft.blood;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.hazard.Glow;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Keys of Peter: the angel at Purgatory's gate keeps two keys, one of gold and one of silver, and gives them to the
 * first soul to reach the seventh terrace. There is only ever one real set on the server (HellState.keysSerial: a set
 * carries the serial it was given with, and any other copy crumbles). They can't be put in a chest, an ender chest or
 * anything else (mixin/SlotMixin); they fall when their holder dies, so whoever kills them can take them. While carried
 * they give two more hearts and let a Blood Altar take you to any Virgil's Rest (TravelMenu), their holder has a faint glow
 * (Glow), and the whole server is told who has them and, now and then, roughly where.
 *
 * Lost (in the void, or left lying until they vanish) or kept by a holder who stays away for keysReturnHours, they go
 * back to the angel for the next soul to reach the seventh terrace. Config keysOfPeter turns them off.
 */
public final class KeysOfPeter {
	private KeysOfPeter() {
	}

	private static final String SERIAL = "hellcraft_keys_serial";
	private static final Identifier HEARTS = HellcraftMod.id("keys_of_peter_hearts");
	/** The extra health while carried (two hearts), and how often the server hears where the keys are (ticks). */
	private static final double EXTRA_HEALTH = 4.0;
	private static final int WHEREABOUTS = 20 * 60 * 15;

	/** The keys lying on the ground (dropped, or fallen from a dead holder): which entity, and since when no-one held them. */
	@Nullable
	private static UUID lying;
	private static long unheldSince = -1;

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof ItemEntity item && isKeys(item.getItem())) {
				// keys on the ground don't burn or despawn
				item.setUnlimitedLifetime();
				item.setPermanentlyInvulnerable(true);
				if (current(level.getServer(), item.getItem())) {
					lying = item.getUUID();
				}
			}
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> commands(dispatcher));
	}

	public static boolean isKeys(ItemStack stack) {
		return stack.is(HellItems.KEYS_OF_PETER);
	}

	private static int serialOf(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? 0 : data.copyTag().getIntOr(SERIAL, 0);
	}

	/** Whether this stack is the real set now (not an old copy). */
	public static boolean current(MinecraftServer server, ItemStack stack) {
		HellState state = HellState.get(server);
		return isKeys(stack) && state.keysClaimed && serialOf(stack) == state.keysSerial;
	}

	static ItemStack create(int serial) {
		ItemStack stack = new ItemStack(HellItems.KEYS_OF_PETER);
		CompoundTag tag = new CompoundTag();
		tag.putInt(SERIAL, serial);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	/** Whether this soul carries the real keys now (for TravelMenu). */
	public static boolean carrying(MinecraftServer server, HellState.Soul soul) {
		HellState state = HellState.get(server);
		return state.keysClaimed && state.keysHolder != null && soul.name.equals(state.keysHolderName)
				&& server.getPlayerList().getPlayer(state.keysHolder) != null;
	}

	/** The angel gives the keys to whoever reaches the seventh terrace, if no-one has them (Purgatory). */
	public static void offer(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		HellState state = HellState.get(server);
		if (!HellConfig.get().keysOfPeter || state.keysClaimed) {
			return;
		}
		give(server, player, player.getGameProfile().name() + " reached the seventh terrace, and the angel at Purgatory's gate gave them "
				+ "the Keys of Peter.");
	}

	private static void give(MinecraftServer server, ServerPlayer player, String news) {
		HellState state = HellState.get(server);
		state.keysSerial++;
		state.keysClaimed = true;
		state.keysHolder = player.getUUID();
		state.keysHolderName = player.getGameProfile().name();
		state.keysSeen = System.currentTimeMillis();
		state.setDirty();
		lying = null;
		unheldSince = -1;
		BloodItems.give(player, create(state.keysSerial));
		server.getPlayerList().broadcastSystemMessage(Component.literal(news).withStyle(ChatFormatting.GOLD), false);
		net.thesmallthings.hellcraft.net.Dialogue.notice(player, Component.literal("You carry the Keys of Peter: two more hearts, and a Blood "
				+ "Altar takes you to any Virgil's Rest. They can't be put away, everyone knows you have them, and if you die they fall.")
				.withStyle(ChatFormatting.GOLD));
	}

	/** Back to the angel: no-one has the real set, and any copy left anywhere is worthless. */
	static void lose(MinecraftServer server, String why) {
		HellState state = HellState.get(server);
		state.keysSerial++;
		state.keysClaimed = false;
		state.keysHolder = null;
		state.keysHolderName = "";
		state.setDirty();
		lying = null;
		unheldSince = -1;
		Glow.keys(server, null);
		server.getPlayerList().broadcastSystemMessage(Component.literal(why + " The angel at Purgatory's gate keeps the Keys of Peter again, "
				+ "for the next soul to reach the seventh terrace.").withStyle(ChatFormatting.GOLD), false);
	}

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 3) {
			return;
		}
		HellState state = HellState.get(server);
		ServerPlayer holder = null;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			boolean has = false;
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				ItemStack stack = p.getInventory().getItem(i);
				if (!isKeys(stack)) {
					continue;
				}
				if (!current(server, stack) || has) {
					// an old copy (or a second one) crumbles
					p.getInventory().setItem(i, ItemStack.EMPTY);
					p.sendOverlayMessage(Component.literal("Those keys were only a copy: they crumble to dust.").withStyle(ChatFormatting.GRAY));
					continue;
				}
				has = true;
			}
			// held on the cursor while moving things about counts too
			has |= current(server, p.containerMenu.getCarried());
			hearts(p, has);
			if (has) {
				holder = p;
			}
		}
		if (!state.keysClaimed) {
			return;
		}
		if (holder != null) {
			if (!holder.getUUID().equals(state.keysHolder)) {
				String before = state.keysHolderName;
				String now = holder.getGameProfile().name();
				state.keysHolder = holder.getUUID();
				state.keysHolderName = now;
				if (!now.equals(before)) {
					server.getPlayerList().broadcastSystemMessage(Component.literal(now + " took the Keys of Peter"
							+ (before.isEmpty() ? "." : " from " + before + ".")).withStyle(ChatFormatting.GOLD), false);
				}
			}
			state.keysSeen = System.currentTimeMillis();
			state.setDirty();
			lying = null;
			unheldSince = -1;
			Glow.keys(server, holder);
			if (server.getTickCount() % WHEREABOUTS < 20) {
				server.getPlayerList().broadcastSystemMessage(Component.literal("The Keys of Peter are with " + holder.getGameProfile().name()
						+ ", " + whereabouts(holder) + ".").withStyle(ChatFormatting.GOLD), false);
			}
			return;
		}
		Glow.keys(server, null);
		ServerPlayer away = state.keysHolder == null ? null : server.getPlayerList().getPlayer(state.keysHolder);
		if (state.keysHolder != null && away == null) {
			// the holder is offline with them: they come back to the angel if they stay away too long
			long hours = Math.max(1, HellConfig.get().keysReturnHours);
			if (System.currentTimeMillis() - state.keysSeen > hours * 3_600_000L) {
				lose(server, state.keysHolderName + " stayed away too long with the Keys of Peter.");
			}
			return;
		}
		// nobody holds them: lying somewhere, or lost (keysHolderName keeps who had them last)
		state.keysHolder = null;
		Entity on = lying == null ? null : find(server, lying);
		if (on != null && on.isAlive()) {
			unheldSince = -1;
			return;
		}
		if (unheldSince < 0) {
			unheldSince = server.getTickCount();
		} else if (server.getTickCount() - unheldSince > 20 * 30) {
			lose(server, "The Keys of Peter were lost.");
		}
	}

	private static void hearts(ServerPlayer p, boolean carrying) {
		AttributeInstance health = p.getAttribute(Attributes.MAX_HEALTH);
		if (health == null) {
			return;
		}
		boolean has = health.getModifier(HEARTS) != null;
		if (carrying && !has) {
			health.addTransientModifier(new AttributeModifier(HEARTS, EXTRA_HEALTH, AttributeModifier.Operation.ADD_VALUE));
		} else if (!carrying && has) {
			health.removeModifier(HEARTS);
			if (p.getHealth() > p.getMaxHealth()) {
				p.setHealth(p.getMaxHealth());
			}
		}
	}

	private static String whereabouts(ServerPlayer p) {
		if (p.level() != p.level().getServer().overworld()) {
			return "somewhere beyond Hell";
		}
		int x = (int) Math.round(p.getX() / 100) * 100;
		int z = (int) Math.round(p.getZ() / 100) * 100;
		return "in " + InfernoGeometry.regionName(p.getX(), p.getZ()) + ", near " + x + ", " + z;
	}

	@Nullable
	private static Entity find(MinecraftServer server, UUID id) {
		for (ServerLevel level : server.getAllLevels()) {
			Entity e = level.getEntity(id);
			if (e != null) {
				return e;
			}
		}
		return null;
	}

	/** For the smoke test: a set given now is the real one, an older copy isn't, and losing them sends them back to the angel. */
	public static String selfTest(MinecraftServer server) {
		HellState state = HellState.get(server);
		boolean claimed = state.keysClaimed;
		int serial = state.keysSerial;
		List<String> wrong = new ArrayList<>();
		try {
			state.keysClaimed = true;
			state.keysSerial = serial + 1;
			if (!current(server, create(serial + 1))) {
				wrong.add("a fresh set isn't the real one");
			}
			if (current(server, create(serial))) {
				wrong.add("an old copy counts");
			}
			if (current(server, new ItemStack(HellItems.KEYS_OF_PETER))) {
				wrong.add("a set without a serial counts");
			}
			if (!create(1).has(DataComponents.DAMAGE_RESISTANT)) {
				wrong.add("they burn in lava");
			}
		} finally {
			state.keysClaimed = claimed;
			state.keysSerial = serial;
		}
		return wrong.isEmpty() ? "Keys test OK: one real set, old copies crumble, fireproof" : "Keys test FAILED: " + String.join(", ", wrong);
	}

	private static void commands(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("hellcraft")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("keys")
						.executes(ctx -> {
							HellState state = HellState.get(ctx.getSource().getServer());
							String where = !state.keysClaimed ? "with the angel at Purgatory's gate"
									: state.keysHolder != null ? "with " + state.keysHolderName : "lying somewhere";
							ctx.getSource().sendSuccess(() -> Component.literal("The Keys of Peter are " + where + " (set " + state.keysSerial + ")."), false);
							return 1;
						})
						.then(Commands.literal("give").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
							ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
							give(ctx.getSource().getServer(), p, "An operator gave the Keys of Peter to " + p.getGameProfile().name() + ".");
							return 1;
						})))
						.then(Commands.literal("return").executes(ctx -> {
							lose(ctx.getSource().getServer(), "An operator took back the Keys of Peter.");
							return 1;
						}))
						.then(Commands.literal("test").executes(ctx -> {
							String result = selfTest(ctx.getSource().getServer());
							HellcraftMod.LOGGER.info(result);
							ctx.getSource().sendSuccess(() -> Component.literal(result), false);
							return 1;
						}))));
	}
}
