package net.thesmallthings.hellcraft.world;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.world.feature.DisWallFeature;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The rooms built into the walls, from end to end: the Gate of Hell's wall and the Walls of Dis. Each wall is cut into bays,
 * ten blocks of its length apart, and a bay is a room (or a stack of them) built into the wall's thickness: its door opens on
 * Hell's side at ground level, a ladder climbs from floor to floor, and barred windows look out over the outside. Every eighth
 * bay is a watch tower: it stands on above the wall, with a lookout room and a watch fire at the top. Every room is furnished
 * by {@link Rooms}. Bays near a gate are left to the gatehouses, and a bay with water at its door is left out.
 *
 * Built once per bay, the first time a player comes within {@link #NEAR} blocks (HellState.wallRooms), so a world made before
 * them gets them too. Each bay is worked out from its place on the wall, so the rooms follow the curve.
 */
public final class WallRooms {
	private WallRooms() {
	}

	public enum Wall {GATE, DIS}

	/** Along a wall, a bay is this many blocks (its walls included), so bays stand side by side. */
	static final double SLOT = 10;
	/** Every this-many-th bay is a watch tower. */
	static final int TOWER_EVERY = 8;
	/** Bays within this many blocks of a gate's centre line are left to the gatehouses. */
	static final double GATE_CLEAR = 26;
	/** How near a soul comes before a bay is built. */
	static final double NEAR = 110;
	/** How far a watch tower stands above the wall's top. */
	static final int TOWER_RISE = 9;
	/** A bay's room: 9 cells along the wall, 5 across it, with its walls round it. */
	static final Rooms.Shape ROOM = Rooms.Shape.square(4, 2);
	/** The ladder's cells, and the entry lane (kept clear), in a bay's frame. */
	private static final java.util.function.BiPredicate<Integer, Integer> RESERVED = (a, b) -> b == -2 && a >= -3 && a <= 1;

	/** One bay: its id, which wall, its place (index) round the wall, its frame, and whether it's a watch tower. */
	public record Bay(String id, Wall wall, int index, Rooms.Frame frame, boolean tower) {
	}

	public static double radius(Wall w) {
		return w == Wall.GATE ? InfernoGeometry.GATE_WALL_RADIUS : InfernoGeometry.DIS_WALL_RADIUS;
	}

	/** How many bays go round a wall. */
	public static int count(Wall w) {
		return (int) Math.round(2 * Math.PI * radius(w) / SLOT);
	}

	/** Bay k of a wall: its frame sits on the wall's centre line, u along the wall, v out of Hell. */
	public static Bay bay(Wall w, int k) {
		int n = count(w);
		double a = 2 * Math.PI * k / n;
		double r = Gatehouses.crossing(a, radius(w));
		double cx = Math.floor(Math.cos(a) * r) + 0.5;
		double cz = Math.floor(Math.sin(a) * r) + 0.5;
		Rooms.Frame f = new Rooms.Frame(cx, cz, -Math.sin(a), Math.cos(a), Math.cos(a), Math.sin(a));
		return new Bay(w.name().toLowerCase(Locale.ROOT) + "_" + k, w, k, f, k % TOWER_EVERY == 0);
	}

	private static double[] gates(Wall w) {
		if (w == Wall.DIS) {
			return InfernoGeometry.DIS_GATE_ANGLES;
		}
		double[] out = new double[InfernoGeometry.GATE_COUNT];
		for (int k = 0; k < out.length; k++) {
			out[k] = InfernoGeometry.gateAngle(k);
		}
		return out;
	}

	/** False for bays within reach of a gate: the gatehouses stand there. */
	public static boolean clear(Wall w, int k) {
		double a = 2 * Math.PI * k / count(w);
		double r = radius(w);
		for (double g : gates(w)) {
			if (InfernoGeometry.angularDistance(a, g) * r < GATE_CLEAR) {
				return false;
			}
		}
		return true;
	}

	/** The air level on the ground at x, z: over the Wood's plants and under the water, not the walls (their stone is solid). */
	static int ground(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
		while (pos.getY() > level.getMinY() + 5) {
			BlockState s = level.getBlockState(pos);
			boolean plant = s.isAir() || s.canBeReplaced() || s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES);
			if (!plant) {
				break;
			}
			pos.move(Direction.DOWN);
		}
		return pos.getY() + 1;
	}

	/** The top block of the wall at x, z: the Walls of Dis stand to a fixed height, the Gate's wall to its ground plus its height. */
	static int wallTop(ServerLevel level, Wall w, int x, int z) {
		if (w == Wall.DIS) {
			return DisWallFeature.BASE_Y + InfernoGeometry.DIS_WALL_HEIGHT;
		}
		// down from above the wall to the first solid block: its top, or a crenel on the top (one block higher, every other one)
		int y = ground(level, x, z) + InfernoGeometry.GATE_WALL_HEIGHT + 6;
		while (y > level.getMinY()) {
			BlockState s = level.getBlockState(new BlockPos(x, y, z));
			if (s.isAir() || s.canBeReplaced() || s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES)) {
				y--;
			} else {
				break;
			}
		}
		if (level.getBlockState(new BlockPos(x, y, z)).is(Blocks.POLISHED_BLACKSTONE_BRICKS) && ((x + z) & 1) == 0) {
			y--;
		}
		return y;
	}

	/**
	 * Builds a bay (or watch tower). Returns {ground, top, the top floor's level} for the room, or null if none is built (water
	 * where its door goes, or no room high enough).
	 */
	public static int[] build(ServerLevel level, Bay bay) {
		Rooms.Frame f = bay.frame();
		boolean dis = bay.wall() == Wall.DIS;
		int cxi = (int) Math.floor(f.cx());
		int czi = (int) Math.floor(f.cz());
		// the door is on Hell's side (b = -3); the ground outside it (b = -4) is the first floor's level
		BlockPos outside = f.at(0, 0, -4);
		int ground = ground(level, outside.getX(), outside.getZ());
		if (!level.getFluidState(new BlockPos(outside.getX(), ground, outside.getZ())).isEmpty()
				|| !level.getFluidState(new BlockPos(outside.getX(), ground - 1, outside.getZ())).isEmpty()) {
			return null;
		}
		int roof = wallTop(level, bay.wall(), cxi, czi);
		int top = bay.tower() ? roof + TOWER_RISE : roof;
		int limit = (bay.tower() ? top : roof) - 3;
		List<Integer> floors = new ArrayList<>();
		for (int i = 0; Rooms.floorLevel(ground, i) <= limit; i++) {
			floors.add(Rooms.floorLevel(ground, i));
		}
		if (floors.isEmpty()) {
			return null;
		}
		int n = floors.size();
		Set<Integer> floorSet = new HashSet<>(floors);
		double wallRadius = radius(bay.wall());
		double half = bay.wall() == Wall.GATE ? InfernoGeometry.GATE_WALL_HALF_THICKNESS : InfernoGeometry.DIS_WALL_HALF_THICKNESS;
		RandomSource random = RandomSource.create(bay.id().hashCode());
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		// the bay itself: solid walls, a floor under each room, air inside, a roof and a crenel row on top
		for (int x = cxi - 8; x <= cxi + 8; x++) {
			for (int z = czi - 8; z <= czi + 8; z++) {
				double[] co = f.coords(x, z);
				if (!ROOM.reaches(co[0], co[1])) {
					continue;
				}
				// a room's air stays inside the wall (where the wobble pushes the wall's face out, its cells are wall instead)
				boolean inside = ROOM.has(co[0], co[1])
						&& Math.abs(InfernoGeometry.effectiveRadius(x + 0.5, z + 0.5) - wallRadius) <= half + 0.5;
				boolean crenel = ((x + z) & 1) == 0;
				level.getChunk(x >> 4, z >> 4);
				for (int y = ground - 1; y <= top + 1; y++) {
					BlockState state;
					if (y == top + 1) {
						state = !inside && crenel ? Rooms.stone(dis, random) : air;
					} else if (y == top || !inside) {
						state = Rooms.stone(dis, random);
					} else if (floorSet.contains(y + 1)) {
						state = Rooms.floorBlock(dis);
					} else {
						state = air;
					}
					level.setBlock(pos.set(x, y, z), state, 2);
				}
			}
		}
		// ladders climb from each floor to the next, through a hole in the floor above
		BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f.dir(0, 1));
		for (int i = 0; i + 1 < n; i++) {
			for (int y = floors.get(i); y <= floors.get(i + 1) - 1; y++) {
				level.setBlock(f.at(y, -3, -2), ladder, 2);
			}
		}
		// the door from the ground on Hell's side, cleared through the wall and the ground outside it
		for (int y = ground; y <= ground + 2; y++) {
			for (int b = -3; b >= -5; b--) {
				level.setBlock(f.at(y, 0, b), air, 2);
			}
		}
		// barred windows looking out, two blocks high on each floor
		BlockState bars = Blocks.IRON_BARS.defaultBlockState();
		for (int i = 0; i < n; i++) {
			int ceiling = i + 1 < n ? floors.get(i + 1) - 1 : top;
			for (int dy = 1; dy <= 2; dy++) {
				int y = floors.get(i) + dy;
				if (y < ceiling) {
					level.setBlock(f.at(y, -2, 3), bars, 2);
					level.setBlock(f.at(y, 2, 3), bars, 2);
				}
			}
		}
		// furnish each floor: the ground floor a guard room, the top one the war room (or the lookout, on a tower)
		Rooms.Kind prev = null;
		for (int i = 0; i < n; i++) {
			Rooms.Kind kind = i == 0 ? Rooms.Kind.GUARD : i == n - 1 ? (bay.tower() ? Rooms.Kind.LOOKOUT : Rooms.Kind.WAR)
					: Rooms.pick(dis, random, prev);
			prev = kind;
			int ceiling = i + 1 < n ? floors.get(i + 1) - 1 : top;
			Rooms.furnish(level, f, kind, floors.get(i), ceiling, ROOM, RESERVED, dis, random);
		}
		return new int[]{ground, top, floors.get(n - 1)};
	}

	/** Builds the bays of the walls that players are near (once per bay; a bay left out is marked as decided too). */
	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 40 != 17) {
			return;
		}
		ServerLevel level = server.overworld();
		if (!HellWorldgen.isInferno(level)) {
			return;
		}
		HellState state = HellState.get(server);
		int span = (int) Math.ceil(NEAR / SLOT) + 2;
		for (Wall w : Wall.values()) {
			double r = radius(w);
			int n = count(w);
			double step = 2 * Math.PI / n;
			for (ServerPlayer p : level.players()) {
				if (p.isSpectator() || Math.abs(Math.hypot(p.getX(), p.getZ()) - r) > NEAR + 4) {
					continue;
				}
				int k0 = (int) Math.round(Math.atan2(p.getZ(), p.getX()) / step);
				for (int d = -span; d <= span; d++) {
					int k = Math.floorMod(k0 + d, n);
					String id = w.name().toLowerCase(Locale.ROOT) + "_" + k;
					if (state.wallRooms.contains(id)) {
						continue;
					}
					Bay bay = bay(w, k);
					double dx = p.getX() - bay.frame().cx();
					double dz = p.getZ() - bay.frame().cz();
					if (dx * dx + dz * dz >= NEAR * NEAR) {
						continue;
					}
					if (clear(w, k)) {
						build(level, bay);
					}
					state.wallRooms.add(id);
					state.setDirty();
				}
			}
		}
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> commands(dispatcher));
	}

	/**
	 * For the smoke test: every bay on both walls (the ones away from the gates) sits inside its wall: its rooms are within the
	 * wall's thickness, and its skin at most a block outside it.
	 */
	public static String selfTest() {
		List<String> wrong = new ArrayList<>();
		int bays = 0;
		int towers = 0;
		int skipped = 0;
		for (Wall w : Wall.values()) {
			double r = radius(w);
			double half = w == Wall.GATE ? InfernoGeometry.GATE_WALL_HALF_THICKNESS : InfernoGeometry.DIS_WALL_HALF_THICKNESS;
			for (int k = 0; k < count(w); k++) {
				if (!clear(w, k)) {
					skipped++;
					continue;
				}
				Bay bay = bay(w, k);
				bays++;
				if (bay.tower()) {
					towers++;
				}
				Rooms.Frame f = bay.frame();
				int cx = (int) Math.floor(f.cx());
				int cz = (int) Math.floor(f.cz());
				for (int x = cx - 8; x <= cx + 8; x++) {
					for (int z = cz - 8; z <= cz + 8; z++) {
						double[] co = f.coords(x, z);
						if (!ROOM.reaches(co[0], co[1])) {
							continue;
						}
						// a room's own cells are inside the wall (within a block); a skin cell can stick out a block where the wobble is steep
						double off = Math.abs(InfernoGeometry.effectiveRadius(x + 0.5, z + 0.5) - r);
						boolean room = ROOM.has(co[0], co[1]);
						if ((room && off > half + 1.0) || off > half + 2.5) {
							if (wrong.size() < 5) {
								wrong.add(bay.id() + " sticks out of its wall at " + x + ", " + z);
							}
						}
					}
				}
			}
		}
		return wrong.isEmpty() ? "Wall rooms test OK: " + bays + " bays (" + towers + " of them watch towers) along both walls, " + skipped
				+ " left to the gatehouses, each inside its wall" : "Wall rooms test FAILED: " + String.join("; ", wrong);
	}

	private static void commands(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("hellcraft")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("wallrooms")
						.then(Commands.literal("test").executes(ctx -> {
							String result = selfTest();
							HellcraftMod.LOGGER.info(result);
							ctx.getSource().sendSuccess(() -> Component.literal(result), false);
							return 1;
						}))
						.then(Commands.literal("build").executes(ctx -> {
							ServerLevel level = ctx.getSource().getServer().overworld();
							double px = ctx.getSource().getPosition().x;
							double pz = ctx.getSource().getPosition().z;
							Wall w = Math.abs(Math.hypot(px, pz) - radius(Wall.GATE)) < Math.abs(Math.hypot(px, pz) - radius(Wall.DIS))
									? Wall.GATE : Wall.DIS;
							int k = Math.floorMod((int) Math.round(Math.atan2(pz, px) / (2 * Math.PI / count(w))), count(w));
							Bay bay = bay(w, k);
							int[] built = build(level, bay);
							ctx.getSource().sendSuccess(() -> Component.literal(built == null ? "No room could be built there (water at its door?)."
									: "Built the wall room " + bay.id() + " at " + (int) bay.frame().cx() + ", " + (int) bay.frame().cz() + ".")
									.withStyle(ChatFormatting.GOLD), false);
							return 1;
						}))));
	}
}
