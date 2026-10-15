package net.thesmallthings.hellcraft.entity.monster;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.entity.HellEntities;
import net.thesmallthings.hellcraft.entity.boss.gen.MalacodaAnim;
import net.thesmallthings.hellcraft.net.Dialogue;
import net.thesmallthings.hellcraft.net.MapSync;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.Zone;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Malacoda, "Evil Tail", captain of the Malebranche (Inferno XXI, tools/art/malacoda.py). The first time a soul comes
 * down the road onto the fifth ridge of Malebolge he meets it there, friendly as anything: the bridge ahead is broken,
 * he says, but follow him along the ridge to another. There is no other. He leads it a little way, laughs, and calls
 * his devils down on it, then strolls off (Frauds starts him; he is never saved, and can't be hurt).
 */
public class Malacoda extends SoulMonster {
	private enum Phase { TALK, LEAD, AMBUSH, LEAVE }

	private static final int TALK = 70;
	private static final int LEAD_MAX = 700;
	private static final int DEVILS = 4;

	private Phase phase = Phase.TALK;
	private int phaseStart;
	@Nullable
	private UUID guest;
	@Nullable
	private Vec3 dest;
	@Nullable
	private Vec3 away;

	public Malacoda(EntityType<? extends Monster> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 60.0)
				.add(Attributes.MOVEMENT_SPEED, 0.25)
				.add(Attributes.FOLLOW_RANGE, 48.0);
	}

	@Override
	protected void registerGoals() {
		// none: the trick moves him
	}

	/** Meets `player` here, and will lead them along the ridge toward `to`. */
	public static void meet(ServerLevel level, ServerPlayer player, Vec3 at, Vec3 to) {
		Malacoda m = HellEntities.MALACODA.create(level, EntitySpawnReason.EVENT);
		if (m == null) {
			return;
		}
		m.snapTo(at.x, at.y, at.z, 0, 0);
		m.guest = player.getUUID();
		m.dest = to;
		m.phaseStart = 0;
		m.setCustomName(Component.literal("Malacoda"));
		level.addFreshEntity(m);
		m.lookAt(player, 180, 180);
		MapSync.unlockCodex(player, "malebranche");
		Dialogue.say(player, "Malacoda", ChatFormatting.RED, Component.literal("Well met, pilgrims! You can't go on: the bridge over the next "
				+ "ditch fell down long ago. But follow me along this ridge and I'll show you another that's whole."), false);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel level) || guest == null) {
			// one summoned by hand just stands about
			return;
		}
		ServerPlayer p = guest == null ? null : level.getServer().getPlayerList().getPlayer(guest);
		if ((p == null || dest == null || p.distanceToSqr(this) > 80 * 80) && phase != Phase.LEAVE) {
			discard();
			return;
		}
		int t = tickCount - phaseStart;
		switch (phase) {
			case TALK -> {
				if (p != null) {
					getLookControl().setLookAt(p);
				}
				if (t == 30) {
					level.broadcastEntityEvent(this, EntityEvent.START_ATTACKING);
				}
				if (t >= TALK) {
					next(Phase.LEAD);
					if (p != null) {
						Dialogue.say(p, "Malacoda", ChatFormatting.RED, Component.literal("This way, this way. My boys will see you safe."), false);
					}
				}
			}
			case LEAD -> {
				if (p == null || dest == null) {
					return;
				}
				// he waits for you if you fall behind
				if (p.distanceToSqr(this) > 14 * 14) {
					getNavigation().stop();
					getLookControl().setLookAt(p);
				} else if (getNavigation().isDone() || tickCount % 40 == 0) {
					getNavigation().moveTo(dest.x, dest.y, dest.z, 0.9);
				}
				if (position().distanceToSqr(dest) < 3 * 3 || t > LEAD_MAX) {
					next(Phase.AMBUSH);
				}
			}
			case AMBUSH -> {
				getNavigation().stop();
				if (p != null) {
					getLookControl().setLookAt(p);
				}
				if (t == 1) {
					playSpecial();
					playSound(SoundEvents.WITCH_CELEBRATE, 2.0f, 0.6f);
					if (p != null) {
						Dialogue.say(p, "Malacoda", ChatFormatting.RED, Component.literal("Ha! There's no bridge here, and there never was. At them, my "
								+ "Malebranche!"), true);
					}
				}
				if (t == 20 && p != null) {
					devils(level, p);
					Dialogue.notice(p, Component.literal("Malacoda lied: there's no bridge here. Fight or run back to the road; the way down is the "
							+ "road's bridge, broken or not.").withStyle(ChatFormatting.GOLD));
				}
				if (t >= MalacodaAnim.LAUGH.ticks + 10) {
					next(Phase.LEAVE);
					away = position().add(position().subtract(p != null ? p.position() : position().add(1, 0, 0)).multiply(1, 0, 1).normalize().scale(30));
				}
			}
			case LEAVE -> {
				if (away != null && (getNavigation().isDone() || tickCount % 40 == 0)) {
					getNavigation().moveTo(away.x, away.y, away.z, 1.1);
				}
				if (t > 120) {
					level.broadcastEntityEvent(this, EntityEvent.POOF);
					discard();
				}
			}
		}
	}

	private void next(Phase phase) {
		this.phase = phase;
		this.phaseStart = tickCount;
	}

	/** His devils drop on the soul from all round, on firm ground out of the pitch. */
	private void devils(ServerLevel level, ServerPlayer p) {
		int placed = 0;
		for (int tries = 0; tries < 24 && placed < DEVILS; tries++) {
			double a = random.nextDouble() * Math.PI * 2;
			double d = 6 + random.nextDouble() * 4;
			int x = (int) Math.floor(p.getX() + Math.cos(a) * d);
			int z = (int) Math.floor(p.getZ() + Math.sin(a) * d);
			if (InfernoGeometry.zoneAt(x, z) != Zone.MALEBOLGE) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			if (Math.abs(y - p.getY()) > 5) {
				continue;
			}
			Malebranche devil = HellEntities.MALEBRANCHE.create(level, EntitySpawnReason.EVENT);
			if (devil == null) {
				return;
			}
			devil.snapTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360, 0);
			if (!level.noCollision(devil)) {
				continue;
			}
			level.addFreshEntity(devil);
			devil.setTarget(p);
			devil.playSpecial();
			placed++;
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.PIGLIN_BRUTE_AMBIENT;
	}

	@Override
	protected int deathTicks() {
		return MalacodaAnim.DEATH.ticks + 4;
	}
}
