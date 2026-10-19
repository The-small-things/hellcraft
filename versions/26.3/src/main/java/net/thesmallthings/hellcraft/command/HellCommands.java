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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.thesmallthings.hellcraft.blood.Admin;
import net.thesmallthings.hellcraft.blood.BloodAltar;
import net.thesmallthings.hellcraft.blood.BloodArmour;
import net.thesmallthings.hellcraft.blood.BloodItems;
import net.thesmallthings.hellcraft.blood.BossSpoils;
import net.thesmallthings.hellcraft.blood.Bounty;
import net.thesmallthings.hellcraft.blood.Fairness;
import net.thesmallthings.hellcraft.blood.GhostPowers;
import net.thesmallthings.hellcraft.blood.Ghosts;
import net.thesmallthings.hellcraft.blood.Hearts;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.blood.HellWeapons;
import net.thesmallthings.hellcraft.blood.LayerEnchants;
import net.thesmallthings.hellcraft.blood.Loadout;
import net.thesmallthings.hellcraft.blood.Prestige;
import net.thesmallthings.hellcraft.blood.Relics;
import net.thesmallthings.hellcraft.blood.TravelMenu;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.hazard.BossModel;
import net.thesmallthings.hellcraft.hazard.Danger;
import net.thesmallthings.hellcraft.hazard.Frauds;
import net.thesmallthings.hellcraft.hazard.Hoarders;
import net.thesmallthings.hellcraft.hazard.LayerRules;
import net.thesmallthings.hellcraft.hazard.LowerMonsters;
import net.thesmallthings.hellcraft.hazard.LowerRules;
import net.thesmallthings.hellcraft.hazard.StyleMeter;
import net.thesmallthings.hellcraft.hazard.guardian.Arenas;
import net.thesmallthings.hellcraft.hazard.guardian.Guardian;
import net.thesmallthings.hellcraft.hazard.guardian.GuardianManager;
import net.thesmallthings.hellcraft.hazard.lucifer.LuciferManager;
import net.thesmallthings.hellcraft.hazard.lucifer.LuciferRewards;
import net.thesmallthings.hellcraft.net.Dialogue;
import net.thesmallthings.hellcraft.net.HudSync;
import net.thesmallthings.hellcraft.net.MapSync;
import net.thesmallthings.hellcraft.world.AntaeusCleft;
import net.thesmallthings.hellcraft.world.Circle;
import net.thesmallthings.hellcraft.world.Ferries;
import net.thesmallthings.hellcraft.world.GreatForge;
import net.thesmallthings.hellcraft.world.HallOfTheDamned;
import net.thesmallthings.hellcraft.world.Heaven;
import net.thesmallthings.hellcraft.world.HellWorldgen;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.Paradiso;
import net.thesmallthings.hellcraft.world.ParadisoGeometry;
import net.thesmallthings.hellcraft.world.Purgatory;
import net.thesmallthings.hellcraft.world.Shades;
import net.thesmallthings.hellcraft.world.ShrineSites;
import net.thesmallthings.hellcraft.world.Shrines;
import net.thesmallthings.hellcraft.world.Spine;
import net.thesmallthings.hellcraft.world.WhiteRoom;
import net.thesmallthings.hellcraft.world.Zone;
import org.jetbrains.annotations.Nullable;

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

		// the Journal (J) holds the Guide: this opens it, for anyone who'd rather type
		dispatcher.register(Commands.literal("guide").executes(ctx -> {
			MapSync.openJournal(ctx.getSource().getPlayerOrException());
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
						player.sendSystemMessage(Component.literal("You have no Lucifer reward to claim.").withStyle(ChatFormatting.GRAY));
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
								.then(Commands.literal("summon").executes(ctx -> guardian(ctx, g -> GuardianManager.summon(ctx.getSource().getServer(), g,
										g.nearestLair(ctx.getSource().getPosition().x, ctx.getSource().getPosition().z)))))
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
				.then(Commands.literal("giveitem")
						.then(Commands.argument("player", EntityArgument.player())
								.then(Commands.argument("item", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("vigil", "anchor", "halo", "seraph_wings", "beatrices_rose"), builder))
										.executes(ctx -> {
											ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
											String item = StringArgumentType.getString(ctx, "item");
											Relics.Relic relic = Relics.Relic.byId(item);
											ItemStack stack = switch (item) {
												case "vigil" -> BloodItems.vigil(1);
												case "anchor" -> BloodItems.anchor(1);
												default -> relic != null ? Relics.create(relic) : ItemStack.EMPTY;
											};
											if (stack.isEmpty()) {
												ctx.getSource().sendFailure(Component.literal("Unknown item. Try: vigil, anchor, halo, seraph_wings, beatrices_rose"));
												return 0;
											}
											BloodItems.give(p, stack);
											ctx.getSource().sendSuccess(() -> Component.literal("Gave a " + item + " to " + p.getGameProfile().name()), true);
											return 1;
										}))))
				.then(Commands.literal("lifts")
						.then(Commands.literal("test").executes(ctx -> reply(ctx.getSource(),
								net.thesmallthings.hellcraft.hazard.guardian.LairLifts.selfTest()))))
				.then(Commands.literal("loadout")
						.executes(ctx -> loadout(ctx.getSource(), List.of(ctx.getSource().getPlayerOrException()), Loadout.Kit.FULL))
						.then(Commands.argument("players", EntityArgument.players())
								.executes(ctx -> loadout(ctx.getSource(), EntityArgument.getPlayers(ctx, "players"), Loadout.Kit.FULL))
								.then(Commands.argument("kit", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
												Arrays.stream(Loadout.Kit.values()).map(Loadout.Kit::id), builder))
										.executes(ctx -> {
											Loadout.Kit kit = Loadout.Kit.byId(StringArgumentType.getString(ctx, "kit"));
											if (kit == null) {
												ctx.getSource().sendFailure(Component.literal("Unknown kit. Try: upper, lower, lucifer, paradiso (what you'd have "
														+ "by then), or full, blood (everything)."));
												return 0;
											}
											return loadout(ctx.getSource(), EntityArgument.getPlayers(ctx, "players"), kit);
										}))))
				.then(Commands.literal("arena")
						.then(Commands.argument("boss", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arenas.ids(), builder))
								.executes(ctx -> arena(ctx.getSource(), List.of(ctx.getSource().getPlayerOrException()), StringArgumentType.getString(ctx, "boss"), 0))
								.then(Commands.argument("lair", IntegerArgumentType.integer(0, InfernoGeometry.LAIRS_EACH))
										.executes(ctx -> arena(ctx.getSource(), List.of(ctx.getSource().getPlayerOrException()),
												StringArgumentType.getString(ctx, "boss"), IntegerArgumentType.getInteger(ctx, "lair")))
										.then(Commands.argument("players", EntityArgument.players())
												.executes(ctx -> arena(ctx.getSource(), EntityArgument.getPlayers(ctx, "players"),
														StringArgumentType.getString(ctx, "boss"), IntegerArgumentType.getInteger(ctx, "lair")))))))
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
						.then(Commands.literal("burrow").executes(ctx -> reply(ctx.getSource(), LuciferManager.openBurrow(ctx.getSource().getServer()))))
						.then(Commands.literal("grant")
								.then(Commands.argument("players", EntityArgument.players())
										.executes(ctx -> {
											int n = 0;
											for (ServerPlayer p : EntityArgument.getPlayers(ctx, "players")) {
												LuciferRewards.grantVictory(p);
												n++;
											}
											return reply(ctx.getSource(), "Granted a victory over Lucifer to " + n + " player" + (n == 1 ? "" : "s") + ".");
										})))
						.then(Commands.literal("attack")
								.then(Commands.argument("attack", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(new String[]{"slash", "sweep", "stars", "lunge",
												"slam", "bite", "wingbeat", "weep"}, builder))
										.executes(ctx -> reply(ctx.getSource(), LuciferManager.attack(StringArgumentType.getString(ctx, "attack")))))))
				.then(Commands.literal("cyclops")
						.then(Commands.literal("summon").executes(ctx -> {
							net.minecraft.world.phys.Vec3 at = ctx.getSource().getPosition();
							net.thesmallthings.hellcraft.entity.boss.CyclopsEntity c = net.thesmallthings.hellcraft.entity.HellEntities.CYCLOPS
									.create(ctx.getSource().getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
							if (c == null) {
								return reply(ctx.getSource(), "Could not make a Cyclops.");
							}
							c.snapTo(at.x, at.y, at.z, 0, 0);
							ctx.getSource().getLevel().addFreshEntity(c);
							return reply(ctx.getSource(), "A Cyclops stands at " + (int) at.x + ", " + (int) at.y + ", " + (int) at.z + ".");
						}))
						.then(Commands.literal("attack").then(Commands.argument("attack", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(new String[]{"smash", "boulder", "quake"}, builder))
								.executes(ctx -> reply(ctx.getSource(), net.thesmallthings.hellcraft.entity.boss.CyclopsEntity.attackNear(
										ctx.getSource().getLevel(), ctx.getSource().getPosition(), StringArgumentType.getString(ctx, "attack")))))))
				.then(Commands.literal("forge").then(Commands.literal("test").executes(ctx -> reply(ctx.getSource(),
						net.thesmallthings.hellcraft.world.ForgeOfDis.selfTest(ctx.getSource().getServer())))))
				.then(Commands.literal("brazier").then(Commands.literal("test").executes(ctx -> reply(ctx.getSource(),
						net.thesmallthings.hellcraft.world.feature.BrazierFeature.selfTest(ctx.getSource().getServer().overworld())))))
				.then(Commands.literal("wingbeat").executes(ctx -> reply(ctx.getSource(), net.thesmallthings.hellcraft.hazard.Wingbeat.now(ctx.getSource().getServer()))))
				.then(Commands.literal("gate").executes(ctx -> teleportToSurface(ctx.getSource(), InfernoGeometry.gateX() + 24, 0)))
				.then(Commands.literal("shrine")
						.then(Commands.literal("list").executes(ctx -> {
							List<String> lines = Shrines.list(ctx.getSource().getServer());
							lines.forEach(l -> ctx.getSource().sendSuccess(() -> Component.literal(l), false));
							return lines.size();
						}))
						.then(Commands.literal("visit")
								.then(Commands.argument("player", EntityArgument.player())
										.then(Commands.argument("rest", StringArgumentType.word())
												.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(
														java.util.stream.Stream.of("all"), ShrineSites.all().stream().map(ShrineSites.Site::id)), builder))
												.executes(ctx -> {
													ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
													String rest = StringArgumentType.getString(ctx, "rest");
													HellState.Soul soul = Hearts.soul(p);
													int before = soul.visited.size();
													for (ShrineSites.Site site : ShrineSites.all()) {
														if (rest.equals("all") || site.id().equals(rest)) {
															soul.visited.add(site.id());
														}
													}
													HellState.get(ctx.getSource().getServer()).setDirty();
													int added = soul.visited.size() - before;
													ctx.getSource().sendSuccess(() -> Component.literal(p.getGameProfile().name() + " now remembers "
															+ added + " more Virgil's Rest(s)"), true);
													return added;
												}))))
						.then(Commands.literal("build")
								.executes(ctx -> buildShrines(ctx.getSource(), null))
								.then(Commands.argument("circle", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ShrineSites.all().stream()
												.map(s -> s.zone().name().toLowerCase(java.util.Locale.ROOT)).distinct(), builder))
										.executes(ctx -> buildShrines(ctx.getSource(), StringArgumentType.getString(ctx, "circle"))))))
				.then(Commands.literal("travel")
						.then(Commands.argument("player", EntityArgument.player())
								.then(Commands.argument("to", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(
												java.util.stream.Stream.of("gate"), ShrineSites.all().stream().map(ShrineSites.Site::id)), builder))
										.executes(ctx -> {
											ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
											TravelMenu.Destination to = TravelMenu.find(ctx.getSource().getServer(), Hearts.soul(p), StringArgumentType.getString(ctx, "to"));
											if (to == null) {
												ctx.getSource().sendFailure(Component.literal("No such place (or that Rest isn't built yet). Try: gate, limbo_1, ..."));
												return 0;
											}
											String result = TravelMenu.travel(p, to, true);
											ctx.getSource().sendSuccess(() -> Component.literal(result), true);
											return 1;
										}))))
				.then(Commands.literal("paradiso")
						.then(Commands.literal("status").executes(ctx -> {
							String status = Paradiso.status(ctx.getSource().getServer());
							ctx.getSource().sendSuccess(() -> Component.literal(status), false);
							return 1;
						}))
						.then(Commands.literal("test").executes(ctx -> reply(ctx.getSource(), Paradiso.selfTest(ctx.getSource().getServer()))))
						.then(Commands.literal("host").executes(ctx -> {
							int n = Heaven.host(Paradiso.level(ctx.getSource().getServer()), java.util.EnumSet.allOf(ParadisoGeometry.Sphere.class), true);
							ctx.getSource().sendSuccess(() -> Component.literal(n + " angels join the host"), true);
							return n;
						}))
						.then(Commands.literal("angels").executes(ctx -> {
							int n = Heaven.angels(ctx.getSource().getLevel(), BlockPos.containing(ctx.getSource().getPosition()));
							ctx.getSource().sendSuccess(() -> Component.literal(n + " angels descend"), true);
							return n;
						}))
						.then(Commands.literal("go")
								.then(Commands.argument("sphere", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
												Arrays.stream(ParadisoGeometry.SPHERES).map(s -> s.id), builder))
										.executes(ctx -> {
											ServerPlayer p = ctx.getSource().getPlayerOrException();
											String id = StringArgumentType.getString(ctx, "sphere");
											ParadisoGeometry.Sphere sphere = Arrays.stream(ParadisoGeometry.SPHERES).filter(s -> s.id.equals(id)).findFirst().orElse(null);
											ServerLevel level = Paradiso.level(ctx.getSource().getServer());
											if (sphere == null || level == null) {
												ctx.getSource().sendFailure(Component.literal(level == null ? "No Paradiso in this world" : "moon, mercury, ... empyrean"));
												return 0;
											}
											Paradiso.setDown(p, level, sphere);
											return reply(ctx.getSource(), "\u2192 " + sphere.title);
										})))
						.then(Commands.literal("pass")
								.then(Commands.argument("player", EntityArgument.player())
										.then(Commands.argument("spheres", IntegerArgumentType.integer(0, 10))
												.executes(ctx -> {
													ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
													HellState.Soul soul = Hearts.soul(p);
													soul.spheres = (1 << IntegerArgumentType.getInteger(ctx, "spheres")) - 1;
													HellState.get(ctx.getSource().getServer()).setDirty();
													return reply(ctx.getSource(), p.getGameProfile().name() + " may now reach " + Paradiso.reach(soul).title);
												}))))
						.then(Commands.literal("ascent").executes(ctx -> {
							Heaven.buildAscent(ctx.getSource().getServer());
							ctx.getSource().sendSuccess(() -> Component.literal("The Ascent is at " + Heaven.ascent().toShortString()), true);
							return 1;
						})))
				.then(Commands.literal("forge")
						.executes(ctx -> {
							ServerPlayer p = ctx.getSource().getPlayerOrException();
							GreatForge.build(ctx.getSource().getServer());
							ServerLevel nether = GreatForge.nether(ctx.getSource().getServer());
							if (nether == null) {
								ctx.getSource().sendFailure(Component.literal("This server has no Nether."));
								return 0;
							}
							BlockPos c = GreatForge.center();
							p.teleportTo(nether, c.getX() + 0.5, c.getY(), c.getZ() + 17.5, java.util.Set.of(), 180.0f, 0.0f, true);
							return 1;
						})
						.then(Commands.literal("build").executes(ctx -> {
							GreatForge.build(ctx.getSource().getServer());
							ctx.getSource().sendSuccess(() -> Component.literal("The Great Forge of Dis stands at "
									+ GreatForge.center().toShortString() + " in the Nether."), true);
							return 1;
						})))
				.then(Commands.literal("inspect")
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> {
									Admin.inspect(EntityArgument.getPlayer(ctx, "player")).forEach(line -> ctx.getSource().sendSuccess(() -> line, false));
									return 1;
								})))
				.then(Commands.literal("setbonus")
						.then(Commands.argument("player", EntityArgument.player())
								.then(Commands.argument("hearts", IntegerArgumentType.integer(0))
										.executes(ctx -> reply(ctx.getSource(), Admin.setBonus(EntityArgument.getPlayer(ctx, "player"),
												IntegerArgumentType.getInteger(ctx, "hearts")))))))
				.then(Commands.literal("reset")
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> reply(ctx.getSource(), Admin.reset(EntityArgument.getPlayer(ctx, "player"))))))
				.then(Commands.literal("strip")
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> reply(ctx.getSource(), Admin.strip(EntityArgument.getPlayer(ctx, "player"))))))
				.then(Commands.literal("bounty")
						.executes(ctx -> reply(ctx.getSource(), Bounty.status(ctx.getSource().getServer())))
						.then(Commands.literal("now").executes(ctx -> {
							String who = Bounty.markNow(ctx.getSource().getServer());
							return reply(ctx.getSource(), who == null ? "Nobody online has enough hearts for a bounty." : "The bounty is on " + who + ".");
						}))
						.then(Commands.literal("clear").executes(ctx -> {
							Bounty.clear();
							return reply(ctx.getSource(), "The bounty is cleared.");
						})))
				.then(Commands.literal("fairness")
						.then(Commands.literal("test").executes(ctx -> reply(ctx.getSource(), Fairness.selfTest()))))
				.then(Commands.literal("dialogue")
						.then(Commands.literal("test").executes(ctx -> reply(ctx.getSource(), Dialogue.selfTest(ctx.getSource().getPlayer())))))
				.then(Commands.literal("ferry")
						.then(Commands.literal("test").executes(ctx -> reply(ctx.getSource(), Ferries.selfTest(ctx.getSource().getServer().overworld())))))
				.then(Commands.literal("castle")
						.then(Commands.literal("test").executes(ctx -> reply(ctx.getSource(), Shades.castleTest(ctx.getSource().getServer().overworld())))))
				.then(Commands.literal("layers")
						.then(Commands.literal("test").executes(ctx -> reply(ctx.getSource(), LayerRules.selfTest() + "; " + Hoarders.selfTest()))))
				.then(Commands.literal("lower")
						.then(Commands.literal("test").executes(ctx -> reply(ctx.getSource(), LowerRules.selfTest() + "; " + LowerRules.woodTest(ctx.getSource().getServer().overworld())
								+ "; " + LowerMonsters.selfTest() + "; " + Frauds.selfTest()))))
				.then(Commands.literal("gear")
						.then(Commands.literal("test").executes(ctx -> reply(ctx.getSource(), LayerEnchants.selfTest(ctx.getSource().getServer())))))
				.then(Commands.literal("antaeus")
						.then(Commands.literal("test").executes(ctx -> reply(ctx.getSource(), AntaeusCleft.selfTest(ctx.getSource().getServer().overworld())))))
				.then(Commands.literal("danger")
						.then(Commands.literal("test").executes(ctx -> reply(ctx.getSource(), Danger.selfTest(ctx.getSource().getLevel()))))
						.then(Commands.literal("show").executes(ctx -> reply(ctx.getSource(), Danger.showcase(ctx.getSource().getPlayerOrException(), 40)))))
				.then(Commands.literal("spoils")
						.then(Commands.literal("test").executes(ctx -> reply(ctx.getSource(), BossSpoils.selfTest() + "; " + StyleMeter.selfTest() + "; " + BossModel.selfTest()))))
				.then(Commands.literal("config")
						.then(Commands.argument("key", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(HellConfig.keys(), builder))
								.executes(ctx -> {
									String key = StringArgumentType.getString(ctx, "key");
									String value = HellConfig.getValue(key);
									return reply(ctx.getSource(), value == null ? "No such setting: " + key : key + " = " + value);
								})
								.then(Commands.argument("value", StringArgumentType.greedyString())
										.executes(ctx -> {
											String result = HellConfig.setValue(StringArgumentType.getString(ctx, "key"), StringArgumentType.getString(ctx, "value"));
											ctx.getSource().getServer().getPlayerList().getPlayers().forEach(Hearts::apply);
											HudSync.sendAll(ctx.getSource().getServer());
											MapSync.sendAll(ctx.getSource().getServer());
											return reply(ctx.getSource(), result);
										}))))
				.then(Commands.literal("whiteroom")
						.then(Commands.literal("list").executes(ctx -> reply(ctx.getSource(), WhiteRoom.list(ctx.getSource().getServer()))))
						.then(Commands.literal("test").executes(ctx -> reply(ctx.getSource(), WhiteRoom.selfTest(ctx.getSource().getServer()))))
						.then(Commands.literal("release")
								.then(Commands.argument("player", EntityArgument.player())
										.executes(ctx -> reply(ctx.getSource(), WhiteRoom.release(EntityArgument.getPlayer(ctx, "player"))))))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> reply(ctx.getSource(), WhiteRoom.trap(EntityArgument.getPlayer(ctx, "player"))))))
				.then(Commands.literal("hall")
						.executes(ctx -> {
							HallOfTheDamned.describe(ctx.getSource().getServer()).forEach(line -> ctx.getSource().sendSuccess(() -> line, false));
							return 1;
						})
						.then(Commands.literal("build").executes(ctx -> {
							BlockPos at = HallOfTheDamned.build(ctx.getSource().getServer());
							return reply(ctx.getSource(), "The Hall of the Damned stands at " + at.toShortString());
						})))
				.then(Commands.literal("prestige")
						.then(Commands.literal("test").executes(ctx -> reply(ctx.getSource(), Prestige.selfTest())))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> reply(ctx.getSource(), Prestige.describe(Hearts.soul(EntityArgument.getPlayer(ctx, "player")))))
								.then(Commands.literal("set")
										.then(Commands.argument("burned", IntegerArgumentType.integer(0, Prestige.MAX))
												.executes(ctx -> {
													ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
													HellState.Soul soul = Hearts.soul(p);
													soul.prestige = IntegerArgumentType.getInteger(ctx, "burned");
													HellState.get(ctx.getSource().getServer()).setDirty();
													Hearts.apply(p);
													HudSync.send(p);
													return reply(ctx.getSource(), Prestige.describe(soul));
												})))))
				.then(Commands.literal("purgatory").executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					ServerLevel level = Purgatory.level(ctx.getSource().getServer());
					if (level == null) {
						ctx.getSource().sendFailure(Component.literal("No Purgatory in this world"));
						return 0;
					}
					Purgatory.teleport(p, level, Purgatory.shore());
					return reply(ctx.getSource(), "\u2192 the shore of Purgatory");
				}))
				.then(Commands.literal("spine")
						.executes(ctx -> toSpine(ctx.getSource(), Spine.Way.EAST))
						.then(Commands.literal("test").executes(ctx -> reply(ctx.getSource(), Spine.selfTest(ctx.getSource().getServer().overworld()))))
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
					HudSync.sendAll(ctx.getSource().getServer());
					MapSync.sendAll(ctx.getSource().getServer());
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
			player.sendSystemMessage(Component.literal("You can't withdraw your last heart. You have " + soul.hearts + " ❤.").withStyle(ChatFormatting.RED));
			return 0;
		}
		Hearts.add(player, -amount);
		BloodItems.give(player, BloodItems.heart(amount));
		player.sendSystemMessage(Component.literal("Withdrew " + amount + " heart" + (amount == 1 ? "" : "s") + " as Blood Heart items.")
				.withStyle(ChatFormatting.DARK_RED));
		return amount;
	}

	private static int buildShrines(CommandSourceStack source, @Nullable String circle) {
		List<String> built = Shrines.buildAll(source.getServer(), circle);
		source.sendSuccess(() -> Component.literal("Built " + built.size() + " Virgil's Rest(s)" + (built.isEmpty() ? "" : ": " + String.join("; ", built))), true);
		return built.size();
	}

	private static int circle(ServerPlayer player) {
		if (!HellWorldgen.isInferno(player.level())) {
			player.sendSystemMessage(Component.literal("You're not in any of the nine circles.").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		Circle circle = InfernoGeometry.circleAt(player.getX(), player.getZ());
		player.sendSystemMessage(Component.literal(circle.title()).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
		player.sendSystemMessage(Component.literal(InfernoGeometry.regionName(player.getX(), player.getZ())).withStyle(ChatFormatting.RED));
		player.sendSystemMessage(Component.literal("\"" + circle.quote() + "\"").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		int dist = (int) Math.sqrt(player.getX() * player.getX() + player.getZ() * player.getZ());
		player.sendSystemMessage(Component.literal(dist + " blocks from the centre (Lucifer's pit).").withStyle(ChatFormatting.DARK_GRAY));
		Component rest = Shrines.nearestLine(player);
		if (rest != null) {
			player.sendSystemMessage(rest);
		}
		return circle.depth();
	}

	/** /hellcraft arena: to the edge of a boss's arena (Arenas). */
	private static int arena(CommandSourceStack source, java.util.Collection<ServerPlayer> players, String boss, int lair) {
		if (!Arenas.ids().contains(boss)) {
			source.sendFailure(Component.literal("Unknown boss \"" + boss + "\". Try: " + String.join(", ", Arenas.ids())));
			return 0;
		}
		for (ServerPlayer p : players) {
			String out = Arenas.send(p, boss, lair);
			source.sendSuccess(() -> Component.literal(out), true);
		}
		return players.size();
	}

	/** /hellcraft loadout: a testing kit for each player (Loadout). */
	private static int loadout(CommandSourceStack source, java.util.Collection<ServerPlayer> players, Loadout.Kit kit) {
		for (ServerPlayer p : players) {
			int stacks = Loadout.give(p, kit);
			p.sendSystemMessage(Component.literal("You were given the " + kit.id() + " kit: " + kit.title + " (" + stacks
					+ " stacks; armour put on where the slot was empty).").withStyle(ChatFormatting.GOLD));
			if (kit.journey()) {
				p.sendSystemMessage(Component.literal("It's about what you'd have by then, for testing the bosses fairly: your hearts are now "
						+ Hearts.soul(p).hearts + " too (bosses hit harder the more hearts you have).").withStyle(ChatFormatting.GRAY));
			}
		}
		String who = players.size() == 1 ? players.iterator().next().getGameProfile().name() : players.size() + " players";
		return reply(source, "Gave the " + kit.id() + " kit to " + who + ".");
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
		source.sendSuccess(() -> Component.literal(count + " ghost(s) right now."), false);
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
