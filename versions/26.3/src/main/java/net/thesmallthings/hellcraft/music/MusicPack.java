package net.thesmallthings.hellcraft.music;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.config.HellConfig;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Optional boss music. The server operator drops Ogg Vorbis files into {@code config/hellcraft/music/};
 * Hellcraft turns them into a resource pack, serves it from a tiny built-in web server and offers it
 * to every player on join. Players who accept hear the custom tracks during the Lucifer fight; everyone
 * else hears vanilla music discs instead.
 */
public final class MusicPack {
	private MusicPack() {
	}

	public enum Track {
		DUEL("duel"),
		ENRAGED("enraged"),
		TRUE_FORM("true_form");

		private final String file;

		Track(String file) {
			this.file = file;
		}

		public Identifier soundId() {
			return HellcraftMod.id("lucifer." + file);
		}
	}

	private static final UUID PACK_ID = UUID.nameUUIDFromBytes("hellcraft:boss-music".getBytes(StandardCharsets.UTF_8));
	private static final String PATH = "/hellcraft-music.zip";
	private static final Set<UUID> LOADED = ConcurrentHashMap.newKeySet();
	private static final Map<Track, Double> LENGTHS = new EnumMap<>(Track.class);

	@Nullable
	private static volatile byte[] zip;
	@Nullable
	private static String sha1;
	@Nullable
	private static String url;
	@Nullable
	private static ServerSocket socket;
	@Nullable
	private static ExecutorService pool;

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(MusicPack::build);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> stopHttp());
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> offer(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LOADED.remove(handler.getPlayer().getUUID()));
	}

	public static Path directory() {
		return FabricLoader.getInstance().getConfigDir().resolve("hellcraft").resolve("music");
	}

	/** True if this player accepted and loaded the pack. */
	public static boolean hasPack(ServerPlayer player) {
		return zip != null && LOADED.contains(player.getUUID());
	}

	public static Holder<SoundEvent> sound(Track track) {
		return Holder.direct(SoundEvent.createVariableRangeEvent(track.soundId()));
	}

	/** Length of the custom track in seconds (0 if unknown). */
	public static double length(Track track) {
		return LENGTHS.getOrDefault(track, 0.0);
	}

	/** Called (on the server thread) when a client answers a resource pack offer. */
	public static void onResponse(ServerPlayer player, UUID packId, ServerboundResourcePackPacket.Action action) {
		if (!PACK_ID.equals(packId)) {
			return;
		}
		switch (action) {
			case SUCCESSFULLY_LOADED -> LOADED.add(player.getUUID());
			case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> LOADED.remove(player.getUUID());
			default -> {
			}
		}
	}

	private static void offer(ServerPlayer player) {
		if (zip == null || url == null || sha1 == null) {
			return;
		}
		player.connection.send(new ClientboundResourcePackPushPacket(PACK_ID, url, sha1, false,
				Optional.of(Component.literal("Hellcraft: Lucifer's boss music (optional)").withStyle(ChatFormatting.DARK_RED))));
	}

	// ------------------------------------------------------------------ building

	private static void build(MinecraftServer server) {
		LENGTHS.clear();
		zip = null;
		Path dir = directory();
		Map<Track, byte[]> audio = new EnumMap<>(Track.class);
		try {
			Files.createDirectories(dir);
			for (Track track : Track.values()) {
				Path file = dir.resolve(track.file + ".ogg");
				if (Files.isRegularFile(file)) {
					audio.put(track, Files.readAllBytes(file));
				}
			}
		} catch (IOException e) {
			HellcraftMod.LOGGER.warn("Could not read boss music from {}", dir, e);
			return;
		}
		if (audio.isEmpty()) {
			HellcraftMod.LOGGER.info("No custom boss music (drop duel.ogg / enraged.ogg / true_form.ogg into {}); using vanilla music discs.", dir);
			return;
		}
		Track firstPresent = audio.keySet().iterator().next();
		StringBuilder sounds = new StringBuilder("{\n");
		Track[] all = Track.values();
		for (int i = 0; i < all.length; i++) {
			Track track = all[i];
			Track source = audio.containsKey(track) ? track : firstPresent;
			double seconds = OggLength.seconds(audio.get(source));
			LENGTHS.put(track, Math.max(0.0, seconds));
			sounds.append("  \"lucifer.").append(track.file).append("\": {\"sounds\": [{\"name\": \"hellcraft:music/")
					.append(source.file).append("\", \"stream\": true}]}").append(i < all.length - 1 ? ",\n" : "\n");
		}
		sounds.append("}\n");
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (ZipOutputStream out = new ZipOutputStream(bytes)) {
				put(out, "pack.mcmeta", "{\"pack\": {\"min_format\": [97, 0], \"max_format\": [99, 0], \"description\": \"Hellcraft: Lucifer's boss music\"}}\n"
						.getBytes(StandardCharsets.UTF_8));
				put(out, "assets/hellcraft/sounds.json", sounds.toString().getBytes(StandardCharsets.UTF_8));
				for (Map.Entry<Track, byte[]> e : audio.entrySet()) {
					put(out, "assets/hellcraft/sounds/music/" + e.getKey().file + ".ogg", e.getValue());
				}
			}
			byte[] built = bytes.toByteArray();
			sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(built));
			zip = built;
		} catch (Exception e) {
			HellcraftMod.LOGGER.warn("Could not build the boss music pack", e);
			return;
		}

		HellConfig config = HellConfig.get();
		if (!config.musicPackUrl.isBlank()) {
			url = config.musicPackUrl.trim();
		} else {
			String host = config.musicPackHost.isBlank() && !server.isDedicatedServer() ? "127.0.0.1" : config.musicPackHost.trim();
			startHttp(config.musicPackPort);
			if (host.isEmpty()) {
				url = null;
				HellcraftMod.LOGGER.warn("Boss music is ready, but players can't be sent it until you set \"musicPackHost\" in config/hellcraft.json "
						+ "to this server's public address (and open port {}).", config.musicPackPort);
			} else {
				url = "http://" + host + ":" + config.musicPackPort + PATH;
			}
		}
		HellcraftMod.LOGGER.info("Music pack ready: {} track(s), {} ({})", audio.size(), LENGTHS, url == null ? "not offered" : url);
	}

	private static void put(ZipOutputStream out, String name, byte[] data) throws IOException {
		ZipEntry entry = new ZipEntry(name);
		entry.setTime(0L); // stable bytes, stable SHA-1
		out.putNextEntry(entry);
		out.write(data);
		out.closeEntry();
	}

	// ------------------------------------------------------------------ web server

	/** A deliberately tiny HTTP server: it only ever answers GET/HEAD for the one pack file. */
	private static void startHttp(int port) {
		stopHttp();
		try {
			ServerSocket server = new ServerSocket();
			server.setReuseAddress(true);
			server.bind(new InetSocketAddress(port));
			socket = server;
		} catch (IOException e) {
			HellcraftMod.LOGGER.warn("Could not start the boss music web server on port {}", port, e);
			return;
		}
		pool = Executors.newCachedThreadPool(r -> {
			Thread t = new Thread(r, "Hellcraft music pack");
			t.setDaemon(true);
			return t;
		});
		ServerSocket server = socket;
		pool.execute(() -> {
			while (!server.isClosed()) {
				try {
					Socket client = server.accept();
					ExecutorService p = pool;
					if (p != null) {
						p.execute(() -> serve(client));
					}
				} catch (IOException e) {
					if (!server.isClosed()) {
						HellcraftMod.LOGGER.debug("Music pack server accept failed", e);
					}
				}
			}
		});
		HellcraftMod.LOGGER.info("Serving the boss music pack on port {}", port);
	}

	private static void serve(Socket client) {
		try (client; InputStream in = client.getInputStream(); OutputStream out = client.getOutputStream()) {
			client.setSoTimeout(10_000);
			StringBuilder head = new StringBuilder();
			int b;
			while ((b = in.read()) != -1 && head.length() < 8192) {
				head.append((char) b);
				if (head.length() >= 4 && head.substring(head.length() - 4).equals("\r\n\r\n")) {
					break;
				}
			}
			String[] requestLine = head.toString().split("\r\n", 2)[0].split(" ");
			byte[] body = zip;
			boolean ok = requestLine.length >= 2 && body != null
					&& (requestLine[0].equals("GET") || requestLine[0].equals("HEAD"))
					&& requestLine[1].split("\\?", 2)[0].equals(PATH);
			if (!ok) {
				out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
				return;
			}
			out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/zip\r\nContent-Length: " + body.length
					+ "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
			if (requestLine[0].equals("GET")) {
				out.write(body);
			}
			out.flush();
		} catch (IOException e) {
			HellcraftMod.LOGGER.debug("Music pack download failed", e);
		}
	}

	private static void stopHttp() {
		try {
			if (socket != null) {
				socket.close();
			}
		} catch (IOException ignored) {
		}
		socket = null;
		if (pool != null) {
			pool.shutdownNow();
			pool = null;
		}
	}
}
