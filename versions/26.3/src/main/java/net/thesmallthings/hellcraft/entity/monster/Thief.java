package net.thesmallthings.hellcraft.entity.monster;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.entity.boss.gen.ThiefAnim;

import java.util.ArrayList;
import java.util.List;

/**
 * A thief (Malebolge's monster, tools/art/thief.py): a soul bound with serpents who sidles up, snatches something from
 * your pack and runs for it. It keeps what it took until it is killed, so chase it down. With its hands full it only
 * runs; empty-handed it comes back for more.
 */
public class Thief extends SoulMonster implements InventoryCarrier {
	private final SimpleContainer loot = new SimpleContainer(4);

	public Thief(EntityType<? extends Monster> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.32)
				.add(Attributes.ATTACK_DAMAGE, 2.0)
				.add(Attributes.FOLLOW_RANGE, 24.0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25, false));
		goalSelector.addGoal(6, new RandomStrollGoal(this, 0.8));
		goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0f));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	public SimpleContainer getInventory() {
		return loot;
	}

	public boolean carrying() {
		return !loot.isEmpty();
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (!carrying()) {
			return;
		}
		// off and away: keep its distance from everyone
		setTarget(null);
		Player near = level.getNearestPlayer(this, 20);
		if (near != null && (getNavigation().isDone() || tickCount % 20 == 0)) {
			Vec3 away = position().subtract(near.position()).multiply(1, 0, 1);
			away = away.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : away.normalize();
			Vec3 to = position().add(away.scale(12));
			getNavigation().moveTo(to.x, to.y, to.z, 1.5);
		}
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit && !carrying() && target instanceof ServerPlayer player) {
			snatch(player);
		}
		return hit;
	}

	/** Takes one stack from the pack (not the hand it holds, not the armour). */
	private void snatch(ServerPlayer player) {
		Inventory inv = player.getInventory();
		List<Integer> slots = new ArrayList<>();
		for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
			if (i != inv.getSelectedSlot() && !inv.getItem(i).isEmpty()) {
				slots.add(i);
			}
		}
		if (slots.isEmpty()) {
			return;
		}
		int slot = slots.get(random.nextInt(slots.size()));
		ItemStack stolen = inv.removeItemNoUpdate(slot);
		loot.addItem(stolen);
		inv.setChanged();
		playSpecial();
		playSound(SoundEvents.ITEM_PICKUP, 1.0f, 0.6f);
		playSound(SoundEvents.SILVERFISH_AMBIENT, 1.0f, 0.6f);
		player.sendOverlayMessage(Component.literal("A thief snatched your " + stolen.getHoverName().getString() + "! Kill it to get it back.")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
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
		return !carrying() && super.removeWhenFarAway(distance);
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

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.SILVERFISH_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.VINDICATOR_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.VINDICATOR_DEATH;
	}

	@Override
	protected int deathTicks() {
		return ThiefAnim.DEATH.ticks + 4;
	}
}
