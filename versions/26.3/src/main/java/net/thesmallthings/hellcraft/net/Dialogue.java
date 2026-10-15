package net.thesmallthings.hellcraft.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/**
 * The dialogue box: what characters say (the guardians, Lucifer, the shades, Virgil, the voice in the Spine) and
 * Hellcraft's own notices to one player are drawn in a box above the hotbar (DialogueHud in the client sources)
 * instead of filling the chat. Chat keeps what everyone on the server should hear (a guardian slain, Cain's Mark).
 * A player without Hellcraft on their side (an admin's vanilla client) gets the line in chat instead.
 */
public final class Dialogue {
	private Dialogue() {
	}

	/** The colour of a notice's edge when it names none. */
	private static final int NOTICE = 0xE0B040;

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(DialoguePayload.TYPE, DialoguePayload.CODEC);
	}

	/** A character speaks: grey italics, or red capitals when shouted (a line in capitals or ending in "!"). */
	public static void say(ServerPlayer player, String speaker, ChatFormatting colour, String line) {
		boolean shout = line.equals(line.toUpperCase(Locale.ROOT)) || line.endsWith("!");
		say(player, speaker, colour, Component.literal(line).withStyle(shout ? new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD}
				: new ChatFormatting[]{ChatFormatting.GRAY, ChatFormatting.ITALIC}), shout);
	}

	/** A character speaks, styled by the caller. */
	public static void say(ServerPlayer player, String speaker, ChatFormatting colour, Component line, boolean shout) {
		int rgb = colour.getColor() == null ? NOTICE : colour.getColor();
		if (ServerPlayNetworking.canSend(player, DialoguePayload.TYPE)) {
			ServerPlayNetworking.send(player, new DialoguePayload(speaker, rgb, line, shout));
		} else {
			player.sendSystemMessage(Component.literal(speaker.toUpperCase(Locale.ROOT) + ": ").withStyle(colour, ChatFormatting.BOLD).append(line));
		}
	}

	/** A notice from Hellcraft itself (no speaker): the box's edge takes the line's own colour. */
	public static void notice(ServerPlayer player, Component line) {
		if (ServerPlayNetworking.canSend(player, DialoguePayload.TYPE)) {
			TextColor colour = line.getStyle().getColor();
			ServerPlayNetworking.send(player, new DialoguePayload("", colour == null ? NOTICE : colour.getValue(), line, false));
		} else {
			player.sendSystemMessage(line);
		}
	}

	/** /hellcraft dialogue test: shows a line, a shout and a notice to whoever ran it. */
	public static String selfTest(ServerPlayer player) {
		if (player == null) {
			return "Dialogue OK (no player to show it to)";
		}
		say(player, "Virgil", ChatFormatting.GOLD, "Here is the dialogue box: what the souls of Hell say to you is written here, not in the chat.");
		say(player, "Minos", ChatFormatting.DARK_PURPLE, "AND THIS IS A SHOUT!");
		notice(player, Component.literal("And this is a notice from Hellcraft itself.").withStyle(ChatFormatting.GOLD));
		return "Dialogue OK (three lines sent to " + player.getGameProfile().name() + ")";
	}
}
