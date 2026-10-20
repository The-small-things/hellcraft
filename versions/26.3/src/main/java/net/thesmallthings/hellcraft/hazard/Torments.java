package net.thesmallthings.hellcraft.hazard;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.entity.HellEntities;
import net.thesmallthings.hellcraft.entity.npc.Damned.Punishment;
import net.thesmallthings.hellcraft.entity.npc.Damned;
import net.thesmallthings.hellcraft.entity.npc.Shade;
import net.thesmallthings.hellcraft.world.HellWorldgen;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.Shrines;
import net.thesmallthings.hellcraft.world.Spine;
import net.thesmallthings.hellcraft.world.Tombs;
import net.thesmallthings.hellcraft.world.Zone;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The damned in their torments (entity/npc/Damned): every circle shows its own punishment, after Dante, to the souls
 * passing through. Every two seconds, near each player in Hell, a few of the damned are brought out 16 to 40 blocks
 * off (never in a guardian's arena, on a Pilgrim Road or in a Virgil's Rest), up to a handful each:
 * <ul>
 *     <li>the Vestibule: a line of the neutrals running after a white banner, stung as they go;</li>
 *     <li>Limbo: a few plain souls, longing and sighing (the Noble Castle's great shades sigh too);</li>
 *     <li>Lust: souls blown round and round over the canyons by the storm;</li>
 *     <li>Gluttony: souls lying half-sunk in the mud;</li>
 *     <li>Greed: pairs rolling great weights of gold at each other, clashing, turning back;</li>
 *     <li>Wrath: pairs fighting in the Styx's shallows;</li>
 *     <li>Heresy: a soul in its burning tomb;</li>
 *     <li>Violence: souls sunk in the river of blood, some to the waist, some to the eyes, and on the Burning Sands
 *     souls running and lying under the fire;</li>
 *     <li>Fraud: the hypocrites' slow procession in gilded cloaks, along the ditches of Malebolge;</li>
 *     <li>Treachery: souls frozen in the ice of Cocytus to the neck (never in Judecca, where Lucifer is).</li>
 * </ul>
 * The Wood of the Suicides adds none: its trees are the souls, and they scream already (LowerRules). The damned can't
 * be hurt and the circles' hazards don't touch them. They scream now and then, but never more than one cry every
 * three seconds near anyone (mayCry). Config damnedSouls turns them off.
 */
public final class Torments {
	private Torments() {
	}

	/** How near a player the damned are counted, and how far off they are brought out. */
	private static final double NEAR = 48;
	private static final double FROM = 16;
	private static final double TO = 40;
	/** The least time between two cries heard by the same player (ticks), and how far a cry carries for that. */
	private static final int CRY_GAP = 60;
	private static final double CRY_RANGE = 40;
	private static final Map<UUID, Long> LAST_CRY = new HashMap<>();

	/** How many of the damned a circle shows near one player. */
	private static final Map<Punishment, Integer> CAP = new EnumMap<>(Map.ofEntries(
			Map.entry(Punishment.VESTIBULE, 7), Map.entry(Punishment.LIMBO, 3), Map.entry(Punishment.LUST, 6),
			Map.entry(Punishment.GLUTTONY, 6), Map.entry(Punishment.GREED, 6), Map.entry(Punishment.WRATH, 6),
			Map.entry(Punishment.HERESY, 5), Map.entry(Punishment.PHLEGETHON, 6), Map.entry(Punishment.SANDS, 6),
			Map.entry(Punishment.MALEBOLGE, 6), Map.entry(Punishment.COCYTUS, 8)));

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 40 != 20) {
			return;
		}
		long now = server.getTickCount();
		LAST_CRY.values().removeIf(t -> now - t > CRY_GAP * 4L);
		ServerLevel level = server.overworld();
		if (!HellConfig.get().damnedSouls || !HellWorldgen.isInferno(level)) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator()) {
				continue;
			}
			Zone zone = InfernoGeometry.zoneAt(p.getX(), p.getZ());
			if (zone == Zone.LIMBO) {
				shadesSigh(level, p);
			}
			for (Punishment punishment : of(zone)) {
				int near = level.getEntitiesOfClass(Damned.class, new AABB(p.blockPosition()).inflate(NEAR), d -> d.punishment() == punishment).size();
				if (near < CAP.get(punishment)) {
					spawnNear(level, punishment, p.getX(), p.getZ(), FROM, TO, 6);
				}
			}
		}
	}

	/** Which punishments show in a zone (the river of blood runs past the edges of its own ring). */
	public static List<Punishment> of(Zone zone) {
		return switch (zone) {
			case VESTIBULE -> List.of(Punishment.VESTIBULE);
			case LIMBO -> List.of(Punishment.LIMBO);
			case LUST -> List.of(Punishment.LUST);
			case GLUTTONY -> List.of(Punishment.GLUTTONY);
			case GREED -> List.of(Punishment.GREED);
			case STYX -> List.of(Punishment.WRATH);
			case HERESY -> List.of(Punishment.HERESY, Punishment.PHLEGETHON);
			case PHLEGETHON, WOOD_OF_SUICIDES -> List.of(Punishment.PHLEGETHON);
			case BURNING_SANDS -> List.of(Punishment.SANDS);
			case MALEBOLGE, MALEBOLGE_BLIGHT, MALEBOLGE_PITCH -> List.of(Punishment.MALEBOLGE);
			case COCYTUS -> List.of(Punishment.COCYTUS);
			default -> List.of();
		};
	}

	/**
	 * Brings out one group suffering this punishment somewhere between `from` and `to` blocks of (x, z), trying a few
	 * spots; returns the souls it brought out (empty if no spot would do).
	 */
	public static List<Damned> spawnNear(ServerLevel level, Punishment punishment, double x, double z, double from, double to, int tries) {
		RandomSource random = level.getRandom();
		for (int i = 0; i < tries; i++) {
			double a = random.nextDouble() * Math.PI * 2;
			double d = from + random.nextDouble() * (to - from);
			List<Damned> group = spawnAt(level, punishment, (int) Math.floor(x + Math.cos(a) * d), (int) Math.floor(z + Math.sin(a) * d));
			if (!group.isEmpty()) {
				return group;
			}
		}
		return List.of();
	}

	/** Brings out one group at this column, if the punishment can be shown there. */
	public static List<Damned> spawnAt(ServerLevel level, Punishment punishment, int x, int z) {
		Vec3 at = site(level, punishment, x, z);
		if (at == null) {
			return List.of();
		}
		RandomSource random = level.getRandom();
		List<Damned> out = new ArrayList<>();
		switch (punishment) {
			case VESTIBULE, MALEBOLGE -> {
				// a line round the circle: the leader in front, the rest strung out behind
				int way = random.nextBoolean() ? 1 : -1;
				int count = punishment == Punishment.VESTIBULE ? 4 + random.nextInt(3) : 3 + random.nextInt(3);
				double r = Math.hypot(at.x, at.z);
				double a0 = Math.atan2(at.z, at.x);
				Damned ahead = null;
				for (int i = 0; i < count; i++) {
					double a = a0 - way * i * 1.8 / r;
					int sx = (int) Math.floor(Math.cos(a) * r);
					int sz = (int) Math.floor(Math.sin(a) * r);
					Vec3 spot = i == 0 ? at : site(level, punishment, sx, sz);
					if (spot == null) {
						break;
					}
					Damned d = create(level, punishment, spot, false);
					if (d == null) {
						break;
					}
					d.follow(ahead, way, ahead == null && punishment == Punishment.VESTIBULE);
					out.add(d);
					ahead = d;
				}
			}
			case GREED -> {
				// two souls facing each other across a stretch of level ground, each with its weight
				double a = random.nextDouble() * Math.PI * 2;
				double half = 4.5 + random.nextDouble() * 2;
				Vec3 one = site(level, punishment, (int) Math.floor(at.x + Math.cos(a) * half), (int) Math.floor(at.z + Math.sin(a) * half));
				Vec3 two = site(level, punishment, (int) Math.floor(at.x - Math.cos(a) * half), (int) Math.floor(at.z - Math.sin(a) * half));
				if (one == null || two == null || Math.abs(one.y - at.y) > 2 || Math.abs(two.y - at.y) > 2) {
					return List.of();
				}
				Damned d1 = create(level, punishment, one, false);
				Damned d2 = create(level, punishment, two, false);
				if (d1 == null || d2 == null) {
					discard(d1, d2);
					return List.of();
				}
				d1.pair(d2, at, 1, 0);
				d2.pair(d1, at, -1, 0);
				face(d1, at);
				face(d2, at);
				out.add(d1);
				out.add(d2);
			}
			case WRATH -> {
				// two souls at each other in the shallows
				double phase = random.nextDouble() * Math.PI * 2;
				Damned d1 = create(level, punishment, at, true);
				Damned d2 = create(level, punishment, at, true);
				if (d1 == null || d2 == null) {
					discard(d1, d2);
					return List.of();
				}
				d1.pair(d2, at, 1, phase);
				d2.pair(d1, at, -1, phase);
				out.add(d1);
				out.add(d2);
			}
			case LUST -> {
				// blown round a wide circle high over the canyons
				Vec3 centre = at.add(0, 9 + random.nextInt(8), 0);
				Damned d = create(level, punishment, centre, true);
				if (d != null) {
					d.blown(5 + random.nextDouble() * 9, 0.025 + random.nextDouble() * 0.025, 1.5 + random.nextDouble() * 2.5);
					out.add(d);
				}
			}
			case SANDS -> {
				int role = random.nextInt(3);
				Damned d = create(level, punishment, role == 1 ? at.add(0, 0.05, 0) : at, role == 1);
				if (d != null) {
					d.lap(role == 0 ? 2.5 + random.nextDouble() * 2 : 0, role == 1);
					out.add(d);
				}
			}
			default -> {
				Damned d = create(level, punishment, at, !punishment.walks);
				if (d != null) {
					out.add(d);
				}
			}
		}
		for (Damned d : out) {
			level.addFreshEntity(d);
		}
		return out;
	}

	@Nullable
	private static Damned create(ServerLevel level, Punishment punishment, Vec3 at, boolean pinned) {
		Damned d = HellEntities.DAMNED.create(level, EntitySpawnReason.EVENT);
		return d == null ? null : d.punish(punishment, at, pinned);
	}

	private static void discard(@Nullable Damned... souls) {
		for (Damned d : souls) {
			if (d != null) {
				d.discard();
			}
		}
	}

	private static void face(Damned d, Vec3 to) {
		float yaw = (float) Math.toDegrees(Math.atan2(-(to.x - d.getX()), to.z - d.getZ()));
		d.setYRot(yaw);
		d.setYBodyRot(yaw);
		d.setYHeadRot(yaw);
	}

	// ------------------------------------------------------------------ where each punishment can be shown

	/**
	 * Where a soul suffering this punishment would be at column (x, z) (its feet; for Lust, the ground under the storm),
	 * or null if this column won't do: the wrong circle, a road, a lair, a Rest, or the wrong ground.
	 */
	@Nullable
	public static Vec3 site(ServerLevel level, Punishment punishment, int x, int z) {
		double cx = x + 0.5;
		double cz = z + 0.5;
		if (!level.isLoaded(new BlockPos(x, 0, z)) || InfernoGeometry.roadFactor(cx, cz) > 0
				|| InfernoGeometry.nearLair(cx, cz, InfernoGeometry.LAIR_CLEARANCE + 12)) {
			return null;
		}
		Zone zone = InfernoGeometry.zoneAt(cx, cz);
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos feet = new BlockPos(x, ground, z);
		if (Shrines.near(level, feet)) {
			return null;
		}
		BlockState below = level.getBlockState(feet.below());
		boolean dry = level.getFluidState(feet.below()).isEmpty() && level.getFluidState(feet).isEmpty();
		return switch (punishment) {
			case VESTIBULE -> zone == Zone.VESTIBULE && dry ? new Vec3(cx, ground, cz) : null;
			case LIMBO -> zone == Zone.LIMBO && dry ? new Vec3(cx, ground, cz) : null;
			case LUST -> zone == Zone.LUST && level.canSeeSky(feet) ? new Vec3(cx, ground, cz) : null;
			// half sunk in the soft mud
			case GLUTTONY -> zone == Zone.GLUTTONY && dry && LayerRules.soft(below) && level.canSeeSky(feet) ? new Vec3(cx, ground - 0.15, cz) : null;
			case GREED -> zone == Zone.GREED && dry && below.isSolid() ? new Vec3(cx, ground, cz) : null;
			case WRATH -> zone == Zone.STYX ? shallows(level, x, z) : null;
			case HERESY -> zone == Zone.HERESY ? tomb(level, x, z) : null;
			case PHLEGETHON -> blood(level, x, z);
			case SANDS -> zone == Zone.BURNING_SANDS && dry && level.canSeeSky(feet) && sand(below) ? new Vec3(cx, ground, cz) : null;
			case MALEBOLGE -> zone == Zone.MALEBOLGE_BLIGHT && dry ? new Vec3(cx, ground, cz) : null;
			case COCYTUS -> zone == Zone.COCYTUS && !Spine.onSpine(cx, cz, 8) ? ice(level, x, ground, z) : null;
		};
	}

	/** The Styx where it is waist to chest deep: the soul stands on the bottom. */
	@Nullable
	private static Vec3 shallows(ServerLevel level, int x, int z) {
		int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
		int bottom = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
		int depth = surface - bottom;
		if (depth < 1 || depth > 2 || !level.getFluidState(new BlockPos(x, surface - 1, z)).is(FluidTags.WATER)) {
			return null;
		}
		return new Vec3(x + 0.5, bottom, z + 0.5);
	}

	/** An open burning tomb near (x, z) with nobody in it yet: the soul stands in its fire, from the waist up over the rim. */
	@Nullable
	private static Vec3 tomb(ServerLevel level, int x, int z) {
		for (Tombs.Tomb t : Tombs.within(x - 6, z - 6, x + 6, z + 6)) {
			int tx = (int) Math.floor(t.centreX());
			int tz = (int) Math.floor(t.centreZ());
			if (!level.isLoaded(new BlockPos(tx, 0, tz))) {
				continue;
			}
			int fire = level.getHeight(Heightmap.Types.MOTION_BLOCKING, tx, tz);
			BlockState flame = level.getBlockState(new BlockPos(tx, fire, tz));
			if (!flame.is(Blocks.FIRE) && !flame.is(Blocks.SOUL_FIRE) && !flame.isAir()) {
				continue;
			}
			Vec3 spot = new Vec3(tx + 0.5, fire + 0.9, tz + 0.5);
			if (level.getEntitiesOfClass(Damned.class, new AABB(spot, spot).inflate(2)).isEmpty()) {
				return spot;
			}
		}
		return null;
	}

	/** Sunk in the river of blood: some to the waist, some to the chest, the throat, the eyes. */
	@Nullable
	private static Vec3 blood(ServerLevel level, int x, int z) {
		if (InfernoGeometry.fluidAt(x + 0.5, z + 0.5) != InfernoGeometry.Fluid.LAVA) {
			return null;
		}
		int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
		int bottom = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
		if (surface - bottom < 2 || !level.getFluidState(new BlockPos(x, surface - 1, z)).is(FluidTags.LAVA)) {
			return null;
		}
		double[] sinks = {0.85, 1.15, 1.45, 1.75};
		return new Vec3(x + 0.5, surface - sinks[level.getRandom().nextInt(sinks.length)], z + 0.5);
	}

	private static boolean sand(BlockState state) {
		return state.is(Blocks.RED_SAND) || state.is(Blocks.SAND) || state.is(Blocks.NETHERRACK) || state.is(Blocks.RED_SANDSTONE);
	}

	/** Frozen to the neck in solid ice (never clear ice, which would show the body). */
	@Nullable
	private static Vec3 ice(ServerLevel level, int x, int ground, int z) {
		for (int dy = 1; dy <= 3; dy++) {
			BlockState s = level.getBlockState(new BlockPos(x, ground - dy, z));
			if (!(s.is(Blocks.PACKED_ICE) || s.is(Blocks.BLUE_ICE) || s.is(Blocks.SNOW_BLOCK))) {
				return null;
			}
		}
		return new Vec3(x + 0.5, ground - 1.35, z + 0.5);
	}

	// ------------------------------------------------------------------ cries

	/**
	 * Whether a cry may sound here now: only if someone is near enough to hear it and nobody near heard one in the last
	 * three seconds (then it counts as heard). Keeps the circles from being a wall of noise.
	 */
	public static boolean mayCry(ServerLevel level, Vec3 at) {
		long now = level.getServer().getTickCount();
		List<ServerPlayer> hearing = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (p.position().distanceToSqr(at) < CRY_RANGE * CRY_RANGE) {
				if (now - LAST_CRY.getOrDefault(p.getUUID(), Long.MIN_VALUE / 2) < CRY_GAP) {
					return false;
				}
				hearing.add(p);
			}
		}
		hearing.forEach(p -> LAST_CRY.put(p.getUUID(), now));
		return !hearing.isEmpty();
	}

	/** Limbo's great shades sigh too, now and then ("sighs, not lamentations"). */
	private static void shadesSigh(ServerLevel level, ServerPlayer p) {
		if (level.getRandom().nextFloat() > 0.15f) {
			return;
		}
		List<Shade> shades = level.getEntitiesOfClass(Shade.class, new AABB(p.blockPosition()).inflate(24), Shade::isAlive);
		if (shades.isEmpty()) {
			return;
		}
		Shade shade = shades.get(level.getRandom().nextInt(shades.size()));
		if (mayCry(level, shade.position())) {
			level.playSound(null, shade.getX(), shade.getEyeY(), shade.getZ(), Damned.SIGH, SoundSource.AMBIENT, 0.9f,
					0.85f + level.getRandom().nextFloat() * 0.2f);
		}
	}

	// ------------------------------------------------------------------ the commands

	/** /hellcraft damned spawn (punishment): brings out a group suffering it near here (it must be shown in this circle). */
	public static String spawnCommand(ServerLevel level, Vec3 at, String id) {
		Punishment punishment = Punishment.byId(id);
		if (punishment == null) {
			return "No such punishment. Try: " + String.join(", ", java.util.Arrays.stream(Punishment.values()).map(Punishment::id).toList());
		}
		List<Damned> group = spawnNear(level, punishment, at.x, at.z, 4, 24, 200);
		if (group.isEmpty()) {
			return "Nowhere near here to show " + punishment.id() + " (each punishment shows only in its own circle, off the roads and out of the arenas).";
		}
		Vec3 first = group.getFirst().position();
		return String.format(java.util.Locale.ROOT, "Brought out %d of the damned (%s) at %.1f %.1f %.1f", group.size(), punishment.id(), first.x, first.y,
				first.z);
	}

	// ------------------------------------------------------------------ the self-test

	/**
	 * For the smoke test: one soul of each punishment brought out by the test spot, then struck with everything the
	 * circles have (fire, lava, falling, drowning, frost, magic, a blow) and must take no harm; then where each
	 * punishment can be shown in the chunks the smoke test loads along the east road.
	 */
	public static String selfTest(ServerLevel level) {
		int x = 3247;
		int z = 1875;
		if (!level.isLoaded(new BlockPos(x, 0, z))) {
			BlockPos spawn = level.getRespawnData().pos();
			x = spawn.getX();
			z = spawn.getZ();
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
		List<DamageSource> sources = List.of(level.damageSources().generic(), level.damageSources().inFire(), level.damageSources().lava(),
				level.damageSources().onFire(), level.damageSources().fall(), level.damageSources().drown(), level.damageSources().freeze(),
				level.damageSources().magic(), level.damageSources().cactus(), level.damageSources().hotFloor(), level.damageSources().inWall());
		boolean invulnerable = true;
		boolean fireproof = true;
		boolean unpushable = true;
		int count = 0;
		List<Damned> made = new ArrayList<>();
		for (Punishment p : Punishment.values()) {
			Damned d = create(level, p, new Vec3(x + 0.5, y + 4, z + 0.5 + count * 2), !p.walks);
			if (d == null) {
				continue;
			}
			level.addFreshEntity(d);
			made.add(d);
			count++;
			for (DamageSource source : sources) {
				invulnerable &= !d.hurtServer(level, source, 50f) && d.getHealth() == d.getMaxHealth();
			}
			Vec3 before = d.position();
			d.push(2, 1, 2);
			d.knockback(2, 1, 1, level.damageSources().generic(), 1f, false);
			unpushable &= !d.isPushable() && d.getDeltaMovement().lengthSqr() < 1e-6 && d.position().equals(before);
			fireproof &= d.fireImmune() && d.isAlive();
		}
		made.forEach(Damned::discard);
		// the smoke test loads one chunk (x .. x + 15, z 0 .. 15) in every ring along the east road
		Map<Punishment, Integer> chunks = new EnumMap<>(Map.ofEntries(Map.entry(Punishment.VESTIBULE, 4620), Map.entry(Punishment.LIMBO, 4155),
				Map.entry(Punishment.LUST, 3637), Map.entry(Punishment.GLUTTONY, 3150), Map.entry(Punishment.GREED, 2700),
				Map.entry(Punishment.WRATH, 2295), Map.entry(Punishment.HERESY, 1860), Map.entry(Punishment.PHLEGETHON, 1597),
				Map.entry(Punishment.SANDS, 1282), Map.entry(Punishment.MALEBOLGE, 960), Map.entry(Punishment.COCYTUS, 397)));
		List<String> found = new ArrayList<>();
		for (Map.Entry<Punishment, Integer> e : chunks.entrySet()) {
			int cx = e.getValue() & ~15;
			search:
			for (int dx = 0; dx < 16; dx++) {
				for (int dz = 0; dz < 16; dz++) {
					if (site(level, e.getKey(), cx + dx, dz) != null) {
						found.add(e.getKey().id());
						break search;
					}
				}
			}
		}
		boolean sounds = Damned.SCREAM.location().equals(net.thesmallthings.hellcraft.HellcraftMod.id("entity.damned.scream"));
		return "Damned test: " + count + " punishments, all invulnerable=" + invulnerable + ", fireproof=" + fireproof + ", unpushable=" + unpushable
				+ ", sounds=" + sounds + "; sites by the east road: " + found.size() + "/" + chunks.size() + " " + found;
	}
}
