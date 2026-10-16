package net.thesmallthings.hellcraft.hazard.lucifer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.BloodItems;
import net.thesmallthings.hellcraft.blood.BossSpoils;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.blood.Judgement;
import net.thesmallthings.hellcraft.blood.Scoreboards;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.cutscene.Cutscene;
import net.thesmallthings.hellcraft.cutscene.CutsceneManager;
import net.thesmallthings.hellcraft.cutscene.Cutscenes;
import net.thesmallthings.hellcraft.entity.HellEntities;
import net.thesmallthings.hellcraft.entity.boss.BossEntity;
import net.thesmallthings.hellcraft.entity.boss.EmperorEntity;
import net.thesmallthings.hellcraft.entity.boss.FallenSeraphEntity;
import net.thesmallthings.hellcraft.entity.boss.gen.EmperorAnim;
import net.thesmallthings.hellcraft.entity.boss.gen.FallenSeraphAnim;
import net.thesmallthings.hellcraft.hazard.BossRules;
import net.thesmallthings.hellcraft.hazard.Danger;
import net.thesmallthings.hellcraft.hazard.StyleMeter;
import net.thesmallthings.hellcraft.music.BossMusic;
import net.thesmallthings.hellcraft.net.Dialogue;
import net.thesmallthings.hellcraft.net.MapSync;
import net.thesmallthings.hellcraft.util.Feedback;
import net.thesmallthings.hellcraft.util.Journey;
import net.thesmallthings.hellcraft.world.Purgatory;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One fight against Lucifer (Phase 5 of the revamp), from the ready check at the pit's edge to the climb out.
 *
 * <pre>
 * READY -(countdown, or everyone in)-> INTRO (the Fallen Seraph descends) -> DUEL -(half)-> ENRAGE_TRANSITION -> ENRAGED
 *   -(his seraph form breaks)-> TRUE_FORM_TRANSITION (the Emperor wakes) -> TRUE_FORM -(he freezes)-> DEFEAT (the climb out) -> DONE
 *                       \____________________ nobody left alive in the pit for 30 s ____________________/-> FAILED
 * </pre>
 *
 * The Emperor sleeps in the pit the whole time (the client draws him asleep when there is no fight); the fight brings
 * the real body (EmperorEntity) and wakes it. Like the guardians' fights, this one runs on its own clock at the
 * bosses' tempo (config bossTempo) while anyone is fighting, so every wind-up, warning and pause lasts 1/tempo as long
 * as it was made and the models' blows slow to match; the cutscenes and transitions keep their own time.
 */
public final class LuciferFight {
	enum Phase {READY, INTRO, DUEL, ENRAGE_TRANSITION, ENRAGED, TRUE_FORM_TRANSITION, TRUE_FORM, DEFEAT, FAILED, DONE}

	private static final double AUDIENCE_RADIUS = 96.0;
	/** The ready check: how long until the pit seals (real ticks), and how long once everyone near is in. */
	private static final int READY_TICKS = 600;
	private static final int READY_ALL_IN = 100;
	private static final int FAIL_AFTER_EMPTY_TICKS = 600;
	private static final Identifier VETERAN_MODIFIER = HellcraftMod.id("veteran");
	/** Where the Seraph comes down: this far out from the centre, between the Emperor and the fighters. */
	private static final double DESCENT = 22.0;
	/** The Emperor faces east, the way the first road comes in. */
	private static final float EMPEROR_FACING = -90.0f;

	private final ServerLevel level;
	/** Started by a command with nobody around: no ready check, and never fails for lack of players (used by tests). */
	private final boolean debug;
	private final int floorY;
	private final ServerBossEvent bar = new ServerBossEvent(UUID.randomUUID(), Component.literal("LUCIFER").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
			BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
	private final Map<UUID, Float> participants = new LinkedHashMap<>();
	/** Each fighter's style: its average over the fight decides their spoils (Judgement). */
	private final Map<UUID, StyleMeter> styles = new HashMap<>();
	private final List<Scheduled> scheduled = new ArrayList<>();
	private final List<UUID> traitors = new ArrayList<>();
	/** Everything else the fight made (the Emperor's weak points), so it isn't taken for a stray. */
	private final Set<UUID> owned = new HashSet<>();
	private final LuciferMusic music;
	/** Returning champions in this round; each one makes Lucifer harder for everyone. */
	private final List<String> veterans = new ArrayList<>();
	private final SeraphAttacks seraphAttacks;
	private final EmperorAttacks emperorAttacks;
	private boolean traitorsRaised;

	private Phase phase = Phase.READY;
	/** The fight's own clock (fight ticks), and how far the next tick has come. */
	private int tick;
	private double clock;
	private int phaseTick;
	private int realTicks;
	private int readyLeft = READY_TICKS;
	private boolean warnedNoReturn;
	private int emptyTicks;
	private int lastHurtBark = -1000;
	private int missingTicks;
	/** Until this fight tick, a shield block of the boss's blow staggers it (the white flash). */
	private int parryUntil = -1;
	@Nullable
	private UUID seraphId;
	@Nullable
	private UUID emperorId;
	/** The pit as it was when the fight began; whatever the fight blows out of it freezes back. */
	@Nullable
	private Map<Long, BlockState> floor;

	private record Scheduled(int at, Runnable action) {
	}

	LuciferFight(ServerLevel level, boolean debug) {
		this.level = level;
		this.debug = debug;
		this.floorY = LuciferArena.floorY(level);
		this.music = new LuciferMusic(level);
		this.seraphAttacks = new SeraphAttacks(this);
		this.emperorAttacks = new EmperorAttacks(this);
		bar.setDarkenScreen(true);
		bar.setCreateWorldFog(true);
		bar.setProgress(1.0f);
	}

	// ------------------------------------------------------------------ for the attacks

	ServerLevel level() {
		return level;
	}

	int floorY() {
		return floorY;
	}

	/** The fight's clock (fight ticks). */
	int tick() {
		return tick;
	}

	boolean enraged() {
		return phase == Phase.ENRAGED;
	}

	boolean done() {
		return phase == Phase.DONE;
	}

	String phaseName() {
		return phase.name();
	}

	/** Difficulty tier: number of returning champions fighting (capped). */
	int tier() {
		return Math.min(veterans.size(), HellConfig.get().luciferMaxVeteranTiers);
	}

	float damageMultiplier() {
		return (float) (1.0 + HellConfig.get().luciferVeteranDamageBonus * tier());
	}

	int extraMarks() {
		return tier();
	}

	/** Runs an action this many fight ticks from now. */
	void schedule(int delay, Runnable action) {
		scheduled.add(new Scheduled(tick + Math.max(0, delay), action));
	}

	/** How fast the fight runs: at the bosses' tempo while anyone fights, as made in between. */
	private double tempo() {
		return fighting(phase) ? Math.max(0.2, Math.min(1.0, HellConfig.get().bossTempo)) : 1.0;
	}

	/** How many real ticks this many fight ticks take now. */
	int realTicks(int fightTicks) {
		return (int) Math.round(fightTicks / tempo());
	}

	private static boolean fighting(Phase p) {
		return p == Phase.DUEL || p == Phase.ENRAGED || p == Phase.TRUE_FORM;
	}

	/** For the next few fight ticks, blocking the boss's blow with a shield counts as a parry. */
	void openParry(int ticks) {
		parryUntil = tick + ticks;
	}

	void say(String line) {
		LuciferDialogue.say(audience(), line);
	}

	private void sayLater(int delay, String line) {
		schedule(delay, () -> say(line));
	}

	/** A plain tip for everyone in the pit. */
	void tip(String text) {
		for (ServerPlayer p : audience()) {
			Dialogue.notice(p, Component.literal(text).withStyle(ChatFormatting.AQUA));
		}
	}

	List<ServerPlayer> audience() {
		return LuciferDialogue.audience(level, AUDIENCE_RADIUS);
	}

	/** Everyone fighting in the pit. */
	List<ServerPlayer> fighters() {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (p.isAlive() && !p.isSpectator() && LuciferArena.inside(p.getX(), p.getZ()) && p.getY() < floorY + 30) {
				out.add(p);
			}
		}
		return out;
	}

	void own(UUID id) {
		owned.add(id);
	}

	@Nullable
	FallenSeraphEntity seraph() {
		Entity e = seraphId == null ? null : level.getEntity(seraphId);
		return e instanceof FallenSeraphEntity s && s.isAlive() ? s : null;
	}

	@Nullable
	EmperorEntity emperor() {
		Entity e = emperorId == null ? null : level.getEntity(emperorId);
		return e instanceof EmperorEntity m && m.isAlive() ? m : null;
	}

	/** A point on the pit's floor. */
	Vec3 onFloor(double x, double z) {
		double r = Math.sqrt(x * x + z * z);
		return new Vec3(x, r < LuciferArena.RADIUS - 2 ? floorY : LuciferArena.groundY(level, (int) Math.floor(x), (int) Math.floor(z)), z);
	}

	/** The Seraph's height over the ice: hovering, breathing up and down a little. */
	double hover(double x, double z) {
		return floorY + 0.25 + 0.2 * Math.sin(tick * 0.08);
	}

	/** Keeps a point out of the Emperor's body and inside the pit. */
	Vec3 clampToRing(Vec3 p) {
		double r = Math.sqrt(p.x * p.x + p.z * p.z);
		double min = SeraphAttacks.bodyRadius();
		double max = LuciferArena.RADIUS - 1.5;
		if (r >= min && r <= max) {
			return p;
		}
		double k = r < 1.0e-4 ? 0 : Mth.clamp(r, min, max) / r;
		return r < 1.0e-4 ? new Vec3(min, p.y, 0) : new Vec3(p.x * k, p.y, p.z * k);
	}

	/** Living things a blow at this point reaches (horizontally within radius, about the same height). */
	List<LivingEntity> victims(Vec3 centre, double radius) {
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(radius, 6, radius))) {
			if (!e.isAlive() || LuciferManager.isLucifer(e) || e instanceof Player p && (p.isSpectator() || p.isCreative())) {
				continue;
			}
			double dx = e.getX() - centre.x;
			double dz = e.getZ() - centre.z;
			if (dx * dx + dz * dz <= radius * radius) {
				out.add(e);
			}
		}
		return out;
	}

	/** Players worth attacking; if none (tests), any other living thing in the pit. */
	List<LivingEntity> targets() {
		List<LivingEntity> players = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (p.isAlive() && !p.isSpectator() && !p.isCreative() && LuciferArena.inside(p.getX(), p.getZ()) && p.getY() < floorY + 30) {
				players.add(p);
			}
		}
		if (!players.isEmpty()) {
			return players;
		}
		double r = LuciferArena.RADIUS;
		return level.getEntitiesOfClass(LivingEntity.class, new AABB(-r, floorY - 12, -r, r, floorY + 30, r),
				e -> e.isAlive() && !LuciferManager.isLucifer(e) && !(e instanceof ServerPlayer));
	}

	// ------------------------------------------------------------------ the ready check

	/** The pit's edge: tells everyone near that the fight is about to start, and when. */
	void ready() {
		setPhase(Phase.READY);
		for (ServerPlayer p : audience()) {
			Dialogue.notice(p, Component.literal("Lucifer's pit. The fight starts in 30 seconds, sooner once everyone near is down on the pit's "
					+ "floor. When it starts, the pit seals: nobody gets in or out until it's over.").withStyle(ChatFormatting.GOLD));
			Feedback.sound(p, SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 1.0f, 0.5f);
		}
	}

	/** One real tick of the ready check. */
	private void readyTick() {
		List<ServerPlayer> near = new ArrayList<>();
		int in = 0;
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && !p.isCreative() && p.getX() * p.getX() + p.getZ() * p.getZ() < 80 * 80) {
				near.add(p);
				if (LuciferArena.inside(p.getX(), p.getZ()) && p.getY() < floorY + 12) {
					in++;
				}
			}
		}
		if (in == 0) {
			if (++emptyTicks > 200) {
				// everyone went back up: no fight after all
				HellcraftMod.LOGGER.info("Lucifer's ready check called off (nobody in the pit)");
				setPhase(Phase.DONE);
			}
			return;
		}
		emptyTicks = 0;
		if (in == near.size() && readyLeft > READY_ALL_IN) {
			// everyone is in: no need for the point-of-no-return warning, the first notice said the pit seals
			readyLeft = READY_ALL_IN;
			warnedNoReturn = true;
		}
		readyLeft--;
		if (readyLeft % 20 == 0) {
			Component text = Component.literal("The pit seals in " + (readyLeft / 20) + " s. Everyone fighting: down on the pit's floor.")
					.withStyle(readyLeft <= 200 ? ChatFormatting.RED : ChatFormatting.GOLD);
			for (ServerPlayer p : near) {
				p.sendOverlayMessage(text);
			}
		}
		if (!warnedNoReturn && readyLeft <= 200) {
			warnedNoReturn = true;
			for (ServerPlayer p : near) {
				Dialogue.notice(p, Component.literal("Point of no return in 10 seconds: once the pit seals, nobody gets out until Lucifer or you fall. "
						+ "Climb back up now if you're not ready.").withStyle(ChatFormatting.RED));
				Feedback.sound(p, SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 1.5f, 0.6f);
			}
		}
		if (readyLeft <= 0) {
			begin();
		}
	}

	// ------------------------------------------------------------------ lifecycle

	/** The pit seals and the Fallen Seraph comes down. */
	void begin() {
		LuciferArena.forceLoad(level, true);
		discardStrays();
		LuciferArena.seal(level);
		LuciferArena.fillHoles(level, floorY);
		LuciferArena.raisePillars(level, floorY);
		floor = LuciferArena.snapshot(level);
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && LuciferArena.inside(p.getX(), p.getZ()) && p.getY() < floorY + 30) {
				participants.putIfAbsent(p.getUUID(), 0.0f);
			}
		}
		HellState state = HellState.get(level.getServer());
		for (UUID id : participants.keySet()) {
			HellState.Soul soul = state.existing(id);
			if (soul != null && soul.slewLucifer) {
				veterans.add(soul.name);
			}
		}
		EmperorEntity emperor = spawnEmperor();
		emperorId = emperor.getUUID();
		// the Seraph comes down between the Emperor and the fighters
		double angle = descentAngle();
		Vec3 landing = new Vec3(Math.cos(angle) * DESCENT, floorY, Math.sin(angle) * DESCENT);
		float facing = (float) Math.toDegrees(angle) - 90.0f;
		FallenSeraphEntity seraph = spawnSeraph(landing, facing);
		seraphId = seraph.getUUID();
		applyVeteranScaling(seraph);
		Cutscene descent = Cutscenes.LUCIFER_DESCENT;
		for (ServerPlayer p : audience()) {
			MapSync.unlockCodex(p, "lucifer");
			CutsceneManager.play(p, descent, landing, facing);
			Feedback.sound(p, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 1.0f, 0.6f);
		}
		if (!veterans.isEmpty()) {
			sayLater(descent.ticks() - 10, LuciferDialogue.veteranLine(veterans));
		}
		sayLater(descent.ticks() - 4, LuciferDialogue.INTRO[2]);
		updateBarName("LUCIFER — The Fallen Seraph");
		setPhase(Phase.INTRO);
		schedule(descent.ticks(), () -> {
			FallenSeraphEntity s = seraph();
			if (s != null) {
				s.setPermanentlyInvulnerable(false);
			}
			setPhase(Phase.DUEL);
			tip(LuciferDialogue.DESCENT_TIP);
		});
	}

	/** The direction the fighters mostly are, seen from the centre. */
	private double descentAngle() {
		double sx = 0;
		double sz = 0;
		for (ServerPlayer p : fighters()) {
			double r = Math.max(1, Math.hypot(p.getX(), p.getZ()));
			sx += p.getX() / r;
			sz += p.getZ() / r;
		}
		return sx * sx + sz * sz < 1.0e-4 ? 0.0 : Math.atan2(sz, sx);
	}

	/** One real tick (LuciferManager): the ready check, or as many fight ticks as the tempo gives. */
	void update() {
		realTicks++;
		if (phase == Phase.DONE) {
			return;
		}
		if (phase == Phase.READY) {
			readyTick();
			return;
		}
		double t = tempo();
		clock += t;
		while (clock >= 1.0 && phase != Phase.DONE) {
			clock -= 1.0;
			Danger.stretched(1.0 / t, this::step);
		}
	}

	private void step() {
		tick++;
		phaseTick++;
		runScheduled();
		if (phase == Phase.DONE) {
			return;
		}
		if (tick % 100 == 0) {
			// someone may have run /forceload remove; the pit must stay loaded while he fights
			LuciferArena.forceLoad(level, true);
		}
		if (tick % 10 == 1) {
			float tempo = (float) tempo();
			for (BossEntity b : new BossEntity[]{seraph(), emperor()}) {
				if (b != null && b.tempo() != tempo) {
					b.tempo(tempo);
				}
			}
		}
		updateBar();
		List<ServerPlayer> watching = audience();
		music.tick(watching, realTicks);
		tickStyles();
		pushOutOfHim();
		if (floor != null && tick % 10 == 0) {
			LuciferArena.heal(level, floor);
		}
		switch (phase) {
			case DUEL, ENRAGED -> {
				FallenSeraphEntity seraph = seraph();
				if (seraph == null) {
					if (++missingTicks > 600) {
						HellcraftMod.LOGGER.warn("The Fallen Seraph vanished without dying; revealing the Emperor");
						beginTrueFormTransition();
					}
					return;
				}
				missingTicks = 0;
				if (checkForFailure()) {
					return;
				}
				if (phase == Phase.DUEL && seraph.getHealth() <= seraph.getMaxHealth() * 0.5f) {
					beginEnrage();
					return;
				}
				seraphAttacks.tick(seraph, targets());
			}
			case TRUE_FORM -> {
				EmperorEntity emperor = emperor();
				if (emperor == null) {
					if (++missingTicks > 600) {
						HellcraftMod.LOGGER.warn("The Emperor vanished without dying; ending the fight without a victory");
						fail("Lucifer's body disappeared, so the fight is over. He'll be back later.");
					}
					return;
				}
				missingTicks = 0;
				if (checkForFailure()) {
					return;
				}
				emperorAttacks.tick(emperor, targets());
			}
			default -> {
			}
		}
	}

	/** Nobody stands inside the Emperor's body: whoever walks in is pushed back out. */
	private void pushOutOfHim() {
		double r = EmperorEntity.BODY_RADIUS;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(-r, floorY - 4, -r, r, floorY + 18, r))) {
			if (LuciferManager.isLucifer(e) || e instanceof Player p && p.isSpectator()) {
				continue;
			}
			double d = Math.hypot(e.getX(), e.getZ());
			if (d < r) {
				Vec3 out = d < 1.0e-3 ? new Vec3(1, 0, 0) : new Vec3(e.getX() / d, 0, e.getZ() / d);
				e.setPos(out.x * (r + 0.5), Math.max(e.getY(), floorY), out.z * (r + 0.5));
				if (e instanceof ServerPlayer p) {
					p.connection.teleport(p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
				}
			}
		}
	}

	/** Returns true if the fight was abandoned. */
	private boolean checkForFailure() {
		if (debug) {
			return false;
		}
		boolean anyone = !fighters().isEmpty();
		emptyTicks = anyone ? 0 : emptyTicks + 1;
		if (emptyTicks > FAIL_AFTER_EMPTY_TICKS) {
			fail(LuciferDialogue.FAIL);
			return true;
		}
		return false;
	}

	/** The fight ends without a victory: no rewards, he returns after the retry cooldown. */
	private void fail(@Nullable String line) {
		if (phase == Phase.FAILED || phase == Phase.DONE) {
			return;
		}
		scheduled.clear();
		setPhase(Phase.FAILED);
		if (line != null) {
			say(line);
		}
		schedule(40, () -> {
			cleanup();
			HellState state = HellState.get(level.getServer());
			state.luciferNextSpawn = level.getGameTime() + HellConfig.get().luciferRetryMinutes * 1200L;
			state.setDirty();
			setPhase(Phase.DONE);
		});
	}

	// ------------------------------------------------------------------ phase changes

	private void setPhase(Phase next) {
		phase = next;
		phaseTick = 0;
		HellcraftMod.LOGGER.info("Lucifer phase: {}{}", next, next == Phase.INTRO && tier() > 0 ? " (returning champions: " + veterans + ")" : "");
		List<ServerPlayer> audience = audience();
		switch (next) {
			case DUEL -> {
				music.switchTo(BossMusic.Track.DUEL, audience, realTicks);
				if (tier() >= 2) {
					raiseTraitors();
				}
			}
			case ENRAGED -> music.switchTo(BossMusic.Track.ENRAGED, audience, realTicks);
			case TRUE_FORM -> music.switchTo(BossMusic.Track.TRUE_FORM, audience, realTicks);
			case DEFEAT, FAILED, DONE -> music.stopAll(audience);
			default -> {
			}
		}
	}

	/** Half his health: the Seraph stops pretending. */
	private void beginEnrage() {
		FallenSeraphEntity seraph = seraph();
		if (seraph == null) {
			return;
		}
		setPhase(Phase.ENRAGE_TRANSITION);
		scheduled.clear();
		seraph.setPermanentlyInvulnerable(true);
		seraph.warn(BossEntity.Warning.NONE);
		seraph.play(FallenSeraphAnim.SNAP);
		for (int i = 0; i < LuciferDialogue.ENRAGE.length; i++) {
			sayLater(i * 18, LuciferDialogue.ENRAGE[i]);
		}
		level.playSound(null, seraph.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3.0f, 1.4f);
		schedule(FallenSeraphAnim.SNAP.at("snap"), () -> {
			FallenSeraphEntity s = seraph();
			if (s == null) {
				return;
			}
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, s.getX(), s.getY() + 2, s.getZ(), 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, s.getX(), s.getY() + 2, s.getZ(), 200, 4, 2, 4, 0.2);
			s.addEffect(new MobEffectInstance(MobEffects.GLOWING, MobEffectInstance.INFINITE_DURATION, 0, false, false));
			raiseTraitors();
			updateBarName("LUCIFER — The Morning Star");
			bar.setColor(BossEvent.BossBarColor.PURPLE);
			LuciferDialogue.nameCard(audience(), "ENOUGH!", "The Morning Star unbound", ChatFormatting.RED);
		});
		schedule(FallenSeraphAnim.SNAP.ticks, () -> {
			FallenSeraphEntity s = seraph();
			if (s != null) {
				s.setPermanentlyInvulnerable(false);
			}
			setPhase(Phase.ENRAGED);
			tip("He's faster now, and often strikes twice. Keep blocking the white flashes.");
		});
	}

	/** The seraph's form breaks, and the mountain of the pit opens six eyes. */
	private void beginTrueFormTransition() {
		if (phase == Phase.TRUE_FORM_TRANSITION || phase == Phase.TRUE_FORM || phase == Phase.DEFEAT) {
			return;
		}
		setPhase(Phase.TRUE_FORM_TRANSITION);
		scheduled.clear();
		FallenSeraphEntity seraph = seraph();
		int breakAt = 0;
		if (seraph != null) {
			seraph.setPermanentlyInvulnerable(true);
			seraph.warn(BossEntity.Warning.NONE);
			seraph.play(FallenSeraphAnim.DEATH);
			breakAt = FallenSeraphAnim.DEATH.at("break");
		}
		schedule(breakAt, () -> {
			FallenSeraphEntity s = seraph();
			if (s != null) {
				level.sendParticles(ParticleTypes.END_ROD, s.getX(), s.getY() + 2.5, s.getZ(), 300, 1, 1.5, 1, 0.3);
				level.sendParticles(ParticleTypes.SNOWFLAKE, s.getX(), s.getY() + 2, s.getZ(), 300, 2, 2, 2, 0.4);
				level.playSound(null, s.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 3.0f, 0.4f);
				s.discard();
			}
			for (UUID id : traitors) {
				Entity t = level.getEntity(id);
				if (t != null) {
					t.discard();
				}
			}
			traitors.clear();
			revealEmperor();
		});
	}

	private void revealEmperor() {
		EmperorEntity emperor = emperor();
		if (emperor == null) {
			emperor = spawnEmperor();
			emperorId = emperor.getUUID();
		}
		applyVeteranScaling(emperor);
		emperor.play(EmperorAnim.REVEAL);
		for (ServerPlayer p : audience()) {
			CutsceneManager.play(p, Cutscenes.LUCIFER_REVEAL, emperor.position(), emperor.getYRot());
		}
		bar.removeAllPlayers();
		schedule(EmperorAnim.REVEAL.at("open"), () -> {
			EmperorEntity e = emperor();
			if (e != null) {
				e.awake(true);
				level.playSound(null, e.blockPosition().above(20), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 5.0f, 0.4f);
			}
		});
		schedule(EmperorAnim.REVEAL.at("roar"), () -> {
			for (ServerPlayer p : audience()) {
				net.thesmallthings.hellcraft.hazard.Wingbeat.beat(p, 1.0f);
			}
		});
		schedule(Cutscenes.LUCIFER_REVEAL.ticks(), () -> {
			updateBarName("LUCIFER — The Three-Faced Emperor");
			bar.setColor(BossEvent.BossBarColor.BLUE);
			setPhase(Phase.TRUE_FORM);
		});
	}

	/** His health is gone: he freezes, weeps, and the way out is through him. */
	private void beginDefeat() {
		if (phase == Phase.DEFEAT || phase == Phase.DONE) {
			return;
		}
		setPhase(Phase.DEFEAT);
		scheduled.clear();
		bar.removeAllPlayers();
		styles.values().forEach(StyleMeter::finish);
		EmperorEntity emperor = emperor();
		if (emperor != null) {
			emperor.setPermanentlyInvulnerable(true);
			emperor.warn(BossEntity.Warning.NONE);
			emperor.play(EmperorAnim.DEATH);
		}
		for (Entity e : level.getEntitiesOfClass(Entity.class, new AABB(-50, floorY - 10, -50, 50, floorY + 40, 50), e -> owned.contains(e.getUUID()))) {
			e.discard();
		}
		for (int i = 0; i < LuciferDialogue.DEFEAT.length; i++) {
			sayLater(10 + i * 40, LuciferDialogue.DEFEAT[i]);
		}
		schedule(EmperorAnim.DEATH.at("freeze"), () -> {
			EmperorEntity e = emperor();
			if (e != null) {
				level.sendParticles(ParticleTypes.SNOWFLAKE, e.getX(), e.getY() + 15, e.getZ(), 800, 12, 12, 12, 0.1);
				level.playSound(null, e.blockPosition().above(15), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 4.0f, 0.3f);
			}
		});
		// the victors climb down his side and out of Hell
		List<ServerPlayer> climbers = new ArrayList<>();
		schedule(EmperorAnim.DEATH.ticks, () -> {
			EmperorEntity e = emperor();
			Vec3 at = e != null ? e.position() : new Vec3(0.5, floorY, 0.5);
			float yaw = e != null ? e.getYRot() : EMPEROR_FACING;
			climbers.addAll(fighters());
			for (ServerPlayer p : climbers) {
				CutsceneManager.play(p, Cutscenes.LUCIFER_CLIMB, at, yaw);
			}
		});
		schedule(EmperorAnim.DEATH.ticks + Cutscenes.LUCIFER_CLIMB.ticks() - 2, () -> {
			// each on its own: whatever goes wrong with one, the victors still get their spoils and their way out
			step("rewards", this::reward);
			step("the climb out", () -> {
				for (ServerPlayer p : climbers) {
					if (p.isAlive() && p.level() == level) {
						Purgatory.arrive(p);
					}
				}
			});
			step("the burrow", () -> Purgatory.openBurrow(level, burrow()));
			step("the cleanup", this::cleanup);
			setPhase(Phase.DONE);
		});
	}

	/** The way out for anyone who missed the climb: a tunnel in the ice at his back. */
	private net.minecraft.core.BlockPos burrow() {
		return burrowAt(floorY);
	}

	/** Where the burrow opens for a pit floor at this height (/hellcraft lucifer openburrow uses it too). */
	public static net.minecraft.core.BlockPos burrowAt(int floorY) {
		double yaw = Math.toRadians(EMPEROR_FACING);
		double bx = Math.sin(yaw) * (EmperorEntity.BODY_RADIUS + 3);
		double bz = -Math.cos(yaw) * (EmperorEntity.BODY_RADIUS + 3);
		return net.minecraft.core.BlockPos.containing(bx, floorY, bz);
	}

	private void step(String what, Runnable action) {
		try {
			action.run();
		} catch (RuntimeException e) {
			HellcraftMod.LOGGER.error("Lucifer's defeat: {} failed", what, e);
		}
	}

	// ------------------------------------------------------------------ hooks (from LuciferManager)

	boolean allowDamage(LivingEntity entity) {
		if (entity.getUUID().equals(seraphId)) {
			return phase == Phase.DUEL || phase == Phase.ENRAGED;
		}
		if (entity.getUUID().equals(emperorId)) {
			return phase == Phase.TRUE_FORM;
		}
		return true;
	}

	/** Everyone fighting in the pit has a style meter; each keeps count while the fight is on. */
	private void tickStyles() {
		if (!fighting(phase) && phase != Phase.ENRAGE_TRANSITION && phase != Phase.TRUE_FORM_TRANSITION) {
			return;
		}
		for (ServerPlayer p : fighters()) {
			if (!p.isCreative() && !styles.containsKey(p.getUUID())) {
				styles.put(p.getUUID(), new StyleMeter(p));
				Dialogue.notice(p, Component.literal("Your style is ranked in this fight (the STYLE bar). Hit hard, swap weapons and don't get hit: "
						+ "the better your style, the better your spoils.").withStyle(ChatFormatting.AQUA));
			}
		}
		Entity boss = seraph();
		if (boss == null) {
			boss = emperor();
		}
		for (Map.Entry<UUID, StyleMeter> e : styles.entrySet()) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(e.getKey());
			if (p != null) {
				e.getValue().tick(p, boss);
			}
		}
	}

	void afterDamage(LivingEntity entity, DamageSource source, float dealt, boolean blocked) {
		// a fighter hurt in the pit by anything but another player loses style; a shield block earns it
		StyleMeter hurt = styles.get(entity.getUUID());
		if (hurt != null && !(source.getEntity() instanceof Player) && LuciferArena.inside(entity.getX(), entity.getZ())) {
			if (blocked && source.getEntity() != null && BossRules.fromBoss(source.getEntity())) {
				hurt.onParry();
				if (tick <= parryUntil) {
					parryUntil = -1;
					FallenSeraphEntity s = seraph();
					EmperorEntity m = emperor();
					if (s != null && source.getEntity() == s) {
						seraphAttacks.parried(s);
					} else if (m != null && source.getEntity() == m) {
						emperorAttacks.parried(m);
					}
				}
			} else if (dealt > 0) {
				hurt.onHurt(dealt / Math.max(1.0f, entity.getMaxHealth()));
			}
			return;
		}
		boolean boss = entity.getUUID().equals(seraphId) || entity.getUUID().equals(emperorId);
		if (!boss || !(source.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		boolean newcomer = !participants.containsKey(player.getUUID());
		participants.merge(player.getUUID(), dealt, Float::sum);
		if (styles.containsKey(player.getUUID())) {
			styles.get(player.getUUID()).onHit(player, source, dealt);
		}
		if (newcomer) {
			HellState.Soul soul = HellState.get(level.getServer()).existing(player.getUUID());
			if (soul != null && soul.slewLucifer && !veterans.contains(soul.name)) {
				veterans.add(soul.name);
				FallenSeraphEntity s = seraph();
				if (s != null) {
					applyVeteranScaling(s);
				}
				EmperorEntity m = emperor();
				if (m != null && phase == Phase.TRUE_FORM) {
					applyVeteranScaling(m);
				}
				say(LuciferDialogue.veteranLine(List.of(soul.name)));
			}
		}
		if (dealt >= 7.0f && tick - lastHurtBark > 120 && level.getRandom().nextFloat() < 0.4f) {
			lastHurtBark = tick;
			say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.HURT));
		}
	}

	/** Neither body truly dies: the Seraph breaks into the Emperor, and the Emperor freezes. */
	boolean allowDeath(LivingEntity entity) {
		if (entity.getUUID().equals(seraphId)) {
			entity.setHealth(1.0f);
			beginTrueFormTransition();
			return false;
		}
		if (entity.getUUID().equals(emperorId)) {
			entity.setHealth(1.0f);
			HellcraftMod.LOGGER.info("Lucifer's true form is slain");
			beginDefeat();
			return false;
		}
		return true;
	}

	void onDeath(LivingEntity entity, DamageSource source) {
		StyleMeter fallen = styles.get(entity.getUUID());
		if (fallen != null) {
			fallen.onDeath();
		} else if (LuciferManager.isLucifer(entity) && traitors.contains(entity.getUUID()) && source.getEntity() != null
				&& styles.containsKey(source.getEntity().getUUID())) {
			// one of his traitors, killed by a fighter
			styles.get(source.getEntity().getUUID()).onCleanup();
		}
		if (entity instanceof ServerPlayer player && LuciferArena.inside(player.getX(), player.getZ()) && fighting(phase)) {
			sayLater(10, LuciferDialogue.pick(level.getRandom(), LuciferDialogue.PLAYER_DEATH));
		}
	}

	/** Test helper: jump to the next phase. */
	void skip() {
		switch (phase) {
			case READY -> begin();
			case INTRO -> {
				scheduled.clear();
				FallenSeraphEntity s = seraph();
				if (s != null) {
					s.setPermanentlyInvulnerable(false);
				}
				setPhase(Phase.DUEL);
			}
			case DUEL -> beginEnrage();
			case ENRAGED -> beginTrueFormTransition();
			case TRUE_FORM -> {
				// killed the way nothing but a command can, so the test covers his real end
				EmperorEntity m = emperor();
				if (m != null) {
					m.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
				}
			}
			default -> {
			}
		}
	}

	/** Test helper: one of the Seraph's attacks now, at the nearest target. */
	boolean forceAttack(SeraphAttacks.Attack attack) {
		FallenSeraphEntity s = seraph();
		List<LivingEntity> targets = targets();
		if (s == null || targets.isEmpty() || !fighting(phase)) {
			return false;
		}
		Danger.stretched(1.0 / tempo(), () -> seraphAttacks.perform(attack, s, targets.getFirst()));
		return true;
	}

	/** Test helper: one of the Emperor's attacks now. */
	boolean forceEmperorAttack(EmperorAttacks.Attack attack) {
		EmperorEntity m = emperor();
		List<LivingEntity> targets = targets();
		if (phase != Phase.TRUE_FORM || m == null || targets.isEmpty()) {
			return false;
		}
		Danger.stretched(1.0 / tempo(), () -> emperorAttacks.perform(attack, m, targets.getFirst()));
		return true;
	}

	void stop() {
		cleanup();
		setPhase(Phase.DONE);
	}

	// ------------------------------------------------------------------ helpers

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
				HellcraftMod.LOGGER.warn("Lucifer fight action failed", e);
			}
		}
	}

	private void updateBar() {
		if (phase == Phase.TRUE_FORM_TRANSITION || phase == Phase.DEFEAT || phase == Phase.FAILED) {
			return;
		}
		List<ServerPlayer> audience = audience();
		for (ServerPlayer p : new ArrayList<>(bar.getPlayers())) {
			if (!audience.contains(p)) {
				bar.removePlayer(p);
			}
		}
		for (ServerPlayer p : audience) {
			bar.addPlayer(p);
		}
		LivingEntity body = phase == Phase.TRUE_FORM ? emperor() : seraph();
		if (phase == Phase.INTRO) {
			bar.setProgress(Math.min(1.0f, phaseTick / (float) Cutscenes.LUCIFER_DESCENT.ticks()));
		} else if (body != null) {
			bar.setProgress(Math.max(0.0f, body.getHealth() / body.getMaxHealth()));
		}
	}

	private static void setBase(LivingEntity entity, Holder<Attribute> attribute, double value) {
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	private EmperorEntity spawnEmperor() {
		EmperorEntity emperor = HellEntities.EMPEROR.create(level, EntitySpawnReason.EVENT);
		if (emperor == null) {
			throw new IllegalStateException("could not create the Emperor");
		}
		emperor.snapTo(0.5, floorY, 0.5, EMPEROR_FACING, 0.0f);
		emperor.setYBodyRot(EMPEROR_FACING);
		emperor.setYHeadRot(EMPEROR_FACING);
		emperor.addTag(LuciferManager.TAG);
		emperor.addTag(BossRules.BODY_TAG);
		emperor.setCustomName(Component.literal("Lucifer, Three-Faced Emperor").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
		setBase(emperor, Attributes.MAX_HEALTH, HellConfig.get().luciferHealth);
		emperor.setHealth(emperor.getMaxHealth());
		emperor.play(EmperorAnim.DORMANT);
		level.addFreshEntity(emperor);
		return emperor;
	}

	private FallenSeraphEntity spawnSeraph(Vec3 at, float facing) {
		FallenSeraphEntity seraph = HellEntities.FALLEN_SERAPH.create(level, EntitySpawnReason.EVENT);
		if (seraph == null) {
			throw new IllegalStateException("could not create the Fallen Seraph");
		}
		seraph.snapTo(at.x, floorY + 0.25, at.z, facing, 0.0f);
		seraph.setYBodyRot(facing);
		seraph.setYHeadRot(facing);
		seraph.addTag(LuciferManager.TAG);
		seraph.addTag(BossRules.BODY_TAG);
		seraph.setCustomName(Component.literal("Lucifer").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
		setBase(seraph, Attributes.MAX_HEALTH, HellConfig.get().luciferAvatarHealth);
		seraph.setHealth(seraph.getMaxHealth());
		seraph.setPermanentlyInvulnerable(true);
		seraph.play(FallenSeraphAnim.INTRO);
		level.addFreshEntity(seraph);
		return seraph;
	}

	private WitherSkeleton spawnTraitor(String name, double x, double z) {
		WitherSkeleton traitor = EntityTypes.WITHER_SKELETON.create(level, EntitySpawnReason.EVENT);
		if (traitor == null) {
			throw new IllegalStateException("could not create " + name);
		}
		Vec3 at = onFloor(x, z);
		traitor.snapTo(at.x, at.y, at.z, 0.0f, 0.0f);
		traitor.addTag(LuciferManager.TAG);
		traitor.setCustomName(Component.literal(name).withStyle(ChatFormatting.GRAY));
		traitor.setCustomNameVisible(true);
		traitor.setPersistenceRequired();
		setBase(traitor, Attributes.SCALE, 1.3);
		setBase(traitor, Attributes.MAX_HEALTH, 40.0);
		traitor.setHealth(traitor.getMaxHealth());
		traitor.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			traitor.setDropChance(slot, 0.0f);
		}
		level.addFreshEntity(traitor);
		level.sendParticles(ParticleTypes.SOUL, at.x, at.y + 1, at.z, 30, 0.5, 1, 0.5, 0.05);
		return traitor;
	}

	/** Removes every trace of the fight except the rewards. */
	private void cleanup() {
		scheduled.clear();
		bar.removeAllPlayers();
		styles.values().forEach(StyleMeter::finish);
		discardStrays();
		traitors.clear();
		owned.clear();
		if (floor != null) {
			LuciferArena.heal(level, floor);
		}
		LuciferArena.lowerPillars(level, floorY);
		LuciferArena.unseal(level);
		LuciferArena.forceLoad(level, false);
	}

	/** Removes every Lucifer-tagged entity in and around the pit. */
	private void discardStrays() {
		double r = LuciferArena.RADIUS + 24;
		for (Entity e : level.getEntitiesOfClass(Entity.class, new AABB(-r, level.getMinY(), -r, r, level.getMaxY() + 1, r),
				e -> LuciferManager.isLucifer(e) && e.isAlive())) {
			e.discard();
		}
	}

	/** True if this entity is one of the fight's own (anything else tagged as Lucifer is a stray). */
	boolean owns(UUID id) {
		return id.equals(seraphId) || id.equals(emperorId) || traitors.contains(id) || owned.contains(id);
	}

	String status() {
		return "phase=" + phase + " tick=" + tick + " avatar=" + (seraph() != null ? "present" : seraphId == null ? "none" : "missing")
				+ " trueForm=" + (emperor() != null ? "present" : emperorId == null ? "none" : "missing")
				+ " awake=" + (emperor() != null && emperor().awake()) + " traitors=" + traitors.size() + " floor=" + floorY
				+ (phase == Phase.TRUE_FORM ? " emperorHalved=" + emperorAttacks.halved() : "") + " participants=" + participants.size()
				+ " champions=" + veterans;
	}


	// ------------------------------------------------------------------ the reward

	private void reward() {
		HellConfig config = HellConfig.get();
		HellState state = HellState.get(level.getServer());
		state.luciferDefeats++;
		state.luciferNextSpawn = level.getGameTime() + config.luciferCooldownMinutes * 1200L;
		state.setDirty();

		List<String> victors = new ArrayList<>();
		int pending = 0;
		for (UUID id : participants.keySet()) {
			HellState.Soul soul = state.existing(id);
			if (soul == null) {
				continue;
			}
			ServerPlayer present = level.getServer().getPlayerList().getPlayer(id);
			boolean standing = present != null && present.isAlive() && !present.isSpectator() && present.level() == level
					&& present.getX() * present.getX() + present.getZ() * present.getZ() < (LuciferArena.RADIUS + 4) * (LuciferArena.RADIUS + 4);
			if (!standing) {
				if (present != null) {
					present.sendSystemMessage(Component.literal("You died before Lucifer did, so you get no reward.")
							.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
				}
				continue;
			}
			victors.add(soul.name);
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
			// ranked by style like the guardians: every victory pays by rank, the first S and SSS a Blood Heart each
			StyleMeter style = styles.get(id);
			Judgement.Grade grade = style != null ? style.grade() : Judgement.Grade.D;
			String summary = style != null ? style.summary() : "you never fought in the pit.";
			String advice = style != null ? style.advice() : "";
			List<String> got = new ArrayList<>();
			int marks = Judgement.marks(soul, "lucifer", grade);
			if (player != null && marks > 0) {
				BloodItems.give(player, BloodItems.heart(marks));
				got.add(Judgement.hearts(marks, Judgement.marksReason(grade, marks)));
			}
			if (player != null && BossSpoils.claim(player, "lucifer", config.luciferSpoilsCooldownMinutes) == BossSpoils.Claim.TOO_SOON) {
				// cast down again before his full reward returns: the victory counts (the Hall, the Journey),
				// and pays by rank
				soul.luciferKills++;
				state.setDirty();
				Scoreboards.update(level.getServer(), soul);
				Journey.award(player, "journey/lucifer");
				int fragments = 2 * grade.fragments;
				BloodItems.give(player, BloodItems.fragment(fragments));
				got.add(0, fragments + " Blood Fragments");
				if (grade.atLeast(Judgement.Grade.S)) {
					Judgement.treasure(player);
					got.add(1, "a treasure roll");
				}
				Judgement.report(player, "Lucifer", grade, summary, got, advice,
						"Lucifer's full reward returns for you in " + Math.max(1, BossSpoils.minutesLeft(player, "lucifer",
								config.luciferSpoilsCooldownMinutes)) + " min.");
				continue;
			}
			if (player != null) {
				got.add(0, "Lucifer's reward (choose one in the window that opens)");
				Judgement.report(player, "Lucifer", grade, summary, got, advice, "");
			}
			LuciferRewards.grant(state, soul);
			Scoreboards.update(level.getServer(), soul);
			pending++;
			if (player != null) {
				Journey.award(player, "journey/lucifer");
				Feedback.sound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0f, 1.0f);
				LuciferRewards.remind(player);
				// the fight object is finished by then, so open it from the server's task queue
				level.getServer().execute(() -> LuciferRewards.open(player));
			}
		}
		HellcraftMod.LOGGER.info("Lucifer rewards: {} pending", pending);
		String who = victors.isEmpty() ? "Someone" : String.join(", ", victors);
		level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(who + " defeated Lucifer!")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		level.getServer().getPlayerList().broadcastSystemMessage(Component.literal("\"Thence we came forth to rebehold the stars.\"")
				.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
	}

	// ------------------------------------------------------------------ returning champions

	private String stars() {
		return "\u2726".repeat(Math.max(0, tier()));
	}

	private void updateBarName(String name) {
		bar.setName(Component.literal(name + (tier() > 0 ? "  " + stars() : "")).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
	}

	/** Each returning champion makes this form tougher (keeps its current health fraction). */
	private void applyVeteranScaling(LivingEntity boss) {
		AttributeInstance health = boss.getAttribute(Attributes.MAX_HEALTH);
		if (health == null) {
			return;
		}
		float fraction = boss.getHealth() / Math.max(1.0f, boss.getMaxHealth());
		health.removeModifier(VETERAN_MODIFIER);
		if (tier() > 0) {
			health.addPermanentModifier(new AttributeModifier(VETERAN_MODIFIER, HellConfig.get().luciferVeteranHealthBonus * tier(),
					AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
		boss.setHealth(Math.max(1.0f, boss.getMaxHealth() * fraction));
		if (phase == Phase.DUEL || phase == Phase.ENRAGED) {
			updateBarName(phase == Phase.ENRAGED ? "LUCIFER \u2014 The Morning Star" : "LUCIFER \u2014 The Fallen Seraph");
		}
	}

	/** Judas, Brutus and Cassius, plus one more traitor of Antenora or Ptolomea per returning champion. */
	private void raiseTraitors() {
		if (traitorsRaised) {
			return;
		}
		traitorsRaised = true;
		String[] all = {"Judas", "Brutus", "Cassius", "Mordred", "Ganelon", "Ugolino", "Alberigo"};
		int count = Math.min(all.length, 3 + tier());
		for (int i = 0; i < count; i++) {
			double ang = i * 2 * Math.PI / count;
			traitors.add(spawnTraitor(all[i], Math.cos(ang) * 20, Math.sin(ang) * 20).getUUID());
		}
	}

}
