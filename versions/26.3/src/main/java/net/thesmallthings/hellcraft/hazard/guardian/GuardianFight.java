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
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
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
import net.thesmallthings.hellcraft.blood.BossSpoils;
import net.thesmallthings.hellcraft.blood.Hearts;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.blood.Judgement;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.hazard.BossModel;
import net.thesmallthings.hellcraft.hazard.BossRules;
import net.thesmallthings.hellcraft.hazard.StyleMeter;
import net.thesmallthings.hellcraft.util.Feedback;
import net.thesmallthings.hellcraft.util.Journey;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
	/** Arrows from further away than this make the guardian pull the archer in. */
	private static final double ARCHER_RANGE = 8.0;
	/** Standing this many blocks above the guardian counts as a perch. */
	private static final double PERCH_HEIGHT = 6.0;

	protected final ServerLevel level;
	protected final Guardian kind;
	/** The lair's centre, on the ground. */
	protected final BlockPos lair;
	private final boolean debug;
	private final ServerBossEvent bar;
	protected final BossModel model;
	private final List<Scheduled> scheduled = new ArrayList<>();
	private final Set<UUID> participants = new LinkedHashSet<>();
	/** Each fighter's style: its average over the fight decides their spoils (Judgement). */
	private final Map<UUID, StyleMeter> styles = new HashMap<>();
	@Nullable
	protected UUID bodyId;
	protected int tick;
	private int cooldown = 60;
	private int emptyTicks;
	private int missingTicks;
	private boolean done;
	private int nextPull;
	/** player -> ticks spent perched high above the guardian */
	private final Map<UUID, Integer> perched = new HashMap<>();
	/** player -> tick before which they aren't told again that they're out of reach */
	private final Map<UUID, Integer> warned = new HashMap<>();

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

	/** A guardian that flies (Geryon) is fair game for arrows: full projectile damage, no pull, no perch rule. */
	protected boolean flies() {
		return false;
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
		if (!flies()) {
			body.addTag(BossRules.BODY_TAG);
		}
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
		if (tick % 10 == 0) {
			watchPerches(body);
		}
		everyTick(body);
		boolean anyone = false;
		for (ServerPlayer p : level.players()) {
			if (inArena(p) && p.isAlive() && !p.isSpectator() && !p.isCreative()) {
				if (participants.add(p.getUUID())) {
					styles.put(p.getUUID(), new StyleMeter(p));
					p.sendSystemMessage(Component.literal("Your style is ranked in this fight (the STYLE bar). Hit hard, swap weapons and don't get hit: "
							+ "the better your style, the better your spoils.").withStyle(ChatFormatting.AQUA));
				}
				anyone = true;
			}
		}
		for (Map.Entry<UUID, StyleMeter> e : styles.entrySet()) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(e.getKey());
			if (p != null) {
				e.getValue().tick(p, body);
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

	/** Only souls inside the lair can hurt a guardian: no shooting it from beyond the braziers. */
	boolean allowDamage(LivingEntity entity, DamageSource source) {
		if (!entity.getUUID().equals(bodyId) || !(source.getEntity() instanceof ServerPlayer player) || inArena(player)) {
			return true;
		}
		if (tick >= warned.getOrDefault(player.getUUID(), 0)) {
			warned.put(player.getUUID(), tick + 60);
			player.sendOverlayMessage(Component.literal(kind.title + " can only be hurt from inside the lair.").withStyle(ChatFormatting.AQUA));
		}
		return false;
	}

	void afterDamage(LivingEntity entity, DamageSource source, float dealt, boolean blocked) {
		// a fighter hurt in the lair by anything but another player loses style; a shield block earns it
		StyleMeter hurt = styles.get(entity.getUUID());
		if (hurt != null && inArena(entity) && !(source.getEntity() instanceof Player)) {
			if (blocked && source.getEntity() != null && BossRules.fromBoss(source.getEntity())) {
				hurt.onParry();
			} else if (dealt > 0) {
				hurt.onHurt(dealt / Math.max(1.0f, entity.getMaxHealth()));
			}
			return;
		}
		if (!entity.getUUID().equals(bodyId)) {
			return;
		}
		onHurt(dealt);
		if (source.getEntity() instanceof ServerPlayer attacker && styles.containsKey(attacker.getUUID())) {
			styles.get(attacker.getUUID()).onHit(attacker, source, dealt);
		}
		if (!flies() && HellConfig.get().bossPullsArchers && source.is(DamageTypeTags.IS_PROJECTILE) && source.getEntity() instanceof ServerPlayer archer
				&& archer.distanceTo(entity) > ARCHER_RANGE && tick >= nextPull) {
			nextPull = tick + 160;
			pullIn(archer);
		}
	}

	/** An archer keeping their distance is dragged in, a second after a warning. */
	private void pullIn(ServerPlayer archer) {
		archer.sendOverlayMessage(Component.literal(kind.title + " is pulling you in! Arrows from afar won't save you.")
				.withStyle(kind.color, ChatFormatting.BOLD));
		Feedback.sound(archer, SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.0f, 0.6f);
		for (int t = 0; t < 20; t += 4) {
			schedule(t, () -> level.sendParticles(ParticleTypes.REVERSE_PORTAL, archer.getX(), archer.getY() + 1, archer.getZ(), 20, 0.4, 0.8, 0.4, 0.05));
		}
		schedule(20, () -> {
			Mob body = body();
			if (body == null || !archer.isAlive() || !inArena(archer)) {
				return;
			}
			Vec3 to = body.position().subtract(archer.position());
			double flat = Math.sqrt(to.x * to.x + to.z * to.z);
			if (flat < 4.0) {
				return;
			}
			double speed = Math.min(3.0, 0.4 + flat * 0.11);
			archer.setDeltaMovement(to.x / flat * speed, 0.45, to.z / flat * speed);
			Feedback.syncMotion(archer);
			Feedback.sound(archer, SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.HOSTILE, 1.5f, 0.5f);
		});
	}

	/** Anyone standing high above the guardian (a tower, a tree) for three seconds is dragged down beside it. */
	private void watchPerches(Mob body) {
		if (flies() || !HellConfig.get().bossPullsArchers) {
			return;
		}
		for (LivingEntity e : targets()) {
			if (!(e instanceof ServerPlayer p)) {
				continue;
			}
			if (p.getY() < body.getY() + PERCH_HEIGHT) {
				perched.remove(p.getUUID());
				continue;
			}
			int ticks = perched.merge(p.getUUID(), 10, Integer::sum);
			if (ticks == 10) {
				p.sendOverlayMessage(Component.literal("You're too high up: come down, or " + kind.title + " will drag you down.")
						.withStyle(kind.color, ChatFormatting.BOLD));
			} else if (ticks >= 60) {
				perched.remove(p.getUUID());
				Vec3 side = p.position().subtract(body.position()).multiply(1, 0, 1);
				side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
				double x = body.getX() + side.x * 4.0;
				double z = body.getZ() + side.z * 4.0;
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 40, 0.5, 1, 0.5, 0.1);
				p.teleportTo(x, groundY(x, z), z);
				p.resetFallDistance();
				Feedback.sound(p, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0f, 0.5f);
				p.sendOverlayMessage(Component.literal(kind.title + " dragged you down. Fight on the ground!").withStyle(kind.color, ChatFormatting.BOLD));
			}
		}
	}

	void onDeath(LivingEntity entity, DamageSource source) {
		if (done) {
			return;
		}
		StyleMeter fallen = styles.get(entity.getUUID());
		if (fallen != null) {
			fallen.onDeath();
			return;
		}
		if (!entity.getUUID().equals(bodyId)) {
			// one of the guardian's summons, killed by a fighter
			if (entity.entityTags().contains(TAG) && source.getEntity() != null && styles.containsKey(source.getEntity().getUUID())) {
				styles.get(source.getEntity().getUUID()).onCleanup();
			}
			return;
		}
		say(deathLine());
		level.sendParticles(BloodAltar.BLOOD, entity.getX(), entity.getY() + 1, entity.getZ(), 80, 1.0, 1.0, 1.0, 0.0);
		// the spoils are personal, and follow each fighter's style: see spoils()
		List<String> names = new ArrayList<>();
		List<ServerPlayer> fighters = new ArrayList<>();
		for (UUID id : participants) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			if (p != null) {
				fighters.add(p);
			}
		}
		for (ServerPlayer p : fighters) {
			names.add(p.getGameProfile().name());
			HellState.Soul soul = Hearts.soul(p);
			soul.guardiansSlain++;
			spoils(p, soul);
			HellState.get(level.getServer()).setDirty();
			Journey.award(p, "journey/guardian_" + kind.id());
		}
		String who = names.isEmpty() ? "Someone" : String.join(", ", names);
		level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(who + " slew " + kind.title + ", " + kind.subtitle + ".")
				.withStyle(kind.color, ChatFormatting.BOLD), false);
		HellcraftMod.LOGGER.info("Guardian slain: {}", kind.id());
		end(true);
	}

	/**
	 * One victor's share, straight into their inventory, by their style rank (StyleMeter, Judgement). Every
	 * victory pays fragments by rank, and S or better a treasure roll. Blood Hearts reward firsts and style,
	 * never repetition: the first victory (with a Soul Anchor), the first S and the first SSS against this
	 * guardian, and, once every few hours, a heart for a victory ranked S or better.
	 */
	private void spoils(ServerPlayer player, HellState.Soul soul) {
		HellConfig config = HellConfig.get();
		StyleMeter style = styles.computeIfAbsent(player.getUUID(), id -> new StyleMeter(player));
		Judgement.Grade grade = style.grade();
		BossSpoils.Claim claim = BossSpoils.peek(player, kind.id(), config.guardianSpoilsCooldownMinutes);
		List<String> got = new ArrayList<>();
		int fragments = grade.fragments;
		String note = "";
		if (claim == BossSpoils.Claim.FIRST) {
			BossSpoils.record(player, kind.id());
			fragments += 3;
			BloodItems.give(player, BloodItems.heart(config.guardianHearts));
			BloodItems.give(player, BloodItems.anchor(1));
			got.add(Judgement.hearts(config.guardianHearts, "your first victory"));
			got.add("a Soul Anchor");
		} else if (claim == BossSpoils.Claim.AGAIN && grade.atLeast(Judgement.Grade.S)) {
			BossSpoils.record(player, kind.id());
			BloodItems.give(player, BloodItems.heart(config.guardianRepeatHearts));
			got.add(Judgement.hearts(config.guardianRepeatHearts, "an S or better"));
		} else if (claim == BossSpoils.Claim.AGAIN) {
			note = kind.title + "'s Blood Heart is ready for you: win with an S or better to take it.";
		} else {
			note = kind.title + "'s Blood Heart returns for you in " + Math.max(1, BossSpoils.minutesLeft(player, kind.id(),
					config.guardianSpoilsCooldownMinutes)) + " min (for a victory ranked S or better).";
		}
		int marks = Judgement.marks(soul, kind.id(), grade);
		if (marks > 0) {
			BloodItems.give(player, BloodItems.heart(marks));
			got.add(Judgement.hearts(marks, Judgement.marksReason(grade, marks)));
		}
		BloodItems.give(player, BloodItems.fragment(fragments));
		got.add(0, fragments + " Blood Fragments");
		if (grade.atLeast(Judgement.Grade.S) || claim == BossSpoils.Claim.FIRST) {
			Judgement.treasure(player);
			got.add(1, "a treasure roll");
		}
		Judgement.report(player, kind.title, grade, style.summary(), got, style.advice(), note);
	}

	/** Ends the fight; a victory puts the guardian to sleep for a while. */
	void end(boolean won) {
		if (done) {
			return;
		}
		done = true;
		scheduled.clear();
		bar.removeAllPlayers();
		styles.values().forEach(StyleMeter::finish);
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
		return inArena(e.getX(), e.getY(), e.getZ());
	}

	boolean inArena(BlockPos pos) {
		return inArena(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
	}

	private boolean inArena(double x, double y, double z) {
		double dx = x - (lair.getX() + 0.5);
		double dz = z - (lair.getZ() + 0.5);
		return dx * dx + dz * dz < ARENA * ARENA && Math.abs(y - lair.getY()) < 24;
	}

	ServerLevel level() {
		return level;
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
