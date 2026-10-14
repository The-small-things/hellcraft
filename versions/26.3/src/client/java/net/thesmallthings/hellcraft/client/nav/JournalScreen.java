package net.thesmallthings.hellcraft.client.nav;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.thesmallthings.hellcraft.blood.Guide;
import net.thesmallthings.hellcraft.hazard.guardian.Guardian;
import net.thesmallthings.hellcraft.world.Circle;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.Navigation;
import net.thesmallthings.hellcraft.world.ShrineSites;

import java.util.ArrayList;
import java.util.List;

/**
 * The Pilgrim's Journal (J): first "Where you are", which says where you stand, what you're here to do and the
 * next step to take; then every page of the Pilgrim's Guide, with this server's numbers (JournalPayload).
 */
public class JournalScreen extends Screen {
	private static final int TITLE = 0xFFD04030;
	private static final int HEADING = 0xFFE8B060;
	private static final int TEXT = 0xFFE0D8C8;
	private static final int DIM = 0xFF989080;
	private static final int ROW = 11;
	private static final String[] DIRECTIONS = {"east", "south-east", "south", "south-west", "west", "north-west", "north", "north-east"};

	/** The open page: 0 is "Where you are", then the Guide's pages. Kept between openings. */
	private static int selected;

	private int panelX;
	private int panelY;
	private int panelW;
	private int panelH;
	private int listW;
	private int listScroll;
	private int textScroll;

	/** A line of the open page, already wrapped. */
	private record Line(FormattedCharSequence text, int colour) {
	}

	public JournalScreen() {
		super(Component.literal("Pilgrim's Journal"));
	}

	/** Opens the Journal at a page (for the client tests). */
	public static JournalScreen at(int page) {
		selected = page;
		return new JournalScreen();
	}

	@Override
	protected void init() {
		panelW = Math.min(width - 16, 440);
		panelH = Math.min(height - 16, 260);
		panelX = (width - panelW) / 2;
		panelY = (height - panelH) / 2;
		listW = font.width("Where you are") + 10;
		for (Guide.Page page : ClientNav.pages()) {
			listW = Math.max(listW, font.width(page.title()) + 10);
		}
		listW = Math.min(listW, panelW / 3);
		selected = Mth.clamp(selected, 0, ClientNav.pages().size());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private List<String> titles() {
		List<String> titles = new ArrayList<>();
		titles.add("Where you are");
		for (Guide.Page page : ClientNav.pages()) {
			titles.add(page.title());
		}
		return titles;
	}

	private int listTop() {
		return panelY + 22;
	}

	private int listRows() {
		return (panelH - 40) / ROW;
	}

	private int textX() {
		return panelX + 8 + listW + 10;
	}

	private int textWidth() {
		return panelX + panelW - 8 - textX();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF0140C0A);
		g.outline(panelX, panelY, panelW, panelH, 0xFF5A3A20);
		g.centeredText(font, "THE PILGRIM'S JOURNAL", panelX + panelW / 2, panelY + 7, TITLE);
		g.fill(panelX + 6, panelY + 18, panelX + panelW - 6, panelY + 19, 0xFF3A2418);

		// the list of pages
		List<String> titles = titles();
		int rows = listRows();
		listScroll = Mth.clamp(listScroll, 0, Math.max(0, titles.size() - rows));
		boolean moreBelow = listScroll + rows < titles.size();
		for (int i = 0; i < rows && listScroll + i < titles.size(); i++) {
			int index = listScroll + i;
			int y = listTop() + i * ROW;
			boolean hover = mouseX >= panelX + 6 && mouseX < panelX + 8 + listW && mouseY >= y - 1 && mouseY < y + ROW - 1;
			if (index == selected) {
				g.fill(panelX + 6, y - 1, panelX + 8 + listW, y + ROW - 1, 0x80A03020);
			} else if (hover) {
				g.fill(panelX + 6, y - 1, panelX + 8 + listW, y + ROW - 1, 0x30FFFFFF);
			}
			String title = font.plainSubstrByWidth(titles.get(index), listW - 4);
			g.text(font, title, panelX + 9, y + 1, index == selected ? 0xFFFFFFFF : index == 0 ? HEADING : TEXT, false);
		}
		if (titles.size() == 1) {
			g.text(font, font.plainSubstrByWidth("(The Guide's pages", listW), panelX + 9, listTop() + ROW + 4, DIM, false);
			g.text(font, font.plainSubstrByWidth("come from the server.)", listW), panelX + 9, listTop() + 2 * ROW + 4, DIM, false);
		}
		g.fill(panelX + 8 + listW + 4, panelY + 22, panelX + 8 + listW + 5, panelY + panelH - 6, 0xFF3A2418);

		// the open page
		List<Line> lines = lines();
		int textRows = (panelH - 28) / 10;
		textScroll = Mth.clamp(textScroll, 0, Math.max(0, lines.size() - textRows));
		for (int i = 0; i < textRows && textScroll + i < lines.size(); i++) {
			Line line = lines.get(textScroll + i);
			g.text(font, line.text, textX(), listTop() + 1 + i * 10, line.colour, false);
		}
		if (lines.size() > textRows) {
			String more = textScroll + textRows < lines.size() ? "scroll for more" : "";
			g.text(font, more, panelX + panelW - 8 - font.width(more), panelY + panelH - 12, DIM, false);
		}
		g.text(font, moreBelow ? "scroll for more pages" : "M: Map of Hell", panelX + 9, panelY + panelH - 12, DIM, false);
	}

	private List<Line> lines() {
		List<Line> out = new ArrayList<>();
		if (selected == 0) {
			for (Line line : now()) {
				out.add(line);
			}
			return out;
		}
		Guide.Page page = ClientNav.pages().get(selected - 1);
		wrap(out, page.title(), HEADING);
		out.add(new Line(FormattedCharSequence.EMPTY, TEXT));
		for (String line : page.lines()) {
			wrap(out, line, TEXT);
		}
		return out;
	}

	private void wrap(List<Line> out, String text, int colour) {
		if (text.isEmpty()) {
			out.add(new Line(FormattedCharSequence.EMPTY, colour));
			return;
		}
		for (FormattedCharSequence part : font.split(FormattedText.of(text), textWidth())) {
			out.add(new Line(part, colour));
		}
	}

	/** "Where you are": the place, the goal, the next step, the nearest Rest and the guardians. */
	private List<Line> now() {
		List<Line> out = new ArrayList<>();
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return out;
		}
		double x = player.getX();
		double z = player.getZ();
		if (!ClientNav.inferno()) {
			wrap(out, "WHERE YOU ARE", HEADING);
			wrap(out, "This world isn't the Inferno, so there is no way down to follow. The Guide's pages still apply.", TEXT);
			return out;
		}
		wrap(out, "WHERE YOU ARE", HEADING);
		if (ClientNav.inHell(minecraft)) {
			Circle circle = InfernoGeometry.circleAt(x, z);
			String depth = circle.depth() > 0 ? " (circle " + circle.depth() + " of 9)" : "";
			wrap(out, InfernoGeometry.regionName(x, z) + depth, TEXT);
			wrap(out, circle.rule(), DIM);
			wrap(out, "Bring: " + circle.gear(), DIM);
		} else if (minecraft.level.dimension() == Level.NETHER) {
			wrap(out, "The Forge of Dis (the Nether). The compass and the Map of Hell work back in Hell itself.", TEXT);
		} else if (minecraft.level.dimension() == Level.END) {
			wrap(out, "Paradiso (the End). The compass and the Map of Hell work back in Hell itself.", TEXT);
		} else {
			wrap(out, "Somewhere outside Hell.", TEXT);
		}
		out.add(new Line(FormattedCharSequence.EMPTY, TEXT));

		wrap(out, "YOUR GOAL", HEADING);
		wrap(out, ClientNav.beaten("lucifer")
				? "Lucifer is beaten. Climb out of Hell and rise to Paradiso (see THE CLIMB OUT)."
				: "Go down through the nine circles to the pit at the centre of the world (0, 0), and defeat Lucifer.", TEXT);
		out.add(new Line(FormattedCharSequence.EMPTY, TEXT));

		if (ClientNav.inHell(minecraft)) {
			wrap(out, "NEXT STEP", HEADING);
			Navigation.Target next = Navigation.wayDown(x, z);
			wrap(out, step(next, x, z), TEXT);
			wrap(out, "The red arrow on your compass points there. The roads (north, south, east and west) lead all the way down.", DIM);
			out.add(new Line(FormattedCharSequence.EMPTY, TEXT));

			ShrineSites.Site rest = ShrineSites.nearest(x, z);
			if (rest != null) {
				wrap(out, "NEAREST VIRGIL'S REST", HEADING);
				boolean found = ClientNav.found(rest.id());
				wrap(out, rest.title() + ": " + away(x, z, rest.x() + 0.5, rest.z() + 0.5) + ". "
						+ (found ? "Found: you can travel back to it from any Blood Altar." : "Not found yet: step into its ring to remember it."), TEXT);
				wrap(out, ClientNav.foundCount() + " of " + ShrineSites.all().size() + " Rests found. Press M for the Map of Hell.", DIM);
				out.add(new Line(FormattedCharSequence.EMPTY, TEXT));
			}
		}

		wrap(out, "GUARDIANS", HEADING);
		wrap(out, "Each waits in a lair beside the east road. None of them blocks the way down.", DIM);
		for (Guardian guardian : Guardian.values()) {
			boolean beaten = ClientNav.beaten(guardian.id());
			wrap(out, (beaten ? "✔ " : "· ") + guardian.title + ", " + guardian.subtitle + (beaten ? ": beaten" : ""), beaten ? DIM : TEXT);
		}
		return out;
	}

	/** One plain sentence: which way, how far, and what you'll find there. */
	static String step(Navigation.Target next, double x, double z) {
		String away = away(x, z, next.x(), next.z());
		return switch (next.label()) {
			case "Lucifer's pit" -> "Lucifer's pit is " + away + ". This is the bottom of Hell.";
			case "Gate of Hell" -> "Go " + away + " to the nearest Gate of Hell, then follow the road to the shore of Acheron.";
			case "Gate of Dis" -> "Go " + away + " to a gate in the Walls of Dis. The causeways on the roads cross the marsh.";
			case "The Spine" -> "Go " + away + " to the top of a Spine: a long stair down to the ice.";
			default -> "Go " + away + " to the nearest way down: a ramp into " + below(next) + ".";
		};
	}

	/** The region just below a way down. */
	private static String below(Navigation.Target way) {
		double r = Math.sqrt(way.x() * way.x() + way.z() * way.z());
		double f = Math.max(0, r - 80) / Math.max(1, r);
		return InfernoGeometry.regionName(way.x() * f, way.z() * f);
	}

	/** "340 m north-east" (or "here"). */
	static String away(double fromX, double fromZ, double x, double z) {
		double dx = x - fromX;
		double dz = z - fromZ;
		double d = Math.sqrt(dx * dx + dz * dz);
		if (d < 8) {
			return "right here";
		}
		int octant = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(dz, dx)) / 45), 8);
		return (d < 1000 ? (int) d + " m " : String.format("%.1f km ", d / 1000)) + DIRECTIONS[octant];
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.x() >= panelX + 6 && event.x() < panelX + 8 + listW && event.y() >= listTop() - 1) {
			int row = (int) ((event.y() - listTop() + 1) / ROW);
			int index = listScroll + row;
			if (row < listRows() && index < titles().size()) {
				selected = index;
				textScroll = 0;
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX < panelX + 8 + listW) {
			listScroll -= (int) Math.signum(scrollY);
		} else {
			textScroll -= (int) Math.signum(scrollY) * 3;
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (NavKeys.JOURNAL.matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}
}
