package net.thesmallthings.hellcraft.client.combat;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.blood.LayerEnchants;
import net.thesmallthings.hellcraft.client.combat.anim.PlayerAnimator;
import net.thesmallthings.hellcraft.combat.Combat;
import net.thesmallthings.hellcraft.combat.Guard;
import net.thesmallthings.hellcraft.entity.boss.gen.PlayerMovesAnim;
import net.thesmallthings.hellcraft.net.CombatPayload;
import net.thesmallthings.hellcraft.net.DashPayload;

/**
 * This client's own dodge roll (combat/Dodge on the server). A player's movement is the client's to decide, so the roll
 * starts the moment the key goes down: the body tumbles (the ROLL clip) and for ten ticks the client carries the player
 * along the roll's line at a speed that rises fast and eases out, however the keys are held. The server is told, takes
 * the stamina and decides which blows miss.
 */
public final class RollControl {
	private RollControl() {
	}

	/** The share of the roll's distance covered in each of its moving ticks: quick off the mark, easing to a stop. */
	private static final float[] PACE = {1.0f, 1.25f, 1.3f, 1.25f, 1.15f, 1.0f, 0.85f, 0.65f, 0.45f, 0.25f};
	private static final float PACE_SUM;

	static {
		float sum = 0;
		for (float p : PACE) {
			sum += p;
		}
		PACE_SUM = sum;
	}

	private static int tick = -1;
	private static double dirX;
	private static double dirZ;
	private static float distance;
	private static int cooldown;

	public static boolean rolling() {
		return tick >= 0;
	}

	public static void reset() {
		tick = -1;
		cooldown = 0;
	}

	/** The Dash key, with Hellcraft's combat on: x, z is the way the keys point in the world (nothing: a hop back). */
	public static void tryRoll(Minecraft mc, double x, double z) {
		LocalPlayer player = mc.player;
		CombatPayload rules = Combat.rules(true);
		if (player == null || !player.isAlive() || player.isSpectator() || player.isPassenger() || player.isFallFlying() || player.isSwimming()
				|| rolling() || cooldown > 0) {
			return;
		}
		if (player.isUsingItem() && !Guard.guarding(player)) {
			return;
		}
		int sidestep = LayerEnchants.level(player, LayerEnchants.SIDESTEP);
		float cost = rules.dodgeCost() * Math.max(0.4f, 1.0f - 0.2f * sidestep);
		if (!player.hasInfiniteMaterials() && StaminaHud.stamina() < cost) {
			StaminaHud.refuse();
			return;
		}
		double len = Math.sqrt(x * x + z * z);
		if (len < 1.0e-4) {
			double yaw = Math.toRadians(player.getYRot());
			x = Math.sin(yaw);
			z = -Math.cos(yaw);
			len = 1;
		}
		dirX = x / len;
		dirZ = z / len;
		distance = rules.dodgeDistance() + 0.5f * sidestep;
		tick = 0;
		cooldown = rules.dodgeCooldown();
		if (!player.hasInfiniteMaterials()) {
			StaminaHud.predict(cost);
		}
		if (player.isUsingItem()) {
			mc.gameMode.releaseUsingItem(player);
		}
		player.setSprinting(false);
		SwingControl.cancel();
		float heading = (float) (Mth.atan2(-dirX, dirZ) * Mth.RAD_TO_DEG);
		PlayerAnimator.of(player).play(PlayerMovesAnim.ROLL, 1.0f, heading, 1.5f, 3.0f);
		net.thesmallthings.hellcraft.combat.RollShape.start(player);
		ClientPlayNetworking.send(new DashPayload((float) dirX, (float) dirZ));
		drive(player);
	}

	public static void tick(Minecraft mc) {
		if (cooldown > 0) {
			cooldown--;
		}
		LocalPlayer player = mc.player;
		if (player == null || !player.isAlive()) {
			tick = -1;
			return;
		}
		if (tick >= 0) {
			tick++;
			if (tick >= PACE.length || player.isPassenger() || player.isInWater() && tick > 2) {
				tick = -1;
			} else {
				drive(player);
			}
		}
	}

	private static void drive(LocalPlayer player) {
		double speed = distance * PACE[tick] / PACE_SUM;
		Vec3 v = player.getDeltaMovement();
		player.setDeltaMovement(dirX * speed, v.y, dirZ * speed);
	}
}
