package net.thesmallthings.hellcraft.hazard.lucifer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.thesmallthings.hellcraft.net.Dialogue;
import net.thesmallthings.hellcraft.util.Feedback;

import java.util.List;

/**
 * Everything Lucifer says. He speaks like a fallen seraph who still thinks he is the most beautiful
 * thing in creation: archaic, contemptuous, and prone to SHOUTING when struck.
 */
final class LuciferDialogue {
	private LuciferDialogue() {
	}

	/** The Fallen Seraph comes down: beautiful, calm, and lying. */
	static final String[] INTRO = {
			"Peace. Lower thy blade, pilgrim.",
			"I am not thy enemy. I was the brightest of them all, and I am still the most beautiful thing in this place.",
			"Stay with me here, where it is quiet. There is nothing above but more judgement.",
	};
	/** The plain tip once he has landed (whatever he says, he's the boss). */
	static final String DESCENT_TIP = "This is Lucifer as he was before the Fall, and the fight has begun. Block his blade when it flashes white. "
			+ "Purple can't be blocked: jump it or step out. Red: get out of it.";

	// the Fallen Seraph's blows
	static final String[] SLASH = {"Hold still.", "Gently now.", "Thou art tired. Rest."};
	static final String[] SWEEP = {"Behold my wings.", "Six wings, and still the most beautiful.", "Bow."};
	static final String[] STARS = {"Fall with me!", "I was the Morning Star. Watch the stars come down.", "Look up."};
	static final String[] LUNGE = {"Here.", "Too slow.", "Behind thee? No. HERE."};

	static final String[] ENRAGE = {
			"ENOUGH!",
			"Thou darest wound ME? The Morning Star?",
			"Judas! Brutus! Cassius! RISE, AND FEED!",
	};

	/** The seraph's form breaks, and the pit's mountain opens six eyes. */
	static final String[] TRUE_FORM = {
			"...So be it.",
			"Thou wouldst see me as I am? LOOK DOWN.",
			"The mountain thou hast been walking toward... was ME.",
	};

	// the Emperor (true form): his hands, his three mouths, his wings and his tears
	static final String[] SLAM = {"BE CRUSHED!", "Down, worm!", "I cannot rise. But I can REACH."};
	static final String[] BITE = {"Judas has room for company!", "Into the mouth, TRAITOR!", "I shall chew thee for eternity."};
	static final String[] WINGBEAT = {"The wind of my wings froze all of Cocytus!", "BE STILL!", "Six wings, and I cannot fly. FEEL THEM!"};
	static final String[] WEEP = {"Six eyes weep... and still I cannot rise.", "Drown in my tears.", "Weep with me, heart-thief."};
	static final String[] EMPEROR_HALF = {"Thou bleedest the Emperor? Then BLEED WITH ME!", "All three faces see thee now."};

	static final String[] DEFEAT = {
			"Impossible... bested by a thing of dirt...",
			"The ice... takes me. It always takes me.",
			"Go, then. Climb. Thou shalt see the stars again. I never shall.",
	};

	static final String FAIL = "Crawl back to thy circle, worm.";

	static final String[] PLAYER_DEATH = {
			"Another heart for the ice.",
			"Is that all thy borrowed blood could buy?",
			"Rest. The ice is patient.",
	};

	static final String[] HURT = {"Hah! AGAIN!", "A scratch!", "Thou bleedest me? Good. GOOD!", "Insolent WORM!"};
	static final String[] PARRIED = {"Thou... blocked ME?", "Impudence!", "A shield. How very mortal."};

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

	/** Lucifer speaks in the dialogue box, with a low voice cue so lines aren't missed. */
	static void say(List<ServerPlayer> audience, String line) {
		boolean shout = line.equals(line.toUpperCase()) || line.endsWith("!");
		Component text = Component.literal(line).withStyle(shout ? new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD}
				: new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.ITALIC});
		for (ServerPlayer p : audience) {
			Dialogue.say(p, "Lucifer", ChatFormatting.DARK_RED, text, shout);
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
