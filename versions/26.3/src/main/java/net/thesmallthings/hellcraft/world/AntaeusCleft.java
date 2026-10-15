package net.thesmallthings.hellcraft.world;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.entity.HellEntities;
import net.thesmallthings.hellcraft.entity.boss.AntaeusEntity;
import net.thesmallthings.hellcraft.util.Signs;

import java.util.List;

/**
 * Antaeus's place at the foot of the Well (built once, with the landmarks): a cleft his arm has worn in the cliff, the
 * giant standing in the ice at its mouth with his hand resting at its far end on the rim, a stop on the rim (crying
 * obsidian, a sign) where he lifts you down, and one on the ice where he lifts you back up. A low wall keeps people
 * from walking into the cleft.
 */
public final class AntaeusCleft {
	/** How far behind and beside the giant the cleft runs (blocks, in his frame: forward from his feet, to his right). */
	private static final double BACK = -4;
	private static final double LEFT = -5;
	private static final double RIGHT = 12;
	/** How far past his resting palm the cleft's far wall is. */
	private static final double PAST_PALM = 2.5;

	private AntaeusCleft() {
	}

	/** Builds the cleft and places Antaeus at this angle round the Well. */
	public static void build(ServerLevel level, double angle) {
		double cx = Math.cos(angle);
		double cz = Math.sin(angle);
		// the brink: walking in from the Well's floor, the first spot well below it
		double floorR = InfernoGeometry.outerRadius(Zone.COCYTUS) + 25;
		int floor = surface(level, cx * floorR, cz * floorR);
		double edge = InfernoGeometry.outerRadius(Zone.COCYTUS) - 20;
		for (double r = floorR; r > floorR - 60; r -= 0.5) {
			if (surface(level, cx * r, cz * r) < floor - 3) {
				edge = r;
				break;
			}
		}
		double wall = edge + 3;
		double palmR = wall - PAST_PALM;
		int rim = surface(level, cx * (wall + 1), cz * (wall + 1));
		Vec3 palm = new Vec3(cx * palmR, rim + 0.5, cz * palmR);
		float yRot = (float) Math.toDegrees(angle) - 90.0f;
		Vec3 feet = palm.subtract(AntaeusEntity.restingPalm(yRot));
		int ice = surface(level, cx * (edge - 20), cz * (edge - 20));

		// the giant's frame: forward (out toward the Well) and to his right
		double fx = cx;
		double fz = cz;
		double rx = -Math.cos(Math.toRadians(yRot));
		double rz = -Math.sin(Math.toRadians(yRot));
		double far = palm.subtract(feet).multiply(1, 0, 1).dot(new Vec3(fx, 0, fz)) + PAST_PALM;
		int x0 = (int) Math.floor(feet.x - 30);
		int z0 = (int) Math.floor(feet.z - 30);
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockState packed = Blocks.PACKED_ICE.defaultBlockState();
		BlockState fence = Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState();
		for (int x = x0; x <= x0 + 60; x++) {
			for (int z = z0; z <= z0 + 60; z++) {
				double dx = x + 0.5 - feet.x;
				double dz = z + 0.5 - feet.z;
				double f = dx * fx + dz * fz;
				double s = dx * rx + dz * rz;
				boolean inside = f >= BACK && f <= far && s >= LEFT && s <= RIGHT;
				if (inside) {
					for (int y = ice; y <= rim + 4; y++) {
						level.setBlock(new BlockPos(x, y, z), air, 2);
					}
					level.setBlock(new BlockPos(x, ice - 1, z), packed, 2);
				} else if (f >= BACK - 1.2 && f <= far + 1.2 && s >= LEFT - 1.2 && s <= RIGHT + 1.2) {
					// the brink round the cleft: a low wall on the rim, but none where you wait for his hand
					int top = surface(level, x + 0.5, z + 0.5);
					boolean atStop = f > far && Math.abs(s - 6.75) < 2.0;
					if (top >= rim - 2 && !atStop) {
						level.setBlock(new BlockPos(x, top, z), fence, 2);
					}
				}
			}
		}

		AntaeusEntity giant = HellEntities.ANTAEUS.create(level, EntitySpawnReason.EVENT);
		if (giant == null) {
			return;
		}
		giant.snapTo(feet.x, feet.y, feet.z, yRot, 0.0f);
		giant.setYBodyRot(yRot);
		giant.setYHeadRot(yRot);
		giant.setCustomName(Component.literal("Antaeus"));
		giant.setPersistenceRequired();
		level.addFreshEntity(giant);
		HellState state = HellState.get(level.getServer());
		state.antaeus = new double[]{feet.x, feet.y, feet.z, yRot};
		state.setDirty();

		// the two stops, marked, each with a sign that says what to do
		Vec3 up = giant.rimStop();
		Vec3 down = giant.iceStop();
		level.setBlock(BlockPos.containing(up.x, up.y - 1, up.z), Blocks.CRYING_OBSIDIAN.defaultBlockState(), 3);
		level.setBlock(BlockPos.containing(down.x, down.y - 1, down.z), Blocks.CRYING_OBSIDIAN.defaultBlockState(), 3);
		int facing = Math.floorMod(Math.round((yRot + 180.0f) / 22.5f), 16);
		Signs.place(level, BlockPos.containing(up.x + fx * 1.5 - rx * 2, up.y, up.z + fz * 1.5 - rz * 2), facing, List.of(
				Component.literal("ANTAEUS").withStyle(ChatFormatting.BOLD),
				Component.literal("Wait on the"),
				Component.literal("crying obsidian:"),
				Component.literal("he lifts you down")));
		Signs.place(level, BlockPos.containing(down.x + rx * 1.5, down.y, down.z + rz * 1.5), facing, List.of(
				Component.literal("ANTAEUS").withStyle(ChatFormatting.BOLD),
				Component.literal("Wait on the"),
				Component.literal("crying obsidian:"),
				Component.literal("he lifts you up")));
		HellcraftMod.LOGGER.info("Antaeus stands in the ice at {} {} {}, his hand on the rim at {} {} {}", (int) feet.x, (int) feet.y, (int) feet.z,
				(int) palm.x, (int) palm.y, (int) palm.z);
	}

	/** The angle round the Well where Antaeus stands. */
	public static double angle() {
		return InfernoGeometry.wayDownAngle(10) + InfernoGeometry.WAY_DOWN_STEP / 2;
	}

	/** /hellcraft antaeus test: he is there, his stops are marked and level, and the cleft is open where his hand goes down. */
	public static String selfTest(ServerLevel level) {
		double[] place = HellState.get(level.getServer()).antaeus;
		if (place == null) {
			return "Antaeus FAILED: he was never placed";
		}
		Vec3 feet = new Vec3(place[0], place[1], place[2]);
		float yRot = (float) place[3];
		for (int cx = -3; cx <= 3; cx++) {
			for (int cz = -3; cz <= 3; cz++) {
				level.getChunk(((int) Math.floor(feet.x) >> 4) + cx, ((int) Math.floor(feet.z) >> 4) + cz);
			}
		}
		// his entity loads a little after its chunk, so the test works from where he was placed
		Vec3 palm = feet.add(AntaeusEntity.restingPalm(yRot));
		Vec3 up = AntaeusEntity.rimStop(level, feet, yRot);
		Vec3 down = AntaeusEntity.iceStop(level, feet, yRot);
		List<String> wrong = new java.util.ArrayList<>();
		if (!level.getBlockState(BlockPos.containing(up.x, up.y - 1, up.z)).is(Blocks.CRYING_OBSIDIAN)) {
			wrong.add("no mark at the rim stop");
		}
		if (!level.getBlockState(BlockPos.containing(down.x, down.y - 1, down.z)).is(Blocks.CRYING_OBSIDIAN)) {
			wrong.add("no mark at the ice stop");
		}
		if (Math.abs(up.y - palm.y) > 2.0) {
			wrong.add(String.format("his palm (y %.1f) isn't level with the rim stop (y %.1f)", palm.y, up.y));
		}
		int blocked = 0;
		for (int y = (int) Math.floor(down.y) + 1; y < (int) Math.floor(up.y) - 1; y++) {
			if (!level.getBlockState(BlockPos.containing(palm.x, y, palm.z)).isAir()) {
				blocked++;
			}
		}
		if (blocked > 0) {
			wrong.add(blocked + " blocks in the way of his hand");
		}
		String where = String.format("rim stop %d %d %d, ice stop %d %d %d, a drop of %d", (int) up.x, (int) up.y, (int) up.z, (int) down.x, (int) down.y,
				(int) down.z, (int) (up.y - down.y));
		return wrong.isEmpty() ? "Antaeus OK: " + where : "Antaeus FAILED: " + String.join(", ", wrong) + " (" + where + ")";
	}

	private static int surface(ServerLevel level, double x, double z) {
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		level.getChunk(bx >> 4, bz >> 4);
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
	}
}
