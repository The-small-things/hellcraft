package net.thesmallthings.hellcraft.client.combat;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.thesmallthings.hellcraft.blood.HellWeapons;
import net.thesmallthings.hellcraft.client.combat.anim.PlayerAnimator;
import net.thesmallthings.hellcraft.combat.Combat;
import net.thesmallthings.hellcraft.combat.Guard;
import net.thesmallthings.hellcraft.entity.boss.gen.PlayerMovesAnim;
import net.thesmallthings.hellcraft.net.ArtPayload;
import net.thesmallthings.hellcraft.net.CombatPayload;
import net.thesmallthings.hellcraft.net.FeelPayload;
import net.thesmallthings.hellcraft.net.GuardPayload;
import net.thesmallthings.hellcraft.net.MovePayload;
import net.thesmallthings.hellcraft.net.StaminaPayload;

/**
 * The client side of Hellcraft's combat (combat/Combat): the server's rules, every player's moves ({@link PlayerAnimator},
 * drawn on the player model through mixin/HumanoidModelMixin and in the hand through mixin/FirstPersonHandsMixin), this
 * player's roll ({@link RollControl}), the stamina bar ({@link StaminaHud}) and what blows feel like ({@link HitFeel}).
 */
public final class CombatClient {
	private CombatClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(CombatPayload.TYPE, (payload, context) -> {
			Combat.clientRules(payload);
			StaminaHud.reset();
		});
		ClientPlayNetworking.registerGlobalReceiver(StaminaPayload.TYPE, (payload, context) -> StaminaHud.receive(payload.stamina()));
		ClientPlayNetworking.registerGlobalReceiver(FeelPayload.TYPE, (payload, context) -> HitFeel.receive(payload));
		ClientPlayNetworking.registerGlobalReceiver(MovePayload.TYPE, (payload, context) -> move(context.client(), payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			Combat.clientRules(null);
			PlayerAnimator.clear();
			FirstPersonEase.reset();
			RollControl.reset();
			SwingControl.reset();
			HitFeel.reset();
			StaminaHud.reset();
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.isPaused()) {
				return;
			}
			RollControl.tick(client);
			tickGuard(client);
			SwingControl.tick(client);
			PlayerAnimator.tickAll(client.level);
			net.thesmallthings.hellcraft.combat.RollShape.tick(true);
			HitFeel.tick(client);
			StaminaHud.tick(client);
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.FOOD_BAR, StaminaHud.ID, new StaminaHud());
		// each player's own choice: Hellcraft's animations for getting about and using items, or vanilla's
		net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
				net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("hellcraft-client")
						.then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("animations")
								.executes(ctx -> animations(ctx.getSource(), null))
								.then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("on")
										.executes(ctx -> animations(ctx.getSource(), true)))
								.then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("off")
										.executes(ctx -> animations(ctx.getSource(), false))))));
	}

	private static int animations(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source, Boolean on) {
		if (on != null) {
			net.thesmallthings.hellcraft.client.combat.anim.Settings.setSmoothAnimations(on);
			PlayerAnimator.clear();
		}
		boolean now = net.thesmallthings.hellcraft.client.combat.anim.Settings.smoothAnimations();
		source.sendFeedback(net.minecraft.network.chat.Component.literal(now
				? "Hellcraft's animations are on: walking, running, jumping, landing, bows, shields and food are animated, and sudden pose changes are eased. Turn them off with /hellcraft-client animations off."
				: "Hellcraft's animations are off: players get about as in vanilla (combat moves still play). Turn them on with /hellcraft-client animations on."));
		return 1;
	}

	/** Someone's move, from the server. */
	private static void move(Minecraft client, MovePayload payload) {
		if (client.level == null) {
			return;
		}
		Entity entity = client.level.getEntity(payload.entity());
		PlayerMovesAnim[] moves = PlayerMovesAnim.values();
		if (!(entity instanceof Player) || payload.move() < 0 || payload.move() >= moves.length) {
			return;
		}
		PlayerMovesAnim move = moves[payload.move()];
		float heading = payload.x() == 0 && payload.z() == 0 ? Float.NaN : (float) (Mth.atan2(-payload.x(), payload.z()) * Mth.RAD_TO_DEG);
		PlayerAnimator.of(entity).play(move, payload.speed(), heading, move == PlayerMovesAnim.PARRY ? 1.0f : 2.0f, move == PlayerMovesAnim.ROLL ? 3.0f : 4.0f);
		if (move == PlayerMovesAnim.ROLL && entity != client.player) {
			net.thesmallthings.hellcraft.combat.RollShape.start((Player) entity);
		}
	}

	/**
	 * A right-click (or a held one) that vanilla found no use for (mixin/MinecraftMixin, at the end of startUseItem): with
	 * a weapon in the main hand, it raises the guard. Raised here at once, and the server is told.
	 */
	public static void afterUse(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.gameMode == null || mc.gameMode.isDestroying() || player.isHandsBusy() || !Combat.rules(true).on()
				|| !Guard.mayRaise(player) || !ClientPlayNetworking.canSend(GuardPayload.TYPE)) {
			return;
		}
		if (!RollControl.guardReady()) {
			// too early in a roll: remembered, and raised the moment the roll allows (tick)
			guardQueued = GUARD_BUFFER;
			return;
		}
		raiseGuard(player);
	}

	/** Ticks a guard pressed too early in a roll is remembered. */
	private static final int GUARD_BUFFER = 10;
	private static int guardQueued;

	/** Raises the guard now, cutting short a swing (at any moment) or the end of a roll. */
	private static void raiseGuard(LocalPlayer player) {
		guardQueued = 0;
		SwingControl.cancel();
		RollControl.stop();
		player.startUsingItem(InteractionHand.MAIN_HAND);
		ClientPlayNetworking.send(GuardPayload.INSTANCE);
	}

	/** Each tick: a guard remembered from a roll goes up as soon as it may. */
	private static void tickGuard(Minecraft mc) {
		if (guardQueued <= 0) {
			return;
		}
		guardQueued--;
		LocalPlayer player = mc.player;
		if (player == null || mc.gui.screen() != null || !Combat.rules(true).on() || !Guard.mayRaise(player)
				|| !ClientPlayNetworking.canSend(GuardPayload.TYPE)) {
			return;
		}
		if (RollControl.guardReady()) {
			raiseGuard(player);
		}
	}

	/**
	 * Before the game reads the keys this tick (mixin/MinecraftMixin). While a weapon's guard is up vanilla throws attack
	 * clicks away; a hell weapon looses its Blood Art on one instead.
	 */
	public static void beforeKeys(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.gui.screen() != null || !Combat.rules(true).on()) {
			return;
		}
		if (player.isUsingItem() && Guard.guarding(player) && HellWeapons.of(player.getMainHandItem()) != null) {
			boolean pressed = false;
			while (mc.options.keyAttack.consumeClick()) {
				pressed = true;
			}
			if (pressed && ClientPlayNetworking.canSend(ArtPayload.TYPE)) {
				ClientPlayNetworking.send(ArtPayload.INSTANCE);
			}
		}
	}
}
