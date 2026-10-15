package net.thesmallthings.hellcraft.hazard;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.entity.HellEntities;
import net.thesmallthings.hellcraft.entity.monster.Centaur;
import net.thesmallthings.hellcraft.entity.monster.Harpy;
import net.thesmallthings.hellcraft.entity.monster.Heretic;
import net.thesmallthings.hellcraft.net.MapSync;
import net.thesmallthings.hellcraft.world.HellWorldgen;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.Zone;

import java.util.ArrayList;
import java.util.List;

/**
 * Lower Hell's own monsters, brought out near the souls passing through: a centaur on each bank beside every ford of
 * the river of blood (they keep their posts), the heretics who climb out of the burning tombs, and the harpies the
 * Wood's trees call when they scream (LowerRules calls these two). layerMonsters turns them all off.
 */
public final class LowerMonsters {
	private LowerMonsters() {
	}

	/** A centaur's post: which ford, which bank, and where it stands. */
	public record Post(int ford, boolean outer, double x, double z) {
		String tag() {
			return "hellcraft_post_" + ford + (outer ? "o" : "i");
		}
	}

	private static List<Post> posts;
	/** How close a soul must come to a ford for its centaurs to take their posts. */
	private static final double POST_RANGE = 96;
	private static final int HERETICS_NEAR = 4;

	/** The posts beside each ford: on both banks, a little to one side of the crossing, on firm ground. */
	public static synchronized List<Post> posts() {
		if (posts != null) {
			return posts;
		}
		List<Post> out = new ArrayList<>();
		int ford = 0;
		for (int k = 0; k < 12; k++) {
			if (k % 3 == 0) {
				continue;
			}
			double a = InfernoGeometry.wayDownAngle(k);
			for (boolean outer : new boolean[]{true, false}) {
				// stand 9 blocks to one side of the ford, at the first dry ground back from the blood
				double side = 9.0 / 1065.0;
				double b = a + (outer ? side : -side);
				double r0 = outer ? 1190 : 960;
				double step = outer ? -1 : 1;
				double bank = r0;
				for (double r = r0; outer ? r > 1000 : r < 1150; r += step) {
					if (InfernoGeometry.fluidAt(Math.cos(b) * r, Math.sin(b) * r) != InfernoGeometry.Fluid.NONE) {
						bank = r - step * 3;
						break;
					}
				}
				out.add(new Post(ford, outer, Math.cos(b) * bank, Math.sin(b) * bank));
			}
			ford++;
		}
		posts = out;
		return posts;
	}

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 100 != 50 || !HellConfig.get().layerMonsters) {
			return;
		}
		ServerLevel level = server.overworld();
		if (!HellWorldgen.isInferno(level) || level.getDifficulty() == Difficulty.PEACEFUL) {
			return;
		}
		for (Post post : posts()) {
			ServerPlayer near = null;
			for (ServerPlayer p : level.players()) {
				if (!p.isSpectator() && Math.hypot(p.getX() - post.x(), p.getZ() - post.z()) < POST_RANGE) {
					near = p;
					break;
				}
			}
			if (near == null) {
				continue;
			}
			BlockPos at = BlockPos.containing(post.x(), 0, post.z());
			if (!level.isLoaded(at)) {
				continue;
			}
			boolean manned = !level.getEntitiesOfClass(Centaur.class, new AABB(post.x() - 48, -64, post.z() - 48, post.x() + 48, 320, post.z() + 48),
					c -> c.entityTags().contains(post.tag())).isEmpty();
			if (!manned) {
				man(level, post);
			}
			MapSync.unlockCodex(near, "centaur");
		}
	}

	private static void man(ServerLevel level, Post post) {
		Centaur centaur = HellEntities.CENTAUR.create(level, EntitySpawnReason.NATURAL);
		if (centaur == null) {
			return;
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(post.x()), (int) Math.floor(post.z()));
		centaur.snapTo(post.x(), y, post.z(), level.getRandom().nextFloat() * 360, 0);
		centaur.addTag(post.tag());
		centaur.post(new Vec3(post.x(), y, post.z()));
		level.addFreshEntity(centaur);
	}

	/** A heretic climbs out of a tomb that just blasted (now and then, and not too many at once). */
	public static void heretic(ServerLevel level, Vec3 tomb, ServerPlayer witness) {
		if (!HellConfig.get().layerMonsters || level.getDifficulty() == Difficulty.PEACEFUL || level.getRandom().nextFloat() > 0.12f) {
			return;
		}
		if (level.getEntitiesOfClass(Heretic.class, new AABB(BlockPos.containing(tomb)).inflate(32)).size() >= HERETICS_NEAR) {
			return;
		}
		Heretic heretic = HellEntities.HERETIC.create(level, EntitySpawnReason.EVENT);
		if (heretic == null) {
			return;
		}
		heretic.snapTo(tomb.x, tomb.y, tomb.z, level.getRandom().nextFloat() * 360, 0);
		level.addFreshEntity(heretic);
		heretic.rise();
		MapSync.unlockCodex(witness, "heretic");
	}

	/** The harpies come out of the canopy, crying, when the trees scream. */
	public static void harpies(ServerLevel level, ServerPlayer prey, int count) {
		if (!HellConfig.get().layerMonsters || level.getDifficulty() == Difficulty.PEACEFUL) {
			return;
		}
		for (int i = 0; i < count; i++) {
			Harpy harpy = HellEntities.HARPY.create(level, EntitySpawnReason.EVENT);
			if (harpy == null) {
				return;
			}
			double a = level.getRandom().nextDouble() * Math.PI * 2;
			double d = 12 + level.getRandom().nextDouble() * 6;
			double x = prey.getX() + Math.cos(a) * d;
			double z = prey.getZ() + Math.sin(a) * d;
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
			harpy.snapTo(x, Math.max(ground, prey.getY()) + 8 + level.getRandom().nextInt(4), z, (float) Math.toDegrees(a) + 90, 0);
			harpy.setTarget(prey);
			level.addFreshEntity(harpy);
			harpy.shriek();
		}
		MapSync.unlockCodex(prey, "harpy");
	}

	/** For the smoke test: every ford has a post on each bank, on dry ground in Violence. */
	public static String selfTest() {
		List<Post> all = posts();
		boolean dry = all.stream().allMatch(p -> InfernoGeometry.fluidAt(p.x(), p.z()) == InfernoGeometry.Fluid.NONE);
		long inViolence = all.stream().filter(p -> InfernoGeometry.zoneAt(p.x(), p.z()).circle() == Zone.PHLEGETHON.circle()
				|| InfernoGeometry.zoneAt(p.x(), p.z()) == Zone.HERESY).count();
		return "Lower monsters test: " + all.size() + " centaur posts, all dry=" + dry + ", by the river=" + (inViolence == all.size());
	}
}
