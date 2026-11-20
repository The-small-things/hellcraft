package net.thesmallthings.hellcraft.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.thesmallthings.hellcraft.blood.Lessons;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.cutscene.CutsceneManager;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Every line of text Hellcraft puts on a player's screen goes through here, to one message box above the hotbar
 * (MessageHud in the client sources): one style and one place for all of it, a queue that puts what matters first and
 * drops repeats, so ten things happening at once never scroll past unread. Chat keeps what everyone on the server
 * should hear (a guardian slain, Cain's Mark) and longer records (a fight's spoils, a ghost's how-to).
 *
 * The kinds of line:
 * <ul>
 * <li>{@link Kind#SPEECH}: a character speaks (the guardians, Lucifer, the shades, the voice in the Spine).</li>
 * <li>{@link Kind#TIP}: a guide's one-time advice (Virgil, Beatrice). Tips are paced here: never mid-fight or in a
 * cutscene, and no closer together than {@code tipSeconds}. {@link #mayTip} says whether one may come now.</li>
 * <li>{@link Kind#NOTICE}: something that happened to you (a Rest remembered, a pilgrimage begun).</li>
 * <li>{@link Kind#HEADLINE}: a title and a line under it (a sphere entered, a trial passed, a prestige).</li>
 * <li>{@link Kind#STATUS}: a quick word that replaces the last one at once (a meter, a countdown, "Parried!"):
 * what the action bar was for.</li>
 * </ul>
 * A player without Hellcraft on their side (an admin's vanilla client) gets the line in chat, the action bar or
 * vanilla's title instead.
 */
public final class Dialogue {
	private Dialogue() {
	}

	/** What a line is, which sets its place in the queue (and nothing about its look: every kind shares one). */
	public enum Kind {
		SPEECH, TIP, NOTICE, HEADLINE, STATUS;

		public static Kind of(int ordinal) {
			Kind[] all = values();
			return ordinal >= 0 && ordinal < all.length ? all[ordinal] : NOTICE;
		}
	}

	/** The colour of a notice's edge when it names none. */
	private static final int NOTICE = 0xE0B040;

	/** When each player last got a tip (server tick). */
	private static final Map<UUID, Integer> LAST_TIP = new HashMap<>();

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(DialoguePayload.TYPE, DialoguePayload.CODEC);
	}

	public static void forget(ServerPlayer player) {
		LAST_TIP.remove(player.getUUID());
	}

	/** A character speaks: grey italics, or red capitals when shouted (a line in capitals or ending in "!"). */
	public static void say(ServerPlayer player, String speaker, ChatFormatting colour, String line) {
		boolean shout = line.equals(line.toUpperCase(Locale.ROOT)) || line.endsWith("!");
		say(player, speaker, colour, Component.literal(line).withStyle(shout ? new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD}
				: new ChatFormatting[]{ChatFormatting.GRAY, ChatFormatting.ITALIC}), shout);
	}

	/** A character speaks, styled by the caller. */
	public static void say(ServerPlayer player, String speaker, ChatFormatting colour, Component line, boolean shout) {
		send(player, Kind.SPEECH, speaker, rgb(colour), line, shout);
	}

	/**
	 * A guide's tip, sent now whatever the pacing (callers that can wait ask {@link #mayTip} first, as
	 * CircleHazards.hintOnce and the Lessons do). It counts toward the pacing all the same.
	 */
	public static void tip(ServerPlayer player, String speaker, ChatFormatting colour, Component line) {
		LAST_TIP.put(player.getUUID(), player.level().getServer().getTickCount());
		send(player, Kind.TIP, speaker, rgb(colour), line, false);
	}

	/**
	 * Whether a tip may come now: not mid-fight (a guardian's arena while it's awake, Lucifer's pit), not during a
	 * cutscene, and at least {@code tipSeconds} since the last one. A tip that may not come should wait and be asked
	 * for again later, not be spent.
	 */
	public static boolean mayTip(ServerPlayer player) {
		if (CutsceneManager.watching(player) || Lessons.inFight(player)) {
			return false;
		}
		Integer last = LAST_TIP.get(player.getUUID());
		int gap = 20 * Math.max(5, HellConfig.get().tipSeconds);
		return last == null || player.level().getServer().getTickCount() - last >= gap;
	}

	/** A notice from Hellcraft itself (no speaker): the box's edge takes the line's own colour. */
	public static void notice(ServerPlayer player, Component line) {
		TextColor colour = line.getStyle().getColor();
		send(player, Kind.NOTICE, "", colour == null ? NOTICE : colour.getValue(), line, false);
	}

	/** A quick word that replaces the last at once (what the action bar was for): a meter, a countdown, "Parried!". */
	public static void status(ServerPlayer player, Component line) {
		if (ServerPlayNetworking.canSend(player, DialoguePayload.TYPE)) {
			TextColor colour = line.getStyle().getColor();
			ServerPlayNetworking.send(player, new DialoguePayload(Kind.STATUS.ordinal(), "", colour == null ? NOTICE : colour.getValue(), line, false));
		} else {
			player.sendOverlayMessage(line);
		}
	}

	/** A title and one line under it, in the title's colour (vanilla's big title for a client without Hellcraft). */
	public static void headline(ServerPlayer player, String title, ChatFormatting colour, String subtitle) {
		headline(player, title, rgb(colour), subtitle);
	}

	/** A headline in any colour (0xRRGGBB). */
	public static void headline(ServerPlayer player, String title, int colour, String subtitle) {
		if (ServerPlayNetworking.canSend(player, DialoguePayload.TYPE)) {
			ServerPlayNetworking.send(player, new DialoguePayload(Kind.HEADLINE.ordinal(), title, colour,
					Component.literal(subtitle).withStyle(ChatFormatting.GRAY), false));
		} else {
			player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
			player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title).withStyle(Style.EMPTY.withColor(colour).withBold(true))));
			player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle).withStyle(ChatFormatting.GRAY)));
		}
	}

	private static void send(ServerPlayer player, Kind kind, String speaker, int colour, Component line, boolean shout) {
		if (ServerPlayNetworking.canSend(player, DialoguePayload.TYPE)) {
			ServerPlayNetworking.send(player, new DialoguePayload(kind.ordinal(), speaker, colour, line, shout));
		} else if (speaker.isEmpty()) {
			player.sendSystemMessage(line);
		} else {
			player.sendSystemMessage(Component.literal(speaker + ": ").withStyle(Style.EMPTY.withColor(colour).withBold(true)).append(line));
		}
	}

	private static int rgb(ChatFormatting colour) {
		TextColor named = Style.EMPTY.applyFormat(colour).getColor();
		return named == null ? NOTICE : named.getValue();
	}

	/** /hellcraft dialogue test: one line of each kind to whoever ran it. */
	public static String selfTest(ServerPlayer player) {
		if (player == null) {
			return "Dialogue OK (no player to show it to)";
		}
		tip(player, "Virgil", ChatFormatting.GOLD, Component.literal("Every message Hellcraft shows you appears here, one at a time.")
				.withStyle(ChatFormatting.GRAY));
		say(player, "Minos", ChatFormatting.DARK_PURPLE, "AND THIS IS A SHOUT!");
		notice(player, Component.literal("And this is a notice from Hellcraft itself.").withStyle(ChatFormatting.GOLD));
		headline(player, "A Headline", ChatFormatting.GOLD, "With one line under it.");
		status(player, Component.literal("A quick status word").withStyle(ChatFormatting.AQUA));
		return "Dialogue OK (five lines sent to " + player.getGameProfile().name() + ")";
	}
}
