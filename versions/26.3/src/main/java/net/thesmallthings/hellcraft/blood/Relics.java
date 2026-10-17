package net.thesmallthings.hellcraft.blood;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.util.Feedback;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Paradiso's relics, real items since 3.0 ({@link HellItems#relic}; relics made before are vanilla items marked with
 * custom data, and still work):
 * <ul>
 *     <li>the <b>Halo</b> (the Seraph's gift): a golden circlet worn as a helmet; Regeneration while you are nearly
 *     whole;</li>
 *     <li><b>Seraph Wings</b> (Saturn's trial): elytra that never wear out; sneak while gliding for a rush of wind,
 *     every 10 s;</li>
 *     <li><b>Beatrice's Rose</b> (the Celestial Rose, once per soul): right-click to be made whole and
 *     cleansed, once an hour.</li>
 * </ul>
 */
public final class Relics {
	private Relics() {
	}

	public static final String KIND = "relic";

	public enum Relic {
		HALO("halo", "Halo", ChatFormatting.GOLD, List.of(
				"From the Seraph. While your health is nearly full,",
				"it gives you Regeneration.")),
		SERAPH_WINGS("seraph_wings", "Seraph Wings", ChatFormatting.AQUA, List.of(
				"Elytra that never break. While gliding, sneak",
				"for a speed boost (every 10 s).")),
		BEATRICES_ROSE("beatrices_rose", "Beatrice's Rose", ChatFormatting.LIGHT_PURPLE, List.of(
				"Right-click: full health and food, and all",
				"effects removed. Once an hour."));

		public final String id;
		final String title;
		final ChatFormatting color;
		final List<String> lore;

		Relic(String id, String title, ChatFormatting color, List<String> lore) {
			this.id = id;
			this.title = title;
			this.color = color;
			this.lore = lore;
		}

		@Nullable
		public static Relic byId(String id) {
			for (Relic r : values()) {
				if (r.id.equals(id) || r.name().toLowerCase(Locale.ROOT).equals(id)) {
					return r;
				}
			}
			return null;
		}
	}

	private static final Map<UUID, Long> WIND_READY = new HashMap<>();
	private static final Map<UUID, Long> ROSE_READY = new HashMap<>();
	private static int ticks;

	public static ItemStack create(Relic relic) {
		return new ItemStack(HellItems.relic(relic));
	}

	/** The item's name and lore, which the real items carry as components. */
	static Component name(Relic relic) {
		return Component.literal(relic.title).withStyle(relic.color, ChatFormatting.BOLD);
	}

	static ItemLore lore(Relic relic) {
		List<Component> lore = new ArrayList<>();
		for (String line : relic.lore) {
			lore.add(Component.literal(line).withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(false)));
		}
		return new ItemLore(lore);
	}

	@Nullable
	public static Relic of(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		Relic real = HellItems.relicOf(stack.getItem());
		if (real != null) {
			return real;
		}
		// a relic made before 3.0: a vanilla item marked with custom data
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		CompoundTag tag = data.copyTag();
		return KIND.equals(tag.getStringOr(BloodItems.KEY, "")) ? Relic.byId(tag.getStringOr(KIND, "")) : null;
	}

	public static void register() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (level.isClientSide() || !(player instanceof ServerPlayer sp) || of(stack) != Relic.BEATRICES_ROSE) {
				return InteractionResult.PASS;
			}
			rose(sp, stack);
			return InteractionResult.SUCCESS;
		});
	}

	private static void rose(ServerPlayer player, ItemStack stack) {
		long now = player.level().getGameTime();
		long ready = ROSE_READY.getOrDefault(player.getUUID(), 0L);
		if (now < ready) {
			player.sendOverlayMessage(Component.literal("The rose is on cooldown (" + (ready - now) / 1200 + " min).").withStyle(ChatFormatting.LIGHT_PURPLE));
			return;
		}
		ROSE_READY.put(player.getUUID(), now + 72000L);
		// (the item shows the hour going down, like an ender pearl's wait)
		player.getCooldowns().addCooldown(stack, 72000);
		player.removeAllEffects();
		player.clearFire();
		player.setTicksFrozen(0);
		player.setHealth(player.getMaxHealth());
		player.getFoodData().eat(20, 1.0f);
		player.level().sendParticles(ParticleTypes.CHERRY_LEAVES, player.getX(), player.getY() + 1.5, player.getZ(), 40, 0.6, 0.6, 0.6, 0.02);
		player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5f, 1.2f);
		player.sendSystemMessage(Component.literal("Beatrice's Rose: full health and food, and all effects removed.")
				.withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
	}

	/** The Halo's regeneration (once a second) and the Wings' rush of wind (every tick). */
	public static void tick(MinecraftServer server) {
		ticks++;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator()) {
				continue;
			}
			if (ticks % 20 == 0 && of(player.getItemBySlot(EquipmentSlot.HEAD)) == Relic.HALO
					&& player.getHealth() >= player.getMaxHealth() * 0.8f) {
				player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 50, 0, true, false, true));
			}
			if (player.isFallFlying() && player.isShiftKeyDown() && of(player.getItemBySlot(EquipmentSlot.CHEST)) == Relic.SERAPH_WINGS) {
				long now = player.level().getGameTime();
				if (now >= WIND_READY.getOrDefault(player.getUUID(), 0L)) {
					WIND_READY.put(player.getUUID(), now + 200L);
					Vec3 look = player.getLookAngle();
					player.push(look.x * 1.6, look.y * 1.6 + 0.2, look.z * 1.6);
					Feedback.syncMotion(player);
					player.level().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY(), player.getZ(), 20, 0.4, 0.4, 0.4, 0.05);
					player.level().playSound(null, player.blockPosition(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.8f, 1.6f);
				}
			}
		}
	}

	/** Hands a relic to a player (to their inventory, or at their feet if it's full). */
	public static void give(ServerPlayer player, Relic relic) {
		BloodItems.give(player, create(relic));
		player.sendSystemMessage(Component.literal("You receive " + relic.title + ".").withStyle(relic.color));
	}

	public static void forget(ServerPlayer player) {
		WIND_READY.remove(player.getUUID());
	}
}
