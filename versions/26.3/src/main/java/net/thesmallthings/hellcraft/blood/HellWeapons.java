package net.thesmallthings.hellcraft.blood;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
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
 * Hell weapons: vanilla tools reforged with blood (recipes in data/hellcraft/recipe), each with two
 * prices for power:
 * <ul>
 *     <li><b>Blood Fragments</b>, spent from the inventory automatically, for a modest boost;</li>
 *     <li>a <b>Blood Oath</b> (sneak + right-click): one of your hearts, forever, for a minute of great
 *     power. Never below {@link #OATH_MIN_HEARTS} hearts, so an oath can't make you a ghost.</li>
 * </ul>
 */
public final class HellWeapons {
	private HellWeapons() {
	}

	public static final String KIND = "weapon";
	public static final int OATH_MIN_HEARTS = 4;
	private static final int OATH_TICKS = 60 * 20;
	private static final int FRENZY_TICKS = 15 * 20;
	private static final int FRENZY_COOLDOWN = 30 * 20;
	private static final int FRENZY_COST = 3;

	public enum Weapon {
		BLOODLETTER("bloodletter", "Bloodletter", Items.IRON_SWORD, Rarity.RARE, List.of(
				"Each hit spends 1 Blood Fragment: +4 damage and bleeding.",
				"Blood Oath: +10 damage, deep bleeding, each hit heals 1❤.")),
		REAPER("reaper_of_minos", "Reaper of Minos", Items.DIAMOND_HOE, Rarity.EPIC, List.of(
				"Each hit spends 1 Blood Fragment: cleaves everything",
				"within 3 blocks for 5 damage.",
				"Blood Oath: cleaves within 5 blocks for 12, slows and drags them in.")),
		TITHE_AXE("tithe_axe", "Tithe Axe", Items.DIAMOND_AXE, Rarity.EPIC, List.of(
				"Right-click: pay " + FRENZY_COST + " Blood Fragments for a Blood Frenzy",
				"(Strength and Speed for 15 s).",
				"Blood Oath: Strength III, Speed II, Resistance, hits heal 1❤."));

		public final String id;
		final String title;
		final Item base;
		final Rarity rarity;
		final List<String> lore;

		Weapon(String id, String title, Item base, Rarity rarity, List<String> lore) {
			this.id = id;
			this.title = title;
			this.base = base;
			this.rarity = rarity;
			this.lore = lore;
		}

		@Nullable
		public static Weapon byId(String id) {
			for (Weapon w : values()) {
				if (w.id.equals(id) || w.name().toLowerCase(Locale.ROOT).equals(id)) {
					return w;
				}
			}
			return null;
		}
	}

	/** Players under a Blood Oath: UUID -> game time it ends. */
	private static final Map<UUID, Long> OATHS = new HashMap<>();
	private static final Map<UUID, Long> FRENZY_READY = new HashMap<>();
	/** Set while bonus damage is being dealt, so it doesn't trigger itself. */
	private static boolean striking;

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(HellWeapons::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> OATHS.remove(entity.getUUID()));
	}

	// ------------------------------------------------------------------ the items

	/** Must match the results in data/hellcraft/recipe/*.json. */
	public static ItemStack create(Weapon weapon) {
		ItemStack stack = new ItemStack(weapon.base);
		CompoundTag tag = new CompoundTag();
		tag.putString(BloodItems.KEY, KIND);
		tag.putString(KIND, weapon.id);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ITEM_MODEL, HellcraftMod.id(weapon.id));
		stack.set(DataComponents.ITEM_NAME, Component.literal(weapon.title).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
		List<Component> lore = new ArrayList<>();
		for (String line : weapon.lore) {
			lore.add(Component.literal(line).withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(false)));
		}
		lore.add(Component.literal("Blood Oath = sneak + right-click: costs 1 max heart, lasts 60 s.")
				.withStyle(s -> s.withColor(ChatFormatting.RED).withItalic(false)));
		stack.set(DataComponents.LORE, new ItemLore(lore));
		stack.set(DataComponents.RARITY, weapon.rarity);
		if (weapon == Weapon.REAPER) {
			// a scythe, not a garden tool: heavy and slow
			stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
					.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(HellcraftMod.id("reaper_damage"), 7.0, AttributeModifier.Operation.ADD_VALUE),
							EquipmentSlotGroup.MAINHAND)
					.add(Attributes.ATTACK_SPEED, new AttributeModifier(HellcraftMod.id("reaper_speed"), -3.0, AttributeModifier.Operation.ADD_VALUE),
							EquipmentSlotGroup.MAINHAND)
					.build());
		}
		return stack;
	}

	@Nullable
	public static Weapon of(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		CompoundTag tag = data.copyTag();
		return KIND.equals(tag.getStringOr(BloodItems.KEY, "")) ? Weapon.byId(tag.getStringOr(KIND, "")) : null;
	}

	// ------------------------------------------------------------------ oaths and frenzies

	public static boolean underOath(ServerPlayer player) {
		Long until = OATHS.get(player.getUUID());
		return until != null && until > player.level().getGameTime();
	}

	/** Right-click with a hell weapon in the main hand (air). Returns PASS if the click isn't ours. */
	public static InteractionResult use(ServerPlayer player, ItemStack stack) {
		Weapon weapon = of(stack);
		if (weapon == null) {
			return InteractionResult.PASS;
		}
		if (player.isShiftKeyDown()) {
			swearOath(player, weapon);
			return InteractionResult.SUCCESS;
		}
		if (weapon == Weapon.TITHE_AXE) {
			frenzy(player);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	private static void swearOath(ServerPlayer player, Weapon weapon) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (underOath(player)) {
			long left = (OATHS.get(player.getUUID()) - now) / 20;
			player.sendOverlayMessage(Component.literal("Your Blood Oath still burns (" + left + " s).").withStyle(ChatFormatting.RED));
			return;
		}
		HellState.Soul soul = Hearts.soul(player);
		if (soul.hearts - 1 < OATH_MIN_HEARTS) {
			player.sendSystemMessage(Component.literal("You have too little blood left to swear an oath (it needs you to keep "
					+ OATH_MIN_HEARTS + "❤).").withStyle(ChatFormatting.RED));
			return;
		}
		Hearts.add(player, -1);
		OATHS.put(player.getUUID(), now + OATH_TICKS);
		player.sendSystemMessage(Component.literal("You swore a Blood Oath on the " + weapon.title + ": −1 ❤ (now "
				+ Hearts.soul(player).hearts + "). Its full power is yours for 60 s.").withStyle(ChatFormatting.DARK_RED));
		if (weapon == Weapon.TITHE_AXE) {
			player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, OATH_TICKS, 2));
			player.addEffect(new MobEffectInstance(MobEffects.SPEED, OATH_TICKS, 1));
			player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, OATH_TICKS, 0));
		}
		bleed(level, player, 40);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.6f, 1.6f);
	}

	private static void frenzy(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		long ready = FRENZY_READY.getOrDefault(player.getUUID(), 0L);
		if (now < ready) {
			player.sendOverlayMessage(Component.literal("The axe is still sated (" + (ready - now + 19) / 20 + " s).").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!BloodItems.takeFragments(player, FRENZY_COST)) {
			player.sendOverlayMessage(Component.literal("A Blood Frenzy costs " + FRENZY_COST + " Blood Fragments.").withStyle(ChatFormatting.RED));
			return;
		}
		FRENZY_READY.put(player.getUUID(), now + FRENZY_COOLDOWN);
		player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, FRENZY_TICKS, 0));
		player.addEffect(new MobEffectInstance(MobEffects.SPEED, FRENZY_TICKS, 0));
		player.sendOverlayMessage(Component.literal("Blood Frenzy!").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
		bleed(level, player, 20);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.8f, 1.2f);
	}

	// ------------------------------------------------------------------ hits

	private static void afterDamage(LivingEntity target, DamageSource source, float base, float taken, boolean blocked) {
		if (striking || blocked || !(source.getEntity() instanceof ServerPlayer player) || source.getDirectEntity() != player || target == player) {
			return;
		}
		Weapon weapon = of(player.getMainHandItem());
		if (weapon == null) {
			return;
		}
		ServerLevel level = player.level();
		boolean oath = underOath(player);
		striking = true;
		try {
			switch (weapon) {
				case BLOODLETTER -> {
					if (oath) {
						extra(level, player, target, 10.0f);
						target.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
						player.heal(2.0f);
					} else if (spendFragment(player)) {
						extra(level, player, target, 4.0f);
						target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
					}
				}
				case REAPER -> {
					if (oath) {
						cleave(level, player, target, 5.0, 12.0f, true);
					} else if (spendFragment(player)) {
						cleave(level, player, target, 3.0, 5.0f, false);
					}
				}
				case TITHE_AXE -> {
					if (oath) {
						player.heal(2.0f);
					}
				}
			}
		} finally {
			striking = false;
		}
	}

	private static boolean spendFragment(ServerPlayer player) {
		if (BloodItems.takeFragments(player, 1)) {
			bleed(player.level(), player, 6);
			return true;
		}
		player.sendOverlayMessage(Component.literal("No blood to spend: carry Blood Fragments, or sneak + right-click to swear a Blood Oath.")
				.withStyle(ChatFormatting.GRAY));
		return false;
	}

	/** Extra damage on top of the swing (credited to the player, so kills still count for lifesteal). */
	private static void extra(ServerLevel level, ServerPlayer player, LivingEntity target, float amount) {
		if (!target.isAlive()) {
			return;
		}
		target.setInvulnerableTime(0);
		target.hurtServer(level, level.damageSources().playerAttack(player), amount);
		level.sendParticles(BloodAltar.BLOOD, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 12, 0.3, 0.3, 0.3, 0.0);
	}

	private static void cleave(ServerLevel level, ServerPlayer player, LivingEntity target, double radius, float damage, boolean drag) {
		List<LivingEntity> hit = level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(radius),
				e -> e != player && e != target && e.isAlive() && !e.isSpectator() && e.distanceTo(target) <= radius);
		for (LivingEntity e : hit) {
			e.setInvulnerableTime(0);
			e.hurtServer(level, level.damageSources().playerAttack(player), damage);
			if (drag) {
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
				Vec3 pull = player.position().subtract(e.position()).multiply(1, 0, 1);
				if (pull.lengthSqr() > 1.0e-4) {
					pull = pull.normalize().scale(0.7);
					e.push(pull.x, 0.15, pull.z);
					Feedback.syncMotion(e);
				}
			}
		}
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + 1.0, target.getZ(), (int) (radius * 3), radius / 2, 0.2, radius / 2, 0.0);
		level.sendParticles(BloodAltar.BLOOD, target.getX(), target.getY() + 0.8, target.getZ(), 30, radius / 2, 0.4, radius / 2, 0.0);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 0.6f);
	}

	/** A burst of blood from the one paying the price. */
	private static void bleed(ServerLevel level, ServerPlayer player, int count) {
		level.sendParticles(BloodAltar.BLOOD, player.getX(), player.getY() + 1.0, player.getZ(), count, 0.3, 0.5, 0.3, 0.0);
	}
}
