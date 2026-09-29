package net.thesmallthings.hellcraft.hazard.guardian;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.BloodAltar;
import net.thesmallthings.hellcraft.blood.BloodItems;
import net.thesmallthings.hellcraft.blood.Hearts;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.hazard.BossModel;
import net.thesmallthings.hellcraft.util.Feedback;
import net.thesmallthings.hellcraft.util.Journey;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One fight against a circle guardian, from the moment someone walks into its lair to its death (or
 * until everyone leaves). Subclasses make the body and its attacks; this class does the rest: the
 * boss bar, the model, the leash to the lair, telegraph helpers, and the spoils.
 */
public abstract class GuardianFight {
	static final String TAG = "hellcraft_guardian";
	/** Players inside this radius of the lair are in the fight. */
	static final double ARENA = 24.0;
	private static final double LEASH = 26.0;
	private static final double AUDIENCE = 48.0;
	private static final int GIVE_UP_TICKS = 600;

	protected final ServerLevel level;
	protected final Guardian kind;
	/** The lair's centre, on the ground. */
	protected final BlockPos lair;
	private final boolean debug;
	private final ServerBossEvent bar;
	protected final BossModel model;
	private final List<Scheduled> scheduled = new ArrayList<>();
	private final Set<UUID> participants = new LinkedHashSet<>();
	@Nullable
	protected UUID bodyId;
	protected int tick;
	private int cooldown = 60;
	private int emptyTicks;
	private int missingTicks;
	private boolean done;

	private record Scheduled(int at, Runnable action) {
	}

	protected GuardianFight(GuardianContext ctx) {
		this.level = ctx.level();
		this.kind = ctx.kind();
		this.lair = ctx.lair();
		this.debug = ctx.debug();
		this.bar = new ServerBossEvent(UUID.randomUUID(), Component.literal(kind.title.toUpperCase(java.util.Locale.ROOT) + " — " + kind.subtitle)
				.withStyle(kind.color, ChatFormatting.BOLD), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
		this.model = new BossModel(level, TAG + "_model", TAG, Vec3.atBottomCenterOf(lair));
	}

	// ------------------------------------------------------------------ what each guardian provides

	/** Creates (not yet added) the guardian's body, standing at the lair. */
	protected abstract Mob createBody();

	/** Names of its attacks, for the test command. */
	public abstract List<String> attacks();

	/** Runs one attack at a target; returns the cooldown before the next, or -1 if it can't. */
	protected abstract int perform(String attack, Mob body, LivingEntity target);

	/** Picks and runs the next attack; returns the cooldown. */
	protected abstract int nextAttack(Mob body, List<LivingEntity> targets);

	protected abstract String introLine();

	protected abstract String deathLine();

	/** Every tick while alive (after the leash), for guardians with ongoing effects. */
	protected void everyTick(Mob body) {
	}

	/** Called whenever the body is hurt. */
	protected void onHurt(float amount) {
	}

	// ------------------------------------------------------------------ lifecycle

	void start() {
		forceLoad(true);
		int players = Math.max(1, (int) level.players().stream().filter(p -> inArena(p) && !p.isSpectator()).count());
		Mob body = createBody();
		body.snapTo(lair.getX() + 0.5, lair.getY(), lair.getZ() + 0.5, 90.0f, 0.0f);
		body.addTag(TAG);
		body.addTag(TAG + "_body");
		body.setCustomName(Component.literal(kind.title).withStyle(kind.color, ChatFormatting.BOLD));
		body.setCustomNameVisible(true);
		body.setPersistenceRequired();
		setBase(body, Attributes.MAX_HEALTH, kind.health * HellConfig.get().guardianHealthMultiplier * (1.0 + 0.5 * (players - 1)));
		setBase(body, Attributes.FOLLOW_RANGE, 48.0);
		setBase(body, Attributes.KNOCKBACK_RESISTANCE, 0.8);
		body.setHealth(body.getMaxHealth());
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			body.setDropChance(slot, 0.0f);
		}
		level.addFreshEntity(body);
		bodyId = body.getUUID();
		model.attach(body, kind.model(), Math.max(kind.modelHeight, body.getBbHeight()));
		level.playSound(null, lair, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2.0f, 1.2f);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, body.getX(), body.getY() + 1, body.getZ(), 120, 1.5, 2, 1.5, 0.05);
		for (ServerPlayer p : audience()) {
			p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 15));
			p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(kind.title).withStyle(kind.color, ChatFormatting.BOLD)));
			p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(kind.subtitle).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
		}
		say(introLine());
		HellcraftMod.LOGGER.info("Guardian awakens: {}", kind.id());
	}

	void tick() {
		tick++;
		runScheduled();
		if (done) {
			return;
		}
		if (tick % 100 == 0) {
			forceLoad(true);
		}
		Mob body = body();
		if (body == null) {
			if (++missingTicks > 200) {
				HellcraftMod.LOGGER.warn("Guardian {} vanished without dying", kind.id());
				end(false);
			}
			return;
		}
		missingTicks = 0;
		List<ServerPlayer> watchers = audience();
		model.tick(watchers);
		updateBar(body, watchers);
		leash(body);
		everyTick(body);
		boolean anyone = false;
		for (ServerPlayer p : level.players()) {
			if (inArena(p) && p.isAlive() && !p.isSpectator() && !p.isCreative()) {
				participants.add(p.getUUID());
				anyone = true;
			}
		}
		emptyTicks = anyone || debug ? 0 : emptyTicks + 1;
		if (emptyTicks > GIVE_UP_TICKS) {
			say(kind.title + " sinks back into the dark, waiting.");
			end(false);
			return;
		}
		List<LivingEntity> targets = targets();
		if (--cooldown <= 0 && !targets.isEmpty()) {
			cooldown = Math.max(20, nextAttack(body, targets));
		}
	}

	boolean forceAttack(String attack) {
		Mob body = body();
		List<LivingEntity> targets = targets();
		if (body == null || targets.isEmpty() || !attacks().contains(attack)) {
			return false;
		}
		int next = perform(attack, body, targets.get(0));
		if (next > 0) {
			cooldown = next;
		}
		return next >= 0;
	}

	/** Kills the guardian outright (test helper). */
	void slay() {
		Mob body = body();
		if (body != null) {
			body.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
		}
	}

	void afterDamage(LivingEntity entity, float dealt) {
		if (entity.getUUID().equals(bodyId)) {
			onHurt(dealt);
		}
	}

	void onDeath(LivingEntity entity) {
		if (done || !entity.getUUID().equals(bodyId)) {
			return;
		}
		say(deathLine());
		level.sendParticles(BloodAltar.BLOOD, entity.getX(), entity.getY() + 1, entity.getZ(), 80, 1.0, 1.0, 1.0, 0.0);
		// the spoils are personal: each soul takes them once per guardian until they return (so a guardian
		// that wakes every half hour can't be farmed), and the first victory over each is the richest
		boolean treasure = false;
		List<String> names = new ArrayList<>();
		for (UUID id : participants) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			if (p != null) {
				names.add(p.getGameProfile().name());
				HellState.Soul soul = Hearts.soul(p);
				soul.guardiansSlain++;
				treasure |= spoils(p, soul, entity);
				HellState.get(level.getServer()).setDirty();
				Journey.award(p, "journey/guardian_" + kind.id());
			}
		}
		if (treasure) {
			// its treasure: enchanted books and more (data/hellcraft/loot_table/gameplay/guardian_spoils.json)
			level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput(),
					String.format(java.util.Locale.ROOT, "loot spawn %.2f %.2f %.2f loot hellcraft:gameplay/guardian_spoils", entity.getX(), entity.getY() + 0.5, entity.getZ()));
		}
		String who = names.isEmpty() ? "Someone" : String.join(", ", names);
		level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(who + " slew " + kind.title + ", " + kind.subtitle + ".")
				.withStyle(kind.color, ChatFormatting.BOLD), false);
		HellcraftMod.LOGGER.info("Guardian slain: {}", kind.id());
		end(true);
	}

	/**
	 * One victor's share, straight into their inventory. The first time: the full spoils and a Soul Anchor.
	 * Again, once the cooldown has passed: fewer hearts. In between: only a few fragments. Returns whether
	 * this victor earned the treasure roll.
	 */
	private boolean spoils(ServerPlayer player, HellState.Soul soul, LivingEntity body) {
		HellConfig config = HellConfig.get();
		long now = level.getServer().overworld().getGameTime();
		Long last = soul.guardianSpoils.get(kind.id());
		long cooldown = config.guardianSpoilsCooldownMinutes * 60L * 20L;
		if (last != null && now - last < cooldown) {
			BloodItems.give(player, BloodItems.fragment(1 + level.getRandom().nextInt(2)));
			long minutes = Math.max(1, (cooldown - (now - last)) / (60L * 20L));
			player.sendSystemMessage(Component.literal(kind.title + " has nothing more for you. Its spoils return for you in "
					+ minutes + " min.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			return false;
		}
		boolean first = last == null;
		soul.guardianSpoils.put(kind.id(), now);
		BloodItems.give(player, BloodItems.heart(first ? config.guardianHearts : config.guardianRepeatHearts));
		BloodItems.give(player, BloodItems.fragment(3 + level.getRandom().nextInt(4)));
		if (first) {
			BloodItems.give(player, BloodItems.anchor(1));
		}
		player.sendSystemMessage(Component.literal(first ? "The spoils of " + kind.title + " are yours."
				: "You take " + kind.title + "'s spoils again.").withStyle(kind.color));
		return true;
	}

	/** Ends the fight; a victory puts the guardian to sleep for a while. */
	void end(boolean won) {
		if (done) {
			return;
		}
		done = true;
		scheduled.clear();
		bar.removeAllPlayers();
		model.clear();
		Mob body = body();
		if (body != null && body.isAlive()) {
			body.discard();
		}
		for (Entity e : level.getEntitiesOfClass(Entity.class, new AABB(lair).inflate(64), e -> e.entityTags().contains(TAG) && e.isAlive())) {
			if (!(e instanceof LivingEntity le) || !le.isDeadOrDying()) {
				e.discard();
			}
		}
		if (won) {
			HellState state = HellState.get(level.getServer());
			state.guardianNext.put(kind.id(), level.getGameTime() + HellConfig.get().guardianRespawnMinutes * 1200L);
			state.setDirty();
		}
		forceLoad(false);
	}

	boolean done() {
		return done;
	}

	boolean owns(UUID id) {
		return id.equals(bodyId) || model.owns(id);
	}

	String status() {
		Mob body = body();
		return kind.id() + ": " + (body == null ? "no body" : String.format(java.util.Locale.ROOT, "%.0f/%.0f hp", body.getHealth(), body.getMaxHealth()))
				+ " tick=" + tick + " participants=" + participants.size() + " lair=" + lair.toShortString();
	}

	// ------------------------------------------------------------------ helpers for the guardians

	@Nullable
	protected Mob body() {
		Entity e = bodyId == null ? null : level.getEntity(bodyId);
		return e instanceof Mob m && m.isAlive() ? m : null;
	}

	protected boolean inArena(Entity e) {
		double dx = e.getX() - (lair.getX() + 0.5);
		double dz = e.getZ() - (lair.getZ() + 0.5);
		return dx * dx + dz * dz < ARENA * ARENA && Math.abs(e.getY() - lair.getY()) < 24;
	}

	protected List<ServerPlayer> audience() {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(lair.getX() + 0.5, lair.getY(), lair.getZ() + 0.5) < AUDIENCE * AUDIENCE) {
				out.add(p);
			}
		}
		return out;
	}

	/** Players in the lair worth attacking; if none (tests), any other living thing there. */
	protected List<LivingEntity> targets() {
		List<LivingEntity> players = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (p.isAlive() && !p.isSpectator() && !p.isCreative() && inArena(p)) {
				players.add(p);
			}
		}
		if (!players.isEmpty()) {
			return players;
		}
		return level.getEntitiesOfClass(LivingEntity.class, new AABB(lair).inflate(ARENA),
				e -> e.isAlive() && !e.entityTags().contains(TAG) && !(e instanceof Player) && inArena(e));
	}

	/** Everything near a point that the guardian can hurt (not itself, not its own). */
	protected List<LivingEntity> victims(Vec3 center, double radius) {
		return level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius), e -> e.isAlive()
				&& !e.entityTags().contains(TAG)
				&& !e.isSpectator()
				&& !(e instanceof Player p && p.isCreative())
				&& e.position().distanceToSqr(center) <= radius * radius);
	}

	protected void schedule(int delay, Runnable action) {
		scheduled.add(new Scheduled(tick + Math.max(0, delay), action));
	}

	private void runScheduled() {
		List<Scheduled> due = new ArrayList<>();
		for (Iterator<Scheduled> it = scheduled.iterator(); it.hasNext(); ) {
			Scheduled s = it.next();
			if (s.at() <= tick) {
				due.add(s);
				it.remove();
			}
		}
		for (Scheduled s : due) {
			try {
				s.action().run();
			} catch (RuntimeException e) {
				HellcraftMod.LOGGER.warn("Guardian action failed", e);
			}
		}
	}

	/** The guardian speaks (in chat, to everyone near the lair). */
	protected void say(String line) {
		boolean shout = line.equals(line.toUpperCase(java.util.Locale.ROOT)) || line.endsWith("!");
		MutableComponent text = Component.literal(kind.title.toUpperCase(java.util.Locale.ROOT) + ": ").withStyle(kind.color, ChatFormatting.BOLD)
				.append(Component.literal(line).withStyle(shout ? new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD}
						: new ChatFormatting[]{ChatFormatting.GRAY, ChatFormatting.ITALIC}));
		for (ServerPlayer p : audience()) {
			p.sendSystemMessage(text);
		}
	}

	/** A hint in the action bar of everyone in the lair. */
	protected void tip(String text) {
		for (ServerPlayer p : audience()) {
			p.sendOverlayMessage(Component.literal(text).withStyle(ChatFormatting.AQUA));
		}
	}

	protected void sound(Holder<net.minecraft.sounds.SoundEvent> sound, float volume, float pitch) {
		for (ServerPlayer p : audience()) {
			Feedback.sound(p, sound, SoundSource.HOSTILE, volume, pitch);
		}
	}

	protected double groundY(double x, double z) {
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
	}

	/** A ring of particles on the ground (a telegraph). */
	protected void ringParticles(ParticleOptions particle, Vec3 center, double radius, int points) {
		for (int i = 0; i < points; i++) {
			double a = i * 2 * Math.PI / points;
			double x = center.x + Math.cos(a) * radius;
			double z = center.z + Math.sin(a) * radius;
			level.sendParticles(particle, x, groundY(x, z) + 0.15, z, 1, 0, 0, 0, 0);
		}
	}

	/**
	 * A shockwave that rolls out along the ground from {@code center}, one block a tick, up to
	 * {@code maxRadius}. Anyone standing on the ground as it passes is hit; jumping clears it.
	 */
	protected void shockwave(Mob body, Vec3 center, int maxRadius, float damage, ParticleOptions particle) {
		Set<UUID> hit = new java.util.HashSet<>();
		for (int r = 2; r <= maxRadius; r++) {
			int radius = r;
			schedule(r - 2, () -> {
				ringParticles(particle, center, radius, Math.max(10, (int) (radius * 5)));
				for (LivingEntity e : victims(center, radius + 6)) {
					double d = Math.sqrt((e.getX() - center.x) * (e.getX() - center.x) + (e.getZ() - center.z) * (e.getZ() - center.z));
					if (Math.abs(d - radius) < 0.9 && e.onGround() && hit.add(e.getUUID())) {
						e.hurt(level.damageSources().mobAttack(body), damage);
						e.push(0, 0.5, 0);
						Feedback.syncMotion(e);
					}
				}
			});
		}
	}

	/** Knocks an entity away from a point (players need their motion sent). */
	protected static void fling(LivingEntity e, Vec3 from, double strength, double up) {
		Vec3 away = e.position().subtract(from).multiply(1, 0, 1);
		away = away.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : away.normalize();
		e.push(away.x * strength, up, away.z * strength);
		Feedback.syncMotion(e);
	}

	protected static void setBase(LivingEntity entity, Holder<Attribute> attribute, double value) {
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	private void updateBar(Mob body, List<ServerPlayer> watchers) {
		for (ServerPlayer p : new ArrayList<>(bar.getPlayers())) {
			if (!watchers.contains(p)) {
				bar.removePlayer(p);
			}
		}
		for (ServerPlayer p : watchers) {
			bar.addPlayer(p);
		}
		bar.setProgress(Math.max(0.0f, body.getHealth() / body.getMaxHealth()));
	}

	/** A guardian never strays far from its lair. */
	private void leash(Mob body) {
		double dx = body.getX() - (lair.getX() + 0.5);
		double dz = body.getZ() - (lair.getZ() + 0.5);
		if (dx * dx + dz * dz > LEASH * LEASH || body.getY() < lair.getY() - 16 || body.getY() > lair.getY() + 40) {
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, body.getX(), body.getY() + 1, body.getZ(), 40, 0.5, 1, 0.5, 0.1);
			body.teleportTo(lair.getX() + 0.5, lair.getY(), lair.getZ() + 0.5);
		}
	}

	private void forceLoad(boolean forced) {
		int cx = lair.getX() >> 4;
		int cz = lair.getZ() >> 4;
		for (int x = cx - 2; x <= cx + 2; x++) {
			for (int z = cz - 2; z <= cz + 2; z++) {
				level.setChunkForced(x, z, forced);
			}
		}
	}
}
