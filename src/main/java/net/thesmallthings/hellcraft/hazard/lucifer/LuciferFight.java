package net.thesmallthings.hellcraft.hazard.lucifer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.Holder;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.BloodItems;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.blood.Hearts;
import net.thesmallthings.hellcraft.config.HellConfig;
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

	private final ServerLevel level;
	/** Started by a command with nobody around: never fails for lack of players (used by tests). */
	private final boolean debug;
	private final int floorY;
	private final ServerBossEvent bar = new ServerBossEvent(Component.literal("LUCIFER").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
			BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
	private final Map<UUID, Float> participants = new LinkedHashMap<>();
	private final List<Scheduled> scheduled = new ArrayList<>();
	private final List<UUID> traitors = new ArrayList<>();

	private Phase phase = Phase.INTRO;
	private int tick;
	private int phaseTick;
	private int attackCooldown;
	private int emptyTicks;
	private int lastHurtBark = -1000;
	@Nullable
	private UUID avatarId;
	@Nullable
	private UUID witherId;

	private record Scheduled(int at, Runnable action) {
	}

	LuciferFight(ServerLevel level, boolean debug) {
		this.level = level;
		this.debug = debug;
		this.floorY = LuciferArena.floorY(level);
		bar.setDarkenScreen(true);
		bar.setCreateWorldFog(true);
		bar.setProgress(1.0f);
	}

	ServerLevel level() {
		return level;
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
		LuciferArena.seal(level);
		for (ServerPlayer p : LuciferDialogue.audience(level, LuciferArena.RADIUS + 4)) {
			if (!p.isSpectator()) {
				participants.putIfAbsent(p.getUUID(), 0.0f);
			}
		}
		WitherSkeleton avatar = spawnAvatar();
		avatarId = avatar.getUUID();
		for (ServerPlayer p : LuciferDialogue.audience(level, AUDIENCE_RADIUS)) {
			p.playNotifySound(SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 1.0f, 0.6f);
		}
		for (int i = 0; i < 4; i++) {
			double a = i * Math.PI / 2 + Math.PI / 4;
			strikeLightning(Math.cos(a) * 18, Math.sin(a) * 18);
		}
		for (int i = 0; i < LuciferDialogue.INTRO.length; i++) {
			sayLater(30 + i * 55, LuciferDialogue.INTRO[i]);
		}
		schedule(INTRO_LENGTH - 30, () -> LuciferDialogue.nameCard(LuciferDialogue.audience(level, AUDIENCE_RADIUS),
				"LUCIFER", "The Morning Star, Emperor of the Kingdom Dolorous", ChatFormatting.DARK_RED));
		setPhase(Phase.INTRO);
	}

	void tick() {
		tick++;
		phaseTick++;
		runScheduled();
		if (phase == Phase.DONE) {
			return;
		}
		updateBar();
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
			case TRUE_FORM -> {
				WitherBoss wither = wither();
				if (wither == null) {
					beginDefeat();
					return;
				}
				leash(wither, 26);
				checkForFailure();
			}
			default -> {
			}
		}
	}

	private void fightTick() {
		Mob avatar = avatar();
		if (avatar == null) {
			beginTrueFormTransition();
			return;
		}
		leash(avatar, 24);
		if (checkForFailure()) {
			return;
		}
		List<LivingEntity> targets = targets();
		if (!targets.isEmpty() && avatar.getTarget() == null) {
			avatar.setTarget(targets.get(level.random.nextInt(targets.size())));
		}
		if (phase == Phase.DUEL && avatar.getHealth() <= avatar.getMaxHealth() * 0.5f) {
			beginEnrage();
			return;
		}
		if (--attackCooldown <= 0 && !targets.isEmpty()) {
			LivingEntity target = targets.get(level.random.nextInt(targets.size()));
			LuciferAttacks.Attack[] all = LuciferAttacks.Attack.values();
			LuciferAttacks.perform(this, all[level.random.nextInt(all.length)], avatar, target);
			if (phase == Phase.ENRAGED && level.random.nextFloat() < 0.6f) {
				LuciferAttacks.Attack follow = all[level.random.nextInt(all.length)];
				schedule(30, () -> {
					Mob a = avatar();
					if (a != null && target.isAlive() && phase == Phase.ENRAGED) {
						LuciferAttacks.perform(this, follow, a, target);
					}
				});
			}
			attackCooldown = phase == Phase.ENRAGED ? 50 : 80;
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
			setPhase(Phase.FAILED);
			say(LuciferDialogue.FAIL);
			schedule(40, () -> {
				cleanup();
				HellState state = HellState.get(level.getServer());
				state.luciferNextSpawn = level.getGameTime() + HellConfig.get().luciferRetryMinutes * 1200L;
				state.setDirty();
				setPhase(Phase.DONE);
			});
			return true;
		}
		return false;
	}

	// ------------------------------------------------------------------ phase changes

	private void setPhase(Phase next) {
		phase = next;
		phaseTick = 0;
		HellcraftMod.LOGGER.info("Lucifer phase: {}", next);
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
			a.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, MobEffectInstance.INFINITE_DURATION, 1, false, false));
			a.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, MobEffectInstance.INFINITE_DURATION, 0, false, false));
			a.setNoAi(false);
			String[] names = {"Judas", "Brutus", "Cassius"};
			for (int i = 0; i < names.length; i++) {
				double ang = i * 2 * Math.PI / 3;
				traitors.add(spawnTraitor(names[i], Math.cos(ang) * 10, Math.sin(ang) * 10).getUUID());
			}
			bar.setName(Component.literal("LUCIFER — The Morning Star").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
			bar.setColor(BossEvent.BossBarColor.PURPLE);
			LuciferDialogue.nameCard(LuciferDialogue.audience(level, AUDIENCE_RADIUS), "THE MORNING STAR", "He is no longer holding back.", ChatFormatting.RED);
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
		schedule(95, () -> {
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
			bar.removeAllPlayers();
			LuciferDialogue.nameCard(LuciferDialogue.audience(level, AUDIENCE_RADIUS), "LUCIFER", "Three-Faced Emperor of the Kingdom Dolorous", ChatFormatting.DARK_PURPLE);
			setPhase(Phase.TRUE_FORM);
		});
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
			cleanup();
			reward();
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
		boolean boss = entity.getUUID().equals(avatarId) || entity.getUUID().equals(witherId);
		if (!boss || !(source.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		participants.merge(player.getUUID(), dealt, Float::sum);
		if (entity.getUUID().equals(avatarId) && dealt >= 7.0f && tick - lastHurtBark > 120 && level.random.nextFloat() < 0.4f) {
			lastHurtBark = tick;
			say(LuciferDialogue.pick(level.random, LuciferDialogue.HURT));
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

	void onDeath(LivingEntity entity) {
		if (entity.getUUID().equals(witherId)) {
			beginDefeat();
		} else if (entity instanceof ServerPlayer player && LuciferArena.inside(player.getX(), player.getZ())
				&& (phase == Phase.DUEL || phase == Phase.ENRAGED || phase == Phase.TRUE_FORM)) {
			sayLater(10, LuciferDialogue.pick(level.random, LuciferDialogue.PLAYER_DEATH));
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
				WitherBoss wither = wither();
				if (wither != null) {
					wither.discard();
				}
				beginDefeat();
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
			mob.teleportTo(0.5, floorY + (mob instanceof WitherBoss ? 6 : 0), 0.5);
		}
	}

	@Nullable
	private Mob avatar() {
		Entity e = avatarId == null ? null : level.getEntity(avatarId);
		return e instanceof Mob m && m.isAlive() ? m : null;
	}

	@Nullable
	private WitherBoss wither() {
		Entity e = witherId == null ? null : level.getEntity(witherId);
		return e instanceof WitherBoss w && w.isAlive() ? w : null;
	}

	private void strikeLightning(double x, double z) {
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
		if (bolt != null) {
			bolt.moveTo(x, floorY, z);
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
		WitherSkeleton avatar = EntityType.WITHER_SKELETON.create(level);
		if (avatar == null) {
			throw new IllegalStateException("could not create Lucifer");
		}
		avatar.moveTo(0.5, floorY, 0.5, 90.0f, 0.0f);
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
		return avatar;
	}

	private WitherSkeleton spawnTraitor(String name, double x, double z) {
		WitherSkeleton traitor = EntityType.WITHER_SKELETON.create(level);
		if (traitor == null) {
			throw new IllegalStateException("could not create " + name);
		}
		double y = LuciferAttacks.floorAt(level, x, floorY + 2, z);
		traitor.moveTo(x, y, z, 0.0f, 0.0f);
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
		WitherBoss wither = EntityType.WITHER.create(level);
		if (wither == null) {
			throw new IllegalStateException("could not create Lucifer's true form");
		}
		wither.moveTo(0.5, floorY + 4, 0.5, 0.0f, 0.0f);
		wither.addTag(LuciferManager.TAG);
		wither.setPersistenceRequired();
		setBase(wither, Attributes.MAX_HEALTH, HellConfig.get().luciferHealth);
		wither.makeInvulnerable();
		wither.setCustomName(Component.literal("Lucifer, Three-Faced Emperor").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
		level.addFreshEntity(wither);
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
		LuciferArena.unseal(level);
	}

	private void reward() {
		HellConfig config = HellConfig.get();
		HellState state = HellState.get(level.getServer());
		state.luciferDefeats++;
		state.luciferNextSpawn = level.getGameTime() + config.luciferCooldownMinutes * 1200L;
		state.setDirty();
		LuciferArena.placeReliquary(level, state.luciferDefeats == 1);

		List<String> victors = new ArrayList<>();
		for (UUID id : participants.keySet()) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
			HellState.Soul soul = state.existing(id);
			if (soul == null) {
				continue;
			}
			victors.add(soul.name);
			if (!soul.slewLucifer) {
				soul.slewLucifer = true;
				soul.maxBonus += config.luciferMaxHeartBonus;
				state.setDirty();
				if (player != null) {
					player.sendSystemMessage(Component.literal("Lucifer's Bane: your veins can now hold " + Hearts.cap(soul) + " hearts.")
							.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
				}
			}
			if (player != null) {
				BloodItems.give(player, BloodItems.heart(2));
				player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0f, 1.0f);
			}
		}
		String who = victors.isEmpty() ? "Someone" : String.join(", ", victors);
		level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(who + " cast down Lucifer at the bottom of the world.")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		level.getServer().getPlayerList().broadcastSystemMessage(Component.literal("\"Thence we came forth to rebehold the stars.\"")
				.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
	}
}
