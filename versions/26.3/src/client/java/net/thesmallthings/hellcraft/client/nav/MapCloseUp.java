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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

/**
 * The painted map, sharp at any zoom. The whole Inferno is painted once at about ten blocks a pixel (MapTexture), which
 * goes blocky when the map is zoomed in; so once the view has settled, the part in view (and a margin round it, for
 * small moves) is painted again in the background at the screen's own resolution, and drawn over the coarse one when
 * it is ready. One picture at a time; it is thrown away when the map closes.
 */
final class MapCloseUp {
	private MapCloseUp() {
	}

	static final Identifier ID = HellcraftMod.id("map_close_up");
	/** The most pixels painted for one view (a view with more is painted a little coarser). */
	private static final int MAX_PIXELS = 1_400_000;
	/** How long the view must hold still before it is painted (ms), and how much is painted round it (each side). */
	private static final long SETTLE_MS = 120;
	private static final double MARGIN = 0.2;
	/** The coarse map is good enough until the view shows fewer blocks a pixel than this share of its own. */
	private static final double NEEDED_BELOW = 0.8;

	/** A painted picture: the world it covers and its size in pixels. */
	record Picture(double x0, double z0, double x1, double z1, int w, int h) {
		double blocksPerPixel() {
			return (x1 - x0) / w;
		}

		boolean covers(double ax0, double az0, double ax1, double az1) {
			return x0 <= ax0 && z0 <= az0 && x1 >= ax1 && z1 >= az1;
		}
	}

	private record Job(Picture picture, AtomicBoolean cancelled, CompletableFuture<int[]> pixels) {
	}

	@Nullable
	private static Picture shown;
	@Nullable
	private static DynamicTexture texture;
	@Nullable
	private static Job job;
	private static double lastX0;
	private static double lastZ0;
	private static double lastX1;
	private static double lastZ1;
	private static long changedAt;

	/**
	 * Called each frame with the view (blocks) and its size on screen in real pixels. Returns the picture to draw over
	 * the coarse map, or null if there is none yet (or the coarse map is sharp enough at this zoom).
	 */
	@Nullable
	static Picture frame(double x0, double z0, double x1, double z1, int pixelsWide, int pixelsHigh) {
		double want = (x1 - x0) / Math.max(1, pixelsWide);
		if (want >= NEEDED_BELOW * 2 * MapTexture.EXTENT / MapTexture.SIZE) {
			cancel();
			return null;
		}
		long now = System.currentTimeMillis();
		if (x0 != lastX0 || z0 != lastZ0 || x1 != lastX1 || z1 != lastZ1) {
			lastX0 = x0;
			lastZ0 = z0;
			lastX1 = x1;
			lastZ1 = z1;
			changedAt = now;
		}
		if (job != null && job.pixels().isDone()) {
			int[] pixels = job.pixels().getNow(null);
			if (pixels != null && !job.cancelled().get()) {
				upload(job.picture(), pixels);
			}
			job = null;
		}
		boolean good = shown != null && shown.covers(x0, z0, x1, z1) && shown.blocksPerPixel() <= want * 1.3;
		if (!good && now - changedAt >= SETTLE_MS) {
			if (job != null && !(job.picture().covers(x0, z0, x1, z1) && job.picture().blocksPerPixel() <= want * 1.3)) {
				cancel();
			}
			if (job == null) {
				start(x0, z0, x1, z1, pixelsWide, pixelsHigh);
			}
		}
		// an older picture is still better than the coarse map wherever it reaches, unless it is itself much too coarse
		return shown != null && shown.blocksPerPixel() <= want * 4 ? shown : null;
	}

	private static void start(double x0, double z0, double x1, double z1, int pixelsWide, int pixelsHigh) {
		double mx = (x1 - x0) * MARGIN;
		double mz = (z1 - z0) * MARGIN;
		double w = pixelsWide * (1 + 2 * MARGIN);
		double h = pixelsHigh * (1 + 2 * MARGIN);
		double shrink = Math.max(1, Math.sqrt(w * h / MAX_PIXELS));
		Picture picture = new Picture(x0 - mx, z0 - mz, x1 + mx, z1 + mz, Math.max(1, (int) (w / shrink)), Math.max(1, (int) (h / shrink)));
		AtomicBoolean cancelled = new AtomicBoolean();
		job = new Job(picture, cancelled, CompletableFuture.supplyAsync(() -> paint(picture, cancelled)));
	}

	private static void cancel() {
		if (job != null) {
			job.cancelled().set(true);
			job = null;
		}
	}

	private static int[] paint(Picture p, AtomicBoolean cancelled) {
		int[] pixels = new int[p.w() * p.h()];
		double bx = (p.x1() - p.x0()) / p.w();
		double bz = (p.z1() - p.z0()) / p.h();
		// (the rim and its shadow keep the width they have on the coarse map)
		double coarse = 2 * MapTexture.EXTENT / MapTexture.SIZE;
		IntStream.range(0, p.h()).parallel().forEach(row -> {
			if (cancelled.get()) {
				return;
			}
			double z = p.z0() + (row + 0.5) * bz;
			for (int col = 0; col < p.w(); col++) {
				double x = p.x0() + (col + 0.5) * bx;
				double r = Math.sqrt(x * x + z * z);
				pixels[row * p.w() + col] = 0xFF000000 | (r > InfernoGeometry.BORDER_RADIUS ? MapTexture.parchment(x, z, r, coarse) : MapPainter.colour(x, z, bx));
			}
		});
		return pixels;
	}

	private static void upload(Picture p, int[] pixels) {
		if (texture == null || texture.getPixels().getWidth() != p.w() || texture.getPixels().getHeight() != p.h()) {
			close();
			texture = new DynamicTexture(() -> "Hellcraft map close-up", p.w(), p.h(), false);
			Minecraft.getInstance().getTextureManager().register(ID, texture);
		}
		NativeImage image = texture.getPixels();
		for (int y = 0; y < p.h(); y++) {
			for (int x = 0; x < p.w(); x++) {
				image.setPixel(x, y, pixels[y * p.w() + x]);
			}
		}
		texture.upload();
		shown = p;
	}

	/** True once a sharp picture is on show (for the tests). */
	static boolean ready() {
		return shown != null;
	}

	/** The map has closed: the picture goes. */
	static void close() {
		if (texture != null) {
			Minecraft.getInstance().getTextureManager().release(ID);
			texture = null;
		}
		shown = null;
	}

	static void closed() {
		cancel();
		close();
	}
}
