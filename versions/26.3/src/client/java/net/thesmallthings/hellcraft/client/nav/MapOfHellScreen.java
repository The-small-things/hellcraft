package net.thesmallthings.hellcraft.client.nav;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.thesmallthings.hellcraft.hazard.guardian.Guardian;
import net.thesmallthings.hellcraft.net.WaymarksPayload;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.Navigation;
import net.thesmallthings.hellcraft.world.NobleCastle;
import net.thesmallthings.hellcraft.world.SecretWays;
import net.thesmallthings.hellcraft.world.ShrineSites;

import java.util.ArrayList;
import java.util.List;

/**
 * The Map of Hell (M): the funnel from above, drawn from the same geometry as the terrain, with every way down,
 * the Virgil's Rests (gold once found), the guardians' lairs (grey once beaten), your waymarks and you. Scroll to zoom,
 * drag to move, and point at anything to see what it is. Right-click to place a waymark (world/Waymarks): name it, pick
 * its colour, share it with everyone if you like, and follow it on the compass; click one to change it.
 */
public class MapOfHellScreen extends Screen {
	private static final int TITLE = 0xFFD04030;
	private static final int TEXT = 0xFFE0D8C8;
	private static final int DIM = 0xFF908878;
	private static final int RAMP = 0xFFB02828;
	private static final int GATE = 0xFFFF4030;
	private static final int LAIR = 0xFFE04040;
	private static final int BEATEN = 0xFF808080;
	private static final int SECRET = 0xFF40D8E8;
	private static final float MAX_ZOOM = 12;

	private static final List<Navigation.Target> WAYS = Navigation.all();

	/** Where the map was left, so it opens there again. */
	private static float zoom = 1;
	private static double viewX;
	private static double viewZ;

	private int side;
	private int left;
	private int top;

	/** The waymark being placed or looked at (null: none). id 0 is a new one. */
	private Editing editing;
	private EditBox nameBox;
	private Checkbox sharedBox;

	private record Editing(int id, int x, int z, String name, int colour, boolean shared, boolean mine, String owner) {
		Editing withColour(int c) {
			return new Editing(id, x, z, name, c, shared, mine, owner);
		}
	}

	private static final int PANEL_W = 210;
	private static final int PANEL_H = 96;

	public MapOfHellScreen() {
		super(Component.literal("Map of Hell"));
	}

	@Override
	protected void init() {
		side = Math.max(64, Math.min(height - 62, width - 28));
		left = (width - side) / 2;
		top = 24;
		MapTexture.prepare();
		clampView();
		if (editing != null) {
			editorWidgets();
		}
	}

	private int panelX() {
		return (width - PANEL_W) / 2;
	}

	private int panelY() {
		return top + side - PANEL_H - 8;
	}

	/** The waymark panel's widgets: its name (your own only), whether everyone sees it, and the buttons. */
	private void editorWidgets() {
		int x = panelX();
		int y = panelY();
		if (editing.mine) {
			String typed = nameBox == null ? editing.name : nameBox.getValue();
			nameBox = new EditBox(font, x + 8, y + 18, PANEL_W - 16, 16, Component.literal("Name"));
			nameBox.setMaxLength(net.thesmallthings.hellcraft.world.Waymarks.MAX_NAME);
			nameBox.setValue(typed);
			nameBox.setHint(Component.literal("Name this waymark").withColor(DIM));
			addRenderableWidget(nameBox);
			setInitialFocus(nameBox);
			sharedBox = Checkbox.builder(Component.literal("Everyone can see it"), font).pos(x + 8, y + 52).selected(editing.shared).build();
			addRenderableWidget(sharedBox);
		}
		int by = y + PANEL_H - 22;
		List<Button> buttons = new ArrayList<>();
		if (editing.mine) {
			buttons.add(Button.builder(Component.literal(editing.id == 0 ? "Place" : "Save"), b -> saveEditing()).build());
		}
		if (editing.id != 0) {
			buttons.add(Button.builder(Component.literal(ClientWaymarks.following(editing.id) ? "Stop following" : "Follow"), b -> {
				ClientWaymarks.follow(editing.id);
				closeEditor();
			}).build());
		}
		if (editing.mine && editing.id != 0) {
			buttons.add(Button.builder(Component.literal("Remove"), b -> {
				ClientWaymarks.remove(editing.id);
				closeEditor();
			}).build());
		}
		buttons.add(Button.builder(Component.literal(editing.mine ? "Cancel" : "Close"), b -> closeEditor()).build());
		int gap = 4;
		int each = (PANEL_W - 16 - gap * (buttons.size() - 1)) / buttons.size();
		for (int i = 0; i < buttons.size(); i++) {
			Button b = buttons.get(i);
			b.setRectangle(each, 18, x + 8 + i * (each + gap), by);
			addRenderableWidget(b);
		}
	}

	private void openEditor(Editing e) {
		editing = e;
		nameBox = null;
		rebuildWidgets();
	}

	private void closeEditor() {
		editing = null;
		nameBox = null;
		sharedBox = null;
		rebuildWidgets();
	}

	private void saveEditing() {
		if (editing == null || !editing.mine) {
			return;
		}
		ClientWaymarks.save(editing.id, nameBox == null ? editing.name : nameBox.getValue(), editing.x, editing.z, editing.colour,
				sharedBox != null && sharedBox.selected());
		closeEditor();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		drawMap(g, mouseX, mouseY);
		// the waymark panel's widgets go over the map
		super.extractRenderState(g, mouseX, mouseY, a);
	}

	private void drawMap(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		frame(g);
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
		// the waymarks, named once you zoom in (or always, the one you follow)
		for (WaymarksPayload.Entry w : ClientWaymarks.all()) {
			int px = sx(w.x() + 0.5);
			int py = sy(w.z() + 0.5);
			boolean followed = ClientWaymarks.following(w.id());
			if (followed) {
				int pulse = (int) (2 + Math.sin(System.currentTimeMillis() / 160.0) * 1.5);
				g.outline(px - 5 - pulse, py - 9 - pulse, 11 + 2 * pulse, 11 + 2 * pulse, ClientWaymarks.colour(w));
			}
			pin(g, px, py, ClientWaymarks.colour(w), w.shared());
			if (zoom >= 3 || followed) {
				String label = font.plainSubstrByWidth(w.name(), 90);
				int lw = font.width(label);
				g.fill(px - lw / 2 - 2, py + 2, px + lw / 2 + 2, py + 12, 0xA0000000);
				g.centeredText(font, label, px, py + 3, ClientWaymarks.colour(w));
			}
		}
		if (editing != null) {
			// where the new waymark will go
			pin(g, sx(editing.x + 0.5), sy(editing.z + 0.5), ClientWaymarks.COLOURS[editing.colour], editing.shared);
		}
		if (here) {
			you(g, sx(player.getX()), sy(player.getZ()), player.getYRot());
		}
		circleLabels(g);
		rose(g, left + side - 24, top + 30);
		scaleBar(g, left + 8, top + side - 12);
		g.disableScissor();
		title(g);

		legend(g);

		if (editing != null) {
			editorPanel(g, mouseX, mouseY);
		} else if (mouseX >= left && mouseX < left + side && mouseY >= top && mouseY < top + side) {
			g.setComponentTooltipForNextFrame(font, tooltip(markers, mouseX, mouseY, player, here), mouseX, mouseY);
		}
	}

	private static final int LEATHER = 0xFF551810;
	private static final int LEATHER_DARK = 0xFF2A0A06;
	private static final int GOLD = 0xFFC09040;
	private static final int INK = 0xFF2A1A10;
	private static final int RED_INK = 0xFF7E140C;

	/** The map's frame: dark leather with a gold line and gold corners, like the Journal's cover. */
	private void frame(GuiGraphicsExtractor g) {
		int x0 = left - 7;
		int y0 = top - 7;
		int x1 = left + side + 7;
		int y1 = top + side + 7;
		g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, LEATHER_DARK);
		g.fill(x0, y0, x1, y1, LEATHER);
		g.outline(x0 + 3, y0 + 3, x1 - x0 - 6, y1 - y0 - 6, 0xFF7A4A20);
		for (int[] c : new int[][]{{x0 + 1, y0 + 1}, {x1 - 7, y0 + 1}, {x0 + 1, y1 - 7}, {x1 - 7, y1 - 7}}) {
			g.fill(c[0], c[1], c[0] + 6, c[1] + 6, GOLD);
			g.fill(c[0] + 1, c[1] + 1, c[0] + 5, c[1] + 5, 0xFF8A6020);
			g.fill(c[0] + 2, c[1] + 2, c[0] + 4, c[1] + 4, GOLD);
		}
		g.fill(left - 1, top - 1, left + side + 1, top + side + 1, 0xFF140C08);
	}

	/** The title, on a parchment label over the frame's top edge. */
	private void title(GuiGraphicsExtractor g) {
		Component title = Component.literal("THE MAP OF HELL").withStyle(net.minecraft.ChatFormatting.BOLD);
		int w = font.width(title) + 28;
		int x = (width - w) / 2;
		int y = top - 18;
		g.fill(x - 1, y - 1, x + w + 1, y + 15, LEATHER_DARK);
		g.fill(x, y, x + w, y + 14, 0xFFE6D8B6);
		g.fill(x + 2, y + 2, x + w - 2, y + 3, 0x608A5A30);
		g.fill(x + 2, y + 11, x + w - 2, y + 12, 0x608A5A30);
		CompassHud.diamond(g, x + 7, y + 7, RED_INK);
		CompassHud.diamond(g, x + w - 8, y + 7, RED_INK);
		g.text(font, title, x + 14, y + 3, RED_INK, false);
	}

	/** Where each circle's label sits: the middle of its band along the north-west ray (between the roads and ramps). */
	private static List<double[]> labels;
	private static List<String> labelNames;

	private static synchronized void prepareLabels() {
		if (labels != null) {
			return;
		}
		labels = new ArrayList<>();
		labelNames = new ArrayList<>();
		double dx = -Math.sqrt(0.5);
		double dz = -Math.sqrt(0.5);
		net.thesmallthings.hellcraft.world.Zone current = null;
		double start = 0;
		for (double r = InfernoGeometry.BORDER_RADIUS - 2; r >= -4; r -= 4) {
			net.thesmallthings.hellcraft.world.Zone z = r < 0 ? null : InfernoGeometry.zoneAt(r * dx, r * dz);
			if (z != current) {
				if (current != null) {
					String name = label(current);
					if (name != null) {
						double mid = (start + r) / 2;
						labels.add(new double[]{mid * dx, mid * dz, start - r});
						labelNames.add(name);
					}
				}
				current = z;
				start = r;
			}
		}
	}

	private static String label(net.thesmallthings.hellcraft.world.Zone zone) {
		return switch (zone) {
			case DARK_WOOD -> "The Dark Wood";
			case LIMBO -> "I  Limbo";
			case LUST -> "II  Lust";
			case GLUTTONY -> "III  Gluttony";
			case GREED -> "IV  Greed";
			case STYX -> "V  The Styx";
			case HERESY -> "VI  Heresy";
			case PHLEGETHON -> "VII  Phlegethon";
			case WOOD_OF_SUICIDES -> "The Wood";
			case BURNING_SANDS -> "The Sands";
			case MALEBOLGE -> "VIII  Malebolge";
			case COCYTUS -> "IX  Cocytus";
			default -> null;
		};
	}

	/** Each circle's name, in the middle of its band (where there's room for it). */
	private void circleLabels(GuiGraphicsExtractor g) {
		prepareLabels();
		for (int i = 0; i < labels.size(); i++) {
			double[] l = labels.get(i);
			double bandPixels = l[2] / scale();
			if (bandPixels < 9) {
				continue;
			}
			String name = labelNames.get(i);
			int x = sx(l[0]);
			int y = sy(l[1]);
			int w = font.width(name);
			if (x + w / 2 < left || x - w / 2 > left + side || y < top || y > top + side) {
				continue;
			}
			g.fill(x - w / 2 - 3, y - 5, x + w / 2 + 3, y + 6, 0x90140A06);
			g.text(font, name, x - w / 2, y - 4, 0xFFEDE3C8, false);
		}
	}

	/** A compass rose: north (up) in red. */
	private static void rose(GuiGraphicsExtractor g, int cx, int cy) {
		int r = 13;
		for (int k = 0; k <= r; k++) {
			int half = Math.max(0, (r - k) / 4);
			// north, south, west, east arms
			g.fill(cx - half, cy - k, cx + half + 1, cy - k + 1, 0xFFB02418);
			g.fill(cx - half, cy + k, cx + half + 1, cy + k + 1, 0xFFE6D8B6);
			g.fill(cx - k, cy - half, cx - k + 1, cy + half + 1, 0xFFE6D8B6);
			g.fill(cx + k, cy - half, cx + k + 1, cy + half + 1, 0xFFE6D8B6);
		}
		for (int k = 0; k <= r * 2 / 3; k++) {
			g.fill(cx - k, cy - k, cx - k + 1, cy - k + 1, 0xC0E6D8B6);
			g.fill(cx + k, cy - k, cx + k + 1, cy - k + 1, 0xC0E6D8B6);
			g.fill(cx - k, cy + k, cx - k + 1, cy + k + 1, 0xC0E6D8B6);
			g.fill(cx + k, cy + k, cx + k + 1, cy + k + 1, 0xC0E6D8B6);
		}
		g.fill(cx - 1, cy - 1, cx + 2, cy + 2, INK);
		Minecraft mc = Minecraft.getInstance();
		g.text(mc.font, "N", cx - 2, cy - r - 10, 0xFFFF7060, true);
	}

	/** A scale bar: a round distance, in alternating dark and light blocks. */
	private void scaleBar(GuiGraphicsExtractor g, int x, int y) {
		int[] nice = {25, 50, 100, 250, 500, 1000, 2000};
		int metres = nice[nice.length - 1];
		for (int n : nice) {
			if (n / scale() >= 40) {
				metres = n;
				break;
			}
		}
		int length = (int) Math.round(metres / scale());
		g.fill(x - 3, y - 13, x + length + 4, y + 5, 0x90140A06);
		for (int i = 0; i < 4; i++) {
			int a = x + length * i / 4;
			int b = x + length * (i + 1) / 4;
			g.fill(a, y, b, y + 3, i % 2 == 0 ? 0xFFE6D8B6 : INK);
		}
		g.outline(x - 1, y - 1, length + 2, 5, 0xFFE6D8B6);
		String text = metres >= 1000 ? metres / 1000 + " km" : metres + " m";
		g.text(font, text, x, y - 11, 0xFFEDE3C8, false);
	}

	/** The waymark panel: its title, the colours to pick from, and (drawn by their widgets) the name, sharing and buttons. */
	private void editorPanel(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int x = panelX();
		int y = panelY();
		g.fill(x - 1, y - 1, x + PANEL_W + 1, y + PANEL_H + 1, 0xFF6A4628);
		g.fill(x, y, x + PANEL_W, y + PANEL_H, 0xF0140C0A);
		String title = editing.id == 0 ? "New waymark" : editing.mine ? "Your waymark" : editing.name;
		g.text(font, title, x + 8, y + 6, TITLE);
		if (!editing.mine) {
			g.text(font, "Shared by " + editing.owner + ".", x + 8, y + 22, TEXT);
			g.text(font, font.plainSubstrByWidth(editing.x + ", " + editing.z + ": " + InfernoGeometry.regionName(editing.x, editing.z), PANEL_W - 16),
					x + 8, y + 36, DIM);
			return;
		}
		String where = editing.x + ", " + editing.z;
		g.text(font, where, x + PANEL_W - 8 - font.width(where), y + 6, DIM);
		// the colours: click one
		for (int i = 0; i < ClientWaymarks.COLOURS.length; i++) {
			int sx = swatchX(i);
			int sy = y + 38;
			boolean picked = editing.colour == i;
			g.fill(sx - 1, sy - 1, sx + 11, sy + 11, picked ? 0xFFFFFFFF : 0xFF000000);
			g.fill(sx, sy, sx + 10, sy + 10, ClientWaymarks.COLOURS[i]);
			if (mouseX >= sx && mouseX < sx + 10 && mouseY >= sy && mouseY < sy + 10) {
				g.setTooltipForNextFrame(font, Component.literal(ClientWaymarks.COLOUR_NAMES[i]), mouseX, mouseY);
			}
		}
	}

	private int swatchX(int i) {
		return panelX() + 8 + i * 14;
	}

	/** A waymark's pin: a coloured head on a short dark stem, its point at (x, y); a shared one has a white ring. */
	static void pin(GuiGraphicsExtractor g, int x, int y, int colour, boolean shared) {
		g.fill(x - 3, y - 8, x + 4, y - 3, 0xFF000000);
		g.fill(x - 2, y - 9, x + 3, y - 2, 0xFF000000);
		g.fill(x - 2, y - 7, x + 3, y - 4, colour);
		g.fill(x - 1, y - 8, x + 2, y - 3, colour);
		if (shared) {
			g.fill(x, y - 6, x + 1, y - 5, 0xFFFFFFFF);
		}
		g.fill(x, y - 3, x + 1, y + 1, 0xFF000000);
	}

	private void legend(GuiGraphicsExtractor g) {
		int y = top + side + 6;
		String[] labels = {"Way down", "Rest found", "Rest not found", "Guardian", "Waymark", "You"};
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
				case 4 -> pin(g, mx, y + 8, ClientWaymarks.COLOURS[0], false);
				default -> g.fill(mx - 2, y + 2, mx + 3, y + 7, 0xFFFFFFFF);
			}
			g.text(font, labels[i], x + 10, y, TEXT);
			x += widths[i];
		}
		g.centeredText(font, "Scroll to zoom, drag to move, right-click to place a waymark. " + ClientNav.foundCount() + " of " + ShrineSites.all().size() + " Rests found"
				+ (SecretMarks.count() > 0 ? ", " + SecretMarks.count() + " of " + SecretWays.count() + " secret ways." : "."),
				width / 2, y + 12, DIM);
	}

	private List<Component> tooltip(List<Marker> markers, int mouseX, int mouseY, LocalPlayer player, boolean here) {
		WaymarksPayload.Entry w = waymarkAt(mouseX, mouseY);
		if (w != null) {
			List<Component> lines = new ArrayList<>();
			lines.add(Component.literal(w.name()).withColor(ClientWaymarks.colour(w)));
			lines.add(Component.literal(w.mine() ? (w.shared() ? "Your waymark, shared with everyone." : "Your waymark (only you see it).")
					: "Shared by " + w.owner() + ".").withColor(DIM));
			if (here) {
				lines.add(Component.literal(JournalScreen.away(player.getX(), player.getZ(), w.x() + 0.5, w.z() + 0.5)).withColor(DIM));
			}
			lines.add(Component.literal(ClientWaymarks.following(w.id()) ? "Followed: the compass points to it. Click to change."
					: "Click to " + (w.mine() ? "change it or " : "") + "follow it on the compass.").withColor(DIM));
			return lines;
		}
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
			for (int k = 0; k < guardian.lairs(); k++) {
				out.add(new Marker(sx(guardian.lairColumn(k).getX() + 0.5), sy(guardian.lairColumn(k).getZ() + 0.5), Shape.LAIR, beaten, List.of(
						Component.literal(guardian.title + ", " + guardian.subtitle).withStyle(guardian.color),
						Component.literal(beaten ? "Beaten." : "One of its " + guardian.lairs() + " lairs: fight it in any of them. Optional: the way down "
								+ "doesn't need it.").withColor(DIM))));
			}
		}
		// the secret ways this player has found (the others stay secret)
		for (SecretWays.Site site : SecretWays.all()) {
			if (SecretMarks.found(site.id())) {
				out.add(new Marker(sx(site.x() + 0.5), sy(site.z() + 0.5), Shape.SECRET, true, List.of(
						Component.literal("Secret way: " + site.kind().title).withColor(SECRET),
						Component.literal("Down to " + site.place() + ". " + site.kind().how).withColor(DIM))));
			}
		}
		if (here) {
			out.add(new Marker(sx(player.getX()), sy(player.getZ()), Shape.NONE, false, List.of(Component.literal("You"),
					Component.literal(InfernoGeometry.regionName(player.getX(), player.getZ())).withColor(DIM))));
		}
		return out;
	}

	private enum Shape {RAMP, GATE, PIT, REST, LAIR, SECRET, NONE}

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
				case SECRET -> {
					// a secret way: a dark hole ringed in soul-fire blue
					g.fill(x - 3, y - 2, x + 4, y + 3, 0xFF000000);
					g.fill(x - 2, y - 3, x + 3, y + 4, 0xFF000000);
					g.fill(x - 2, y - 2, x + 3, y + 3, SECRET);
					g.fill(x - 1, y - 1, x + 2, y + 2, 0xFF061418);
				}
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

	/** The waymark under the mouse (its pin's head), or null. */
	private WaymarksPayload.Entry waymarkAt(double mouseX, double mouseY) {
		WaymarksPayload.Entry best = null;
		double bestD = 6 * 6;
		for (WaymarksPayload.Entry w : ClientWaymarks.all()) {
			double dx = sx(w.x() + 0.5) - mouseX;
			double dy = sy(w.z() + 0.5) - 5 - mouseY;
			if (dx * dx + dy * dy <= bestD) {
				bestD = dx * dx + dy * dy;
				best = w;
			}
		}
		return best;
	}

	private boolean inPanel(double x, double y) {
		return editing != null && x >= panelX() && x < panelX() + PANEL_W && y >= panelY() && y < panelY() + PANEL_H;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (inPanel(event.x(), event.y())) {
			for (int i = 0; editing.mine && i < ClientWaymarks.COLOURS.length; i++) {
				int sx = swatchX(i);
				int sy = panelY() + 38;
				if (event.x() >= sx && event.x() < sx + 10 && event.y() >= sy && event.y() < sy + 10) {
					editing = editing.withColour(i);
				}
			}
			return true;
		}
		boolean onMap = event.x() >= left && event.x() < left + side && event.y() >= top && event.y() < top + side;
		if (!onMap || !ClientNav.inferno()) {
			return false;
		}
		WaymarksPayload.Entry w = waymarkAt(event.x(), event.y());
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && w != null) {
			openEditor(new Editing(w.id(), w.x(), w.z(), w.name(), Math.floorMod(w.colour(), ClientWaymarks.COLOURS.length), w.shared(), w.mine(), w.owner()));
			return true;
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && ClientWaymarks.canSend()) {
			int x = (int) Math.floor(wx(event.x()));
			int z = (int) Math.floor(wz(event.y()));
			if (Math.sqrt((double) x * x + (double) z * z) > InfernoGeometry.BORDER_RADIUS) {
				return true;
			}
			int colour = ClientWaymarks.all().size() % ClientWaymarks.COLOURS.length;
			openEditor(new Editing(0, x, z, "", colour, false, true, ""));
			return true;
		}
		if (editing != null) {
			closeEditor();
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (inPanel(event.x(), event.y())) {
			// dragging inside the waymark panel (selecting the name's text) doesn't move the map
			super.mouseDragged(event, dx, dy);
			return true;
		}
		viewX -= dx * scale();
		viewZ -= dy * scale();
		clampView();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (editing != null) {
			if (event.isEscape()) {
				closeEditor();
				return true;
			}
			if (event.isConfirmation() && editing.mine) {
				saveEditing();
				return true;
			}
			return super.keyPressed(event);
		}
		if (NavKeys.MAP.matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	/** Opens the map with a new waymark's panel open at a point (for the client tests). */
	public static MapOfHellScreen placing(double x, double z, float zoomTo, String name, int colour) {
		MapOfHellScreen screen = at(x, z, zoomTo);
		screen.editing = new Editing(0, (int) x, (int) z, name, colour, true, true, "");
		return screen;
	}

	/** Opens the map centred on a point (for the client tests). */
	public static MapOfHellScreen at(double x, double z, float zoomTo) {
		zoom = zoomTo;
		viewX = x;
		viewZ = z;
		return new MapOfHellScreen();
	}
}
