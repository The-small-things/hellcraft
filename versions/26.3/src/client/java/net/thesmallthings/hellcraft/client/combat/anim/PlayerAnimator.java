package net.thesmallthings.hellcraft.client.combat.anim;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.thesmallthings.hellcraft.combat.Guard;
import net.thesmallthings.hellcraft.combat.WeaponClass;
import net.thesmallthings.hellcraft.entity.boss.gen.PlayerMovesAnim;
import org.jetbrains.annotations.Nullable;

/**
 * What one player's body is doing in Hellcraft's combat, on this client: the move playing (a swing, a roll, a parry) and
 * the stance under it (a raised guard), each a clip from {@link Clips}, blended into one {@link RigPose} per frame.
 *
 * <p>Nothing ever snaps. A move fades in from whatever was showing when it began (a walk, the guard, the end of the
 * move before: a snapshot is kept and cross-faded out of over {@link #blendIn} ticks), plays on its own clock between
 * ticks (so it is as smooth as the screen, not as the 20 ticks a second the game thinks in), can be held for a few
 * frames when a blow lands ({@link #freeze}), and fades back out to the vanilla pose after its last key. A stance eases
 * in and out by its weight.
 */
public final class PlayerAnimator {
	private static final Int2ObjectMap<PlayerAnimator> BY_ID = new Int2ObjectOpenHashMap<>();

	@Nullable
	private Clips.Clip action;
	private float time;
	private float lastTime;
	private float speed = 1;
	private float blendIn = 2;
	private float blendOut = 4;
	private float heading = Float.NaN;
	private final RigPose from = new RigPose();
	private int freeze;
	private boolean held;

	@Nullable
	private Clips.Clip stance;
	private float stanceTime;
	private float stanceWeight;
	private float lastStanceWeight;

	private final RigPose scratch = new RigPose();
	private final RigPose scratch2 = new RigPose();

	@Nullable
	public static PlayerAnimator get(int entityId) {
		return BY_ID.get(entityId);
	}

	public static PlayerAnimator of(Entity entity) {
		return BY_ID.computeIfAbsent(entity.getId(), id -> new PlayerAnimator());
	}

	public static void clear() {
		BY_ID.clear();
	}

	/** Once a client tick: every player's animator moves on; those of players gone, or doing nothing, are dropped. */
	public static void tickAll(@Nullable ClientLevel level) {
		if (level == null) {
			BY_ID.clear();
			return;
		}
		for (Player player : level.players()) {
			PlayerAnimator animator = BY_ID.get(player.getId());
			if (animator == null) {
				if (!Guard.guarding(player)) {
					continue;
				}
				animator = of(player);
			}
			animator.tick(player);
		}
		BY_ID.int2ObjectEntrySet().removeIf(e -> {
			Entity entity = level.getEntity(e.getIntKey());
			return entity == null || !entity.isAlive() || e.getValue().idle();
		});
	}

	private boolean idle() {
		return action == null && stance == null;
	}

	/** Starts a move, fading in from whatever shows now. heading: the way it goes in the world (a yaw), or NaN. */
	public void play(PlayerMovesAnim move, float speed, float heading) {
		play(move, speed, heading, 2.0f, 4.0f);
	}

	public void play(PlayerMovesAnim move, float speed, float heading, float blendIn, float blendOut) {
		sample(1.0f, from);
		this.action = Clips.of(move);
		this.time = 0;
		this.lastTime = 0;
		this.speed = Math.max(0.05f, speed);
		this.heading = heading;
		this.blendIn = Math.max(0.01f, blendIn);
		this.blendOut = Math.max(0.01f, blendOut);
		this.freeze = 0;
		this.held = false;
	}

	/**
	 * Shows a move stopped at one moment, with no fade (the client tests film every move this way, on mannequins too:
	 * anything drawn as a player can carry an animator). Cleared by {@link #clear()} or the next {@link #play}.
	 */
	public void hold(PlayerMovesAnim move, float tick, float heading) {
		from.clear();
		this.action = Clips.of(move);
		this.time = tick;
		this.lastTime = tick;
		this.speed = 0;
		this.heading = heading;
		this.blendIn = 0.0001f;
		this.blendOut = 1000;
		this.freeze = 0;
		this.held = true;
	}

	/** Holds the move still for a few ticks: the pause as a blow lands. */
	public void freeze(int ticks) {
		if (action != null) {
			freeze = Math.max(freeze, ticks);
		}
	}

	/** The move playing, or null. */
	@Nullable
	public PlayerMovesAnim move() {
		return action == null ? null : action.move;
	}

	/** Ticks into the move playing (its own clock: a faster move's ticks pass sooner). */
	public float time() {
		return time;
	}

	/** True while the move itself runs (not its fade back out). */
	public boolean busy() {
		return action != null && time < action.ticks;
	}

	private void tick(Player player) {
		// the stance: a raised guard, by the weapon
		Clips.Clip want = null;
		if (Guard.guarding(player)) {
			WeaponClass kind = WeaponClass.of(player.getUseItem());
			want = Clips.of(kind != null && kind.twoHanded ? PlayerMovesAnim.GUARD_TWO : PlayerMovesAnim.GUARD);
		}
		lastStanceWeight = stanceWeight;
		if (want != null) {
			if (stance != want) {
				if (stance == null || stanceWeight <= 0) {
					stanceTime = 0;
				}
				stance = want;
			}
			stanceWeight = Math.min(1, stanceWeight + 0.34f);
		} else {
			stanceWeight = Math.max(0, stanceWeight - 0.25f);
			if (stanceWeight <= 0 && lastStanceWeight <= 0) {
				stance = null;
			}
		}
		stanceTime += 1;
		lastTime = time;
		if (action != null) {
			if (freeze > 0) {
				freeze--;
			} else {
				time += speed;
			}
			if (lastTime >= action.ticks + blendOut) {
				action = null;
				heading = Float.NaN;
			}
		}
	}

	/** The pose at this frame (partial: how far between ticks), written into `out`. Returns false if there is none. */
	public boolean sample(float partial, RigPose out) {
		out.clear();
		float sw = smooth(Mth.lerp(partial, lastStanceWeight, stanceWeight));
		if (stance != null && sw > 0) {
			stance.sample(stanceTime + partial - 1, scratch.clear());
			scratch.legs = 0.3f;
			out.layer(scratch, sw);
		}
		if (action != null) {
			float t = held ? time : Mth.lerp(partial, lastTime, time);
			action.sample(t, scratch.clear());
			scratch.heading = heading;
			scratch.legs = action.move == PlayerMovesAnim.ROLL || action.move == PlayerMovesAnim.GUARD_BREAK ? 1.0f : 0.3f;
			float fade = t <= action.ticks ? 1 : 1 - smooth((t - action.ticks) / blendOut);
			out.layer(scratch, fade);
			if (!held && t < blendIn) {
				scratch2.set(from).crossfade(out, smooth(t / blendIn));
				out.set(scratch2);
			}
		}
		return !out.empty();
	}

	private static float smooth(float x) {
		x = Mth.clamp(x, 0, 1);
		return x * x * (3 - 2 * x);
	}
}
