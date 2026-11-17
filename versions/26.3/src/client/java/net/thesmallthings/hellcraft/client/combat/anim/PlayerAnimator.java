package net.thesmallthings.hellcraft.client.combat.anim;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
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
 *
 * <p>Under it all, for every player seen (unless this player turned it off, {@link Settings}), {@link Locomotion} draws the
 * walk, run, sneak, jumps and landings; over that an item stance shows what the player is using (a bow drawn by how far
 * it is drawn, a shield braced behind, food or a bottle at the mouth), and letting go of a bow, a trident or a crossbow
 * plays its release as a move. {@link PoseSmoother} eases whatever vanilla still switches at once.
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
	/** A move cut short (a guard raised mid-swing or late in a roll): held where it was and faded out over cutTotal ticks. */
	private float cutLeft;
	private float cutTotal;

	@Nullable
	private Clips.Clip stance;
	private float stanceTime;
	private float stanceWeight;
	private float lastStanceWeight;

	private final RigPose scratch = new RigPose();
	private final RigPose scratch2 = new RigPose();
	private final RigPose scratch3 = new RigPose();
	private boolean mirrored;

	/** Getting about, under everything. */
	public final Locomotion motion = new Locomotion();
	/** Vanilla's sudden pose changes, eased. */
	public final PoseSmoother smoother = new PoseSmoother();

	// what the player is using: a bow, a shield, food or drink (its clip, weight, its own clock, and which hand)
	@Nullable
	private Clips.Clip use;
	private float useTime;
	private float lastUseTime;
	private float useWeight;
	private float lastUseWeight;
	private boolean useMirrored;
	private boolean useByProgress;
	// last tick, for releases
	private ItemUseAnimation lastUseAnim = ItemUseAnimation.NONE;
	private int lastTicksUsing;
	private boolean lastUseMirrored;
	private final boolean[] crossbowCharged = new boolean[2];
	private boolean motionTicked;

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
		boolean smooth = Settings.smoothAnimations();
		for (Player player : level.players()) {
			PlayerAnimator animator = BY_ID.get(player.getId());
			if (animator == null) {
				if (!smooth && !Guard.guarding(player)) {
					continue;
				}
				animator = of(player);
			}
			animator.tick(player, smooth);
		}
		BY_ID.int2ObjectEntrySet().removeIf(e -> {
			Entity entity = level.getEntity(e.getIntKey());
			return entity == null || !entity.isAlive() || !smooth && e.getValue().idle();
		});
	}

	private boolean idle() {
		return action == null && stance == null && use == null;
	}

	/** True once {@link Locomotion} has been ticked for this player (mannequins in the tests never are). */
	public boolean moving() {
		return motionTicked;
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
		this.mirrored = false;
		this.cutLeft = this.cutTotal = 0;
	}

	/**
	 * Cuts the move playing short: it stops where it is and fades out over `fade` ticks into whatever is under it (the guard
	 * a player raised mid-swing). Nothing jumps: the pose holds still while its weight goes.
	 */
	public void cut(float fade) {
		if (action == null || cutTotal > 0 || held) {
			return;
		}
		speed = 0;
		freeze = 0;
		cutTotal = Math.max(0.01f, fade);
		cutLeft = cutTotal;
	}

	/** Starts a move made for the right hand, done with the other one (a trident thrown left-handed). */
	public void playMirrored(PlayerMovesAnim move, float speed, float blendIn, float blendOut) {
		play(move, speed, Float.NaN, blendIn, blendOut);
		this.mirrored = true;
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
		this.mirrored = false;
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

	private void tick(Player player, boolean smooth) {
		if (smooth) {
			motion.tick(player, player == net.minecraft.client.Minecraft.getInstance().player);
			motionTicked = true;
			items(player);
		} else {
			motionTicked = false;
			use = null;
			useWeight = lastUseWeight = 0;
		}
		// the stance: a raised guard, by the weapon
		Clips.Clip want = null;
		if (Guard.guarding(player)) {
			WeaponClass kind = WeaponClass.of(player.getUseItem());
			want = Clips.of(kind != null && kind.twoHanded ? PlayerMovesAnim.GUARD_TWO : PlayerMovesAnim.GUARD);
		}
		lastStanceWeight = stanceWeight;
		if (want != null && stanceWeight <= 0 && action != null && busy()
				&& (action.move == PlayerMovesAnim.ROLL || action.move.ordinal() >= PlayerMovesAnim.SWORD_1.ordinal())) {
			// the guard went up mid-swing or late in a roll: the move gives way to it at once (for everyone watching too)
			cut(3.0f);
		}
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
		if (action != null && time < action.ticks && action.move != PlayerMovesAnim.ROLL || want != null) {
			// swinging or guarding: the body squares up to where the player looks (vanilla does the same for its own swing)
			player.setYBodyRot(Mth.rotLerp(0.6f, player.yBodyRot, player.getYHeadRot()));
		}
		lastTime = time;
		if (action != null && cutTotal > 0) {
			cutLeft -= 1;
			if (cutLeft <= 0) {
				action = null;
				heading = Float.NaN;
				cutLeft = cutTotal = 0;
			}
		}
		if (action != null) {
			if (freeze > 0) {
				freeze--;
			} else {
				time += speed;
			}
			if (time > lastTime && lastTime < action.ticks) {
				net.thesmallthings.hellcraft.client.combat.Trails.tick(player, this, action.move, lastTime, time);
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
		float uw = smooth(Mth.lerp(partial, lastUseWeight, useWeight));
		if (use != null && uw > 0) {
			use.sample(useByProgress ? Mth.lerp(partial, lastUseTime, useTime) : useTime + partial - 1, scratch.clear());
			if (useMirrored) {
				scratch.mirror();
			}
			scratch.legs = 0.4f;
			out.layer(scratch, uw);
		}
		if (action != null) {
			float t = held ? time : Mth.lerp(partial, lastTime, time);
			action.sample(t, scratch.clear());
			if (mirrored) {
				scratch.mirror();
			}
			scratch.heading = heading;
			scratch.legs = action.move == PlayerMovesAnim.ROLL || action.move == PlayerMovesAnim.GUARD_BREAK ? 1.0f : 0.3f;
			float fade = t <= action.ticks ? 1 : 1 - smooth((t - action.ticks) / blendOut);
			if (cutTotal > 0) {
				fade *= smooth((cutLeft - partial) / cutTotal);
			}
			out.layer(scratch, fade);
			if (!held && t < blendIn) {
				scratch2.set(from).crossfade(out, smooth(t / blendIn));
				out.set(scratch2);
			}
		}
		return !out.empty();
	}

	/**
	 * The whole body at this frame: getting about underneath, the moves and stances over it (a move keeps its own legs as
	 * far as it says, {@link RigPose#legs}, and lets the stride show through the rest while the player walks). For the
	 * player model (mixin/AvatarRendererMixin); first person reads {@link #sample}.
	 */
	public boolean sampleBody(float partial, net.minecraft.client.renderer.entity.state.HumanoidRenderState state, RigPose out) {
		boolean moved = motionTicked && motion.sample(partial, state, out);
		if (!moved) {
			return sample(partial, out);
		}
		if (sample(partial, scratch3)) {
			float walk = Mth.clamp(state.walkAnimationSpeed, 0, 1);
			float keep = 1 - (1 - scratch3.legs) * walk * 0.85f;
			scratch3.weight[Bone.RIGHT_LEG.ordinal()] *= keep;
			scratch3.weight[Bone.LEFT_LEG.ordinal()] *= keep;
			if (!Float.isNaN(scratch3.heading)) {
				// a roll turns the whole body its own way: under it the stride would turn too, so it gives way
				out.scale(1 - smooth(scratch3.weight(Bone.ROOT)));
			}
			out.layer(scratch3, 1);
		}
		out.legs = 1;
		return !out.empty();
	}

	/**
	 * First person: the hand's share of getting about (when the view bobs: vanilla's View Bobbing setting), with the moves
	 * and stances over it. For mixin/FirstPersonHandsMixin.
	 */
	public boolean sampleFirstPerson(float partial, Player player, RigPose out) {
		out.clear();
		boolean bob = net.minecraft.client.Minecraft.getInstance().options.bobView().get();
		if (motionTicked && bob) {
			motion.sampleFirstPerson(partial, player, out);
		}
		if (sample(partial, scratch3)) {
			out.layer(scratch3, 1);
		}
		return !out.empty();
	}

	/** Each tick: what the player is using, and what they just let go of. */
	private void items(Player player) {
		ItemStack using = player.isUsingItem() ? player.getUseItem() : ItemStack.EMPTY;
		ItemUseAnimation anim = using.isEmpty() ? ItemUseAnimation.NONE : using.getUseAnimation();
		int ticksUsing = player.isUsingItem() ? player.getTicksUsingItem() : 0;
		boolean mirror = player.isUsingItem() && player.getUsedItemHand().asArm(player.getMainArm()) != player.getMainArm();
		PlayerMovesAnim want = switch (anim) {
			case BOW -> PlayerMovesAnim.BOW_DRAW;
			case EAT -> PlayerMovesAnim.EAT;
			case DRINK -> PlayerMovesAnim.DRINK;
			case BLOCK -> Guard.guarding(player) ? null : PlayerMovesAnim.SHIELD_BRACE;
			default -> null;
		};
		lastUseWeight = useWeight;
		lastUseTime = useTime;
		if (want != null) {
			Clips.Clip clip = Clips.of(want);
			if (use != clip) {
				if (use == null || useWeight <= 0) {
					useTime = 0;
					lastUseTime = 0;
				}
				use = clip;
			}
			useMirrored = mirror;
			useByProgress = want == PlayerMovesAnim.BOW_DRAW;
			useTime = useByProgress ? Math.min(ticksUsing, clip.ticks) : useTime + 1;
			useWeight = Math.min(1, useWeight + 0.34f);
		} else {
			useWeight = Math.max(0, useWeight - 0.25f);
			if (useByProgress) {
				lastUseTime = useTime;
			} else {
				useTime += 1;
			}
			if (useWeight <= 0 && lastUseWeight <= 0) {
				use = null;
			}
		}
		// let go: an arrow loosed, a trident thrown
		if (lastUseAnim != anim) {
			if (lastUseAnim == ItemUseAnimation.BOW && lastTicksUsing >= 3 && holding(player, Items.BOW)) {
				release(PlayerMovesAnim.BOW_RELEASE, lastUseMirrored, 0.5f, 3.0f);
				useWeight = lastUseWeight = 0;
				use = null;
			} else if (lastUseAnim == ItemUseAnimation.TRIDENT && lastTicksUsing >= 10) {
				release(PlayerMovesAnim.THROW, lastUseMirrored, 1.0f, 4.0f);
			}
		}
		// a crossbow fired: it was loaded last tick and isn't now
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = player.getItemInHand(hand);
			boolean charged = stack.is(Items.CROSSBOW) && CrossbowItem.isCharged(stack);
			if (crossbowCharged[hand.ordinal()] && !charged && stack.is(Items.CROSSBOW)) {
				release(PlayerMovesAnim.CROSSBOW_FIRE, hand.asArm(player.getMainArm()) != player.getMainArm(), 0.3f, 3.0f);
			}
			crossbowCharged[hand.ordinal()] = charged;
		}
		lastUseAnim = anim;
		lastTicksUsing = ticksUsing;
		lastUseMirrored = mirror;
	}

	private void release(PlayerMovesAnim move, boolean mirror, float blendIn, float blendOut) {
		if (busy() && action != null && action.move.ordinal() >= PlayerMovesAnim.SWORD_1.ordinal()) {
			return;
		}
		if (mirror) {
			playMirrored(move, 1.0f, blendIn, blendOut);
		} else {
			play(move, 1.0f, Float.NaN, blendIn, blendOut);
		}
	}

	private static boolean holding(Player player, net.minecraft.world.item.Item item) {
		return player.getMainHandItem().is(item) || player.getOffhandItem().is(item);
	}

	/** For the client tests: the item stance showing (null for none) and how strongly. */
	@Nullable
	public PlayerMovesAnim using() {
		return use == null || useWeight <= 0 ? null : use.move;
	}

	private static float smooth(float x) {
		x = Mth.clamp(x, 0, 1);
		return x * x * (3 - 2 * x);
	}
}
