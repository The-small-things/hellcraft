package net.thesmallthings.hellcraft.blood;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

	public static final ResourceLocation MODIFIER = HellcraftMod.id("hearts");

	public static HellState.Soul soul(ServerPlayer player) {
		return HellState.get(player.server).soul(player.getUUID(), player.getGameProfile().getName());
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
	}

	/** Changes a player's hearts by {@code delta}, clamped to [0, max]. Returns the applied change. */
	public static int add(ServerPlayer player, int delta) {
		HellState state = HellState.get(player.server);
		HellState.Soul soul = soul(player);
		int before = soul.hearts;
		soul.hearts = Math.max(0, Math.min(HellConfig.get().maxHearts, soul.hearts + delta));
		state.setDirty();
		apply(player);
		return soul.hearts - before;
	}

	public static void set(ServerPlayer player, int hearts) {
		HellState.Soul soul = soul(player);
		soul.hearts = Math.max(1, Math.min(HellConfig.get().maxHearts, hearts));
		HellState.get(player.server).setDirty();
		apply(player);
	}

	public static Component describe(String name, int hearts) {
		return Component.literal(name + ": ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal("❤ " + hearts).withStyle(ChatFormatting.DARK_RED))
				.append(Component.literal(" / " + HellConfig.get().maxHearts).withStyle(ChatFormatting.DARK_GRAY));
	}
}
