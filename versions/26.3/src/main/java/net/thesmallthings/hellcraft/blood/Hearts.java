package net.thesmallthings.hellcraft.blood;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.config.HellConfig;

/** Turns a soul's heart count into actual max health. */
public final class Hearts {
	private Hearts() {
	}

	public static final Identifier MODIFIER = HellcraftMod.id("hearts");

	public static HellState.Soul soul(ServerPlayer player) {
		return HellState.get(player.level().getServer()).soul(player.getUUID(), player.getGameProfile().name());
	}

	/** Most hearts this soul may hold: the server cap plus anything earned (Lucifer's Bane, the burned P's), never past the ceiling. */
	public static int cap(HellState.Soul soul) {
		HellConfig config = HellConfig.get();
		return Math.min(config.heartCeiling, config.maxHearts + soul.maxBonus + Prestige.capBonus(soul));
	}

	/** True once nothing more can raise this soul's capacity. */
	public static boolean atCeiling(HellState.Soul soul) {
		return cap(soul) >= HellConfig.get().heartCeiling;
	}

	public static void apply(ServerPlayer player) {
		HellState.Soul soul = soul(player);
		AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
		if (health == null) {
			return;
		}
		health.removeModifier(MODIFIER);
		int hearts = Math.max(1, soul.hearts);
		double delta = hearts * 2.0 - health.getBaseValue();
		if (delta != 0) {
			health.addPermanentModifier(new AttributeModifier(MODIFIER, delta, AttributeModifier.Operation.ADD_VALUE));
		}
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
		Prestige.applyVirtues(player, soul);
		Scoreboards.update(player.level().getServer(), soul);
	}

	/** Changes a player's hearts by {@code delta}, clamped to [0, max]. Returns the applied change. */
	public static int add(ServerPlayer player, int delta) {
		HellState state = HellState.get(player.level().getServer());
		HellState.Soul soul = soul(player);
		int before = soul.hearts;
		soul.hearts = Math.max(0, Math.min(cap(soul), soul.hearts + delta));
		state.setDirty();
		apply(player);
		return soul.hearts - before;
	}

	public static void set(ServerPlayer player, int hearts) {
		HellState.Soul soul = soul(player);
		soul.hearts = Math.max(1, Math.min(cap(soul), hearts));
		HellState.get(player.level().getServer()).setDirty();
		apply(player);
	}

	public static Component describe(String name, HellState.Soul soul) {
		return Component.literal(name + ": ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal("❤ " + soul.hearts).withStyle(ChatFormatting.DARK_RED))
				.append(Component.literal(" / " + cap(soul)).withStyle(ChatFormatting.DARK_GRAY));
	}
}
