package net.thesmallthings.hellcraft.client.hud;

import net.minecraft.client.Minecraft;
import net.thesmallthings.hellcraft.net.BloodPayload;

/**
 * What the server last said about this player's blood (net/BloodPayload, from blood/HellWeapons): the blood charge, and the
 * Blood Oath's time left and cooldown, counted down here between updates. The hotbar's blood aura ({@link BloodAura}) and the
 * oath meter ({@link OathMeter}) draw from it.
 */
public final class BloodClient {
	private BloodClient() {
	}

	public static final int FULL = 100;

	private static int charge;
	private static int oathLeft;
	private static int oathLength = 600;
	private static int cooldownLeft;
	private static int cooldownLength = 3600;
	/** Client ticks since the charge last became full (for the aura's flare), and since the oath came off cooldown. */
	private static int sinceReady = 1000;
	private static int sinceOathReady = 1000;

	public static void receive(BloodPayload payload) {
		if (payload.charge() >= FULL && charge < FULL) {
			sinceReady = 0;
		}
		charge = payload.charge();
		oathLeft = payload.oathLeft();
		oathLength = Math.max(1, payload.oathLength());
		cooldownLeft = payload.cooldownLeft();
		cooldownLength = Math.max(1, payload.cooldownLength());
	}

	public static void reset() {
		charge = 0;
		oathLeft = 0;
		cooldownLeft = 0;
		sinceReady = 1000;
		sinceOathReady = 1000;
	}

	public static void tick(Minecraft mc) {
		if (mc.isPaused()) {
			return;
		}
		if (oathLeft > 0) {
			oathLeft--;
		}
		if (cooldownLeft > 0 && --cooldownLeft == 0) {
			sinceOathReady = 0;
		}
		if (sinceReady < 1000) {
			sinceReady++;
		}
		if (sinceOathReady < 1000) {
			sinceOathReady++;
		}
	}

	/** A hell weapon's Blood Art is ready (the charge is full). */
	public static boolean artReady() {
		return charge >= FULL;
	}

	public static int charge() {
		return charge;
	}

	/** Ticks since the charge filled. */
	public static int sinceReady() {
		return sinceReady;
	}

	/** Ticks since the oath came off cooldown. */
	public static int sinceOathReady() {
		return sinceOathReady;
	}

	public static boolean underOath() {
		return oathLeft > 0;
	}

	/** How much of the oath is left, 0 to 1. */
	public static float oath() {
		return Math.min(1f, oathLeft / (float) oathLength);
	}

	public static boolean cooling() {
		return cooldownLeft > 0;
	}

	/** How far the oath's cooldown has come back, 0 to 1. */
	public static float recovered() {
		return 1f - Math.min(1f, cooldownLeft / (float) cooldownLength);
	}

	/** For tests: set the state as the server would. */
	public static void set(int newCharge, int newOathLeft, int newCooldownLeft) {
		receive(new BloodPayload(newCharge, newOathLeft, oathLength, newCooldownLeft, cooldownLength));
	}
}
