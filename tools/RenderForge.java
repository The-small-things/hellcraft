import net.thesmallthings.hellcraft.world.ForgeGeometry;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Offline preview of the Forge of Dis (the Nether), from ForgeGeometry alone: a top-down map of the regions shaded by
 * floor height, and cross-sections through the middle (rock dark, open space black, lava orange).
 *
 * <pre>
 * javac -d build/tools src/main/java/net/thesmallthings/hellcraft/world/{Zone,Circle,InfernoGeometry,ForgeGeometry}.java ../../tools/RenderForge.java
 * java -cp build/tools RenderForge out/
 * </pre>
 */
public class RenderForge {
	private static final Color[] REGION = {
			new Color(0xC08040), // the Great Forge
			new Color(0xE05010), // the Slag Sea
			new Color(0x6A6A78), // the Foundry Halls
			new Color(0x405870), // the Chainlands
			new Color(0x9A9488), // the Ash Wastes
			new Color(0x7A5038), // the Cyclops Quarries
	};

	public static void main(String[] args) throws Exception {
		File out = new File(args.length > 0 ? args[0] : "out");
		out.mkdirs();
		topDown(new File(out, "forge_map.png"), 900, ForgeGeometry.BORDER + 20);
		// east to west through the centre (the Chainlands, the mesa, the Quarries), then south to north
		section(new File(out, "forge_section_ew.png"), 1, 0);
		section(new File(out, "forge_section_sn.png"), 0, 1);
		System.out.println("Wrote the Forge of Dis previews to " + out);
	}

	private static void topDown(File file, int size, double half) throws Exception {
		BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
		for (int py = 0; py < size; py++) {
			for (int px = 0; px < size; px++) {
				double x = (px / (double) size * 2 - 1) * half;
				double z = (py / (double) size * 2 - 1) * half;
				if (Math.hypot(x, z) > ForgeGeometry.BORDER) {
					img.setRGB(px, py, 0x101010);
					continue;
				}
				ForgeGeometry.Region region = ForgeGeometry.regionAt(x, z);
				int ground = ForgeGeometry.groundBelow((int) x, 100, (int) z);
				Color c = REGION[region.ordinal()];
				if (ground < ForgeGeometry.LAVA) {
					c = new Color(0xFF6A10);
				}
				double shade = 0.45 + 0.55 * Math.min(1, Math.max(0, (ground - 20) / 70.0));
				img.setRGB(px, py, new Color((int) (c.getRed() * shade), (int) (c.getGreen() * shade), (int) (c.getBlue() * shade)).getRGB());
			}
		}
		ImageIO.write(img, "png", file);
	}

	private static void section(File file, int dx, int dz) throws Exception {
		int w = (int) (ForgeGeometry.BORDER * 2);
		int h = 128;
		int scale = 2;
		BufferedImage img = new BufferedImage(w, h * scale, BufferedImage.TYPE_INT_RGB);
		for (int i = 0; i < w; i++) {
			double t = i - ForgeGeometry.BORDER;
			double x = t * dx;
			double z = t * dz;
			double[] column = ForgeGeometry.column(x, z);
			for (int y = 0; y < h; y++) {
				boolean rock = ForgeGeometry.shape(x, y, z, column) > 0;
				int rgb = rock ? REGION[ForgeGeometry.regionAt(x, z).ordinal()].darker().getRGB() : y < ForgeGeometry.LAVA ? 0xFF6A10 : 0x080808;
				for (int s = 0; s < scale; s++) {
					img.setRGB(i, (h - 1 - y) * scale + s, rgb);
				}
			}
		}
		ImageIO.write(img, "png", file);
	}
}
