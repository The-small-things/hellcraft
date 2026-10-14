package net.thesmallthings.hellcraft.entity.monster;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.thesmallthings.hellcraft.hazard.Hoarders;

/**
 * A hoarder (Greed's monster): drawn to whoever carries gold (Hoarders spawns them, more for a heavier purse), it
 * hunts only the laden, and its grasping blow steals gold, which it keeps until it is killed. Stash your gold and it
 * loses interest and wanders off. Its body is tools/art/hoarder.py.
 */
public class Hoarder extends Monster implements InventoryCarrier {
	public final AnimationState idle = new AnimationState();
	public final AnimationState attack = new AnimationState();
	public final AnimationState hurt = new AnimationState();
	public final AnimationState death = new AnimationState();
	private final SimpleContainer loot = new SimpleContainer(9);
	/** Ticks without anyone carrying gold to hunt; long enough, and it goes. */
	private int bored;

	public Hoarder(EntityType<? extends Monster> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 24.0)
				.add(Attributes.MOVEMENT_SPEED, 0.26)
				.add(Attributes.ATTACK_DAMAGE, 3.0)
				.add(Attributes.FOLLOW_RANGE, 32.0)
				.add(Attributes.ARMOR, 2.0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, false));
		goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
		goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0f));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false, (target, level) -> Hoarders.weight(target) > 0));
	}

	@Override
	public SimpleContainer getInventory() {
		return loot;
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		level.broadcastEntityEvent(this, EntityEvent.START_ATTACKING);
		boolean hit = super.doHurtTarget(level, target);
		if (hit && target instanceof ServerPlayer player) {
			Hoarders.steal(this, player);
		}
		return hit;
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == EntityEvent.START_ATTACKING) {
			attack.start(tickCount);
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	public void handleDamageEvent(DamageSource source) {
		super.handleDamageEvent(source);
		hurt.start(tickCount);
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			idle.animateWhen(isAlive() && walkAnimation.speed() < 0.1f, tickCount);
			if (deathTime > 0) {
				death.startIfStopped(tickCount);
			}
			return;
		}
		// a soul that stashed its gold (and hasn't hurt it) is of no more interest
		if (getTarget() instanceof Player p && Hoarders.weight(p) == 0 && getLastHurtByMob() != p) {
			setTarget(null);
		}
		bored = getTarget() == null ? bored + 1 : 0;
		if (bored > 600 && loot.isEmpty() && level().getNearestPlayer(this, 16) == null) {
			discard();
		}
	}

	@Override
	protected void tickDeath() {
		deathTime++;
		if (deathTime >= 26 && !level().isClientSide() && !isRemoved()) {
			level().broadcastEntityEvent(this, EntityEvent.POOF);
			remove(RemovalReason.KILLED);
		}
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		for (int i = 0; i < loot.getContainerSize(); i++) {
			ItemStack stack = loot.removeItemNoUpdate(i);
			if (!stack.isEmpty()) {
				spawnAtLocation(level, stack);
			}
		}
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return loot.isEmpty() && super.removeWhenFarAway(distance);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		writeInventoryToTag(output);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		readInventoryFromTag(input);
	}
}
