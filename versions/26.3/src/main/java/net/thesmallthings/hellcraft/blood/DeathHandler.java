package net.thesmallthings.hellcraft.blood;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.hazard.lucifer.LuciferManager;
import net.thesmallthings.hellcraft.world.Circle;
import net.thesmallthings.hellcraft.world.HellWorldgen;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.Zone;

import java.util.List;

/** Lifesteal: hearts change hands on death, the heartless become ghosts, and mobs bleed. */
public final class DeathHandler {
	private DeathHandler() {
	}

	public static final String REVENANT_TAG = "hellcraft_revenant";

	/** Fired before death; returns true to allow it. Used to raise a revenant before the gear drops. */
	public static boolean beforeDeath(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof ServerPlayer player) || player.isSpectator() || player.isCreative()) {
			return true;
		}
		if (!HellConfig.get().revenants || Hearts.soul(player).hearts > 1 || savedByTotem(player, source)) {
			return true;
		}
		raiseRevenant(player);
		return true;
	}

	private static boolean savedByTotem(ServerPlayer player, DamageSource source) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		return player.getMainHandItem().is(Items.TOTEM_OF_UNDYING) || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
	}

	public static void afterDeath(LivingEntity entity, DamageSource source) {
		if (entity instanceof ServerPlayer player) {
			onPlayerDeath(player, source);
		} else if (entity.level() instanceof ServerLevel level) {
			onMobDeath(level, entity, source);
		}
	}

	private static void onPlayerDeath(ServerPlayer player, DamageSource source) {
		if (player.isSpectator()) {
			return;
		}
		HellConfig config = HellConfig.get();
		HellState state = HellState.get(player.level().getServer());
		HellState.Soul soul = Hearts.soul(player);
		String name = player.getGameProfile().name();
		soul.hearts = Math.max(0, soul.hearts - 1);
		state.setDirty();

		LivingEntity credit = player.getKillCredit();
		if (credit instanceof ServerPlayer killer && killer != player && !killer.isSpectator()) {
			if (Hearts.add(killer, 1) > 0) {
				killer.sendSystemMessage(Component.literal("You drink " + name + "'s blood. +1 ❤").withStyle(ChatFormatting.DARK_RED));
			} else {
				BloodItems.give(killer, BloodItems.heart(1));
				killer.sendSystemMessage(Component.literal("Your veins are full; " + name + "'s heart is yours to keep.").withStyle(ChatFormatting.DARK_RED));
			}
		} else if (config.naturalDeathDropsHeart) {
			ItemEntity drop = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), BloodItems.heart(1));
			drop.setUnlimitedLifetime();
			player.level().addFreshEntity(drop);
		}

		if (soul.hearts <= 0) {
			soul.ghost = true;
			soul.deathSpot = new HellState.GlobalSpot(player.level().dimension(), player.blockPosition());
			state.setDirty();
			player.level().getServer().getPlayerList().broadcastSystemMessage(Component.literal("Hell is full. ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
					.append(Component.literal(name + " now walks the earth.").withStyle(ChatFormatting.RED)), false);
			// the ghost gets the same instructions when they respawn as one
			List<Component> howTo = Ghosts.howToRevive(player.level().getServer());
			for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
				if (other != player) {
					howTo.forEach(other::sendSystemMessage);
				}
			}
		}
	}

	private static void onMobDeath(ServerLevel level, LivingEntity entity, DamageSource source) {
		ServerPlayer killer = source.getEntity() instanceof ServerPlayer p ? p
				: entity.getKillCredit() instanceof ServerPlayer p2 ? p2 : null;
		if (killer == null) {
			return;
		}
		if (LuciferManager.isLucifer(entity)) {
			// the fight hands out its own rewards
			return;
		}
		if (entity instanceof WitherBoss) {
			BloodItems.give(killer, BloodItems.heart(1));
			return;
		}
		if (entity instanceof Warden) {
			BloodItems.give(killer, BloodItems.heart(1));
			return;
		}
		if (entity instanceof EnderDragon) {
			BloodItems.give(killer, BloodItems.heart(2));
			return;
		}
		if (entity.entityTags().contains(REVENANT_TAG)) {
			entity.spawnAtLocation(level, BloodItems.fragment(3));
			return;
		}
		if (!(entity instanceof Enemy)) {
			return;
		}
		int depth = depthAt(level, entity);
		HellConfig config = HellConfig.get();
		double chance = config.fragmentChanceBase + config.fragmentChancePerDepth * depth;
		if (level.getRandom().nextDouble() < chance) {
			entity.spawnAtLocation(level, BloodItems.fragment(1));
		}
	}

	/** Circle depth of an entity's position: 1-9 in the Inferno, 5 in the Nether, 9 in the End. */
	public static int depthAt(ServerLevel level, LivingEntity entity) {
		if (level.dimension() == Level.NETHER) {
			return 5;
		}
		if (level.dimension() == Level.END) {
			return 9;
		}
		if (!HellWorldgen.isInferno(level)) {
			return 1;
		}
		return InfernoGeometry.circleAt(entity.getX(), entity.getZ()).depth();
	}

	/** The dead walk the earth: the eliminated player's corpse rises, wearing their gear and face. */
	private static void raiseRevenant(ServerPlayer player) {
		ServerLevel level = player.level();
		EntityType<? extends Mob> type = EntityTypes.ZOMBIE;
		if (HellWorldgen.isInferno(level)) {
			Zone zone = InfernoGeometry.zoneAt(player.getX(), player.getZ());
			if (zone == Zone.STYX || zone == Zone.ACHERON || player.isInWater()) {
				type = EntityTypes.DROWNED;
			} else if (zone == Zone.BURNING_SANDS) {
				type = EntityTypes.HUSK;
			} else if (zone.circle() == Circle.TREACHERY) {
				type = EntityTypes.STRAY;
			}
		} else if (player.isInWater()) {
			type = EntityTypes.DROWNED;
		}
		Mob revenant = type.create(level, EntitySpawnReason.EVENT);
		if (revenant == null) {
			return;
		}
		String name = player.getGameProfile().name();
		revenant.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0f);
		revenant.setCustomName(Component.literal(name).withStyle(ChatFormatting.DARK_RED));
		revenant.setCustomNameVisible(true);
		revenant.setPersistenceRequired();
		revenant.addTag(REVENANT_TAG);

		for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND}) {
			ItemStack stack = player.getItemBySlot(slot);
			revenant.setItemSlot(slot, stack.copy());
			revenant.setDropChance(slot, 2.0f);
			player.setItemSlot(slot, ItemStack.EMPTY);
		}
		ItemStack head = new ItemStack(Items.PLAYER_HEAD);
		head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(player.getGameProfile()));
		revenant.setItemSlot(EquipmentSlot.HEAD, head);
		revenant.setDropChance(EquipmentSlot.HEAD, 2.0f);

		AttributeInstance health = revenant.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.setBaseValue(health.getBaseValue() * 2.0);
			revenant.setHealth(revenant.getMaxHealth());
		}
		AttributeInstance follow = revenant.getAttribute(Attributes.FOLLOW_RANGE);
		if (follow != null) {
			follow.setBaseValue(48.0);
		}
		level.addFreshEntity(revenant);

		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
		if (bolt != null) {
			bolt.snapTo(player.getX(), player.getY(), player.getZ());
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
		}
	}
}
