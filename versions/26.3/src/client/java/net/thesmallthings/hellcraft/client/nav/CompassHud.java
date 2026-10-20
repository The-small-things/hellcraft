package net.thesmallthings.hellcraft.client.nav;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.client.mixin.BossOverlayAccessor;
import net.thesmallthings.hellcraft.net.WaymarksPayload;
import net.thesmallthings.hellcraft.world.Circle;
import net.thesmallthings.hellcraft.world.ForgeGeometry;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.Navigation;
import net.thesmallthings.hellcraft.world.ParadisoGeometry;
import net.thesmallthings.hellcraft.world.ShrineSites;

/**
 * The compass strip at the top of the screen, in the Inferno: which way you face, a red arrow to the nearest way
 * down into the next circle and a gold diamond for the nearest Virgil's Rest, with how far each is. Under it, a
 * thin depth bar that fills toward the pit, where you are and how far each marker is (those lines give way to a
 * circle's card while it shows), and the waymark you follow, if any (ClientWaymarks). It steps aside while boss bars show.
 */
public final class CompassHud implements HudElement {
	public static final Identifier ID = HellcraftMod.id("compass");

	/** Half the strip's width: 90 degrees each side of where you face. */
	private static final int HALF = 96;
	/** The strip's top: the markers hang just above it. */
	private static final int TOP = 8;
	private static final int HEIGHT = 11;

	static final int WAY_DOWN = 0xFFE03030;
	static final int REST_FOUND = 0xFFF0C040;
	static final int REST_NEW = 0xFF9AA8B8;

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		boolean forge = ClientNav.inForge(mc);
		boolean paradiso = ClientNav.inParadiso(mc);
		if (!ClientNav.inHell(mc) && !forge && !paradiso || !((BossOverlayAccessor) mc.gui.hud.getBossOverlay()).hellcraft$events().isEmpty()) {
			return;
		}
		LocalPlayer player = mc.player;
		float partial = delta.getGameTimeDeltaPartialTick(true);
		Vec3 pos = player.getPosition(partial);
		float yaw = player.getViewYRot(partial);
		Font font = mc.font;
		int cx = g.guiWidth() / 2;
		int left = cx - HALF - 3;
		int right = cx + HALF + 3;

		g.fill(left, TOP, right, TOP + HEIGHT, ARGB.black(120));
		g.enableScissor(cx - HALF, TOP, cx + HALF + 1, TOP + HEIGHT);
		for (int bearing = 0; bearing < 360; bearing += 15) {
			float rel = Mth.wrapDegrees(bearing - yaw);
			if (Math.abs(rel) > 95) {
				continue;
			}
			int sx = cx + Math.round(rel * HALF / 90f);
			if (bearing % 90 == 0) {
				// Minecraft's yaw: 0 faces south (+Z), 90 west, 180 north, 270 east
				String letter = switch (bearing) {
					case 0 -> "S";
					case 90 -> "W";
					case 180 -> "N";
					default -> "E";
				};
				g.centeredText(font, letter, sx + 1, TOP + 2, bearing == 180 ? 0xFFFF6060 : 0xFFFFFFFF);
			} else if (bearing % 45 == 0) {
				g.fill(sx, TOP + 3, sx + 1, TOP + HEIGHT - 3, 0xFFB8B8B8);
			} else {
				g.fill(sx, TOP + 4, sx + 1, TOP + HEIGHT - 4, 0xFF686868);
			}
		}
		g.disableScissor();
		g.fill(cx, TOP + HEIGHT - 2, cx + 1, TOP + HEIGHT, 0xFFFFFFFF);

		if (forge) {
			forge(g, font, cx, pos, yaw);
			return;
		}
		if (paradiso) {
			paradiso(g, font, cx, pos, yaw);
			return;
		}
		// the nearest Rest and the way down, above the strip; pinned faintly to its ends when they're behind you
		ShrineSites.Site rest = ShrineSites.nearest(pos.x, pos.z);
		boolean found = rest != null && ClientNav.found(rest.id());
		if (rest != null) {
			float rel = relative(pos, rest.x() + 0.5, rest.z() + 0.5, yaw);
			diamond(g, stripX(cx, rel), TOP - 3, ARGB.multiplyAlpha(found ? REST_FOUND : REST_NEW, Math.abs(rel) > 90 ? 0.5f : 1));
		}
		Navigation.Target down = Navigation.wayDown(pos.x, pos.z);
		float downRel = relative(pos, down.x(), down.z(), yaw);
		arrow(g, stripX(cx, downRel), TOP - 5, ARGB.multiplyAlpha(WAY_DOWN, Math.abs(downRel) > 90 ? 0.5f : 1));
		// the waymark you follow (Map of Hell: click one, then Follow)
		WaymarksPayload.Entry mark = ClientWaymarks.followed();
		if (mark != null) {
			float rel = relative(pos, mark.x() + 0.5, mark.z() + 0.5, yaw);
			MapOfHellScreen.pin(g, stripX(cx, rel), TOP - 1, ARGB.multiplyAlpha(ClientWaymarks.colour(mark), Math.abs(rel) > 90 ? 0.5f : 1), mark.shared());
		}

		// the depth bar: the border on the left, the pit on the right
		Circle circle = InfernoGeometry.circleAt(pos.x, pos.z);
		double depth = 1 - Math.min(1, Math.sqrt(pos.x * pos.x + pos.z * pos.z) / InfernoGeometry.BORDER_RADIUS);
		g.fill(left, TOP + HEIGHT, right, TOP + HEIGHT + 2, ARGB.black(120));
		g.fill(left, TOP + HEIGHT, left + (int) Math.round((right - left) * depth), TOP + HEIGHT + 2, ARGB.opaque(LayerCard.accent(circle)));

		// where you are, then how far to each marker (not while a circle's card is up: it says the same, bigger)
		if (LayerCard.showing() != null) {
			return;
		}
		int y = TOP + HEIGHT + 5;
		g.centeredText(font, InfernoGeometry.regionName(pos.x, pos.z), cx, y, ARGB.opaque(LayerCard.accent(circle)));
		String downText = down.label() + " " + distance(pos, down.x(), down.z());
		String restText = rest == null ? "" : (found ? "Rest " : "Rest (not found) ") + distance(pos, rest.x() + 0.5, rest.z() + 0.5);
		int gap = 14;
		int width = 9 + font.width(downText) + (rest == null ? 0 : gap + 9 + font.width(restText));
		int x = cx - width / 2;
		y += 11;
		arrow(g, x + 3, y, WAY_DOWN);
		g.text(font, downText, x + 9, y, 0xFFE8E8E8);
		if (rest != null) {
			x += 9 + font.width(downText) + gap;
			diamond(g, x + 3, y + 4, found ? REST_FOUND : REST_NEW);
			g.text(font, restText, x + 9, y, 0xFFE8E8E8);
		}
		if (mark != null) {
			// the followed waymark gets a line of its own
			String markText = font.plainSubstrByWidth(mark.name(), 120) + " " + distance(pos, mark.x() + 0.5, mark.z() + 0.5);
			int mx = cx - (9 + font.width(markText)) / 2;
			y += 11;
			MapOfHellScreen.pin(g, mx + 3, y + 8, ClientWaymarks.colour(mark), mark.shared());
			g.text(font, markText, mx + 9, y, 0xFFE8E8E8);
		}
	}

	/** In the Forge of Dis: a flame over the strip for the Great Forge, the region's name and how far the Forge is. */
	private static void forge(GuiGraphicsExtractor g, Font font, int cx, Vec3 pos, float yaw) {
		float rel = relative(pos, 0.5, 0.5, yaw);
		flame(g, stripX(cx, rel), TOP - 4, ARGB.multiplyAlpha(FORGE, Math.abs(rel) > 90 ? 0.5f : 1));
		int y = TOP + HEIGHT + 5;
		g.centeredText(font, ForgeGeometry.regionAt(pos.x, pos.z).title(), cx, y, FORGE);
		String text = "The Great Forge " + distance(pos, 0.5, 0.5);
		int x = cx - (9 + font.width(text)) / 2;
		y += 11;
		flame(g, x + 3, y + 1, FORGE);
		g.text(font, text, x + 9, y, 0xFFE8E8E8);
	}

	static final int FORGE = 0xFFFF8A30;

	/**
	 * Paradiso (Phase 7): the sphere you're in, and the way on: its great island while its trial is still to do, then the
	 * column of light up to the next sphere.
	 */
	private static void paradiso(GuiGraphicsExtractor g, Font font, int cx, Vec3 pos, float yaw) {
		ParadisoGeometry.Sphere s = ParadisoGeometry.sphereAtY(pos.y);
		int colour = ARGB.opaque(s.colour);
		int y = TOP + HEIGHT + 5;
		g.centeredText(font, s.title, cx, y, colour);
		double[] to;
		String text;
		if (s.last()) {
			to = ParadisoGeometry.centre(s);
			text = "The Celestial Rose ";
		} else if (ClientNav.passed(s)) {
			to = ParadisoGeometry.departure(s);
			text = "Column of light up ";
		} else {
			to = ParadisoGeometry.centre(s);
			text = "The trial ";
		}
		float rel = relative(pos, to[0], to[1], yaw);
		arrow(g, stripX(cx, rel), TOP - 5, ARGB.multiplyAlpha(colour, Math.abs(rel) > 90 ? 0.5f : 1));
		text += distance(pos, to[0], to[1]);
		int x = cx - (9 + font.width(text)) / 2;
		y += 11;
		arrow(g, x + 3, y, colour);
		g.text(font, text, x + 9, y, 0xFFE8E8E8);
	}

	/** A small flame, 5 wide and 6 tall, its base at (x, y + 5). */
	static void flame(GuiGraphicsExtractor g, int x, int y, int colour) {
		g.fill(x, y, x + 1, y + 1, colour);
		g.fill(x - 1, y + 1, x + 2, y + 3, colour);
		g.fill(x - 2, y + 3, x + 3, y + 5, colour);
		g.fill(x - 1, y + 5, x + 2, y + 6, colour);
		g.fill(x, y + 3, x + 1, y + 5, 0xFFFFE070);
	}

	/** How far a world position is from straight ahead, in degrees (negative: to the left). */
	private static float relative(Vec3 from, double x, double z, float yaw) {
		float bearing = (float) Math.toDegrees(Math.atan2(-(x - from.x), z - from.z));
		return Mth.wrapDegrees(bearing - yaw);
	}

	/** Where a direction sits on the strip; things behind you are pinned to its nearer end. */
	private static int stripX(int cx, float rel) {
		return cx + Math.round(Mth.clamp(rel, -90, 90) * HALF / 90f);
	}

	static String distance(Vec3 from, double x, double z) {
		double d = Math.sqrt((x - from.x) * (x - from.x) + (z - from.z) * (z - from.z));
		if (d < 8) {
			return "here";
		}
		return d < 1000 ? (int) d + " m" : String.format("%.1f km", d / 1000);
	}

	/** A downward arrow, 7 wide and 5 tall, its tip at (x, y + 4). */
	static void arrow(GuiGraphicsExtractor g, int x, int y, int colour) {
		g.fill(x - 1, y, x + 2, y + 1, ARGB.black(200));
		for (int row = 0; row < 4; row++) {
			int half = 3 - row;
			g.fill(x - half, y + 1 + row, x + half + 1, y + 2 + row, colour);
		}
	}

	/** A diamond 5 wide, centred on (x, y). */
	static void diamond(GuiGraphicsExtractor g, int x, int y, int colour) {
		for (int row = -2; row <= 2; row++) {
			int half = 2 - Math.abs(row);
			g.fill(x - half, y + row, x + half + 1, y + row + 1, colour);
		}
	}
}
