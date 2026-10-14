package net.thesmallthings.hellcraft.cutscene;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.Hearts;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.net.CutsceneDonePayload;
import net.thesmallthings.hellcraft.net.CutscenePayload;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Plays cutscenes to players (Cutscene): tells their client to play one, keeps them from harm until it ends (or they
 * skip it), and remembers that they have seen it, so next time they may skip it.
 */
public final class CutsceneManager {
	private CutsceneManager() {
	}

	/** Who is watching what, until which server tick (a lost "done" doesn't leave anyone unhurtable for long). */
	private static final Map<UUID, Long> WATCHING = new HashMap<>();
	private static long now;

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(CutscenePayload.TYPE, CutscenePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(CutsceneDonePayload.TYPE, CutsceneDonePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CutsceneDonePayload.TYPE, (payload, context) -> WATCHING.remove(context.player().getUUID()));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayer p && WATCHING.containsKey(p.getUUID())));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			now++;
			WATCHING.values().removeIf(until -> until < now);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> WATCHING.remove(handler.getPlayer().getUUID()));
	}

	/** Plays a cutscene to a player, filmed around an anchor (a point, and the way it faces). */
	public static void play(ServerPlayer player, Cutscene cutscene, Vec3 anchor, float yaw) {
		if (!ServerPlayNetworking.canSend(player, CutscenePayload.TYPE)) {
			return;
		}
		HellState.Soul soul = Hearts.soul(player);
		boolean seen = !soul.cutscenes.add(cutscene.id());
		HellState.get(player.level().getServer()).setDirty();
		WATCHING.put(player.getUUID(), now + cutscene.ticks() + 40);
		ServerPlayNetworking.send(player, new CutscenePayload(cutscene.id(), anchor.x, anchor.y, anchor.z, yaw, seen));
		HellcraftMod.LOGGER.info("Cutscene {} for {}{}", cutscene.id(), player.getGameProfile().name(), seen ? " (can skip)" : "");
	}

	public static boolean watching(ServerPlayer player) {
		return WATCHING.containsKey(player.getUUID());
	}
}
