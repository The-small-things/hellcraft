package net.thesmallthings.hellcraft.hazard.lucifer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.blood.Scoreboards;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.hazard.BossModel;
import net.thesmallthings.hellcraft.music.MusicPack;
import net.thesmallthings.hellcraft.util.Feedback;
import net.thesmallthings.hellcraft.util.Journey;
import net.thesmallthings.hellcraft.world.Purgatory;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One fight against Lucifer, from the moment the pit seals to the moment it opens again.
 *
 * <pre>
 * INTRO -> DUEL -(50%)-> ENRAGE_TRANSITION -> ENRAGED -(0%)-> TRUE_FORM_TRANSITION -> TRUE_FORM -(Wither dies)-> DEFEAT
 *                     \______________________ nobody left alive in the pit for 30 s ______________________/-> FAILED
 * </pre>
 */
public final class LuciferFight {
	enum Phase {INTRO, DUEL, ENRAGE_TRANSITION, ENRAGED, TRUE_FORM_TRANSITION, TRUE_FORM, DEFEAT, FAILED, DONE}

	private static final String AVATAR_TAG = LuciferManager.TAG + "_avatar";
	private static final double AUDIENCE_RADIUS = 96.0;
	private static final int INTRO_LENGTH = 280;
	private static final int FAIL_AFTER_EMPTY_TICKS = 600;
	private static final Identifier VETERAN_MODIFIER = HellcraftMod.id("veteran");

	private final ServerLevel level;
	/** Started by a command with nobody around: never fails for lack of players (used by tests). */
	private final boolean debug;
	private final int floorY;
	private final ServerBossEvent bar = new ServerBossEvent(java.util.UUID.randomUUID(), Component.literal("LUCIFER").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
			BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
	private final Map<UUID, Float> participants = new LinkedHashMap<>();
	private final List<Scheduled> scheduled = new ArrayList<>();
	private final List<UUID> traitors = new ArrayList<>();
	private final LuciferMusic music;
	private final BossModel model;
	/** Returning champions in this round; each one makes Lucifer harder for everyone. */
	private final List<String> veterans = new ArrayList<>();
	private boolean traitorsRaised;

	private Phase phase = Phase.INTRO;
	private int tick;
	private int phaseTick;
	private int attackCooldown;
	private int emptyTicks;
	private int lastHurtBark = -1000;
	/** Ticks the current boss form has been missing from a loaded arena (it is never assumed dead). */
	private int missingTicks;
	/** The true form's last seen health fraction (so a body that vanishes at death's door still counts as slain). */
	private float lastTrueFormHealth = 1.0f;
	/** The same for the Morning Star: if his first body dies for real, the Emperor still rises. */
	private float lastAvatarHealth = 1.0f;
	@Nullable
	private UUID avatarId;
	@Nullable
	private UUID witherId;
	/** The Emperor's attacks (true form only). */
	@Nullable
	private EmperorAttacks emperor;
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
		this.model = new BossModel(level, "hellcraft_lucifer_model", LuciferManager.TAG, Vec3.ZERO);
		bar.setDarkenScreen(true);
		bar.setCreateWorldFog(true);
		bar.setProgress(1.0f);
	}

	ServerLevel level() {
		return level;
	}

	int floorY() {
		return floorY;
	}

	BossModel model() {
		return model;
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

	int extraLines() {
		return tier();
	}

	int extraMarks() {
		return tier();
	}

	void schedule(int delay, Runnable action) {
		scheduled.add(new Scheduled(tick + Math.max(0, delay), action));
	}

	void say(String line) {
		LuciferDialogue.say(LuciferDialogue.audience(level, AUDIENCE_RADIUS), line);
	}

	private void sayLater(int delay, String line) {
		schedule(delay, () -> say(line));
	}

	// ------------------------------------------------------------------ lifecycle

	void start() {
		LuciferArena.forceLoad(level, true);
		discardStrays();
		LuciferArena.seal(level);
		floor = LuciferArena.snapshot(level);
		for (ServerPlayer p : LuciferDialogue.audience(level, LuciferArena.RADIUS + 4)) {
			if (!p.isSpectator()) {
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
		WitherSkeleton avatar = spawnAvatar();
		avatarId = avatar.getUUID();
		applyVeteranScaling(avatar);
		for (ServerPlayer p : LuciferDialogue.audience(level, AUDIENCE_RADIUS)) {
			Feedback.sound(p, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 1.0f, 0.6f);
		}
		for (int i = 0; i < 4; i++) {
			double a = i * Math.PI / 2 + Math.PI / 4;
			strikeLightning(Math.cos(a) * 18, Math.sin(a) * 18);
		}
		List<String> intro = new ArrayList<>(List.of(LuciferDialogue.INTRO));
		if (!veterans.isEmpty()) {
			intro.add(2, LuciferDialogue.veteranLine(veterans));
		}
		int spacing = (INTRO_LENGTH - 60) / intro.size();
		for (int i = 0; i < intro.size(); i++) {
			sayLater(30 + i * spacing, intro.get(i));
		}
		String subtitle = "The Morning Star" + (tier() > 0 ? " " + stars() : "");
		schedule(INTRO_LENGTH - 30, () -> LuciferDialogue.nameCard(LuciferDialogue.audience(level, AUDIENCE_RADIUS),
				"LUCIFER", subtitle, ChatFormatting.DARK_RED));
		updateBarName("LUCIFER \u2014 The Fallen Seraph");
		setPhase(Phase.INTRO);
	}

	void tick() {
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
		updateBar();
		List<ServerPlayer> watching = LuciferDialogue.audience(level, AUDIENCE_RADIUS);
		music.tick(watching, tick);
		model.tick(watching);
		if (floor != null && tick % 10 == 0) {
			LuciferArena.heal(level, floor);
		}
		switch (phase) {
			case INTRO -> {
				Mob avatar = avatar();
				if (avatar != null && tick % 2 == 0) {
					double a = tick * 0.35;
					double r = 3.5 - (phaseTick % 60) / 20.0;
					level.sendParticles(ParticleTypes.SNOWFLAKE, avatar.getX() + Math.cos(a) * r, avatar.getY() + phaseTick % 60 / 10.0,
							avatar.getZ() + Math.sin(a) * r, 4, 0.1, 0.1, 0.1, 0.0);
					level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, avatar.getX() - Math.cos(a) * r, avatar.getY() + phaseTick % 60 / 10.0,
							avatar.getZ() - Math.sin(a) * r, 2, 0.1, 0.1, 0.1, 0.0);
				}
				if (phaseTick >= INTRO_LENGTH) {
					if (avatar != null) {
						avatar.setNoAi(false);
					}
					setPhase(Phase.DUEL);
				}
			}
			case DUEL, ENRAGED -> fightTick();
			case TRUE_FORM_TRANSITION -> {
				if (phaseTick > 300) {
					HellcraftMod.LOGGER.warn("Lucifer's true form never appeared; revealing it again");
					try {
						revealTrueForm();
					} catch (RuntimeException e) {
						HellcraftMod.LOGGER.error("Lucifer's true form could not be revealed", e);
						fail("The ice groans, but nothing rises. The fight is over, and he will return.");
					}
				}
			}
			case TRUE_FORM -> {
				// his death is also watched for here, not only through the death event (so a victory is never lost)
				Entity body = witherId != null ? level.getEntity(witherId) : null;
				if (body instanceof WitherBoss dying && dying.isDeadOrDying()) {
					HellcraftMod.LOGGER.info("Lucifer's true form is dead (seen by the fight)");
					beginDefeat();
					return;
				}
				WitherBoss wither = wither();
				if (wither == null) {
					if (lastTrueFormHealth <= 0.1f) {
						// gone at death's door: that was the killing blow
						HellcraftMod.LOGGER.info("Lucifer's true form is gone at {}% health: a victory", Math.round(lastTrueFormHealth * 100));
						beginDefeat();
						return;
					}
					// a missing boss is not a dead boss
					bossMissing();
					return;
				}
				lastTrueFormHealth = wither.getHealth() / Math.max(1.0f, wither.getMaxHealth());
				missingTicks = 0;
				if (checkForFailure()) {
					return;
				}
				if (emperor != null) {
					emperor.tick(wither);
				}
			}
			default -> {
			}
		}
	}

	private void fightTick() {
		Mob avatar = avatar();
		if (avatar == null) {
			Entity body = avatarId != null ? level.getEntity(avatarId) : null;
			if ((body instanceof LivingEntity dying && dying.isDeadOrDying()) || lastAvatarHealth <= 0.1f) {
				// the Morning Star died outright instead of shattering: the Emperor rises all the same
				HellcraftMod.LOGGER.info("The Morning Star died outright; revealing the true form");
				beginTrueFormTransition();
				return;
			}
			bossMissing();
			return;
		}
		lastAvatarHealth = avatar.getHealth() / Math.max(1.0f, avatar.getMaxHealth());
		missingTicks = 0;
		leash(avatar, 24);
		if (checkForFailure()) {
			return;
		}
		List<LivingEntity> targets = targets();
		if (!targets.isEmpty() && avatar.getTarget() == null) {
			avatar.setTarget(targets.get(level.getRandom().nextInt(targets.size())));
		}
		if (phase == Phase.DUEL && avatar.getHealth() <= avatar.getMaxHealth() * 0.5f) {
			beginEnrage();
			return;
		}
		if (--attackCooldown <= 0 && !targets.isEmpty()) {
			LivingEntity target = targets.get(level.getRandom().nextInt(targets.size()));
			LuciferAttacks.Attack[] all = LuciferAttacks.Attack.values();
			LuciferAttacks.perform(this, all[level.getRandom().nextInt(all.length)], avatar, target);
			if (phase == Phase.ENRAGED && level.getRandom().nextFloat() < 0.6f) {
				LuciferAttacks.Attack follow = all[level.getRandom().nextInt(all.length)];
				schedule(30, () -> {
					Mob a = avatar();
					if (a != null && target.isAlive() && phase == Phase.ENRAGED) {
						LuciferAttacks.perform(this, follow, a, target);
					}
				});
			}
			double faster = Math.max(0.6, 1.0 - 0.12 * tier());
			attackCooldown = (int) Math.round((phase == Phase.ENRAGED ? 50 : 80) * faster);
		}
	}

	/** Returns true if the fight was abandoned. */
	private boolean checkForFailure() {
		if (debug) {
			return false;
		}
		boolean anyone = level.players().stream().anyMatch(p -> p.isAlive() && !p.isSpectator() && LuciferArena.inside(p.getX(), p.getZ()));
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

	/** The boss entity can't be found even though the pit is loaded (e.g. removed by a command). */
	private void bossMissing() {
		if (++missingTicks > 600) {
			HellcraftMod.LOGGER.warn("Lucifer's body vanished without dying; ending the fight without a victory");
			fail("Lucifer's body is gone from the pit. The fight is over, and he will return.");
		}
	}

	// ------------------------------------------------------------------ phase changes

	private void setPhase(Phase next) {
		phase = next;
		phaseTick = 0;
		HellcraftMod.LOGGER.info("Lucifer phase: {}{}", next, next == Phase.INTRO && tier() > 0 ? " (returning champions: " + veterans + ")" : "");
		List<ServerPlayer> audience = LuciferDialogue.audience(level, AUDIENCE_RADIUS);
		switch (next) {
			case DUEL -> {
				music.switchTo(MusicPack.Track.DUEL, audience, tick);
				if (tier() >= 2) {
					raiseTraitors();
				}
			}
			case ENRAGED -> music.switchTo(MusicPack.Track.ENRAGED, audience, tick);
			case TRUE_FORM -> music.switchTo(MusicPack.Track.TRUE_FORM, audience, tick);
			case DEFEAT, FAILED, DONE -> music.stopAll(audience);
			default -> {
			}
		}
	}

	private void beginEnrage() {
		Mob avatar = avatar();
		if (avatar == null) {
			return;
		}
		setPhase(Phase.ENRAGE_TRANSITION);
		avatar.setNoAi(true);
		scheduled.clear();
		for (int i = 0; i < LuciferDialogue.ENRAGE.length; i++) {
			sayLater(i * 30, LuciferDialogue.ENRAGE[i]);
		}
		level.playSound(null, avatar.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3.0f, 1.4f);
		schedule(75, () -> {
			Mob a = avatar();
			if (a == null) {
				return;
			}
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, a.getX(), a.getY() + 2, a.getZ(), 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, a.getX(), a.getY() + 2, a.getZ(), 200, 4, 2, 4, 0.2);
			a.addEffect(new MobEffectInstance(MobEffects.GLOWING, MobEffectInstance.INFINITE_DURATION, 0, false, false));
			a.addEffect(new MobEffectInstance(MobEffects.SPEED, MobEffectInstance.INFINITE_DURATION, 1, false, false));
			a.addEffect(new MobEffectInstance(MobEffects.STRENGTH, MobEffectInstance.INFINITE_DURATION, 0, false, false));
			a.setNoAi(false);
			raiseTraitors();
			updateBarName("LUCIFER \u2014 The Morning Star");
			bar.setColor(BossEvent.BossBarColor.PURPLE);
			LuciferDialogue.nameCard(LuciferDialogue.audience(level, AUDIENCE_RADIUS), "ENOUGH!", "The Morning Star unbound", ChatFormatting.RED);
			attackCooldown = 30;
			setPhase(Phase.ENRAGED);
		});
	}

	private void beginTrueFormTransition() {
		if (phase == Phase.TRUE_FORM_TRANSITION || phase == Phase.TRUE_FORM || phase == Phase.DEFEAT) {
			return;
		}
		setPhase(Phase.TRUE_FORM_TRANSITION);
		scheduled.clear();
		Mob avatar = avatar();
		if (avatar != null) {
			avatar.setNoAi(true);
		}
		for (int i = 0; i < LuciferDialogue.TRUE_FORM.length; i++) {
			sayLater(i * 45, LuciferDialogue.TRUE_FORM[i]);
		}
		schedule(95, this::revealTrueForm);
	}

	/** The Morning Star shatters and the Emperor, frozen in the ice, is revealed. */
	private void revealTrueForm() {
		if (phase != Phase.TRUE_FORM_TRANSITION) {
			return;
		}
		{
			Mob a = avatar();
			if (a != null) {
				level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, a.getX(), a.getY() + 2, a.getZ(), 2, 1, 1, 1, 0);
				level.sendParticles(ParticleTypes.SNOWFLAKE, a.getX(), a.getY() + 2, a.getZ(), 500, 3, 3, 3, 0.5);
				level.playSound(null, a.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 3.0f, 0.4f);
				a.discard();
			}
			for (UUID id : traitors) {
				Entity t = level.getEntity(id);
				if (t != null) {
					t.discard();
				}
			}
			traitors.clear();
			WitherBoss wither = spawnTrueForm();
			witherId = wither.getUUID();
			emperor = new EmperorAttacks(this, witherId);
			bar.removeAllPlayers();
			lastTrueFormHealth = 1.0f;
			LuciferDialogue.nameCard(LuciferDialogue.audience(level, AUDIENCE_RADIUS), "LUCIFER", "Three-Faced Emperor", ChatFormatting.DARK_PURPLE);
			setPhase(Phase.TRUE_FORM);
		}
	}

	private void beginDefeat() {
		if (phase == Phase.DEFEAT || phase == Phase.DONE) {
			return;
		}
		setPhase(Phase.DEFEAT);
		scheduled.clear();
		bar.removeAllPlayers();
		for (int i = 0; i < LuciferDialogue.DEFEAT.length; i++) {
			sayLater(20 + i * 60, LuciferDialogue.DEFEAT[i]);
		}
		schedule(210, () -> {
			// each on its own: whatever goes wrong with one, the victors still get their spoils and their way out
			step("rewards", this::reward);
			step("the burrow", () -> Purgatory.openBurrow(level, floorY));
			step("the cleanup", this::cleanup);
			setPhase(Phase.DONE);
		});
	}

	// ------------------------------------------------------------------ hooks (from LuciferManager)

	boolean allowDamage(LivingEntity entity) {
		if (entity.getUUID().equals(avatarId)) {
			return phase == Phase.DUEL || phase == Phase.ENRAGED;
		}
		return true;
	}

	void afterDamage(LivingEntity entity, DamageSource source, float dealt) {
		if (emperor != null && entity.getUUID().equals(witherId)) {
			emperor.onHurt(dealt);
		}
		boolean boss = entity.getUUID().equals(avatarId) || entity.getUUID().equals(witherId);
		if (!boss || !(source.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		boolean newcomer = !participants.containsKey(player.getUUID());
		participants.merge(player.getUUID(), dealt, Float::sum);
		if (newcomer) {
			HellState.Soul soul = HellState.get(level.getServer()).existing(player.getUUID());
			if (soul != null && soul.slewLucifer && !veterans.contains(soul.name)) {
				veterans.add(soul.name);
				Mob avatar = avatar();
				if (avatar != null) {
					applyVeteranScaling(avatar);
				}
				WitherBoss wither = wither();
				if (wither != null) {
					applyVeteranScaling(wither);
				}
				say(LuciferDialogue.veteranLine(List.of(soul.name)));
			}
		}
		if (entity.getUUID().equals(avatarId) && dealt >= 7.0f && tick - lastHurtBark > 120 && level.getRandom().nextFloat() < 0.4f) {
			lastHurtBark = tick;
			say(LuciferDialogue.pick(level.getRandom(), LuciferDialogue.HURT));
		}
	}

	/** The avatar can't truly die: at zero health he reveals his true form instead. */
	boolean allowDeath(LivingEntity entity) {
		if (!entity.getUUID().equals(avatarId)) {
			return true;
		}
		entity.setHealth(1.0f);
		beginTrueFormTransition();
		return false;
	}

	private void step(String what, Runnable action) {
		try {
			action.run();
		} catch (RuntimeException e) {
			HellcraftMod.LOGGER.error("Lucifer's defeat: {} failed", what, e);
		}
	}

	void onDeath(LivingEntity entity) {
		if (entity.getUUID().equals(witherId)) {
			HellcraftMod.LOGGER.info("Lucifer's true form is slain");
			beginDefeat();
		} else if (entity instanceof ServerPlayer player && LuciferArena.inside(player.getX(), player.getZ())
				&& (phase == Phase.DUEL || phase == Phase.ENRAGED || phase == Phase.TRUE_FORM)) {
			sayLater(10, LuciferDialogue.pick(level.getRandom(), LuciferDialogue.PLAYER_DEATH));
		}
	}

	/** Test helper: jump to the next phase. */
	void skip() {
		switch (phase) {
			case INTRO -> {
				scheduled.clear();
				Mob avatar = avatar();
				if (avatar != null) {
					avatar.setNoAi(false);
				}
				setPhase(Phase.DUEL);
			}
			case DUEL -> beginEnrage();
			case ENRAGED -> beginTrueFormTransition();
			case TRUE_FORM -> {
				// killed the way players kill him, so the test covers the real death
				WitherBoss wither = wither();
				if (wither != null) {
					wither.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
				}
			}
			default -> {
			}
		}
	}

	/** Test helper: run one attack at the nearest target. */
	boolean forceAttack(LuciferAttacks.Attack attack) {
		Mob avatar = avatar();
		List<LivingEntity> targets = targets();
		if (avatar == null || targets.isEmpty()) {
			return false;
		}
		LuciferAttacks.perform(this, attack, avatar, targets.get(0));
		return true;
	}

	/** Test helper: run one of the Emperor's attacks now. */
	boolean forceEmperorAttack(EmperorAttacks.Attack attack) {
		WitherBoss wither = wither();
		return phase == Phase.TRUE_FORM && wither != null && emperor != null && emperor.perform(attack, wither);
	}

	void stop() {
		cleanup();
		setPhase(Phase.DONE);
	}

	// ------------------------------------------------------------------ helpers

	/** Players worth attacking; if none (tests), any other living thing in the pit. */
	List<LivingEntity> targets() {
		List<LivingEntity> players = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (p.isAlive() && !p.isSpectator() && !p.isCreative() && LuciferArena.inside(p.getX(), p.getZ())) {
				players.add(p);
			}
		}
		if (!players.isEmpty()) {
			return players;
		}
		return level.getEntitiesOfClass(LivingEntity.class,
				new net.minecraft.world.phys.AABB(-LuciferArena.RADIUS, floorY - 12, -LuciferArena.RADIUS, LuciferArena.RADIUS, floorY + 30, LuciferArena.RADIUS),
				e -> e.isAlive() && !LuciferManager.isLucifer(e) && !(e instanceof ServerPlayer));
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
				HellcraftMod.LOGGER.warn("Lucifer fight action failed", e);
			}
		}
	}

	private void updateBar() {
		if (phase == Phase.TRUE_FORM || phase == Phase.TRUE_FORM_TRANSITION || phase == Phase.DEFEAT || phase == Phase.FAILED) {
			return;
		}
		List<ServerPlayer> audience = LuciferDialogue.audience(level, AUDIENCE_RADIUS);
		for (ServerPlayer p : new ArrayList<>(bar.getPlayers())) {
			if (!audience.contains(p)) {
				bar.removePlayer(p);
			}
		}
		for (ServerPlayer p : audience) {
			bar.addPlayer(p);
		}
		Mob avatar = avatar();
		if (avatar != null) {
			bar.setProgress(phase == Phase.INTRO ? Math.min(1.0f, phaseTick / (float) INTRO_LENGTH)
					: Math.max(0.0f, avatar.getHealth() / avatar.getMaxHealth()));
		}
	}

	private void leash(Mob mob, double radius) {
		if (mob.getX() * mob.getX() + mob.getZ() * mob.getZ() > radius * radius || mob.getY() < floorY - 10) {
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, mob.getX(), mob.getY() + 1, mob.getZ(), 40, 0.5, 1, 0.5, 0.1);
			mob.teleportTo(0.5, floorY, 0.5);
		}
	}

	@Nullable
	private Mob avatar() {
		Entity e = avatarId == null ? null : level.getEntity(avatarId);
		return e instanceof Mob m && m.isAlive() ? m : null;
	}

	@Nullable
	WitherBoss wither() {
		Entity e = witherId == null ? null : level.getEntity(witherId);
		return e instanceof WitherBoss w && w.isAlive() ? w : null;
	}

	private void strikeLightning(double x, double z) {
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
		if (bolt != null) {
			bolt.snapTo(x, floorY, z);
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
		}
	}

	private static void setBase(LivingEntity entity, Holder<Attribute> attribute, double value) {
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	private WitherSkeleton spawnAvatar() {
		WitherSkeleton avatar = EntityTypes.WITHER_SKELETON.create(level, EntitySpawnReason.EVENT);
		if (avatar == null) {
			throw new IllegalStateException("could not create Lucifer");
		}
		avatar.snapTo(0.5, floorY, 0.5, 90.0f, 0.0f);
		avatar.addTag(LuciferManager.TAG);
		avatar.addTag(AVATAR_TAG);
		avatar.setCustomName(Component.literal("Lucifer").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
		avatar.setCustomNameVisible(true);
		avatar.setPersistenceRequired();
		avatar.setNoAi(true);
		setBase(avatar, Attributes.SCALE, 2.4);
		setBase(avatar, Attributes.MAX_HEALTH, HellConfig.get().luciferAvatarHealth);
		setBase(avatar, Attributes.ATTACK_DAMAGE, 12.0);
		setBase(avatar, Attributes.ARMOR, 12.0);
		setBase(avatar, Attributes.MOVEMENT_SPEED, 0.3);
		setBase(avatar, Attributes.KNOCKBACK_RESISTANCE, 1.0);
		setBase(avatar, Attributes.FOLLOW_RANGE, 64.0);
		avatar.setHealth(avatar.getMaxHealth());
		ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
		sword.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		avatar.setItemSlot(EquipmentSlot.MAINHAND, sword);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			avatar.setDropChance(slot, 0.0f);
		}
		level.addFreshEntity(avatar);
		model.attach(avatar, "lucifer_morning_star", Math.max(avatar.getBbHeight(), 5.6f));
		return avatar;
	}

	private WitherSkeleton spawnTraitor(String name, double x, double z) {
		WitherSkeleton traitor = EntityTypes.WITHER_SKELETON.create(level, EntitySpawnReason.EVENT);
		if (traitor == null) {
			throw new IllegalStateException("could not create " + name);
		}
		double y = LuciferAttacks.floorAt(level, x, floorY + 2, z);
		traitor.snapTo(x, y, z, 0.0f, 0.0f);
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
		level.sendParticles(ParticleTypes.SOUL, x, y + 1, z, 30, 0.5, 1, 0.5, 0.05);
		return traitor;
	}

	private WitherBoss spawnTrueForm() {
		WitherBoss wither = EntityTypes.WITHER.create(level, EntitySpawnReason.EVENT);
		if (wither == null) {
			throw new IllegalStateException("could not create Lucifer's true form");
		}
		// frozen to the chest in the ice at the very centre (EmperorAttacks keeps him there); no vanilla
		// spawn charge-up, whose blast used to crater the pit
		wither.snapTo(0.5, floorY, 0.5, 0.0f, 0.0f);
		wither.addTag(LuciferManager.TAG);
		wither.setPersistenceRequired();
		setBase(wither, Attributes.MAX_HEALTH, HellConfig.get().luciferHealth);
		wither.setHealth(wither.getMaxHealth());
		applyVeteranScaling(wither);
		level.playSound(null, wither.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 4.0f, 0.5f);
		wither.setCustomName(Component.literal("Lucifer, Three-Faced Emperor").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
		level.addFreshEntity(wither);
		model.attach(wither, "lucifer_emperor", Math.max(4.0f, wither.getBbHeight() * 1.2f));
		return wither;
	}

	/** Removes every trace of the fight except the rewards. */
	private void cleanup() {
		scheduled.clear();
		bar.removeAllPlayers();
		Mob avatar = avatar();
		if (avatar != null) {
			avatar.discard();
		}
		WitherBoss wither = wither();
		if (wither != null) {
			wither.discard();
		}
		for (UUID id : traitors) {
			Entity t = level.getEntity(id);
			if (t != null) {
				t.discard();
			}
		}
		traitors.clear();
		model.clear();
		discardStrays();
		if (floor != null) {
			LuciferArena.heal(level, floor);
		}
		LuciferArena.unseal(level);
		LuciferArena.forceLoad(level, false);
	}

	/** Removes every Lucifer-tagged entity in and around the pit. */
	private void discardStrays() {
		double r = LuciferArena.RADIUS + 24;
		for (Entity e : level.getEntitiesOfClass(Entity.class, new net.minecraft.world.phys.AABB(-r, level.getMinY(), -r, r, level.getMaxY() + 1, r),
				e -> LuciferManager.isLucifer(e) && e.isAlive())) {
			e.discard();
		}
	}

	/** True if this entity is one of the fight's own bodies (anything else tagged as Lucifer is a stray). */
	boolean owns(UUID id) {
		return id.equals(avatarId) || id.equals(witherId) || traitors.contains(id) || model.owns(id);
	}

	String status() {
		return "phase=" + phase + " tick=" + tick + " avatar=" + (avatar() != null ? "present" : avatarId == null ? "none" : "missing")
				+ " trueForm=" + (wither() != null ? "present" : witherId == null ? "none" : "missing")
				+ " traitors=" + traitors.size() + " floor=" + floorY + (emperor != null ? " emperorStage=" + emperor.stage() : "") + " participants=" + participants.size() + " champions=" + veterans;
	}

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
					&& present.getX() * present.getX() + present.getZ() * present.getZ() < 40 * 40;
			if (!standing) {
				if (present != null) {
					present.sendSystemMessage(Component.literal("You fell before the Emperor did. There are no spoils for the dead.")
							.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
				}
				continue;
			}
			victors.add(soul.name);
			LuciferRewards.grant(state, soul);
			Scoreboards.update(level.getServer(), soul);
			pending++;
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
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
		level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(who + " cast down Lucifer at the bottom of the world.")
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

	/** The Emperor spits out the three traitors he chews (the last third of the true form). */
	void releaseTraitors(WitherBoss boss) {
		String[] names = {"Judas", "Brutus", "Cassius"};
		for (int i = 0; i < names.length; i++) {
			double ang = i * 2 * Math.PI / names.length;
			WitherSkeleton traitor = spawnTraitor(names[i], boss.getX() + Math.cos(ang) * 4, boss.getZ() + Math.sin(ang) * 4);
			traitors.add(traitor.getUUID());
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
			traitors.add(spawnTraitor(all[i], Math.cos(ang) * 10, Math.sin(ang) * 10).getUUID());
		}
	}

}
