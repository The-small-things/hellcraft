package net.thesmallthings.hellcraft.hazard.lucifer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.thesmallthings.hellcraft.util.Feedback;

import java.util.List;

/**
 * Everything Lucifer says. He speaks like a fallen seraph who still thinks he is the most beautiful
 * thing in creation: archaic, contemptuous, and prone to SHOUTING when struck.
 */
final class LuciferDialogue {
	private LuciferDialogue() {
	}

	static final String[] INTRO = {
			"Another soul crawls to the bottom of the world.",
			"Thou hast waded through fire, blood and ice... only to kneel before ME?",
			"I was the brightest of them all. I will not be judged by dirt and borrowed blood.",
			"Come then, heart-thief. Let the ice drink thy heart.",
	};

	static final String[] SLASH = {"Behind thee.", "Too slow!", "Here."};
	static final String[] FANGS = {"BE JUDGED!", "KNEEL!", "The ice remembers every traitor."};
	static final String[] WINGS = {"FREEZE!", "Feel the wind of Cocytus!", "My wings froze this lake. Thou art nothing."};
	static final String[] HELLFIRE = {"BURN!", "Rain, fire!", "Dance for me."};

	static final String[] ENRAGE = {
			"ENOUGH!",
			"Thou darest wound ME? The Morning Star?",
			"Judas! Brutus! Cassius! RISE, AND FEED!",
	};

	static final String[] TRUE_FORM = {
			"...So be it.",
			"Behold the face that God cast down!",
			"Frozen here since the Fall... but my faces still bite.",
	};

	// the Emperor (true form): one set of lines per face, his wings and his mouths
	static final String[] HATRED = {"HATE WITH ME!", "Burn in the fire I carry!", "Feel how I HATE Him!"};
	static final String[] IMPOTENCE = {"Six eyes weep... and still I cannot rise.", "Drown in my tears.", "Weep with me, heart-thief."};
	static final String[] IGNORANCE = {"See nothing. Know nothing. As I do.", "Into the dark with thee.", "Where is thy God now? I cannot see Him either."};
	static final String[] WINGBEAT = {"The wind of my wings froze all of Cocytus!", "BE STILL!", "Six wings, and I cannot fly. FEEL THEM!"};
	static final String[] MOUTHS = {"Judas has room for company!", "Into the mouth, TRAITOR!", "I shall chew thee for eternity."};
	static final String[] SPIT = {"Bitter. Like all the rest.", "Thou tastest of stolen blood.", "Pah! Next!"};
	static final String[] EMPEROR_CRACK = {"The ice... CRACKS!", "Thou bleedest the Emperor? Then BLEED WITH ME!"};
	static final String[] EMPEROR_RAGE = {"JUDAS! BRUTUS! CASSIUS! Out of my mouths, and FIGHT FOR ME!", "All three faces see thee now."};

	static final String[] DEFEAT = {
			"Impossible... bested by a thing of dirt...",
			"Mark me, heart-thief. Hell is FULL, and every soul in it is MINE.",
			"Go. Climb. I will be waiting at the bottom of the world.",
	};

	static final String FAIL = "Crawl back to thy circle, worm.";

	static final String[] PLAYER_DEATH = {
			"Another heart for the ice.",
			"Is that all thy borrowed blood could buy?",
			"Rest. The ice is patient.",
	};

	static final String[] HURT = {"Hah! AGAIN!", "A scratch!", "Thou bleedest me? Good. GOOD!", "Insolent WORM!"};

	/** Lucifer remembers everyone who has beaten him before. */
	static String veteranLine(List<String> veterans) {
		if (veterans.size() == 1) {
			return "Thou again, " + veterans.get(0) + "? Then I shall not hold back.";
		}
		return String.join(", ", veterans) + "... so many who dared return. The ice will be GENEROUS.";
	}

	static String pick(RandomSource random, String[] lines) {
		return lines[random.nextInt(lines.length)];
	}

	/** Lucifer speaks in chat (only there, so nothing overlaps), with a low voice cue so lines aren't missed. */
	static void say(List<ServerPlayer> audience, String line) {
		boolean shout = line.equals(line.toUpperCase()) || line.endsWith("!");
		MutableComponent text = Component.literal("LUCIFER: ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
				.append(Component.literal(line).withStyle(shout ? new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD}
						: new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.ITALIC}));
		for (ServerPlayer p : audience) {
			p.sendSystemMessage(text);
			if (shout) {
				Feedback.sound(p, SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 0.35f, 0.5f);
			} else {
				Feedback.sound(p, SoundEvents.ENDERMAN_AMBIENT, SoundSource.HOSTILE, 0.5f, 0.5f);
			}
		}
	}

	/** The big boss name card. Keep both lines short: titles are drawn huge and clip on small windows. */
	static void nameCard(List<ServerPlayer> audience, String title, String subtitle, ChatFormatting color) {
		for (ServerPlayer p : audience) {
			p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 60, 20));
			p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title).withStyle(color, ChatFormatting.BOLD)));
			p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
		}
	}

	static List<ServerPlayer> audience(ServerLevel level, double radius) {
		return level.players().stream()
				.filter(p -> p.getX() * p.getX() + p.getZ() * p.getZ() < radius * radius)
				.map(p -> (ServerPlayer) p)
				.toList();
	}
}
