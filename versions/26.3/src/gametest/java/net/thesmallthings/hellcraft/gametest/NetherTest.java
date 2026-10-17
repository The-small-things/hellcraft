package net.thesmallthings.hellcraft.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.thesmallthings.hellcraft.block.HellBlocks;
import net.thesmallthings.hellcraft.block.SlagCrustBlock;
import net.thesmallthings.hellcraft.client.nav.ClientNav;
import net.thesmallthings.hellcraft.entity.HellEntities;
import net.thesmallthings.hellcraft.entity.boss.CyclopsEntity;
import net.thesmallthings.hellcraft.entity.boss.gen.VulcanAnim;
import net.thesmallthings.hellcraft.hazard.guardian.Guardian;
import net.thesmallthings.hellcraft.hazard.guardian.GuardianManager;
import net.thesmallthings.hellcraft.world.ForgeGeometry;
import net.thesmallthings.hellcraft.world.feature.CyclopsCampFeature;

import java.util.Locale;
import java.util.Properties;

/**
 * The Forge of Dis, the Nether of an Inferno world (Phase 6), seen from a spectator's camera (screenshots forge_*): the
 * Great Forge's mesa, the Slag Sea, each of the four regions, and the crust cracking and melting.
 */
public class NetherTest implements FabricClientGameTest {
	/** A camera: where it stands and what it looks at. */
	private record Shot(String name, double x, double y, double z, double lx, double ly, double lz, ForgeGeometry.Region region) {
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		Properties properties = new Properties();
		properties.setProperty("view-distance", "8");
		properties.setProperty("simulation-distance", "4");
		Shot[] shots = {
				new Shot("forge_mesa", 70, 80, 24, 0, 66, 0, ForgeGeometry.Region.GREAT_FORGE),
				new Shot("forge_slag_sea", 96, 46, 12, 50, 31, 0, ForgeGeometry.Region.SLAG_SEA),
				new Shot("forge_foundry_halls", 12, 72, -190, 0, 58, -250, ForgeGeometry.Region.FOUNDRY_HALLS),
				new Shot("forge_chainlands", 180, 92, 8, 260, 72, 30, ForgeGeometry.Region.CHAINLANDS),
				new Shot("forge_ash_wastes", 8, 76, 190, 0, 56, 250, ForgeGeometry.Region.ASH_WASTES),
				new Shot("forge_cyclops_quarries", -180, 86, 8, -250, 56, 0, ForgeGeometry.Region.CYCLOPS_QUARRIES),
		};
		try (TestDedicatedServerContext server = context.worldBuilder().adjustSettings(MapTest::inferno).createServer(properties);
			 TestDedicatedServerConnection connection = server.connect()) {
			connection.waitForChunksRender();
			server.runCommand("gamemode spectator Player0");
			context.runOnClient(client -> {
				if (!client.gui.hud.isHidden()) {
					client.gui.hud.toggle();
				}
			});
			for (Shot shot : shots) {
				String biome = server.computeOnServer(s -> {
					ServerLevel nether = s.getLevel(Level.NETHER);
					return nether.getBiome(BlockPos.containing(shot.lx(), shot.ly(), shot.lz())).unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
				});
				if (!biome.equals(shot.region().id())) {
					throw new AssertionError(shot.name() + " looks at " + biome + ", not " + shot.region().id());
				}
				server.runCommand(String.format(Locale.ROOT, "execute in minecraft:the_nether run tp Player0 %.1f %.1f %.1f facing %.1f %.1f %.1f",
						shot.x(), shot.y(), shot.z(), shot.lx(), shot.ly(), shot.lz()));
				context.waitTicks(20);
				connection.waitForChunksRender();
				context.waitTicks(20);
				context.takeScreenshot(shot.name());
			}
			// a stairway down the mesa to its causeway (the Forge is built once a player has come near)
			String test = server.computeOnServer(s -> net.thesmallthings.hellcraft.world.ForgeOfDis.selfTest(s));
			if (!test.startsWith("Forge test OK")) {
				throw new AssertionError(test);
			}
			server.runCommand("execute in minecraft:the_nether run tp Player0 74 52 9 facing 44 62 0");
			context.waitTicks(20);
			connection.waitForChunksRender();
			context.takeScreenshot("forge_stairway");
			crust(context, server, connection);
			// the heat column over the Forge from the Ash Wastes, with the compass pointing to it
			context.runOnClient(client -> {
				if (client.gui.hud.isHidden()) {
					client.gui.hud.toggle();
				}
			});
			server.runCommand("execute in minecraft:the_nether run tp Player0 30 88 140 facing 0 105 0");
			context.waitTicks(20);
			connection.waitForChunksRender();
			context.waitTicks(10);
			context.takeScreenshot("forge_heat_column");
			chainLift(context, server, connection);
			vulcanFight(context, server, connection);
			cyclopsCamp(context, server, connection);
		}
	}

	/**
	 * A Foundry Halls chain-lift (screenshots forge_chain_lift_*): a player stands on the plate by a pillar's chain, rises
	 * to the top and steps off onto the arm.
	 */
	private static void chainLift(ClientGameTestContext context, TestDedicatedServerContext server, TestDedicatedServerConnection connection) {
		for (int i = -4; i <= 4; i++) {
			for (int j = -10; j <= -6; j++) {
				int[] site = net.thesmallthings.hellcraft.world.feature.FoundryPillarFeature.site(i, j);
				if (site == null) {
					continue;
				}
				int px = site[0] + net.thesmallthings.hellcraft.world.feature.FoundryPillarFeature.LIFT_DX;
				int pz = site[1] + 3;
				server.runCommand(String.format(Locale.ROOT, "execute in minecraft:the_nether run tp Player0 %d 90 %d", px, pz + 12));
				context.waitTicks(30);
				connection.waitForChunksRender();
				int plate = server.computeOnServer(s -> {
					ServerLevel nether = s.getLevel(Level.NETHER);
					for (int y = 110; y > ForgeGeometry.LAVA; y--) {
						if (nether.getBlockState(new BlockPos(px, y, pz)).is(net.minecraft.world.level.block.Blocks.POLISHED_BLACKSTONE_PRESSURE_PLATE)) {
							return y;
						}
					}
					return Integer.MIN_VALUE;
				});
				if (plate == Integer.MIN_VALUE) {
					continue;
				}
				server.runCommand("gamemode survival Player0");
				server.runCommand("effect give Player0 minecraft:resistance 60 4 true");
				server.runCommand(String.format(Locale.ROOT, "execute in minecraft:the_nether run tp Player0 %.1f %d %.1f facing %.1f %d %.1f",
						px + 0.5, plate, pz + 0.5, px + 0.5, plate + 4, pz + 8.0));
				context.waitTicks(30);
				context.takeScreenshot("forge_chain_lift_rising");
				context.waitTicks(120);
				double y = server.computeOnServer(s -> s.getPlayerList().getPlayerByName("Player0").getY());
				context.takeScreenshot("forge_chain_lift_top");
				server.runCommand("effect clear Player0");
				server.runCommand("gamemode spectator Player0");
				if (y < plate + 8) {
					throw new AssertionError("the chain-lift didn't carry the player up: from " + plate + " to " + y);
				}
				return;
			}
		}
		throw new AssertionError("no chain-lift found in the Foundry Halls");
	}

	/**
	 * Vulcan's fight in the Great Forge, against a dummy (screenshots vulcan_fight_*): his entrance, then each blow as its
	 * danger mark fills, his rage at half health, and his death.
	 */
	private static void vulcanFight(ClientGameTestContext context, TestDedicatedServerContext server, TestDedicatedServerConnection connection) {
		context.runOnClient(client -> {
			if (!client.gui.hud.isHidden()) {
				client.gui.hud.toggle();
			}
		});
		server.runCommand("execute in minecraft:the_nether run tp Player0 -6 76 -11 facing 1 71 -2");
		context.waitTicks(20);
		connection.waitForChunksRender();
		String out = server.computeOnServer(s -> GuardianManager.summon(s, Guardian.VULCAN));
		if (!out.contains("awakens")) {
			throw new AssertionError("Vulcan could not be summoned: " + out);
		}
		server.runCommand("execute in minecraft:the_nether run summon minecraft:villager 3 70 -6 {NoAI:1b,Invulnerable:1b,PersistenceRequired:1b,Tags:[\"fight_dummy\"]}");
		context.waitTicks(VulcanAnim.INTRO.at("roar") + 6);
		context.takeScreenshot("vulcan_fight_intro");
		context.waitTicks(VulcanAnim.INTRO.ticks - VulcanAnim.INTRO.at("roar") + 10);
		String[][] shots = {
				{"strike", String.valueOf(VulcanAnim.STRIKE.at("flash") + 6)},
				{"hammerfall", String.valueOf(VulcanAnim.HAMMERFALL.at("leap"))},
				{"molten", String.valueOf(VulcanAnim.POUR.at("pour") + 4)},
				{"chains", String.valueOf(VulcanAnim.THROW.at("throw") - 6)},
				{"seams", "18"},
				{"forgeborn", String.valueOf(VulcanAnim.SUMMON.at("ring") + 10)},
		};
		for (String[] shot : shots) {
			String used = server.computeOnServer(s -> GuardianManager.attack(Guardian.VULCAN, shot[0]));
			if (!used.contains(" uses ")) {
				throw new AssertionError("Vulcan could not use " + shot[0] + ": " + used);
			}
			context.waitTicks(Integer.parseInt(shot[1]));
			context.takeScreenshot("vulcan_fight_" + shot[0]);
			context.waitTicks(90);
		}
		// bring him under half his health: he drives his hammer into the floor
		server.runOnServer(s -> s.getLevel(Level.NETHER).getEntities(HellEntities.VULCAN, e -> true)
				.forEach(v -> v.setHealth(v.getMaxHealth() * 0.45f)));
		context.waitTicks(VulcanAnim.PHASE.at("plunge") + 4);
		context.takeScreenshot("vulcan_fight_rage");
		context.waitTicks(80);
		server.computeOnServer(s -> GuardianManager.slay(Guardian.VULCAN));
		context.waitTicks(VulcanAnim.DEATH.at("fall"));
		context.takeScreenshot("vulcan_fight_death");
		server.runCommand("kill @e[tag=fight_dummy]");
		if (!context.computeOnClient(client -> ClientNav.codex().stream().anyMatch(e -> e.id().equals("vulcan")))) {
			throw new AssertionError("meeting Vulcan didn't open his Codex entry");
		}
	}

	/**
	 * A Cyclops's camp in the quarries (screenshots cyclops_camp, cyclops_*): the camp from above, then his three blows
	 * against a dummy as their danger marks fill.
	 */
	private static void cyclopsCamp(ClientGameTestContext context, TestDedicatedServerContext server, TestDedicatedServerConnection connection) {
		int[] camp = null;
		for (int i = -4; i <= -1 && camp == null; i++) {
			for (int j = -2; j <= 1 && camp == null; j++) {
				camp = CyclopsCampFeature.site(i, j);
			}
		}
		if (camp == null) {
			throw new AssertionError("no Cyclops camp west of the Forge");
		}
		int[] at = camp;
		server.runCommand(String.format(Locale.ROOT, "execute in minecraft:the_nether run tp Player0 %d 100 %d facing %d 60 %d", at[0] + 14, at[1] + 14,
				at[0], at[1]));
		context.waitTicks(20);
		connection.waitForChunksRender();
		int floor = server.computeOnServer(s -> {
			ServerLevel nether = s.getLevel(Level.NETHER);
			for (int y = 100; y > ForgeGeometry.LAVA; y--) {
				if (nether.getBlockState(new BlockPos(at[0], y, at[1])).is(net.minecraft.world.level.block.Blocks.CAMPFIRE)) {
					return y;
				}
			}
			return -1;
		});
		if (floor < 0) {
			throw new AssertionError("the camp at " + at[0] + ", " + at[1] + " has no fire");
		}
		server.runCommand(String.format(Locale.ROOT, "execute in minecraft:the_nether run tp Player0 %d %d %d facing %d %d %d", at[0] - 10, floor + 5,
				at[1] - 7, at[0] - 2, floor + 2, at[1]));
		context.waitTicks(20);
		connection.waitForChunksRender();
		context.waitTicks(10);
		context.takeScreenshot("cyclops_camp");
		boolean there = server.computeOnServer(s -> !s.getLevel(Level.NETHER).getEntities(HellEntities.CYCLOPS,
				c -> c.distanceToSqr(at[0], c.getY(), at[1]) < 100).isEmpty());
		if (!there) {
			throw new AssertionError("no Cyclops at his camp");
		}
		server.runCommand(String.format(Locale.ROOT, "execute in minecraft:the_nether run summon minecraft:villager %d %d %d {NoAI:1b,Invulnerable:1b,"
				+ "PersistenceRequired:1b,Tags:[\"fight_dummy\"]}", at[0] + 3, floor, at[1] - 4));
		String[][] blows = {{"smash", "16"}, {"boulder", "40"}, {"quake", "22"}};
		for (String[] blow : blows) {
			String out = server.computeOnServer(s -> CyclopsEntity.attackNear(s.getLevel(Level.NETHER),
					new net.minecraft.world.phys.Vec3(at[0], floor, at[1]), blow[0]));
			if (!out.contains("uses")) {
				throw new AssertionError("the Cyclops could not use " + blow[0] + ": " + out);
			}
			context.waitTicks(Integer.parseInt(blow[1]));
			context.takeScreenshot("cyclops_" + blow[0]);
			context.waitTicks(70);
		}
		server.runCommand("kill @e[tag=fight_dummy]");
	}

	/** The causeway east of the mesa wears crust; cracked, it glows through three stages and melts. */
	private static void crust(ClientGameTestContext context, TestDedicatedServerContext server, TestDedicatedServerConnection connection) {
		BlockPos at = new BlockPos(86, ForgeGeometry.LAVA - 1, 0);
		server.runCommand(String.format(Locale.ROOT, "execute in minecraft:the_nether run tp Player0 %d %d %d facing %d %d %d",
				at.getX() + 4, at.getY() + 4, at.getZ() + 3, at.getX(), at.getY(), at.getZ()));
		context.waitTicks(20);
		connection.waitForChunksRender();
		boolean crusted = server.computeOnServer(s -> s.getLevel(Level.NETHER).getBlockState(at).is(HellBlocks.SLAG_CRUST));
		if (!crusted) {
			throw new AssertionError("no crust on the causeway at " + at.toShortString() + ": "
					+ server.computeOnServer(s -> s.getLevel(Level.NETHER).getBlockState(at).toString()));
		}
		context.takeScreenshot("forge_crust_0");
		server.runOnServer(s -> {
			ServerLevel nether = s.getLevel(Level.NETHER);
			for (BlockPos p : BlockPos.betweenClosed(at.offset(-1, 0, -1), at.offset(1, 0, 1))) {
				BlockState state = nether.getBlockState(p);
				if (state.is(HellBlocks.SLAG_CRUST)) {
					SlagCrustBlock.crack(nether, p.immutable(), state);
				}
			}
		});
		context.waitTicks(SlagCrustBlock.CRACK_TICKS + 2);
		context.takeScreenshot("forge_crust_2");
		context.waitTicks(SlagCrustBlock.CRACK_TICKS * 2 + 2);
		int stage = server.computeOnServer(s -> {
			BlockState state = s.getLevel(Level.NETHER).getBlockState(at);
			return state.is(HellBlocks.SLAG_CRUST) ? state.getValue(SlagCrustBlock.STAGE) : -1;
		});
		context.takeScreenshot("forge_crust_molten");
		if (stage != SlagCrustBlock.MOLTEN) {
			throw new AssertionError("the cracked crust didn't melt: stage " + stage);
		}
	}
}
