package net.thesmallthings.hellcraft.combat;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.entity.boss.BossPart;
import net.thesmallthings.hellcraft.hazard.BossRules;
import net.thesmallthings.hellcraft.net.SwingPayload;
import net.thesmallthings.hellcraft.sound.HellSounds;
import net.thesmallthings.hellcraft.util.Feedback;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Swings, on the server (combat/Moveset says how each weapon swings; the client plays its own at once and tells us:
 * net/SwingPayload). A swing is accepted if the held weapon has that move and it may follow the last one, shown to
 * everyone watching, and its blow lands when the animation says: everything in the move's arc in front of the player is
 * struck, nearest first, each with vanilla's own attack (so enchantments, critical hits, the hell weapons' passives,
 * style and the fairness rules all apply) at the move's share of the weapon's damage.
 *
 * <p>What a swing strikes: whatever the player looks straight at, and besides it only what fights back (monsters and
 * other players) inside the arc, so a cut at a zombie never catches the villager or the dog beside it. Never through
 * walls.
 */
public final class Swing {
	private Swing() {
	}

	/**
	 * Ticks the attack key must stay down for a press to be the heavy move (client/combat/SwingControl waits that long, with
	 * nothing playing, before choosing; let go sooner and it is the next light move).
	 */
	public static final int CHARGE_AT = 3;
	/** At most this many ticks of a heavy's wind-up are left out when it begins (the ticks spent waiting to see it was held). */
	public static final int HEAVY_SKIP = 2;
	/**
	 * At most this many ticks of the network's one-way delay are made up: the blow lands when the swinger's own animation
	 * shows it, not that much later.
	 */
	private static final int MAX_CATCH_UP = 5;
	/** Ticks after a move ends in which the chain still carries on. */
	public static final int CHAIN_GRACE = 6;
	/** Others in the arc take this share of the first target's blow (all of it with Sweeping Edge III). */
	private static final float SWEEP_SHARE = 0.6f;
	private static final int MAX_TARGETS = 8;
	private static final Identifier MOVE_DAMAGE = HellcraftMod.id("combat_move");

	private static final class State {
		Moveset.Move move;
		ItemStack weapon = ItemStack.EMPTY;
		long start;
		float pace = 1;
		long hitAt;
		long end;
		boolean landed;
		/** Who this player's last blow struck: a combo's next blow lands on them though they'd still be flashing red. */
		Set<Integer> struck = new HashSet<>();
	}

	private static final Map<UUID, State> STATES = new HashMap<>();
	/** The player whose swing is landing right now (mixin/PlayerMixin: full strength, no vanilla sweep), and its move. */
	@Nullable
	private static Player landing;
	@Nullable
	private static Moveset.Move landingMove;

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(SwingPayload.TYPE, SwingPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SwingPayload.TYPE, (payload, context) -> request(context.player(), payload.move(), payload.skip()));
	}

	static void forget(ServerPlayer player) {
		STATES.remove(player.getUUID());
	}

	/** True while this player's swing is landing its blow. */
	public static boolean landing(Player player) {
		return landing == player;
	}

	/** True while a heavy move's blow is landing. */
	public static boolean landingHeavy() {
		return landingMove != null && landingMove.heavy();
	}

	/** Mid-swing: the move has begun and not ended. */
	public static boolean swinging(ServerPlayer player) {
		State s = STATES.get(player.getUUID());
		return s != null && s.move != null && player.level().getGameTime() < s.end;
	}

	/** What a braced wielder (a heavy weapon's committed recovery) takes of a blow. */
	private static final float BRACED = 0.75f;

	/**
	 * A heavy weapon's swing whose blow has landed commits its wielder until the move ends: no guard cuts its recovery short
	 * (one pressed now goes up when it ends; client/combat/SwingControl.committed agrees). Lighter weapons may guard at any
	 * moment.
	 */
	public static boolean committed(ServerPlayer player) {
		State s = STATES.get(player.getUUID());
		if (s == null || s.move == null || !s.landed || player.level().getGameTime() >= s.end) {
			return false;
		}
		WeaponClass kind = WeaponClass.of(s.weapon);
		return kind != null && kind.heavy;
	}

	/**
	 * A blow reaches someone (mixin/LivingEntityMixin): a heavy weapon's wielder, committed to the recovery of a blow that
	 * landed, is braced and takes a quarter less (what they gave up by not being able to guard out of it).
	 */
	public static float brace(LivingEntity victim, float amount) {
		return victim instanceof ServerPlayer player && Combat.on() && committed(player) ? amount * BRACED : amount;
	}

	/** A roll or a raised guard cuts a swing short: a blow not yet landed never lands. */
	public static void cancel(ServerPlayer player) {
		State s = STATES.get(player.getUUID());
		if (s != null && s.move != null && !s.landed) {
			s.landed = true;
			s.end = player.level().getGameTime();
		}
	}

	private static void request(ServerPlayer player, int ordinal, int skip) {
		if (!Combat.on() || !player.isAlive() || player.isSpectator() || player.isUsingItem() || Dodge.rolling(player)) {
			return;
		}
		ItemStack weapon = player.getMainHandItem();
		Moveset set = Moveset.of(weapon);
		Moveset.Move move = set == null ? null : set.find(ordinal);
		if (move == null) {
			return;
		}
		long now = player.level().getGameTime();
		State s = STATES.computeIfAbsent(player.getUUID(), id -> new State());
		if (!mayBegin(s, set, move, now)) {
			return;
		}
		if (move.heavy() && !Stamina.spend(player, HellConfig.get().heavyCost)) {
			return;
		}
		s.move = move;
		s.weapon = weapon;
		s.pace = set.pace(player);
		// the client began this move half a round trip ago (and maybe a little way in): count from then, so the blow lands
		// when the swinger sees it land, not a ping later
		float head = move.heavy() ? Mth.clamp(skip, 0, HEAVY_SKIP) : 0;
		int behind = catchUp(player);
		s.start = now - behind - Math.round(head / s.pace);
		s.hitAt = Math.max(now, s.start + Math.max(1, Math.round(move.hitTick() / s.pace)));
		s.end = Math.max(now + 1, s.start + Mth.ceil(move.ticks() / s.pace));
		s.landed = false;
		Combat.show(player, move.anim(), 0, 0, s.pace, false);
	}

	/** How many ticks behind the player's client the server hears of a swing: half the measured round trip, capped. */
	private static int catchUp(ServerPlayer player) {
		return Mth.clamp(Math.round(player.connection.latency() / 100.0f), 0, MAX_CATCH_UP);
	}

	/** Whether `move` may begin now, given what the player last did. A little slack for the network's sake. */
	private static boolean mayBegin(State s, Moveset set, Moveset.Move move, long now) {
		if (s.move == null) {
			return move.heavy() || move.index() == 0;
		}
		if (now < s.end) {
			// mid-move: the held key turns a light move not yet landed into the heavy; otherwise only the next in the chain,
			// and only once this one frees it
			float into = (now - s.start) * s.pace;
			if (move.heavy()) {
				return !s.move.heavy() && (!s.landed || into + 2 >= s.move.nextTick());
			}
			return into + 2 >= s.move.nextTick() && (move == set.after(s.move) || move.index() == 0);
		}
		if (move.heavy() || move.index() == 0) {
			return true;
		}
		return now - s.end <= CHAIN_GRACE + 2 && move == set.after(s.move);
	}

	static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			State s = STATES.get(player.getUUID());
			if (s == null || s.move == null) {
				continue;
			}
			long now = player.level().getGameTime();
			if (!s.landed && now >= s.hitAt) {
				s.landed = true;
				if (player.isAlive() && !player.isSpectator() && ItemStack.isSameItem(player.getMainHandItem(), s.weapon) && !player.isUsingItem()) {
					land(player, s);
				}
			}
			if (now > s.end + CHAIN_GRACE + 4) {
				s.move = null;
				s.struck.clear();
			}
		}
	}

	/** One thing a swing may strike: how far its nearest point is from the player's eyes. */
	private record Target(Entity entity, double distance, boolean aimed) {
	}

	private static void land(ServerPlayer player, State s) {
		Moveset.Move move = s.move;
		ServerLevel level = player.level();
		List<Target> targets = targets(player, move);
		Set<Integer> struck = new HashSet<>();
		boolean riposte = Guard.riposting(player) && !targets.isEmpty();
		AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
		double sweeping = player.getAttributeValue(Attributes.SWEEPING_DAMAGE_RATIO);
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		for (int i = 0; i < targets.size(); i++) {
			Entity target = targets.get(i).entity();
			float share = move.damage() * (i == 0 ? 1.0f : (float) (SWEEP_SHARE + (1 - SWEEP_SHARE) * Math.min(1.0, sweeping)));
			if (riposte) {
				share *= 1.5f;
			}
			if (target instanceof LivingEntity living && s.struck.contains(target.getId())) {
				// the chain's last blow left them flashing: this one lands all the same
				living.setInvulnerableTime(0);
			}
			if (damage != null) {
				damage.removeModifier(MOVE_DAMAGE);
				damage.addTransientModifier(new AttributeModifier(MOVE_DAMAGE, share - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
			landing = player;
			landingMove = move;
			try {
				player.attack(target);
			} finally {
				landing = null;
				landingMove = null;
				if (damage != null) {
					damage.removeModifier(MOVE_DAMAGE);
				}
			}
			if (move.knockback() > 0 && target instanceof LivingEntity living && living.isAlive()) {
				living.knockback(move.knockback(), Mth.sin(yaw), -Mth.cos(yaw), level.damageSources().playerAttack(player), 0.0f);
			}
			if (move.pull() > 0 && target instanceof LivingEntity living && living.isAlive() && !BossRules.fromBoss(living)) {
				// dragged in towards the reaper
				Vec3 to = player.position().subtract(living.position());
				if (to.horizontalDistance() > 1.2) {
					Vec3 in = new Vec3(to.x, 0, to.z).normalize().scale(move.pull());
					living.push(in.x, 0.18, in.z);
					Feedback.syncMotion(living);
				}
			}
			struck.add(target.getId());
		}
		s.struck = struck;
		if (move.shock() > 0 && player.onGround()) {
			shockwave(player, move, struck);
		}
		if (riposte) {
			Guard.spendRiposte(player);
		}
		if (targets.isEmpty()) {
			// a swing at nothing: others hear it pass (the swinger's client plays its own)
			level.playSound(player, player.getX(), player.getY(), player.getZ(), (move.heavy() ? HellSounds.COMBAT_SWING_HEAVY : HellSounds.COMBAT_SWING_LIGHT).value(), SoundSource.PLAYERS, 0.5f,
					move.heavy() ? 0.95f : 1.0f);
			player.resetAttackStrengthTicker();
		} else if (move.heavy()) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), HellSounds.COMBAT_SWING_HEAVY.value(), SoundSource.PLAYERS, 0.9f, 0.95f);
		}
	}

	/**
	 * The slam shakes the ground where it lands, two blocks ahead: what fights back within the ring and wasn't struck by
	 * the blade itself takes 40% of the weapon's damage and is thrown up.
	 */
	private static void shockwave(ServerPlayer player, Moveset.Move move, Set<Integer> struck) {
		ServerLevel level = player.level();
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		Vec3 at = player.position().add(-Mth.sin(yaw) * 2.0, 0, Mth.cos(yaw) * 2.0);
		float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.4f;
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(move.shock(), 1.5, move.shock()),
				e -> e != player && e.isAlive() && fightsBack(e, player) && e.position().distanceTo(at) <= move.shock())) {
			if (!struck.contains(living.getId())) {
				living.hurtServer(level, level.damageSources().playerAttack(player), damage);
			}
			if (!BossRules.fromBoss(living)) {
				living.push(0, 0.42, 0);
				Feedback.syncMotion(living);
			}
		}
		BlockState ground = level.getBlockState(BlockPos.containing(at.x, player.getY() - 0.2, at.z));
		if (!ground.isAir()) {
			for (int i = 0; i < 16; i++) {
				double a = Math.PI * 2 * i / 16;
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x + Math.cos(a) * move.shock() * 0.7, player.getY() + 0.15,
						at.z + Math.sin(a) * move.shock() * 0.7, 4, 0.2, 0.1, 0.2, 0.12);
			}
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, player.getY() + 0.2, at.z, 30, 0.6, 0.2, 0.6, 0.2);
		}
		level.sendParticles(ParticleTypes.POOF, at.x, player.getY() + 0.2, at.z, 12, 1.0, 0.1, 1.0, 0.03);
		level.playSound(null, at.x, at.y, at.z, HellSounds.COMBAT_SHOCKWAVE.value(), SoundSource.PLAYERS, 0.5f, 1.12f);
	}

	/** The move landing right now, or null (blood/HellWeapons asks, for the Bloodletter's finisher). */
	@Nullable
	public static Moveset.Move landingMove() {
		return landingMove;
	}

	/** Everything this move's blow strikes, nearest first, what the player looks straight at before the rest. */
	private static List<Target> targets(ServerPlayer player, Moveset.Move move) {
		ServerLevel level = player.level();
		double reach = player.entityInteractionRange() + move.reach();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		// a boss's part (BossPart: Cerberus's heads, Geryon's tail) is the boss: its box is where the blow lands, the boss what it hurts
		Entity aimed = BossPart.body(aimed(player, eye, look, reach));
		List<Target> found = new ArrayList<>();
		AABB box = player.getBoundingBox().inflate(reach + 1.5);
		for (Entity entity : level.getEntities(player, box, e -> e.isAlive() && e.isAttackable() && !e.isSpectator())) {
			Entity body = BossPart.body(entity);
			if (body == null || body == player || body == player.getVehicle() || body.isPassengerOfSameVehicle(player)) {
				continue;
			}
			boolean isAimed = body == aimed;
			if (!isAimed && !fightsBack(body, player)) {
				continue;
			}
			Vec3 nearest = nearest(entity.getBoundingBox(), eye);
			double distance = nearest.distanceTo(eye);
			if (distance > reach) {
				continue;
			}
			if (!isAimed && !inArc(player.getYRot(), player.getXRot(), eye, entity.getBoundingBox(), move.halfAngle(), move.pitch())) {
				continue;
			}
			Vec3 mid = entity.getBoundingBox().getCenter();
			if (level.clip(new ClipContext(eye, mid, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS
					&& level.clip(new ClipContext(eye, nearest, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS) {
				continue;
			}
			found.add(new Target(body, distance, isAimed));
		}
		found.sort(Comparator.comparing((Target t) -> !t.aimed()).thenComparingDouble(Target::distance));
		// a boss struck through its main box and a part, or two parts, is struck once (at its nearest)
		Set<Integer> seen = new HashSet<>();
		found.removeIf(t -> !seen.add(t.entity().getId()));
		return found.size() > MAX_TARGETS ? new ArrayList<>(found.subList(0, MAX_TARGETS)) : found;
	}

	/** What the player looks straight at within reach: the first thing their line of sight passes through. */
	@Nullable
	private static Entity aimed(ServerPlayer player, Vec3 eye, Vec3 look, double reach) {
		Vec3 end = eye.add(look.scale(reach));
		Entity best = null;
		double bestAt = Double.MAX_VALUE;
		for (Entity entity : player.level().getEntities(player, new AABB(eye, end).inflate(1.0), e -> e.isAlive() && e.isAttackable() && !e.isSpectator())) {
			Optional<Vec3> at = entity.getBoundingBox().inflate(entity.getPickRadius() + 0.1).clip(eye, end);
			if (entity.getBoundingBox().contains(eye)) {
				return entity;
			}
			if (at.isPresent() && at.get().distanceToSqr(eye) < bestAt) {
				bestAt = at.get().distanceToSqr(eye);
				best = entity;
			}
		}
		return best;
	}

	/** Monsters and other players: what an arc strikes without being aimed at. Not the player's own animals. */
	static boolean fightsBack(Entity entity, Player player) {
		if (entity instanceof OwnableEntity owned && owned.getOwner() == player) {
			return false;
		}
		return entity instanceof Enemy || entity instanceof Player;
	}

	private static Vec3 nearest(AABB box, Vec3 p) {
		return new Vec3(Mth.clamp(p.x, box.minX, box.maxX), Mth.clamp(p.y, box.minY, box.maxY), Mth.clamp(p.z, box.minZ, box.maxZ));
	}

	/**
	 * Whether a box lies in the arc in front of someone looking along yaw and pitch: some part of it within halfAngle to
	 * either side, and within `pitch` degrees above or below the line of sight (a box that straddles it counts).
	 */
	static boolean inArc(float yaw, float lookPitch, Vec3 eye, AABB box, float halfAngle, float pitch) {
		Vec3 nearest = nearest(box, eye);
		double dx = nearest.x - eye.x;
		double dz = nearest.z - eye.z;
		double flat = Math.sqrt(dx * dx + dz * dz);
		if (flat > 0.35) {
			// the box's bearing: its nearest point and its middle, whichever is closer to straight ahead
			float toNearest = Mth.wrapDegrees((float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG) - yaw);
			Vec3 mid = box.getCenter();
			float toMid = Mth.wrapDegrees((float) (Mth.atan2(-(mid.x - eye.x), mid.z - eye.z) * Mth.RAD_TO_DEG) - yaw);
			if (Math.min(Math.abs(toNearest), Math.abs(toMid)) > halfAngle) {
				return false;
			}
		}
		double reachFlat = Math.max(0.35, flat);
		// Minecraft's pitch grows downward
		double toTop = -Math.toDegrees(Math.atan2(box.maxY - eye.y, reachFlat));
		double toBottom = -Math.toDegrees(Math.atan2(box.minY - eye.y, reachFlat));
		return toTop <= lookPitch + pitch && toBottom >= lookPitch - pitch;
	}

	static String selfTest() {
		Vec3 eye = new Vec3(0, 1.6, 0);
		AABB ahead = new AABB(-0.3, 0, 1.7, 0.3, 1.8, 2.3);
		AABB behind = new AABB(-0.3, 0, -2.3, 0.3, 1.8, -1.7);
		AABB side = new AABB(1.7, 0, 0.7, 2.3, 1.8, 1.3);
		AABB above = new AABB(-0.3, 6, 1.7, 0.3, 7.8, 2.3);
		// looking south (yaw 0), level
		if (!inArc(0, 0, eye, ahead, 60, 45) || inArc(0, 0, eye, behind, 60, 45)) {
			return "a swing's arc faces the wrong way";
		}
		if (!inArc(0, 0, eye, side, 70, 45) || inArc(0, 0, eye, side, 24, 45)) {
			return "a swing's arc is the wrong width";
		}
		if (inArc(0, 0, eye, above, 60, 45) || !inArc(0, -70, eye, above, 60, 45)) {
			return "a swing's arc is the wrong height";
		}
		return Moveset.selfTest();
	}
}
