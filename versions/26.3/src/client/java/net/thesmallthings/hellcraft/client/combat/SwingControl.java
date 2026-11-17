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
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.client.combat.anim.PlayerAnimator;
import net.thesmallthings.hellcraft.combat.Combat;
import net.thesmallthings.hellcraft.combat.Moveset;
import net.thesmallthings.hellcraft.combat.Swing;
import net.thesmallthings.hellcraft.net.CombatPayload;
import net.thesmallthings.hellcraft.net.SwingPayload;
import org.jetbrains.annotations.Nullable;

/**
 * This client's own swings (combat/Swing on the server, combat/Moveset for what each weapon does). With a weapon that
 * has moves in hand, the attack key no longer pokes at whatever is under the crosshair: it swings.
 *
 * <ul>
 *     <li><b>A click</b> starts the next light move of the chain at once (the wind-up plays here and now; the server is
 *     told and lands the blow when the animation does).</li>
 *     <li><b>Clicking again</b> during a move queues the next, which begins the moment this one frees it: a combo.</li>
 *     <li><b>Holding</b> the key through the first ticks of a light move's wind-up turns it into the heavy move (if there
 *     is stamina for it): no wait on a tap, no second key. With no stamina, holding just keeps the chain going.</li>
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
	/** The key has been down since the move began (so it may become the heavy one). */
	private static boolean held;
	/** A click came mid-move: the next begins when this one frees it. Counts the ticks it has waited. */
	private static int queued = -1;
	/** After a heavy move the key must come up before holding it does anything again. */
	private static boolean spent;
	@Nullable
	private static Moveset.Move last;
	private static int sinceLast = 1000;

	/** A move is playing: the attack key isn't vanilla's for now (no mining what's behind the target). */
	public static boolean busy() {
		return current != null;
	}

	/** Winding up: before the blow has gone forward (a guard can't cut in). */
	public static boolean windingUp() {
		return current != null && time < current.hitTick();
	}

	public static void reset() {
		current = null;
		last = null;
		queued = -1;
		sinceLast = 1000;
		spent = false;
	}

	/** A roll or a raised guard takes over. */
	public static void cancel() {
		if (current != null) {
			last = null;
			current = null;
			queued = -1;
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
				|| RollControl.rolling() || !ClientPlayNetworking.canSend(SwingPayload.TYPE)) {
			return false;
		}
		Moveset moves = Moveset.of(player.getMainHandItem());
		if (moves == null) {
			return false;
		}
		if (current != null) {
			// mid-move: a click queues the next
			queued = 0;
			held = false;
			return true;
		}
		if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK && !somethingToFight(player, moves.light.get(0))) {
			// a block in front and nothing to fight: mine it, as ever
			return false;
		}
		Moveset.Move next = last != null && sinceLast <= Swing.CHAIN_GRACE ? moves.after(last) : moves.light.get(0);
		begin(mc, moves, next, 2.0f);
		return true;
	}

	private static void begin(Minecraft mc, Moveset moves, Moveset.Move move, float blendIn) {
		LocalPlayer player = mc.player;
		set = moves;
		current = move;
		weapon = player.getMainHandItem();
		time = 0;
		pace = moves.pace(player);
		held = mc.options.keyAttack.isDown() && !spent;
		queued = -1;
		PlayerAnimator.of(player).play(move.anim(), pace, Float.NaN, blendIn, 4.0f);
		ClientPlayNetworking.send(new SwingPayload(move.anim().ordinal()));
		player.resetAttackStrengthTicker();
		if (mc.gameMode != null) {
			mc.gameMode.stopDestroyBlock();
		}
	}

	public static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (!mc.options.keyAttack.isDown()) {
			spent = false;
		}
		if (current == null) {
			if (sinceLast < 1000) {
				sinceLast++;
			}
			return;
		}
		if (player == null || !player.isAlive() || !ItemStack.isSameItem(player.getMainHandItem(), weapon) || player.isUsingItem() || RollControl.rolling()) {
			cancel();
			return;
		}
		CombatPayload rules = Combat.rules(true);
		float before = time;
		time += pace;
		if (!mc.options.keyAttack.isDown()) {
			held = false;
		}
		// the key still down a few ticks into a light move: it becomes the heavy one
		if (held && !current.heavy() && time >= Swing.CHARGE_AT && before < current.hitTick() - 1) {
			held = false;
			if (player.hasInfiniteMaterials() || StaminaHud.stamina() >= rules.heavyCost()) {
				if (!player.hasInfiniteMaterials()) {
					StaminaHud.predict(rules.heavyCost());
				}
				spent = true;
				begin(mc, set, set.heavy, 3.0f);
				return;
			}
		}
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
		if (queued >= 0) {
			queued++;
		}
		boolean keep = queued >= 0 || mc.options.keyAttack.isDown() && !spent && !current.heavy();
		if (keep && time >= current.nextTick()) {
			// the chain goes on: a queued click, or the key held with no stamina for a heavy
			begin(mc, set, set.after(current), 3.0f);
			return;
		}
		if (time >= current.ticks()) {
			last = current.heavy() ? null : current;
			sinceLast = 0;
			current = null;
			queued = -1;
		}
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
