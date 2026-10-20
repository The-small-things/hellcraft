package net.thesmallthings.hellcraft.world;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.Hearts;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.net.Dialogue;
import net.thesmallthings.hellcraft.net.SecretsPayload;
import net.thesmallthings.hellcraft.util.Journey;
import net.thesmallthings.hellcraft.world.feature.SecretWayFeature;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Using the secret ways down (SecretWays): drop into a mouth (or right-click anything close to it) and a soul-fire draught
 * lifts you for a moment, then sets you down on its cairn in the circle below, slowly, unhurt. Each soul remembers the
 * ones it has found (HellState.Soul.secrets); the Map of Hell marks them and the Journal counts them (SecretsPayload).
 */
public final class SecretWayLift {
	private SecretWayLift() {
	}

	/** Ticks of the float before you are set down, and of the protection after. */
	private static final int FLOAT = 30;
	private static final int SAFE_AFTER = 80;
	/** How near a mouth a right-click counts. */
	private static final int TOUCH = 4;

	private record Transit(SecretWays.Site site, long until) {
	}

	private static final Map<UUID, Transit> CARRIED = new HashMap<>();
	private static final Map<UUID, Long> SAFE = new HashMap<>();
	/** The secret each player was last told was nearby (so the hint isn't repeated while they stand there). */
	private static final Map<UUID, String> HINTED = new HashMap<>();

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(SecretsPayload.TYPE, SecretsPayload.CODEC);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.getPlayer()));
		ServerTickEvents.END_SERVER_TICK.register(SecretWayLift::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayer p && protectedNow(p)));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer sp) || level.dimension() != Level.OVERWORLD || sp.isSpectator()
					|| !HellConfig.get().secretWays || CARRIED.containsKey(sp.getUUID())) {
				return InteractionResult.PASS;
			}
			BlockPos at = hit.getBlockPos();
			SecretWays.Site site = nearest(at.getX() + 0.5, at.getZ() + 0.5);
			if (site == null || !HellWorldgen.isInferno(sp.level())) {
				return InteractionResult.PASS;
			}
			int mx = site.worldX(site.kind().mouthF(), 0);
			int mz = site.worldZ(site.kind().mouthF(), 0);
			if (Math.abs(at.getX() - mx) > TOUCH || Math.abs(at.getZ() - mz) > TOUCH || Math.abs(at.getY() - site.y()) > 8) {
				return InteractionResult.PASS;
			}
			start(sp, site);
			return InteractionResult.SUCCESS;
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> commands(dispatcher));
	}

	/** The secret whose mouth is nearest a point, within a few dozen blocks; null if none is. */
	@Nullable
	public static SecretWays.Site nearest(double x, double z) {
		SecretWays.Site best = null;
		double bestD = 48 * 48;
		for (SecretWays.Site s : SecretWays.all()) {
			double d = (s.x() - x) * (s.x() - x) + (s.z() - z) * (s.z() - z);
			if (d < bestD) {
				bestD = d;
				best = s;
			}
		}
		return best;
	}

	private static boolean protectedNow(ServerPlayer player) {
		if (CARRIED.containsKey(player.getUUID())) {
			return true;
		}
		Long until = SAFE.get(player.getUUID());
		return until != null && player.level().getGameTime() < until;
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		long now = level.getGameTime();
		if (!CARRIED.isEmpty()) {
			for (Map.Entry<UUID, Transit> e : List.copyOf(CARRIED.entrySet())) {
				ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
				if (p == null || p.level() != level) {
					CARRIED.remove(e.getKey());
					continue;
				}
				// a ring of soul fire spirals up round you as you float
				double a = now * 0.6;
				level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX() + Math.cos(a) * 0.9, p.getY() + 0.3 + (now % 10) * 0.18,
						p.getZ() + Math.sin(a) * 0.9, 2, 0.05, 0.05, 0.05, 0.01);
				level.sendParticles(ParticleTypes.SOUL, p.getX(), p.getY() + 0.8, p.getZ(), 1, 0.4, 0.6, 0.4, 0.02);
				p.resetFallDistance();
				if (now >= e.getValue().until()) {
					CARRIED.remove(e.getKey());
					land(level, p, e.getValue().site());
				}
			}
		}
		if (server.getTickCount() % 5 != 0 || !HellConfig.get().secretWays || !HellWorldgen.isInferno(level)) {
			return;
		}
		boolean ambient = server.getTickCount() % 20 == 0;
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator() || CARRIED.containsKey(p.getUUID())) {
				continue;
			}
			SecretWays.Site site = nearest(p.getX(), p.getZ());
			if (site == null) {
				HINTED.remove(p.getUUID());
				continue;
			}
			int bx = p.getBlockX();
			int bz = p.getBlockZ();
			if (site.mouthAt(bx, bz) && p.getY() <= site.y() + 0.8 && p.getY() >= site.y() - 8) {
				start(p, site);
				continue;
			}
			double d = Math.hypot(p.getX() - site.x() - 0.5, p.getZ() - site.z() - 0.5);
			if (ambient && d < 32) {
				// a thread of souls rises from the mouth: something here goes down
				double mx = site.worldX(site.kind().mouthF(), 0) + 0.5;
				double mz = site.worldZ(site.kind().mouthF(), 0) + 0.5;
				level.sendParticles(site.kind() == SecretWays.Kind.WHIRLPOOL ? ParticleTypes.BUBBLE_POP : ParticleTypes.SOUL, mx, site.y() + 1.2, mz,
						4, 0.6, 0.4, 0.6, 0.01);
			}
			if (d < site.kind().radius + 3 && !site.id().equals(HINTED.get(p.getUUID()))) {
				HINTED.put(p.getUUID(), site.id());
				boolean found = Hearts.soul(p).secrets.contains(site.id());
				p.sendOverlayMessage(Component.literal(found ? "A secret way down to " + site.place() + ". " + site.kind().how
						: site.kind().title + ". Something here leads down: " + site.kind().how.substring(0, 1).toLowerCase(Locale.ROOT)
						+ site.kind().how.substring(1)).withStyle(ChatFormatting.DARK_AQUA));
			} else if (d > site.kind().radius + 12 && site.id().equals(HINTED.get(p.getUUID()))) {
				HINTED.remove(p.getUUID());
			}
		}
	}

	/** The way takes a player: a moment's float in soul fire, then down. */
	public static void start(ServerPlayer player, SecretWays.Site site) {
		ServerLevel level = player.level();
		CARRIED.put(player.getUUID(), new Transit(site, level.getGameTime() + FLOAT));
		player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, FLOAT, 0, true, false, false));
		player.resetFallDistance();
		level.playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.6f, 0.6f);
		level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7f, 0.5f);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 0.5, player.getZ(), 30, 0.6, 0.3, 0.6, 0.04);
		player.sendOverlayMessage(Component.literal("The way takes you down...").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
	}

	private static void land(ServerLevel level, ServerPlayer player, SecretWays.Site site) {
		int x = site.landX();
		int z = site.landZ();
		level.getChunk(x >> 4, z >> 4);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		player.removeEffect(MobEffects.LEVITATION);
		// out of any boat first (the whirlpool drags boats under too)
		player.stopRiding();
		// face on down, toward the pit
		float yaw = (float) Math.toDegrees(Math.atan2(-z, -x)) - 90f;
		player.teleportTo(level, x + 0.5, y + 1.5, z + 0.5, java.util.Set.of(), yaw, player.getXRot(), true);
		player.resetFallDistance();
		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, true, false, false));
		SAFE.put(player.getUUID(), level.getGameTime() + SAFE_AFTER);
		level.sendParticles(ParticleTypes.SOUL, x + 0.5, y + 1.5, z + 0.5, 30, 0.5, 0.8, 0.5, 0.04);
		level.playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.4f, 1.3f);
		HellState state = HellState.get(level.getServer());
		HellState.Soul soul = Hearts.soul(player);
		if (soul.secrets.add(site.id())) {
			state.setDirty();
			int found = (int) soul.secrets.stream().filter(id -> SecretWays.byId(id) != null).count();
			Dialogue.notice(player, Component.literal("A secret way: it brought you down to " + site.place() + ". Secret ways are marked on your "
					+ "Map of Hell (M) once found (" + found + " of " + SecretWays.count() + ").").withStyle(ChatFormatting.AQUA));
			Journey.award(player, "journey/secret_way");
			if (found >= SecretWays.count()) {
				Journey.award(player, "journey/secret_ways");
			}
			send(player);
			HellcraftMod.LOGGER.info("Secret way found: {} by {}", site.id(), player.getGameProfile().name());
		} else {
			player.sendOverlayMessage(Component.literal("You came down a secret way into " + site.place() + ".")
					.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
	}

	/** The secrets this player has found, to their Map of Hell and Journal. */
	public static void send(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, SecretsPayload.TYPE)) {
			return;
		}
		HellState.Soul soul = HellState.get(player.level().getServer()).existing(player.getUUID());
		ServerPlayNetworking.send(player, new SecretsPayload(soul == null ? List.of() : List.copyOf(soul.secrets)));
	}

	public static void forget(ServerPlayer player) {
		CARRIED.remove(player.getUUID());
		SAFE.remove(player.getUUID());
		HINTED.remove(player.getUUID());
	}

	// ------------------------------------------------------------------ commands and the self-test

	private static void commands(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("hellcraft")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("secrets")
						.then(Commands.literal("test").executes(ctx -> reply(ctx.getSource(), selfTest(ctx.getSource().getServer()))))
						.then(Commands.literal("list").executes(ctx -> {
							StringBuilder sb = new StringBuilder();
							for (SecretWays.Site s : SecretWays.all()) {
								sb.append(String.format(Locale.ROOT, "%s (%s) at %d %d %d -> %d %d; ", s.id(), s.kind().title, s.x(), s.y(), s.z(),
										s.landX(), s.landZ()));
							}
							return reply(ctx.getSource(), sb.toString());
						}))
						.then(Commands.literal("goto")
								.then(Commands.argument("id", StringArgumentType.word())
										.suggests((ctx, b) -> SharedSuggestionProvider.suggest(SecretWays.all().stream().map(SecretWays.Site::id), b))
										.executes(ctx -> visit(ctx.getSource(), StringArgumentType.getString(ctx, "id"), false))
										.then(Commands.literal("landing")
												.executes(ctx -> visit(ctx.getSource(), StringArgumentType.getString(ctx, "id"), true)))))
						.then(Commands.literal("find")
								.then(Commands.argument("player", EntityArgument.player())
										.then(Commands.argument("id", StringArgumentType.word())
												.suggests((ctx, b) -> SharedSuggestionProvider.suggest(
														java.util.stream.Stream.concat(java.util.stream.Stream.of("all"), SecretWays.all().stream().map(SecretWays.Site::id)), b))
												.executes(ctx -> {
													ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
													String id = StringArgumentType.getString(ctx, "id");
													HellState.Soul soul = Hearts.soul(p);
													for (SecretWays.Site s : SecretWays.all()) {
														if (id.equals("all") || id.equals(s.id())) {
															soul.secrets.add(s.id());
														}
													}
													HellState.get(p.level().getServer()).setDirty();
													send(p);
													return reply(ctx.getSource(), p.getGameProfile().name() + " has found " + soul.secrets.size() + " secret ways.");
												}))))
						.then(Commands.literal("forget")
								.then(Commands.argument("player", EntityArgument.player())
										.executes(ctx -> {
											ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
											Hearts.soul(p).secrets.clear();
											HellState.get(p.level().getServer()).setDirty();
											send(p);
											return reply(ctx.getSource(), p.getGameProfile().name() + " has forgotten the secret ways.");
										})))));
	}

	private static int reply(CommandSourceStack source, String text) {
		source.sendSuccess(() -> Component.literal(text), false);
		return 1;
	}

	/** /hellcraft secrets goto: to the edge of a secret (or onto its landing), facing it. */
	private static int visit(CommandSourceStack source, String id, boolean landing) {
		SecretWays.Site s = SecretWays.byId(id);
		if (s == null) {
			source.sendFailure(Component.literal("No secret way " + id + ". Try /hellcraft secrets list."));
			return 0;
		}
		ServerPlayer p;
		try {
			p = source.getPlayerOrException();
		} catch (Exception e) {
			source.sendFailure(Component.literal("Only a player can go there."));
			return 0;
		}
		ServerLevel level = source.getServer().overworld();
		int x = landing ? s.landX() + 2 : s.worldX(s.kind().radius + 3, 0);
		int z = landing ? s.landZ() + 2 : s.worldZ(s.kind().radius + 3, 0);
		level.getChunk(x >> 4, z >> 4);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		double tx = landing ? s.landX() + 0.5 : s.x() + 0.5;
		double tz = landing ? s.landZ() + 0.5 : s.z() + 0.5;
		float yaw = (float) Math.toDegrees(Math.atan2(tz - z - 0.5, tx - x - 0.5)) - 90f;
		p.teleportTo(level, x + 0.5, y + 0.1, z + 0.5, java.util.Set.of(), yaw, 20f, true);
		return reply(source, "At " + (landing ? "the landing of " : "") + s.id() + " (" + s.kind().title + ").");
	}

	/**
	 * /hellcraft secrets test: every site is off the roads, the lairs and the Rests, and lands on the next circle's floor
	 * (SecretWays.problems); and the first site is built in the world (its shaft is open, its bottom in place).
	 */
	public static String selfTest(MinecraftServer server) {
		String problems = SecretWays.problems();
		StringBuilder kinds = new StringBuilder();
		for (SecretWays.Kind k : SecretWays.Kind.values()) {
			long n = SecretWays.all().stream().filter(s -> s.kind() == k).count();
			kinds.append(' ').append(k.name().toLowerCase(Locale.ROOT)).append('=').append(n);
		}
		String built = "skipped";
		ServerLevel level = server.overworld();
		if (HellWorldgen.isInferno(level)) {
			SecretWays.Site s = SecretWays.all().getFirst();
			int mx = s.worldX(s.kind().mouthF(), 0);
			int mz = s.worldZ(s.kind().mouthF(), 0);
			level.getChunk(mx >> 4, mz >> 4);
			boolean bottom = level.getBlockState(new BlockPos(mx, s.y() - SecretWayFeature.SHAFT, mz)).is(SecretWayFeature.bottom(s.kind()));
			boolean open = level.getBlockState(new BlockPos(mx, s.y() - 1, mz)).getCollisionShape(level, new BlockPos(mx, s.y() - 1, mz)).isEmpty();
			built = s.id() + (bottom && open ? " built" : " NOT built (bottom " + bottom + ", open " + open + ")");
			if (!(bottom && open)) {
				problems = (problems == null ? "" : problems + " ") + s.id() + " is not built;";
			}
		}
		String head = problems == null ? "Secrets test OK" : "Secrets test FAILED";
		return head + ": " + SecretWays.count() + " secret ways," + kinds + "; " + built + (problems == null ? "" : "; " + problems);
	}
}
