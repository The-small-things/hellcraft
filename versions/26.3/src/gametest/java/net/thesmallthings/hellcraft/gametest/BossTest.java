package net.thesmallthings.hellcraft.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.entity.Ferry;
import net.thesmallthings.hellcraft.entity.HellEntities;
import net.thesmallthings.hellcraft.entity.boss.BossEntity;
import net.thesmallthings.hellcraft.entity.boss.CerberusEntity;
import net.thesmallthings.hellcraft.entity.boss.MinosEntity;
import net.thesmallthings.hellcraft.entity.boss.PhlegyasEntity;
import net.thesmallthings.hellcraft.entity.boss.PlutusEntity;
import net.thesmallthings.hellcraft.entity.boss.RollingCoin;
import net.thesmallthings.hellcraft.entity.boss.gen.CerberusAnim;
import net.thesmallthings.hellcraft.entity.boss.gen.MinosAnim;
import net.thesmallthings.hellcraft.entity.boss.gen.PhlegyasAnim;
import net.thesmallthings.hellcraft.entity.boss.gen.PlutusAnim;
import net.thesmallthings.hellcraft.entity.monster.SoulMonster;

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
			world.getServer().runCommand("kill @e[type=hellcraft:minos]");
			cerberus(context, world);
			world.getServer().runCommand("kill @e[type=hellcraft:cerberus]");
			plutus(context, world);
			world.getServer().runCommand("kill @e[type=hellcraft:plutus]");
			phlegyas(context, world);
			world.getServer().runCommand("kill @e[type=hellcraft:phlegyas]");
			world.getServer().runCommand("kill @e[type=hellcraft:ferry]");
			monsters(context, world);
			context.runOnClient(client -> {
				if (client.gui.hud.isHidden()) {
					client.gui.hud.toggle();
				}
			});
		}
	}

	/**
	 * The upper circles' monsters in a row (screenshots monsters_*): the lovers, a glutton, a wrathful soul and a
	 * hoarder, at rest, then attacking, then each with its special (the glutton rising from the mud, the wrathful
	 * beating its chest).
	 */
	private static void monsters(ClientGameTestContext context, TestSingleplayerContext world) {
		String[] kinds = {"lovers", "glutton", "wrathful", "hoarder"};
		for (int i = 0; i < kinds.length; i++) {
			world.getServer().runCommand(String.format(java.util.Locale.ROOT,
					"execute at Player0 run summon hellcraft:%s ~%d ~%s ~120 {NoAI:1b,Rotation:[180f,0f],Silent:1b,Tags:[\"lineup\"]}",
					kinds[i], -6 + i * 4, kinds[i].equals("lovers") ? "1.5" : "0"));
		}
		world.getConnection().waitForClientboundEntityUpdates(HellEntities.WRATHFUL);
		world.getServer().runCommand("execute at Player0 run tp Player0 ~ ~2.5 ~111 0 8");
		context.waitTicks(30);
		context.takeScreenshot("monsters_lineup");
		world.getServer().runOnServer(server -> {
			for (var e : server.overworld().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
					server.getPlayerList().getPlayerByName("Player0").getBoundingBox().inflate(30), m -> m.entityTags().contains("lineup"))) {
				server.overworld().broadcastEntityEvent(e, net.minecraft.world.entity.EntityEvent.START_ATTACKING);
			}
		});
		context.waitTicks(8);
		context.takeScreenshot("monsters_attack");
		context.waitTicks(30);
		world.getServer().runOnServer(server -> {
			for (var e : server.overworld().getEntitiesOfClass(SoulMonster.class,
					server.getPlayerList().getPlayerByName("Player0").getBoundingBox().inflate(30), m -> m.entityTags().contains("lineup"))) {
				e.playSpecial();
			}
		});
		context.waitTicks(16);
		context.takeScreenshot("monsters_special");
		world.getServer().runCommand("kill @e[tag=lineup]");
	}

	/** Phlegyas's model and animations (screenshots phlegyas_*), as Minos's, and his ferry beside him. */
	private static void phlegyas(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("execute at Player0 run summon hellcraft:phlegyas ~ ~ ~80 {Rotation:[180f,0f],Silent:1b}");
		world.getConnection().waitForClientboundEntityUpdates(HellEntities.PHLEGYAS);
		world.getServer().runOnServer(server -> {
			PhlegyasEntity p = server.overworld().getEntities(HellEntities.PHLEGYAS, e -> true).getFirst();
			Ferry ferry = HellEntities.FERRY.create(server.overworld(), EntitySpawnReason.EVENT);
			Vec3 at = p.position().add(9, 0.4, 2);
			ferry.route(at, at, at.add(0, 0, 400), at.add(0, 0, 400), 6000);
			server.overworld().addFreshEntity(ferry);
		});
		world.getServer().runCommand("execute at @e[type=hellcraft:phlegyas,limit=1] run tp Player0 ~ ~7 ~-17 0 10");
		context.waitTicks(30);
		boolean animated = context.computeOnClient(client -> client.level.getEntitiesOfClass(PhlegyasEntity.class,
				client.player.getBoundingBox().inflate(40)).stream().anyMatch(c -> c.states[PhlegyasAnim.IDLE.ordinal()].isStarted()));
		if (!animated) {
			throw new AssertionError("the client sees no Phlegyas with his idle animation running");
		}
		context.takeScreenshot("phlegyas_front");
		world.getServer().runCommand("execute at @e[type=hellcraft:phlegyas,limit=1] run tp Player0 ~18 ~7 ~-6 70 12");
		context.waitTicks(20);
		context.takeScreenshot("phlegyas_side");
		world.getServer().runCommand("execute at @e[type=hellcraft:phlegyas,limit=1] run tp Player0 ~-11 ~8 ~-14 -35 14");
		record PhlegyasPose(PhlegyasAnim anim, int at, BossEntity.Warning warning) {
		}
		List<PhlegyasPose> poses = List.of(
				new PhlegyasPose(PhlegyasAnim.INTRO, PhlegyasAnim.INTRO.at("roar") + 8, BossEntity.Warning.NONE),
				new PhlegyasPose(PhlegyasAnim.OAR, PhlegyasAnim.OAR.at("flash") + 3, BossEntity.Warning.WHITE),
				new PhlegyasPose(PhlegyasAnim.OAR, PhlegyasAnim.OAR.at("hit"), BossEntity.Warning.NONE),
				new PhlegyasPose(PhlegyasAnim.SWEEP, (PhlegyasAnim.SWEEP.at("sweep") + PhlegyasAnim.SWEEP.at("swept")) / 2, BossEntity.Warning.PURPLE),
				new PhlegyasPose(PhlegyasAnim.WAVE, PhlegyasAnim.WAVE.at("slam"), BossEntity.Warning.NONE),
				new PhlegyasPose(PhlegyasAnim.GRAB, PhlegyasAnim.GRAB.at("grab"), BossEntity.Warning.NONE),
				new PhlegyasPose(PhlegyasAnim.STAGGER, 6, BossEntity.Warning.NONE),
				new PhlegyasPose(PhlegyasAnim.PHASE, PhlegyasAnim.PHASE.at("roar") + 8, BossEntity.Warning.NONE),
				new PhlegyasPose(PhlegyasAnim.DEATH, PhlegyasAnim.DEATH.at("sink"), BossEntity.Warning.NONE));
		for (PhlegyasPose pose : poses) {
			world.getServer().runOnServer(server -> {
				PhlegyasEntity p = server.overworld().getEntities(HellEntities.PHLEGYAS, e -> true).getFirst();
				p.warn(pose.warning());
				p.play(pose.anim());
			});
			context.waitTicks(pose.at());
			context.takeScreenshot("phlegyas_" + pose.anim().name().toLowerCase() + "_" + pose.at());
			world.getServer().runOnServer(server -> server.overworld().getEntities(HellEntities.PHLEGYAS, e -> true).getFirst().warn(BossEntity.Warning.NONE));
			context.waitTicks(Math.max(5, pose.anim().ticks - pose.at() + 5));
		}
	}

	/** Plutus's model and animations (screenshots plutus_*), as Minos's, and a great coin of the Mint rolling past. */
	private static void plutus(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("execute at Player0 run summon hellcraft:plutus ~ ~ ~60 {Rotation:[180f,0f],Silent:1b}");
		world.getConnection().waitForClientboundEntityUpdates(HellEntities.PLUTUS);
		world.getServer().runCommand("execute at @e[type=hellcraft:plutus,limit=1] run tp Player0 ~ ~5 ~-14 0 8");
		context.waitTicks(30);
		boolean animated = context.computeOnClient(client -> client.level.getEntitiesOfClass(PlutusEntity.class,
				client.player.getBoundingBox().inflate(40)).stream().anyMatch(c -> c.states[PlutusAnim.IDLE.ordinal()].isStarted()));
		if (!animated) {
			throw new AssertionError("the client sees no Plutus with his idle animation running");
		}
		context.takeScreenshot("plutus_front");
		world.getServer().runCommand("execute at @e[type=hellcraft:plutus,limit=1] run tp Player0 ~14 ~5 ~-2 90 10");
		context.waitTicks(20);
		context.takeScreenshot("plutus_side");
		world.getServer().runCommand("execute at @e[type=hellcraft:plutus,limit=1] run tp Player0 ~-9 ~6 ~-12 -35 12");
		record PlutusPose(PlutusAnim anim, int at, BossEntity.Warning warning) {
		}
		List<PlutusPose> poses = List.of(
				new PlutusPose(PlutusAnim.INTRO, PlutusAnim.INTRO.at("roar") + 8, BossEntity.Warning.NONE),
				new PlutusPose(PlutusAnim.FIST, PlutusAnim.FIST.at("flash") + 3, BossEntity.Warning.WHITE),
				new PlutusPose(PlutusAnim.FIST, PlutusAnim.FIST.at("hit"), BossEntity.Warning.NONE),
				new PlutusPose(PlutusAnim.GOLD, PlutusAnim.GOLD.at("throw") + 2, BossEntity.Warning.NONE),
				new PlutusPose(PlutusAnim.LUNGE, PlutusAnim.LUNGE.at("leap") + 2, BossEntity.Warning.NONE),
				new PlutusPose(PlutusAnim.PAPE, PlutusAnim.PAPE.at("shriek") + 4, BossEntity.Warning.PURPLE),
				new PlutusPose(PlutusAnim.STAGGER, 6, BossEntity.Warning.NONE),
				new PlutusPose(PlutusAnim.PHASE, PlutusAnim.PHASE.at("roar") + 6, BossEntity.Warning.NONE),
				new PlutusPose(PlutusAnim.DEATH, PlutusAnim.DEATH.ticks - 4, BossEntity.Warning.NONE));
		for (PlutusPose pose : poses) {
			world.getServer().runOnServer(server -> {
				PlutusEntity p = server.overworld().getEntities(HellEntities.PLUTUS, e -> true).getFirst();
				p.warn(pose.warning());
				p.play(pose.anim());
			});
			context.waitTicks(pose.at());
			context.takeScreenshot("plutus_" + pose.anim().name().toLowerCase() + "_" + pose.at());
			world.getServer().runOnServer(server -> server.overworld().getEntities(HellEntities.PLUTUS, e -> true).getFirst().warn(BossEntity.Warning.NONE));
			context.waitTicks(Math.max(5, pose.anim().ticks - pose.at() + 5));
		}
		// a great coin rolling across in front of the camera
		world.getServer().runOnServer(server -> {
			PlutusEntity p = server.overworld().getEntities(HellEntities.PLUTUS, e -> true).getFirst();
			RollingCoin coin = HellEntities.ROLLING_COIN.create(server.overworld(), EntitySpawnReason.EVENT);
			coin.snapTo(p.getX() - 14, p.getY(), p.getZ() - 5, 0, 0);
			coin.launch(null, 0, 60);
			server.overworld().addFreshEntity(coin);
		});
		context.waitTicks(22);
		context.takeScreenshot("plutus_coin");
	}

	/** Cerberus's model and animations (screenshots cerberus_*), as Minos's. */
	private static void cerberus(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("execute at Player0 run summon hellcraft:cerberus ~ ~ ~40 {Rotation:[180f,0f],Silent:1b}");
		world.getConnection().waitForClientboundEntityUpdates(HellEntities.CERBERUS);
		world.getServer().runCommand("execute at @e[type=hellcraft:cerberus,limit=1] run tp Player0 ~ ~5 ~-13 0 12");
		context.waitTicks(30);
		boolean animated = context.computeOnClient(client -> client.level.getEntitiesOfClass(CerberusEntity.class,
				client.player.getBoundingBox().inflate(40)).stream().anyMatch(c -> c.states[CerberusAnim.IDLE.ordinal()].isStarted()));
		if (!animated) {
			throw new AssertionError("the client sees no Cerberus with his idle animation running");
		}
		context.takeScreenshot("cerberus_front");
		world.getServer().runCommand("execute at @e[type=hellcraft:cerberus,limit=1] run tp Player0 ~16 ~6 ~-2 90 15");
		context.waitTicks(20);
		context.takeScreenshot("cerberus_side");
		world.getServer().runCommand("execute at @e[type=hellcraft:cerberus,limit=1] run tp Player0 ~-9 ~6 ~-12 -35 18");
		record CerberusPose(CerberusAnim anim, int at, BossEntity.Warning warning) {
		}
		List<CerberusPose> poses = List.of(
				new CerberusPose(CerberusAnim.INTRO, CerberusAnim.INTRO.at("roar") + 8, BossEntity.Warning.NONE),
				new CerberusPose(CerberusAnim.BITE_RIGHT, CerberusAnim.BITE_RIGHT.at("flash") + 3, BossEntity.Warning.WHITE),
				new CerberusPose(CerberusAnim.BITE_MID, CerberusAnim.BITE_MID.at("bite"), BossEntity.Warning.NONE),
				new CerberusPose(CerberusAnim.MAWS, CerberusAnim.MAWS.at("maw2"), BossEntity.Warning.NONE),
				new CerberusPose(CerberusAnim.FILTH, CerberusAnim.FILTH.at("spew") + 2, BossEntity.Warning.NONE),
				new CerberusPose(CerberusAnim.HOWL, CerberusAnim.HOWL.at("howl") + 4, BossEntity.Warning.PURPLE),
				new CerberusPose(CerberusAnim.CHARGE, 6, BossEntity.Warning.NONE),
				new CerberusPose(CerberusAnim.STAGGER, 6, BossEntity.Warning.NONE),
				new CerberusPose(CerberusAnim.PHASE, CerberusAnim.PHASE.at("roar") + 8, BossEntity.Warning.NONE),
				new CerberusPose(CerberusAnim.DEATH, CerberusAnim.DEATH.ticks - 4, BossEntity.Warning.NONE));
		for (CerberusPose pose : poses) {
			world.getServer().runOnServer(server -> {
				CerberusEntity c = server.overworld().getEntities(HellEntities.CERBERUS, e -> true).getFirst();
				c.warn(pose.warning());
				c.play(pose.anim());
			});
			context.waitTicks(pose.at());
			context.takeScreenshot("cerberus_" + pose.anim().name().toLowerCase() + "_" + pose.at());
			world.getServer().runOnServer(server -> {
				CerberusEntity c = server.overworld().getEntities(HellEntities.CERBERUS, e -> true).getFirst();
				c.warn(BossEntity.Warning.NONE);
				if (pose.anim().looping) {
					c.play(CerberusAnim.IDLE);
				}
			});
			context.waitTicks(Math.max(5, pose.anim().ticks - pose.at() + 5));
		}
	}

	private static MinosEntity minos(MinecraftServer server) {
		return server.overworld().getEntities(HellEntities.MINOS, m -> true).getFirst();
	}
}
