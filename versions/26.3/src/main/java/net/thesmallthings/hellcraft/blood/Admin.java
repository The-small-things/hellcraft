package net.thesmallthings.hellcraft.blood;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.thesmallthings.hellcraft.config.HellConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Operator tools for keeping the mighty in check: inspect a soul, set or reset its power, strip its blood gear. */
public final class Admin {
	private Admin() {
	}

	/** Everything about a soul's power, for /hellcraft inspect. */
	public static List<Component> inspect(ServerPlayer player) {
		HellConfig c = HellConfig.get();
		HellState.Soul soul = Hearts.soul(player);
		long now = player.level().getServer().overworld().getGameTime();
		int raw = c.maxHearts + soul.maxBonus + Prestige.capBonus(soul);
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal("☠ " + player.getGameProfile().name()).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
		lines.add(line("Hearts", soul.hearts + " / " + Hearts.cap(soul) + (soul.ghost ? " (a ghost)" : "")
				+ "  [base " + c.maxHearts + " + Bane " + soul.maxBonus + " + P's " + Prestige.capBonus(soul)
				+ (raw > c.heartCeiling ? ", held to the ceiling of " + c.heartCeiling : "") + "]"));
		lines.add(line("Seven P's", soul.prestige + " burned"));
		lines.add(line("Deeds", "Lucifer slain " + soul.luciferKills + ", guardians slain " + soul.guardiansSlain + ", deaths " + soul.deaths));
		lines.add(line("Boss spoils", BossSpoils.describe(soul, now, Map.of(
				"lucifer", c.luciferSpoilsCooldownMinutes, "wither", c.bossSpoilsCooldownMinutes,
				"warden", c.bossSpoilsCooldownMinutes, "ender_dragon", c.bossSpoilsCooldownMinutes))));
		lines.add(line("Carries", describe(count(player.getInventory()), "on them") + "; " + describe(count(player.getEnderChestInventory()), "in their ender chest")));
		return lines;
	}

	private static Component line(String label, String value) {
		return Component.literal(label + ": ").withStyle(ChatFormatting.GRAY).append(Component.literal(value).withStyle(ChatFormatting.WHITE));
	}

	/** [hell weapons, blood armour, relics, Blood Hearts, Lucifer's Banes] in a container. */
	private static int[] count(Container container) {
		int[] n = new int[5];
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack s = container.getItem(i);
			if (HellWeapons.of(s) != null) {
				n[0]++;
			} else if (BloodArmour.of(s) != null) {
				n[1]++;
			} else if (Relics.of(s) != null) {
				n[2]++;
			} else if (BloodItems.isHeart(s)) {
				n[3] += s.getCount();
			} else if (BloodItems.isBane(s)) {
				n[4] += s.getCount();
			}
		}
		return n;
	}

	private static String describe(int[] n, String where) {
		return n[0] + " hell weapons, " + n[1] + " blood armour, " + n[2] + " relics, " + n[3] + " Blood Hearts, " + n[4] + " Banes " + where;
	}

	/** Sets the heart capacity a soul has earned from Lucifer's Bane. */
	public static String setBonus(ServerPlayer player, int bonus) {
		HellState.Soul soul = Hearts.soul(player);
		soul.maxBonus = Math.max(0, bonus);
		soul.hearts = Math.min(soul.hearts, Hearts.cap(soul));
		HellState.get(player.level().getServer()).setDirty();
		Hearts.apply(player);
		return player.getGameProfile().name() + ": Bane bonus " + soul.maxBonus + ", hearts " + soul.hearts + " / " + Hearts.cap(soul);
	}

	/** Takes a soul back to a fresh start: starting hearts, no Bane bonus, no burned P's (deeds and spoils are kept). */
	public static String reset(ServerPlayer player) {
		HellState.Soul soul = Hearts.soul(player);
		soul.maxBonus = 0;
		soul.prestige = 0;
		soul.hearts = HellConfig.get().startHearts;
		HellState.get(player.level().getServer()).setDirty();
		Hearts.apply(player);
		player.sendSystemMessage(Component.literal("An operator reset your hearts and prestige.").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
		return player.getGameProfile().name() + " is reset to " + soul.hearts + " hearts (max " + Hearts.cap(soul) + ", prestige 0).";
	}

	/** Removes every hell weapon, blood armour piece, relic, Blood Heart and Bane a player holds, ender chest included. */
	public static String strip(ServerPlayer player) {
		int removed = strip(player.getInventory()) + strip(player.getEnderChestInventory());
		player.containerMenu.broadcastChanges();
		player.sendSystemMessage(Component.literal("An operator removed your Hellcraft gear.").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
		return "Took " + removed + " blood item" + (removed == 1 ? "" : "s") + " from " + player.getGameProfile().name() + ".";
	}

	private static int strip(Container container) {
		int removed = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack s = container.getItem(i);
			if (HellWeapons.of(s) != null || BloodArmour.of(s) != null || Relics.of(s) != null || BloodItems.isHeart(s) || BloodItems.isBane(s)) {
				removed += s.getCount();
				container.setItem(i, ItemStack.EMPTY);
			}
		}
		container.setChanged();
		return removed;
	}
}
