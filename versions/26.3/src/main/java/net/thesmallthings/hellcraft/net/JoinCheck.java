package net.thesmallthings.hellcraft.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ConfigurationTask;
import net.thesmallthings.hellcraft.HellcraftMod;

import java.util.function.Consumer;

/**
 * Players need Hellcraft on their own computer, at the same version as the server. While a player joins,
 * the server sends its version and waits for the client's; a client without the mod, or with another
 * version, is turned away with a message saying what to install.
 */
public final class JoinCheck {
	private JoinCheck() {
	}

	public static final String DOWNLOADS = "github.com/The-small-things/hellcraft/releases";

	private static final ConfigurationTask.Type TASK = new ConfigurationTask.Type(HellcraftMod.MOD_ID + ":version_check");

	public static void register() {
		PayloadTypeRegistry.clientboundConfiguration().register(VersionPayload.TYPE, VersionPayload.CODEC);
		PayloadTypeRegistry.serverboundConfiguration().register(VersionPayload.TYPE, VersionPayload.CODEC);

		ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
			if (ServerConfigurationNetworking.canSend(handler, VersionPayload.TYPE)) {
				handler.addTask(new Task());
			} else {
				HellcraftMod.LOGGER.info("Turned away a player without Hellcraft installed");
				handler.disconnect(missing());
			}
		});
		ServerConfigurationNetworking.registerGlobalReceiver(VersionPayload.TYPE, (payload, context) -> {
			if (payload.version().equals(HellcraftMod.VERSION)) {
				context.packetListener().completeTask(TASK);
			} else {
				HellcraftMod.LOGGER.info("Turned away a player with Hellcraft {} (this server runs {})", payload.version(), HellcraftMod.VERSION);
				context.packetListener().disconnect(mismatch(payload.version()));
			}
		});
	}

	static Component missing() {
		return Component.literal("This server runs Hellcraft, and you need it installed too.\n\n"
				+ "Get the Hellcraft " + HellcraftMod.VERSION + " modpack (the .mrpack file) from\n" + DOWNLOADS + "\n"
				+ "open it in the Modrinth App or Prism Launcher, and join again from there.");
	}

	static Component mismatch(String theirs) {
		return Component.literal("This server runs Hellcraft " + HellcraftMod.VERSION + ", but you have " + theirs + ".\n\n"
				+ "Get Hellcraft " + HellcraftMod.VERSION + " from\n" + DOWNLOADS + "\nand join again.");
	}

	private static final class Task implements ConfigurationTask {
		@Override
		public void start(Consumer<Packet<?>> send) {
			send.accept(ServerConfigurationNetworking.createClientboundPacket(new VersionPayload(HellcraftMod.VERSION)));
		}

		@Override
		public Type type() {
			return TASK;
		}
	}
}
