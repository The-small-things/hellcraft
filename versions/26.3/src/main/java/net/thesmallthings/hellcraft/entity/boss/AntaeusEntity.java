package net.thesmallthings.hellcraft.entity.boss;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.entity.Carrier;
import net.thesmallthings.hellcraft.entity.boss.gen.AntaeusAnim;
import net.thesmallthings.hellcraft.hazard.CircleHazards;
import net.thesmallthings.hellcraft.net.Dialogue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Antaeus (Inferno XXXI), the unchained giant, standing in the ice of Cocytus at the foot of the Well with his right
 * hand resting on its rim (world/AntaeusCleft builds his place). Whoever stands at the brink by his hand is lifted and
 * set down on the ice; whoever stands by the hand's place on the ice is lifted back up. He can't be hurt, and nobody
 * riding in his hand can be (Carrier). Modelled in tools/art/antaeus.py: the hand follows the path of points in
 * LOWER_PATH and RAISE_PATH there, and riders follow the same points here.
 */
public class AntaeusEntity extends AnimatedBoss implements Carrier {
	/** He is drawn this many times his Blockbench size (about 28 blocks tall). */
	public static final float SCALE = 8.0f;
	private static final double PX = SCALE / 16.0;
	/** His hips, and how far to his right his hand is (pixels). */
	private static final double HIPS = 24;
	private static final double SIDE = 13.5;
	/**
	 * The middle of his right hand in pixels from his hips, by ticks into the animation: (tick, forward, up, palm), palm
	 * being how far the top of the palm is above the hand's middle. Keep in step with tools/art/antaeus.py.
	 */
	private static final double[][] LOWER_PATH = {{0, 24.43, 26.89, 4.91}, {10, 24.43, 26.89, 4.91}, {25, 22, 32, 4.87}, {70, 24.43, -20, 3.16},
			{80, 24.43, -20, 3.16}};
	private static final double[][] RAISE_PATH = {{0, 24.43, -20, 3.16}, {5, 24.43, -20, 3.16}, {48, 22, 32, 4.87}, {55, 24.43, 26.89, 4.91},
			{60, 24.43, 26.89, 4.91}};
	private static final double[] AT_RIM = LOWER_PATH[0];
	private static final double[] AT_ICE = RAISE_PATH[0];
	/** How far beside his resting hand the places to wait are (blocks): out on the rim, and to the side on the ice. */
	public static final double RIM_STEP = 3.5;
	public static final double ICE_STEP = 3.0;
	private static final int MAX_RIDERS = 3;
	/** Ticks someone must wait at a stop before he moves his hand. */
	private static final int WAIT = 40;
	/** Ticks his hand waits on the ice for someone going up. */
	private static final int LOW_WAIT = 200;

	private AntaeusAnim move = AntaeusAnim.IDLE;
	private int moveStart;
	private int waiting;
	private int restUntil;
	private final Set<UUID> riders = new HashSet<>();

	public AntaeusEntity(EntityType<? extends Monster> type, Level level) {
		super(type, level, AntaeusAnim.values());
		setNoAi(true);
		setNoGravity(true);
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 1000.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.MOVEMENT_SPEED, 0.0);
	}

	@Override
	protected void registerGoals() {
	}

	// ------------------------------------------------------------------ where his hand is

	private double[] hand() {
		int t = tickCount - moveStart;
		return switch (move) {
			case LOWER -> along(LOWER_PATH, t);
			case RAISE -> along(RAISE_PATH, t);
			case LOW -> AT_ICE;
			default -> AT_RIM;
		};
	}

	private static double[] along(double[][] path, double t) {
		for (int i = 1; i < path.length; i++) {
			if (t <= path[i][0]) {
				double[] a = path[i - 1];
				double[] b = path[i];
				double k = Math.max(0, (t - a[0]) / (b[0] - a[0]));
				return new double[]{t, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k, a[3] + (b[3] - a[3]) * k};
			}
		}
		return path[path.length - 1];
	}

	/** From his feet to the top of his palm (and `across` blocks to the side of it), for a giant turned to `yRot`. */
	private static Vec3 palm(float yRot, double[] hand, double across) {
		double yaw = Math.toRadians(yRot);
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double rx = -Math.cos(yaw);
		double rz = -Math.sin(yaw);
		double forward = hand[1] * PX;
		double side = SIDE * PX + across;
		return new Vec3(fx * forward + rx * side, (HIPS + hand[2] + hand[3]) * PX, fz * forward + rz * side);
	}

	/** From his feet to the top of his palm as it rests on the rim, for a giant turned to `yRot` (AntaeusCleft). */
	public static Vec3 restingPalm(float yRot) {
		return palm(yRot, AT_RIM, 0);
	}

	/** Where the top of his palm is now, in the world. */
	public Vec3 palmNow() {
		return position().add(palm(getYRot(), hand(), 0));
	}

	/** Where to wait on the rim to be carried down. */
	public Vec3 rimStop() {
		return rimStop(level(), position(), getYRot());
	}

	/** Where to wait on the ice to be carried up. */
	public Vec3 iceStop() {
		return iceStop(level(), position(), getYRot());
	}

	/** The rim stop of a giant standing at `feet`, turned to `yRot` (his entity need not be loaded). */
	public static Vec3 rimStop(Level level, Vec3 feet, float yRot) {
		return stop(level, feet, yRot, AT_RIM, RIM_STEP, 0);
	}

	/** The ice stop of a giant standing at `feet`, turned to `yRot`. */
	public static Vec3 iceStop(Level level, Vec3 feet, float yRot) {
		return stop(level, feet, yRot, AT_ICE, 0, ICE_STEP);
	}

	private static Vec3 stop(Level level, Vec3 feet, float yRot, double[] hand, double out, double side) {
		double yaw = Math.toRadians(yRot);
		Vec3 p = feet.add(palm(yRot, hand, side));
		p = p.add(-Math.sin(yaw) * out, 0, Math.cos(yaw) * out);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(p.x), (int) Math.floor(p.z));
		return new Vec3(p.x, y, p.z);
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		int i = Math.max(0, getPassengers().indexOf(passenger));
		return palm(getYRot(), hand(), (i - (getPassengers().size() - 1) / 2.0) * 1.1);
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengers().size() < MAX_RIDERS;
	}

	// ------------------------------------------------------------------ carrying

	private void startMove(AntaeusAnim anim) {
		move = anim;
		moveStart = tickCount;
		waiting = 0;
		play(anim);
	}

	@Override
	protected void onAnimation(int id) {
		super.onAnimation(id);
		move = AntaeusAnim.values()[id];
		moveStart = tickCount;
	}

	@Override
	public void tick() {
		super.tick();
		setYBodyRot(getYRot());
		setYHeadRot(getYRot());
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		int t = tickCount - moveStart;
		switch (move) {
			case LOWER -> {
				if (t == AntaeusAnim.LOWER.at("set")) {
					setDown(iceStop(), "Antaeus sets you down on the ice of Cocytus. To go back up, wait on the crying obsidian beside his hand.");
				}
				if (t >= AntaeusAnim.LOWER.ticks) {
					startMove(AntaeusAnim.LOW);
				}
			}
			case LOW -> {
				List<ServerPlayer> below = waitingAt(level, iceStop());
				if (below.isEmpty()) {
					waiting = 0;
					if (t >= LOW_WAIT) {
						startMove(AntaeusAnim.RAISE);
					}
				} else if (++waiting >= WAIT / 2) {
					take(below);
					startMove(AntaeusAnim.RAISE);
				}
			}
			case RAISE -> {
				if (t == AntaeusAnim.RAISE.at("set")) {
					setDown(rimStop(), "Antaeus sets you down on the rim of the Well.");
				}
				if (t >= AntaeusAnim.RAISE.ticks) {
					startMove(AntaeusAnim.IDLE);
					restUntil = tickCount + WAIT;
				}
			}
			default -> rest(level);
		}
		watchRiders(level);
		if (tickCount % 20 == 0) {
			for (ServerPlayer p : level.getPlayers(p -> p.distanceToSqr(this) < 48 * 48 && !p.isSpectator())) {
				CircleHazards.hintOnce(p, 37, "That is Antaeus, who was never chained. He will carry you down the Well: wait on the crying "
						+ "obsidian at the brink by his hand. To come back up, wait on the one beside his hand on the ice.");
			}
		}
	}

	/** His hand on the rim: someone waiting at either stop sets it moving. */
	private void rest(ServerLevel level) {
		if (tickCount < restUntil) {
			return;
		}
		List<ServerPlayer> above = waitingAt(level, rimStop());
		List<ServerPlayer> below = above.isEmpty() ? waitingAt(level, iceStop()) : List.of();
		if (above.isEmpty() && below.isEmpty()) {
			waiting = 0;
			return;
		}
		if (++waiting == 1) {
			for (ServerPlayer p : above.isEmpty() ? below : above) {
				p.sendOverlayMessage(Component.literal(above.isEmpty() ? "Antaeus reaches down for you. Stay where you are."
						: "Antaeus will lift you. Stay where you are.").withStyle(ChatFormatting.AQUA));
			}
		}
		if (waiting >= WAIT) {
			if (!above.isEmpty()) {
				take(above);
			}
			startMove(AntaeusAnim.LOWER);
			level.playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.NEUTRAL, 2.0f, 0.4f);
		}
	}

	/** The players standing still at a stop. */
	private List<ServerPlayer> waitingAt(ServerLevel level, Vec3 stop) {
		List<ServerPlayer> found = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator() || !p.isAlive() || p.isPassenger()) {
				continue;
			}
			double dx = p.getX() - stop.x;
			double dz = p.getZ() - stop.z;
			if (dx * dx + dz * dz < 1.6 * 1.6 && Math.abs(p.getY() - stop.y) < 1.5) {
				found.add(p);
			}
		}
		return found;
	}

	private void take(List<ServerPlayer> players) {
		for (ServerPlayer p : players) {
			if (getPassengers().size() < MAX_RIDERS && p.startRiding(this, true, true)) {
				riders.add(p.getUUID());
				Dialogue.notice(p, Component.literal("Antaeus takes you in his hand. Don't sneak: you would fall.").withStyle(ChatFormatting.AQUA));
			}
		}
	}

	private void setDown(Vec3 at, String text) {
		for (Entity e : List.copyOf(getPassengers())) {
			e.stopRiding();
			e.teleportTo(at.x, at.y, at.z);
			e.resetFallDistance();
			if (e instanceof ServerPlayer p) {
				p.sendOverlayMessage(Component.literal(text).withStyle(ChatFormatting.AQUA));
			}
		}
		riders.clear();
		level().playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.6f, 0.4f);
	}

	/** Whoever sneaked out of his hand on the way falls slowly, at least. */
	private void watchRiders(ServerLevel level) {
		riders.removeIf(id -> {
			if (!(level.getEntity(id) instanceof ServerPlayer p)) {
				return true;
			}
			if (p.getVehicle() == this) {
				return false;
			}
			p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0));
			p.sendOverlayMessage(Component.literal("You slipped out of Antaeus's hand!").withStyle(ChatFormatting.RED));
			return true;
		});
	}

	// ------------------------------------------------------------------ a landmark, not a fight

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	protected int getBaseExperienceReward(ServerLevel level) {
		return 0;
	}
}
