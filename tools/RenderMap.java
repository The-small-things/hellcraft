import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.MapPainter;
import net.thesmallthings.hellcraft.world.Zone;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Offline preview of the Inferno layout (no Minecraft needed). Renders a shaded top-down map (painted by
 * MapPainter, like the Map of Hell in game), zooms on the gates and Malebolge, and a radial cross-section.
 *
 * <pre>
 * javac -d build/tools src/main/java/net/thesmallthings/hellcraft/world/{Zone,Circle,InfernoGeometry,MapPainter}.java tools/RenderMap.java
 * java -cp build/tools RenderMap out/
 * </pre>
 */
public class RenderMap {
	public static void main(String[] args) throws Exception {
		File out = new File(args.length > 0 ? args[0] : "out");
		out.mkdirs();
		double border = InfernoGeometry.BORDER_RADIUS;
		topDown(new File(out, "inferno_map.png"), 1400, border + 150, 0, 0);
		topDown(new File(out, "malebolge_zoom.png"), 1000, 460, 440, 440);
		topDown(new File(out, "dis_gate_zoom.png"), 800, 120, InfernoGeometry.DIS_WALL_RADIUS, 0);
		topDown(new File(out, "gate_of_hell_zoom.png"), 800, 90, InfernoGeometry.GATE_WALL_RADIUS, 0);
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
				int c = MapPainter.colour(x, z, scale);
				if (Math.sqrt(x * x + z * z) > InfernoGeometry.BORDER_RADIUS) {
					c = MapPainter.tint(c, 0.5);
				}
				img.setRGB(px, py, c);
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
		double maxR = InfernoGeometry.BORDER_RADIUS + 100;
		int minY = -64, maxY = 320;
		for (int px = 0; px < w; px++) {
			double x = px * maxR / w;
			double z = 0;
			double top = InfernoGeometry.baseHeight(x, z);
			Zone zone = InfernoGeometry.zoneAt(x, z);
			int yTop = (int) ((maxY - top) / (maxY - minY) * h);
			for (int py = yTop; py < h; py++) {
				img.setRGB(px, py, MapPainter.colour(zone));
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
		g.drawString("Radial cross-section along the east road (+X; vertical exaggeration ~" + (int) (maxR / w / ((maxY - minY) / (double) h)) + "x). Left = centre (Judecca), right = world border.", 10, 20);
		g.dispose();
		ImageIO.write(img, "png", file);
	}
}
