package net.thesmallthings.hellcraft.entity.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.InteractGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.LookAtTradingPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.TradeWithPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.thesmallthings.hellcraft.world.Shades;
import org.jetbrains.annotations.Nullable;

/**
 * A shade of Limbo (Shades): one of the great souls of old in the Noble Castle. It floats about its spot on the
 * castle's mound, and when you right-click it, it speaks (a plain tip, in its own voice), hands you a Codex page the
 * first time if it has one, and opens its trades. It can't be hurt. Its body is tools/art/shade.py.
 */
public class Shade extends AbstractVillager {
	private static final EntityDataAccessor<Integer> WHO = SynchedEntityData.defineId(Shade.class, EntityDataSerializers.INT);

	public final AnimationState idle = new AnimationState();
	public final AnimationState talk = new AnimationState();

	public Shade(EntityType<? extends AbstractVillager> type, Level level) {
		super(type, level);
		setPermanentlyInvulnerable(true);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder attributes() {
		return Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.5).add(Attributes.FOLLOW_RANGE, 16.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(WHO, 0);
	}

	/** Which shade this is (an index into Shades.ALL). */
	public int who() {
		return entityData.get(WHO);
	}

	public void setWho(int who) {
		entityData.set(WHO, who);
	}

	public Shades.Who whoIs() {
		return Shades.ALL.get(Math.floorMod(who(), Shades.ALL.size()));
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new TradeWithPlayerGoal(this));
		goalSelector.addGoal(1, new LookAtTradingPlayerGoal(this));
		goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 0.35));
		goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.3));
		goalSelector.addGoal(9, new InteractGoal(this, Player.class, 4.0f, 1.0f));
		goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 8.0f));
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (!isAlive() || isTrading() || hand != InteractionHand.MAIN_HAND) {
			return super.mobInteract(player, hand);
		}
		if (player instanceof ServerPlayer sp && level() instanceof ServerLevel level) {
			level.broadcastEntityEvent(this, EntityEvent.START_ATTACKING);
			Shades.speak(this, sp);
			if (!getOffers().isEmpty()) {
				setTradingPlayer(player);
				openTradingScreen(player, getDisplayName(), 1);
			}
		}
		return InteractionResult.SUCCESS_SERVER;
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == EntityEvent.START_ATTACKING) {
			talk.start(tickCount);
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			idle.animateWhen(isAlive() && walkAnimation.speed() < 0.1f, tickCount);
		}
	}

	@Override
	protected void updateTrades(ServerLevel level) {
		getOffers().addAll(whoIs().trades().apply(level));
	}

	@Override
	protected void rewardTradeXp(MerchantOffer offer) {
		if (offer.shouldRewardExp()) {
			level().addFreshEntity(new ExperienceOrb(level(), getX(), getY() + 0.5, getZ(), 3 + random.nextInt(4)));
		}
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return null;
	}

	@Override
	public boolean showProgressBar() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected SoundEvent getTradeUpdatedSound(boolean validTrade) {
		return validTrade ? SoundEvents.ALLAY_ITEM_GIVEN : SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM;
	}

	@Override
	public SoundEvent getNotifyTradeSound() {
		return SoundEvents.ALLAY_ITEM_GIVEN;
	}

	/** Where it lingers: its spot in the castle, and how far it drifts from it. */
	public void home(BlockPos spot, int radius) {
		setHomeTo(spot, radius);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("who", who());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		setWho(input.getIntOr("who", 0));
	}
}
