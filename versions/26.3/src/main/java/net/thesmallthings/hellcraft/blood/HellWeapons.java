package net.thesmallthings.hellcraft.blood;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.util.Feedback;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Hell weapons: vanilla tools reforged with Blood Fragments (recipes in data/hellcraft/recipe). They cost
 * nothing to swing:
 * <ul>
 *     <li>a <b>passive</b> on every fully charged hit (bleeding and healing, a cleave, a richer tithe);</li>
 *     <li><b>blood charge</b>: hits and kills fill it, and a right-click at 100% unleashes the weapon's
 *     <b>Blood Art</b>;</li>
 *     <li>a <b>Blood Oath</b> (sneak + right-click): pay 3 hearts of health now for a full charge and 30 s of
 *     the weapon's full power. Never below {@link #OATH_MIN_HEALTH} health, and a cooldown of 3 minutes.</li>
 * </ul>
 */
public final class HellWeapons {
	private HellWeapons() {
	}

	public static final String KIND = "weapon";
	public static final float OATH_MIN_HEALTH = 8.0f;
	private static final float OATH_PRICE = 6.0f;
	private static final int OATH_TICKS = 30 * 20;
	private static final int OATH_COOLDOWN = 180 * 20;
	private static final int FULL = 100;
	private static final int PER_HIT = 5;
	private static final int PER_KILL = 20;
	private static final int FRENZY_TICKS = 15 * 20;
	private static final int EXCAVATE_TICKS = 20 * 20;
	private static final int PER_ORE = 2;

	public enum Weapon {
		BLOODLETTER("bloodletter", "Bloodletter", Items.IRON_SWORD, Rarity.RARE, List.of(
				"Charged hits bleed your foe and heal you.",
				"Blood Art, Exsanguinate: lunge forward, cutting everything",
				"in your path (8 damage, deep bleeding, heals you).",
				"Blood Oath: +10 damage, and every hit heals 1❤.")),
		REAPER("reaper_of_minos", "Reaper of Minos", Items.DIAMOND_HOE, Rarity.EPIC, List.of(
				"Charged hits cleave everything within 3 blocks for 4.",
				"Blood Art, Harvest: reap everything within 5 blocks",
				"for 12, slowing them and dragging them in.",
				"Blood Oath: every hit is a Harvest.")),
		TITHE_AXE("tithe_axe", "Tithe Axe", Items.DIAMOND_AXE, Rarity.EPIC, List.of(
				"The tithe: kills with it drop Blood Fragments twice as often.",
				"Blood Art, Blood Frenzy: Strength II, Speed II and Haste II",
				"for 15 s.",
				"Blood Oath: Strength III, Speed II, Resistance, hits heal 1❤.")),
		BLOOD_PICKAXE("blood_pickaxe", "Blood Pickaxe", Items.DIAMOND_PICKAXE, Rarity.EPIC, List.of(
				"Veins bleed out: breaking an ore breaks the rest of its vein",
				"(sneak to mine just one). Ores you mine fill its blood too.",
				"Blood Art, Excavate: for 20 s it digs 3x3, with Haste II.",
				"Blood Oath: Excavate, Haste III and Night Vision."));

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
	private static final Map<UUID, Long> OATH_READY = new HashMap<>();
	/** Blood charge, 0-100, per player (spilled when they die). */
	private static final Map<UUID, Integer> CHARGE = new HashMap<>();
	/** Set while bonus damage is being dealt, so it doesn't trigger itself. */
	private static boolean striking;
	/** The Blood Pickaxe's Excavate: UUID -> game time it ends. */
	private static final Map<UUID, Long> EXCAVATE = new HashMap<>();
	/** Set while the Blood Pickaxe breaks extra blocks, so they don't set off more. */
	private static boolean mining;

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(HellWeapons::afterDamage);
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (player instanceof ServerPlayer sp && level instanceof ServerLevel server) {
				afterBreak(sp, server, pos, state);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			OATHS.remove(entity.getUUID());
			CHARGE.remove(entity.getUUID());
		});
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
		stack.set(DataComponents.LORE, lore(weapon, false));
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

	static ItemLore lore(Weapon weapon, boolean infernal) {
		List<Component> lore = new ArrayList<>();
		if (infernal) {
			lore.add(Component.literal("Infernal: forged anew in the Great Forge of Dis. Everything is stronger.")
					.withStyle(s -> s.withColor(ChatFormatting.GOLD).withItalic(false)));
		}
		for (String line : weapon.lore) {
			lore.add(Component.literal(line).withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(false)));
		}
		lore.add(Component.literal("Right-click at full blood: its Blood Art. Hits and kills fill it.")
				.withStyle(s -> s.withColor(ChatFormatting.RED).withItalic(false)));
		lore.add(Component.literal("Blood Oath = sneak + right-click: costs 3❤ of health, lasts 30 s.")
				.withStyle(s -> s.withColor(ChatFormatting.RED).withItalic(false)));
		return new ItemLore(lore);
	}

	/** Brings a weapon made by an older version up to date (its lore), keeping everything else. */
	static void refresh(ItemStack stack) {
		Weapon weapon = of(stack);
		if (weapon != null) {
			ItemLore lore = lore(weapon, infernal(stack));
			if (!lore.equals(stack.get(DataComponents.LORE))) {
				stack.set(DataComponents.LORE, lore);
			}
		}
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

	public static final String INFERNAL = "infernal";

	/** Infernal gear was reforged at the Hellforge (Hellforge.java): netherite, and everything stronger. */
	public static boolean infernal(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBooleanOr(INFERNAL, false);
	}

	/** How much stronger a weapon's powers are: 1, or 1.5 once infernal. */
	private static float power(ServerPlayer player) {
		return infernal(player.getMainHandItem()) ? 1.5f : 1.0f;
	}

	// ------------------------------------------------------------------ blood charge

	public static int charge(ServerPlayer player) {
		return CHARGE.getOrDefault(player.getUUID(), 0);
	}

	private static void addCharge(ServerPlayer player, int amount) {
		int before = charge(player);
		if (before >= FULL) {
			return;
		}
		int now = Math.min(FULL, before + amount);
		CHARGE.put(player.getUUID(), now);
		if (now >= FULL) {
			ItemStack held = player.getMainHandItem();
			if (of(held) != null) {
				held.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			}
			player.sendOverlayMessage(Component.literal("Your weapon brims with blood: right-click to unleash its Blood Art!")
					.withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
			player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6f, 1.8f);
		} else {
			player.sendOverlayMessage(meter(now));
		}
	}

	private static Component meter(int charge) {
		int bars = charge / 10;
		return Component.literal("Blood ").withStyle(ChatFormatting.DARK_RED)
				.append(Component.literal("▮".repeat(bars)).withStyle(ChatFormatting.RED))
				.append(Component.literal("▮".repeat(10 - bars)).withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.literal(" " + charge + "%").withStyle(ChatFormatting.GRAY));
	}

	/** Spends a full charge; the glow leaves every blood weapon the player carries. */
	private static void spend(ServerPlayer player) {
		CHARGE.put(player.getUUID(), 0);
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack s = player.getInventory().getItem(i);
			if (of(s) != null && s.has(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)) {
				s.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
			}
		}
	}

	/** A monster killed by a player: blood for the weapon in their hand. */
	public static void onKill(ServerPlayer killer, LivingEntity victim) {
		if (victim instanceof Enemy && of(killer.getMainHandItem()) != null) {
			addCharge(killer, Math.round(PER_KILL * (infernal(killer.getMainHandItem()) ? 1.25f : 1.0f)));
		}
	}

	/** Kills with the Tithe Axe drop Blood Fragments twice as often. */
	public static double fragmentMultiplier(ServerPlayer killer) {
		ItemStack held = killer.getMainHandItem();
		return of(held) == Weapon.TITHE_AXE ? (infernal(held) ? 3.0 : 2.0) : 1.0;
	}

	// ------------------------------------------------------------------ oaths and arts

	public static boolean underOath(ServerPlayer player) {
		Long until = OATHS.get(player.getUUID());
		return until != null && until > player.level().getGameTime();
	}

	/** Right-click with a hell weapon in the main hand. Returns PASS if the click isn't ours. */
	public static InteractionResult use(ServerPlayer player, ItemStack stack) {
		Weapon weapon = of(stack);
		if (weapon == null) {
			return InteractionResult.PASS;
		}
		if (player.isShiftKeyDown()) {
			swearOath(player, weapon);
			return InteractionResult.SUCCESS;
		}
		if (charge(player) < FULL) {
			player.sendOverlayMessage(meter(charge(player)).copy().append(Component.literal("  (hits and kills fill it)").withStyle(ChatFormatting.DARK_GRAY)));
			// a sword or axe still blocks/strips as usual when there's no art to use
			return InteractionResult.PASS;
		}
		spend(player);
		switch (weapon) {
			case BLOODLETTER -> exsanguinate(player, infernal(stack) ? 9.0 : 6.0, infernal(stack) ? 11.0f : 8.0f);
			case REAPER -> harvest(player, infernal(stack) ? 7.0 : 5.0, infernal(stack) ? 15.0f : 12.0f);
			case TITHE_AXE -> frenzy(player, infernal(stack) ? FRENZY_TICKS * 4 / 3 : FRENZY_TICKS);
			case BLOOD_PICKAXE -> excavate(player, infernal(stack) ? EXCAVATE_TICKS * 3 / 2 : EXCAVATE_TICKS);
		}
		return InteractionResult.SUCCESS;
	}

	private static void swearOath(ServerPlayer player, Weapon weapon) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (underOath(player)) {
			long left = (OATHS.get(player.getUUID()) - now) / 20;
			player.sendOverlayMessage(Component.literal("Your Blood Oath still burns (" + left + " s).").withStyle(ChatFormatting.RED));
			return;
		}
		long ready = OATH_READY.getOrDefault(player.getUUID(), 0L);
		if (now < ready) {
			player.sendOverlayMessage(Component.literal("Your blood has not yet recovered from the last oath (" + (ready - now + 19) / 20 + " s).")
					.withStyle(ChatFormatting.GRAY));
			return;
		}
		if (player.getHealth() <= OATH_MIN_HEALTH) {
			player.sendOverlayMessage(Component.literal("You have too little blood left to swear an oath (it needs more than 4❤ of health).")
					.withStyle(ChatFormatting.RED));
			return;
		}
		player.setHealth(player.getHealth() - OATH_PRICE);
		OATHS.put(player.getUUID(), now + OATH_TICKS);
		OATH_READY.put(player.getUUID(), now + OATH_COOLDOWN);
		addCharge(player, FULL);
		player.sendSystemMessage(Component.literal("You swore a Blood Oath on the " + weapon.title + ": its full power is yours for 30 s.")
				.withStyle(ChatFormatting.DARK_RED));
		if (BloodArmour.worn(player) >= 4) {
			// a full set of blood armour hardens under an oath
			player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, OATH_TICKS, 0));
		}
		if (weapon == Weapon.BLOOD_PICKAXE) {
			player.addEffect(new MobEffectInstance(MobEffects.HASTE, OATH_TICKS, 2));
			player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, OATH_TICKS + 200, 0));
		}
		if (weapon == Weapon.TITHE_AXE) {
			player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, OATH_TICKS, 2));
			player.addEffect(new MobEffectInstance(MobEffects.SPEED, OATH_TICKS, 1));
			player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, OATH_TICKS, 0));
		}
		bleed(level, player, 40);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.6f, 1.6f);
	}

	/** Bloodletter: a lunge that cuts through everything in its path. */
	private static void exsanguinate(ServerPlayer player, double reach, float damage) {
		ServerLevel level = player.level();
		Vec3 look = player.getLookAngle().multiply(1, 0, 1);
		if (look.lengthSqr() < 1.0e-4) {
			look = new Vec3(1, 0, 0);
		}
		look = look.normalize();
		Vec3 from = player.position();
		Vec3 to = from.add(look.scale(reach));
		player.push(look.x * reach * 0.3, 0.2, look.z * reach * 0.3);
		Feedback.syncMotion(player);
		int cut = 0;
		striking = true;
		try {
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(1.5, 1.5, 1.5), e -> canReap(player, e))) {
				if (distanceToSegment(e.position(), from, to) <= 1.8) {
					e.setInvulnerableTime(0);
					e.hurtServer(level, level.damageSources().playerAttack(player), damage);
					e.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
					level.sendParticles(BloodAltar.BLOOD, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 16, 0.3, 0.3, 0.3, 0.0);
					cut++;
				}
			}
		} finally {
			striking = false;
		}
		player.heal(2.0f * cut);
		for (int i = 0; i <= (int) reach; i++) {
			Vec3 p = from.add(look.scale(i));
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y + 1.0, p.z, 1, 0, 0, 0, 0);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2f, 0.5f);
		player.sendOverlayMessage(Component.literal("Exsanguinate!").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
	}

	private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double t = Math.max(0, Math.min(1, p.subtract(a).dot(ab) / Math.max(1.0e-6, ab.lengthSqr())));
		return p.distanceTo(a.add(ab.scale(t)));
	}

	/** Reaper: everything around you. */
	private static void harvest(ServerPlayer player, double radius, float damage) {
		cleave(player.level(), player, player, radius, damage, true);
		player.sendOverlayMessage(Component.literal("Harvest!").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
	}

	/** Tithe Axe. */
	private static void frenzy(ServerPlayer player, int ticks) {
		ServerLevel level = player.level();
		player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, ticks, 1));
		player.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, 1));
		player.addEffect(new MobEffectInstance(MobEffects.HASTE, ticks, 1));
		player.sendOverlayMessage(Component.literal("Blood Frenzy!").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
		bleed(level, player, 30);
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
		// the passives (and the charge) only come from a (nearly) fully charged swing, so spam-clicking gains nothing
		boolean charged = base >= 0.9f * (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
		if (!charged && !oath) {
			return;
		}
		if (target instanceof Enemy || target instanceof Player) {
			addCharge(player, Math.round(PER_HIT * (infernal(player.getMainHandItem()) ? 1.25f : 1.0f)));
		}
		float power = power(player);
		striking = true;
		try {
			switch (weapon) {
				case BLOODLETTER -> {
					if (oath) {
						extra(level, player, target, 10.0f);
						target.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
						player.heal(2.0f);
					} else {
						target.addEffect(new MobEffectInstance(MobEffects.WITHER, Math.round(60 * power), 0));
						player.heal(1.0f * power);
						bleed(level, target, 6);
					}
				}
				case REAPER -> {
					if (oath) {
						cleave(level, player, target, 5.0, 12.0f, true);
					} else {
						cleave(level, player, target, 3.0 * power, 4.0f * power, false);
					}
				}
				case TITHE_AXE -> {
					if (oath) {
						player.heal(2.0f);
					}
				}
				case BLOOD_PICKAXE -> {
				}
			}
		} finally {
			striking = false;
		}
	}

	// ------------------------------------------------------------------ the Blood Pickaxe

	private static void excavate(ServerPlayer player, int ticks) {
		EXCAVATE.put(player.getUUID(), player.level().getGameTime() + ticks);
		player.addEffect(new MobEffectInstance(MobEffects.HASTE, ticks, 1));
		player.sendOverlayMessage(Component.literal("Excavate: your pickaxe bites three by three.").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
		player.level().playSound(null, player.blockPosition(), SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 1.2f, 0.6f);
		bleed(player.level(), player, 30);
	}

	private static boolean excavating(ServerPlayer player) {
		Long until = EXCAVATE.get(player.getUUID());
		return (until != null && until > player.level().getGameTime()) || underOath(player);
	}

	public static boolean isOre(BlockState state) {
		String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		return path.endsWith("_ore") || path.equals("ancient_debris");
	}

	/** After a block broken with the Blood Pickaxe: the rest of an ore's vein, and the 3x3 of an Excavate. */
	private static void afterBreak(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
		ItemStack held = player.getMainHandItem();
		if (mining || of(held) != Weapon.BLOOD_PICKAXE || player.isCreative()) {
			return;
		}
		boolean infernal = infernal(held);
		boolean ore = isOre(state);
		mining = true;
		try {
			if (ore) {
				addCharge(player, Math.round(PER_ORE * (infernal ? 1.25f : 1.0f)));
				if (!player.isShiftKeyDown()) {
					vein(player, level, pos, state.getBlock(), infernal ? 24 : 12);
				}
			}
			if (excavating(player)) {
				square(player, level, pos, state);
			}
		} finally {
			mining = false;
		}
	}

	/** Breaks up to {@code max} more blocks of the same ore touching this one (as if mined: fortune, durability). */
	private static void vein(ServerPlayer player, ServerLevel level, BlockPos start, Block block, int max) {
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		Set<BlockPos> seen = new HashSet<>();
		queue.add(start);
		seen.add(start);
		List<BlockPos> found = new ArrayList<>();
		while (!queue.isEmpty() && found.size() < max) {
			BlockPos at = queue.poll();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						BlockPos next = at.offset(dx, dy, dz);
						if (seen.add(next) && level.getBlockState(next).is(block) && found.size() < max) {
							found.add(next);
							queue.add(next);
						}
					}
				}
			}
		}
		for (BlockPos p : found) {
			if (of(player.getMainHandItem()) != Weapon.BLOOD_PICKAXE) {
				return; // it broke
			}
			player.gameMode.destroyBlock(p);
		}
		if (!found.isEmpty()) {
			level.sendParticles(BloodAltar.BLOOD, start.getX() + 0.5, start.getY() + 0.5, start.getZ() + 0.5, 10 + found.size() * 2, 0.8, 0.8, 0.8, 0.0);
		}
	}

	/** Breaks the eight blocks around this one, square to where the player is looking. */
	private static void square(ServerPlayer player, ServerLevel level, BlockPos center, BlockState broken) {
		Vec3 look = player.getLookAngle();
		double ax = Math.abs(look.x), ay = Math.abs(look.y), az = Math.abs(look.z);
		float hardness = broken.getDestroySpeed(level, center);
		for (int a = -1; a <= 1; a++) {
			for (int b = -1; b <= 1; b++) {
				if (a == 0 && b == 0) {
					continue;
				}
				BlockPos p = ay >= ax && ay >= az ? center.offset(a, 0, b) : ax >= az ? center.offset(0, a, b) : center.offset(a, b, 0);
				BlockState st = level.getBlockState(p);
				float h = st.getDestroySpeed(level, p);
				// only what the pickaxe could mine as easily: never bedrock, obsidian beside stone, or chests
				if (st.isAir() || h < 0 || h > hardness + 1.5f || level.getBlockEntity(p) != null || !player.hasCorrectToolForDrops(st)) {
					continue;
				}
				if (of(player.getMainHandItem()) != Weapon.BLOOD_PICKAXE) {
					return;
				}
				player.gameMode.destroyBlock(p);
			}
		}
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

	/** The scythe only reaps the damned: monsters, and players you could hit anyway (PvP and team rules). */
	private static boolean canReap(ServerPlayer player, LivingEntity e) {
		return e != player && e.isAlive() && !e.isSpectator() && e != player.getVehicle()
				&& (e instanceof Enemy || e instanceof Player other && player.canHarmPlayer(other));
	}

	/** Hits everything within {@code radius} of {@code center} (but not the center itself, unless it's the player). */
	private static void cleave(ServerLevel level, ServerPlayer player, LivingEntity center, double radius, float damage, boolean drag) {
		List<LivingEntity> hit = level.getEntitiesOfClass(LivingEntity.class, center.getBoundingBox().inflate(radius),
				e -> e != center && canReap(player, e) && e.distanceTo(center) <= radius);
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
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, center.getX(), center.getY() + 1.0, center.getZ(), (int) (radius * 3), radius / 2, 0.2, radius / 2, 0.0);
		level.sendParticles(BloodAltar.BLOOD, center.getX(), center.getY() + 0.8, center.getZ(), 30, radius / 2, 0.4, radius / 2, 0.0);
		level.playSound(null, center.getX(), center.getY(), center.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 0.6f);
	}

	/** A burst of blood. */
	private static void bleed(ServerLevel level, LivingEntity entity, int count) {
		level.sendParticles(BloodAltar.BLOOD, entity.getX(), entity.getY() + 1.0, entity.getZ(), count, 0.3, 0.5, 0.3, 0.0);
	}
}
