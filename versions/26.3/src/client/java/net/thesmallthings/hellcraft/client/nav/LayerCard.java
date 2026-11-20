package net.thesmallthings.hellcraft.client.nav;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.thesmallthings.hellcraft.client.dialogue.DialogueHud;
import net.thesmallthings.hellcraft.world.Circle;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * The card shown on entering a circle: its name, then one plain line on what the place does to you and the gear worth
 * having, as a headline in the message box (DialogueHud) like every other message. The circle is worked out here from
 * the shared geometry, so the server only says whether cards are on (circleTitles).
 */
public final class LayerCard {
	/** The same circle's card isn't shown again within this long, so walking along a border doesn't flash it. */
	private static final long AGAIN_AFTER = 60_000;
	private static final String TAG = "card:";

	/** The circle the player stands in (null outside the Inferno). */
	private static Circle current;
	private static final Map<Circle, Long> LAST_SHOWN = new EnumMap<>(Circle.class);

	/** Every client tick: shows the card when the player crosses into another circle. */
	public static void tick(Minecraft mc) {
		if (!ClientNav.inHell(mc)) {
			current = null;
			return;
		}
		Circle circle = InfernoGeometry.circleAt(mc.player.getX(), mc.player.getZ());
		if (circle == current) {
			return;
		}
		current = circle;
		long now = Util.getMillis();
		Long last = LAST_SHOWN.get(circle);
		if (ClientNav.cards() && (last == null || now - last > AGAIN_AFTER)) {
			show(circle, false);
		}
	}

	/** Puts this circle's card on screen (starting it over if it's up already). */
	public static void show(Circle circle) {
		show(circle, true);
	}

	private static void show(Circle circle, boolean again) {
		LAST_SHOWN.put(circle, Util.getMillis());
		DialogueHud.headline(circle.cardTitle(), accent(circle), Component.literal(circle.rule() + " ").withStyle(ChatFormatting.WHITE)
				.append(Component.literal("Bring: " + circle.gear()).withStyle(ChatFormatting.GRAY)), TAG + circle.name(), again);
	}

	/** The circle whose card is on screen, or null. */
	@Nullable
	public static Circle showing() {
		String tag = DialogueHud.showingTag();
		if (!tag.startsWith(TAG)) {
			return null;
		}
		try {
			return Circle.valueOf(tag.substring(TAG.length()));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	public static void reset() {
		current = null;
		LAST_SHOWN.clear();
	}

	/** Each circle's colour on the card, the compass and the Map of Hell. */
	public static int accent(Circle circle) {
		return switch (circle) {
			case DARK_WOOD -> 0x8FB878;
			case VESTIBULE -> 0xB8B098;
			case LIMBO -> 0xC4D0D4;
			case LUST -> 0xD86AAA;
			case GLUTTONY -> 0xB08A50;
			case GREED -> 0xF0C040;
			case WRATH -> 0x5AB0A0;
			case HERESY -> 0xF08040;
			case VIOLENCE -> 0xE03030;
			case FRAUD -> 0xA898C8;
			case TREACHERY -> 0xA8E0F8;
		};
	}
}
