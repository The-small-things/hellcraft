package net.thesmallthings.hellcraft.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.thesmallthings.hellcraft.world.InfernoGeometry;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Hellcraft with a far-terrain mod (Distant Horizons), run only when HELLCRAFT_LOD=1 (the lod workflow puts the mod in the
 * test client's mods folder): an Inferno single-player world, a view from high over Lust out across the funnel, filmed
 * at once and again after the far terrain has had time to draw (lod_*). It fails if the far-terrain mod isn't loaded or
 * the game falls over; it says how much of the band above the horizon is land in each picture, so the two can be compared.
 */
public class LodTest implements FabricClientGameTest {
	private static final boolean ON = System.getenv("HELLCRAFT_LOD") != null;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!ON) {
			return;
		}
		boolean dh = FabricLoader.getInstance().isModLoaded("distanthorizons");
		System.out.println("LOD test: Distant Horizons loaded = " + dh);
		if (!dh) {
			throw new AssertionError("Distant Horizons isn't loaded: the lod workflow should have put it in the mods folder");
		}
		try (TestSingleplayerContext world = context.worldBuilder().adjustSettings(MapTest::inferno).create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode spectator Player0");
			context.runOnClient(client -> {
				client.options.renderDistance().set(8);
				if (!client.gui.hud.isHidden()) {
					client.gui.hud.toggle();
				}
			});
			double x = InfernoGeometry.scaled(2600);
			world.getServer().runCommand(String.format(Locale.ROOT, "tp Player0 %.1f 220 0 90 18", x));
			context.waitTicks(100);
			Path near = context.takeScreenshot(TestScreenshotOptions.of("lod_at_once").withSize(1280, 720));
			System.out.printf(Locale.ROOT, "LOD test: land above the horizon at once: %.2f%n", land(near));
			// time for the far terrain to be built and drawn
			context.waitTicks(20 * 240);
			Path far = context.takeScreenshot(TestScreenshotOptions.of("lod_after").withSize(1280, 720));
			System.out.printf(Locale.ROOT, "LOD test: land above the horizon after four minutes: %.2f%n", land(far));
		}
	}

	/** How much of the band just above the middle of the picture is not sky (the sky being the top row's colour). */
	private static double land(Path picture) {
		try {
			BufferedImage image = ImageIO.read(picture.toFile());
			int w = image.getWidth();
			int h = image.getHeight();
			int sky = image.getRGB(w / 2, 4);
			int landPixels = 0;
			int total = 0;
			for (int y = h * 30 / 100; y < h * 48 / 100; y += 2) {
				for (int x = 0; x < w; x += 4) {
					total++;
					if (distance(image.getRGB(x, y), sky) > 40) {
						landPixels++;
					}
				}
			}
			return total == 0 ? 0 : landPixels / (double) total;
		} catch (IOException e) {
			throw new AssertionError("couldn't read " + picture, e);
		}
	}

	private static int distance(int a, int b) {
		return Math.abs((a >> 16 & 255) - (b >> 16 & 255)) + Math.abs((a >> 8 & 255) - (b >> 8 & 255)) + Math.abs((a & 255) - (b & 255));
	}
}
