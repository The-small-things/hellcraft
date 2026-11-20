package net.thesmallthings.hellcraft.client.hud;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.client.combat.anim.Settings;
import net.thesmallthings.hellcraft.client.cutscene.CutscenePlayer;
import net.thesmallthings.hellcraft.client.dialogue.DialogueHud;
import net.thesmallthings.hellcraft.client.nav.ClientNav;
import net.thesmallthings.hellcraft.client.nav.ClientWaymarks;
import net.thesmallthings.hellcraft.client.nav.LayerCard;
import net.thesmallthings.hellcraft.client.nav.NavKeys;
import net.thesmallthings.hellcraft.entity.boss.BossEntity;
import net.thesmallthings.hellcraft.entity.monster.Mimic;
import net.thesmallthings.hellcraft.entity.npc.Angel;
import net.thesmallthings.hellcraft.entity.npc.Damned;
import net.thesmallthings.hellcraft.entity.npc.Shade;
import net.thesmallthings.hellcraft.net.Dialogue;
import net.thesmallthings.hellcraft.net.DialoguePayload;
import net.thesmallthings.hellcraft.net.WaymarksPayload;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.Navigation;
import net.thesmallthings.hellcraft.world.ParadisoGeometry;
import net.thesmallthings.hellcraft.world.ShrineSites;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The minimap, after Xaero's Minimap: the ground around you as a map draws it (MinimapScan), in a circle or a square, in a
 * corner of the screen (top left by default; HudLayout keeps it clear of everything else). It turns with you (forward is up)
 * or keeps north up; the N on its rim always shows north. On it: other players (white), monsters (red), bosses (a large red
 * mark), animals (yellow), and with height: what is far above or below you is fainter. Your waymarks, the Rests you have
 * found and the next way down (in Hell), and where you last died (anywhere), each pinned to the rim when off the map.
 * Under it, your coordinates. It knows every realm: Hell's circles tint its rim, the Forge of Dis is always seen as caves
 * (it has a roof), Purgatory and Paradiso have their own colours. Underground it switches to a cave view on its own.
 * <p>
 * Keys (Controls, "Hellcraft"): Z zooms in, X out, B marks where you stand (a waymark, in the Inferno), Y opens its settings
 * (shape, size, corner, turning, zoom, caves, what it shows). Also /hellcraft-client minimap.
 */
public final class Minimap implements HudElement {
	public static final Identifier ID = HellcraftMod.id("minimap");
	private static final Identifier TEXTURE = HellcraftMod.id("minimap_view");

	public static final KeyMapping ZOOM_IN = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.hellcraft.minimap_zoom_in", InputConstants.KEY_Z,
			NavKeys.CATEGORY));
	public static final KeyMapping ZOOM_OUT = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.hellcraft.minimap_zoom_out", InputConstants.KEY_X,
			NavKeys.CATEGORY));
	public static final KeyMapping MARK = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.hellcraft.minimap_mark", InputConstants.KEY_B,
			NavKeys.CATEGORY));
	public static final KeyMapping SETTINGS = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.hellcraft.minimap_settings", InputConstants.KEY_Y,
			NavKeys.CATEGORY));

	/** Under the map: one line of coordinates. */
	private static final int TEXT = 11;
	private static final int FRAME = 2;
	/** The texture is one texel per screen pixel, up to this (a 160 wide map at GUI scale 8). */
	private static final int MAX_TEXTURE = 1280;

	private static DynamicTexture texture;
	private static int textureSize;
	/** What the texture was last drawn for, so it is only drawn again when something changed. */
	private static double drawnX = Double.NaN;
	private static double drawnZ;
	private static float drawnYaw;
	private static String drawnFor = "";
	/** The ground around the player, one int per cell (a cell is 1, 2 or 4 blocks), copied from MinimapScan when it changes. */
	private static int[] grid = new int[0];
	private static int gridX = Integer.MIN_VALUE;
	private static int gridZ;
	private static int gridW;
	private static int gridCell;
	private static int gridChanges = -1;
	private static int ticks;
	private static int zoomShownAt = -100;
	/** Where this player last died, and in which dimension (kept for the session). */
	private static Vec3 death;
	private static ResourceKey<Level> deathLevel;
	private static boolean wasDead;

	/** Loads the class, registering the keys. */
	public static void register() {
	}

	private static Settings.MapPrefs prefs() {
		return Settings.minimap();
	}

	/** Is the minimap showing now (setting on, a world, no cutscene)? */
	public static boolean showing(Minecraft mc) {
		return prefs().on && mc.player != null && mc.level != null && !CutscenePlayer.active();
	}

	/** Should it be in its cave view now? */
	public static boolean caves(Minecraft mc) {
		return switch (prefs().caves) {
			case ON -> true;
			case OFF -> false;
			case AUTO -> mc.level != null && mc.player != null && MinimapScan.underground(mc.level, mc.player);
		};
	}

	/** The GUI scale (screen pixels per GUI pixel). */
	private static int guiScale() {
		return Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
	}

	/**
	 * The map's half width in blocks, its corners included (so a turning map has ground in its corners), but no further than
	 * the chunks the game can have loaded.
	 */
	private static int reach() {
		Settings.MapPrefs p = prefs();
		int wanted = (int) Math.ceil(p.size / 2.0 * p.blocksPerPixel(guiScale()) * 1.42) + 2;
		int loaded = (Minecraft.getInstance().options.renderDistance().get() + 2) * 16;
		return Math.min(wanted, loaded);
	}

	public static void tick(Minecraft mc) {
		ticks++;
		LocalPlayer player = mc.player;
		if (player != null && mc.gui.screen() == null) {
			while (ZOOM_IN.consumeClick()) {
				zoom(-1);
			}
			while (ZOOM_OUT.consumeClick()) {
				zoom(1);
			}
			while (MARK.consumeClick()) {
				mark(mc, player);
			}
			while (SETTINGS.consumeClick()) {
				mc.gui.setScreen(new MinimapScreen(null));
			}
		}
		if (player != null && mc.level != null) {
			boolean dead = player.isDeadOrDying();
			if (dead && !wasDead) {
				death = player.position();
				deathLevel = mc.level.dimension();
			}
			wasDead = dead;
		}
		if (showing(mc) && !mc.isPaused()) {
			MinimapScan.tick(mc.level, player, reach(), caves(mc));
		}
		if (ticks % 100 == 0) {
			MobHeads.tidy(ticks);
		}
	}

	/** Holding the player list key (Tab) shows heads on the map instead of dots, as in Xaero's. */
	public static boolean heads(Minecraft mc) {
		return forceHeads || mc.options.keyPlayerList.isDown();
	}

	/** For the client test: show the heads without holding Tab. */
	public static boolean forceHeads;
	private static int headsDrawn;

	/** For the client test: how many heads the last frame drew. */
	public static int headsDrawn() {
		return headsDrawn;
	}

	/** Zooms one step in (-1) or out (+1). */
	public static void zoom(int step) {
		Settings.MapPrefs p = prefs();
		int z = Mth.clamp(p.zoom + step, 0, Settings.MapPrefs.ZOOMS.length - 1);
		if (z != p.zoom) {
			p.zoom = z;
			Settings.save();
		}
		zoomShownAt = ticks;
	}

	/** B: a waymark where the player stands (the Map of Hell's waymarks, so only in an Inferno world's overworld). */
	private static void mark(Minecraft mc, LocalPlayer player) {
		if (!ClientNav.inHell(mc) || !ClientWaymarks.canSend()) {
			status("Waymarks belong to the Map of Hell, so you can only mark a spot in Hell's circles.");
			return;
		}
		int x = player.getBlockX();
		int z = player.getBlockZ();
		String name = InfernoGeometry.regionName(x, z);
		ClientWaymarks.save(0, name.length() > 24 ? name.substring(0, 24) : name, x, z, 2, false);
		status("Marked this spot (" + x + ", " + z + "). Rename or share it on the Map of Hell (M).");
	}

	private static void status(String text) {
		DialogueHud.receive(new DialoguePayload(Dialogue.Kind.STATUS.ordinal(), "", 0xC8B070, Component.literal(text), false));
	}

	public static void reset() {
		MinimapScan.clear();
		MinimapColours.clear();
		MobHeads.clear();
		death = null;
		deathLevel = null;
		drawnX = Double.NaN;
		gridX = Integer.MIN_VALUE;
	}

	/** For the HUD layout: the map, its frame and the line of coordinates under it. */
	public static HudLayout.Size measure(Minecraft mc, int width, int height) {
		if (!showing(mc) || mc.gui.hud.isHidden()) {
			return null;
		}
		Settings.MapPrefs p = prefs();
		int size = Math.min(p.size, Math.min(width, height) / 2);
		return new HudLayout.Size(size + FRAME * 2, size + FRAME * 2 + (p.coordinates ? TEXT : 0));
	}

	/** The corner it lives in, as a HudLayout anchor. */
	public static HudLayout.Anchor anchor() {
		return switch (prefs().corner) {
			case TOP_LEFT -> HudLayout.Anchor.TOP_LEFT;
			case TOP_RIGHT -> HudLayout.Anchor.TOP_RIGHT;
			case BOTTOM_LEFT -> HudLayout.Anchor.BOTTOM_LEFT;
			case BOTTOM_RIGHT -> HudLayout.Anchor.BOTTOM_RIGHT;
		};
	}

	// ------------------------------------------------------------------ drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		HudLayout.Box box = HudLayout.box(ID);
		if (box == null || !showing(mc)) {
			return;
		}
		Settings.MapPrefs p = prefs();
		LocalPlayer player = mc.player;
		float partial = delta.getGameTimeDeltaPartialTick(true);
		Vec3 pos = player.getPosition(partial);
		float yaw = p.rotate ? player.getViewYRot(partial) : 180f;
		int size = box.w() - FRAME * 2;
		int x0 = box.x() + FRAME;
		int y0 = box.y() + FRAME;
		float half = size / 2f;
		float cx = x0 + half;
		float cy = y0 + half;
		boolean circle = p.shape == Settings.Shape.CIRCLE;
		int scale = guiScale();
		float bpp = p.blocksPerPixel(scale);
		int rim = rimColour(mc, pos);

		// the ground, drawn into a texture with one texel for each of the screen's pixels, only when something changed
		int res = Math.min(MAX_TEXTURE, size * scale);
		draw(res, pos, yaw, p.blocksPerScreenPixel(), circle, p.rotate);
		g.fill(x0, y0, x0 + size, y0 + size, circle ? 0 : 0xC0080606);
		g.blit(TEXTURE, x0, y0, x0 + size, y0 + size, 0, 1, 0, 1);

		// the frame: dark iron with the realm's colour on its rim
		frame(g, x0, y0, size, circle, rim);

		// what is on the map: places first, then creatures, then you on top
		Rot r = new Rot(yaw);
		if (p.waymarks) {
			places(g, mc, pos, r, cx, cy, half, bpp, circle);
		}
		creatures(g, mc, player, pos, r, cx, cy, half, bpp, circle, partial);
		you(g, cx, cy, !p.rotate, player.getViewYRot(partial));
		north(g, mc.font, r, cx, cy, half, circle);

		// the zoom, for a moment after it changes; the cave view's mark
		Font font = mc.font;
		if (ticks - zoomShownAt < 40) {
			String z = bpp >= 1 ? "1:" + trim(bpp) : trim(1 / bpp) + ":1";
			g.text(font, z, x0 + size - font.width(z) - 2, y0 + 2, 0xFFE8E8E8, true);
		}
		if (MinimapScan.cave()) {
			g.text(font, "Caves", x0 + 3, y0 + size - 10, 0xFFB0A090, true);
		}
		if (p.coordinates) {
			String coords = String.format(Locale.ROOT, "%d, %d, %d", Mth.floor(pos.x), Mth.floor(pos.y), Mth.floor(pos.z));
			int tw = font.width(coords);
			int tx = box.x() + (box.w() - tw) / 2;
			int ty = box.y() + box.h() - TEXT + 2;
			g.fill(tx - 3, ty - 2, tx + tw + 2, ty + 9, ARGB.black(120));
			g.text(font, coords, tx, ty, 0xFFE8E8E8, false);
		}
	}

	private static String trim(float v) {
		return v == Math.round(v) ? Integer.toString(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
	}

	/** The rim's colour: the circle's in Hell, the Forge's fire, Purgatory's dawn, the sphere's in Paradiso, else iron. */
	private static int rimColour(Minecraft mc, Vec3 pos) {
		if (ClientNav.inHell(mc)) {
			return ARGB.opaque(LayerCard.accent(InfernoGeometry.circleAt(pos.x, pos.z)));
		}
		if (ClientNav.inForge(mc)) {
			return 0xFFFF8A30;
		}
		if (ClientNav.inParadiso(mc)) {
			return ARGB.opaque(ParadisoGeometry.sphereAtY(pos.y).colour);
		}
		if (mc.level.dimension() == net.thesmallthings.hellcraft.world.Purgatory.LEVEL) {
			return 0xFFE8C890;
		}
		if (mc.level.dimension() == Level.NETHER) {
			return 0xFFB03020;
		}
		if (mc.level.dimension() == Level.END) {
			return 0xFFC8B0E0;
		}
		return 0xFF9A9A9A;
	}

	/**
	 * Draws the ground into the texture, if anything changed since it was last drawn. One texel is one screen pixel and
	 * {@code bpt} blocks (1/8 to 4), so a block is always a whole number of pixels: with north up, the map is moved in whole
	 * texels (where the player stands is rounded to the texel), so every block keeps its exact size and edges as you walk;
	 * turning with you, each pixel takes the one block under it (no blur). Zoomed out, a texel is the average of 2x2 or 4x4
	 * columns, worked out once per chunk, so the map doesn't shimmer.
	 */
	private static void draw(int res, Vec3 pos, float yaw, float bpt, boolean circle, boolean turning) {
		int cell = bpt >= 4 ? 4 : bpt >= 2 ? 2 : 1;
		double step = bpt / cell;
		int half = res / 2;
		// where the map's middle is, in cells; with north up, rounded to the texel so blocks stay put on the pixel grid
		double mx = pos.x / cell;
		double mz = pos.z / cell;
		if (!turning) {
			mx = Math.round(mx / step) * step;
			mz = Math.round(mz / step) * step;
		}
		int radius = (int) Math.min(Math.ceil(half * 1.42 * step) + 2, reach() / (double) cell + 2);
		int gx0 = Mth.floor(mx) - radius;
		int gz0 = Mth.floor(mz) - radius;
		int w = radius * 2 + 1;
		boolean newGrid = gx0 != gridX || gz0 != gridZ || w != gridW || cell != gridCell || gridChanges != MinimapScan.changes();
		if (newGrid) {
			if (grid.length < w * w) {
				grid = new int[w * w];
			}
			MinimapScan.fill(grid, gx0, gz0, w, w, cell);
			gridX = gx0;
			gridZ = gz0;
			gridW = w;
			gridCell = cell;
			gridChanges = MinimapScan.changes();
		}
		String key = res + ":" + bpt + ":" + circle + ":" + turning;
		boolean moved = Double.isNaN(drawnX) || (turning ? Math.abs(mx - drawnX) > step * 0.4 || Math.abs(mz - drawnZ) > step * 0.4
				|| Math.abs(Mth.wrapDegrees(yaw - drawnYaw)) > 0.25f : mx != drawnX || mz != drawnZ);
		if (texture != null && textureSize == res && !moved && !newGrid && key.equals(drawnFor)) {
			return;
		}
		if (texture == null || textureSize != res) {
			if (texture != null) {
				Minecraft.getInstance().getTextureManager().release(TEXTURE);
			}
			texture = new DynamicTexture(() -> "Hellcraft minimap", res, res, true);
			Minecraft.getInstance().getTextureManager().register(TEXTURE, texture);
			textureSize = res;
		}
		drawnX = mx;
		drawnZ = mz;
		drawnYaw = yaw;
		drawnFor = key;
		NativeImage image = texture.getPixels();
		Rot r = new Rot(turning ? yaw : 180f);
		// a texel's step across the world, in cells, along the texture's rows and down its columns
		double ax = r.rx * step;
		double az = r.rz * step;
		double bx = -r.fx * step;
		double bz = -r.fz * step;
		double edge = res / 2.0;
		int unknown = 0xFF100C0C;
		for (int ty = 0; ty < res; ty++) {
			double oy = ty + 0.5 - half;
			double wx = mx + (0.5 - half) * ax + oy * bx - gx0;
			double wz = mz + (0.5 - half) * az + oy * bz - gz0;
			double dy = ty + 0.5 - edge;
			for (int tx = 0; tx < res; tx++, wx += ax, wz += az) {
				float alpha = 1f;
				if (circle) {
					double dx = tx + 0.5 - edge;
					double d = Math.sqrt(dx * dx + dy * dy);
					if (d > edge) {
						image.setPixel(tx, ty, 0);
						continue;
					}
					alpha = (float) Math.min(1, edge - d);
				}
				int ix = (int) Math.floor(wx);
				int iz = (int) Math.floor(wz);
				int c = ix < 0 || iz < 0 || ix >= w || iz >= w ? MinimapScan.UNKNOWN : grid[iz * w + ix];
				if (c == MinimapScan.UNKNOWN) {
					c = unknown;
				}
				if (alpha < 1f) {
					c = ARGB.color(Math.round(alpha * 255), c & 0xFFFFFF);
				}
				image.setPixel(tx, ty, c);
			}
		}
		texture.upload();
	}

	/** The map's turn: the world's directions for the screen's right (r) and up (f, the way the player faces). */
	private record Rot(float rx, float rz, float fx, float fz) {
		Rot(float yaw) {
			this(-(float) Math.cos(Math.toRadians(yaw)), -(float) Math.sin(Math.toRadians(yaw)),
					-(float) Math.sin(Math.toRadians(yaw)), (float) Math.cos(Math.toRadians(yaw)));
		}

		/** A world offset (blocks) to screen offset (GUI pixels at 1 block a pixel): x right, y down. */
		float sx(double dx, double dz) {
			return (float) (dx * rx + dz * rz);
		}

		float sy(double dx, double dz) {
			return (float) -(dx * fx + dz * fz);
		}
	}

	private static void frame(GuiGraphicsExtractor g, int x0, int y0, int size, boolean circle, int rim) {
		if (!circle) {
			g.outline(x0 - 2, y0 - 2, size + 4, size + 4, 0xFF140C0C);
			g.outline(x0 - 1, y0 - 1, size + 2, size + 2, ARGB.multiplyAlpha(rim, 0.85f));
			return;
		}
		float r = size / 2f;
		float cx = x0 + r;
		float cy = y0 + r;
		for (int row = -Math.round(r) - 2; row <= Math.round(r) + 2; row++) {
			float yy = row + 0.5f;
			int outer = halfWidth(r + 2, yy);
			int mid = halfWidth(r + 1, yy);
			int inner = halfWidth(r, yy);
			int y = Math.round(cy + row);
			if (outer < 0) {
				continue;
			}
			// the dark outer ring, then the coloured ring inside it
			ring(g, cx, y, outer, mid, 0xFF140C0C);
			ring(g, cx, y, mid, inner, ARGB.multiplyAlpha(rim, 0.85f));
		}
	}

	/** A row's two pieces of a ring between half widths {@code outer} and {@code inner} (inner -1: no hole). */
	private static void ring(GuiGraphicsExtractor g, float cx, int y, int outer, int inner, int colour) {
		if (outer < 0) {
			return;
		}
		int c = Math.round(cx);
		if (inner < 0) {
			g.fill(c - outer, y, c + outer, y + 1, colour);
			return;
		}
		if (outer > inner) {
			g.fill(c - outer, y, c - inner, y + 1, colour);
			g.fill(c + inner, y, c + outer, y + 1, colour);
		}
	}

	private static int halfWidth(float r, float yy) {
		float s = r * r - yy * yy;
		return s < 0 ? -1 : Math.round((float) Math.sqrt(s));
	}

	/** Puts a screen offset on the map, or on its rim if it falls off; returns {x, y, 1 if pinned to the rim}. */
	private static float[] onMap(float sx, float sy, float half, boolean circle) {
		float limit = half - 3;
		if (circle) {
			float d = (float) Math.sqrt(sx * sx + sy * sy);
			if (d <= limit) {
				return new float[] {sx, sy, 0};
			}
			return new float[] {sx / d * limit, sy / d * limit, 1};
		}
		float m = Math.max(Math.abs(sx), Math.abs(sy));
		if (m <= limit) {
			return new float[] {sx, sy, 0};
		}
		return new float[] {sx / m * limit, sy / m * limit, 1};
	}

	/** Waymarks, Rests, the way down and where you died. */
	private static void places(GuiGraphicsExtractor g, Minecraft mc, Vec3 pos, Rot r, float cx, float cy, float half, float bpp, boolean circle) {
		if (ClientNav.inHell(mc)) {
			for (ShrineSites.Site site : ShrineSites.all()) {
				double dx = site.x() + 0.5 - pos.x;
				double dz = site.z() + 0.5 - pos.z;
				if (dx * dx + dz * dz > (half * bpp * 1.5) * (half * bpp * 1.5) && !ClientNav.found(site.id())) {
					continue;
				}
				float[] at = onMap(r.sx(dx, dz) / bpp, r.sy(dx, dz) / bpp, half, circle);
				if (at[2] == 1 && !ClientNav.found(site.id())) {
					continue;
				}
				diamond(g, Math.round(cx + at[0]), Math.round(cy + at[1]), ARGB.multiplyAlpha(ClientNav.found(site.id()) ? 0xFFF0C040 : 0xFF9AA8B8, at[2] == 1 ? 0.6f : 1f));
			}
			Navigation.Target down = Navigation.wayDown(pos.x, pos.z);
			double dx = down.x() - pos.x;
			double dz = down.z() - pos.z;
			float[] at = onMap(r.sx(dx, dz) / bpp, r.sy(dx, dz) / bpp, half, circle);
			arrow(g, Math.round(cx + at[0]), Math.round(cy + at[1]) - 2, ARGB.multiplyAlpha(0xFFE03030, at[2] == 1 ? 0.7f : 1f));
			WaymarksPayload.Entry followed = ClientWaymarks.followed();
			for (WaymarksPayload.Entry mark : ClientWaymarks.all()) {
				double mx = mark.x() + 0.5 - pos.x;
				double mz = mark.z() + 0.5 - pos.z;
				float[] m = onMap(r.sx(mx, mz) / bpp, r.sy(mx, mz) / bpp, half, circle);
				boolean follow = followed != null && followed.id() == mark.id();
				if (m[2] == 1 && !follow) {
					continue;
				}
				pin(g, Math.round(cx + m[0]), Math.round(cy + m[1]), ARGB.multiplyAlpha(ClientWaymarks.colour(mark), m[2] == 1 ? 0.75f : 1f));
			}
		}
		if (death != null && mc.level.dimension() == deathLevel) {
			double dx = death.x - pos.x;
			double dz = death.z - pos.z;
			float[] at = onMap(r.sx(dx, dz) / bpp, r.sy(dx, dz) / bpp, half, circle);
			cross(g, Math.round(cx + at[0]), Math.round(cy + at[1]), at[2] == 1 ? 0xB0D0D0D0 : 0xFFE8E8E8);
		}
	}

	/** Players, monsters, bosses and animals within the map, fainter the further above or below you. */
	private static void creatures(GuiGraphicsExtractor g, Minecraft mc, LocalPlayer self, Vec3 pos, Rot r, float cx, float cy, float half,
			float bpp, boolean circle, float partial) {
		Settings.MapPrefs p = prefs();
		double reach = half * bpp * (circle ? 1 : 1.42);
		int drawn = 0;
		boolean heads = heads(mc);
		headsDrawn = 0;
		net.minecraft.world.phys.AABB around = self.getBoundingBox().inflate(reach, 96, reach);
		for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, around)) {
			if (e == self || e.isRemoved() || e.isInvisible() || !(e instanceof Mob || e instanceof Player)
					|| e instanceof Damned || e instanceof Shade || e instanceof Angel || e instanceof Mimic m && m.dormant()) {
				continue;
			}
			boolean boss = e instanceof BossEntity || e instanceof EnderDragon || e instanceof WitherBoss;
			boolean player = e instanceof Player;
			boolean monster = !player && (boss || e instanceof Enemy);
			if (player && !p.players || monster && !p.monsters || !player && !monster && !p.animals) {
				continue;
			}
			Vec3 at = e.getPosition(partial);
			double dx = at.x - pos.x;
			double dz = at.z - pos.z;
			if (Math.abs(dx) > reach || Math.abs(dz) > reach) {
				continue;
			}
			float sx = r.sx(dx, dz) / bpp;
			float sy = r.sy(dx, dz) / bpp;
			float limit = half - 1.5f;
			if (circle ? sx * sx + sy * sy > limit * limit : Math.max(Math.abs(sx), Math.abs(sy)) > limit) {
				if (!boss) {
					continue;
				}
				// a boss off the map still shows, on the rim
				float[] rim = onMap(sx, sy, half, circle);
				sx = rim[0];
				sy = rim[1];
			}
			float dy = (float) Math.abs(at.y - pos.y);
			float a = dy < 8 ? 1f : Math.max(0.35f, 1f - (dy - 8) / 24f);
			int x = Math.round(cx + sx);
			int y = Math.round(cy + sy);
			if (heads && MobHeads.draw(g, e, x, y, boss ? 12 : 8, partial, ticks,
					boss ? ARGB.color(a, 0xB01010) : player ? ARGB.color(a, 0xE8E8E8) : ARGB.color(a * 0.8f, 0x000000))) {
				headsDrawn++;
			} else if (boss) {
				float beat = 0.7f + 0.3f * Mth.sin((ticks + partial) * 0.4f);
				g.fill(x - 3, y - 3, x + 3, y + 3, ARGB.color(a, 0x100000));
				g.fill(x - 2, y - 2, x + 2, y + 2, ARGB.color(a * beat, 0xFF2020));
			} else if (player) {
				g.fill(x - 2, y - 2, x + 2, y + 2, ARGB.color(a, 0x000000));
				g.fill(x - 1, y - 1, x + 1, y + 1, ARGB.color(a, 0xFFFFFF));
			} else {
				int colour = monster ? 0xE83A30 : 0xE8D860;
				g.fill(x - 1, y - 1, x + 2, y + 2, ARGB.color(a * 0.7f, 0x000000));
				g.fill(x - 1, y - 1, x + 1, y + 1, ARGB.color(a, colour));
			}
			if (++drawn >= 200) {
				break;
			}
		}
	}

	/** You: a white arrow in the middle, pointing up when the map turns with you, or the way you face when north is up. */
	private static void you(GuiGraphicsExtractor g, float cx, float cy, boolean turn, float yaw) {
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		if (turn) {
			g.pose().rotate((float) Math.toRadians(yaw + 180));
		}
		for (int pass = 0; pass < 2; pass++) {
			int colour = pass == 0 ? 0xFF000000 : 0xFFFFFFFF;
			int grow = pass == 0 ? 1 : 0;
			for (int row = 0; row < 4; row++) {
				g.fill(-row - grow, -4 + row - grow, row + 1 + grow, -3 + row + grow, colour);
			}
			g.fill(-1 - grow, 0, 2 + grow, 3 + grow, colour);
		}
		g.pose().popMatrix();
	}

	/** N on the rim, wherever north is; small ticks for east, south and west. */
	private static void north(GuiGraphicsExtractor g, Font font, Rot r, float cx, float cy, float half, boolean circle) {
		for (int i = 0; i < 4; i++) {
			double dx = i == 1 ? 1 : i == 3 ? -1 : 0;
			double dz = i == 0 ? -1 : i == 2 ? 1 : 0;
			float sx = r.sx(dx, dz);
			float sy = r.sy(dx, dz);
			float[] at;
			if (circle) {
				at = new float[] {sx * half, sy * half};
			} else {
				float m = Math.max(Math.abs(sx), Math.abs(sy));
				at = new float[] {sx / m * half, sy / m * half};
			}
			int x = Math.round(cx + at[0]);
			int y = Math.round(cy + at[1]);
			if (i == 0) {
				g.fill(x - 4, y - 5, x + 4, y + 4, 0xE0140C0C);
				g.text(font, "N", x - 2, y - 4, 0xFFFF5040, false);
			} else {
				g.fill(x - 1, y - 1, x + 1, y + 1, 0xFFD0C8C0);
			}
		}
	}

	private static void diamond(GuiGraphicsExtractor g, int x, int y, int colour) {
		g.fill(x - 1, y - 3, x + 2, y + 4, 0xC0000000);
		g.fill(x - 3, y - 1, x + 4, y + 2, 0xC0000000);
		for (int row = -2; row <= 2; row++) {
			int w = 2 - Math.abs(row);
			g.fill(x - w, y + row, x + w + 1, y + row + 1, colour);
		}
	}

	private static void arrow(GuiGraphicsExtractor g, int x, int y, int colour) {
		g.fill(x - 4, y - 1, x + 5, y + 5, 0x90000000);
		for (int row = 0; row < 4; row++) {
			int w = 3 - row;
			g.fill(x - w, y + row, x + w + 1, y + row + 1, colour);
		}
	}

	private static void pin(GuiGraphicsExtractor g, int x, int y, int colour) {
		g.fill(x - 2, y - 6, x + 3, y - 1, 0xFF000000);
		g.fill(x - 1, y - 5, x + 2, y - 2, colour);
		g.fill(x, y - 1, x + 1, y + 1, 0xFF000000);
	}

	private static void cross(GuiGraphicsExtractor g, int x, int y, int colour) {
		for (int i = -2; i <= 2; i++) {
			g.fill(x + i - 1, y + i - 1, x + i + 2, y + i + 2, 0x90000000);
			g.fill(x + i - 1, y - i - 1, x + i + 2, y - i + 2, 0x90000000);
		}
		for (int i = -2; i <= 2; i++) {
			g.fill(x + i, y + i, x + i + 1, y + i + 1, colour);
			g.fill(x + i, y - i, x + i + 1, y - i + 1, colour);
		}
	}

	/** A text line for /hellcraft-client minimap: how it is set now. */
	public static String describe() {
		Settings.MapPrefs p = prefs();
		if (!p.on) {
			return "The minimap is hidden. Show it with /hellcraft-client minimap on (or its settings, key Y).";
		}
		return "Minimap: " + p.shape.name().toLowerCase(Locale.ROOT) + ", " + p.size + " wide, "
				+ (p.rotate ? "turning with you" : "north up") + ", " + p.corner.name().toLowerCase(Locale.ROOT).replace('_', ' ')
				+ ", caves " + p.caves.name().toLowerCase(Locale.ROOT) + ". Z and X zoom, B marks a spot, Y opens its settings.";
	}

	/** For the client test: the texture's colour at the map's middle (ARGB), or 0 if it has not drawn. */
	public static int middleColour() {
		if (texture == null) {
			return 0;
		}
		NativeImage image = texture.getPixels();
		return image == null ? 0 : image.getPixel(textureSize / 2, textureSize / 2);
	}

	/**
	 * For the client test, with north up and a zoom of several pixels a block: are all blocks the same whole number of texels?
	 * Along the middle row and column, every change of colour must fall on the same step of that grid. Null if so.
	 */
	@Nullable
	public static String texelCheck() {
		Settings.MapPrefs p = prefs();
		float bpt = p.blocksPerScreenPixel();
		if (texture == null || p.rotate || bpt >= 1) {
			return "not north up and zoomed in (" + p.rotate + ", " + bpt + ")";
		}
		int k = Math.round(1 / bpt);
		NativeImage image = texture.getPixels();
		int n = textureSize;
		int lo = n / 4;
		int hi = n * 3 / 4;
		for (int axis = 0; axis < 2; axis++) {
			int residue = -1;
			int changes = 0;
			for (int i = lo + 1; i < hi; i++) {
				int a = axis == 0 ? image.getPixel(i - 1, n / 2) : image.getPixel(n / 2, i - 1);
				int b = axis == 0 ? image.getPixel(i, n / 2) : image.getPixel(n / 2, i);
				if (a == b) {
					continue;
				}
				changes++;
				if (residue < 0) {
					residue = i % k;
				} else if (i % k != residue) {
					return (axis == 0 ? "row" : "column") + " changes at " + i + ", off the " + k + "-texel grid (" + residue + ")";
				}
			}
			if (changes == 0) {
				return "nothing to see along the " + (axis == 0 ? "row" : "column") + " (all one colour)";
			}
		}
		return null;
	}

	@Nullable
	public static Vec3 death() {
		return death;
	}
}
