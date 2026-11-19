package net.thesmallthings.hellcraft.client.combat;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.client.combat.anim.PlayerAnimator;
import net.thesmallthings.hellcraft.combat.Combat;
import net.thesmallthings.hellcraft.combat.Moveset;
import net.thesmallthings.hellcraft.combat.Swing;
import net.thesmallthings.hellcraft.combat.WeaponClass;
import net.thesmallthings.hellcraft.net.CombatPayload;
import net.thesmallthings.hellcraft.net.SwingPayload;
import org.jetbrains.annotations.Nullable;

/**
 * This client's own swings (combat/Swing on the server, combat/Moveset for what each weapon does). With a weapon that
 * has moves in hand, the attack key no longer pokes at whatever is under the crosshair: it swings.
 *
 * <ul>
 *     <li><b>A click</b> starts the next light move of the chain the moment the key comes up (the wind-up plays here and
 *     now; the server is told and lands the blow when the animation does).</li>
 *     <li><b>Clicking again</b> during a move queues the next, which begins the moment this one frees it: a combo.</li>
 *     <li><b>Holding</b> the key {@link Swing#CHARGE_AT} ticks is the heavy move (if there is stamina for it), begun
 *     straight away and a little way into its wind-up to make up the wait: no light move starts first. With no stamina,
 *     holding just keeps the chain going.</li>
 *     <li>Looking at a block with nothing to fight in front of you, the key mines as it always did.</li>
 * </ul>
 */
public final class SwingControl {
	private SwingControl() {
	}

	@Nullable
	private static Moveset.Move current;
	@Nullable
	private static Moveset set;
	private static ItemStack weapon = ItemStack.EMPTY;
	/** Ticks into the move, on the move's own clock (a faster weapon's pass sooner). */
	private static float time;
	private static float pace = 1;
	/**
	 * A press not yet turned into a move: it is a light one once the key comes up, the heavy once it has been down
	 * {@link Swing#CHARGE_AT} ticks. Mid-move it waits for the move to free the next.
	 */
	private static boolean pressed;
	/** Ticks the key has stayed down since the press. */
	private static int pressHeld;
	/** The key came up since the press. */
	private static boolean pressUp;
	/** Ticks the press has waited with nothing playing (made up by starting a heavy that far in). */
	private static int idleWait;
	/** After a heavy move the key must come up before holding it does anything again. */
	private static boolean spent;
	@Nullable
	private static Moveset.Move last;
	private static int sinceLast = 1000;

	/** A move is playing or a press is being decided: the attack key isn't vanilla's for now (no mining what's behind the target). */
	public static boolean busy() {
		return current != null || pressed;
	}

	/** Winding up: before the blow has gone forward. */
	public static boolean windingUp() {
		return current != null && time < current.hitTick();
	}

	/**
	 * A heavy weapon's blow has gone forward: committed to the recovery, no guard until the move ends (combat/Swing.committed,
	 * which the server checks; a guard pressed now is raised when it ends).
	 */
	public static boolean committed() {
		if (current == null || time < current.hitTick()) {
			return false;
		}
		WeaponClass kind = WeaponClass.of(weapon);
		return kind != null && kind.heavy;
	}

	/**
	 * Ticks until the server, whose swing a guard cut short after its blow landed, takes a fresh one (Swing.mayBegin: a new
	 * chain only from the old move's combo window). A click before then waits for it, instead of a swing only this client sees.
	 */
	private static int fresh;
	/** A click came while {@link #fresh} counted down: it swings the moment that ends. */
	private static boolean freshQueued;

	public static void reset() {
		current = null;
		last = null;
		pressed = false;
		sinceLast = 1000;
		spent = false;
		fresh = 0;
		freshQueued = false;
	}

	/** A roll or a raised guard takes over. */
	public static void cancel() {
		pressed = false;
		if (current != null) {
			// past its blow, the server keeps the move until its combo window (cut in the wind-up, it lets it go at once)
			fresh = time >= current.hitTick() ? Math.max(0, Mth.ceil((current.nextTick() - 2 - time) / pace)) : 0;
			freshQueued = false;
			last = null;
			current = null;
			sinceLast = 1000;
		}
	}

	/**
	 * The attack key was clicked (mixin/MinecraftMixin, before vanilla's own attack). Returns true if the click was a
	 * swing's: vanilla then does nothing with it.
	 */
	public static boolean click(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || !Combat.rules(true).on() || player.isSpectator() || player.isHandsBusy() || player.isUsingItem()
				|| RollControl.busy() || !ClientPlayNetworking.canSend(SwingPayload.TYPE)) {
			return false;
		}
		Moveset moves = Moveset.of(player.getMainHandItem());
		if (moves == null) {
			return false;
		}
		if (current == null) {
			if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK && !somethingToFight(player, moves.light.get(0))) {
				// a block in front and nothing to fight: mine it, as ever
				return false;
			}
			if (fresh > 0) {
				freshQueued = true;
				return true;
			}
		}
		// a light move or the heavy: that depends on how long the key stays down (mid-move, a click queues the next)
		pressed = true;
		pressHeld = 0;
		pressUp = !mc.options.keyAttack.isDown();
		idleWait = 0;
		if (current == null) {
			resolve(mc, moves);
		}
		return true;
	}

	/**
	 * Turns the press into a move if it can be told yet: the next light move once the key is up, the heavy once it has been
	 * held long enough (with no stamina for a heavy, a light move at once). Returns true if a move began.
	 */
	private static boolean resolve(Minecraft mc, Moveset moves) {
		LocalPlayer player = mc.player;
		Moveset.Move next = current != null ? moves.after(current) : last != null && sinceLast <= Swing.CHAIN_GRACE ? moves.after(last) : moves.light.get(0);
		float blend = current != null ? 3.0f : 2.0f;
		CombatPayload rules = Combat.rules(true);
		boolean canHeavy = player.hasInfiniteMaterials() || StaminaHud.stamina() >= rules.heavyCost();
		if (pressUp || !canHeavy) {
			// a tap: the next light move. With no stamina for a heavy there's nothing to wait for: the chain goes on (and goes
			// on while the key stays down)
			pressed = false;
			begin(mc, moves, next, blend, 0);
			return true;
		}
		if (pressHeld < Swing.CHARGE_AT) {
			return false;
		}
		if (current != null && current.heavy()) {
			// one heavy after another: the second once the first is over
			return false;
		}
		if (!player.hasInfiniteMaterials()) {
			StaminaHud.predict(rules.heavyCost());
		}
		pressed = false;
		spent = true;
		begin(mc, moves, moves.heavy, 3.0f, Math.min(idleWait, Swing.HEAVY_SKIP));
		return true;
	}

	private static void begin(Minecraft mc, Moveset moves, Moveset.Move move, float blendIn, int skip) {
		LocalPlayer player = mc.player;
		set = moves;
		current = move;
		weapon = player.getMainHandItem();
		time = skip;
		pace = moves.pace(player);
		PlayerAnimator.of(player).play(move.anim(), pace, Float.NaN, blendIn, 4.0f, skip);
		ClientPlayNetworking.send(new SwingPayload(move.anim().ordinal(), skip));
		player.resetAttackStrengthTicker();
		if (mc.gameMode != null) {
			mc.gameMode.stopDestroyBlock();
		}
	}

	public static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		boolean down = mc.options.keyAttack.isDown();
		if (!down) {
			spent = false;
		}
		if (pressed) {
			if (down && !pressUp) {
				pressHeld++;
			} else {
				pressUp = true;
			}
		}
		if (current == null) {
			if (sinceLast < 1000) {
				sinceLast++;
			}
			if (fresh > 0 && --fresh == 0 && freshQueued) {
				freshQueued = false;
				click(mc);
			}
			if (pressed) {
				Moveset moves = player == null ? null : Moveset.of(player.getMainHandItem());
				if (moves == null || !player.isAlive() || player.isUsingItem() || RollControl.busy() || !Combat.rules(true).on()) {
					pressed = false;
					return;
				}
				idleWait++;
				resolve(mc, moves);
			}
			return;
		}
		if (player == null || !player.isAlive() || !ItemStack.isSameItem(player.getMainHandItem(), weapon) || player.isUsingItem() || RollControl.busy()) {
			cancel();
			return;
		}
		float before = time;
		time += pace;
		// the weapon starts forward: the swish, and the step into the blow
		if (before < current.swingTick() && time >= current.swingTick()) {
			player.playSound(current.heavy() ? SoundEvents.PLAYER_ATTACK_SWEEP : SoundEvents.PLAYER_ATTACK_WEAK, current.heavy() ? 0.8f : 0.55f,
					(current.heavy() ? 0.7f : 1.25f) + player.getRandom().nextFloat() * 0.15f);
			if (current.lunge() > 0 && player.onGround() && !player.isShiftKeyDown()) {
				float yaw = player.getYRot() * Mth.DEG_TO_RAD;
				Vec3 v = player.getDeltaMovement();
				player.setDeltaMovement(v.x - Mth.sin(yaw) * current.lunge(), v.y, v.z + Mth.cos(yaw) * current.lunge());
			}
		}
		// the blow lands: feel it now, as the animation shows it, not a round trip later when the server says so
		if (before < current.hitTick() && time >= current.hitTick() && (aimedAtSomething(mc) || somethingToFight(player, current))) {
			HitFeel.predict(current.heavy());
		}
		if (time >= current.nextTick()) {
			if (pressed) {
				// a click queued mid-move: the next light move, or the heavy if the key is still held
				if (resolve(mc, set)) {
					return;
				}
				idleWait++;
			} else if (down && !spent && !current.heavy()) {
				// the key held with no stamina for a heavy: the chain goes on
				begin(mc, set, set.after(current), 3.0f, 0);
				return;
			}
		}
		if (time >= current.ticks()) {
			last = current.heavy() ? null : current;
			sinceLast = 0;
			current = null;
		}
	}

	/** The crosshair is on something that can be struck (an animal too: a swing strikes what it is aimed at). */
	private static boolean aimedAtSomething(Minecraft mc) {
		return mc.hitResult instanceof EntityHitResult hit && hit.getEntity().isAttackable() && hit.getEntity().isAlive();
	}

	/** Whether anything that fights back stands in the move's arc, within reach: then a click swings even with a block under the crosshair. */
	private static boolean somethingToFight(LocalPlayer player, Moveset.Move move) {
		double reach = player.entityInteractionRange() + move.reach() + 0.5;
		Vec3 eye = player.getEyePosition();
		float yaw = player.getYRot();
		for (Entity entity : player.level().getEntities(player, player.getBoundingBox().inflate(reach + 1), e -> e instanceof LivingEntity && e.isAlive())) {
			if (!(entity instanceof Enemy) && !(entity instanceof Player) || entity instanceof OwnableEntity owned && owned.getOwner() == player) {
				continue;
			}
			Vec3 to = entity.getBoundingBox().getCenter().subtract(eye);
			if (to.length() > reach + entity.getBbWidth()) {
				continue;
			}
			float bearing = Mth.wrapDegrees((float) (Mth.atan2(-to.x, to.z) * Mth.RAD_TO_DEG) - yaw);
			if (Math.abs(bearing) <= move.halfAngle()) {
				return true;
			}
		}
		return false;
	}
}
