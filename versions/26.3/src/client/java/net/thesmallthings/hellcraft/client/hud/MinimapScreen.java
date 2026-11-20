package net.thesmallthings.hellcraft.client.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.thesmallthings.hellcraft.client.combat.anim.Settings;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * The minimap's settings (key Y): a button for each, which steps through its choices when clicked. Saved at once in
 * config/hellcraft-client.json; the minimap behind the screen shows each change as it is made.
 */
public final class MinimapScreen extends Screen {
	private static final int[] SIZES = {48, 64, 84, 104, 128, 160};

	@Nullable
	private final Screen parent;

	public MinimapScreen(@Nullable Screen parent) {
		super(Component.literal("Minimap"));
		this.parent = parent;
	}

	private record Choice(Supplier<String> label, Runnable next) {
	}

	@Override
	protected void init() {
		Settings.MapPrefs p = Settings.minimap();
		List<Choice> choices = new ArrayList<>();
		choices.add(new Choice(() -> "Minimap: " + (p.on ? "shown" : "hidden"), () -> p.on = !p.on));
		choices.add(new Choice(() -> "Shape: " + word(p.shape), () -> p.shape = Settings.Shape.values()[(p.shape.ordinal() + 1) % 2]));
		choices.add(new Choice(() -> "Size: " + p.size, () -> p.size = nextSize(p.size)));
		choices.add(new Choice(() -> "Corner: " + word(p.corner), () -> p.corner = Settings.Corner.values()[(p.corner.ordinal() + 1) % 4]));
		choices.add(new Choice(() -> p.rotate ? "Turns with you" : "North up", () -> p.rotate = !p.rotate));
		choices.add(new Choice(() -> "Zoom: " + zoomLabel(p.blocksPerPixel()), () -> p.zoom = (p.zoom + 1) % Settings.MapPrefs.ZOOMS.length));
		choices.add(new Choice(() -> "Cave view: " + word(p.caves), () -> p.caves = Settings.Caves.values()[(p.caves.ordinal() + 1) % 3]));
		choices.add(new Choice(() -> "Coordinates: " + onOff(p.coordinates), () -> p.coordinates = !p.coordinates));
		choices.add(new Choice(() -> "Players: " + onOff(p.players), () -> p.players = !p.players));
		choices.add(new Choice(() -> "Monsters: " + onOff(p.monsters), () -> p.monsters = !p.monsters));
		choices.add(new Choice(() -> "Animals: " + onOff(p.animals), () -> p.animals = !p.animals));
		choices.add(new Choice(() -> "Waymarks and Rests: " + onOff(p.waymarks), () -> p.waymarks = !p.waymarks));

		int columnW = 150;
		int gap = 6;
		int left = width / 2 - columnW - gap / 2;
		int top = Math.max(30, height / 2 - 80);
		for (int i = 0; i < choices.size(); i++) {
			Choice c = choices.get(i);
			Button b = Button.builder(Component.literal(c.label().get()), btn -> {
				c.next().run();
				Settings.save();
				btn.setMessage(Component.literal(c.label().get()));
			}).build();
			b.setRectangle(columnW, 20, left + (i % 2) * (columnW + gap), top + (i / 2) * 24);
			addRenderableWidget(b);
		}
		Button done = Button.builder(Component.literal("Done"), btn -> onClose()).build();
		done.setRectangle(columnW, 20, width / 2 - columnW / 2, top + ((choices.size() + 1) / 2) * 24 + 8);
		addRenderableWidget(done);
	}

	private static int nextSize(int size) {
		for (int s : SIZES) {
			if (s > size) {
				return s;
			}
		}
		return SIZES[0];
	}

	private static String word(Enum<?> e) {
		String w = e.name().toLowerCase(Locale.ROOT).replace('_', ' ');
		return Character.toUpperCase(w.charAt(0)) + w.substring(1);
	}

	private static String onOff(boolean on) {
		return on ? "on" : "off";
	}

	private static String zoomLabel(float bpp) {
		return bpp >= 1 ? (bpp == Math.round(bpp) ? Integer.toString(Math.round(bpp)) : Float.toString(bpp)) + " blocks a pixel"
				: Math.round(1 / bpp) + " pixels a block";
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		g.centeredText(font, title.getString(), width / 2, Math.max(12, height / 2 - 100), 0xFFFFFFFF);
		g.centeredText(font, "Z and X zoom, B marks where you stand (in Hell), Y opens this screen.",
				width / 2, height - 20, 0xFFA0A0A0);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
