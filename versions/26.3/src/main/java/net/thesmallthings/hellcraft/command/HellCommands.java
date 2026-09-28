package net.thesmallthings.hellcraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.thesmallthings.hellcraft.blood.BloodAltar;
import net.thesmallthings.hellcraft.blood.BloodArmour;
import net.thesmallthings.hellcraft.blood.BloodItems;
import net.thesmallthings.hellcraft.blood.GhostPowers;
import net.thesmallthings.hellcraft.blood.Ghosts;
import net.thesmallthings.hellcraft.blood.GuideBook;
import net.thesmallthings.hellcraft.blood.Hearts;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.blood.HellWeapons;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.hazard.guardian.Guardian;
import net.thesmallthings.hellcraft.hazard.guardian.GuardianManager;
import net.thesmallthings.hellcraft.hazard.lucifer.LuciferManager;
import net.thesmallthings.hellcraft.hazard.lucifer.LuciferRewards;
import net.thesmallthings.hellcraft.world.Circle;
import net.thesmallthings.hellcraft.world.HellWorldgen;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.Spine;
import net.thesmallthings.hellcraft.world.Zone;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Player commands: /hearts, /withdraw, /circle. Admin: /hellcraft ... (permission level 2; in single player,
 * turn cheats on). {@code /hellcraft goto <zone>} and {@code /hellcraft gate} help with testing.
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

		GhostPowers.register(dispatcher);

		dispatcher.register(Commands.literal("guide").executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			BloodItems.give(player, GuideBook.create(ctx.getSource().getServer()));
			player.sendSystemMessage(Component.literal("Virgil hands you The Pilgrim's Guide.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			return 1;
		}));

		// for everyone: how reviving works, who is dead, and the revive itself next to an altar
		dispatcher.register(Commands.literal("revive")
				.executes(ctx -> reviveHelp(ctx.getSource()))
				.then(Commands.argument("name", StringArgumentType.word())
						.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ghostNames(ctx.getSource()), builder))
						.executes(ctx -> reviveAtAltar(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "name")))));

		dispatcher.register(Commands.literal("lucifer")
				.then(Commands.literal("reward").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					if (!LuciferRewards.open(player)) {
						player.sendSystemMessage(Component.literal("You have no spoils of Lucifer to claim.").withStyle(ChatFormatting.GRAY));
						return 0;
					}
					return 1;
				})));

		dispatcher.register(Commands.literal("hellcraft")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("sethearts")
						.then(Commands.argument("player", EntityArgument.player())
								.then(Commands.argument("hearts", IntegerArgumentType.integer(1))
										.executes(ctx -> {
											ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
											Hearts.set(p, IntegerArgumentType.getInteger(ctx, "hearts"));
											ctx.getSource().sendSuccess(() -> Hearts.describe(p.getGameProfile().name(), Hearts.soul(p)), true);
											return 1;
										}))))
				.then(Commands.literal("giveheart")
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> give(ctx, true, 1))
								.then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
										.executes(ctx -> give(ctx, true, IntegerArgumentType.getInteger(ctx, "count"))))))
				.then(Commands.literal("givebane")
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> giveBane(ctx, 1))
								.then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
										.executes(ctx -> giveBane(ctx, IntegerArgumentType.getInteger(ctx, "count"))))))
				.then(Commands.literal("guardian")
						.then(Commands.argument("name", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(Guardian.values()).map(Guardian::id), builder))
								.then(Commands.literal("summon").executes(ctx -> guardian(ctx, g -> GuardianManager.summon(ctx.getSource().getServer(), g))))
								.then(Commands.literal("slay").executes(ctx -> guardian(ctx, GuardianManager::slay)))
								.then(Commands.literal("stop").executes(ctx -> guardian(ctx, GuardianManager::stop)))
								.then(Commands.literal("status").executes(ctx -> guardian(ctx, GuardianManager::status)))
								.then(Commands.literal("attack")
										.then(Commands.argument("attack", StringArgumentType.word())
												.executes(ctx -> guardian(ctx, g -> GuardianManager.attack(g, StringArgumentType.getString(ctx, "attack"))))))))
				.then(Commands.literal("givearmour")
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> {
									ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
									for (BloodArmour.Piece piece : BloodArmour.Piece.values()) {
										BloodItems.give(p, BloodArmour.create(piece));
									}
									ctx.getSource().sendSuccess(() -> Component.literal("Gave a set of blood armour to " + p.getGameProfile().name()), true);
									return 1;
								})))
				.then(Commands.literal("giveweapon")
						.then(Commands.argument("player", EntityArgument.player())
								.then(Commands.argument("weapon", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
												Arrays.stream(HellWeapons.Weapon.values()).map(w -> w.id), builder))
										.executes(ctx -> {
											ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
											HellWeapons.Weapon weapon = HellWeapons.Weapon.byId(StringArgumentType.getString(ctx, "weapon"));
											if (weapon == null) {
												ctx.getSource().sendFailure(Component.literal("Unknown weapon. Try: bloodletter, reaper_of_minos, tithe_axe"));
												return 0;
											}
											BloodItems.give(p, HellWeapons.create(weapon));
											ctx.getSource().sendSuccess(() -> Component.literal("Gave " + weapon.id + " to " + p.getGameProfile().name()), true);
											return 1;
										}))))
				.then(Commands.literal("givefragment")
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> give(ctx, false, 1))
								.then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
										.executes(ctx -> give(ctx, false, IntegerArgumentType.getInteger(ctx, "count"))))))
				.then(Commands.literal("revive")
						.then(Commands.argument("name", StringArgumentType.word())
								.executes(HellCommands::revive)))
				.then(Commands.literal("ghosts").executes(ctx -> listGhosts(ctx.getSource())))
				.then(Commands.literal("goto")
						.then(Commands.argument("zone", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(Zone.values()).map(Zone::id), builder))
								.executes(HellCommands::gotoZone)))
				.then(Commands.literal("lucifer")
						.then(Commands.literal("summon").executes(HellCommands::summonLucifer))
						.then(Commands.literal("skip").executes(ctx -> reply(ctx.getSource(), LuciferManager.skip())))
						.then(Commands.literal("stop").executes(ctx -> reply(ctx.getSource(), LuciferManager.stop())))
						.then(Commands.literal("status").executes(ctx -> reply(ctx.getSource(), LuciferManager.status())))
						.then(Commands.literal("attack")
								.then(Commands.argument("attack", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(new String[]{"slash", "fangs", "wings", "hellfire",
												"hatred", "impotence", "ignorance", "wingbeat", "mouths"}, builder))
										.executes(ctx -> reply(ctx.getSource(), LuciferManager.attack(StringArgumentType.getString(ctx, "attack")))))))
				.then(Commands.literal("gate").executes(ctx -> teleportToSurface(ctx.getSource(), InfernoGeometry.gateX() + 24, 0)))
				.then(Commands.literal("spine")
						.executes(ctx -> toSpine(ctx.getSource(), Spine.Way.EAST))
						.then(Commands.argument("way", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(Spine.Way.values()).map(w -> w.id), builder))
								.executes(ctx -> {
									Spine.Way way = Spine.byId(StringArgumentType.getString(ctx, "way"));
									if (way == null) {
										ctx.getSource().sendFailure(Component.literal("north, east, south or west"));
										return 0;
									}
									return toSpine(ctx.getSource(), way);
								})))
				.then(Commands.literal("where").executes(ctx -> where(ctx.getSource())))
				.then(Commands.literal("reload").executes(ctx -> {
					HellConfig.load();
					ctx.getSource().getServer().getPlayerList().getPlayers().forEach(Hearts::apply);
					ctx.getSource().sendSuccess(() -> Component.literal("Hellcraft config reloaded."), true);
					return 1;
				})));
	}

	private static int showHearts(CommandSourceStack source, ServerPlayer player) {
		source.sendSuccess(() -> Hearts.describe(player.getGameProfile().name(), Hearts.soul(player)), false);
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
		if (!HellWorldgen.isInferno(player.level())) {
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
				+ p.getGameProfile().name()), true);
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
				: new HellState.GlobalSpot(source.getLevel().dimension(), source.getServer().overworld().getRespawnData().pos());
		Ghosts.revive(source.getServer(), target.getKey(), at);
		source.sendSuccess(() -> Component.literal("Revived " + target.getValue().name + "."), true);
		return 1;
	}

	private static List<String> ghostNames(CommandSourceStack source) {
		List<String> names = new ArrayList<>();
		for (HellState.Soul soul : HellState.get(source.getServer()).souls().values()) {
			if (soul.ghost) {
				names.add(soul.name);
			}
		}
		return names;
	}

	private static int reviveHelp(CommandSourceStack source) {
		Ghosts.howToRevive(source.getServer()).forEach(source::sendSystemMessage);
		List<String> ghosts = ghostNames(source);
		source.sendSystemMessage(ghosts.isEmpty()
				? Component.literal("No one is a ghost right now.").withStyle(ChatFormatting.GRAY)
				: Component.literal("Ghosts: " + String.join(", ", ghosts)).withStyle(ChatFormatting.RED));
		return 1;
	}

	private static int reviveAtAltar(ServerPlayer player, String name) {
		Map.Entry<UUID, HellState.Soul> target = HellState.get(player.level().getServer()).findByName(name);
		if (target == null || !target.getValue().ghost) {
			player.sendSystemMessage(Component.literal(name + " is not a ghost. /revive lists who is.").withStyle(ChatFormatting.RED));
			return 0;
		}
		BlockPos altar = BloodAltar.near(player, 5);
		if (altar == null) {
			player.sendSystemMessage(Component.literal("Stand next to a Blood Altar (a respawn anchor on 3x3 crying obsidian) first.")
					.withStyle(ChatFormatting.RED));
			HellState.GlobalSpot starter = HellState.get(player.level().getServer()).starterAltar;
			if (starter != null) {
				player.sendSystemMessage(Component.literal("There is one beside the Gate of Hell at " + starter.pos().getX() + " "
						+ starter.pos().getY() + " " + starter.pos().getZ() + ".").withStyle(ChatFormatting.GRAY));
			}
			return 0;
		}
		return BloodAltar.revive(player, player.level(), altar, target.getKey()) ? 1 : 0;
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

	/** Brings the caller (if a player) to the edge of the pit and wakes Lucifer. */
	private static int summonLucifer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		if (source.getEntity() instanceof ServerPlayer) {
			teleportToSurface(source, 0, 18);
		}
		return reply(source, LuciferManager.start(source.getServer(), source.getEntity() == null));
	}

	private static int giveBane(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
		ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
		BloodItems.give(p, BloodItems.bane(count));
		ctx.getSource().sendSuccess(() -> Component.literal("Gave " + count + " Lucifer's Bane to " + p.getGameProfile().name()), true);
		return count;
	}

	private static int reply(CommandSourceStack source, String message) {
		source.sendSuccess(() -> Component.literal(message), true);
		return 1;
	}

	/** Test helper: jump to the surface of any zone of Hell. */
	private static int gotoZone(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		String id = StringArgumentType.getString(ctx, "zone");
		Zone zone = Arrays.stream(Zone.values()).filter(z -> z.id().equalsIgnoreCase(id)).findFirst().orElse(null);
		if (zone == null) {
			ctx.getSource().sendFailure(Component.literal("Unknown zone \"" + id + "\". Try: "
					+ Arrays.stream(Zone.values()).map(Zone::id).collect(Collectors.joining(", "))));
			return 0;
		}
		// scan inward along a few headings and land in the middle of the first stretch of that zone
		for (int step = 0; step < 24; step++) {
			double theta = step * 0.26;
			double start = -1;
			for (double r = InfernoGeometry.BORDER_RADIUS - 50; r >= 0; r -= 2) {
				boolean inside = InfernoGeometry.zoneAt(Math.cos(theta) * r, Math.sin(theta) * r) == zone;
				if (inside && start < 0) {
					start = r;
				} else if (!inside && start >= 0) {
					double mid = (start + r) / 2.0;
					return teleportToSurface(ctx.getSource(), (int) Math.round(Math.cos(theta) * mid), (int) Math.round(Math.sin(theta) * mid));
				}
			}
			if (start >= 0) {
				return teleportToSurface(ctx.getSource(), (int) Math.round(Math.cos(theta) * start / 2), (int) Math.round(Math.sin(theta) * start / 2));
			}
		}
		ctx.getSource().sendFailure(Component.literal("Could not find " + zone.id()));
		return 0;
	}

	private static int guardian(CommandContext<CommandSourceStack> ctx, Function<Guardian, String> action) {
		Guardian g = Guardian.byId(StringArgumentType.getString(ctx, "name"));
		if (g == null) {
			ctx.getSource().sendFailure(Component.literal("Unknown guardian. Try: minos, cerberus, plutus, minotaur, geryon"));
			return 0;
		}
		String result = action.apply(g);
		return reply(ctx.getSource(), result);
	}

	private static int toSpine(CommandSourceStack source, Spine.Way way) throws CommandSyntaxException {
		return teleportToSurface(source, way.x(Spine.START + 5, 0), way.z(Spine.START + 5, 0));
	}

	private static int teleportToSurface(CommandSourceStack source, int x, int z) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ServerLevel level = source.getServer().overworld();
		if (!HellWorldgen.isInferno(level)) {
			source.sendFailure(Component.literal("This world was not created as an Inferno world (World Type: Inferno)."));
			return 0;
		}
		level.getChunk(x >> 4, z >> 4);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		if (level.getBlockState(new BlockPos(x, y - 1, z)).is(Blocks.LAVA)) {
			player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 1200, 0));
		}
		player.teleportTo(level, x + 0.5, y, z + 0.5, java.util.Set.of(), player.getYRot(), player.getXRot(), true);
		source.sendSuccess(() -> Component.literal("\u2192 " + InfernoGeometry.regionName(x, z) + " (" + x + ", " + y + ", " + z + ")")
				.withStyle(ChatFormatting.RED), false);
		return 1;
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
