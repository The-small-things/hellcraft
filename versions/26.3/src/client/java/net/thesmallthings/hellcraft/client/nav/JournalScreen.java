package net.thesmallthings.hellcraft.client.nav;

import com.mojang.blaze3d.platform.InputConstants;
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
import net.thesmallthings.hellcraft.codex.Codex;
import net.thesmallthings.hellcraft.hazard.guardian.Guardian;
import net.thesmallthings.hellcraft.world.Circle;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.Navigation;
import net.thesmallthings.hellcraft.world.ShrineSites;

import java.util.ArrayList;
import java.util.List;

/**
 * The Pilgrim's Journal (J), drawn as an open book: a leather cover and two parchment pages. The left page is the table of
 * contents ("Here and now", then the Codex entries met so far, then the Pilgrim's Guide with this server's numbers,
 * JournalPayload), a red ribbon marking the open page; the right page is that page, in ink, with a scrollbar and arrows to
 * turn to the next. Left and right turn the pages, up and down (or the wheel) scroll, J closes it.
 */
public class JournalScreen extends Screen {
	// ink on parchment
	private static final int TITLE = 0xFF8E1C12;
	private static final int HEADING = 0xFF8E1C12;
	private static final int TEXT = 0xFF3A2414;
	private static final int DIM = 0xFF8A7458;
	private static final int PARCHMENT = 0xFFEADFC3;
	private static final int PARCHMENT_DARK = 0xFFD6C49C;
	private static final int LEATHER = 0xFF551810;
	private static final int LEATHER_DARK = 0xFF2A0A06;
	private static final int GOLD = 0xFFC09040;
	private static final int RIBBON = 0xFFA82418;
	private static final int ROW = 11;
	private static final int LINE = 10;
	private static final String[] DIRECTIONS = {"east", "south-east", "south", "south-west", "west", "north-west", "north", "north-east"};

	/** The open page: 0 is "Where you are", then the Codex entries, then the Guide's pages. Kept between openings. */
	private static int selected;

	private int bookX;
	private int bookY;
	private int bookW;
	private int bookH;
	private int listScroll;
	private int textScroll;

	/** A line of the open page, already wrapped. */
	private record Line(FormattedCharSequence text, int colour) {
	}

	/** A row of the table of contents: a section's heading, or a page (its number in the Journal). */
	private record Row(String text, int page, boolean heading) {
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
		bookW = Math.min(width - 20, 520);
		bookH = Math.min(height - 20, 300);
		bookX = (width - bookW) / 2;
		bookY = (height - bookH) / 2;
		selected = Mth.clamp(selected, 0, pageCount() - 1);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int pageCount() {
		return 1 + ClientNav.codex().size() + ClientNav.pages().size();
	}

	/** The table of contents, in sections. */
	private List<Row> index() {
		List<Row> rows = new ArrayList<>();
		rows.add(new Row("HERE AND NOW", -1, true));
		rows.add(new Row("Where you are", 0, false));
		List<Codex.Entry> codex = ClientNav.codex();
		if (!codex.isEmpty()) {
			rows.add(new Row("THE CODEX", -1, true));
			for (int i = 0; i < codex.size(); i++) {
				rows.add(new Row(capitalised(codex.get(i).title()), 1 + i, false));
			}
		}
		List<Guide.Page> pages = ClientNav.pages();
		if (!pages.isEmpty()) {
			rows.add(new Row("THE PILGRIM'S GUIDE", -1, true));
			for (int i = 0; i < pages.size(); i++) {
				rows.add(new Row(capitalised(pages.get(i).title()), 1 + codex.size() + i, false));
			}
		}
		return rows;
	}

	/** The open page's name, for the top of the right-hand page. */
	private String pageTitle() {
		if (selected == 0) {
			return "Where You Are";
		}
		List<Codex.Entry> codex = ClientNav.codex();
		if (selected <= codex.size()) {
			return capitalised(codex.get(selected - 1).title());
		}
		return capitalised(ClientNav.pages().get(selected - 1 - codex.size()).title());
	}

	// ------------------------------------------------------------------ the book's shape

	private int spine() {
		return bookX + bookW / 2;
	}

	private int pageTop() {
		return bookY + 10;
	}

	private int pageBottom() {
		return bookY + bookH - 10;
	}

	private int leftPageX() {
		return bookX + 12;
	}

	private int rightPageEnd() {
		return bookX + bookW - 12;
	}

	/** Where the right-hand page's text starts, and how wide it may be. */
	private int textX() {
		return spine() + 16;
	}

	private int textWidth() {
		return rightPageEnd() - 14 - textX();
	}

	private int listTop() {
		return pageTop() + 30;
	}

	private int listRows() {
		return (pageBottom() - 22 - listTop()) / ROW;
	}

	private int textTop() {
		return pageTop() + 30;
	}

	private int textRows() {
		return (pageBottom() - 22 - textTop()) / LINE;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		book(g);

		// the left page: the table of contents
		int lx = leftPageX();
		int lw = spine() - 8 - lx;
		g.centeredText(font, "The Pilgrim's Journal", lx + lw / 2, pageTop() + 8, TITLE);
		rule(g, lx + 14, spine() - 22, pageTop() + 20);
		List<Row> rows = index();
		int visible = listRows();
		listScroll = Mth.clamp(listScroll, 0, Math.max(0, rows.size() - visible));
		for (int i = 0; i < visible && listScroll + i < rows.size(); i++) {
			Row row = rows.get(listScroll + i);
			int y = listTop() + i * ROW;
			if (row.heading) {
				g.text(font, row.text, lx + 10, y + 1, HEADING, false);
				g.fill(lx + 10, y + 10, lx + 10 + font.width(row.text), y + 11, 0x408E1C12);
				continue;
			}
			boolean open = row.page == selected;
			boolean hover = mouseX >= lx + 6 && mouseX < spine() - 12 && mouseY >= y - 1 && mouseY < y + ROW - 1;
			if (open) {
				// the ribbon: out from the book's edge to the open page's name
				g.fill(bookX + 2, y - 1, lx + 14, y + ROW - 1, RIBBON);
				g.fill(bookX + 2, y + 2, bookX + 5, y + ROW - 4, LEATHER_DARK);
				g.fill(lx + 14, y - 1, spine() - 12, y + ROW - 1, 0x30A82418);
			} else if (hover) {
				g.fill(lx + 14, y - 1, spine() - 12, y + ROW - 1, 0x228A5A30);
			}
			String text = font.plainSubstrByWidth(row.text, spine() - 30 - (lx + 18));
			g.text(font, text, lx + 18, y + 1, open ? TITLE : TEXT, false);
		}
		scrollbar(g, spine() - 12, listTop(), visible * ROW, listScroll, visible, rows.size());
		g.centeredText(font, "← → turn the page   J closes", lx + lw / 2, pageBottom() - 12, DIM);

		// the right page: the open page
		int rx = spine() + 8;
		int rw = rightPageEnd() - rx;
		g.centeredText(font, font.plainSubstrByWidth(pageTitle(), rw - 20), rx + rw / 2, pageTop() + 8, TITLE);
		rule(g, rx + 22, rightPageEnd() - 14, pageTop() + 20);
		List<Line> lines = lines();
		int textRows = textRows();
		textScroll = Mth.clamp(textScroll, 0, Math.max(0, lines.size() - textRows));
		for (int i = 0; i < textRows && textScroll + i < lines.size(); i++) {
			Line line = lines.get(textScroll + i);
			g.text(font, line.text, textX(), textTop() + 1 + i * LINE, line.colour, false);
		}
		scrollbar(g, rightPageEnd() - 8, textTop(), textRows * LINE, textScroll, textRows, lines.size());

		// turning the page
		int fy = pageBottom() - 12;
		String page = "page " + (selected + 1) + " of " + pageCount();
		g.centeredText(font, page, rx + rw / 2, fy, DIM);
		if (selected > 0) {
			boolean over = overPrev(mouseX, mouseY);
			g.text(font, "‹ Back", textX(), fy, over ? TITLE : TEXT, false);
		}
		if (selected < pageCount() - 1) {
			boolean over = overNext(mouseX, mouseY);
			String next = "Next ›";
			g.text(font, next, rightPageEnd() - 14 - font.width(next), fy, over ? TITLE : TEXT, false);
		}
	}

	/** The cover, the two pages and the shadow down the spine. */
	private void book(GuiGraphicsExtractor g) {
		int x0 = bookX;
		int y0 = bookY;
		int x1 = bookX + bookW;
		int y1 = bookY + bookH;
		// the leather cover, with a gold line round it and gold corners
		g.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, LEATHER_DARK);
		g.fill(x0, y0, x1, y1, LEATHER);
		g.outline(x0 + 3, y0 + 3, bookW - 6, bookH - 6, 0xFF7A4A20);
		for (int[] c : new int[][]{{x0 + 2, y0 + 2}, {x1 - 8, y0 + 2}, {x0 + 2, y1 - 8}, {x1 - 8, y1 - 8}}) {
			g.fill(c[0], c[1], c[0] + 6, c[1] + 6, GOLD);
			g.fill(c[0] + 1, c[1] + 1, c[0] + 5, c[1] + 5, 0xFF8A6020);
			g.fill(c[0] + 2, c[1] + 2, c[0] + 4, c[1] + 4, GOLD);
		}
		// the stack of pages under the open ones
		int px0 = leftPageX();
		int px1 = rightPageEnd();
		for (int k = 3; k >= 1; k--) {
			g.fill(px0 - k, pageTop() + k, px1 + k, pageBottom() + k, k % 2 == 0 ? 0xFFC8B48A : 0xFFE0D2B0);
		}
		// the two pages
		g.fill(px0, pageTop(), spine() - 1, pageBottom(), PARCHMENT);
		g.fill(spine() + 1, pageTop(), px1, pageBottom(), PARCHMENT);
		g.fill(spine() - 1, pageTop(), spine() + 1, pageBottom(), 0xFF8A7050);
		// the shadow where the pages curve into the spine, and their worn outer edges
		for (int i = 0; i < 12; i++) {
			int alpha = (int) (70 * Math.pow(1 - i / 12.0, 2));
			g.fill(spine() - 2 - i, pageTop(), spine() - 1 - i, pageBottom(), alpha << 24 | 0x5A3A18);
			g.fill(spine() + 1 + i, pageTop(), spine() + 2 + i, pageBottom(), alpha << 24 | 0x5A3A18);
		}
		for (int i = 0; i < 4; i++) {
			int alpha = 40 - i * 10;
			g.fill(px0 + i, pageTop(), px0 + i + 1, pageBottom(), alpha << 24 | 0x6A4A20);
			g.fill(px1 - i - 1, pageTop(), px1 - i, pageBottom(), alpha << 24 | 0x6A4A20);
			g.fill(px0, pageTop() + i, px1, pageTop() + i + 1, alpha << 24 | 0x6A4A20);
			g.fill(px0, pageBottom() - i - 1, px1, pageBottom() - i, alpha << 24 | 0x6A4A20);
		}
		// age: a few faint stains, always in the same places
		for (int k = 0; k < 18; k++) {
			long h = (k + 1) * 0x9E3779B97F4A7C15L;
			int sx = px0 + 6 + (int) Math.floorMod(h >>> 13, (long) Math.max(1, px1 - px0 - 12));
			int sy = pageTop() + 6 + (int) Math.floorMod(h >>> 37, (long) Math.max(1, pageBottom() - pageTop() - 12));
			int r = 2 + (int) Math.floorMod(h >>> 51, 4L);
			g.fill(sx - r, sy - r / 2, sx + r, sy + r / 2, 0x0C6A4A20);
			g.fill(sx - r / 2, sy - r, sx + r / 2, sy + r, 0x0C6A4A20);
		}
		g.fill(px0, pageBottom() - 1, px1, pageBottom(), PARCHMENT_DARK);
	}

	/** A thin red rule with a diamond in the middle, under a page's title. */
	private static void rule(GuiGraphicsExtractor g, int x0, int x1, int y) {
		int mid = (x0 + x1) / 2;
		g.fill(x0, y, mid - 4, y + 1, 0xA08E1C12);
		g.fill(mid + 5, y, x1, y + 1, 0xA08E1C12);
		CompassHud.diamond(g, mid, y, HEADING);
	}

	/** A slim scrollbar, when there's more than fits. */
	private static void scrollbar(GuiGraphicsExtractor g, int x, int y, int height, int scroll, int visible, int total) {
		if (total <= visible) {
			return;
		}
		g.fill(x, y, x + 2, y + height, 0x306A4A20);
		int thumb = Math.max(8, height * visible / total);
		int at = y + (height - thumb) * scroll / Math.max(1, total - visible);
		g.fill(x, at, x + 2, at + thumb, 0xC08E1C12);
	}

	private boolean overPrev(double mouseX, double mouseY) {
		int fy = pageBottom() - 12;
		return selected > 0 && mouseX >= textX() - 2 && mouseX < textX() + font.width("‹ Back") + 2 && mouseY >= fy - 2 && mouseY < fy + 10;
	}

	private boolean overNext(double mouseX, double mouseY) {
		int fy = pageBottom() - 12;
		int w = font.width("Next ›");
		int x = rightPageEnd() - 14 - w;
		return selected < pageCount() - 1 && mouseX >= x - 2 && mouseX < x + w + 2 && mouseY >= fy - 2 && mouseY < fy + 10;
	}

	private void turn(int page) {
		int to = Mth.clamp(page, 0, pageCount() - 1);
		if (to != selected) {
			selected = to;
			textScroll = 0;
			// keep the open page's row in view
			List<Row> rows = index();
			for (int i = 0; i < rows.size(); i++) {
				if (rows.get(i).page == selected) {
					if (i < listScroll) {
						listScroll = i;
					} else if (i >= listScroll + listRows()) {
						listScroll = i - listRows() + 1;
					}
				}
			}
		}
	}

	private List<Line> lines() {
		List<Line> out = new ArrayList<>();
		if (selected == 0) {
			for (Line line : now()) {
				out.add(line);
			}
			return out;
		}
		List<Codex.Entry> codex = ClientNav.codex();
		if (selected <= codex.size()) {
			codexPage(out, codex.get(selected - 1));
			return out;
		}
		Guide.Page page = ClientNav.pages().get(selected - 1 - codex.size());
		for (String line : page.lines()) {
			wrap(out, line, TEXT);
		}
		return out;
	}

	/** A Codex entry: who it is, how to fight it, and Dante's words. */
	private void codexPage(List<Line> out, Codex.Entry entry) {
		wrap(out, entry.subtitle().toUpperCase(java.util.Locale.ROOT), HEADING);
		out.add(new Line(FormattedCharSequence.EMPTY, TEXT));
		for (String line : entry.who()) {
			wrap(out, line, TEXT);
		}
		if (entry == Codex.EMPEROR || entry.id().equals(Codex.EMPEROR.id())) {
			// the Emperor's page fills in, one piece for each guardian beaten (L1)
			int known = 0;
			for (Codex.Rumour r : Codex.RUMOURS) {
				out.add(new Line(FormattedCharSequence.EMPTY, TEXT));
				if (ClientNav.beaten(r.guardian())) {
					wrap(out, r.piece(), TEXT);
					known++;
				} else {
					wrap(out, "· Beat " + guardianTitle(r.guardian()) + " to learn what they know of him.", DIM);
				}
			}
			out.add(new Line(FormattedCharSequence.EMPTY, TEXT));
			wrap(out, known + " of " + Codex.RUMOURS.size() + " pieces.", DIM);
			wrap(out, "\"" + entry.quote() + "\"", DIM);
			wrap(out, "  (" + entry.source() + ")", DIM);
			return;
		}
		out.add(new Line(FormattedCharSequence.EMPTY, TEXT));
		wrap(out, "HOW TO FIGHT", HEADING);
		for (String line : entry.fight()) {
			wrap(out, "· " + line, TEXT);
		}
		out.add(new Line(FormattedCharSequence.EMPTY, TEXT));
		wrap(out, "\"" + entry.quote() + "\"", DIM);
		wrap(out, "  (" + entry.source() + ")", DIM);
	}

	private static String guardianTitle(String id) {
		net.thesmallthings.hellcraft.hazard.guardian.Guardian g = net.thesmallthings.hellcraft.hazard.guardian.Guardian.byId(id);
		return g == null ? id : g.title;
	}

	/** "THE CIRCLES" -> "The Circles" (small words stay small). */
	private static String capitalised(String title) {
		StringBuilder out = new StringBuilder();
		String[] words = title.toLowerCase(java.util.Locale.ROOT).split(" ");
		for (int i = 0; i < words.length; i++) {
			String w = words[i];
			boolean small = i > 0 && List.of("of", "the", "a", "an", "and", "in", "to", "on", "at").contains(w);
			out.append(i > 0 ? " " : "").append(w.isEmpty() || small ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1));
		}
		return out.toString();
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

		String pilgrimage = net.thesmallthings.hellcraft.client.PilgrimHud.summary();
		if (pilgrimage != null) {
			wrap(out, "YOUR PILGRIMAGE", HEADING);
			wrap(out, pilgrimage + ". Reach Purgatory without dying. /pilgrimage abandon gives it up.", TEXT);
			out.add(new Line(FormattedCharSequence.EMPTY, TEXT));
		}
		wrap(out, "SECRET WAYS", HEADING);
		wrap(out, "Secret ways found: " + SecretMarks.count() + " of " + net.thesmallthings.hellcraft.world.SecretWays.count()
				+ ". Hidden shortcuts off the roads, one circle down each; found ones are on the Map of Hell.", SecretMarks.count() > 0 ? TEXT : DIM);
		out.add(new Line(FormattedCharSequence.EMPTY, TEXT));

		wrap(out, "GUARDIANS", HEADING);
		wrap(out, "Each has four lairs in its circle, one in each quarter of Hell: fight it in whichever is nearest (the Map of Hell shows "
				+ "them). None of them blocks the way down.", DIM);
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
		if (overPrev(event.x(), event.y())) {
			turn(selected - 1);
			return true;
		}
		if (overNext(event.x(), event.y())) {
			turn(selected + 1);
			return true;
		}
		if (event.x() >= leftPageX() && event.x() < spine() - 12 && event.y() >= listTop() - 1) {
			int row = (int) ((event.y() - listTop() + 1) / ROW);
			List<Row> rows = index();
			int index = listScroll + row;
			if (row < listRows() && index < rows.size() && !rows.get(index).heading) {
				selected = rows.get(index).page;
				textScroll = 0;
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX < spine()) {
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
		if (event.isLeft()) {
			turn(selected - 1);
			return true;
		}
		if (event.isRight()) {
			turn(selected + 1);
			return true;
		}
		if (event.isUp() || event.isDown()) {
			textScroll += event.isUp() ? -1 : 1;
			return true;
		}
		if (event.key() == InputConstants.KEY_PAGEUP || event.key() == InputConstants.KEY_PAGEDOWN) {
			textScroll += (event.key() == InputConstants.KEY_PAGEUP ? -1 : 1) * Math.max(1, textRows() - 2);
			return true;
		}
		return super.keyPressed(event);
	}
}
