package net.thesmallthings.hellcraft.hazard;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.entity.HellEntities;
import net.thesmallthings.hellcraft.entity.monster.Hoarder;
import net.thesmallthings.hellcraft.world.HellWorldgen;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.Shrines;
import net.thesmallthings.hellcraft.world.Spine;
import net.thesmallthings.hellcraft.world.Zone;

/**
 * Greed's rule (L-IV): gold draws the hoarders. In Greed, a soul carrying gold (or diamonds, emeralds, netherite)
 * draws hoarders out of the hoards: the heavier the purse, the more come, up to six. They hunt only the laden, and
 * their blow steals some of it (kill them to get it back). Stash it, in an ender chest say, and they lose interest.
 */
public final class Hoarders {
	private Hoarders() {
	}

	private static final int HINT = 30;

	/** How much treasure someone is hauling, in "gold ingot equivalents" (0 for anything but a player). */
	public static int weight(LivingEntity entity) {
		if (!(entity instanceof ServerPlayer player)) {
			return 0;
		}
		int weight = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			weight += worth(player.getInventory().getItem(i));
		}
		return weight;
	}

	static int worth(ItemStack s) {
		int c = s.getCount();
		if (s.is(Items.GOLD_INGOT) || s.is(Items.RAW_GOLD) || s.is(Items.DIAMOND) || s.is(Items.EMERALD) || s.is(Items.GILDED_BLACKSTONE)) {
			return c;
		} else if (s.is(Items.GOLD_BLOCK) || s.is(Items.RAW_GOLD_BLOCK) || s.is(Items.DIAMOND_BLOCK) || s.is(Items.EMERALD_BLOCK)) {
			return c * 9;
		} else if (s.is(Items.NETHERITE_INGOT)) {
			return c * 4;
		} else if (s.is(Items.NETHERITE_BLOCK)) {
			return c * 36;
		} else if (s.is(Items.GOLD_NUGGET)) {
			return c / 9;
		}
		return 0;
	}

	/** How many hoarders a purse this heavy draws (hoarderGold, hoarderGoldPerExtra, hoardersMax). */
	static int drawn(int weight) {
		HellConfig c = HellConfig.get();
		return weight < c.hoarderGold ? 0 : Math.min(c.hoardersMax, 1 + weight / c.hoarderGoldPerExtra);
	}

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 40 != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		if (!HellWorldgen.isInferno(level) || !HellConfig.get().circleHazards) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator() || p.isCreative() || InfernoGeometry.zoneAt(p.getX(), p.getZ()) != Zone.GREED
					|| CircleHazards.warded(p) || Spine.shelters(p) || Shrines.shelters(p)) {
				continue;
			}
			int want = drawn(weight(p));
			if (want == 0) {
				continue;
			}
			int near = level.getEntitiesOfClass(Hoarder.class, new AABB(p.blockPosition()).inflate(40), Hoarder::isAlive).size();
			if (near < want && level.getRandom().nextFloat() < 0.4f) {
				spawnNear(level, p);
			}
		}
	}

	private static void spawnNear(ServerLevel level, ServerPlayer p) {
		for (int tries = 0; tries < 8; tries++) {
			double a = level.getRandom().nextDouble() * Math.PI * 2;
			double d = 14 + level.getRandom().nextDouble() * 8;
			int x = (int) Math.floor(p.getX() + Math.cos(a) * d);
			int z = (int) Math.floor(p.getZ() + Math.sin(a) * d);
			if (InfernoGeometry.zoneAt(x, z) != Zone.GREED || InfernoGeometry.fluidAt(x, z) != InfernoGeometry.Fluid.NONE) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			Hoarder h = HellEntities.HOARDER.create(level, EntitySpawnReason.EVENT);
			if (h == null) {
				return;
			}
			h.snapTo(x + 0.5, y, z + 0.5, level.getRandom().nextFloat() * 360, 0);
			if (!level.noCollision(h)) {
				continue;
			}
			h.setTarget(p);
			level.addFreshEntity(h);
			level.playSound(null, x, y, z, SoundEvents.ARMOR_EQUIP_GOLD.value(), SoundSource.HOSTILE, 1.0f, 0.6f);
			CircleHazards.hintOnce(p, HINT, "Gold draws the hoarders: the more you carry, the more come, and they steal it. "
					+ "Kill them to get it back, or stash your gold (an ender chest) and they lose interest.");
			return;
		}
	}

	/** A hoarder's blow landed: it takes some of the most valuable treasure the soul carries. */
	public static void steal(Hoarder hoarder, ServerPlayer p) {
		int best = -1;
		int bestWorth = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			ItemStack s = p.getInventory().getItem(i);
			int each = s.isEmpty() ? 0 : worth(s.copyWithCount(1)) + (s.is(Items.GOLD_NUGGET) ? 1 : 0);
			if (each > bestWorth) {
				bestWorth = each;
				best = i;
			}
		}
		if (best < 0) {
			return;
		}
		ItemStack from = p.getInventory().getItem(best);
		int take = Math.min(from.getCount(), bestWorth >= 9 ? 1 : from.is(Items.GOLD_NUGGET) ? 9 : 4);
		ItemStack taken = from.split(take);
		ItemStack left = hoarder.getInventory().addItem(taken);
		if (!left.isEmpty()) {
			hoarder.spawnAtLocation(p.level(), left);
		}
		p.level().playSound(null, p.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.HOSTILE, 1.0f, 0.5f);
		p.sendOverlayMessage(Component.literal("A hoarder stole " + taken.getCount() + " " + taken.getHoverName().getString() + "! Kill it to get it back.")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
	}

	/** For the smoke test: what a purse is worth, and how many hoarders it draws. */
	public static String selfTest() {
		return "Hoarders test: 4 ingots draw " + drawn(4) + ", 20 draw " + drawn(20) + ", a stack of blocks (576) draws " + drawn(576)
				+ "; a gold block is worth " + worth(new ItemStack(Items.GOLD_BLOCK)) + " ingots";
	}
}
