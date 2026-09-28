package net.thesmallthings.hellcraft.blood;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.thesmallthings.hellcraft.config.HellConfig;

import java.util.Locale;

/**
 * Vanilla scoreboards, so every client shows them: hearts next to each name in the player list, and
 * the "Hall of the Damned" sidebar of everyone who has cast Lucifer down. Driven through the
 * scoreboard command, whose syntax is part of the game's stable surface.
 */
public final class Scoreboards {
	private Scoreboards() {
	}

	static final String HEARTS = "hellcraft_hearts";
	static final String HALL = "hellcraft_hall";

	/** Creates (or removes) the objectives to match the config, and fills them from the saved souls. */
	public static void setUp(MinecraftServer server) {
		HellConfig config = HellConfig.get();
		run(server, "scoreboard objectives remove " + HEARTS);
		run(server, "scoreboard objectives remove " + HALL);
		if (config.tabListHearts) {
			run(server, "scoreboard objectives add " + HEARTS + " dummy \"Hearts\"");
			run(server, "scoreboard objectives setdisplay list " + HEARTS);
		}
		if (config.sidebarHall) {
			run(server, "scoreboard objectives add " + HALL + " dummy \"Hall of the Damned\"");
			run(server, "scoreboard objectives modify " + HALL + " displayname {text:\"☠ Hall of the Damned ☠\",color:\"dark_red\",bold:true}");
		}
		for (HellState.Soul soul : HellState.get(server).souls().values()) {
			update(server, soul);
		}
	}

	/** Refreshes one soul's scores (called whenever their hearts change). */
	public static void update(MinecraftServer server, HellState.Soul soul) {
		if (soul.name.isEmpty() || !soul.name.matches("[A-Za-z0-9_]{1,16}")) {
			return;
		}
		HellConfig config = HellConfig.get();
		if (config.tabListHearts) {
			run(server, String.format(Locale.ROOT, "scoreboard players set %s %s %d", soul.name, HEARTS, soul.ghost ? 0 : soul.hearts));
		}
		if (config.sidebarHall && soul.luciferKills > 0) {
			run(server, String.format(Locale.ROOT, "scoreboard players set %s %s %d", soul.name, HALL, soul.luciferKills));
			// only shown once there is someone to honour (an empty sidebar would just be a box)
			run(server, "scoreboard objectives setdisplay sidebar " + HALL);
		}
	}

	private static void run(MinecraftServer server, String command) {
		CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
		server.getCommands().performPrefixedCommand(source, command);
	}
}
