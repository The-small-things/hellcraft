package net.thesmallthings.hellcraft.hazard;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.net.DangerPayload;

/**
 * The danger language (P6): the same colours mean the same thing on every boss.
 * <ul>
 *     <li><b>Red</b> on the ground: get out of it before it fills. Shields don't help.</li>
 *     <li><b>White</b>: the boss flashes white (BossEntity.Warning.WHITE) and the ground where the blow lands shows
 *     white: raise your shield now. A block in time staggers the boss.</li>
 *     <li><b>Purple</b>: can't be blocked. Jump it, or get out of the way.</li>
 * </ul>
 * Marks fill from the middle out over their warning time; the blow lands as they fill. Each player is told what a
 * colour means the first time they see it. The client draws them on the ground (client/danger/DangerMarks).
 */
public final class Danger {
	private Danger() {
	}

	public enum Colour {
		RED("Red on the ground: get out of it before it fills. A shield won't help."),
		WHITE("A white flash: raise your shield now. Blocking that blow in time staggers the boss."),
		PURPLE("Purple can't be blocked: jump over it, or get out of the way.");

		final String meaning;

		Colour(String meaning) {
			this.meaning = meaning;
		}
	}

	public enum Shape {CIRCLE, RING, LINE, CONE, CLEAR}

	/** Blows that shields don't stop (the red and purple ones): hellcraft:judgement, in minecraft:bypasses_shield. */
	public static final ResourceKey<DamageType> JUDGEMENT = ResourceKey.create(Registries.DAMAGE_TYPE, HellcraftMod.id("judgement"));
	/** Players this close see a boss's marks. */
	private static final double AUDIENCE = 64.0;
	/** The first hint bit (CircleHazards.hintOnce) of the three colours'. */
	private static final int HINT_BITS = 24;
	private static int nextId = 1;

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(DangerPayload.TYPE, DangerPayload.CODEC);
	}

	public static DamageSource unblockable(ServerLevel level, Entity attacker) {
		return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(JUDGEMENT), attacker);
	}

	/** For the smoke test: the red and purple blows' damage type is loaded and shields don't stop it. */
	public static String selfTest(ServerLevel level) {
		DamageSource source = unblockable(level, null);
		return "Danger test: judgement bypasses shields: " + source.is(DamageTypeTags.BYPASSES_SHIELD);
	}

	/**
	 * For the client test (ImpactTest) and anyone curious: a red circle, a white ring and a purple line in front of a
	 * player, each landing after `warnTicks`, so their eruption (client/danger/Impacts) can be seen.
	 */
	public static String showcase(ServerPlayer player, int warnTicks) {
		ServerLevel level = (ServerLevel) player.level();
		double yaw = Math.toRadians(player.getYRot());
		Vec3 ahead = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
		Vec3 side = new Vec3(-ahead.z, 0, ahead.x);
		Vec3 at = player.position().add(ahead.scale(14));
		circle(level, Colour.RED, at.add(side.scale(-9)), 4, warnTicks);
		ring(level, Colour.WHITE, at, 2, 5, warnTicks);
		line(level, Colour.PURPLE, at.add(side.scale(6)).add(ahead.scale(-6)), Math.atan2(ahead.z, ahead.x), 14, 1.5, warnTicks);
		return "Danger showcase: 3 marks land in " + warnTicks + " ticks";
	}

	/** A filled circle. Returns the mark's id. */
	public static int circle(ServerLevel level, Colour colour, Vec3 centre, double radius, int warnTicks) {
		return send(level, colour, Shape.CIRCLE, centre, radius, 0, 0, warnTicks, null, 0);
	}

	/** A circle that follows an entity for followTicks, then stays where it was. */
	public static int follow(ServerLevel level, Colour colour, Entity target, double radius, int followTicks, int warnTicks) {
		return send(level, colour, Shape.CIRCLE, target.position(), radius, 0, 0, warnTicks, target, followTicks);
	}

	/** A ring between two radii (a sweep, a shockwave). */
	public static int ring(ServerLevel level, Colour colour, Vec3 centre, double inner, double outer, int warnTicks) {
		return send(level, colour, Shape.RING, centre, outer, inner, 0, warnTicks, null, 0);
	}

	/** A straight band from a point, angle in radians (atan2(z, x)). */
	public static int line(ServerLevel level, Colour colour, Vec3 from, double angle, double length, double halfWidth, int warnTicks) {
		return send(level, colour, Shape.LINE, from, length, halfWidth, angle, warnTicks, null, 0);
	}

	/** A wedge from a point, angle in radians (atan2(z, x)), half its opening in radians. */
	public static int cone(ServerLevel level, Colour colour, Vec3 apex, double angle, double length, double halfAngle, int warnTicks) {
		return send(level, colour, Shape.CONE, apex, length, halfAngle, angle, warnTicks, null, 0);
	}

	/** Takes a mark away early (the boss died, the attack was cut short). */
	public static void clear(ServerLevel level, int id, Vec3 near) {
		DangerPayload payload = new DangerPayload(id, 0, Shape.CLEAR.ordinal(), near.x, near.y, near.z, 0, 0, 0, 0, -1, 0);
		for (ServerPlayer p : level.players()) {
			if (p.position().distanceToSqr(near) < AUDIENCE * AUDIENCE && ServerPlayNetworking.canSend(p, DangerPayload.TYPE)) {
				ServerPlayNetworking.send(p, payload);
			}
		}
	}

	/** World angle (radians, atan2(z, x)) a mob faces. */
	public static double facing(Entity e) {
		double yaw = Math.toRadians(e.getYRot());
		return Math.atan2(Math.cos(yaw), -Math.sin(yaw));
	}

	/** While a boss fight runs on its slowed clock, its marks take longer to fill by the same factor (GuardianFight). */
	private static double stretch = 1.0;

	/** Runs `action` with every mark it sends stretched by this factor (1 / the boss's tempo). */
	public static void stretched(double factor, Runnable action) {
		double before = stretch;
		stretch = factor;
		try {
			action.run();
		} finally {
			stretch = before;
		}
	}

	private static int send(ServerLevel level, Colour colour, Shape shape, Vec3 at, double size, double width, double angle, int warnTicks,
							Entity follow, int followTicks) {
		warnTicks = (int) Math.round(warnTicks * stretch);
		followTicks = (int) Math.round(followTicks * stretch);
		int id = nextId++;
		DangerPayload payload = new DangerPayload(id, colour.ordinal(), shape.ordinal(), at.x, at.y, at.z, (float) size, (float) width, (float) angle,
				warnTicks, follow == null ? -1 : follow.getId(), followTicks);
		for (ServerPlayer p : level.players()) {
			if (p.position().distanceToSqr(at) < AUDIENCE * AUDIENCE && ServerPlayNetworking.canSend(p, DangerPayload.TYPE)) {
				ServerPlayNetworking.send(p, payload);
				CircleHazards.hintOnce(p, HINT_BITS + colour.ordinal(), colour.meaning);
			}
		}
		return id;
	}
}
