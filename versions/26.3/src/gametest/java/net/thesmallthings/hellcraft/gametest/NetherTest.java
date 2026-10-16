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
import net.thesmallthings.hellcraft.world.ForgeGeometry;

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
		}
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
