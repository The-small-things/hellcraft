package net.thesmallthings.hellcraft.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.entity.Ferry;
import net.thesmallthings.hellcraft.entity.HellEntities;
import net.thesmallthings.hellcraft.entity.boss.AnimatedBoss;
import net.thesmallthings.hellcraft.entity.boss.BossAnim;
import net.thesmallthings.hellcraft.entity.boss.BossEntity;
import net.thesmallthings.hellcraft.entity.boss.CerberusEntity;
import net.thesmallthings.hellcraft.entity.boss.EmperorEntity;
import net.thesmallthings.hellcraft.entity.boss.MinosEntity;
import net.thesmallthings.hellcraft.entity.boss.PhlegyasEntity;
import net.thesmallthings.hellcraft.entity.boss.PlutusEntity;
import net.thesmallthings.hellcraft.entity.boss.RollingCoin;
import net.thesmallthings.hellcraft.entity.boss.gen.AntaeusAnim;
import net.thesmallthings.hellcraft.entity.boss.gen.CerberusAnim;
import net.thesmallthings.hellcraft.entity.boss.gen.EmperorAnim;
import net.thesmallthings.hellcraft.entity.boss.gen.FallenSeraphAnim;
import net.thesmallthings.hellcraft.entity.boss.gen.FarinataAnim;
import net.thesmallthings.hellcraft.entity.boss.gen.FuryAnim;
import net.thesmallthings.hellcraft.entity.boss.gen.GeryonAnim;
import net.thesmallthings.hellcraft.entity.boss.gen.MedusaAnim;
import net.thesmallthings.hellcraft.entity.boss.gen.MinosAnim;
import net.thesmallthings.hellcraft.entity.boss.gen.MinotaurAnim;
import net.thesmallthings.hellcraft.entity.boss.gen.PhlegyasAnim;
import net.thesmallthings.hellcraft.entity.boss.gen.PlutusAnim;
import net.thesmallthings.hellcraft.entity.monster.SoulMonster;

import java.util.List;

/**
 * Minos's model and animations (screenshots minos_*): at rest from the front and the side, then each animation at
 * its telling moment, with the danger language's white flash and purple glow where his fight shows them.
 */
public class BossTest implements FabricClientGameTest {
	/**
	 * Set HELLCRAFT_POSE_AUDIT=1 to film every model's key frames from straight beside it instead (screenshots audit_*):
	 * the camera stands at the model's +X looking west, so its front (north) is always on the right of the picture.
	 * That shows at a glance whether a blow swings forward and a body bends the right way.
	 */
	private static final boolean AUDIT = System.getenv("HELLCRAFT_POSE_AUDIT") != null;

	private static void audit(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("kill @e[type=hellcraft:minos]");
		upper(context, world, HellEntities.CERBERUS, "cerberus", 12, 3, List.of(new Shot(CerberusAnim.BITE_MID, CerberusAnim.BITE_MID.at("bite"), null),
				new Shot(CerberusAnim.MAWS, CerberusAnim.MAWS.at("maw2"), null), new Shot(CerberusAnim.PHASE, CerberusAnim.PHASE.at("slam"), null),
				new Shot(CerberusAnim.STAGGER, 10, null)), (e, a) -> ((CerberusEntity) e).play((CerberusAnim) a));
		upper(context, world, HellEntities.PLUTUS, "plutus", 12, 3, List.of(new Shot(PlutusAnim.FIST, PlutusAnim.FIST.at("flash"), null),
				new Shot(PlutusAnim.FIST, PlutusAnim.FIST.at("hit"), null), new Shot(PlutusAnim.LUNGE, PlutusAnim.LUNGE.at("leap"), null),
				new Shot(PlutusAnim.LUNGE, PlutusAnim.LUNGE.at("land"), null), new Shot(PlutusAnim.STAGGER, 10, null)),
				(e, a) -> ((PlutusEntity) e).play((PlutusAnim) a));
		upper(context, world, HellEntities.PHLEGYAS, "phlegyas", 12, 3, List.of(new Shot(PhlegyasAnim.OAR, PhlegyasAnim.OAR.at("flash"), null),
				new Shot(PhlegyasAnim.OAR, PhlegyasAnim.OAR.at("hit"), null), new Shot(PhlegyasAnim.GRAB, PhlegyasAnim.GRAB.at("grab"), null),
				new Shot(PhlegyasAnim.WAVE, PhlegyasAnim.WAVE.at("slam"), null), new Shot(PhlegyasAnim.STAGGER, 10, null)),
				(e, a) -> ((PhlegyasEntity) e).play((PhlegyasAnim) a));
		lowerBosses(context, world);
		lucifer(context, world);
		for (String m : List.of("lovers", "glutton", "wrathful", "hoarder", "heretic", "centaur", "harpy", "mimic", "thief", "malebranche", "malacoda")) {
			monster(context, world, m);
		}
	}

	/** One of the upper guardians (their own entity classes) from beside it, at each shot. */
	private static <T extends BossEntity> void upper(ClientGameTestContext context, TestSingleplayerContext world, EntityType<T> type, String name,
													 double far, double eye, List<Shot> shots, java.util.function.BiConsumer<BossEntity, BossAnim> play) {
		world.getServer().runCommand("execute at Player0 run summon hellcraft:" + name + " ~ ~ ~" + (int) (far + 20) + " {Rotation:[180f,0f],Silent:1b}");
		world.getConnection().waitForClientboundEntityUpdates(type);
		String at = "execute at @e[type=hellcraft:" + name + ",limit=1] run ";
		world.getServer().runCommand(at + String.format("tp Player0 ~%.1f ~%.1f ~ 90 8", far, eye));
		context.waitTicks(20);
		for (Shot shot : shots) {
			world.getServer().runOnServer(server -> play.accept(server.overworld().getEntities(type, e -> true).getFirst(), shot.anim()));
			context.waitTicks(shot.at());
			context.takeScreenshot("audit_" + name + "_" + shot.anim().name().toLowerCase() + "_" + shot.at());
			context.waitTicks(Math.max(5, shot.anim().ticks() - shot.at() + 5));
		}
		world.getServer().runOnServer(server -> server.overworld().getEntities(type, e -> true).forEach(e -> e.discard()));
	}

	/** One of Hell's monsters from beside it: attacking (three moments), then its special. */
	private static void monster(ClientGameTestContext context, TestSingleplayerContext world, String name) {
		world.getServer().runCommand("execute at Player0 run summon hellcraft:" + name + " ~ ~ ~40 {NoAI:1b,Rotation:[180f,0f],Silent:1b,Tags:[\"audit\"]}");
		context.waitTicks(10);
		world.getServer().runCommand("execute at @e[tag=audit,limit=1] run tp Player0 ~5 ~1.2 ~ 90 5");
		context.waitTicks(20);
		context.takeScreenshot("audit_" + name + "_rest");
		for (int at : new int[]{4, 8, 12}) {
			world.getServer().runOnServer(server -> server.overworld().getAllEntities().forEach(e -> {
				if (e.entityTags().contains("audit")) {
					server.overworld().broadcastEntityEvent(e, net.minecraft.world.entity.EntityEvent.START_ATTACKING);
				}
			}));
			context.waitTicks(at);
			context.takeScreenshot("audit_" + name + "_attack_" + at);
			context.waitTicks(30);
		}
		world.getServer().runOnServer(server -> server.overworld().getAllEntities().forEach(e -> {
			if (e instanceof SoulMonster m && e.entityTags().contains("audit")) {
				m.playSpecial();
			}
		}));
		context.waitTicks(14);
		context.takeScreenshot("audit_" + name + "_special");
		world.getServer().runOnServer(server -> server.overworld().getAllEntities().forEach(e -> {
			if (e.entityTags().contains("audit")) {
				e.discard();
			}
		}));
		world.getServer().runCommand("execute at Player0 run tp Player0 ~-5 ~-1.2 ~-40 0 5");
		context.waitTicks(10);
	}

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
			if (AUDIT) {
				audit(context, world);
				return;
			}
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
			lowerBosses(context, world);
			lucifer(context, world);
			monsters(context, world);
			context.runOnClient(client -> {
				if (client.gui.hud.isHidden()) {
					client.gui.hud.toggle();
				}
			});
		}
	}

	/** One of a generic boss's poses: an animation, how far into it to look, and what it warns of meanwhile. */
	private record Shot(BossAnim anim, int at, BossEntity.Warning warning) {
	}

	/**
	 * Lower Hell's guardians, Medusa, the tamed Geryon and Antaeus (screenshots fury_*, medusa_*, farinata_*,
	 * minotaur_*, geryon_*, antaeus_*): each from the front and the side, then each animation at its telling moment.
	 */
	private static void lowerBosses(ClientGameTestContext context, TestSingleplayerContext world) {
		BossEntity.Warning w = BossEntity.Warning.WHITE;
		BossEntity.Warning p = BossEntity.Warning.PURPLE;
		BossEntity.Warning n = BossEntity.Warning.NONE;
		boss(context, world, HellEntities.FURY, "fury", 9, 2, List.of(
				new Shot(FuryAnim.INTRO, FuryAnim.INTRO.at("scream") + 6, n),
				new Shot(FuryAnim.TALONS, FuryAnim.TALONS.at("flash") + 3, w),
				new Shot(FuryAnim.TALONS, FuryAnim.TALONS.at("hit"), n),
				new Shot(FuryAnim.SHRIEK, FuryAnim.SHRIEK.at("scream") + 6, p),
				new Shot(FuryAnim.LASH, FuryAnim.LASH.at("lash"), n),
				new Shot(FuryAnim.TEAR, FuryAnim.TEAR.at("tear"), n),
				new Shot(FuryAnim.STAGGER, 8, n),
				new Shot(FuryAnim.PHASE, FuryAnim.PHASE.at("call") + 6, n),
				new Shot(FuryAnim.DEATH, FuryAnim.DEATH.ticks - 4, n)));
		boss(context, world, HellEntities.MEDUSA, "medusa", 14, 3, List.of(
				new Shot(MedusaAnim.INTRO, MedusaAnim.INTRO.ticks - 6, n),
				new Shot(MedusaAnim.GAZE, MedusaAnim.GAZE.at("open") + 12, n)));
		boss(context, world, HellEntities.FARINATA, "farinata", 10, 3, List.of(
				new Shot(FarinataAnim.INTRO, FarinataAnim.INTRO.at("speak") + 6, n),
				new Shot(FarinataAnim.DISDAIN, FarinataAnim.DISDAIN.at("flash") + 3, w),
				new Shot(FarinataAnim.DISDAIN, FarinataAnim.DISDAIN.at("hit"), n),
				new Shot(FarinataAnim.COMMAND, FarinataAnim.COMMAND.at("fire"), n),
				new Shot(FarinataAnim.EXILE, FarinataAnim.EXILE.at("blast"), p),
				new Shot(FarinataAnim.STAGGER, FarinataAnim.STAGGER.at("sink") + 6, n),
				new Shot(FarinataAnim.PHASE, FarinataAnim.PHASE.at("roar") + 6, n),
				new Shot(FarinataAnim.DEATH, FarinataAnim.DEATH.ticks - 4, n)));
		boss(context, world, HellEntities.MINOTAUR, "minotaur", 13, 4, List.of(
				new Shot(MinotaurAnim.INTRO, MinotaurAnim.INTRO.at("roar") + 8, n),
				new Shot(MinotaurAnim.GORE, MinotaurAnim.GORE.at("flash") + 3, w),
				new Shot(MinotaurAnim.GORE, MinotaurAnim.GORE.at("hit"), n),
				new Shot(MinotaurAnim.CHARGE, MinotaurAnim.CHARGE.at("go") - 6, n),
				new Shot(MinotaurAnim.STOMP, MinotaurAnim.STOMP.at("slam"), n),
				new Shot(MinotaurAnim.FRENZY, (MinotaurAnim.FRENZY.at("spin") + MinotaurAnim.FRENZY.at("spun")) / 2, p),
				new Shot(MinotaurAnim.STAGGER, 8, n),
				new Shot(MinotaurAnim.PHASE, MinotaurAnim.PHASE.at("bite") + 4, n),
				new Shot(MinotaurAnim.DEATH, MinotaurAnim.DEATH.ticks - 4, n)));
		boss(context, world, HellEntities.GERYON, "geryon", 16, 3, List.of(
				new Shot(GeryonAnim.INTRO, GeryonAnim.INTRO.at("roar") + 6, n),
				new Shot(GeryonAnim.STING, GeryonAnim.STING.at("flash") + 3, w),
				new Shot(GeryonAnim.STING, GeryonAnim.STING.at("hit"), n),
				new Shot(GeryonAnim.FALSEFACE, GeryonAnim.FALSEFACE.at("split"), p),
				new Shot(GeryonAnim.COIL, GeryonAnim.COIL.at("squeeze"), n),
				new Shot(GeryonAnim.DIVE, GeryonAnim.DIVE.at("go"), n),
				new Shot(GeryonAnim.CARRY, 20, n),
				new Shot(GeryonAnim.STAGGER, 8, n),
				new Shot(GeryonAnim.PHASE, GeryonAnim.PHASE.at("roar") + 6, n),
				new Shot(GeryonAnim.DEATH, GeryonAnim.DEATH.at("bow") + 8, n)));
		boss(context, world, HellEntities.ANTAEUS, "antaeus", 46, 14, List.of(
				new Shot(AntaeusAnim.LOWER, 25, n),
				new Shot(AntaeusAnim.LOWER, 50, n),
				new Shot(AntaeusAnim.LOWER, AntaeusAnim.LOWER.at("set"), n),
				new Shot(AntaeusAnim.RAISE, 30, n)));
	}

	/**
	 * Lucifer's two bodies (screenshots fallen_seraph_* and emperor_*): the Fallen Seraph's duel, and the Emperor
	 * asleep, waking, slamming, biting, beating his wings, weeping and freezing.
	 */
	private static void lucifer(ClientGameTestContext context, TestSingleplayerContext world) {
		BossEntity.Warning w = BossEntity.Warning.WHITE;
		BossEntity.Warning p = BossEntity.Warning.PURPLE;
		BossEntity.Warning n = BossEntity.Warning.NONE;
		boss(context, world, HellEntities.FALLEN_SERAPH, "fallen_seraph", 9, 2, List.of(
				new Shot(FallenSeraphAnim.INTRO, FallenSeraphAnim.INTRO.at("speak") + 4, n),
				new Shot(FallenSeraphAnim.SLASH, FallenSeraphAnim.SLASH.at("flash") + 3, w),
				new Shot(FallenSeraphAnim.SLASH, FallenSeraphAnim.SLASH.at("hit"), n),
				new Shot(FallenSeraphAnim.SWEEP, (FallenSeraphAnim.SWEEP.at("sweep") + FallenSeraphAnim.SWEEP.at("swept")) / 2, p),
				new Shot(FallenSeraphAnim.STARS, FallenSeraphAnim.STARS.at("call"), n),
				new Shot(FallenSeraphAnim.LUNGE, FallenSeraphAnim.LUNGE.at("go"), n),
				new Shot(FallenSeraphAnim.STAGGER, 8, n),
				new Shot(FallenSeraphAnim.SNAP, FallenSeraphAnim.SNAP.at("snap") + 6, n),
				new Shot(FallenSeraphAnim.DEATH, FallenSeraphAnim.DEATH.at("break") - 2, n)));
		boss(context, world, HellEntities.EMPEROR, "emperor", 75, 28, List.of(
				new Shot(EmperorAnim.DORMANT, 30, n),
				new Shot(EmperorAnim.REVEAL, EmperorAnim.REVEAL.at("open") + 10, n),
				new Shot(EmperorAnim.SLAM_RIGHT, EmperorAnim.SLAM_RIGHT.at("slam") - 12, n),
				new Shot(EmperorAnim.SLAM_RIGHT, EmperorAnim.SLAM_RIGHT.at("slam") + 10, n),
				new Shot(EmperorAnim.BITE, EmperorAnim.BITE.at("flash") + 3, w),
				new Shot(EmperorAnim.BITE_LEFT, EmperorAnim.BITE_LEFT.at("bite") + 10, n),
				new Shot(EmperorAnim.WINGBEAT, EmperorAnim.WINGBEAT.at("beat") - 6, p),
				new Shot(EmperorAnim.WEEP, EmperorAnim.WEEP.at("weep") + 10, n),
				new Shot(EmperorAnim.DEATH, EmperorAnim.DEATH.ticks - 4, n)));
	}

	private static <T extends AnimatedBoss> void boss(ClientGameTestContext context, TestSingleplayerContext world, EntityType<T> type, String name,
													  double far, double eye, List<Shot> shots) {
		world.getServer().runCommand("execute at Player0 run summon hellcraft:" + name + " ~ ~ ~" + (int) (far + 20) + " {Rotation:[180f,0f],Silent:1b}");
		world.getConnection().waitForClientboundEntityUpdates(type);
		// the Emperor with his eyes open (but asleep in his DORMANT shot)
		world.getServer().runOnServer(server -> server.overworld().getEntities(type, e -> e instanceof EmperorEntity)
				.forEach(e -> ((EmperorEntity) e).awake(true)));
		String at = "execute at @e[type=hellcraft:" + name + ",limit=1] run ";
		world.getServer().runCommand(at + String.format("tp Player0 ~ ~%.1f ~%.1f 0 8", eye, -far));
		context.waitTicks(30);
		boolean animated = context.computeOnClient(client -> client.level.getEntitiesOfClass(AnimatedBoss.class,
				client.player.getBoundingBox().inflate(far * 2)).stream().anyMatch(b -> b.getType() == type && b.states[0].isStarted()));
		if (!animated) {
			throw new AssertionError("the client sees no " + name + " with its idle animation running");
		}
		context.takeScreenshot(name + "_front");
		world.getServer().runCommand(at + String.format("tp Player0 ~%.1f ~%.1f ~-2 90 10", far, eye));
		context.waitTicks(20);
		context.takeScreenshot(name + "_side");
		// from in front and to its left; Antaeus from his right, where the hand that carries you is
		boolean right = type == HellEntities.ANTAEUS;
		if (AUDIT) {
			world.getServer().runCommand(at + String.format("tp Player0 ~%.1f ~%.1f ~ 90 8", far, eye));
		} else {
			world.getServer().runCommand(at + String.format("tp Player0 ~%.1f ~%.1f ~%.1f %d 12", right ? far * 0.6 : -far * 0.6, eye + 1, -far * 0.85,
					right ? 35 : -35));
		}
		for (Shot shot : shots) {
			world.getServer().runOnServer(server -> {
				T b = server.overworld().getEntities(type, e -> true).getFirst();
				if (b instanceof EmperorEntity e) {
					e.awake(shot.anim() != EmperorAnim.DORMANT);
				}
				b.warn(shot.warning());
				b.play(shot.anim());
			});
			context.waitTicks(shot.at());
			context.takeScreenshot((AUDIT ? "audit_" : "") + name + "_" + shot.anim().name().toLowerCase() + "_" + shot.at());
			world.getServer().runOnServer(server -> server.overworld().getEntities(type, e -> true).getFirst().warn(BossEntity.Warning.NONE));
			context.waitTicks(Math.max(5, shot.anim().ticks() - shot.at() + 5));
		}
		// (some can't be hurt, so they are removed rather than killed)
		world.getServer().runOnServer(server -> server.overworld().getEntities(type, e -> true).forEach(e -> e.discard()));
	}

	/**
	 * Hell's own monsters in rows (screenshots monsters_* and lower_*): first the upper circles' (the lovers, a glutton,
	 * a wrathful soul and a hoarder), then the lower circles' (a heretic, a centaur, a harpy, a mimic and a thief); each
	 * row at rest, then attacking, then with its specials (a glutton rising from the mud, a centaur's long look round,
	 * a mimic's breath...).
	 */
	private static void monsters(ClientGameTestContext context, TestSingleplayerContext world) {
		lineup(context, world, "monsters_", new String[]{"lovers", "glutton", "wrathful", "hoarder"}, "lovers", 4, HellEntities.WRATHFUL);
		lineup(context, world, "lower_", new String[]{"heretic", "centaur", "harpy", "mimic", "thief"}, "harpy", 4, HellEntities.THIEF);
		lineup(context, world, "devils_", new String[]{"malebranche", "malacoda", "malebranche"}, "", 4, HellEntities.MALACODA);
	}

	private static void lineup(ClientGameTestContext context, TestSingleplayerContext world, String prefix, String[] kinds, String flyer, int gap,
							   net.minecraft.world.entity.EntityType<?> last) {
		double first = -gap * (kinds.length - 1) / 2.0;
		for (int i = 0; i < kinds.length; i++) {
			world.getServer().runCommand(String.format(java.util.Locale.ROOT,
					"execute at Player0 run summon hellcraft:%s ~%.1f ~%s ~120 {NoAI:1b,Rotation:[180f,0f],Silent:1b,Tags:[\"lineup\"]}",
					kinds[i], first + i * gap, kinds[i].equals(flyer) ? "1.5" : "0"));
		}
		world.getConnection().waitForClientboundEntityUpdates(last);
		world.getServer().runCommand("execute at Player0 run tp Player0 ~ ~2.5 ~111 0 8");
		context.waitTicks(30);
		context.takeScreenshot(prefix + "lineup");
		world.getServer().runOnServer(server -> {
			for (var e : server.overworld().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
					server.getPlayerList().getPlayerByName("Player0").getBoundingBox().inflate(30), m -> m.entityTags().contains("lineup"))) {
				server.overworld().broadcastEntityEvent(e, net.minecraft.world.entity.EntityEvent.START_ATTACKING);
			}
		});
		context.waitTicks(8);
		context.takeScreenshot(prefix + "attack");
		context.waitTicks(30);
		world.getServer().runOnServer(server -> {
			for (var e : server.overworld().getEntitiesOfClass(SoulMonster.class,
					server.getPlayerList().getPlayerByName("Player0").getBoundingBox().inflate(30), m -> m.entityTags().contains("lineup"))) {
				e.playSpecial();
			}
		});
		context.waitTicks(16);
		context.takeScreenshot(prefix + "special");
		world.getServer().runCommand("kill @e[tag=lineup]");
		world.getServer().runCommand("execute at Player0 run tp Player0 ~ ~-2.5 ~-111 0 8");
		context.waitTicks(40);
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
