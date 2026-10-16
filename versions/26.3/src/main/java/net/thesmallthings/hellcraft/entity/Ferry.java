package net.thesmallthings.hellcraft.entity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Phlegyas's ferry (tools/art/ferry.py): it glides in to a dock, waits for pilgrims, and carries whoever boards it
 * (right-click) across the Fen of Styx and through a gate of Dis, where it sets them down on the road inside the walls and
 * goes back into the fog (world/Ferries says where and when). It skims over whatever stands in the water (reeds,
 * boardwalks, the banks) instead of ploughing through it, and nobody aboard can be hurt (Carrier): not the sullen, not
 * the rock. It can't be hurt either, and doesn't outlast a server restart.
 */
public class Ferry extends Entity implements Carrier {
	/** Blocks a tick, crossing. */
	private static final double SPEED = 0.8;
	/** How fast it rises over something in the water, and settles back (blocks a tick). */
	private static final double RISE = 0.35;
	private static final double SETTLE = 0.12;
	private static final int MAX_PASSENGERS = 8;
	/** Ticks it waits after the first pilgrim boards, for the others. */
	private static final int BOARDING = 60;

	public enum Phase {
		ARRIVING, WAITING, SAILING, LEAVING
	}

	public final AnimationState bob = new AnimationState();
	private Phase phase = Phase.WAITING;
	@Nullable
	private Vec3 dock;
	@Nullable
	private Vec3 across;
	@Nullable
	private Vec3 land;
	private int waitLeft;
	private int departAt = -1;
	private int leaving;

	public Ferry(EntityType<? extends Ferry> type, Level level) {
		super(type, level);
		noPhysics = true;
		setNoGravity(true);
	}

	/**
	 * Sends it on its way: in from {@code from} to the dock, where it waits so many ticks, then across to {@code across}
	 * (on the water by a gate of Dis), setting its passengers down at {@code land}.
	 */
	public void route(Vec3 from, Vec3 dock, Vec3 across, Vec3 land, int wait) {
		this.dock = dock;
		this.across = across;
		this.land = land;
		this.waitLeft = wait;
		this.phase = from.distanceToSqr(dock) < 1 ? Phase.WAITING : Phase.ARRIVING;
		snapTo(from.x, from.y, from.z, yawToward(from, dock), 0);
	}

	public Phase phase() {
		return phase;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (level().isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (phase != Phase.WAITING && phase != Phase.ARRIVING || player.isPassenger() || getPassengers().size() >= MAX_PASSENGERS) {
			return InteractionResult.PASS;
		}
		if (player.startRiding(this)) {
			if (departAt < 0) {
				departAt = tickCount + BOARDING;
			}
			if (player instanceof ServerPlayer sp) {
				sp.sendOverlayMessage(Component.literal("The ferry casts off in a few seconds. Sneak to get off before it does.").withStyle(ChatFormatting.DARK_AQUA));
			}
			level().playSound(null, blockPosition(), SoundEvents.WOOD_STEP, SoundSource.NEUTRAL, 1.0f, 0.6f);
			return InteractionResult.SUCCESS_SERVER;
		}
		return InteractionResult.PASS;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengers().size() < MAX_PASSENGERS;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			bob.startIfStopped(tickCount);
			return;
		}
		if (dock == null || across == null || land == null) {
			discard();
			return;
		}
		switch (phase) {
			case ARRIVING -> {
				if (glide(dock)) {
					phase = Phase.WAITING;
					level().playSound(null, blockPosition(), SoundEvents.WOOD_PLACE, SoundSource.NEUTRAL, 1.5f, 0.5f);
				}
			}
			case WAITING -> {
				if (getPassengers().isEmpty()) {
					departAt = -1;
					if (--waitLeft <= 0) {
						phase = Phase.LEAVING;
					}
				} else if (tickCount >= departAt) {
					phase = Phase.SAILING;
					for (Entity e : getPassengers()) {
						if (e instanceof ServerPlayer p) {
							p.sendOverlayMessage(Component.literal("The ferry carries you across the Styx and through the gate of Dis. Nothing can hurt you aboard.")
									.withStyle(ChatFormatting.DARK_AQUA));
						}
					}
				}
			}
			case SAILING -> {
				if (tickCount % 30 == 0) {
					level().playSound(null, blockPosition(), SoundEvents.BOAT_PADDLE_WATER, SoundSource.NEUTRAL, 1.5f, 0.6f);
				}
				if (glide(across)) {
					setDown();
					phase = Phase.LEAVING;
				}
			}
			case LEAVING -> {
				if (++leaving > 80) {
					discard();
				} else {
					Vec3 away = position().add(position().subtract(across).normalize().scale(SPEED)).multiply(1, 0, 1).add(0, getY() - leaving * 0.02, 0);
					setPos(away);
				}
			}
		}
	}

	/** One step toward a point on the water (lifted over anything in the way); true once there. */
	private boolean glide(Vec3 to) {
		Vec3 d = to.subtract(position());
		double flat = Math.sqrt(d.x * d.x + d.z * d.z);
		if (flat <= SPEED) {
			setPos(to.x, Math.max(to.y, clearance(to.x, to.z, 0, 0)), to.z);
			return true;
		}
		double dx = d.x / flat;
		double dz = d.z / flat;
		double x = getX() + dx * SPEED;
		double z = getZ() + dz * SPEED;
		// what it must clear: under its hull, and a few blocks ahead so it lifts before it gets there
		double want = Math.max(to.y, clearance(x, z, dx, dz));
		double y = want > getY() ? Math.min(want, getY() + RISE) : Math.max(want, getY() - SETTLE);
		setYRot(yawToward(position(), to));
		setPos(x, y, z);
		return false;
	}

	/** The height its hull must float at to pass over everything round (x, z) and just ahead of it. */
	private double clearance(double x, double z, double dx, double dz) {
		double top = Double.NEGATIVE_INFINITY;
		for (double ahead = 0; ahead <= 4; ahead += 2) {
			for (int ox = -2; ox <= 2; ox += 2) {
				for (int oz = -2; oz <= 2; oz += 2) {
					int bx = (int) Math.floor(x + dx * ahead + ox);
					int bz = (int) Math.floor(z + dz * ahead + oz);
					// the first free block above the ground or the water there
					top = Math.max(top, level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz));
				}
			}
		}
		// the water itself is "ground" one block up: the hull rides 0.6 below its surface, as on open water
		return top - 0.6;
	}

	/** Sets its passengers down on the road inside the gate. */
	private void setDown() {
		for (Entity e : getPassengers()) {
			e.stopRiding();
			int y = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(land.x), (int) Math.floor(land.z));
			e.teleportTo(land.x, y, land.z);
			e.resetFallDistance();
			if (e instanceof ServerPlayer p) {
				p.sendOverlayMessage(Component.literal("Inside the Walls of Dis: Heresy lies ahead.").withStyle(ChatFormatting.RED));
			}
		}
		level().playSound(null, blockPosition(), SoundEvents.WOOD_PLACE, SoundSource.NEUTRAL, 1.5f, 0.5f);
	}

	private static float yawToward(Vec3 from, Vec3 to) {
		return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return !isRemoved();
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}
}
