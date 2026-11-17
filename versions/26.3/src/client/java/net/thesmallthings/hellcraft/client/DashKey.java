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
 * The Dash key (Left Alt; Controls, "Hellcraft"): the dodge roll with Hellcraft's combat on (combat/RollControl), otherwise
 * Sidestep's quick dash the way you're moving, or a hop back if you stand still (blood/LayerEnchants on the server). And
 * the Blood Art key (R), for a full hell weapon.
 */
public final class DashKey {
	private DashKey() {
	}

	public static final KeyMapping DASH = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.hellcraft.dash", InputConstants.KEY_LALT,
			NavKeys.CATEGORY));
	/** The Blood Art key (R): looses a full hell weapon's Blood Art (blood/HellWeapons.art). */
	public static final KeyMapping BLOOD_ART = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.hellcraft.blood_art", InputConstants.KEY_R,
			NavKeys.CATEGORY));

	/** Loads the class, registering the key. */
	public static void register() {
	}

	public static void tick(Minecraft mc) {
		while (BLOOD_ART.consumeClick()) {
			if (mc.player != null && mc.gui.screen() == null && ClientPlayNetworking.canSend(net.thesmallthings.hellcraft.net.ArtPayload.TYPE)
					&& net.thesmallthings.hellcraft.blood.HellWeapons.of(mc.player.getMainHandItem()) != null) {
				ClientPlayNetworking.send(net.thesmallthings.hellcraft.net.ArtPayload.INSTANCE);
			}
		}
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
			if (net.thesmallthings.hellcraft.combat.Combat.rules(true).on()) {
				// Hellcraft's combat: everyone rolls, and the roll starts here and now (combat/RollControl)
				net.thesmallthings.hellcraft.client.combat.RollControl.tryRoll(mc, x, z);
				continue;
			}
			ClientPlayNetworking.send(new DashPayload((float) x, (float) z));
		}
	}
}
