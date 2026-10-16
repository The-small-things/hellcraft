package net.thesmallthings.hellcraft.blood;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.hazard.BossRules;
import net.thesmallthings.hellcraft.hazard.CircleHazards;
import net.thesmallthings.hellcraft.hazard.Danger;
import net.thesmallthings.hellcraft.net.DashPayload;
import net.thesmallthings.hellcraft.util.Feedback;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Hellcraft's own enchantments (data/hellcraft/enchantment, written by tools/gen_enchants.py), each found in one circle
 * only (tools/gen_loot.py). Windstep (boots, faster walking) and Quickening (weapons, faster blows) are attributes in the
 * data; the rest is here:
 * <ul>
 *     <li><b>Sidestep</b> (leggings, Wrath): the Dash key throws you a few blocks the way you're moving (back, if you
 *     stand still), and for a moment a boss's blow passes through you. Cooldown 3 / 2.25 / 1.5 s.</li>
 *     <li><b>Steadfast</b> (chestplate, Heresy): bosses (the guardians, Lucifer and what they bring) hurt you 7% less
 *     a level.</li>
 *     <li><b>Judgement</b> (weapons, Violence): your blows hurt bosses 1.5 more a level.</li>
 * </ul>
 */
public final class LayerEnchants {
	private LayerEnchants() {
	}

	public static final ResourceKey<Enchantment> WINDSTEP = key("windstep");
	public static final ResourceKey<Enchantment> QUICKENING = key("quickening");
	public static final ResourceKey<Enchantment> SIDESTEP = key("sidestep");
	public static final ResourceKey<Enchantment> STEADFAST = key("steadfast");
	public static final ResourceKey<Enchantment> JUDGEMENT = key("judgement");

	private static final int HINT_DASH = 39;
	/** How long a dash lets a boss's blow pass through you (ticks). */
	private static final int DODGE_TICKS = 7;
	private static final Map<UUID, Long> READY = new HashMap<>();
	private static final Map<UUID, Long> DODGING = new HashMap<>();

	private static ResourceKey<Enchantment> key(String id) {
		return ResourceKey.create(Registries.ENCHANTMENT, HellcraftMod.id(id));
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(DashPayload.TYPE, DashPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(DashPayload.TYPE, (payload, context) -> dash(context.player(), payload.x(), payload.z()));
		// a boss's blow passes through someone mid-dash
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayer p && dodging(p) && fromBoss(source)));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			READY.remove(entity.getUUID());
			DODGING.remove(entity.getUUID());
		});
	}

	/** The level of one of these enchantments the entity has on (its best piece). */
	public static int level(LivingEntity entity, ResourceKey<Enchantment> key) {
		Optional<Holder.Reference<Enchantment>> holder = entity.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key);
		return holder.map(h -> EnchantmentHelper.getEnchantmentLevel(h, entity)).orElse(0);
	}

	// ------------------------------------------------------------------ Sidestep

	private static void dash(ServerPlayer player, float x, float z) {
		if (!player.isAlive() || player.isSpectator() || player.isPassenger() || player.isFallFlying()) {
			return;
		}
		int lvl = level(player, SIDESTEP);
		if (lvl <= 0) {
			CircleHazards.hintOnce(player, HINT_DASH, "To dash you need leggings enchanted with Sidestep. Wrath's watchtower caches, in the "
					+ "Fen of Styx, sometimes hold the book.");
			return;
		}
		long now = player.level().getGameTime();
		if (now < READY.getOrDefault(player.getUUID(), 0L)) {
			return;
		}
		Vec3 dir = new Vec3(x, 0, z);
		if (dir.lengthSqr() < 1.0e-4) {
			// standing still: a hop back
			double yaw = Math.toRadians(player.getYRot());
			dir = new Vec3(Math.sin(yaw), 0, -Math.cos(yaw));
		}
		dir = dir.normalize();
		double speed = 0.9 + 0.15 * lvl;
		player.push(dir.x * speed, player.onGround() ? 0.18 : 0.05, dir.z * speed);
		Feedback.syncMotion(player);
		READY.put(player.getUUID(), now + Math.round(60 * (1.0 - 0.25 * (lvl - 1))));
		DODGING.put(player.getUUID(), now + DODGE_TICKS);
		ServerLevel level = player.level();
		level.playSound(null, player.blockPosition(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.8f, 1.4f);
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.3, player.getZ(), 8, 0.3, 0.1, 0.3, 0.02);
	}

	/** Mid-dash: a boss's blow passes through. */
	public static boolean dodging(Player player) {
		Long until = DODGING.get(player.getUUID());
		return until != null && player.level().getGameTime() <= until;
	}

	private static boolean fromBoss(DamageSource source) {
		Entity attacker = source.getEntity();
		return attacker != null && BossRules.fromBoss(attacker) || source.is(Danger.JUDGEMENT);
	}

	// ------------------------------------------------------------------ Steadfast and Judgement (BossRules.adjust)

	/** How much of a boss's blow reaches this player through Steadfast. */
	public static float bossDamageTaken(LivingEntity victim) {
		int lvl = level(victim, STEADFAST);
		return Math.max(0.5f, 1.0f - 0.07f * lvl);
	}

	/** What Judgement adds to a blow at a boss. */
	public static float bossDamageDealt(LivingEntity attacker) {
		return 1.5f * level(attacker, JUDGEMENT);
	}

	/** /hellcraft gear test: the blood gear is real items (old marked ones still count), and the circles' enchantments loaded. */
	public static String selfTest(net.minecraft.server.MinecraftServer server) {
		StringBuilder out = new StringBuilder("Gear test:");
		boolean real = true;
		for (HellWeapons.Weapon w : HellWeapons.Weapon.values()) {
			real &= HellWeapons.of(HellWeapons.create(w)) == w && HellWeapons.of(new net.minecraft.world.item.ItemStack(HellItems.weapon(w, true))) == w
					&& HellWeapons.infernal(new net.minecraft.world.item.ItemStack(HellItems.weapon(w, true)));
		}
		for (BloodArmour.Piece p : BloodArmour.Piece.values()) {
			real &= BloodArmour.of(BloodArmour.create(p)) == p && BloodArmour.of(new net.minecraft.world.item.ItemStack(HellItems.armour(p, true))) == p;
		}
		out.append(" real items=").append(real);
		// one made before 3.0: a vanilla sword marked with custom data
		net.minecraft.world.item.ItemStack old = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD);
		net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
		tag.putString(BloodItems.KEY, HellWeapons.KIND);
		tag.putString(HellWeapons.KIND, HellWeapons.Weapon.BLOODLETTER.id);
		old.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
		out.append(", old ones count=").append(HellWeapons.of(old) == HellWeapons.Weapon.BLOODLETTER);
		var registry = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		int found = 0;
		for (ResourceKey<Enchantment> k : java.util.List.of(WINDSTEP, QUICKENING, SIDESTEP, STEADFAST, JUDGEMENT)) {
			found += registry.get(k).isPresent() ? 1 : 0;
		}
		out.append("; ").append(found).append(" circle enchantments");
		return out.toString();
	}
}
