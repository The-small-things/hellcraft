package net.thesmallthings.hellcraft.hazard;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.mixin.MannequinAccess;
import net.thesmallthings.hellcraft.net.OnlookersPayload;
import net.thesmallthings.hellcraft.world.HellWorldgen;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.Purgatory;
import net.thesmallthings.hellcraft.world.Shrines;
import net.thesmallthings.hellcraft.world.Zone;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The onlookers: in Dante the dead keep stopping to stare at the living man walking among them. Now and then (about
 * every onlookerMinutes for each player) a shade stands far off, 40 to 64 blocks away on open ground where the player
 * can see it, and stares at them: it turns to follow them, crouches, lifts an arm. Come within 20 blocks and it is
 * simply gone; it goes by itself after a minute or two. It is lit by a faint glow of its own (the client draws it
 * brighter than the dark around it: layer/OnlookerGlow, told which entities are onlookers by OnlookersPayload). It wears the skin of a soul who has walked this Hell
 * before (one not online now, the fallen first), or a stranger's.
 *
 * An onlooker is a vanilla mannequin (it renders a player's skin), still, silent and unhurtable, tagged
 * hellcraft_onlooker and remembered only in memory: any found when a world loads are removed. In Hell's circles and on
 * Purgatory's island; never near a guardian's lair or a Rest. Config onlookers turns them off.
 */
public final class Onlookers {
	private Onlookers() {
	}

	public static final String TAG = "hellcraft_onlooker";
	/** How far off an onlooker stands, and how near a player comes before it fades. */
	static final double FROM = 40;
	static final double TO = 64;
	static final double FADE = 20;

	/** One onlooker: who it watches, when it fades by itself, and when it next does something. */
	private static final class Watch {
		final UUID viewer;
		final long until;
		long nextAct;
		int swings;

		Watch(UUID viewer, long until, long nextAct) {
			this.viewer = viewer;
			this.until = until;
			this.nextAct = nextAct;
		}
	}

	private static final Map<UUID, Watch> LIVE = new HashMap<>();
	/** The onlookers' entity ids (what the client knows them by). */
	private static final Map<UUID, Integer> IDS = new HashMap<>();

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(OnlookersPayload.TYPE, OnlookersPayload.CODEC);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			remember(handler.getPlayer());
			send(handler.getPlayer());
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> onLoad(entity));
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> commands(dispatcher));
	}

	/** An onlooker isn't kept: one loaded with a world (left from before a restart) goes at once. */
	static void onLoad(Entity entity) {
		if (entity.entityTags().contains(TAG) && !LIVE.containsKey(entity.getUUID())) {
			entity.discard();
		}
	}

	/** Tells a player which entities are onlookers, for their faint glow. */
	static void send(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, OnlookersPayload.TYPE)) {
			ServerPlayNetworking.send(player, new OnlookersPayload(List.copyOf(IDS.values())));
		}
	}

	private static void broadcast(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			send(player);
		}
	}

	/** Keeps the skin a player joined with, so onlookers can wear it while they're away. */
	static void remember(ServerPlayer player) {
		HellState state = HellState.get(player.level().getServer());
		HellState.Soul soul = state.soul(player.getUUID(), player.getGameProfile().name());
		for (Property p : player.getGameProfile().properties().get("textures")) {
			String signature = p.signature() == null ? "" : p.signature();
			if (!p.value().equals(soul.skin) || !signature.equals(soul.skinSignature)) {
				soul.skin = p.value();
				soul.skinSignature = signature;
				state.setDirty();
			}
		}
	}

	public static void tick(MinecraftServer server) {
		long now = server.getTickCount();
		if (now % 2 == 0) {
			watch(server, now);
		}
		if (now % 20 != 7 || !HellConfig.get().onlookers || !HellWorldgen.isInferno(server.overworld())) {
			return;
		}
		double chance = 1.0 / Math.max(20, HellConfig.get().onlookerMinutes * 60);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator() || watching(player.getUUID()) || !where(player) || player.getRandom().nextDouble() >= chance) {
				continue;
			}
			spawnFor(player);
		}
	}

	/** Whether onlookers come to a player where they stand: Hell's circles (not the Dark Wood) or Purgatory. */
	static boolean where(ServerPlayer player) {
		ServerLevel level = player.level();
		if (level.dimension() == Purgatory.LEVEL) {
			return true;
		}
		if (level != level.getServer().overworld() || InfernoGeometry.nearLair(player.getX(), player.getZ(), InfernoGeometry.LAIR_CLEARANCE + 32)) {
			return false;
		}
		Zone zone = InfernoGeometry.zoneAt(player.getX(), player.getZ());
		return zone != Zone.DARK_WOOD && zone != Zone.JUDECCA;
	}

	private static boolean watching(UUID viewer) {
		return LIVE.values().stream().anyMatch(w -> w.viewer.equals(viewer));
	}

	/** Puts an onlooker somewhere the player can see it; null if no spot would do this time. */
	@Nullable
	public static Mannequin spawnFor(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 at = site(level, player);
		return at == null ? null : spawnAt(level, at, profileFor(level.getServer(), player.getUUID()), player);
	}

	/** A spot 40 to 64 blocks off, mostly ahead of the player, on dry open ground they have a clear line to. */
	@Nullable
	static Vec3 site(ServerLevel level, ServerPlayer player) {
		RandomSource random = level.getRandom();
		Vec3 eyes = player.getEyePosition();
		double facing = Math.toRadians(player.getYRot() + 90);
		for (int i = 0; i < 20; i++) {
			double a = facing + (random.nextDouble() - 0.5) * Math.toRadians(150);
			double d = FROM + random.nextDouble() * (TO - FROM);
			int x = Mth.floor(player.getX() + Math.cos(a) * d);
			int z = Mth.floor(player.getZ() + Math.sin(a) * d);
			if (!level.isLoaded(new BlockPos(x, 0, z))) {
				continue;
			}
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos feet = new BlockPos(x, ground, z);
			boolean dry = level.getFluidState(feet).isEmpty() && level.getFluidState(feet.below()).isEmpty();
			if (!dry || !level.getBlockState(feet.below()).isSolid() || !level.isEmptyBlock(feet.above())
					|| InfernoGeometry.nearLair(x, z, InfernoGeometry.LAIR_CLEARANCE + 12) || Shrines.near(level, feet)) {
				continue;
			}
			Vec3 head = new Vec3(x + 0.5, ground + 1.6, z + 0.5);
			if (level.clip(new ClipContext(eyes, head, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS) {
				return new Vec3(x + 0.5, ground, z + 0.5);
			}
		}
		return null;
	}

	/**
	 * Whose skin an onlooker wears: a soul who has played here and isn't online (the fallen, ghosts or souls who've lost
	 * hearts, first), else a stranger (Minecraft's own skins).
	 */
	static ResolvableProfile profileFor(MinecraftServer server, UUID viewer) {
		List<Map.Entry<UUID, HellState.Soul>> fallen = new ArrayList<>();
		List<Map.Entry<UUID, HellState.Soul>> others = new ArrayList<>();
		for (Map.Entry<UUID, HellState.Soul> e : HellState.get(server).souls().entrySet()) {
			HellState.Soul s = e.getValue();
			if (s.skin.isEmpty() || e.getKey().equals(viewer) || server.getPlayerList().getPlayer(e.getKey()) != null) {
				continue;
			}
			(s.ghost || s.hearts < HellConfig.get().startHearts ? fallen : others).add(e);
		}
		RandomSource random = server.overworld().getRandom();
		List<Map.Entry<UUID, HellState.Soul>> pool = !fallen.isEmpty() && (others.isEmpty() || random.nextFloat() < 0.7f) ? fallen : others;
		if (pool.isEmpty()) {
			return ResolvableProfile.createResolved(new GameProfile(UUID.randomUUID(), "Shade"));
		}
		Map.Entry<UUID, HellState.Soul> e = pool.get(random.nextInt(pool.size()));
		return skinned(e.getKey(), e.getValue().name, e.getValue().skin, e.getValue().skinSignature);
	}

	static ResolvableProfile skinned(UUID id, String name, String skin, String signature) {
		Property textures = signature.isEmpty() ? new Property("textures", skin) : new Property("textures", skin, signature);
		String shown = name.isEmpty() || name.length() > 16 ? "Shade" : name;
		return ResolvableProfile.createResolved(new GameProfile(id, shown, new PropertyMap(ImmutableMultimap.of("textures", textures))));
	}

	/** Brings out an onlooker at this spot, wearing this skin, watching this player (or nobody, for the self-test). */
	static Mannequin spawnAt(ServerLevel level, Vec3 at, ResolvableProfile profile, @Nullable ServerPlayer viewer) {
		Mannequin m = EntityTypes.MANNEQUIN.create(level, EntitySpawnReason.EVENT);
		if (m == null) {
			return null;
		}
		MannequinAccess access = (MannequinAccess) m;
		access.hellcraft$setProfile(profile);
		access.hellcraft$setImmovable(true);
		access.hellcraft$setHideDescription(true);
		m.setPermanentlyInvulnerable(true);
		m.setSilent(true);
		m.addTag(TAG);
		float yaw = viewer == null ? 0 : yawTowards(at, viewer.position());
		m.snapTo(at.x, at.y, at.z, yaw, 0);
		m.setYHeadRot(yaw);
		m.setYBodyRot(yaw);
		long now = level.getServer().getTickCount();
		RandomSource random = level.getRandom();
		LIVE.put(m.getUUID(), new Watch(viewer == null ? new UUID(0, 0) : viewer.getUUID(), now + 1200 + random.nextInt(1200), now + 40 + random.nextInt(60)));
		level.addFreshEntity(m);
		IDS.put(m.getUUID(), m.getId());
		broadcast(level.getServer());
		return m;
	}

	private static float yawTowards(Vec3 from, Vec3 to) {
		return (float) (Math.toDegrees(Math.atan2(to.z - from.z, to.x - from.x)) - 90);
	}

	/** Every other tick: each onlooker turns to its player, now and then crouches or lifts an arm, and fades when it should. */
	private static void watch(MinecraftServer server, long now) {
		int before = IDS.size();
		Iterator<Map.Entry<UUID, Watch>> it = LIVE.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Watch> e = it.next();
			Watch w = e.getValue();
			Entity entity = find(server, e.getKey());
			if (!(entity instanceof Mannequin m) || entity.isRemoved()) {
				it.remove();
				IDS.remove(e.getKey());
				continue;
			}
			ServerLevel level = (ServerLevel) m.level();
			ServerPlayer viewer = server.getPlayerList().getPlayer(w.viewer);
			ServerPlayer near = null;
			for (ServerPlayer p : level.players()) {
				if (!p.isSpectator() && p.distanceToSqr(m) < FADE * FADE) {
					near = p;
				}
			}
			boolean lost = viewer == null || viewer.level() != level || viewer.distanceToSqr(m) > 110 * 110;
			if (near != null || now > w.until || (lost && !w.viewer.equals(new UUID(0, 0)))) {
				fade(m);
				it.remove();
				continue;
			}
			if (viewer != null) {
				float yaw = yawTowards(m.position(), viewer.position());
				double dy = viewer.getEyeY() - m.getEyeY();
				float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(m.distanceToSqr(viewer.getX(), m.getY(), viewer.getZ()))));
				m.setYRot(yaw);
				m.setYHeadRot(yaw);
				m.setYBodyRot(yaw);
				m.setXRot(Mth.clamp(pitch, -40, 40));
			}
			if (w.swings > 0 && now % 6 == 0) {
				m.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
				w.swings--;
			}
			if (now >= w.nextAct) {
				RandomSource random = level.getRandom();
				switch (random.nextInt(4)) {
					case 0 -> m.setPose(m.getPose() == Pose.CROUCHING ? Pose.STANDING : Pose.CROUCHING);
					case 1 -> w.swings = 1;
					case 2 -> w.swings = 3 + random.nextInt(3);
					default -> m.setPose(Pose.STANDING);
				}
				w.nextAct = now + 40 + random.nextInt(100);
			}
		}
		if (IDS.size() != before) {
			broadcast(server);
		}
	}

	@Nullable
	private static Entity find(MinecraftServer server, UUID id) {
		for (ServerLevel level : server.getAllLevels()) {
			Entity e = level.getEntity(id);
			if (e != null) {
				return e;
			}
		}
		return null;
	}

	/** Simply gone: no sound, no smoke. */
	static void fade(Mannequin m) {
		IDS.remove(m.getUUID());
		m.discard();
	}

	/**
	 * For the smoke test: an onlooker wearing a soul's skin stands where it's put, can't be hurt or moved, fades when
	 * told to, and one left over from before a restart is cleared away.
	 */
	public static String selfTest(MinecraftServer server) {
		ServerLevel level = server.overworld();
		List<String> wrong = new ArrayList<>();
		double x = InfernoGeometry.scaled(2425);
		double z = 30;
		level.getChunk(Mth.floor(x) >> 4, Mth.floor(z) >> 4);
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
		UUID soul = UUID.nameUUIDFromBytes("hellcraft-onlooker-test".getBytes());
		Mannequin m = spawnAt(level, new Vec3(x, ground, z), skinned(soul, "Farinata", "e30=", ""), null);
		if (m == null) {
			return "Onlookers test FAILED: no mannequin";
		}
		if (!m.entityTags().contains(TAG) || !m.isPermanentlyInvulnerable() || !m.getProfile().name().orElse("").equals("Farinata")) {
			wrong.add("not a still, unhurtable shade in the soul's skin");
		}
		if (m.hurtServer(level, level.damageSources().generic(), 20) || m.isRemoved()) {
			wrong.add("it could be hurt");
		}
		fade(m);
		LIVE.remove(m.getUUID());
		if (!m.isRemoved()) {
			wrong.add("it didn't fade");
		}
		Mannequin left = spawnAt(level, new Vec3(x + 2, ground, z), skinned(soul, "Farinata", "e30=", ""), null);
		LIVE.remove(left.getUUID());
		IDS.remove(left.getUUID());
		onLoad(left);
		if (!left.isRemoved()) {
			left.discard();
			wrong.add("one left from before a restart stays");
		}
		return wrong.isEmpty() ? "Onlookers test OK: a shade in a soul's skin stood, couldn't be hurt, faded, and a stray was cleared"
				: "Onlookers test FAILED: " + String.join(", ", wrong);
	}

	private static void commands(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("hellcraft")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("onlooker")
						.then(Commands.literal("spawn")
								.executes(ctx -> spawnCommand(ctx.getSource(), ctx.getSource().getPlayerOrException()))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(ctx -> spawnCommand(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
						.then(Commands.literal("test").executes(ctx -> {
							String result = selfTest(ctx.getSource().getServer());
							HellcraftMod.LOGGER.info(result);
							ctx.getSource().sendSuccess(() -> Component.literal(result), false);
							return 1;
						}))));
	}

	private static int spawnCommand(CommandSourceStack source, ServerPlayer player) {
		Mannequin m = spawnFor(player);
		if (m == null) {
			source.sendFailure(Component.literal("No open spot 40 to 64 blocks from " + player.getGameProfile().name() + " that they can see."));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("A shade stands " + (int) m.distanceTo(player) + " blocks from " + player.getGameProfile().name()
				+ ", wearing " + m.getProfile().name().orElse("a stranger's") + "'s skin."), false);
		return 1;
	}
}
