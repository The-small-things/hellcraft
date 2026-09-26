package net.thesmallthings.hellcraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.thesmallthings.hellcraft.blood.BloodItems;
import net.thesmallthings.hellcraft.blood.Ghosts;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.blood.Hearts;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.world.Circle;
import net.thesmallthings.hellcraft.world.HellWorldgen;
import net.thesmallthings.hellcraft.world.InfernoGeometry;

import java.util.Map;
import java.util.UUID;

/**
 * Player commands: /hearts, /withdraw, /circle. Admin: /hellcraft ... (permission level 2).
 */
public final class HellCommands {
	private HellCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("hearts")
				.executes(ctx -> showHearts(ctx.getSource(), ctx.getSource().getPlayerOrException()))
				.then(Commands.argument("player", EntityArgument.player())
						.executes(ctx -> showHearts(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))));

		dispatcher.register(Commands.literal("withdraw")
				.executes(ctx -> withdraw(ctx.getSource().getPlayerOrException(), 1))
				.then(Commands.argument("amount", IntegerArgumentType.integer(1))
						.executes(ctx -> withdraw(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "amount")))));

		dispatcher.register(Commands.literal("circle").executes(ctx -> circle(ctx.getSource().getPlayerOrException())));

		dispatcher.register(Commands.literal("hellcraft")
				.requires(src -> src.hasPermission(2))
				.then(Commands.literal("sethearts")
						.then(Commands.argument("player", EntityArgument.player())
								.then(Commands.argument("hearts", IntegerArgumentType.integer(1))
										.executes(ctx -> {
											ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
											Hearts.set(p, IntegerArgumentType.getInteger(ctx, "hearts"));
											ctx.getSource().sendSuccess(() -> Hearts.describe(p.getGameProfile().getName(), Hearts.soul(p).hearts), true);
											return 1;
										}))))
				.then(Commands.literal("giveheart")
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> give(ctx, true, 1))
								.then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
										.executes(ctx -> give(ctx, true, IntegerArgumentType.getInteger(ctx, "count"))))))
				.then(Commands.literal("givefragment")
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> give(ctx, false, 1))
								.then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
										.executes(ctx -> give(ctx, false, IntegerArgumentType.getInteger(ctx, "count"))))))
				.then(Commands.literal("revive")
						.then(Commands.argument("name", StringArgumentType.word())
								.executes(HellCommands::revive)))
				.then(Commands.literal("ghosts").executes(ctx -> listGhosts(ctx.getSource())))
				.then(Commands.literal("where").executes(ctx -> where(ctx.getSource())))
				.then(Commands.literal("reload").executes(ctx -> {
					HellConfig.load();
					ctx.getSource().getServer().getPlayerList().getPlayers().forEach(Hearts::apply);
					ctx.getSource().sendSuccess(() -> Component.literal("Hellcraft config reloaded."), true);
					return 1;
				})));
	}

	private static int showHearts(CommandSourceStack source, ServerPlayer player) {
		source.sendSuccess(() -> Hearts.describe(player.getGameProfile().getName(), Hearts.soul(player).hearts), false);
		return Hearts.soul(player).hearts;
	}

	private static int withdraw(ServerPlayer player, int amount) {
		HellState.Soul soul = Hearts.soul(player);
		if (soul.hearts - amount < 1) {
			player.sendSystemMessage(Component.literal("You cannot bleed yourself dry. You have " + soul.hearts + " ❤.").withStyle(ChatFormatting.RED));
			return 0;
		}
		Hearts.add(player, -amount);
		BloodItems.give(player, BloodItems.heart(amount));
		player.sendSystemMessage(Component.literal("You draw " + amount + " heart" + (amount == 1 ? "" : "s") + " of blood from your veins.")
				.withStyle(ChatFormatting.DARK_RED));
		return amount;
	}

	private static int circle(ServerPlayer player) {
		if (!HellWorldgen.isInferno(player.serverLevel())) {
			player.sendSystemMessage(Component.literal("You are beyond the circles of the Inferno.").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		Circle circle = InfernoGeometry.circleAt(player.getX(), player.getZ());
		player.sendSystemMessage(Component.literal(circle.title()).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
		player.sendSystemMessage(Component.literal(InfernoGeometry.regionName(player.getX(), player.getZ())).withStyle(ChatFormatting.RED));
		player.sendSystemMessage(Component.literal("\"" + circle.quote() + "\"").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		int dist = (int) Math.sqrt(player.getX() * player.getX() + player.getZ() * player.getZ());
		player.sendSystemMessage(Component.literal(dist + " blocks from the bottom of Hell.").withStyle(ChatFormatting.DARK_GRAY));
		return circle.depth();
	}

	private static int give(CommandContext<CommandSourceStack> ctx, boolean heart, int count) throws CommandSyntaxException {
		ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
		BloodItems.give(p, heart ? BloodItems.heart(count) : BloodItems.fragment(count));
		ctx.getSource().sendSuccess(() -> Component.literal("Gave " + count + (heart ? " Blood Heart(s)" : " Blood Fragment(s)") + " to "
				+ p.getGameProfile().getName()), true);
		return count;
	}

	private static int revive(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		String name = StringArgumentType.getString(ctx, "name");
		HellState state = HellState.get(source.getServer());
		Map.Entry<UUID, HellState.Soul> target = state.findByName(name);
		if (target == null || !target.getValue().ghost) {
			source.sendFailure(Component.literal(name + " is not a ghost."));
			return 0;
		}
		HellState.GlobalSpot at = target.getValue().deathSpot != null ? target.getValue().deathSpot
				: new HellState.GlobalSpot(source.getLevel().dimension(), source.getServer().overworld().getSharedSpawnPos());
		Ghosts.revive(source.getServer(), target.getKey(), at);
		source.sendSuccess(() -> Component.literal("Revived " + target.getValue().name + "."), true);
		return 1;
	}

	private static int listGhosts(CommandSourceStack source) {
		int n = 0;
		for (HellState.Soul soul : HellState.get(source.getServer()).souls().values()) {
			if (soul.ghost) {
				n++;
				String where = soul.deathSpot == null ? "?" : soul.deathSpot.pos().toShortString();
				source.sendSuccess(() -> Component.literal(" ☠ " + soul.name + " @ " + where).withStyle(ChatFormatting.GRAY), false);
			}
		}
		int count = n;
		source.sendSuccess(() -> Component.literal(count + " ghost(s) walk the earth."), false);
		return n;
	}

	private static int where(CommandSourceStack source) {
		double x = source.getPosition().x;
		double z = source.getPosition().z;
		source.sendSuccess(() -> Component.literal(String.format("zone=%s circle=%s region=\"%s\" r=%.0f r_eff=%.0f baseY=%.1f fluid=%s@%d wall=%s",
				InfernoGeometry.zoneAt(x, z), InfernoGeometry.circleAt(x, z), InfernoGeometry.regionName(x, z),
				Math.sqrt(x * x + z * z), InfernoGeometry.effectiveRadius(x, z), InfernoGeometry.baseHeight(x, z),
				InfernoGeometry.fluidAt(x, z), InfernoGeometry.fluidLevel(x, z), InfernoGeometry.disWallAt(x, z))), false);
		return 1;
	}
}
