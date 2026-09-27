package net.thesmallthings.hellcraft.hazard.lucifer;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.world.HellWorldgen;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * At the very bottom of Judecca waits the Emperor of the kingdom dolorous. Reach his pit and the ice
 * closes behind you. See {@link LuciferFight} for the fight itself.
 */
public final class LuciferManager {
	private LuciferManager() {
	}

	static final String TAG = "hellcraft_lucifer";
	private static final double TRIGGER_RADIUS = 28.0;

	@Nullable
	private static LuciferFight fight;
	private static final List<Entity> strays = new ArrayList<>();
	private static int ticks;

	public static boolean isLucifer(Entity entity) {
		return entity.entityTags().contains(TAG);
	}

	public static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> fight == null || fight.allowDamage(entity));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (fight != null) {
				fight.afterDamage(entity, source, taken);
			}
		});
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> fight == null || fight.allowDeath(entity));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (fight != null) {
				fight.onDeath(entity);
			}
		});
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
				!(level instanceof ServerLevel serverLevel) || !HellWorldgen.isInferno(serverLevel) || !LuciferArena.isSealBlock(serverLevel, pos));
		// leftovers from a fight interrupted by a restart
		// never more than one Lucifer: anything tagged as him that the current fight doesn't own goes
		// (ownership is checked a tick later: a freshly spawned body loads before the fight records its UUID)
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (isLucifer(entity)) {
				strays.add(entity);
			}
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ServerLevel level = server.overworld();
			if (HellWorldgen.isInferno(level) && HellState.get(server).arenaSealed) {
				// a fight was interrupted by a shutdown or crash
				LuciferArena.unseal(level);
				LuciferArena.forceLoad(level, false);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (fight != null) {
				fight.stop();
				fight = null;
			}
		});
	}

	public static void tick(MinecraftServer server) {
		if (!strays.isEmpty()) {
			for (Entity entity : strays) {
				if (fight == null || !fight.owns(entity.getUUID())) {
					entity.discard();
				}
			}
			strays.clear();
		}
		if (fight != null) {
			fight.tick();
			if (fight.done()) {
				fight = null;
			}
			return;
		}
		if (++ticks % 20 != 0 || !HellConfig.get().lucifer) {
			return;
		}
		ServerLevel level = server.overworld();
		if (!HellWorldgen.isInferno(level) || level.getGameTime() < HellState.get(server).luciferNextSpawn) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && !p.isCreative() && p.getY() < InfernoGeometry.PIT_FLOOR_Y + 12
					&& p.getX() * p.getX() + p.getZ() * p.getZ() < TRIGGER_RADIUS * TRIGGER_RADIUS) {
				start(server, false);
				return;
			}
		}
	}

	/** Starts a fight now (used by the trigger and by {@code /hellcraft lucifer summon}). */
	public static String start(MinecraftServer server, boolean debug) {
		if (fight != null) {
			return "Lucifer is already fighting (" + fight.phaseName() + ").";
		}
		ServerLevel level = server.overworld();
		if (!HellWorldgen.isInferno(level)) {
			return "This world is not an Inferno world.";
		}
		fight = new LuciferFight(level, debug);
		fight.start();
		return "Lucifer awakens.";
	}

	public static String skip() {
		if (fight == null) {
			return "No fight in progress.";
		}
		fight.skip();
		return "Lucifer phase: " + fight.phaseName();
	}

	public static String stop() {
		if (fight == null) {
			return "No fight in progress.";
		}
		ServerLevel level = fight.level();
		fight.stop();
		fight = null;
		// don't let the proximity trigger wake him again the very next second
		HellState state = HellState.get(level.getServer());
		state.luciferNextSpawn = level.getGameTime() + HellConfig.get().luciferRetryMinutes * 1200L;
		state.setDirty();
		return "The fight is over.";
	}

	public static String status() {
		return fight == null ? "No fight in progress." : fight.status();
	}

	public static String attack(String name) {
		if (fight == null) {
			return "No fight in progress.";
		}
		LuciferAttacks.Attack attack;
		try {
			attack = LuciferAttacks.Attack.valueOf(name.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return "Unknown attack: " + name;
		}
		return fight.forceAttack(attack) ? "Lucifer uses " + attack + "." : "No target in the pit.";
	}
}
