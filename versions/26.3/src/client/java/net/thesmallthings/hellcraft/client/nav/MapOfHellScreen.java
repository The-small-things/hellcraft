package net.thesmallthings.hellcraft.client.nav;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.thesmallthings.hellcraft.hazard.guardian.Guardian;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.Navigation;
import net.thesmallthings.hellcraft.world.NobleCastle;
import net.thesmallthings.hellcraft.world.ShrineSites;

import java.util.ArrayList;
import java.util.List;

/**
 * The Map of Hell (M): the funnel from above, drawn from the same geometry as the terrain, with every way down,
 * the Virgil's Rests (gold once found), the guardians' lairs (grey once beaten) and you. Scroll to zoom, drag to
 * move, and point at anything to see what it is.
 */
public class MapOfHellScreen extends Screen {
	private static final int TITLE = 0xFFD04030;
	private static final int TEXT = 0xFFE0D8C8;
	private static final int DIM = 0xFF908878;
	private static final int RAMP = 0xFFB02828;
	private static final int GATE = 0xFFFF4030;
	private static final int LAIR = 0xFFE04040;
	private static final int BEATEN = 0xFF808080;
	private static final float MAX_ZOOM = 12;

	private static final List<Navigation.Target> WAYS = Navigation.all();

	/** Where the map was left, so it opens there again. */
	private static float zoom = 1;
	private static double viewX;
	private static double viewZ;

	private int side;
	private int left;
	private int top;

	public MapOfHellScreen() {
		super(Component.literal("Map of Hell"));
	}

	@Override
	protected void init() {
		side = Math.max(64, Math.min(height - 54, width - 24));
		left = (width - side) / 2;
		top = 18;
		MapTexture.prepare();
		clampView();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		g.centeredText(font, "THE MAP OF HELL", width / 2, 6, TITLE);
		g.fill(left - 2, top - 2, left + side + 2, top + side + 2, 0xFF4A2E1C);
		g.fill(left - 1, top - 1, left + side + 1, top + side + 1, 0xFF0C0806);
		Identifier texture = MapTexture.get();
		if (texture == null) {
			g.centeredText(font, "Drawing the map...", width / 2, top + side / 2 - 4, DIM);
			return;
		}
		double half = half();
		double span = 2 * MapTexture.EXTENT;
		g.blit(texture, left, top, left + side, top + side,
				(float) ((viewX - half + MapTexture.EXTENT) / span), (float) ((viewX + half + MapTexture.EXTENT) / span),
				(float) ((viewZ - half + MapTexture.EXTENT) / span), (float) ((viewZ + half + MapTexture.EXTENT) / span));

		LocalPlayer player = minecraft.player;
		boolean here = ClientNav.inHell(minecraft);
		List<Marker> markers = markers(player, here);
		g.enableScissor(left, top, left + side, top + side);
		if (here) {
			// the way down the compass points to, ringed
			Navigation.Target next = Navigation.wayDown(player.getX(), player.getZ());
			int nx = sx(next.x());
			int ny = sy(next.z());
			g.outline(nx - 6, ny - 5, 13, 12, 0xFFFFFFFF);
		}
		for (Marker m : markers) {
			m.draw(g);
		}
		if (here) {
			you(g, sx(player.getX()), sy(player.getZ()), player.getYRot());
		}
		g.disableScissor();

		legend(g);

		if (mouseX >= left && mouseX < left + side && mouseY >= top && mouseY < top + side) {
			g.setComponentTooltipForNextFrame(font, tooltip(markers, mouseX, mouseY, player, here), mouseX, mouseY);
		}
	}

	private void legend(GuiGraphicsExtractor g) {
		int y = top + side + 6;
		String[] labels = {"Way down", "Rest found", "Rest not found", "Guardian", "You"};
		int[] widths = new int[labels.length];
		int total = 0;
		for (int i = 0; i < labels.length; i++) {
			widths[i] = 10 + font.width(labels[i]) + 10;
			total += widths[i];
		}
		int x = (width - total) / 2;
		for (int i = 0; i < labels.length; i++) {
			int mx = x + 3;
			switch (i) {
				case 0 -> CompassHud.arrow(g, mx, y, GATE);
				case 1 -> CompassHud.diamond(g, mx, y + 4, CompassHud.REST_FOUND);
				case 2 -> CompassHud.diamond(g, mx, y + 4, CompassHud.REST_NEW);
				case 3 -> lair(g, mx, y + 4, LAIR, false);
				default -> g.fill(mx - 2, y + 2, mx + 3, y + 7, 0xFFFFFFFF);
			}
			g.text(font, labels[i], x + 10, y, TEXT);
			x += widths[i];
		}
		g.centeredText(font, "Scroll to zoom, drag to move. " + ClientNav.foundCount() + " of " + ShrineSites.all().size() + " Rests found.",
				width / 2, y + 12, DIM);
	}

	private List<Component> tooltip(List<Marker> markers, int mouseX, int mouseY, LocalPlayer player, boolean here) {
		Marker best = null;
		int bestD = 7 * 7;
		for (Marker m : markers) {
			int d = (m.x - mouseX) * (m.x - mouseX) + (m.y - mouseY) * (m.y - mouseY);
			if (d <= bestD) {
				bestD = d;
				best = m;
			}
		}
		if (best != null) {
			return best.lines;
		}
		double x = wx(mouseX);
		double z = wz(mouseY);
		List<Component> lines = new ArrayList<>();
		if (Math.sqrt(x * x + z * z) > InfernoGeometry.BORDER_RADIUS) {
			lines.add(Component.literal("The world's edge"));
		} else {
			lines.add(Component.literal(InfernoGeometry.regionName(x, z)));
			if (NobleCastle.distance(x, z) < NobleCastle.STREAM_OUTER) {
				lines.add(Component.literal("The Noble Castle: shades who talk, trade and give you Codex pages"));
			}
			lines.add(Component.literal(InfernoGeometry.circleAt(x, z).rule()).withColor(DIM));
		}
		String where = (int) x + ", " + (int) z;
		if (here) {
			double d = Math.sqrt((x - player.getX()) * (x - player.getX()) + (z - player.getZ()) * (z - player.getZ()));
			where += "  (" + (d < 1000 ? (int) d + " m" : String.format("%.1f km", d / 1000)) + " away)";
		}
		lines.add(Component.literal(where).withColor(DIM));
		return lines;
	}

	/** Everything drawn on the map, nearest to the eye last. */
	private List<Marker> markers(LocalPlayer player, boolean here) {
		List<Marker> out = new ArrayList<>();
		for (Navigation.Target way : WAYS) {
			boolean ramp = way.label().equals("Way down");
			String hint = switch (way.label()) {
				case "Gate of Hell" -> "Through the wall to the shore of Acheron.";
				case "Gate of Dis" -> "The way into the city: Heresy and below.";
				case "The Spine" -> "A stair down the Well of Giants to the ice.";
				case "Lucifer's pit" -> "The bottom of Hell. Lucifer waits here.";
				default -> "A ramp down the cliff into the next circle.";
			};
			out.add(new Marker(sx(way.x()), sy(way.z()), ramp ? Shape.RAMP : way.label().equals("Lucifer's pit") ? Shape.PIT : Shape.GATE,
					ClientNav.beaten("lucifer"), List.of(Component.literal(way.label()), Component.literal(hint).withColor(DIM))));
		}
		for (ShrineSites.Site site : ShrineSites.all()) {
			boolean found = ClientNav.found(site.id());
			out.add(new Marker(sx(site.x() + 0.5), sy(site.z() + 0.5), Shape.REST, found, List.of(
					Component.literal("Virgil's Rest: " + site.title()),
					Component.literal(found ? "Found: travel here from any Blood Altar." : "Not found yet: step into its ring to remember it.").withColor(DIM))));
		}
		for (Guardian guardian : Guardian.values()) {
			if (guardian.offMap()) {
				continue;
			}
			boolean beaten = ClientNav.beaten(guardian.id());
			out.add(new Marker(sx(guardian.lairColumn().getX() + 0.5), sy(guardian.lairColumn().getZ() + 0.5), Shape.LAIR, beaten, List.of(
					Component.literal(guardian.title + ", " + guardian.subtitle).withStyle(guardian.color),
					Component.literal(beaten ? "Beaten." : "A guardian's lair, beside the east road. Optional: the road goes on past it.").withColor(DIM))));
		}
		if (here) {
			out.add(new Marker(sx(player.getX()), sy(player.getZ()), Shape.NONE, false, List.of(Component.literal("You"),
					Component.literal(InfernoGeometry.regionName(player.getX(), player.getZ())).withColor(DIM))));
		}
		return out;
	}

	private enum Shape {RAMP, GATE, PIT, REST, LAIR, NONE}

	private record Marker(int x, int y, Shape shape, boolean done, List<Component> lines) {
		void draw(GuiGraphicsExtractor g) {
			switch (shape) {
				case RAMP -> {
					if (zoom < 2) {
						// zoomed out, the ramps are only small notches: they'd crowd the inner circles
						g.fill(x - 1, y - 1, x + 2, y, RAMP);
						g.fill(x, y, x + 1, y + 1, RAMP);
					} else {
						CompassHud.arrow(g, x, y - 3, RAMP);
					}
				}
				case GATE -> CompassHud.arrow(g, x, y - 3, GATE);
				case PIT -> {
					g.fill(x - 4, y - 4, x + 5, y + 5, done ? BEATEN : GATE);
					g.fill(x - 2, y - 2, x + 3, y + 3, 0xFF000000);
				}
				case REST -> {
					CompassHud.diamond(g, x, y, 0xFF000000);
					g.fill(x - 3, y, x + 4, y + 1, 0xFF000000);
					g.fill(x, y - 3, x + 1, y + 4, 0xFF000000);
					CompassHud.diamond(g, x, y, done ? CompassHud.REST_FOUND : CompassHud.REST_NEW);
				}
				case LAIR -> lair(g, x, y, done ? BEATEN : LAIR, done);
				default -> {
				}
			}
		}
	}

	/** A guardian's lair: a red square with a dark heart (a grey one with a tick once beaten). */
	private static void lair(GuiGraphicsExtractor g, int x, int y, int colour, boolean beaten) {
		g.fill(x - 3, y - 3, x + 4, y + 4, 0xFF000000);
		g.fill(x - 2, y - 2, x + 3, y + 3, colour);
		if (beaten) {
			g.fill(x - 1, y, x, y + 1, 0xFFFFFFFF);
			g.fill(x, y + 1, x + 1, y + 2, 0xFFFFFFFF);
			g.fill(x + 1, y - 1, x + 2, y + 1, 0xFFFFFFFF);
		} else {
			g.fill(x - 1, y - 1, x + 2, y + 2, 0xFF300000);
		}
	}

	/** You: a white arrow pointing the way you face (north is up). */
	private static void you(GuiGraphicsExtractor g, int x, int y, float yaw) {
		g.pose().pushMatrix();
		g.pose().translate(x + 0.5f, y + 0.5f);
		g.pose().rotate((float) Math.toRadians(yaw + 180));
		for (int pass = 0; pass < 2; pass++) {
			int colour = pass == 0 ? 0xFF000000 : 0xFFFFFFFF;
			int grow = pass == 0 ? 1 : 0;
			for (int row = 0; row < 4; row++) {
				g.fill(-row - grow, -5 + row - grow, row + 1 + grow, -4 + row + grow, colour);
			}
			g.fill(-1 - grow, -1, 2 + grow, 4 + grow, colour);
		}
		g.pose().popMatrix();
	}

	// ------------------------------------------------------------------ the view

	/** Half the width of the world shown, in blocks. */
	private double half() {
		return MapTexture.EXTENT / zoom;
	}

	/** Blocks per screen pixel. */
	private double scale() {
		return 2 * half() / side;
	}

	private int sx(double x) {
		return (int) Math.floor(left + side / 2.0 + (x - viewX) / scale());
	}

	private int sy(double z) {
		return (int) Math.floor(top + side / 2.0 + (z - viewZ) / scale());
	}

	private double wx(double mouseX) {
		return viewX + (mouseX - left - side / 2.0) * scale();
	}

	private double wz(double mouseY) {
		return viewZ + (mouseY - top - side / 2.0) * scale();
	}

	private void clampView() {
		double limit = MapTexture.EXTENT - half();
		viewX = Mth.clamp(viewX, -limit, limit);
		viewZ = Mth.clamp(viewZ, -limit, limit);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY == 0) {
			return false;
		}
		// zoom about the point under the mouse
		double x = wx(Mth.clamp(mouseX, left, left + side));
		double z = wz(Mth.clamp(mouseY, top, top + side));
		zoom = Mth.clamp(zoom * (float) Math.pow(1.25, scrollY), 1, MAX_ZOOM);
		viewX = x - (Mth.clamp(mouseX, left, left + side) - left - side / 2.0) * scale();
		viewZ = z - (Mth.clamp(mouseY, top, top + side) - top - side / 2.0) * scale();
		clampView();
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		viewX -= dx * scale();
		viewZ -= dy * scale();
		clampView();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (NavKeys.MAP.matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	/** Opens the map centred on a point (for the client tests). */
	public static MapOfHellScreen at(double x, double z, float zoomTo) {
		zoom = zoomTo;
		viewX = x;
		viewZ = z;
		return new MapOfHellScreen();
	}
}
