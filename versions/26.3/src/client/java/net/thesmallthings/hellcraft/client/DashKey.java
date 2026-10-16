package net.thesmallthings.hellcraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec2;
import net.thesmallthings.hellcraft.client.nav.NavKeys;
import net.thesmallthings.hellcraft.net.DashPayload;

/**
 * The Dash key (Left Alt; Controls, "Hellcraft"): with Sidestep leggings, a quick dash the way you're moving, or a hop
 * back if you stand still (blood/LayerEnchants does the rest on the server).
 */
public final class DashKey {
	private DashKey() {
	}

	public static final KeyMapping DASH = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.hellcraft.dash", InputConstants.KEY_LALT,
			NavKeys.CATEGORY));

	/** Loads the class, registering the key. */
	public static void register() {
	}

	public static void tick(Minecraft mc) {
		while (DASH.consumeClick()) {
			LocalPlayer player = mc.player;
			if (player == null || mc.gui.screen() != null || !ClientPlayNetworking.canSend(DashPayload.TYPE)) {
				continue;
			}
			// the keys held, turned into the world: forward along the look, left to its left
			Vec2 move = player.input.getMoveVector();
			double yaw = Math.toRadians(player.getYRot());
			double fx = -Math.sin(yaw);
			double fz = Math.cos(yaw);
			double x = fx * move.y + fz * move.x;
			double z = fz * move.y - fx * move.x;
			ClientPlayNetworking.send(new DashPayload((float) x, (float) z));
		}
	}
}
