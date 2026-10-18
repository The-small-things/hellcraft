package net.thesmallthings.hellcraft.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.thesmallthings.hellcraft.client.nav.ClientNav;
import net.thesmallthings.hellcraft.entity.HellEntities;
import net.thesmallthings.hellcraft.entity.boss.gen.EmperorAnim;
import net.thesmallthings.hellcraft.entity.boss.gen.FallenSeraphAnim;
import net.thesmallthings.hellcraft.hazard.lucifer.LuciferManager;
import net.thesmallthings.hellcraft.world.InfernoGeometry;

import java.util.Locale;
import java.util.Properties;

/**
 * Lucifer's whole fight in an Inferno world, played by a real (unhurtable) player at the pit (screenshots lucifer_*):
 * the ready check, the Fallen Seraph's descent, his four blows as their danger marks fill, his enrage, his breaking and the Emperor's
 * reveal, the Emperor's slam with the hand that stays down to be hit, his wingbeat and weeping, and the climb out
 * past his frozen body to Purgatory.
 */
public class LuciferTest implements FabricClientGameTest {
	/** The fight's own clock runs at luciferTempo (0.85 by default): a blow's fight ticks take this many real ticks each. */
	private static final double SLOW = 1 / 0.85;

	@Override
	public void runTest(ClientGameTestContext context) {
		Properties properties = new Properties();
		properties.setProperty("view-distance", "8");
		properties.setProperty("simulation-distance", "4");
		try (TestDedicatedServerContext server = context.worldBuilder().adjustSettings(MapTest::inferno).createServer(properties);
			 TestDedicatedServerConnection connection = server.connect()) {
			connection.waitForChunksRender();
			server.runCommand("forceload add -48 -48 48 48");
			// the pit's floor (not the heightmap: Purgatory floats over the pit)
			int ground = server.computeOnServer(s -> {
				for (int y = InfernoGeometry.PIT_FLOOR_Y + 32; y > s.overworld().getMinY(); y--) {
					if (s.overworld().getBlockState(new BlockPos(30, y, 0)).isSolid()) {
						return y;
					}
				}
				return InfernoGeometry.PIT_FLOOR_Y;
			});
			// a real fighter (survival, so the fight counts them), who can't be hurt
			server.runCommand("gamemode survival Player0");
			server.runOnServer(s -> {
				ServerPlayer p = s.getPlayerList().getPlayerByName("Player0");
				p.getAbilities().invulnerable = true;
				p.onUpdateAbilities();
			});
			server.runCommand(String.format(Locale.ROOT, "tp Player0 30.5 %d 0.5 90 10", ground + 1));
			context.waitTicks(20);
			connection.waitForChunksRender();
			descent(context, server);
			seraph(context, server);
			reveal(context, server);
			emperor(context, server, connection);
			climb(context, server);
		}
	}

	/**
	 * Standing on the pit's floor starts the ready check (the countdown, shortened once everyone near is in); then the
	 * pit seals and the Seraph comes down (the descent cutscene, its name card).
	 */
	private static void descent(ClientGameTestContext context, TestDedicatedServerContext server) {
		waitForPhase(context, server, "READY", 100);
		context.waitTicks(30);
		context.takeScreenshot("lucifer_ready");
		waitForPhase(context, server, "INTRO", 200);
		context.waitTicks(FallenSeraphAnim.INTRO.at("land") + 4);
		context.takeScreenshot("lucifer_descent_land");
		context.waitTicks(FallenSeraphAnim.INTRO.at("speak") - FallenSeraphAnim.INTRO.at("land") + 10);
		context.takeScreenshot("lucifer_descent_speak");
		context.waitTicks(FallenSeraphAnim.INTRO.ticks + 20 - FallenSeraphAnim.INTRO.at("speak") - 14);
		context.takeScreenshot("lucifer_descent_card");
		context.waitTicks(50);
		expectPhase(server, "DUEL");
		if (!context.computeOnClient(client -> ClientNav.codex().stream().anyMatch(e -> e.id().equals("lucifer")))) {
			throw new AssertionError("meeting Lucifer didn't open his Codex entry");
		}
		context.takeScreenshot("lucifer_duel");
	}

	/** Each of the Seraph's blows as its mark fills, then his enrage. */
	private static void seraph(ClientGameTestContext context, TestDedicatedServerContext server) {
		for (String attack : new String[]{"slash", "sweep", "stars", "lunge"}) {
			String used = server.computeOnServer(s -> LuciferManager.attack(attack));
			if (!used.contains("The Seraph uses")) {
				throw new AssertionError("the Seraph could not use " + attack + ": " + used);
			}
			context.waitTicks(20);
			context.takeScreenshot("lucifer_" + attack);
			context.waitTicks(90);
		}
		server.computeOnServer(s -> LuciferManager.skip());
		context.waitTicks(FallenSeraphAnim.SNAP.at("snap") + 6);
		context.takeScreenshot("lucifer_enrage");
		context.waitTicks(80);
		expectPhase(server, "ENRAGED");
	}

	/** His seraph form breaks, and the mountain in the middle of the pit opens its eyes. */
	private static void reveal(ClientGameTestContext context, TestDedicatedServerContext server) {
		server.computeOnServer(s -> LuciferManager.skip());
		int breakAt = FallenSeraphAnim.DEATH.at("break");
		context.waitTicks(breakAt - 4);
		context.takeScreenshot("lucifer_break");
		context.waitTicks(4 + EmperorAnim.REVEAL.at("open") + 12);
		context.takeScreenshot("lucifer_reveal_open");
		context.waitTicks(EmperorAnim.REVEAL.at("roar") - EmperorAnim.REVEAL.at("open"));
		context.takeScreenshot("lucifer_reveal_roar");
		context.waitTicks(EmperorAnim.REVEAL.ticks + 30 - EmperorAnim.REVEAL.at("roar") - 12 + 20);
		expectPhase(server, "TRUE_FORM");
	}

	/** The Emperor's blows, seen from the pit's floor: a slam and the hand it leaves down, his wingbeat, his tears. */
	private static void emperor(ClientGameTestContext context, TestDedicatedServerContext server, TestDedicatedServerConnection connection) {
		// from high up beside the pit, the whole of him
		server.runCommand("execute as Player0 at @s run tp @s 34 ~30 -26 facing 0 ~18 0");
		context.waitTicks(10);
		context.takeScreenshot("lucifer_emperor_overview");
		server.runCommand("execute as Player0 at @s run tp @s 30.5 ~-30 0.5 90 -28");
		context.waitTicks(10);
		// look up at him from the floor
		server.runCommand("execute as Player0 at @s run tp @s ~ ~ ~ 90 -28");
		context.waitTicks(10);
		context.takeScreenshot("lucifer_emperor");
		use(server, "slam");
		boolean opened = false;
		for (int i = 0; i < 12 && !opened; i++) {
			context.waitTicks(10);
			opened = server.computeOnServer(s -> !s.overworld().getEntities(HellEntities.EMPEROR_WEAK_POINT, e -> true).isEmpty());
		}
		if (!opened) {
			throw new AssertionError("the Emperor's slam left no hand down to hit");
		}
		server.runCommand("execute as Player0 at @s run tp @s ~ ~ ~ facing entity @e[type=hellcraft:emperor_weak_point,limit=1] feet");
		context.waitTicks(6);
		context.takeScreenshot("lucifer_slam_hand");
		server.runCommand("execute as Player0 at @s run tp @s ~ ~ ~ 90 -28");
		context.waitTicks(160);
		use(server, "bite");
		context.waitTicks((int) (EmperorAnim.BITE.at("flash") * SLOW) + 4);
		context.takeScreenshot("lucifer_bite");
		context.waitTicks(200);
		use(server, "wingbeat");
		context.waitTicks(30);
		context.takeScreenshot("lucifer_wingbeat");
		context.waitTicks(160);
		use(server, "weep");
		context.waitTicks(24);
		context.takeScreenshot("lucifer_weep");
		context.waitTicks(120);
		// the blows he gained from the playtest: both hands at once, a hand dragged round, his frozen breath
		use(server, "both_hands");
		context.waitTicks((int) (EmperorAnim.SLAM_BOTH.at("slam") * SLOW) + 4);
		context.takeScreenshot("lucifer_both_hands");
		context.waitTicks(160);
		use(server, "drag");
		context.waitTicks((int) ((EmperorAnim.DRAG_RIGHT.at("down") + 20) * SLOW));
		context.takeScreenshot("lucifer_drag");
		context.waitTicks(160);
		use(server, "breath");
		context.waitTicks((int) ((EmperorAnim.BREATH.at("breathe") + 16) * SLOW));
		context.takeScreenshot("lucifer_breath");
		context.waitTicks(160);
	}

	/** He freezes, the victors climb down his side (the camera rolls over as they pass his middle) and out to Purgatory. */
	private static void climb(ClientGameTestContext context, TestDedicatedServerContext server) {
		server.computeOnServer(s -> LuciferManager.skip());
		context.waitTicks(EmperorAnim.DEATH.at("freeze") + 6);
		context.takeScreenshot("lucifer_freeze");
		context.waitTicks(EmperorAnim.DEATH.ticks - EmperorAnim.DEATH.at("freeze") - 6 + 40);
		context.takeScreenshot("lucifer_climb_down");
		context.waitTicks(60);
		context.takeScreenshot("lucifer_climb_turn");
		context.waitTicks(100);
		String status = server.computeOnServer(s -> LuciferManager.status());
		if (!status.contains("No fight")) {
			throw new AssertionError("the fight didn't end after the climb: " + status);
		}
		// Purgatory is its own dimension (Purgatory.LEVEL)
		boolean out = server.computeOnServer(s -> s.getPlayerList().getPlayerByName("Player0").level().dimension()
				== net.thesmallthings.hellcraft.world.Purgatory.LEVEL);
		context.waitTicks(20);
		context.takeScreenshot("lucifer_purgatory");
		if (!out) {
			throw new AssertionError("the climb didn't bring the player out to Purgatory");
		}
	}

	private static void use(TestDedicatedServerContext server, String attack) {
		String used = server.computeOnServer(s -> LuciferManager.attack(attack));
		if (!used.contains("The Emperor uses")) {
			throw new AssertionError("the Emperor could not use " + attack + ": " + used);
		}
	}

	/** Waits, a tick at a time, until the fight is in this phase. */
	private static void waitForPhase(ClientGameTestContext context, TestDedicatedServerContext server, String phase, int ticks) {
		for (int i = 0; i < ticks; i++) {
			if (server.computeOnServer(s -> LuciferManager.status()).contains("phase=" + phase + " ")) {
				return;
			}
			context.waitTicks(1);
		}
		throw new AssertionError("Lucifer's fight never reached " + phase + ": " + server.computeOnServer(s -> LuciferManager.status()));
	}

	private static void expectPhase(TestDedicatedServerContext server, String phase) {
		String status = server.computeOnServer(s -> LuciferManager.status());
		if (!status.contains("phase=" + phase + " ")) {
			throw new AssertionError("expected Lucifer's fight in " + phase + ": " + status);
		}
	}
}
