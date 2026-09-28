package net.thesmallthings.hellcraft.world;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.hazard.guardian.GuardianManager;
import net.thesmallthings.hellcraft.hazard.lucifer.LuciferManager;
import net.thesmallthings.hellcraft.util.Feedback;
import net.thesmallthings.hellcraft.util.Signs;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Virgil's Rests: a safe camp where each ramp comes down into a new circle (see {@link ShrineSites}).
 * Each has a free Blood Altar to bind your respawn to, soul campfires, and a supply chest. Inside the
 * ring no circle torments you, monsters don't spawn, and those that wander in are driven out.
 * They are built the first time a player comes near.
 */
public final class Shrines {
	private Shrines() {
	}

	/** Hazards stop and monsters are driven out within this distance of a Rest's altar. */
	public static final int SAFE_RADIUS = 20;
	private static final int BUILD_RANGE = 128;
	private static final int PLATFORM = 5;
	private static final String[] TIERS = {"upper", "middle", "lower"};
	private static final List<Entity> EXILED = new ArrayList<>();
	private static int ticks;

	public static void tick(MinecraftServer server) {
		if (!EXILED.isEmpty()) {
			for (Entity e : EXILED) {
				e.discard();
			}
			EXILED.clear();
		}
		ticks++;
		if (ticks % 20 != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		if (!HellWorldgen.isInferno(level) || level.players().isEmpty()) {
			return;
		}
		HellState state = HellState.get(server);
		for (ShrineSites.Site site : ShrineSites.all()) {
			ServerPlayer near = null;
			double best = Double.MAX_VALUE;
			for (ServerPlayer p : level.players()) {
				double d = dist2(site, p.getX(), p.getZ());
				if (d < best) {
					best = d;
					near = p;
				}
			}
			if (near == null || best > BUILD_RANGE * BUILD_RANGE) {
				continue;
			}
			Integer floor = state.shrines.get(site.id());
			if (floor == null) {
				if (ticks % 40 == 0 && level.hasChunk(site.x() >> 4, site.z() >> 4)) {
					build(level, site);
				}
			} else if (best < 64 * 64) {
				driveOut(level, site, floor);
			}
		}
	}

	/** Monsters that appear inside a built Rest are gone a tick later (named ones, guardians and Lucifer excepted). */
	public static void onLoad(Entity entity, ServerLevel level) {
		if (entity instanceof Enemy && !entity.hasCustomName() && !LuciferManager.isLucifer(entity) && !GuardianManager.isGuardian(entity)
				&& level.dimension() == net.minecraft.world.level.Level.OVERWORLD && HellWorldgen.isInferno(level)
				&& inside(level.getServer(), entity.getX(), entity.getY(), entity.getZ())) {
			EXILED.add(entity);
		}
	}

	/** True inside the ring of a built Rest: the circles don't torment you there. */
	public static boolean shelters(ServerPlayer player) {
		return player.level().dimension() == net.minecraft.world.level.Level.OVERWORLD
				&& inside(player.level().getServer(), player.getX(), player.getY(), player.getZ());
	}

	private static boolean inside(MinecraftServer server, double x, double y, double z) {
		ShrineSites.Site site = ShrineSites.nearest(x, z);
		if (site == null || dist2(site, x, z) > SAFE_RADIUS * SAFE_RADIUS) {
			return false;
		}
		Integer floor = HellState.get(server).shrines.get(site.id());
		return floor != null && Math.abs(y - floor) < 24;
	}

	private static double dist2(ShrineSites.Site site, double x, double z) {
		double dx = site.x() + 0.5 - x;
		double dz = site.z() + 0.5 - z;
		return dx * dx + dz * dz;
	}

	/** Hostile mobs that walk into the ring are thrown back out by the soul fire. */
	private static void driveOut(ServerLevel level, ShrineSites.Site site, int floor) {
		double cx = site.x() + 0.5;
		double cz = site.z() + 0.5;
		AABB box = new AABB(cx - 16, floor - 8, cz - 16, cx + 16, floor + 12, cz + 16);
		for (Mob mob : level.getEntitiesOfClass(Mob.class, box, m -> m instanceof Enemy && m.isAlive() && !m.hasCustomName()
				&& !GuardianManager.isGuardian(m) && !LuciferManager.isLucifer(m))) {
			double dx = mob.getX() - cx;
			double dz = mob.getZ() - cz;
			if (dx * dx + dz * dz > 16 * 16) {
				continue;
			}
			Feedback.pushAway(mob, cx, cz, 1.2);
			mob.setTarget(null);
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, mob.getX(), mob.getY() + 1, mob.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
		}
	}

	// ------------------------------------------------------------------ building

	/** Builds a Rest now; returns the altar position. */
	public static BlockPos build(ServerLevel level, ShrineSites.Site site) {
		int x = site.x();
		int z = site.z();
		level.getChunk(x >> 4, z >> 4);
		int y = ground(level, x, z);
		for (int dx = -PLATFORM; dx <= PLATFORM; dx++) {
			for (int dz = -PLATFORM; dz <= PLATFORM; dz++) {
				boolean edge = Math.abs(dx) == PLATFORM || Math.abs(dz) == PLATFORM;
				set(level, x + dx, y - 1, z + dz, edge ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : Blocks.POLISHED_BLACKSTONE.defaultBlockState());
				// footings down to the ground (or through the marsh)
				for (int d = 2; d <= 10; d++) {
					BlockState below = level.getBlockState(new BlockPos(x + dx, y - d, z + dz));
					if (!below.isAir() && below.getFluidState().isEmpty() && !below.is(BlockTags.REPLACEABLE)) {
						break;
					}
					set(level, x + dx, y - d, z + dz, Blocks.BLACKSTONE.defaultBlockState());
				}
				for (int dy = 0; dy <= 5; dy++) {
					set(level, x + dx, y + dy, z + dz, Blocks.AIR.defaultBlockState());
				}
			}
		}
		buildAltar(level, x, y, z, List.of(
				Component.literal("VIRGIL'S REST").withStyle(ChatFormatting.BOLD),
				Component.literal(shortName(site.zone())),
				Component.literal("Right-click altar:"),
				Component.literal("bind here, free")));
		for (int[] c : new int[][]{{-4, -4}, {-4, 4}, {4, -4}, {4, 4}}) {
			set(level, x + c[0], y, z + c[1], Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
		}
		BlockPos chest = new BlockPos(x, y, z + 4);
		level.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH), 3);
		RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), chest,
				ResourceKey.create(Registries.LOOT_TABLE, HellcraftMod.id("chests/virgils_rest_" + TIERS[site.tier()])));
		HellState state = HellState.get(level.getServer());
		state.shrines.put(site.id(), y);
		state.setDirty();
		HellcraftMod.LOGGER.info("Virgil's Rest ({}) at {} {} {}", site.id(), x, y, z);
		return new BlockPos(x, y, z);
	}

	/** The floor of a Rest: the surface under trees, or the top of the marsh water. */
	private static int ground(ServerLevel level, int x, int z) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		while (y > level.getMinY() + 1 && level.getBlockState(new BlockPos(x, y - 1, z)).is(BlockTags.LOGS)) {
			y--;
		}
		return y;
	}

	/** A Blood Altar: a respawn anchor on 3x3 crying obsidian, with soul lanterns and a sign facing north. */
	public static void buildAltar(ServerLevel level, int x, int ground, int z, List<Component> sign) {
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int dy = 0; dy <= 4; dy++) {
					set(level, x + dx, ground + dy, z + dz, Blocks.AIR.defaultBlockState());
				}
				boolean core = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
				set(level, x + dx, ground - 1, z + dz, core ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
			}
		}
		set(level, x, ground, z, Blocks.RESPAWN_ANCHOR.defaultBlockState());
		for (int[] c : new int[][]{{-2, -2}, {-2, 2}, {2, -2}, {2, 2}}) {
			set(level, x + c[0], ground, z + c[1], Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState());
			set(level, x + c[0], ground + 1, z + c[1], Blocks.SOUL_LANTERN.defaultBlockState());
		}
		Signs.place(level, new BlockPos(x, ground, z - 2), 8, sign);
	}

	private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
		level.setBlock(new BlockPos(x, y, z), state, 3);
	}

	private static String shortName(Zone zone) {
		return switch (zone) {
			case STYX -> "The Styx";
			case WOOD_OF_SUICIDES -> "Wood of Suicides";
			case BURNING_SANDS -> "Burning Sands";
			default -> zone.name().charAt(0) + zone.name().substring(1).toLowerCase(Locale.ROOT);
		};
	}

	// ------------------------------------------------------------------ finding them

	/** "Virgil's Rest nearest you: x, z (n blocks)", or null outside the Inferno. */
	@Nullable
	public static Component nearestLine(ServerPlayer player) {
		if (player.level().dimension() != net.minecraft.world.level.Level.OVERWORLD || !HellWorldgen.isInferno(player.level())) {
			return null;
		}
		ShrineSites.Site site = ShrineSites.nearest(player.getX(), player.getZ());
		if (site == null) {
			return null;
		}
		int d = (int) Math.sqrt(dist2(site, player.getX(), player.getZ()));
		return Component.literal("Nearest Virgil's Rest (" + site.title() + "): " + site.x() + ", " + site.z() + ", " + d + " blocks away. "
				+ "Monsters can't follow you there, and binding your respawn is free.").withStyle(ChatFormatting.GOLD);
	}

	/** Builds every Rest (or those of one circle) that isn't built yet; returns what was built. */
	public static List<String> buildAll(MinecraftServer server, @Nullable String zone) {
		ServerLevel level = server.overworld();
		List<String> built = new ArrayList<>();
		if (!HellWorldgen.isInferno(level)) {
			return built;
		}
		HellState state = HellState.get(server);
		for (ShrineSites.Site site : ShrineSites.all()) {
			if ((zone == null || site.zone().name().equalsIgnoreCase(zone)) && !state.shrines.containsKey(site.id())) {
				BlockPos p = build(level, site);
				built.add(site.id() + " at " + p.getX() + " " + p.getY() + " " + p.getZ());
			}
		}
		return built;
	}

	public static List<String> list(MinecraftServer server) {
		HellState state = HellState.get(server);
		List<String> out = new ArrayList<>();
		for (ShrineSites.Site site : ShrineSites.all()) {
			Integer y = state.shrines.get(site.id());
			out.add(site.id() + ": " + site.x() + " " + (y != null ? y : "?") + " " + site.z() + (y != null ? "" : " (not built yet)"));
		}
		return out;
	}
}
