package net.thesmallthings.hellcraft.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.MinecraftServer;
import net.thesmallthings.hellcraft.entity.HellEntities;
import net.thesmallthings.hellcraft.entity.boss.BossEntity;
import net.thesmallthings.hellcraft.entity.boss.MinosEntity;
import net.thesmallthings.hellcraft.entity.boss.gen.MinosAnim;

import java.util.List;

/**
 * Minos's model and animations (screenshots minos_*): at rest from the front and the side, then each animation at
 * its telling moment, with the danger language's white flash and purple glow where his fight shows them.
 */
public class BossTest implements FabricClientGameTest {
	/** An animation, how far into it to look (ticks), and what he warns of meanwhile. */
	private record Pose(MinosAnim anim, int at, BossEntity.Warning warning) {
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("gamemode spectator Player0");
			world.getServer().runCommand("execute at Player0 run summon hellcraft:minos ~ ~ ~16 {Rotation:[180f,0f],Silent:1b}");
			world.getConnection().waitForClientboundEntityUpdates(HellEntities.MINOS);
			context.runOnClient(client -> {
				if (!client.gui.hud.isHidden()) {
					client.gui.hud.toggle();
				}
			});
			// from the front, eye to eye
			world.getServer().runCommand("execute at @e[type=hellcraft:minos,limit=1] run tp Player0 ~ ~5 ~-16 0 8");
			context.waitTicks(30);
			boolean animated = context.computeOnClient(client -> client.level.getEntitiesOfClass(MinosEntity.class,
					client.player.getBoundingBox().inflate(40)).stream().anyMatch(m -> m.states[MinosAnim.IDLE.ordinal()].isStarted()));
			if (!animated) {
				throw new AssertionError("the client sees no Minos with his idle animation running");
			}
			context.takeScreenshot("minos_front");
			// from his right, to see the tail curled round him
			world.getServer().runCommand("execute at @e[type=hellcraft:minos,limit=1] run tp Player0 ~17 ~5 ~2 90 12");
			context.waitTicks(20);
			context.takeScreenshot("minos_side");
			world.getServer().runCommand("execute at @e[type=hellcraft:minos,limit=1] run tp Player0 ~-10 ~6 ~-14 -35 12");
			List<Pose> poses = List.of(
					new Pose(MinosAnim.INTRO, MinosAnim.INTRO.at("roar") + 8, BossEntity.Warning.NONE),
					new Pose(MinosAnim.STRIKE, MinosAnim.STRIKE.at("flash") + 3, BossEntity.Warning.WHITE),
					new Pose(MinosAnim.STRIKE, MinosAnim.STRIKE.at("hit"), BossEntity.Warning.NONE),
					new Pose(MinosAnim.TAIL, MinosAnim.TAIL.at("sweep") + 4, BossEntity.Warning.PURPLE),
					new Pose(MinosAnim.SENTENCE, 20, BossEntity.Warning.NONE),
					new Pose(MinosAnim.GRAB, MinosAnim.GRAB.ticks, BossEntity.Warning.NONE),
					new Pose(MinosAnim.STAGGER, 6, BossEntity.Warning.NONE),
					new Pose(MinosAnim.PHASE, MinosAnim.PHASE.at("roar") + 10, BossEntity.Warning.NONE),
					new Pose(MinosAnim.DEATH, MinosAnim.DEATH.ticks - 4, BossEntity.Warning.NONE));
			for (Pose pose : poses) {
				world.getServer().runOnServer(server -> {
					MinosEntity minos = minos(server);
					minos.warn(pose.warning());
					minos.play(pose.anim());
				});
				context.waitTicks(pose.at());
				context.takeScreenshot("minos_" + pose.anim().name().toLowerCase() + "_" + pose.at());
				world.getServer().runOnServer(server -> minos(server).warn(BossEntity.Warning.NONE));
				context.waitTicks(Math.max(5, pose.anim().ticks - pose.at() + 5));
			}
			context.runOnClient(client -> {
				if (client.gui.hud.isHidden()) {
					client.gui.hud.toggle();
				}
			});
		}
	}

	private static MinosEntity minos(MinecraftServer server) {
		return server.overworld().getEntities(HellEntities.MINOS, m -> true).getFirst();
	}
}
