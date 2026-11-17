package net.thesmallthings.hellcraft.entity.boss;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Base64;

/**
 * Where a boss can be hit beyond its main box: its parts (tools/art/hitboxes.py) and where each part's centre is at every
 * tick of every animation, baked from the model by tools/gen_bosses.py into entity/boss/gen/<Boss>Shape. Common code: the
 * server and every client work out the same places from the same numbers, so the parts (BossPart) sit where the model is
 * drawn without sending positions.
 *
 * Positions are in the model's own frame, in blocks, already at the renderer's scale: Blockbench's axes (Y up, the model
 * facing -Z), measured from its feet. {@link #toWorld} turns them by the boss's body yaw the way the renderer does.
 */
public final class BossShape {
	/** One hit box: upright and square, `width` across and `height` tall; `gaze` how far it turns with the head, about `pivot`. */
	public record Part(String name, float width, float height, float gaze, float pivotX, float pivotY, float pivotZ) {
	}

	/** The baked positions are whole 128ths of a block. */
	private static final float UNIT = 1 / 128f;

	public final float scale;
	private final Part[] parts;
	private final String[] anims;
	private final int[] ticks;
	private final boolean[] looping;
	/** [animation][(tick * parts + part) * 3 + axis], in UNITs. */
	private final short[][] tracks;

	public BossShape(float scale, Part[] parts, String[] anims, int[] ticks, boolean[] looping, String[] tracks) {
		this.scale = scale;
		this.parts = parts;
		this.anims = anims;
		this.ticks = ticks;
		this.looping = looping;
		this.tracks = new short[tracks.length][];
		for (int a = 0; a < tracks.length; a++) {
			byte[] bytes = Base64.getDecoder().decode(tracks[a]);
			short[] values = new short[bytes.length / 2];
			for (int i = 0; i < values.length; i++) {
				values[i] = (short) ((bytes[2 * i] << 8) | (bytes[2 * i + 1] & 0xFF));
			}
			if (values.length != (ticks[a] + 1) * parts.length * 3) {
				throw new IllegalStateException("A boss shape's " + anims[a] + " track has " + values.length + " numbers, not "
						+ (ticks[a] + 1) * parts.length * 3);
			}
			this.tracks[a] = values;
		}
	}

	public int parts() {
		return parts.length;
	}

	public Part part(int i) {
		return parts[i];
	}

	public int anims() {
		return anims.length;
	}

	public String animName(int anim) {
		return anims[anim];
	}

	public int ticks(int anim) {
		return ticks[anim];
	}

	public boolean looping(int anim) {
		return looping[anim];
	}

	/** The animation's index by its name (IDLE, BITE_MID...), or -1. */
	public int anim(String name) {
		for (int i = 0; i < anims.length; i++) {
			if (anims[i].equals(name)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Where the part's centre is, `tick` ticks (fractions too) into the animation, in the model's frame: a looping one goes
	 * round, any other holds its last pose.
	 */
	public Vec3 local(int part, int anim, float tick) {
		int n = ticks[anim];
		if (n <= 0) {
			return sample(part, anim, 0);
		}
		float t = looping[anim] ? tick % n : Mth.clamp(tick, 0, n);
		if (t < 0) {
			t += n;
		}
		int i = Math.min((int) t, n - 1);
		float f = t - i;
		Vec3 a = sample(part, anim, i);
		Vec3 b = sample(part, anim, i + 1);
		return a.lerp(b, f);
	}

	private Vec3 sample(int part, int anim, int tick) {
		short[] track = tracks[anim];
		int at = (tick * parts.length + part) * 3;
		return new Vec3(track[at] * UNIT, track[at + 1] * UNIT, track[at + 2] * UNIT);
	}

	/**
	 * A point in the model's frame turned into the world's, offset from the boss's feet, for a body facing `yaw` degrees
	 * (as the renderer draws it: a half turn less the yaw about Y, after flipping X and Y twice over, which cancel).
	 */
	public static Vec3 toWorld(Vec3 local, float yaw) {
		float r = yaw * Mth.DEG_TO_RAD;
		double cos = Mth.cos(r);
		double sin = Mth.sin(r);
		return new Vec3(-local.x * cos + local.z * sin, local.y, -local.x * sin - local.z * cos);
	}

	/**
	 * Where part `i`'s centre is in the world, offset from the boss's feet: the body faces `bodyYaw`, the head is turned
	 * `headTurn` degrees from it (the part turns that much times its gaze, about its pivot).
	 */
	public Vec3 offset(int i, int anim, float tick, float bodyYaw, float headTurn) {
		Part p = parts[i];
		Vec3 local = local(i, anim, tick);
		if (p.gaze() == 0 || headTurn == 0) {
			return toWorld(local, bodyYaw);
		}
		Vec3 pivot = new Vec3(p.pivotX(), p.pivotY(), p.pivotZ());
		return toWorld(pivot, bodyYaw).add(toWorld(local.subtract(pivot), bodyYaw + p.gaze() * headTurn));
	}

	/** Checks the numbers hold together and the parts face the way the boss does; null if all is well. */
	public String check(String boss, BossAnim[] enumAnims) {
		if (enumAnims.length != anims.length) {
			return boss + "'s shape has " + anims.length + " animations, its body " + enumAnims.length + ": run tools/gen_bosses.py";
		}
		for (BossAnim a : enumAnims) {
			if (!anims[a.ordinal()].equals(a.name()) || ticks[a.ordinal()] != a.ticks()) {
				return boss + "'s shape doesn't match its animation " + a.name() + ": run tools/gen_bosses.py";
			}
		}
		if (anim("IDLE") < 0) {
			return boss + "'s shape has no IDLE";
		}
		return null;
	}

	static String selfTest() {
		// facing south (yaw 0) a model's front (-Z in its frame) is +Z in the world; facing west (yaw 90), -X
		Vec3 ahead = new Vec3(0, 1, -2);
		Vec3 south = toWorld(ahead, 0);
		Vec3 west = toWorld(ahead, 90);
		if (Math.abs(south.z - 2) > 1e-4 || Math.abs(south.x) > 1e-4 || Math.abs(west.x + 2) > 1e-4 || Math.abs(west.z) > 1e-4) {
			return "boss parts turn the wrong way with the body: " + south + ", " + west;
		}
		// the model's right hand (+X in Blockbench) is on its right: facing south, that is west (-X)
		Vec3 right = toWorld(new Vec3(1, 0, 0), 0);
		if (Math.abs(right.x + 1) > 1e-4) {
			return "boss parts are mirrored: " + right;
		}
		return null;
	}
}
