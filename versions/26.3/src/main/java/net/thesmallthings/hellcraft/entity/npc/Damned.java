package net.thesmallthings.hellcraft.entity.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.BloodAltar;
import net.thesmallthings.hellcraft.entity.boss.gen.DamnedAnim;
import net.thesmallthings.hellcraft.hazard.LowerRules;
import net.thesmallthings.hellcraft.hazard.Torments;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.Tombs;
import net.thesmallthings.hellcraft.world.Zone;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * One of the damned: a soul suffering its circle's punishment, after Dante (hazard/Torments brings them out near the
 * souls passing through). It is scenery that suffers: it can't be hurt or pushed (only /kill moves it), pays you no
 * mind, drops nothing, isn't saved with the world and goes when nobody is near. The server says which punishment it
 * suffers and which looping animation that is (Punishment.base), and plays the scream, the sigh or a flinch over it;
 * now and then it screams (Torments keeps that to one cry every few seconds near anyone). Its body is
 * tools/art/damned.py; the client draws it pale and see-through (client/render/npc/DamnedRenderer), with the weight,
 * the banner or the gilded cloak its punishment calls for.
 */
public class Damned extends PathfinderMob {
	public static final SoundEvent SCREAM = sound("entity.damned.scream");
	public static final SoundEvent SIGH = sound("entity.damned.sigh");

	private static final EntityDataAccessor<Byte> DATA_PUNISHMENT = SynchedEntityData.defineId(Damned.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Byte> DATA_LOOK = SynchedEntityData.defineId(Damned.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Byte> DATA_BASE = SynchedEntityData.defineId(Damned.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Integer> DATA_ACT = SynchedEntityData.defineId(Damned.class, EntityDataSerializers.INT);
	/** DATA_LOOK's bit for the Vestibule's leader, who carries the banner (the low bits are the tint). */
	private static final int LEADER = 4;
	/** It goes once nobody is this near (blocks). */
	private static final double DESPAWN = 72;

	/** Each circle's punishment: the animation it loops, whether it walks on its own legs, and whether it burns. */
	public enum Punishment {
		/** The neutrals, running after a banner forever, stung by wasps (Inferno III). */
		VESTIBULE(DamnedAnim.RUN, true, false),
		/** Limbo: no torment, only longing; they sigh (Inferno IV). */
		LIMBO(DamnedAnim.IDLE, true, false),
		/** Lust: tossed about the sky by the storm (Inferno V). */
		LUST(DamnedAnim.TUMBLE, false, false),
		/** Gluttony: lying in the mud under the filthy rain (Inferno VI). */
		GLUTTONY(DamnedAnim.WALLOW, false, false),
		/** Greed: rolling great weights at each other, clashing, turning back (Inferno VII). */
		GREED(DamnedAnim.PUSH, true, false),
		/** Wrath: fighting each other in the shallows of the Styx (Inferno VII-VIII). */
		WRATH(DamnedAnim.BRAWL, false, false),
		/** Heresy: in the burning tombs (Inferno IX-X). */
		HERESY(DamnedAnim.WRITHE, false, true),
		/** Violence against others: sunk in the boiling river of blood (Inferno XII). */
		PHLEGETHON(DamnedAnim.BOB, false, false),
		/** Violence against God: under the rain of fire on the Burning Sands (Inferno XIV). */
		SANDS(DamnedAnim.BURN, true, true),
		/** Fraud: the hypocrites' slow procession in gilded cloaks of lead (Inferno XXIII). */
		MALEBOLGE(DamnedAnim.MARCH, true, false),
		/** Treachery: frozen in the ice to the neck (Inferno XXXII). */
		COCYTUS(DamnedAnim.SHIVER, false, false);

		public final DamnedAnim base;
		public final boolean walks;
		public final boolean burns;

		Punishment(DamnedAnim base, boolean walks, boolean burns) {
			this.base = base;
			this.walks = walks;
			this.burns = burns;
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		@Nullable
		public static Punishment byId(String id) {
			for (Punishment p : values()) {
				if (p.id().equals(id)) {
					return p;
				}
			}
			return null;
		}
	}

	/** One state per animation (client side), in DamnedAnim's order. */
	public final AnimationState[] states = new AnimationState[DamnedAnim.values().length];
	private int actStarted;
	private int act = -1;
	private int sequence;

	// the server's side: where it suffers and with whom (none of it is saved: the damned aren't)
	private Vec3 home = Vec3.ZERO;
	private Vec3 meet = Vec3.ZERO;
	/** Fixed in place (in the mud, the ice, the blood, a tomb, the sky) rather than walking. */
	private boolean pinned;
	@Nullable
	private Damned partner;
	@Nullable
	private Damned leader;
	/** Lust's orbit, the Wrath pair's circling, the Sands' runner's lap: radius, speed, phase. */
	private double radius;
	private double speed;
	private double phase;
	private double rise;
	/** Which way round a line walks, or which side of a pair it is on. */
	private int side = 1;
	private boolean towardMeet = true;
	private int turnedAt;
	private int nextCry;
	private boolean gusting;
	private boolean wasBlasting;

	public Damned(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
		for (int i = 0; i < states.length; i++) {
			states[i] = new AnimationState();
		}
		setPathfindingMalus(PathType.WATER, -1.0f);
		setPathfindingMalus(PathType.LAVA, -1.0f);
		setPathfindingMalus(PathType.FIRE, 0.0f);
		setPathfindingMalus(PathType.FIRE_IN_NEIGHBOR, 0.0f);
		nextCry = 200 + level.getRandom().nextInt(600);
	}

	public static AttributeSupplier.Builder attributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.28)
				.add(Attributes.FOLLOW_RANGE, 48.0)
				.add(Attributes.STEP_HEIGHT, 1.1);
	}

	private static SoundEvent sound(String name) {
		Identifier id = HellcraftMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	/** Registers the screams (called while the mod starts, so the sound events exist before the registries freeze). */
	public static void registerSounds() {
		// the static fields do it
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_PUNISHMENT, (byte) 0);
		builder.define(DATA_LOOK, (byte) 0);
		builder.define(DATA_BASE, (byte) DamnedAnim.IDLE.ordinal());
		builder.define(DATA_ACT, -1);
	}

	public Punishment punishment() {
		Punishment[] all = Punishment.values();
		return all[Math.floorMod(entityData.get(DATA_PUNISHMENT), all.length)];
	}

	/** Which of the four pale tints the client draws it with. */
	public int tint() {
		return entityData.get(DATA_LOOK) & 3;
	}

	/** The Vestibule's leader, who runs with the banner. */
	public boolean leader() {
		return (entityData.get(DATA_LOOK) & LEADER) != 0;
	}

	public boolean isPinned() {
		return pinned;
	}

	/** The animation it loops now. */
	public DamnedAnim base() {
		return DamnedAnim.values()[Math.floorMod(entityData.get(DATA_BASE), DamnedAnim.values().length)];
	}

	// ------------------------------------------------------------------ setting it to its punishment (Torments)

	/**
	 * Sets it to suffer this punishment here: at home (its feet), pinned in place or on its own legs. Lust's souls take
	 * home as the middle of the circle they are blown round (see {@link #blown}).
	 */
	public Damned punish(Punishment punishment, Vec3 home, boolean pinned) {
		entityData.set(DATA_PUNISHMENT, (byte) punishment.ordinal());
		entityData.set(DATA_LOOK, (byte) (getRandom().nextInt(4)));
		this.home = home;
		this.meet = home;
		this.pinned = pinned;
		setNoGravity(pinned);
		noPhysics = pinned;
		setBase(punishment.base);
		snapTo(home.x, home.y, home.z, getRandom().nextFloat() * 360, 0);
		setYHeadRot(getYRot());
		yBodyRot = getYRot();
		return this;
	}

	/** Lust: blown round a circle of this radius about home, rising and falling by rise. */
	public Damned blown(double radius, double speed, double rise) {
		this.radius = radius;
		this.speed = speed;
		this.rise = rise;
		this.phase = getRandom().nextDouble() * Math.PI * 2;
		Vec3 at = orbit();
		snapTo(at.x, at.y, at.z, getYRot(), 0);
		return this;
	}

	/** Greed and Wrath: the soul it pushes against or fights, and where they meet (Wrath: which side it stands on). */
	public Damned pair(Damned partner, Vec3 meet, int side, double phase) {
		this.partner = partner;
		this.meet = meet;
		this.side = side;
		this.phase = phase;
		return this;
	}

	/** The Vestibule's and Malebolge's lines: whom it follows (null: it leads, going round the circle this way). */
	public Damned follow(@Nullable Damned leader, int way, boolean banner) {
		this.leader = leader;
		this.side = way;
		if (banner) {
			entityData.set(DATA_LOOK, (byte) (entityData.get(DATA_LOOK) | LEADER));
			setBase(DamnedAnim.LEAD);
		}
		return this;
	}

	/** The Burning Sands: a soul that runs round and round its spot (radius > 0) or lies on its back in the fire. */
	public Damned lap(double radius, boolean lying) {
		this.radius = radius;
		this.phase = getRandom().nextDouble() * Math.PI * 2;
		if (lying) {
			setBase(DamnedAnim.WALLOW);
		}
		return this;
	}

	public Vec3 home() {
		return home;
	}

	// ------------------------------------------------------------------ animations

	private void setBase(DamnedAnim anim) {
		entityData.set(DATA_BASE, (byte) anim.ordinal());
	}

	/** Plays a one-off animation (a scream, a sigh, a flinch) over its punishment, on every client watching. */
	public void play(DamnedAnim anim) {
		entityData.set(DATA_ACT, (++sequence << 8) | anim.ordinal());
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (DATA_ACT.equals(accessor) && level().isClientSide()) {
			int value = entityData.get(DATA_ACT);
			if (value >= 0) {
				if (act >= 0) {
					states[act].stop();
				}
				act = Math.floorMod(value & 0xFF, states.length);
				states[act].start(tickCount);
				actStarted = tickCount;
			}
		}
	}

	private void animateClient() {
		int base = base().ordinal();
		DamnedAnim[] all = DamnedAnim.values();
		for (int i = 0; i < states.length; i++) {
			if (i == act) {
				continue;
			}
			if (i == base) {
				if (!states[i].isStarted()) {
					// out of step with its neighbours, so a line or a crowd doesn't move as one
					states[i].start(tickCount - Math.floorMod(getId() * 7, Math.max(1, all[i].ticks)));
				}
			} else {
				states[i].stop();
			}
		}
		if (act >= 0 && tickCount - actStarted > all[act].ticks) {
			states[act].stop();
			act = -1;
		}
	}

	// ------------------------------------------------------------------ ticking

	@Override
	public void tick() {
		if (pinned) {
			noPhysics = true;
			setNoGravity(true);
			setDeltaMovement(Vec3.ZERO);
		}
		super.tick();
		if (level().isClientSide()) {
			animateClient();
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (tickCount % 40 == 0 && !anyoneNear(level)) {
			discard();
			return;
		}
		switch (punishment()) {
			case VESTIBULE -> line(level, 1.0, Zone.VESTIBULE);
			case LIMBO -> linger(level);
			case LUST -> storm(level);
			case GLUTTONY -> wallow(level);
			case GREED -> roll(level);
			case WRATH -> brawl(level);
			case HERESY -> tomb(level);
			case PHLEGETHON -> blood(level);
			case SANDS -> sands(level);
			case MALEBOLGE -> line(level, 0.45, Zone.MALEBOLGE_BLIGHT);
			case COCYTUS -> ice(level);
		}
		cry(level);
	}

	private boolean anyoneNear(ServerLevel level) {
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(this) < DESPAWN * DESPAWN) {
				return true;
			}
		}
		return false;
	}

	/** Holds it where it suffers, facing this way. */
	private void hold(Vec3 at, float yaw) {
		setPos(at.x, at.y, at.z);
		setYRot(yaw);
		yBodyRot = yaw;
		setYHeadRot(yaw);
	}

	/** The Vestibule and Malebolge: a line going round the circle after its leader, who never stops. */
	private void line(ServerLevel level, double pace, Zone zone) {
		if (leader != null && leader.isAlive()) {
			if (tickCount % 10 == 0) {
				// keep a little behind and to one side of the one ahead
				Vec3 l = leader.position();
				double a = Math.atan2(l.z, l.x) - leader.side * 1.6 / Math.max(50, Math.hypot(l.x, l.z));
				double r = Math.hypot(l.x, l.z) + ((getId() & 1) == 0 ? 0.7 : -0.7);
				getNavigation().moveTo(Math.cos(a) * r, l.y, Math.sin(a) * r, pace * (distanceTo(leader) > 4 ? 1.35 : 1.05));
			}
		} else if (tickCount % 20 == 0 || getNavigation().isDone()) {
			leader = null;
			double r = Math.max(50, Math.hypot(home.x, home.z));
			double a = Math.atan2(getZ(), getX()) + side * 9.0 / r;
			double x = Math.cos(a) * r;
			double z = Math.sin(a) * r;
			if (InfernoGeometry.zoneAt(x, z) != zone || tickCount - turnedAt > 2400) {
				side = -side;
				turnedAt = tickCount;
				a = Math.atan2(getZ(), getX()) + side * 9.0 / r;
				x = Math.cos(a) * r;
				z = Math.sin(a) * r;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
			getNavigation().moveTo(x, y, z, pace);
		}
		if (punishment() == Punishment.VESTIBULE && getRandom().nextInt(70) == 0) {
			// stung: a jolt, a slap at the neck
			play(DamnedAnim.FLINCH);
			level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 1.7, getZ(), 3, 0.3, 0.2, 0.3, 0.05);
			if (getRandom().nextInt(3) == 0) {
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEE_STING, SoundSource.AMBIENT, 0.4f, 1.2f);
			}
		}
	}

	/** Limbo: drifting a little about its spot, sighing now and then. */
	private void linger(ServerLevel level) {
		if (tickCount % 200 == 0 && getRandom().nextInt(3) == 0) {
			double x = home.x + getRandom().nextGaussian() * 3;
			double z = home.z + getRandom().nextGaussian() * 3;
			getNavigation().moveTo(x, home.y, z, 0.45);
		}
	}

	private Vec3 orbit() {
		double a = phase;
		double r = radius * (0.85 + 0.15 * Math.sin(a * 1.7));
		return new Vec3(home.x + Math.cos(a) * r, home.y + Math.sin(a * 0.6 + radius) * rise, home.z + Math.sin(a) * r);
	}

	/** Lust: blown round and round, faster in the gusts, crying out as each gust takes it. */
	private void storm(ServerLevel level) {
		double gust = 1.0 + 0.8 * Math.sin(tickCount * 0.011 + radius);
		phase += speed * gust;
		Vec3 at = orbit();
		Vec3 ahead = new Vec3(Math.cos(phase + 0.1), 0, Math.sin(phase + 0.1)).subtract(Math.cos(phase), 0, Math.sin(phase));
		hold(at, (float) Math.toDegrees(Math.atan2(-ahead.x, ahead.z)));
		boolean peak = gust > 1.6;
		if (peak && !gusting) {
			cryNow();
		}
		gusting = peak;
		if (tickCount % 15 == 0) {
			level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1, getZ(), 1, 0.4, 0.4, 0.4, 0.02);
		}
	}

	/** Gluttony: wallowing where it lies, the mud spattering. */
	private void wallow(ServerLevel level) {
		hold(home, getYRot() + (float) Math.sin(tickCount * 0.02 + getId()) * 0.3f);
		if (tickCount % 20 == getId() % 20) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MUD.defaultBlockState()), getX(), getY() + 0.3, getZ(), 5,
					0.6, 0.1, 0.6, 0.1);
		}
	}

	/** Greed: rolling the weight to the meeting point, clashing with the other, turning back, and again, forever. */
	private void roll(ServerLevel level) {
		Vec3 to = towardMeet ? meet : home;
		getMoveControl().setWantedPosition(to.x, to.y, to.z, 0.5);
		double left = Math.hypot(getX() - to.x, getZ() - to.z);
		boolean stuck = tickCount - turnedAt > 600;
		if (towardMeet && partner != null && partner.isAlive() && distanceTo(partner) < 3.0) {
			clash(level);
			partner.clash(level);
		} else if (left < (towardMeet && partner != null ? 0.3 : 0.8) || stuck) {
			towardMeet = !towardMeet;
			turnedAt = tickCount;
		}
		if (tickCount % 12 == 0 && getDeltaMovement().horizontalDistanceSqr() > 1e-4) {
			BlockPos below = blockPosition().below();
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(below)), getX(), getY() + 0.1, getZ(), 3,
					0.4, 0.05, 0.4, 0.05);
		}
	}

	/** "Why hoardest thou?" "Why squanderest thou?": the weights crash together and both turn back. */
	private void clash(ServerLevel level) {
		if (!towardMeet) {
			return;
		}
		towardMeet = false;
		turnedAt = tickCount;
		play(DamnedAnim.FLINCH);
		Vec3 front = position().add(Vec3.directionFromRotation(0, getYRot()).scale(1.3));
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.RAW_GOLD_BLOCK.defaultBlockState()), front.x, front.y + 0.6, front.z, 10,
				0.3, 0.3, 0.3, 0.1);
		level.sendParticles(ParticleTypes.CRIT, front.x, front.y + 0.6, front.z, 6, 0.3, 0.3, 0.3, 0.2);
		if (side > 0) {
			level.playSound(null, front.x, front.y, front.z, SoundEvents.ANVIL_LAND, SoundSource.AMBIENT, 0.5f, 0.5f);
		}
		cryNow();
	}

	/** Wrath: two souls at each other in the shallows, circling slowly, the water flying. */
	private void brawl(ServerLevel level) {
		double a = phase + level.getGameTime() * 0.008;
		Vec3 at = new Vec3(meet.x + Math.cos(a) * 0.8 * side, home.y, meet.z + Math.sin(a) * 0.8 * side);
		Vec3 facing = meet.subtract(at);
		hold(at, (float) Math.toDegrees(Math.atan2(-facing.x, facing.z)));
		int beat = Math.floorMod(tickCount + getId() * 3, DamnedAnim.BRAWL.ticks);
		if (beat == DamnedAnim.BRAWL.at("right") || beat == DamnedAnim.BRAWL.at("left")) {
			level.sendParticles(ParticleTypes.SPLASH, getX(), home.y + 1.1, getZ(), 6, 0.4, 0.1, 0.4, 0.1);
			if (partner != null && getRandom().nextInt(4) == 0) {
				partner.play(DamnedAnim.FLINCH);
				level.playSound(null, getX(), getY() + 1, getZ(), SoundEvents.PLAYER_SPLASH, SoundSource.AMBIENT, 0.3f, 1.3f);
			}
		}
	}

	/** Heresy: writhing in the flames of its open tomb; it cries out as the tomb blasts. */
	private void tomb(ServerLevel level) {
		hold(home, getYRot());
		if (tickCount % 8 == 0) {
			level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 1.4, getZ(), 2, 0.3, 0.4, 0.3, 0.01);
		}
		boolean blasting = Tombs.blastingAt(getX(), getZ(), level.getGameTime()) != null;
		if (blasting && !wasBlasting) {
			cryNow();
		}
		wasBlasting = blasting;
	}

	/** The river of blood: bobbing in it, flailing, the blood seething round it. */
	private void blood(ServerLevel level) {
		hold(home, getYRot() + (float) Math.sin(tickCount * 0.03 + getId()) * 0.5f);
		if (tickCount % 15 == getId() % 15) {
			level.sendParticles(BloodAltar.BLOOD, getX(), getY() + 1.6, getZ(), 3, 0.4, 0.2, 0.4, 0.0);
		}
		if (getRandom().nextInt(80) == 0) {
			level.sendParticles(ParticleTypes.LAVA, getX(), getY() + 1.6, getZ(), 1, 0.3, 0.1, 0.3, 0.0);
		}
	}

	/** The Burning Sands: running round and round, or lying, beating at the flakes of fire; worst when the fire falls. */
	private void sands(ServerLevel level) {
		if (pinned) {
			hold(home, getYRot());
		} else if (tickCount % 15 == 0 || getNavigation().isDone()) {
			phase += 1.1;
			getNavigation().moveTo(home.x + Math.cos(phase) * radius, home.y, home.z + Math.sin(phase) * radius, 1.1);
		}
		boolean falling = LowerRules.fireFalling(level.getGameTime());
		if (tickCount % (falling ? 4 : 10) == 0) {
			level.sendParticles(falling ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME, getX(), getY() + (pinned ? 0.3 : 1.2), getZ(), 2,
					0.3, 0.4, 0.3, 0.01);
		}
		if (falling && !wasBlasting) {
			cryNow();
		}
		wasBlasting = falling;
	}

	/** Cocytus: frozen to the neck, shaking, the frost on it. */
	private void ice(ServerLevel level) {
		hold(home, getYRot());
		if (tickCount % 20 == getId() % 20) {
			level.sendParticles(ParticleTypes.SNOWFLAKE, getX(), home.y + 1.6, getZ(), 2, 0.3, 0.2, 0.3, 0.01);
		}
	}

	// ------------------------------------------------------------------ crying out

	/** Cries out soon (its punishment just peaked), if nobody near heard a cry a moment ago. */
	public void cryNow() {
		nextCry = Math.min(nextCry, tickCount + getRandom().nextInt(20));
	}

	private void cry(ServerLevel level) {
		if (tickCount < nextCry) {
			return;
		}
		if (!Torments.mayCry(level, position())) {
			nextCry = tickCount + 40 + getRandom().nextInt(60);
			return;
		}
		nextCry = tickCount + 300 + getRandom().nextInt(500);
		float pitch = 0.9f + getRandom().nextFloat() * 0.3f;
		if (punishment() == Punishment.LIMBO) {
			play(DamnedAnim.SIGH);
			level.playSound(null, getX(), getEyeY(), getZ(), SIGH, SoundSource.AMBIENT, 1.0f, pitch);
			return;
		}
		// the frozen can't move to scream: only the sound comes out of the ice
		if (punishment() != Punishment.COCYTUS) {
			play(DamnedAnim.SCREAM);
		}
		level.playSound(null, getX(), getEyeY(), getZ(), SCREAM, SoundSource.AMBIENT, 1.8f, pitch);
	}

	// ------------------------------------------------------------------ never harmed, never in the way

	/** Nothing hurts the damned (their torment is their own): only /kill and the void touch them. */
	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
	}

	/** Burning souls are drawn on fire, though fire can't hurt them (their entity type is fireproof). */
	@Override
	public boolean displayFireAnimation() {
		return punishment().burns && !isRemoved();
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void doPush(Entity entity) {
	}

	@Override
	public void push(Entity entity) {
	}

	@Override
	public void push(double xa, double ya, double za) {
	}

	@Override
	public void knockback(double power, double xd, double zd, DamageSource source, float damage, boolean comesFromEffect) {
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	/** Seen, not touched: hits and arrows pass through a soul. */
	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean canBeHitByProjectile() {
		return false;
	}

	@Override
	public boolean canBeSeenAsEnemy() {
		return false;
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	@Override
	public boolean canFreeze() {
		return false;
	}

	@Override
	public boolean isAffectedByPotions() {
		return false;
	}

	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean canUsePortal(boolean ignorePassenger) {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	protected int getBaseExperienceReward(ServerLevel level) {
		return 0;
	}

	@Override
	protected boolean shouldDropLoot(ServerLevel level) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return true;
	}

	/** Never saved with the world: they're brought out afresh wherever someone is. */
	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return null;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return null;
	}
}
