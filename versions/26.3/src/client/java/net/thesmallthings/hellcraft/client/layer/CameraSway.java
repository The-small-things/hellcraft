package net.thesmallthings.hellcraft.client.layer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.thesmallthings.hellcraft.client.nav.ClientNav;
import net.thesmallthings.hellcraft.entity.Carrier;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.Zone;

/**
 * A camera that feels the ride: in Lust the view rocks gently in the storm, and harder while a wind stream carries you;
 * on Geryon's back (or in Antaeus's hand) it leans into each turn and sways with the flight. Only the view tilts (a roll
 * round the line of sight); where you aim is untouched. Off when View Bobbing is off (Accessibility), like every other
 * motion of the view. Applied in mixin/CameraMixin.
 */
public final class CameraSway {
	private CameraSway() {
	}

	/** The storm's rock, gentle and while carried (degrees), and the most a ride leans into a turn. */
	private static final float STORM = 1.2f;
	private static final float CARRIED = 3.5f;
	private static final float MAX_LEAN = 14f;

	private static float amplitude;
	private static float lean;
	private static float lastLean;
	private static float lastVehicleYaw = Float.NaN;
	private static int age;

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(CameraSway::tick);
	}

	private static void tick(Minecraft mc) {
		age++;
		lastLean = lean;
		if (mc.player == null || mc.isPaused()) {
			return;
		}
		float wantAmplitude = 0;
		float wantLean = 0;
		Entity vehicle = mc.player.getVehicle();
		if (vehicle instanceof Carrier) {
			// lean into the turn: the faster the ride turns, the further the view tips (smoothed)
			float yaw = vehicle.getYRot();
			if (!Float.isNaN(lastVehicleYaw)) {
				float rate = Mth.wrapDegrees(yaw - lastVehicleYaw);
				wantLean = Mth.clamp(-rate * 2.5f, -MAX_LEAN, MAX_LEAN);
			}
			lastVehicleYaw = yaw;
			wantAmplitude = 2.0f;
		} else {
			lastVehicleYaw = Float.NaN;
			if (ClientNav.inHell(mc) && InfernoGeometry.zoneAt(mc.player.getX(), mc.player.getZ()) == Zone.LUST) {
				boolean carried = !mc.player.onGround() && mc.player.getDeltaMovement().horizontalDistance() > 0.4;
				wantAmplitude = carried ? CARRIED : STORM;
			}
		}
		amplitude += (wantAmplitude - amplitude) * 0.05f;
		lean += (wantLean - lean) * 0.12f;
	}

	/** The view's roll this frame (degrees; 0 when nothing sways it or View Bobbing is off). */
	public static float roll(float partialTicks) {
		Minecraft mc = Minecraft.getInstance();
		if (!mc.options.bobView().get() || (amplitude < 0.01f && Math.abs(lean) < 0.01f && Math.abs(lastLean) < 0.01f)) {
			return 0;
		}
		float t = age + partialTicks;
		// two slow waves, so the rocking never quite repeats
		float sway = (float) (Math.sin(t * 0.07) * 0.7 + Math.sin(t * 0.031 + 1.3) * 0.3) * amplitude;
		return sway + Mth.lerp(partialTicks, lastLean, lean);
	}
}
