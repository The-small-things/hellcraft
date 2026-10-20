package net.thesmallthings.hellcraft.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.thesmallthings.hellcraft.blood.HellItems;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.blood.Sins;
import net.thesmallthings.hellcraft.client.dialogue.DialogueHud;
import net.thesmallthings.hellcraft.client.nav.ClientNav;
import net.thesmallthings.hellcraft.client.nav.JournalScreen;
import net.thesmallthings.hellcraft.entity.HellEntities;
import net.thesmallthings.hellcraft.entity.boss.MinosEntity;
import net.thesmallthings.hellcraft.entity.boss.gen.MinosAnim;
import net.thesmallthings.hellcraft.hazard.Champions;
import net.thesmallthings.hellcraft.hazard.guardian.Guardian;
import net.thesmallthings.hellcraft.hazard.guardian.GuardianManager;

import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Minos's judgement and the champions of the circles, in an Inferno world on a dedicated server:
 * <ul>
 *     <li>a soul that has enchanted 140 items walks into Minos's lair: he rises, wraps his tail six times
 *     (minos_judgement_tail) and names it a Heretic (minos_judgement, the dialogue box); the title shows after its name
 *     in the tab list (minos_judgement_tab) and on the Journal's page (minos_judgement_journal);</li>
 *     <li>a champion of Gluttony brought out in front of it, glowing, named, with its boss bar (champion_near), and
 *     slain: its spoils go to whoever hurt it most, and it splits into two shards (champion_slain).</li>
 * </ul>
 */
public class JudgementTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		Properties properties = new Properties();
		properties.setProperty("view-distance", "8");
		properties.setProperty("simulation-distance", "4");
		try (TestDedicatedServerContext server = context.worldBuilder().adjustSettings(MapTest::inferno).createServer(properties);
			 TestDedicatedServerConnection connection = server.connect()) {
			connection.waitForChunksRender();
			selfTests(server);
			judgement(context, server, connection);
			champion(context, server, connection);
		}
	}

	private static void selfTests(TestDedicatedServerContext server) {
		String sins = server.computeOnServer(s -> Sins.selfTest());
		if (!sins.contains("Judgement test OK")) {
			throw new AssertionError(sins);
		}
		server.runCommand("forceload add 0 0");
		String champions = server.computeOnServer(s -> Champions.selfTest(s.overworld(), new net.minecraft.world.phys.Vec3(8.5, 120, 8.5)));
		if (!champions.contains("Champion test OK")) {
			throw new AssertionError(champions);
		}
		server.runCommand("forceload remove 0 0");
	}

	private static void judgement(ClientGameTestContext context, TestDedicatedServerContext server, TestDedicatedServerConnection connection) {
		int x = Guardian.MINOS.lairColumn().getX();
		int z = Guardian.MINOS.lairColumn().getZ();
		server.runCommand(String.format(Locale.ROOT, "forceload add %d %d", x, z));
		int ground = server.computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
		// a heretic: 140 items enchanted, and an ordinary pilgrim's share of everything else
		server.runCommand("gamemode survival Player0");
		server.runOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayerByName("Player0");
			p.getAbilities().invulnerable = true;
			p.onUpdateAbilities();
			p.getStats().setValue(p, Stats.CUSTOM.get(Stats.ENCHANT_ITEM), 140);
			p.getStats().setValue(p, Stats.CUSTOM.get(Stats.MOB_KILLS), 80);
			p.getStats().setValue(p, Stats.CUSTOM.get(Stats.OPEN_CHEST), 120);
		});
		// walk in: he wakes, rises (his cutscene), then judges
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %d %d %d facing %d %d %d", x - 4, ground + 1, z - 11, x, ground + 4, z));
		context.waitTicks(10);
		connection.waitForChunksRender();
		context.waitFor(client -> client.level.getEntitiesOfClass(MinosEntity.class, client.player.getBoundingBox().inflate(40))
				.stream().anyMatch(m -> m.animation() == MinosAnim.JUDGE.ordinal()), 900);
		context.waitTicks(30);
		context.takeScreenshot("minos_judgement_tail");
		boolean judged = waitForVerdict(context, server, 600);
		if (!judged) {
			throw new AssertionError("Minos never pronounced his verdict");
		}
		// the dialogue box catches up (it waits out his cutscene): his verdict, then the plain line
		context.waitFor(client -> DialogueHud.showing().contains("Heresy"), 900);
		context.waitTicks(30);
		context.takeScreenshot("minos_judgement");
		context.waitFor(client -> DialogueHud.showing().contains("Your worst sin"), 600);
		context.waitTicks(60);
		context.takeScreenshot("minos_judgement_plain");
		String verdict = server.computeOnServer(s -> {
			HellState.Soul soul = HellState.get(s).existing(s.getPlayerList().getPlayerByName("Player0").getUUID());
			return soul == null ? "" : soul.judged + " / " + soul.judgedFact;
		});
		if (!verdict.startsWith("heresy")) {
			throw new AssertionError("Minos judged the enchanter " + verdict + ", not heresy");
		}
		context.waitTicks(20);
		Component tab = context.computeOnClient(client -> {
			PlayerInfo info = client.getConnection().getPlayerInfo("Player0");
			return info == null ? null : info.getTabListDisplayName();
		});
		if (tab == null || !tab.getString().contains("Heretic")) {
			throw new AssertionError("the tab list doesn't show the verdict: " + tab);
		}
		context.getInput().holdKey(options -> options.keyPlayerList);
		context.waitTicks(5);
		context.takeScreenshot("minos_judgement_tab");
		context.getInput().releaseKey(options -> options.keyPlayerList);
		// the Journal's page (after the Codex entries, and the Guide's first page)
		int page = context.computeOnClient(client -> {
			int codex = ClientNav.codex().size();
			for (int i = 0; i < ClientNav.pages().size(); i++) {
				if (ClientNav.pages().get(i).title().equals("MINOS'S JUDGEMENT")) {
					return 1 + codex + i;
				}
			}
			return -1;
		});
		if (page < 0) {
			throw new AssertionError("the Journal has no page for Minos's judgement");
		}
		context.setScreen(() -> JournalScreen.at(page));
		context.waitTicks(5);
		context.takeScreenshot("minos_judgement_journal");
		context.setScreen(() -> null);
		server.computeOnServer(s -> GuardianManager.slay(Guardian.MINOS));
		context.waitTicks(40);
		server.runCommand(String.format(Locale.ROOT, "forceload remove %d %d", x, z));
	}

	private static boolean waitForVerdict(ClientGameTestContext context, TestDedicatedServerContext server, int ticks) {
		for (int t = 0; t < ticks; t += 5) {
			boolean done = server.computeOnServer(s -> {
				HellState.Soul soul = HellState.get(s).existing(s.getPlayerList().getPlayerByName("Player0").getUUID());
				return soul != null && soul.cutscenes.contains("minos_judgement");
			});
			if (done) {
				return true;
			}
			context.waitTicks(5);
		}
		return false;
	}

	private static void champion(ClientGameTestContext context, TestDedicatedServerContext server, TestDedicatedServerConnection connection) {
		// out of the lair, on open ground in Lust
		int x = Guardian.MINOS.lairColumn().getX() + 60;
		int z = Guardian.MINOS.lairColumn().getZ();
		server.runCommand(String.format(Locale.ROOT, "forceload add %d %d", x, z));
		int ground = server.computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
		server.runCommand(String.format(Locale.ROOT, "tp Player0 %d %d %d -90 10", x, ground, z));
		context.waitTicks(10);
		connection.waitForChunksRender();
		server.runCommand("execute as Player0 at @s run hellcraft champion gluttony vampiric splitting");
		context.waitTicks(60);
		boolean shown = context.computeOnClient(client -> client.level.getEntitiesOfClass(LivingEntity.class, client.player.getBoundingBox().inflate(24),
				e -> e.getType() == HellEntities.GLUTTON && e.isCurrentlyGlowing() && e.getCustomName() != null
						&& e.getCustomName().getString().endsWith("Vampiric and Splitting")).size() == 1);
		if (!shown) {
			throw new AssertionError("the champion of Gluttony isn't there, glowing and named");
		}
		server.runCommand("execute as Player0 at @s anchored eyes run tp @s ~ ~ ~ facing entity @e[tag=hellcraft_champion,limit=1] feet");
		context.waitTicks(10);
		context.takeScreenshot("champion_near");
		int before = fragments(server);
		server.computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayerByName("Player0");
			List<LivingEntity> found = s.overworld().getEntitiesOfClass(LivingEntity.class, new AABB(p.blockPosition()).inflate(32),
					e -> e.entityTags().contains(Champions.TAG));
			for (LivingEntity e : found) {
				e.hurtServer(s.overworld(), s.overworld().damageSources().playerAttack(p), 10000f);
			}
			return found.size();
		});
		context.waitTicks(30);
		context.takeScreenshot("champion_slain");
		int gained = fragments(server) - before;
		if (gained < 6) {
			throw new AssertionError("slaying a champion of Gluttony paid " + gained + " Blood Fragments, not 6 or more");
		}
		int shards = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(Entity.class, new AABB(x - 32, -64, z - 32, x + 32, 320, z + 32),
				e -> e.entityTags().contains("hellcraft_champion_echo") && e.isAlive()).size());
		if (shards != 2) {
			throw new AssertionError("a Splitting champion left " + shards + " shards, not 2");
		}
		server.runCommand("kill @e[tag=hellcraft_champion_echo]");
		server.runCommand(String.format(Locale.ROOT, "forceload remove %d %d", x, z));
	}

	private static int fragments(TestDedicatedServerContext server) {
		return server.computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayerByName("Player0");
			int n = 0;
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				if (p.getInventory().getItem(i).is(HellItems.BLOOD_FRAGMENT)) {
					n += p.getInventory().getItem(i).getCount();
				}
			}
			return n;
		});
	}
}
