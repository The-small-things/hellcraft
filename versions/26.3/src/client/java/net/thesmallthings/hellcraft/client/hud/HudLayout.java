package net.thesmallthings.hellcraft.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.client.mixin.BossOverlayAccessor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Where every piece of Hellcraft's HUD goes, worked out afresh each frame so that none of them is ever drawn over another,
 * or over the game's own HUD, however much is happening at once.
 * <p>
 * Each element registers a {@link Slot}: the corner or edge it belongs to ({@link Anchor}), its place in the queue (lower
 * goes first and keeps its spot) and a {@link Measure} that says how big it is right now (or that it isn't showing). Once a
 * frame, before any HUD is drawn, {@link #solve} first marks what the game itself draws (the hotbar, hearts, armour, food and
 * air, the offhand slot, boss bars, the status effect icons and the scoreboard sidebar), then places the slots in order:
 * each starts at its anchor, below (or above, at the bottom) whatever its anchor already holds, and if it would cover anything
 * placed before it, it moves on past it, along its anchor's direction first and then inwards from its edge. An element that
 * can't fit anywhere on screen is left out for that frame rather than drawn on top of another. Elements ask for their box
 * with {@link #box}; it eases to a new spot instead of jumping when something above it comes or goes.
 */
public final class HudLayout implements HudElement {
	public static final Identifier ID = HellcraftMod.id("hud_layout");
	public static final HudLayout INSTANCE = new HudLayout();
	/** The space kept between two elements. */
	public static final int GAP = 3;
	/** How far from the screen's edge the corner elements sit. */
	public static final int MARGIN = 4;

	/** Where an element belongs, and which way it gives ground. */
	public enum Anchor {
		/** Top left corner, stacking down. */
		TOP_LEFT,
		/** Top middle, stacking down (under the boss bars). */
		TOP_CENTER,
		/** Top right corner, stacking down (under the status effects). */
		TOP_RIGHT,
		/** Bottom left corner, stacking up (the chat's corner: only for elements the player put there). */
		BOTTOM_LEFT,
		/** Bottom middle, stacking up above the hotbar, hearts and food. */
		BOTTOM_CENTER,
		/** Bottom right corner, stacking up. */
		BOTTOM_RIGHT,
		/** Over the food bar (the right half of the hotbar), stacking up: the stamina bar and the oath meter. */
		ABOVE_FOOD;

		boolean down() {
			return this == TOP_LEFT || this == TOP_CENTER || this == TOP_RIGHT;
		}

		boolean right() {
			return this == TOP_RIGHT || this == BOTTOM_RIGHT || this == ABOVE_FOOD;
		}

		boolean centred() {
			return this == TOP_CENTER || this == BOTTOM_CENTER;
		}
	}

	/** A rectangle on the GUI's scale. */
	public record Box(int x, int y, int w, int h) {
		public int right() {
			return x + w;
		}

		public int bottom() {
			return y + h;
		}

		boolean hits(Box o, int gap) {
			return x < o.x + o.w + gap && o.x < x + w + gap && y < o.y + o.h + gap && o.y < y + h + gap;
		}

		boolean onScreen(int width, int height) {
			return x >= 0 && y >= 0 && x + w <= width && y + h <= height;
		}
	}

	/** An element's size this frame. */
	public record Size(int w, int h) {
	}

	/** How big an element is now, on a screen this size; null when it isn't showing. */
	@FunctionalInterface
	public interface Measure {
		@Nullable
		Size measure(Minecraft mc, int width, int height);
	}

	private record Slot(Identifier id, Supplier<Anchor> anchor, int order, Measure measure) {
	}

	private static final List<Slot> SLOTS = new ArrayList<>();
	private static final Map<Identifier, Box> PLACED = new HashMap<>();
	/** Where each element is shown, easing toward where it was placed: x, y. */
	private static final Map<Identifier, float[]> SHOWN = new HashMap<>();
	/** What the game itself drew this frame, for the client test and for debugging. */
	private static final List<Box> RESERVED = new ArrayList<>();
	private static long lastNanos;

	private HudLayout() {
	}

	/**
	 * Adds an element to the layout: its anchor, and its place in the queue (lower first). The anchors' elements are placed
	 * in this order across the whole screen, so a low number keeps its spot when space runs short.
	 */
	public static void register(Identifier id, Supplier<Anchor> anchor, int order, Measure measure) {
		SLOTS.removeIf(s -> s.id.equals(id));
		SLOTS.add(new Slot(id, anchor, order, measure));
		SLOTS.sort(Comparator.comparingInt(Slot::order));
	}

	/** Where an element goes this frame, or null if it isn't showing (or found no room). */
	@Nullable
	public static Box box(Identifier id) {
		Box placed = PLACED.get(id);
		if (placed == null) {
			return null;
		}
		float[] shown = SHOWN.get(id);
		return shown == null ? placed : new Box(Math.round(shown[0]), Math.round(shown[1]), placed.w(), placed.h());
	}

	/** Where an element was placed, without the easing (tests). */
	@Nullable
	public static Box target(Identifier id) {
		return PLACED.get(id);
	}

	/** Every placed element and the game's own HUD parts this frame: for the client test's overlap check. */
	public static List<Box> everything() {
		List<Box> all = new ArrayList<>(RESERVED);
		all.addAll(PLACED.values());
		return all;
	}

	/** True if no two placed elements (or an element and the game's HUD) overlap this frame: for the client test. */
	public static boolean clear() {
		List<Box> placed = new ArrayList<>(PLACED.values());
		for (int i = 0; i < placed.size(); i++) {
			for (int j = i + 1; j < placed.size(); j++) {
				if (placed.get(i).hits(placed.get(j), 0)) {
					return false;
				}
			}
			for (Box r : RESERVED) {
				if (placed.get(i).hits(r, 0)) {
					return false;
				}
			}
		}
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
		solve(Minecraft.getInstance(), g.guiWidth(), g.guiHeight());
	}

	/** Places every element for this frame. */
	public static void solve(Minecraft mc, int width, int height) {
		long nanos = System.nanoTime();
		float dt = lastNanos == 0 ? 1 : Math.min(0.25f, (nanos - lastNanos) / 1.0e9f);
		lastNanos = nanos;
		RESERVED.clear();
		Map<Anchor, Integer> cursor = new HashMap<>();
		reserveVanilla(mc, width, height, cursor);
		List<Box> taken = new ArrayList<>(RESERVED);
		PLACED.clear();
		for (Slot slot : SLOTS) {
			Size size;
			try {
				size = slot.measure.measure(mc, width, height);
			} catch (RuntimeException e) {
				size = null;
			}
			if (size == null || size.w() <= 0 || size.h() <= 0) {
				continue;
			}
			Anchor anchor = slot.anchor.get();
			Box box = place(anchor, size, width, height, cursor, taken);
			if (box == null) {
				continue;
			}
			taken.add(box);
			PLACED.put(slot.id, box);
			// the anchor's next element goes past this one
			if (anchor.down()) {
				cursor.put(anchor, Math.max(cursor.getOrDefault(anchor, MARGIN), box.bottom() + GAP));
			} else {
				cursor.put(anchor, Math.min(cursor.getOrDefault(anchor, height - MARGIN), box.y() - GAP));
			}
		}
		// ease each shown position toward its place (snapping when it is new or far)
		float k = 1 - (float) Math.exp(-dt * 18);
		SHOWN.keySet().removeIf(id -> !PLACED.containsKey(id));
		for (Map.Entry<Identifier, Box> e : PLACED.entrySet()) {
			Box b = e.getValue();
			float[] s = SHOWN.get(e.getKey());
			if (s == null || Math.abs(s[0] - b.x()) > 80 || Math.abs(s[1] - b.y()) > 80) {
				SHOWN.put(e.getKey(), new float[] {b.x(), b.y()});
			} else {
				s[0] += (b.x() - s[0]) * k;
				s[1] += (b.y() - s[1]) * k;
				if (Math.abs(s[0] - b.x()) < 0.3f) {
					s[0] = b.x();
				}
				if (Math.abs(s[1] - b.y()) < 0.3f) {
					s[1] = b.y();
				}
			}
		}
	}

	/** The spot for an element: its anchor's next place, moved past anything it would cover; null if there is none. */
	@Nullable
	private static Box place(Anchor anchor, Size size, int width, int height, Map<Anchor, Integer> cursor, List<Box> taken) {
		int w = size.w();
		int h = size.h();
		int x = switch (anchor) {
			case TOP_LEFT, BOTTOM_LEFT -> MARGIN;
			case TOP_CENTER, BOTTOM_CENTER -> (width - w) / 2;
			case TOP_RIGHT, BOTTOM_RIGHT -> width - MARGIN - w;
			case ABOVE_FOOD -> width / 2 + 91 - w;
		};
		int y = anchor.down() ? cursor.getOrDefault(anchor, MARGIN) : cursor.getOrDefault(anchor, height - MARGIN) - h;
		Box start = new Box(x, y, w, h);
		// first along the anchor's own direction
		Box along = slide(start, anchor, false, width, height, taken);
		if (along != null) {
			return along;
		}
		// then in from its edge, at its first height
		if (!anchor.centred()) {
			return slide(start, anchor, true, width, height, taken);
		}
		return null;
	}

	/** Moves a box past whatever it covers, down/up ({@code sideways} false) or in from its edge (true), until it is clear. */
	@Nullable
	private static Box slide(Box box, Anchor anchor, boolean sideways, int width, int height, List<Box> taken) {
		for (int tries = 0; tries < 24; tries++) {
			if (!box.onScreen(width, height)) {
				return null;
			}
			Box hit = null;
			for (Box t : taken) {
				if (box.hits(t, GAP - 1)) {
					hit = t;
					break;
				}
			}
			if (hit == null) {
				return box;
			}
			if (sideways) {
				box = anchor.right() ? new Box(hit.x() - GAP - box.w(), box.y(), box.w(), box.h())
						: new Box(hit.right() + GAP, box.y(), box.w(), box.h());
			} else {
				box = anchor.down() ? new Box(box.x(), hit.bottom() + GAP, box.w(), box.h())
						: new Box(box.x(), hit.y() - GAP - box.h(), box.w(), box.h());
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ the game's own HUD

	/** Marks what vanilla draws this frame, and where the bottom anchors start above it. */
	private static void reserveVanilla(Minecraft mc, int width, int height, Map<Anchor, Integer> cursor) {
		LocalPlayer player = mc.player;
		int cx = width / 2;
		// boss bars across the top middle: a name and a bar for each, from y 12, 19 apart (vanilla stops at a third of the screen)
		int bosses = ((BossOverlayAccessor) mc.gui.hud.getBossOverlay()).hellcraft$events().size();
		if (bosses > 0) {
			int bottom = Math.min(12 + 19 * bosses, height / 3 + 19);
			RESERVED.add(new Box(cx - 110, 0, 220, bottom));
		}
		if (player == null) {
			return;
		}
		// the status effect icons in the top right: a row of the good ones, a row of the bad ones under it
		int good = 0;
		int bad = 0;
		for (MobEffectInstance e : player.getActiveEffects()) {
			if (!e.showIcon()) {
				continue;
			}
			if (e.getEffect().value().isBeneficial()) {
				good++;
			} else {
				bad++;
			}
		}
		if (good > 0) {
			RESERVED.add(new Box(width - 25 * good, 0, 25 * good, 26));
		}
		if (bad > 0) {
			RESERVED.add(new Box(width - 25 * bad, 26, 25 * bad, 26));
		}
		// the scoreboard sidebar on the right (the Hall of the Damned): a band in the middle of the right edge
		if (mc.level != null && mc.level.getScoreboard().getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR) != null) {
			RESERVED.add(new Box(width - 132, height / 2 - 90, 132, 150));
		}
		if (player.isSpectator()) {
			cursor.put(Anchor.BOTTOM_CENTER, Math.min(height - MARGIN, height - 74));
			cursor.put(Anchor.ABOVE_FOOD, height - 30);
			return;
		}
		// the hotbar and the experience bar (and its level number)
		RESERVED.add(new Box(cx - 91, height - 36, 182, 36));
		if (!player.getOffhandItem().isEmpty()) {
			RESERVED.add(new Box(cx - 91 - 29, height - 23, 29, 23));
		}
		int leftTop = height - 36;
		int rightTop = height - 36;
		if (!player.hasInfiniteMaterials()) {
			// hearts (more rows, closer together, as health and absorption grow) and the armour row over them
			float health = Math.max(player.getMaxHealth(), player.getHealth()) + player.getAbsorptionAmount();
			int rows = Mth.ceil(health / 2.0f / 10.0f);
			int rowHeight = Math.max(10 - (rows - 2), 3);
			leftTop = height - 39 - (rows - 1) * rowHeight;
			if (player.getArmorValue() > 0) {
				leftTop -= 10;
			}
			RESERVED.add(new Box(cx - 91, leftTop, 91, height - 36 - leftTop));
			// food, or the mount's hearts (up to three rows), and the air bubbles over it
			rightTop = height - 39;
			if (player.getVehicle() instanceof LivingEntity mount) {
				int mountRows = Math.min(3, Mth.ceil(mount.getMaxHealth() / 2.0f / 10.0f));
				rightTop = height - 39 - (mountRows - 1) * 10;
			}
			if (player.getAirSupply() < player.getMaxAirSupply() || player.isUnderWater()) {
				rightTop -= 10;
			}
			RESERVED.add(new Box(cx, rightTop, 91, height - 36 - rightTop));
		}
		cursor.put(Anchor.ABOVE_FOOD, rightTop - (GAP - 1));
		// the message box stays clear of vanilla's action bar and item name too (up to 74 above the bottom)
		cursor.put(Anchor.BOTTOM_CENTER, Math.min(height - 74, Math.min(leftTop, rightTop) - GAP));
	}
}
