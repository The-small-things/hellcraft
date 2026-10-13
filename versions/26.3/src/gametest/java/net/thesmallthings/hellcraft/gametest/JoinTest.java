package net.thesmallthings.hellcraft.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.thesmallthings.hellcraft.HellcraftMod;

/** A client with Hellcraft joins a dedicated server running the same Hellcraft: the join check lets it in. */
public class JoinTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestDedicatedServerContext server = context.worldBuilder().createServer();
			 TestDedicatedServerConnection connection = server.connect()) {
			connection.waitForChunksRender();
			int players = server.computeOnServer(s -> s.getPlayerList().getPlayerCount());
			if (players != 1) {
				throw new AssertionError("expected the client on the server, but it has " + players + " players");
			}
			HellcraftMod.LOGGER.info("Client test: joined a dedicated server running Hellcraft {}", HellcraftMod.VERSION);
			context.takeScreenshot("join_dedicated_server");
		}
	}
}
