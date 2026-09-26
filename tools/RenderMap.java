import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.Zone;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.EnumMap;
import java.util.Map;

/**
 * Offline preview of the Inferno layout (no Minecraft needed). Renders a shaded top-down map,
 * a zoom on Malebolge and the Walls of Dis, and a radial cross-section.
 *
 * <pre>
 * javac -d build/tools src/main/java/net/thesmallthings/hellcraft/world/{Zone,Circle,InfernoGeometry}.java tools/RenderMap.java
 * java -cp build/tools RenderMap out/
 * </pre>
 */
public class RenderMap {
	static final Map<Zone, Color> COLORS = new EnumMap<>(Zone.class);

	static {
		COLORS.put(Zone.DARK_WOOD, new Color(0x2E4A26));
		COLORS.put(Zone.VESTIBULE, new Color(0x8A8A80));
		COLORS.put(Zone.ACHERON, new Color(0x3A4A5E));
		COLORS.put(Zone.LIMBO, new Color(0xB8B8A8));
		COLORS.put(Zone.LUST, new Color(0x7A4A9A));
		COLORS.put(Zone.GLUTTONY, new Color(0x5A4430));
		COLORS.put(Zone.GREED, new Color(0xC8A030));
		COLORS.put(Zone.STYX, new Color(0x2A3A2A));
		COLORS.put(Zone.WALLS_OF_DIS, new Color(0x3A2A2A));
		COLORS.put(Zone.HERESY, new Color(0x5A7A8A));
		COLORS.put(Zone.PHLEGETHON, new Color(0xB01010));
		COLORS.put(Zone.WOOD_OF_SUICIDES, new Color(0x4A3020));
		COLORS.put(Zone.BURNING_SANDS, new Color(0xD06030));
		COLORS.put(Zone.MALEBOLGE, new Color(0x404048));
		COLORS.put(Zone.MALEBOLGE_PITCH, new Color(0x201010));
		COLORS.put(Zone.MALEBOLGE_BLIGHT, new Color(0x0A4A55));
		COLORS.put(Zone.WELL_OF_GIANTS, new Color(0x606060));
		COLORS.put(Zone.COCYTUS, new Color(0xA0C8E8));
		COLORS.put(Zone.JUDECCA, new Color(0x70A0F0));
	}

	public static void main(String[] args) throws Exception {
		File out = new File(args.length > 0 ? args[0] : "out");
		out.mkdirs();
		topDown(new File(out, "inferno_map.png"), 1400, 6300, 0, 0);
		topDown(new File(out, "malebolge_zoom.png"), 1000, 900, 700, 700);
		topDown(new File(out, "dis_gate_zoom.png"), 800, 120, 1715, 1715);
		crossSection(new File(out, "cross_section.png"));
		System.out.println("Wrote previews to " + out.getAbsolutePath());
	}

	static void topDown(File file, int size, double halfExtent, double cx, double cz) throws Exception {
		BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
		double scale = 2 * halfExtent / size;
		for (int py = 0; py < size; py++) {
			for (int px = 0; px < size; px++) {
				double x = cx - halfExtent + px * scale;
				double z = cz - halfExtent + py * scale;
				double h = InfernoGeometry.baseHeight(x, z);
				double hx = InfernoGeometry.baseHeight(x + scale, z);
				double hz = InfernoGeometry.baseHeight(x, z + scale);
				Color c = COLORS.get(InfernoGeometry.zoneAt(x, z));
				InfernoGeometry.Fluid fluid = InfernoGeometry.fluidAt(x, z);
				if (fluid != InfernoGeometry.Fluid.NONE && h < InfernoGeometry.fluidLevel(x, z)) {
					c = fluid == InfernoGeometry.Fluid.WATER ? new Color(0x203A70) : new Color(0xFF5A00);
				}
				InfernoGeometry.WallPart wall = InfernoGeometry.disWallAt(x, z);
				if (wall == InfernoGeometry.WallPart.WALL || wall == InfernoGeometry.WallPart.TOWER) {
					c = new Color(0x111111);
				}
				if (Math.sqrt(x * x + z * z) > InfernoGeometry.BORDER_RADIUS) {
					c = c.darker().darker();
				}
				double slope = ((h - hx) + (h - hz)) / scale;
				double shade = Math.max(0.35, Math.min(1.4, 1.0 + slope * 0.6));
				double height = 0.75 + (h + 60) / 320.0 * 0.5;
				img.setRGB(px, py, tint(c, shade * height));
			}
		}
		ImageIO.write(img, "png", file);
	}

	static void crossSection(File file) throws Exception {
		int w = 1600, h = 500;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(0x120808));
		g.fillRect(0, 0, w, h);
		double maxR = 6200;
		int minY = -64, maxY = 320;
		for (int px = 0; px < w; px++) {
			double x = px * maxR / w;
			double z = 0;
			double top = InfernoGeometry.baseHeight(x, z);
			Zone zone = InfernoGeometry.zoneAt(x, z);
			int yTop = (int) ((maxY - top) / (maxY - minY) * h);
			for (int py = yTop; py < h; py++) {
				img.setRGB(px, py, COLORS.get(zone).getRGB());
			}
			InfernoGeometry.Fluid fluid = InfernoGeometry.fluidAt(x, z);
			int level = InfernoGeometry.fluidLevel(x, z);
			if (fluid != InfernoGeometry.Fluid.NONE && top < level) {
				int yl = (int) ((maxY - level) / (double) (maxY - minY) * h);
				for (int py = yl; py < yTop; py++) {
					img.setRGB(px, py, fluid == InfernoGeometry.Fluid.WATER ? 0x3050A0 : 0xFF5A00);
				}
			}
		}
		g.setColor(Color.WHITE);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
		g.drawString("Radial cross-section along +X (vertical exaggeration ~" + (int) (maxR / w / ((maxY - minY) / (double) h)) + "x). Left = centre (Judecca), right = world border.", 10, 20);
		g.dispose();
		ImageIO.write(img, "png", file);
	}

	static int tint(Color c, double f) {
		int r = (int) Math.max(0, Math.min(255, c.getRed() * f));
		int gr = (int) Math.max(0, Math.min(255, c.getGreen() * f));
		int b = (int) Math.max(0, Math.min(255, c.getBlue() * f));
		return (r << 16) | (gr << 8) | b;
	}
}
