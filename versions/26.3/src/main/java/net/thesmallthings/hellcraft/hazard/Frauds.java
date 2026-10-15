package net.thesmallthings.hellcraft.hazard;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.entity.HellEntities;
import net.thesmallthings.hellcraft.entity.monster.Malacoda;
import net.thesmallthings.hellcraft.entity.monster.Malebranche;
import net.thesmallthings.hellcraft.entity.monster.Mimic;
import net.thesmallthings.hellcraft.entity.monster.Thief;
import net.thesmallthings.hellcraft.net.Dialogue;
import net.thesmallthings.hellcraft.net.MapSync;
import net.thesmallthings.hellcraft.util.Signs;
import net.thesmallthings.hellcraft.world.Circle;
import net.thesmallthings.hellcraft.world.FraudSites;
import net.thesmallthings.hellcraft.world.HellWorldgen;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.Shrines;
import net.thesmallthings.hellcraft.world.Zone;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Fraud's rule (L-VIII): nothing is what it seems. On the server side:
 * <ul>
 *     <li>Mimics wait where a third of Malebolge's caches should be (FraudSites): set down as chests when a soul comes
 *     near, until killed.</li>
 *     <li>A fake Virgil's Rest stands beside each bridge the roads don't take: it looks like any Rest (soul fires, an
 *     altar, a sign), but step into it and the trap springs: the fires turn red, the altar goes dead, its chest is a
 *     mimic and thieves come for you. The tell: it isn't on your compass.</li>
 *     <li>Thieves prowl the ridges, snatch something from your pack and run (Thief).</li>
 *     <li>The Malebranche keep the ditches of pitch, hooks ready (Malebranche); the first time a soul comes down a road
 *     onto the fifth ridge their captain Malacoda meets it with a kindly lie and leads it into an ambush (Malacoda).</li>
 *     <li>Some of Malebolge's monsters are invisible, but give themselves away with a swirl (MobEmpowerment).</li>
 * </ul>
 */
public final class Frauds {
	private Frauds() {
	}

	static final int HINT_FRAUD = 35;
	private static final double MIMIC_RANGE = 40;
	private static final double FAKE_BUILD_RANGE = 96;
	private static final double FAKE_TRAP = 7;
	private static final int THIEVES_NEAR = 3;
	private static final int DEVILS_NEAR = 3;
	static final int HINT_MALACODA = 36;
	static final int HINT_DEVILS = 38;
	/** Where Malacoda meets you: the fifth ridge, the bank of the fifth ditch (the first of boiling pitch he keeps). */
	private static final int MALACODA_RIDGE = 5;

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 7) {
			return;
		}
		ServerLevel level = server.overworld();
		if (!HellWorldgen.isInferno(level)) {
			return;
		}
		HellState state = HellState.get(server);
		boolean monsters = HellConfig.get().layerMonsters && level.getDifficulty() != Difficulty.PEACEFUL;
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator()) {
				continue;
			}
			Zone zone = InfernoGeometry.zoneAt(p.getX(), p.getZ());
			if (zone.circle() != Circle.FRAUD || zone == Zone.WELL_OF_GIANTS) {
				continue;
			}
			if (monsters) {
				for (FraudSites.Cache c : FraudSites.cachesNear(p.getX(), p.getZ(), MIMIC_RANGE)) {
					if (c.mimic() && !state.fraudSpent.contains(c.id())) {
						setMimic(level, c.id(), BlockPos.containing(c.x(), 0, c.z()), c.facing());
					}
				}
				if (server.getTickCount() % 100 == 7 && level.getRandom().nextFloat() < 0.3f) {
					thiefNear(level, p);
				}
				if (server.getTickCount() % 100 == 47 && level.getRandom().nextFloat() < 0.35f) {
					devilNear(level, p);
				}
				malacoda(level, p);
			}
			for (FraudSites.FakeRest f : FraudSites.fakeRests()) {
				double d = Math.hypot(p.getX() - f.x(), p.getZ() - f.z());
				if (d > FAKE_BUILD_RANGE) {
					continue;
				}
				Integer floor = state.fakeRests.get(f.id());
				if (floor == null) {
					if (level.hasChunk(f.x() >> 4, f.z() >> 4)) {
						build(level, f);
					}
				} else if (d < FAKE_TRAP && Math.abs(p.getY() - floor) < 6 && !state.fraudSpent.contains(f.id()) && !p.isCreative()) {
					spring(level, f, floor, p);
				}
			}
		}
	}

	/** Sets a dormant mimic down on its site, unless one is already there. */
	@Nullable
	private static Mimic setMimic(ServerLevel level, String id, BlockPos column, int facing) {
		if (!level.isLoaded(column)) {
			return null;
		}
		AABB around = new AABB(column).inflate(48, 320, 48);
		if (!level.getEntitiesOfClass(Mimic.class, around, m -> id.equals(m.site())).isEmpty()) {
			return null;
		}
		Mimic mimic = HellEntities.MIMIC.create(level, EntitySpawnReason.EVENT);
		if (mimic == null) {
			return null;
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
		mimic.snapTo(column.getX() + 0.5, y, column.getZ() + 0.5, facing * 90f, 0);
		mimic.lurk(id, facing);
		level.addFreshEntity(mimic);
		return mimic;
	}

	/** A Malebranche comes up out of a ditch of pitch near a soul passing it. */
	private static void devilNear(ServerLevel level, ServerPlayer p) {
		boolean pitch = false;
		for (int i = 0; i < 8 && !pitch; i++) {
			double a = i * Math.PI / 4;
			pitch = InfernoGeometry.zoneAt(p.getX() + Math.cos(a) * 14, p.getZ() + Math.sin(a) * 14) == Zone.MALEBOLGE_PITCH;
		}
		if (!pitch || level.getEntitiesOfClass(Malebranche.class, new AABB(p.blockPosition()).inflate(40)).size() >= DEVILS_NEAR) {
			return;
		}
		for (int tries = 0; tries < 6; tries++) {
			double a = level.getRandom().nextDouble() * Math.PI * 2;
			double d = 14 + level.getRandom().nextDouble() * 10;
			int x = (int) Math.floor(p.getX() + Math.cos(a) * d);
			int z = (int) Math.floor(p.getZ() + Math.sin(a) * d);
			if (InfernoGeometry.zoneAt(x, z) != Zone.MALEBOLGE || !level.isLoaded(new BlockPos(x, 0, z)) || Shrines.near(level, new BlockPos(x, 0, z))) {
				continue;
			}
			Malebranche devil = HellEntities.MALEBRANCHE.create(level, EntitySpawnReason.NATURAL);
			if (devil == null) {
				return;
			}
			devil.snapTo(x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z + 0.5, level.getRandom().nextFloat() * 360, 0);
			if (!level.noCollision(devil)) {
				continue;
			}
			level.addFreshEntity(devil);
			MapSync.unlockCodex(p, "malebranche");
			CircleHazards.hintOnce(p, HINT_DEVILS, "The Malebranche throw their hooks along a red line on the ground. Step off the line "
					+ "before the hook flies, or you're dragged to their claws.");
			return;
		}
	}

	/** Malacoda's trick, once per soul: on a road where it crosses the fifth ridge. */
	private static void malacoda(ServerLevel level, ServerPlayer p) {
		if (p.isCreative() || CircleHazards.hinted(p, HINT_MALACODA) || !InfernoGeometry.onRoad(p.getX(), p.getZ())) {
			return;
		}
		double angle = Math.atan2(p.getZ(), p.getX());
		double ridge = FraudSites.onRidge(angle, FraudSites.ridgeRadius(MALACODA_RIDGE));
		double r = Math.hypot(p.getX(), p.getZ());
		if (Math.abs(r - ridge) > 4 || !level.getEntitiesOfClass(Malacoda.class, new AABB(p.blockPosition()).inflate(64)).isEmpty()) {
			return;
		}
		CircleHazards.spendHint(p, HINT_MALACODA);
		// he steps out a little along the ridge, and will lead you about 40 blocks further along it, away from the road
		double side = level.getRandom().nextBoolean() ? 1 : -1;
		double a0 = angle + side * 7 / ridge;
		double a1 = angle + side * 46 / ridge;
		double r0 = FraudSites.onRidge(a0, FraudSites.ridgeRadius(MALACODA_RIDGE));
		double r1 = FraudSites.onRidge(a1, FraudSites.ridgeRadius(MALACODA_RIDGE));
		int x0 = (int) Math.floor(Math.cos(a0) * r0);
		int z0 = (int) Math.floor(Math.sin(a0) * r0);
		int x1 = (int) Math.floor(Math.cos(a1) * r1);
		int z1 = (int) Math.floor(Math.sin(a1) * r1);
		if (!level.isLoaded(new BlockPos(x1, 0, z1))) {
			return;
		}
		Vec3 at = new Vec3(x0 + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0, z0), z0 + 0.5);
		Vec3 to = new Vec3(x1 + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x1, z1), z1 + 0.5);
		Malacoda.meet(level, p, at, to);
		HellcraftMod.LOGGER.info("Malacoda meets {} on the fifth ridge at {} {} {}", p.getName().getString(), x0, (int) at.y, z0);
	}

	private static void thiefNear(ServerLevel level, ServerPlayer p) {
		if (level.getEntitiesOfClass(Thief.class, new AABB(p.blockPosition()).inflate(40)).size() >= THIEVES_NEAR) {
			return;
		}
		for (int tries = 0; tries < 6; tries++) {
			double a = level.getRandom().nextDouble() * Math.PI * 2;
			double d = 20 + level.getRandom().nextDouble() * 12;
			int x = (int) Math.floor(p.getX() + Math.cos(a) * d);
			int z = (int) Math.floor(p.getZ() + Math.sin(a) * d);
			if (InfernoGeometry.zoneAt(x, z).circle() != Circle.FRAUD || !level.isLoaded(new BlockPos(x, 0, z)) || Shrines.near(level, new BlockPos(x, 0, z))) {
				continue;
			}
			if (spawnThief(level, x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z) != null) {
				MapSync.unlockCodex(p, "thief");
				return;
			}
		}
	}

	@Nullable
	private static Thief spawnThief(ServerLevel level, int x, int y, int z) {
		Thief thief = HellEntities.THIEF.create(level, EntitySpawnReason.NATURAL);
		if (thief == null) {
			return null;
		}
		thief.snapTo(x + 0.5, y, z + 0.5, level.getRandom().nextFloat() * 360, 0);
		if (!level.noCollision(thief)) {
			return null;
		}
		level.addFreshEntity(thief);
		return thief;
	}

	// ------------------------------------------------------------------ the fake Rests

	private static final int PLATFORM = 5;

	/** Builds a fake Rest: to the eye, a Virgil's Rest like any other. */
	static void build(ServerLevel level, FraudSites.FakeRest f) {
		int x = f.x();
		int z = f.z();
		level.getChunk(x >> 4, z >> 4);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		for (int dx = -PLATFORM; dx <= PLATFORM; dx++) {
			for (int dz = -PLATFORM; dz <= PLATFORM; dz++) {
				boolean edge = Math.abs(dx) == PLATFORM || Math.abs(dz) == PLATFORM;
				set(level, x + dx, y - 1, z + dz, edge ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : Blocks.POLISHED_BLACKSTONE.defaultBlockState());
				for (int d = 2; d <= 8; d++) {
					BlockState below = level.getBlockState(new BlockPos(x + dx, y - d, z + dz));
					if (!below.isAir() && below.getFluidState().isEmpty()) {
						break;
					}
					set(level, x + dx, y - d, z + dz, Blocks.BLACKSTONE.defaultBlockState());
				}
				for (int dy = 0; dy <= 5; dy++) {
					set(level, x + dx, y + dy, z + dz, Blocks.AIR.defaultBlockState());
				}
			}
		}
		Shrines.buildAltar(level, x, y, z, List.of(
				Component.literal("VIRGIL'S REST").withStyle(ChatFormatting.BOLD),
				Component.literal("Malebolge"),
				Component.literal("Right-click altar:"),
				Component.literal("set respawn, free")));
		for (int[] c : new int[][]{{-4, -4}, {-4, 4}, {4, -4}, {4, 4}}) {
			set(level, x + c[0], y, z + c[1], Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
		}
		// where the supply chest should be, a mimic
		setMimic(level, f.id() + "_chest", new BlockPos(x, 0, z + 4), 2);
		HellState state = HellState.get(level.getServer());
		state.fakeRests.put(f.id(), y);
		state.setDirty();
		HellcraftMod.LOGGER.info("Fake Rest ({}) at {} {} {}", f.id(), x, y, z);
	}

	/** The trap springs: the fires go red, the altar goes dead, and the thieves come. */
	static void spring(ServerLevel level, FraudSites.FakeRest f, int y, ServerPlayer victim) {
		HellState state = HellState.get(level.getServer());
		state.fraudSpent.add(f.id());
		state.setDirty();
		int x = f.x();
		int z = f.z();
		for (int[] c : new int[][]{{-4, -4}, {-4, 4}, {4, -4}, {4, 4}}) {
			set(level, x + c[0], y, z + c[1], Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
		}
		set(level, x, y, z, Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				set(level, x + dx, y - 1, z + dz, Blocks.BLACKSTONE.defaultBlockState());
			}
		}
		Signs.place(level, new BlockPos(x, y, z - 2), 8, List.of(
				Component.literal("A LIE").withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_RED),
				Component.literal("Malebolge's Rests"),
				Component.literal("are not on your"),
				Component.literal("compass.")));
		level.sendParticles(ParticleTypes.SOUL, x + 0.5, y + 1, z + 0.5, 40, 3, 1, 3, 0.05);
		level.playSound(null, new BlockPos(x, y, z), SoundEvents.EVOKER_PREPARE_WOLOLO, SoundSource.HOSTILE, 2.0f, 0.5f);
		AABB around = new AABB(new BlockPos(x, y, z)).inflate(12, 8, 12);
		for (Mimic m : level.getEntitiesOfClass(Mimic.class, around)) {
			m.wake(victim);
		}
		if (HellConfig.get().layerMonsters && level.getDifficulty() != Difficulty.PEACEFUL) {
			for (int i = 0; i < 2; i++) {
				double a = level.getRandom().nextDouble() * Math.PI * 2;
				int tx = (int) Math.floor(x + Math.cos(a) * 7);
				int tz = (int) Math.floor(z + Math.sin(a) * 7);
				Thief t = spawnThief(level, tx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tx, tz), tz);
				if (t != null) {
					t.setTarget(victim);
				}
			}
		}
		Dialogue.notice(victim, Component.literal("It's a trap: this Rest is a fake! Malebolge lies. The real Rests are on your compass (the gold diamond).")
				.withStyle(ChatFormatting.RED));
		CircleHazards.hintOnce(victim, HINT_FRAUD, "In Malebolge nothing is what it seems: some chests are mimics (watch for a lid that breathes), "
				+ "thieves snatch from your pack (kill them to get it back), and a Rest that isn't on your compass is a trap.");
		MapSync.unlockCodex(victim, "mimic");
		MapSync.unlockCodex(victim, "thief");
		HellcraftMod.LOGGER.info("Fake Rest {} sprung by {}", f.id(), victim.getGameProfile().name());
	}

	private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
		level.setBlock(new BlockPos(x, y, z), state, 3);
	}

	/** For the smoke test: Malebolge's caches and fake Rests are on its ridges. */
	public static String selfTest() {
		List<FraudSites.Cache> caches = FraudSites.caches();
		long mimics = caches.stream().filter(FraudSites.Cache::mimic).count();
		boolean onRidges = caches.stream().allMatch(c -> InfernoGeometry.zoneAt(c.x(), c.z()) == Zone.MALEBOLGE);
		boolean fakesOk = FraudSites.fakeRests().stream().allMatch(f -> InfernoGeometry.zoneAt(f.x(), f.z()) == Zone.MALEBOLGE);
		return "Fraud test: " + caches.size() + " caches (" + mimics + " mimics), on the ridges=" + onRidges + "; "
				+ FraudSites.fakeRests().size() + " fake Rests, on the ridges=" + fakesOk;
	}
}
