package net.thesmallthings.hellcraft.entity.boss;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.entity.Carrier;
import net.thesmallthings.hellcraft.entity.HellEntities;
import net.thesmallthings.hellcraft.entity.boss.gen.GeryonAnim;
import net.thesmallthings.hellcraft.world.FraudSites;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Geryon tamed (GeryonFight, once he's beaten): he waits at the brink of the great cliff, and whoever right-clicks him
 * rides on his back down over the first ditches to the fourth ridge of Malebolge, wheeling slowly down as he carried Dante ("he goes swimming
 * slowly, slowly; wheels and descends"). He waits there a while for anyone going back up, then flies home. He can't be
 * hurt, and nobody riding him can be (Carrier). Drawn with Geryon's own model.
 */
public class GeryonCarrier extends AnimatedBoss implements Carrier {
	private static final int MAX_RIDERS = 2;
	/** Ticks he waits after the first rider climbs on, for the others. */
	private static final int BOARDING = 60;
	/** Ticks the flight down (or up) takes. */
	private static final int FLIGHT = 520;
	/** The ridge of Malebolge he sets you down on, past the first ditches. */
	private static final int RIDGE = 4;
	/** The radius of his wheeling (blocks), how many full turns he makes on the way, and how high he swings over the ditches. */
	private static final double WHEEL = 22.0;
	private static final int TURNS = 2;
	private static final double LOFT = 10.0;
	/** Ticks he waits below for riders up, before he flies home. */
	private static final int BELOW_WAIT = 400;

	private enum Phase { PERCHED, DOWN, BELOW, UP }

	private Phase phase = Phase.PERCHED;
	@Nullable
	private Vec3 home;
	@Nullable
	private Vec3 landing;
	private int phaseStart;
	private int departAt = -1;
	private final Set<UUID> riders = new HashSet<>();

	public GeryonCarrier(EntityType<? extends Monster> type, Level level) {
		super(type, level, GeryonAnim.values());
		setNoAi(true);
		setNoGravity(true);
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 320.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.MOVEMENT_SPEED, 0.0)
				.add(Attributes.FLYING_SPEED, 0.0);
	}

	@Override
	protected void registerGoals() {
	}

	/** Puts a tamed Geryon at the brink by this lair (the fight's end), replacing any already there. */
	public static void perch(ServerLevel level, BlockPos lair, Vec3 from) {
		double angle = Math.atan2(lair.getZ(), lair.getX());
		double cx = Math.cos(angle);
		double cz = Math.sin(angle);
		double lairR = Math.sqrt((double) lair.getX() * lair.getX() + (double) lair.getZ() * lair.getZ());
		double edge = lairR - 10;
		for (double r = lairR; r > lairR - 100; r -= 0.5) {
			if (surface(level, cx * r, cz * r) < lair.getY() - 6) {
				edge = r;
				break;
			}
		}
		double homeR = edge + 4;
		Vec3 home = new Vec3(cx * homeR, surface(level, cx * homeR, cz * homeR) + 3, cz * homeR);
		double ridgeR = FraudSites.onRidge(angle, FraudSites.ridgeRadius(RIDGE));
		Vec3 landing = new Vec3(cx * ridgeR, surface(level, cx * ridgeR, cz * ridgeR) + 1.5, cz * ridgeR);
		GeryonCarrier carrier = HellEntities.GERYON_CARRIER.create(level, EntitySpawnReason.EVENT);
		if (carrier == null) {
			return;
		}
		carrier.home = home;
		carrier.landing = landing;
		carrier.snapTo(home.x, home.y, home.z, (float) Math.toDegrees(angle) + 90.0f, 0.0f);
		carrier.setCustomName(Component.literal("Geryon"));
		if (level.addFreshEntity(carrier)) {
			HellcraftMod.LOGGER.info("Geryon perches at the brink at {} {} {}, and lands on ridge {} at {} {} {}", (int) home.x, (int) home.y,
					(int) home.z, RIDGE, (int) landing.x, (int) landing.y, (int) landing.z);
		}
	}

	private static int surface(ServerLevel level, double x, double z) {
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		level.getChunk(bx >> 4, bz >> 4);
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
	}

	// ------------------------------------------------------------------ the flight

	/**
	 * Where he is `s` of the way down (0 at the brink, 1 on the ridge): out over the abyss and down across the first
	 * ditches of Malebolge, wheeling in wide loops (which open and close with the flight, so he leaves and lands
	 * straight), and swung high enough over the ditches' ridges never to touch them.
	 */
	private Vec3 along(double s) {
		double k = smooth(s);
		Vec3 line = new Vec3(home.x + (landing.x - home.x) * k, 0, home.z + (landing.z - home.z) * k);
		Vec3 out = new Vec3(home.x, 0, home.z).normalize();
		Vec3 side = new Vec3(-out.z, 0, out.x);
		double psi = Math.PI * 2 * TURNS * s;
		double open = Math.sin(Math.PI * s);
		Vec3 flat = line.add(side.scale(WHEEL * open * Math.sin(psi))).add(out.scale(WHEEL * open * (Math.cos(psi) - 1) * 0.5));
		double y = home.y + (landing.y - home.y) * smooth(s) + LOFT * Math.sin(Math.PI * Math.min(1.0, s * 1.2));
		return new Vec3(flat.x, y, flat.z);
	}

	private static double smooth(double t) {
		t = Mth.clamp(t, 0, 1);
		return t * t * (3 - 2 * t);
	}

	private void flyTo(double s, boolean down) {
		Vec3 at = along(s);
		Vec3 next = along(Mth.clamp(s + (down ? 0.01 : -0.01), 0, 1));
		Vec3 d = next.subtract(at);
		if (d.horizontalDistanceSqr() > 1.0e-6) {
			float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
			setYRot(yaw);
			setYBodyRot(yaw);
			setYHeadRot(yaw);
		}
		setPos(at.x, at.y, at.z);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		if (home == null || landing == null) {
			discard();
			return;
		}
		setDeltaMovement(Vec3.ZERO);
		int t = tickCount - phaseStart;
		switch (phase) {
			case PERCHED, BELOW -> {
				if (getPassengers().isEmpty()) {
					departAt = -1;
					if (phase == Phase.BELOW && t > BELOW_WAIT) {
						start(Phase.UP);
					}
				} else if (tickCount >= departAt) {
					start(phase == Phase.PERCHED ? Phase.DOWN : Phase.UP);
					for (Entity e : getPassengers()) {
						if (e instanceof ServerPlayer p) {
							p.sendOverlayMessage(Component.literal(phase == Phase.DOWN ? "Geryon wheels slowly down into Malebolge. Hold on!"
									: "Geryon carries you back up to the brink.").withStyle(ChatFormatting.AQUA));
						}
					}
				}
			}
			case DOWN -> {
				flyTo((double) t / FLIGHT, true);
				if (t % 25 == 0) {
					level.playSound(null, blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.NEUTRAL, 1.5f, 0.5f);
				}
				if (t >= FLIGHT) {
					setDown(landing, "Geryon sets you down on the fourth ridge of Malebolge. He waits a little, if you want to go back up.");
					start(Phase.BELOW);
				}
			}
			case UP -> {
				flyTo(1.0 - (double) t / FLIGHT, false);
				if (t % 25 == 0) {
					level.playSound(null, blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.NEUTRAL, 1.5f, 0.5f);
				}
				if (t >= FLIGHT) {
					setDown(home, "Geryon sets you down at the brink of the great cliff.");
					start(Phase.PERCHED);
				}
			}
		}
		watchRiders(level);
	}

	private void start(Phase next) {
		phase = next;
		phaseStart = tickCount;
		departAt = -1;
		play(next == Phase.DOWN || next == Phase.UP ? GeryonAnim.CARRY : GeryonAnim.IDLE);
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (level().isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (phase != Phase.PERCHED && phase != Phase.BELOW || player.isPassenger() || getPassengers().size() >= MAX_RIDERS) {
			return InteractionResult.PASS;
		}
		if (player.startRiding(this, true, true)) {
			riders.add(player.getUUID());
			if (departAt < 0) {
				departAt = tickCount + BOARDING;
			}
			if (player instanceof ServerPlayer sp) {
				sp.sendOverlayMessage(Component.literal("Geryon takes off in a few seconds. Sneak now to get off; once he flies, don't.")
						.withStyle(ChatFormatting.AQUA));
			}
			level().playSound(null, blockPosition(), SoundEvents.HORSE_SADDLE.value(), SoundSource.NEUTRAL, 1.0f, 0.6f);
			return InteractionResult.SUCCESS_SERVER;
		}
		return InteractionResult.PASS;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengers().size() < MAX_RIDERS;
	}

	/** On his back: the first rider at his shoulders, the second behind. */
	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		int i = Math.max(0, getPassengers().indexOf(passenger));
		return new Vec3(0, 2.4, i == 0 ? 1.4 : -0.6).yRot(-getYRot() * Mth.DEG_TO_RAD);
	}

	private void setDown(Vec3 at, String text) {
		for (Entity e : List.copyOf(getPassengers())) {
			e.stopRiding();
			int y = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(at.x), (int) Math.floor(at.z));
			e.teleportTo(at.x, y, at.z);
			e.resetFallDistance();
			if (e instanceof ServerPlayer p) {
				p.sendOverlayMessage(Component.literal(text).withStyle(ChatFormatting.AQUA));
			}
		}
		riders.clear();
	}

	/** Whoever lets go in the air falls slowly, at least. */
	private void watchRiders(ServerLevel level) {
		riders.removeIf(id -> {
			if (!(level.getEntity(id) instanceof ServerPlayer p)) {
				return true;
			}
			if (p.getVehicle() == this) {
				return false;
			}
			if (phase == Phase.DOWN || phase == Phase.UP) {
				p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 300, 0));
				p.sendOverlayMessage(Component.literal("You let go of Geryon!").withStyle(ChatFormatting.RED));
			}
			return true;
		});
	}

	// ------------------------------------------------------------------ tamed: no harm, no fight

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	protected int getBaseExperienceReward(ServerLevel level) {
		return 0;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (home != null && landing != null) {
			output.putDouble("home_x", home.x);
			output.putDouble("home_y", home.y);
			output.putDouble("home_z", home.z);
			output.putDouble("landing_x", landing.x);
			output.putDouble("landing_y", landing.y);
			output.putDouble("landing_z", landing.z);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		double hx = input.getDoubleOr("home_x", Double.NaN);
		double lx = input.getDoubleOr("landing_x", Double.NaN);
		if (!Double.isNaN(hx) && !Double.isNaN(lx)) {
			home = new Vec3(hx, input.getDoubleOr("home_y", 0), input.getDoubleOr("home_z", 0));
			landing = new Vec3(lx, input.getDoubleOr("landing_y", 0), input.getDoubleOr("landing_z", 0));
			// back at the brink after a restart
			setPos(home.x, home.y, home.z);
			phase = Phase.PERCHED;
		}
	}
}
