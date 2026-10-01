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
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.util.Feedback;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Paradiso's relics:
 * <ul>
 *     <li>the <b>Halo</b> (the Seraph's gift): a golden helmet; Regeneration while you are nearly whole;</li>
 *     <li><b>Seraph Wings</b> (end city treasure): elytra that never wear out; sneak while gliding for a rush
 *     of wind, every 10 s;</li>
 *     <li><b>Beatrice's Rose</b> (the Celestial Rose, once per soul): right-click to be made whole and
 *     cleansed, once an hour.</li>
 * </ul>
 */
public final class Relics {
	private Relics() {
	}

	public static final String KIND = "relic";

	public enum Relic {
		HALO("halo", "Halo", Items.GOLDEN_HELMET, ChatFormatting.GOLD, List.of(
				"From the Seraph. While your health is nearly full,",
				"it gives you Regeneration.")),
		SERAPH_WINGS("seraph_wings", "Seraph Wings", Items.ELYTRA, ChatFormatting.AQUA, List.of(
				"Elytra that never break. While gliding, sneak",
				"for a speed boost (every 10 s).")),
		BEATRICES_ROSE("beatrices_rose", "Beatrice's Rose", Items.POISONOUS_POTATO, ChatFormatting.LIGHT_PURPLE, List.of(
				"Right-click: full health and food, and all",
				"effects removed. Once an hour."));

		public final String id;
		final String title;
		final Item base;
		final ChatFormatting color;
		final List<String> lore;

		Relic(String id, String title, Item base, ChatFormatting color, List<String> lore) {
			this.id = id;
			this.title = title;
			this.base = base;
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

	/** Must match the loot tables that hand out relics (tools/gen_models.py WINGS). */
	public static ItemStack create(Relic relic) {
		ItemStack stack = new ItemStack(relic.base);
		if (relic == Relic.BEATRICES_ROSE) {
			stack.remove(DataComponents.FOOD);
			stack.remove(DataComponents.CONSUMABLE);
		}
		CompoundTag tag = new CompoundTag();
		tag.putString(BloodItems.KEY, KIND);
		tag.putString(KIND, relic.id);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ITEM_MODEL, HellcraftMod.id(relic.id));
		stack.set(DataComponents.ITEM_NAME, Component.literal(relic.title).withStyle(relic.color, ChatFormatting.BOLD));
		List<Component> lore = new ArrayList<>();
		for (String line : relic.lore) {
			lore.add(Component.literal(line).withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(false)));
		}
		stack.set(DataComponents.LORE, new ItemLore(lore));
		stack.set(DataComponents.RARITY, Rarity.EPIC);
		if (relic != Relic.BEATRICES_ROSE) {
			stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
		}
		if (relic == Relic.HALO) {
			stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		return stack;
	}

	@Nullable
	public static Relic of(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
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
			rose(sp);
			return InteractionResult.SUCCESS;
		});
	}

	private static void rose(ServerPlayer player) {
		long now = player.level().getGameTime();
		long ready = ROSE_READY.getOrDefault(player.getUUID(), 0L);
		if (now < ready) {
			player.sendOverlayMessage(Component.literal("The rose is on cooldown (" + (ready - now) / 1200 + " min).").withStyle(ChatFormatting.LIGHT_PURPLE));
			return;
		}
		ROSE_READY.put(player.getUUID(), now + 72000L);
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
