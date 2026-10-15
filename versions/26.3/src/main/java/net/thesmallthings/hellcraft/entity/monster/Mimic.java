package net.thesmallthings.hellcraft.entity.monster;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.entity.boss.gen.MimicAnim;
import org.jetbrains.annotations.Nullable;

/**
 * A mimic (Malebolge's monster, tools/art/mimic.py): a chest that isn't, waiting on a ridge where a cache should be
 * (Frauds). Shut, it is a chest to the life, except that now and then its lid lifts a crack, as if breathing. Come
 * within a couple of blocks, touch it or hit it, and it gapes and hops after you. Killed, it gives up the treasure it
 * was guarding (loot_table/entities/mimic).
 */
public class Mimic extends SoulMonster {
	private static final EntityDataAccessor<Boolean> DORMANT = SynchedEntityData.defineId(Mimic.class, EntityDataSerializers.BOOLEAN);
	/** How close a soul must come to wake it. */
	private static final double WAKE = 2.4;
	@Nullable
	private String site;
	private int nextBreath = 80;

	public Mimic(EntityType<? extends Monster> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 36.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.ATTACK_DAMAGE, 7.0)
				.add(Attributes.ARMOR, 6.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.FOLLOW_RANGE, 20.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DORMANT, false);
	}

	public boolean dormant() {
		return entityData.get(DORMANT);
	}

	/** Lies in wait as a chest on this site (FraudSites), facing one of the four ways. */
	public void lurk(@Nullable String site, int facing) {
		this.site = site;
		entityData.set(DORMANT, true);
		float yaw = facing * 90f;
		setYRot(yaw);
		yBodyRot = yaw;
		yHeadRot = yaw;
		setPersistenceRequired();
	}

	@Nullable
	public String site() {
		return site;
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, false));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		if (!dormant()) {
			super.customServerAiStep(level);
			return;
		}
		getNavigation().stop();
		setTarget(null);
		yHeadRot = getYRot();
		yBodyRot = getYRot();
		if (tickCount >= nextBreath) {
			// the tell: the lid lifts a crack
			nextBreath = tickCount + 120 + random.nextInt(100);
			playSpecial();
			playSound(SoundEvents.CHEST_LOCKED, 0.25f, 0.5f);
		}
		Player near = level.getNearestPlayer(this, WAKE);
		if (near instanceof ServerPlayer p && !p.isSpectator() && !p.isCreative()) {
			wake(p);
		}
	}

	/** Gapes and comes for whoever woke it. */
	public void wake(@Nullable ServerPlayer by) {
		if (!dormant()) {
			return;
		}
		entityData.set(DORMANT, false);
		playSound(SoundEvents.CHEST_OPEN, 1.0f, 0.5f);
		playSound(SoundEvents.RAVAGER_ROAR, 0.8f, 1.6f);
		if (by != null) {
			setTarget(by);
			by.sendOverlayMessage(Component.literal("That chest was a mimic!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
		}
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (dormant() && player instanceof ServerPlayer p) {
			wake(p);
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (dormant()) {
			wake(source.getEntity() instanceof ServerPlayer p ? p : null);
		}
		return super.hurtServer(level, source, amount);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (site != null && level() instanceof ServerLevel level) {
			HellState state = HellState.get(level.getServer());
			state.fraudSpent.add(site);
			state.setDirty();
		}
	}

	@Override
	public boolean isPushable() {
		return !dormant() && super.isPushable();
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("dormant", dormant());
		if (site != null) {
			output.putString("site", site);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		entityData.set(DORMANT, input.getBooleanOr("dormant", false));
		site = input.getStringOr("site", null);
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return dormant() ? null : SoundEvents.RAVAGER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR;
	}

	@Override
	protected int deathTicks() {
		return MimicAnim.DEATH.ticks + 4;
	}
}
