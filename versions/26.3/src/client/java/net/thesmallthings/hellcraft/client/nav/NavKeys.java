package net.thesmallthings.hellcraft.client.nav;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.thesmallthings.hellcraft.HellcraftMod;

/** M opens the Map of Hell, J the Pilgrim's Journal (both can be rebound under Controls, "Hellcraft"). */
public final class NavKeys {
	private NavKeys() {
	}

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(HellcraftMod.id("hellcraft"));
	public static final KeyMapping MAP = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.hellcraft.map", InputConstants.KEY_M, CATEGORY));
	public static final KeyMapping JOURNAL = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.hellcraft.journal", InputConstants.KEY_J, CATEGORY));

	/** Loads the class, registering the keys. */
	public static void register() {
	}

	/** Every client tick: opens a screen when its key was pressed. */
	public static void tick(Minecraft mc) {
		while (MAP.consumeClick()) {
			if (mc.gui.screen() != null || mc.player == null) {
				continue;
			}
			if (ClientNav.inferno()) {
				mc.gui.setScreen(new MapOfHellScreen());
			} else {
				mc.player.sendOverlayMessage(Component.literal("This world isn't the Inferno, so there is no Map of Hell."));
			}
		}
		while (JOURNAL.consumeClick()) {
			if (mc.gui.screen() == null && mc.player != null) {
				mc.gui.setScreen(new JournalScreen());
			}
		}
	}
}
