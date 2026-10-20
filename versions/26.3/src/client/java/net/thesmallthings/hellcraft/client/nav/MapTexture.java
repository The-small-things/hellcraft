package net.thesmallthings.hellcraft.client.nav;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.MapPainter;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * The Map of Hell's picture: the whole Inferno painted from the geometry (MapPainter), {@link #SIZE} pixels
 * across, on old parchment with an inked rim round the world's edge. Painting takes about a second, so it starts in the
 * background as soon as the server says this world is the Inferno, and is uploaded the first time the map is opened.
 */
public final class MapTexture {
	private MapTexture() {
	}

	public static final Identifier ID = HellcraftMod.id("map_of_hell");
	public static final int SIZE = 1024;
	/** The picture runs from -EXTENT to +EXTENT on both axes. */
	public static final double EXTENT = InfernoGeometry.BORDER_RADIUS + 100;

	private static CompletableFuture<int[]> painting;
	private static boolean uploaded;

	/** Starts painting, if it hasn't started yet. */
	public static synchronized void prepare() {
		if (painting == null) {
			painting = CompletableFuture.supplyAsync(MapTexture::paint);
		}
	}

	/** The texture, once painted (call on the render thread); null while it's still being painted. */
	@Nullable
	public static synchronized Identifier get() {
		prepare();
		if (uploaded) {
			return ID;
		}
		int[] pixels = painting.getNow(null);
		if (pixels == null) {
			return null;
		}
		NativeImage image = new NativeImage(SIZE, SIZE, false);
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				image.setPixel(x, y, pixels[y * SIZE + x]);
			}
		}
		Minecraft.getInstance().getTextureManager().register(ID, new DynamicTexture(() -> "Map of Hell", image));
		uploaded = true;
		return ID;
	}

	/** World x (or z) at the middle of a pixel column (or row). */
	public static double world(double pixel) {
		return -EXTENT + pixel * 2 * EXTENT / SIZE;
	}

	/** Outside the world's edge: parchment, its grain and stains, an inked rim and a shadow cast by the funnel. */
	private static int parchment(double x, double z, double r, double scale) {
		double past = r - InfernoGeometry.BORDER_RADIUS;
		if (past < scale * 1.5) {
			return 0x2A1A10;
		}
		double grain = 1.0 + 0.06 * InfernoGeometry.fbm(x / 60, z / 60, 51) + 0.03 * InfernoGeometry.fbm(x / 9, z / 9, 53);
		double shadow = Math.min(1, past / (scale * 10));
		return MapPainter.tint(MapPainter.PARCHMENT, grain * (0.78 + 0.22 * shadow));
	}

	private static int[] paint() {
		double scale = 2 * EXTENT / SIZE;
		int[] pixels = new int[SIZE * SIZE];
		for (int py = 0; py < SIZE; py++) {
			for (int px = 0; px < SIZE; px++) {
				double x = world(px + 0.5);
				double z = world(py + 0.5);
				double r = Math.sqrt(x * x + z * z);
				pixels[py * SIZE + px] = 0xFF000000 | (r > InfernoGeometry.BORDER_RADIUS ? parchment(x, z, r, scale) : MapPainter.colour(x, z, scale));
			}
		}
		return pixels;
	}
}
