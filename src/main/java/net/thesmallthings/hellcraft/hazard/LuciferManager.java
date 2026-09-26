package net.thesmallthings.hellcraft.hazard;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.world.HellWorldgen;
import net.thesmallthings.hellcraft.world.InfernoGeometry;

/**
 * At the very bottom of Judecca, frozen to the waist, waits the three-faced Emperor of the kingdom
 * dolorous. Vanilla's three-headed Wither plays him.
 */
public final class LuciferManager {
	private LuciferManager() {
	}

	private static final String TAG = "hellcraft_lucifer";
	private static final double TRIGGER_RADIUS = 36.0;
	private static int ticks;

	public static boolean isLucifer(Entity entity) {
		return entity.getTags().contains(TAG);
	}

	public static void tick(MinecraftServer server) {
		if (++ticks % 40 != 0 || !HellConfig.get().lucifer) {
			return;
		}
		ServerLevel level = server.overworld();
		if (!HellWorldgen.isInferno(level)) {
			return;
		}
		HellState state = HellState.get(server);
		if (state.luciferId != null) {
			Entity existing = level.getEntity(state.luciferId);
			if (existing != null && existing.isAlive()) {
				return;
			}
		}
		if (level.getGameTime() < state.luciferNextSpawn) {
			return;
		}
		ServerPlayer challenger = null;
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && !p.isCreative() && p.getY() < InfernoGeometry.PIT_FLOOR_Y + 24
					&& p.getX() * p.getX() + p.getZ() * p.getZ() < TRIGGER_RADIUS * TRIGGER_RADIUS) {
				challenger = p;
				break;
			}
		}
		if (challenger == null) {
			return;
		}
		WitherBoss lucifer = EntityType.WITHER.create(level);
		if (lucifer == null) {
			return;
		}
		lucifer.moveTo(0.5, InfernoGeometry.PIT_FLOOR_Y + 4, 0.5, 0.0f, 0.0f);
		lucifer.addTag(TAG);
		lucifer.setPersistenceRequired();
		AttributeInstance health = lucifer.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.setBaseValue(HellConfig.get().luciferHealth);
		}
		lucifer.makeInvulnerable();
		lucifer.setCustomName(Component.literal("Lucifer").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
		level.addFreshEntity(lucifer);
		state.luciferId = lucifer.getUUID();
		state.luciferNextSpawn = level.getGameTime() + HellConfig.get().luciferCooldownMinutes * 1200L;
		state.setDirty();
		server.getPlayerList().broadcastSystemMessage(Component.literal("The Emperor of the kingdom dolorous stirs beneath the ice...")
				.withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), false);
	}

	public static void onDefeated(MinecraftServer server, ServerPlayer killer) {
		server.getPlayerList().broadcastSystemMessage(Component.literal(killer.getGameProfile().getName()
				+ " has cast down Lucifer. \"Thence we came forth to rebehold the stars.\"").withStyle(ChatFormatting.GOLD), false);
		HellState state = HellState.get(server);
		state.luciferId = null;
		state.setDirty();
	}
}
