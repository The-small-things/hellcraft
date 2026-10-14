package net.thesmallthings.hellcraft.world;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.entity.HellEntities;
import net.thesmallthings.hellcraft.entity.npc.Shade;
import net.thesmallthings.hellcraft.hazard.CircleHazards;
import net.thesmallthings.hellcraft.net.MapSync;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * The shades of Limbo's Noble Castle (Inferno IV): the poets, the thinkers, the healers and the heroes who were born
 * before Christ, and live on in the only green place in Hell. Each lingers at its spot round the castle's mound
 * (Saladin alone, apart, by the outer wall). Right-click one: it says something worth knowing, gives you a Codex
 * page the first time if it has one, and trades for emeralds. They are kept in place by {@link #tick}.
 */
public final class Shades {
	private Shades() {
	}

	private static final int HINT = 11;

	/**
	 * A shade: its name and title, where it stands (angle round the mound and distance from the heart), what it says
	 * (in turn), the Codex page it hands out (or null), and its trades.
	 */
	public record Who(String name, String title, double angle, double distance, List<String> lines, @Nullable String codex,
					  Function<ServerLevel, List<MerchantOffer>> trades) {
	}

	public static final List<Who> ALL = List.of(
			new Who("Homer", "the sovereign poet", 22.5, 13,
					List.of("Below the wind lies the rain, and in the rain the great worm Cerberus. When his three maws open, get behind him.",
							"I sang of heroes coming home. Step into every Virgil's Rest you pass: from any Blood Altar you can travel back to one."),
					"cerberus",
					level -> List.of(offer(Items.EMERALD, 12, book(level, Enchantments.SHARPNESS, 2)),
							offer(Items.EMERALD, 4, new ItemStack(Items.IRON_SWORD)))),
			new Who("Horace", "the satirist", 67.5, 13,
					List.of("Greed's mountains are gold, and so is their master, Plutus, in the Mint. Leave your gold up here: the hoarders smell it.",
							"Eat before you go down into the mire. Hunger and mud are poor company."),
					"plutus",
					level -> List.of(offer(Items.EMERALD, 1, new ItemStack(Items.BREAD, 6)),
							offer(Items.EMERALD, 2, new ItemStack(Items.COOKED_BEEF, 4)),
							offer(Items.EMERALD, 6, new ItemStack(Items.GOLDEN_CARROT, 8)))),
			new Who("Ovid", "of things that change", 112.5, 13,
					List.of("In Lust the wind will carry you if you let it: step into a white stream and it takes you down to the next way down. Sneak to let go.",
							"In Greed, things change hands. The hoarders steal gold, and drop it all when they die."),
					"hoarder",
					level -> List.of(offer(Items.EMERALD, 4, potion(Items.POTION, Potions.LONG_SLOW_FALLING)),
							offer(Items.EMERALD, 4, potion(Items.POTION, Potions.LONG_WATER_BREATHING)))),
			new Who("Lucan", "of the civil wars", 157.5, 13,
					List.of("Across the Styx the walls of Dis burn red. Light the watchtower's beacon and Phlegyas comes with his ferry. He does not love passengers.",
							"Here no pilgrim can hurt another: Limbo is a sanctuary. Below it, they can."),
					"phlegyas",
					level -> List.of(offer(Items.EMERALD, 4, new ItemStack(Items.SHIELD)),
							offer(Items.EMERALD, 3, new ItemStack(Items.BOW)),
							offer(Items.EMERALD, 2, new ItemStack(Items.ARROW, 16)))),
			new Who("Aristotle", "the master of those who know", 202.5, 13,
					List.of("Minos judges at the gate of Lust. When his claw flashes white, raise your shield as it lands: he reels.",
							"Gold you carry draws hoarders. An ender chest keeps it safe, and opens the same wherever you set one down."),
					"minos",
					level -> List.of(offer(Items.EMERALD, 10, new ItemStack(Items.ENDER_CHEST)),
							offer(Items.EMERALD, 5, new ItemStack(Items.SPYGLASS)))),
			new Who("Euclid", "the geometer", 247.5, 13,
					List.of("Every cliff in Hell has a way down every thirty degrees, like the hours on a clock. The compass at the top of your screen points to the nearest.",
							"Press M for the Map of Hell, and J for your Journal. Even I needed a map down here."),
					null,
					level -> List.of(offer(Items.EMERALD, 1, new ItemStack(Items.LADDER, 8)),
							offer(Items.EMERALD, 1, new ItemStack(Items.TORCH, 16)),
							offer(Items.EMERALD, 3, new ItemStack(Items.LANTERN, 4)))),
			new Who("Hippocrates", "the physician", 292.5, 13,
					List.of("A Virgil's Rest heals you and keeps monsters away. Setting your respawn there is free.",
							"Lose your last heart and you become a ghost. A friend can bring you back at a Blood Altar."),
					null,
					level -> List.of(offer(Items.EMERALD, 3, potion(Items.SPLASH_POTION, Potions.HEALING)),
							offer(Items.EMERALD, 5, potion(Items.POTION, Potions.REGENERATION)),
							offer(Items.EMERALD, 8, new ItemStack(Items.GOLDEN_APPLE)))),
			new Who("Hector", "of Troy, in arms", 337.5, 13,
					List.of("Iron is the least you want below Limbo, and diamond by the Styx. The cards at each circle say what to bring.",
							"A shield turns the guardians' white blows. Block as the white flash lands."),
					null,
					level -> List.of(offer(Items.EMERALD, 9, new ItemStack(Items.IRON_CHESTPLATE)),
							offer(Items.EMERALD, 7, new ItemStack(Items.IRON_LEGGINGS)),
							offer(Items.EMERALD, 5, new ItemStack(Items.IRON_HELMET)),
							offer(Items.EMERALD, 4, new ItemStack(Items.IRON_BOOTS)))),
			new Who("Saladin", "alone, apart", 205, 42,
					List.of("I stand apart, as I always did. Ride if you can: a horse tires less than a pilgrim.",
							"The roads run all the way down to the Well of Giants. Follow one and you can't get lost."),
					null,
					level -> List.of(offer(Items.EMERALD, 6, new ItemStack(Items.SADDLE)),
							offer(Items.EMERALD, 8, new ItemStack(Items.IRON_HORSE_ARMOR)),
							offer(Items.EMERALD, 1, new ItemStack(Items.LEAD, 2)))));

	private static MerchantOffer offer(Item price, int count, ItemStack result) {
		return new MerchantOffer(new ItemCost(price, count), result, 8, 2, 0.05f);
	}

	private static ItemStack potion(Item item, Holder<Potion> potion) {
		return PotionContents.createItemStack(item, potion);
	}

	private static ItemStack book(ServerLevel level, ResourceKey<Enchantment> key, int lvl) {
		Holder<Enchantment> enchantment = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
		return EnchantmentHelper.createBook(new EnchantmentInstance(enchantment, lvl));
	}

	/** The block a shade stands at. */
	public static BlockPos spot(ServerLevel level, Who who) {
		double a = Math.toRadians(who.angle());
		int x = (int) Math.floor(NobleCastle.X + Math.cos(a) * who.distance());
		int z = (int) Math.floor(NobleCastle.Z + Math.sin(a) * who.distance());
		return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
	}

	private static final Map<UUID, Long> LAST_WORDS = new HashMap<>();

	/** A shade speaks to a player: its next line, and its Codex page the first time. */
	public static void speak(Shade shade, ServerPlayer player) {
		Who who = shade.whoIs();
		long now = player.level().getGameTime();
		Long last = LAST_WORDS.get(player.getUUID());
		if (last == null || now - last > 60) {
			LAST_WORDS.put(player.getUUID(), now);
			int turn = (int) Math.floorMod(now / 60 + shade.getId(), (long) who.lines().size());
			player.sendSystemMessage(Component.literal(who.name() + ": ").withStyle(ChatFormatting.AQUA)
					.append(Component.literal(who.lines().get(turn)).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
			player.level().playSound(null, shade.blockPosition(), SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, SoundSource.NEUTRAL, 0.8f, 0.55f);
		}
		if (who.codex() != null) {
			MapSync.unlockCodex(player, who.codex());
		}
	}

	/**
	 * Every five seconds while a player is near the castle: each shade is there (any missing is called back), and
	 * every ten minutes their wares are restocked.
	 */
	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 100 != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		if (!HellWorldgen.isInferno(level)) {
			return;
		}
		BlockPos heart = new BlockPos(NobleCastle.X, 0, NobleCastle.Z);
		boolean near = false;
		for (ServerPlayer p : level.players()) {
			double d = NobleCastle.distance(p.getX(), p.getZ());
			if (d < 128) {
				near = true;
			}
			if (d < NobleCastle.radius(NobleCastle.WALLS - 1) && !p.isSpectator()) {
				CircleHazards.hintOnce(p, HINT, "The Noble Castle: the shades of the great poets and thinkers live on here. "
						+ "Right-click one to talk: they give tips, pages for your Codex, and trade for emeralds.");
			}
		}
		if (near && level.isLoaded(heart)) {
			ensure(level, server.getTickCount() % 12000 == 0);
		}
	}

	/** Calls back any shade that is missing (where its spot is loaded), and restocks their wares if asked. Returns how many there are. */
	public static int ensure(ServerLevel level, boolean restock) {
		BlockPos heart = new BlockPos(NobleCastle.X, 0, NobleCastle.Z);
		List<Shade> here = level.getEntitiesOfClass(Shade.class, new AABB(heart).inflate(64, 400, 64), Shade::isAlive);
		int count = 0;
		for (int i = 0; i < ALL.size(); i++) {
			final int who = i;
			Shade shade = here.stream().filter(s -> s.who() == who).findFirst().orElse(null);
			if (shade == null) {
				BlockPos spot = spot(level, ALL.get(i));
				if (!level.isLoaded(spot)) {
					continue;
				}
				shade = summon(level, i, spot);
			} else if (restock) {
				shade.getOffers().forEach(MerchantOffer::resetUses);
			}
			if (shade != null) {
				count++;
			}
		}
		// only one of each
		for (Shade s : here) {
			if (here.stream().anyMatch(o -> o != s && o.who() == s.who() && o.getId() < s.getId())) {
				s.discard();
			}
		}
		return count;
	}

	@Nullable
	public static Shade summon(ServerLevel level, int who, BlockPos spot) {
		Shade shade = HellEntities.SHADE.create(level, EntitySpawnReason.EVENT);
		if (shade == null) {
			return null;
		}
		Who w = ALL.get(who);
		shade.setWho(who);
		shade.setCustomName(Component.literal(w.name()).withStyle(ChatFormatting.AQUA));
		shade.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
		shade.home(spot, w.distance() > 20 ? 3 : 4);
		level.addFreshEntity(shade);
		HellcraftMod.LOGGER.info("Shade {} ({}) at {}", w.name(), w.title(), spot.toShortString());
		return shade;
	}

	/** For the smoke test: the castle stands (stone bricks up its outermost wall), and its shades are there. */
	public static String castleTest(ServerLevel level) {
		double a = Math.toRadians(30);
		int x = (int) Math.floor(NobleCastle.X + Math.cos(a) * NobleCastle.radius(NobleCastle.WALLS - 1));
		int z = (int) Math.floor(NobleCastle.Z + Math.sin(a) * NobleCastle.radius(NobleCastle.WALLS - 1));
		level.getChunk(x >> 4, z >> 4);
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		int bricks = 0;
		for (int y = top - 12; y < top; y++) {
			if (level.getBlockState(new BlockPos(x, y, z)).is(BlockTags.STONE_BRICKS)) {
				bricks++;
			}
		}
		level.getChunk(NobleCastle.X >> 4, NobleCastle.Z >> 4);
		int shades = ensure(level, false);
		return "Castle test: wall at " + x + " " + z + " has " + bricks + " stone bricks, standing=" + (bricks >= 6) + "; shades=" + shades + "/" + ALL.size()
				+ "; " + selfTest();
	}

	/** For the smoke test: the shades are all in the castle's courts, clear of its walls. */
	public static String selfTest() {
		List<String> bad = new ArrayList<>();
		for (Who w : ALL) {
			double a = Math.toRadians(w.angle());
			double x = NobleCastle.X + Math.cos(a) * w.distance() + 0.5;
			double z = NobleCastle.Z + Math.sin(a) * w.distance() + 0.5;
			NobleCastle.Part part = NobleCastle.partAt(x, z);
			if (part != NobleCastle.Part.MEADOW && part != NobleCastle.Part.MOUND) {
				bad.add(w.name() + " on " + part);
			}
		}
		return "Shades test: " + ALL.size() + " shades, misplaced: " + (bad.isEmpty() ? "none" : String.join(", ", bad));
	}
}
