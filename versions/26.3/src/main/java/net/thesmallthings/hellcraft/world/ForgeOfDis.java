package net.thesmallthings.hellcraft.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.thesmallthings.hellcraft.block.HellBlocks;

import java.util.ArrayList;
import java.util.List;

/** The Forge of Dis in a running world (Phase 6): its self-test (/hellcraft forge test). The shape is ForgeGeometry. */
public final class ForgeOfDis {
	private ForgeOfDis() {
	}

	/** What each region is, in one plain tip, the first time you enter it (hint bits 45 to 50). */
	private static final String[] TIPS = {
			"The Great Forge: Vulcan's smithy on its mesa. The Hellforge inside upgrades blood gear (a netherite ingot and Blood "
					+ "Fragments). Vulcan wakes when you go in.",
			"The Slag Sea: molten slag round the Great Forge. Cross on its crust (the causeways run straight to the Forge's stairs) "
					+ "and keep moving: it cracks under you.",
			"The Foundry Halls: tall halls cut by rivers of lava. The pillars have ladders up to their arms, if you want to look around.",
			"The Chainlands: islands of rock hung on chains over the lava. Cross on the bridges; it's a long way down.",
			"The Ash Wastes: dunes of ash under a low roof. Piglins trade here, and there are bastions and fortresses (blaze rods are "
					+ "the way to Paradiso).",
			"The Cyclops Quarries: terraces cut in the rock, with ramps winding up them. The Cyclopes keep camps here: leave them by "
					+ "their fires, or fight them for their gold.",
	};
	private static int ticks;

	/** Every second: the first time someone enters a region of the Forge, a tip says what it is. */
	public static void tick(MinecraftServer server) {
		if (++ticks % 20 != 0 || !HellWorldgen.isInferno(server.overworld())) {
			return;
		}
		ServerLevel nether = server.getLevel(Level.NETHER);
		if (nether == null) {
			return;
		}
		for (net.minecraft.server.level.ServerPlayer p : nether.players()) {
			if (!p.isSpectator()) {
				ForgeGeometry.Region region = ForgeGeometry.regionAt(p.getX(), p.getZ());
				net.thesmallthings.hellcraft.hazard.CircleHazards.hintOnce(p, 45 + region.ordinal(), TIPS[region.ordinal()]);
			}
		}
	}

	/**
	 * Each region's biome stands where ForgeGeometry puts it, the causeways wear crust, and (once the Forge is built)
	 * its stairways come down the mesa.
	 */
	public static String selfTest(MinecraftServer server) {
		ServerLevel nether = server.getLevel(Level.NETHER);
		if (nether == null) {
			return "Forge test FAILED: no Nether";
		}
		List<String> wrong = new ArrayList<>();
		double[][] points = {{0, 0}, {75, 30}, {0, -250}, {250, 20}, {0, 250}, {-250, 0}};
		for (double[] p : points) {
			ForgeGeometry.Region expected = ForgeGeometry.regionAt(p[0], p[1]);
			nether.getChunk((int) p[0] >> 4, (int) p[1] >> 4);
			String biome = nether.getBiome(BlockPos.containing(p[0], 64, p[1])).unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
			if (!biome.equals(expected.id())) {
				wrong.add((int) p[0] + "," + (int) p[1] + " is " + biome + " not " + expected.id());
			}
		}
		int crust = 0;
		// past the foot of the east stairway (r 78), where the causeway goes on as crust
		for (int r = 80; r <= 100; r += 4) {
			nether.getChunk(r >> 4, 0);
			if (nether.getBlockState(new BlockPos(r, ForgeGeometry.LAVA - 1, 0)).is(HellBlocks.SLAG_CRUST)) {
				crust++;
			}
		}
		String stairs = "not built yet";
		if (GreatForge.built(server)) {
			int edge = (int) ForgeGeometry.MESA_RADIUS;
			int t = 10;
			stairs = nether.getBlockState(new BlockPos(edge + t, GreatForge.FLOOR_Y - 1 - t, 0)).is(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS)
					? "down the mesa" : "missing";
		}
		boolean ok = wrong.isEmpty() && crust >= 5 && !stairs.equals("missing");
		return (ok ? "Forge test OK: " : "Forge test FAILED: ") + (wrong.isEmpty() ? "six regions in place" : String.join("; ", wrong))
				+ ", crust on " + crust + " of 6 causeway spots, stairs " + stairs;
	}
}
