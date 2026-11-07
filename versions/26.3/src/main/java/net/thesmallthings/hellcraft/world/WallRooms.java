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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.world.feature.DisWallFeature;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The walls lived in, all the way round: the Gate of Hell's wall and the Walls of Dis, cut into bays ten blocks long.
 *
 * A bay is a stack of rooms built into the wall's thickness (9 blocks along it, 5 across), following its curve. Its door
 * opens on Hell's side at the foot of the wall; a newel stair (a stair winding round a stone post, in an axis-aligned 3 by 3
 * corner) climbs through every floor; each floor is a furnished room ({@link Rooms}), with barred windows looking out. Floors
 * are 8 blocks apart, counted down from the wall's top (so the rooms of neighbouring bays on Dis share their heights).
 *
 * Every eighth bay is a watch tower: a square tower 11 blocks across standing out from both faces of the wall and 9 blocks
 * above it, with the same stair from its door on Hell's side up through its rooms to a floor level with the wall's top (with
 * doors out onto the walk, wherever the wall meets the tower) and on to an open, crenellated top with a watch fire.
 *
 * Over every bay the top of the wall becomes a walk, clear between a parapet on each face, so the walk runs from tower to tower.
 * Bays near a gate are left to the gatehouses; a bay with water at its door is left out.
 *
 * Built once per bay, the first time a player comes within {@link #NEAR} blocks (HellState.wallRooms), so a world made before
 * them gets them as its players walk the walls. Everything is worked out from InfernoGeometry and the bay's place on its wall.
 */
public final class WallRooms {
	private WallRooms() {
	}

	public enum Wall {GATE, DIS}

	/** A bay is this many blocks of wall (its walls included); bays stand side by side. */
	static final double SLOT = 10;
	/** Every this-many-th bay is a watch tower. */
	static final int TOWER_EVERY = 8;
	/** Bays within this many blocks of a gate's middle are left to its gatehouse towers. */
	static final double GATE_CLEAR = 23.5;
	/** How near a soul comes before a bay is built. */
	static final double NEAR = 110;
	/** Floor to floor: 7 blocks of room between. */
	static final int STOREY = 8;
	/** The ring of a newel stair round its post, in the order it climbs (world offsets x, z). */
	private static final int[][] RING = {{1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}, {1, 0}};

	private static final int NONE = 0;
	private static final int SHELL = 1;
	private static final int ROOM = 2;
	private static final int NEWEL = 3;
	private static final int STAIR = 4;

	/**
	 * One bay: its id, wall, place round the wall, frame (a along the wall, b out of Hell, centred on the wall's line; a tower's
	 * is square to the world), whether it's a watch tower, and which way its stair stands ({@code side} -1 puts it on the -a end,
	 * away from a tower next door).
	 */
	public record Bay(String id, Wall wall, int index, Rooms.Frame frame, boolean tower, int side) {
	}

	/** What a bay holds, worked out from its geometry alone: each cell's part, the stair's post, and the room's plan. */
	static final class Layout {
		final Bay bay;
		final Map<Long, Integer> cells = new HashMap<>();
		/** The stair's post (world x, z), or none. */
		boolean core;
		int nx;
		int nz;
		/** The 5 by 4 plan the room's furniture stands on. */
		Rooms.Frame plan;
		/** Ring places next to the open floor (a floor's way onto the stair must be one of them). */
		final Set<Integer> open = new HashSet<>();

		Layout(Bay bay) {
			this.bay = bay;
		}

		int type(int x, int z) {
			return cells.getOrDefault(BlockPos.asLong(x, 0, z), NONE);
		}
	}

	/** What one build made: the ground at its door, the wall's top, and its floors. */
	public record Built(int ground, int roof, int[] floors) {
	}

	public static double radius(Wall w) {
		return w == Wall.GATE ? InfernoGeometry.GATE_WALL_RADIUS : InfernoGeometry.DIS_WALL_RADIUS;
	}

	static double half(Wall w) {
		return w == Wall.GATE ? InfernoGeometry.GATE_WALL_HALF_THICKNESS : InfernoGeometry.DIS_WALL_HALF_THICKNESS;
	}

	/** How many bays go round a wall. */
	public static int count(Wall w) {
		return (int) Math.round(2 * Math.PI * radius(w) / SLOT);
	}

	static String id(Wall w, int k) {
		return w.name().toLowerCase(Locale.ROOT) + ":" + k;
	}

	/** Bay k of a wall. */
	public static Bay bay(Wall w, int k) {
		int n = count(w);
		double a = 2 * Math.PI * Math.floorMod(k, n) / n;
		double r = Gatehouses.crossing(a, radius(w));
		double x = Math.cos(a) * r;
		double z = Math.sin(a) * r;
		boolean tower = Math.floorMod(k, TOWER_EVERY) == 0;
		Rooms.Frame f;
		if (tower) {
			Direction out = Rooms.cardinal(Math.cos(a), Math.sin(a));
			double vx = out.getStepX();
			double vz = out.getStepZ();
			f = new Rooms.Frame(Math.floor(x) + 0.5, Math.floor(z) + 0.5, -vz, vx, vx, vz);
		} else {
			f = new Rooms.Frame(x, z, -Math.sin(a), Math.cos(a), Math.cos(a), Math.sin(a));
		}
		int side = Math.floorMod(k + 1, TOWER_EVERY) == 0 ? -1 : 1;
		return new Bay(id(w, k), w, Math.floorMod(k, n), f, tower, side);
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

	/** False for bays within reach of a gate: its gatehouse towers stand there. */
	public static boolean clear(Wall w, int k) {
		double a = 2 * Math.PI * Math.floorMod(k, count(w)) / count(w);
		for (double g : gates(w)) {
			if (InfernoGeometry.angularDistance(a, g) * radius(w) < GATE_CLEAR) {
				return false;
			}
		}
		return true;
	}

	// ------------------------------------------------------------------ the layout

	/** Half-widths of a bay's or tower's room (along, across): its walls stand one block further out. */
	private static double ha(Bay bay) {
		return 4;
	}

	private static double hb(Bay bay) {
		return bay.tower() ? 4 : 2;
	}

	/** Where a bay's door is (a, b of the cell in its wall). */
	private static int doorA(Bay bay) {
		return bay.tower() ? 2 : -2 * bay.side();
	}

	private static int doorB(Bay bay) {
		return bay.tower() ? -5 : -3;
	}

	/** Works out a bay's cells, its stair's place and its room's plan. */
	static Layout layout(Bay bay) {
		Layout l = new Layout(bay);
		Rooms.Frame f = bay.frame();
		double ha = ha(bay);
		double hb = hb(bay);
		double r = radius(bay.wall());
		double half = half(bay.wall());
		// a tower next door claims its own square
		List<Bay> towers = new ArrayList<>();
		if (!bay.tower()) {
			for (int d = -1; d <= 1; d += 2) {
				if (Math.floorMod(bay.index() + d, TOWER_EVERY) == 0) {
					towers.add(bay(bay.wall(), bay.index() + d));
				}
			}
		}
		int cx = (int) Math.floor(f.cx());
		int cz = (int) Math.floor(f.cz());
		for (int x = cx - 9; x <= cx + 9; x++) {
			for (int z = cz - 9; z <= cz + 9; z++) {
				double[] co = f.coords(x, z);
				if (Math.abs(co[0]) > ha + 1.5 || Math.abs(co[1]) > hb + 1.5) {
					continue;
				}
				boolean claimed = false;
				for (Bay t : towers) {
					double[] tc = t.frame().coords(x, z);
					claimed |= Math.abs(tc[0]) <= ha(t) + 1.5 && Math.abs(tc[1]) <= hb(t) + 1.5;
				}
				if (claimed) {
					continue;
				}
				boolean room = Math.abs(co[0]) <= ha + 0.5 && Math.abs(co[1]) <= hb + 0.5;
				if (room && !bay.tower() && Math.abs(InfernoGeometry.effectiveRadius(x + 0.5, z + 0.5) - r) > half + 0.5) {
					// where the wobble pulls the wall's face in, the room stays inside it
					room = false;
				}
				l.cells.put(BlockPos.asLong(x, 0, z), room ? ROOM : SHELL);
			}
		}
		// the plan: toward the bay's door end, its front a row in from Hell's side
		l.plan = f.shifted(bay.tower() ? -2 : -2 * bay.side(), 0);
		// the stair: a tower's in its corner; a bay's where an axis-aligned 3 by 3 fits with the most of its ring open to the
		// floor (so every floor can reach it), then the most room round it, toward the bay's far end
		if (bay.tower()) {
			BlockPos p = f.at(0, 3, 3);
			l.core = place(l, p.getX(), p.getZ());
		} else {
			double best = -1;
			BlockPos aim = f.at(0, 2.3 * bay.side(), 0);
			int bx = 0;
			int bz = 0;
			for (int x = aim.getX() - 3; x <= aim.getX() + 3; x++) {
				for (int z = aim.getZ() - 3; z <= aim.getZ() + 3; z++) {
					double margin = Double.MAX_VALUE;
					for (int dx = -1; dx <= 1 && margin >= 0; dx++) {
						for (int dz = -1; dz <= 1; dz++) {
							if (l.type(x + dx, z + dz) != ROOM) {
								margin = -1;
								break;
							}
							double[] co = f.coords(x + dx, z + dz);
							margin = Math.min(margin, Math.min(ha + 0.5 - Math.abs(co[0]), hb + 0.5 - Math.abs(co[1])));
						}
					}
					if (margin < 0) {
						continue;
					}
					int open = 0;
					for (int[] c : RING) {
						boolean beside = false;
						for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
							int ax = x + c[0] + d[0];
							int az = z + c[1] + d[1];
							beside |= (Math.abs(ax - x) > 1 || Math.abs(az - z) > 1) && free(l, ax, az);
						}
						open += beside ? 1 : 0;
					}
					double[] co = f.coords(x, z);
					double score = 10 * open + margin - 0.05 * Math.abs(co[0] - 2.3 * bay.side());
					if (score > best) {
						best = score;
						bx = x;
						bz = z;
					}
				}
			}
			l.core = best >= 0 && place(l, bx, bz);
		}
		if (l.core) {
			for (int i = 0; i < 8; i++) {
				int x = l.nx + RING[i][0];
				int z = l.nz + RING[i][1];
				for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					if (free(l, x + d[0], z + d[1])) {
						l.open.add(i);
					}
				}
			}
		}
		return l;
	}

	/** Marks a stair's post and ring at (x, z), if all nine cells are room. */
	private static boolean place(Layout l, int x, int z) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (l.type(x + dx, z + dz) != ROOM) {
					return false;
				}
			}
		}
		l.nx = x;
		l.nz = z;
		l.cells.put(BlockPos.asLong(x, 0, z), NEWEL);
		for (int[] d : RING) {
			l.cells.put(BlockPos.asLong(x + d[0], 0, z + d[1]), STAIR);
		}
		return true;
	}

	/** Open floor: room that isn't the stair or the furniture's plan, so a soul can always walk there. */
	private static boolean free(Layout l, int x, int z) {
		if (l.type(x, z) != ROOM) {
			return false;
		}
		double[] p = l.plan.coords(x, z);
		return !(p[0] >= -2.5 && p[0] <= 2.5 && p[1] >= -1.5 && p[1] <= 2.5);
	}

	/**
	 * The floors: the ground at the door, then every 8 blocks down from 7 under the wall's top (at least 4 above the ground); a
	 * tower adds one level with the wall's top (the walk) and its open top 8 above that.
	 */
	static List<Integer> floors(int ground, int roof, boolean tower) {
		List<Integer> out = new ArrayList<>();
		out.add(ground);
		List<Integer> upper = new ArrayList<>();
		for (int f = roof - 7; f >= ground + 4; f -= STOREY) {
			upper.add(0, f);
		}
		out.addAll(upper);
		if (tower) {
			if (roof + 1 >= ground + 4) {
				out.add(roof + 1);
			}
			out.add(roof + 1 + STOREY);
		}
		return out;
	}

	/**
	 * Where on its ring the stair starts so that every floor's way on is beside open floor: the first step, from the ground
	 * floor, and on every floor above it the step that lies level with that floor (it's where you step off, and where you step
	 * on to climb on: the next step is half a block up along the stair). -1 if no start works.
	 */
	static int stairStart(List<Integer> floors, Set<Integer> open) {
		int base = floors.get(0);
		for (int s = 0; s < 8; s++) {
			boolean ok = open.contains(s);
			for (int k = 1; k < floors.size() && ok; k++) {
				// step 2 * (floor - base) is the one level with the floor; it stands on ring place s + that - 1
				ok = open.contains(Math.floorMod(s + 2 * (floors.get(k) - base) - 1, 8));
			}
			if (ok) {
				return s;
			}
		}
		return -1;
	}

	/** The stair's blocks (world cell to state), half a block a step, from the ground floor to the top one. */
	static Map<Long, BlockState> stair(Layout l, List<Integer> floors, int start, boolean dis) {
		Map<Long, BlockState> out = new HashMap<>();
		Block slab = dis ? Blocks.RED_NETHER_BRICK_SLAB : Blocks.POLISHED_BLACKSTONE_BRICK_SLAB;
		Block stairs = dis ? Blocks.RED_NETHER_BRICK_STAIRS : Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS;
		int base = floors.get(0);
		int top = floors.get(floors.size() - 1);
		for (int n = 1; n <= 2 * (top - base); n++) {
			int i = Math.floorMod(start + n - 1, 8);
			int[] c = RING[i];
			int[] next = RING[(i + 1) % 8];
			int x = l.nx + c[0];
			int z = l.nz + c[1];
			if (n % 2 == 1) {
				out.put(BlockPos.asLong(x, base + (n - 1) / 2, z), slab.defaultBlockState());
			} else {
				out.put(BlockPos.asLong(x, base + n / 2 - 1, z),
						stairs.defaultBlockState().setValue(StairBlock.FACING, Rooms.cardinal(next[0] - c[0], next[1] - c[1])));
			}
		}
		return out;
	}

	// ------------------------------------------------------------------ the world

	/** The air level on the ground at x, z: over the Wood's plants and under any water (a wall's stone counts as ground). */
	static int ground(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
		while (pos.getY() > level.getMinY() + 5) {
			BlockState s = level.getBlockState(pos);
			if (!(s.isAir() || s.canBeReplaced() || s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES))) {
				break;
			}
			pos.move(Direction.DOWN);
		}
		return pos.getY() + 1;
	}

	/** The top block of the wall at x, z: Dis's stands to a fixed height, the Gate's to its ground plus its height. */
	static int wallTop(ServerLevel level, Wall w, int x, int z) {
		if (w == Wall.DIS) {
			return DisWallFeature.BASE_Y + InfernoGeometry.DIS_WALL_HEIGHT;
		}
		// down from above the wall to the first solid block: its top, or one of the crenels on it
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

	/** Builds a bay or a watch tower. Null when none is built (water at its door, or too little height for a room). */
	public static Built build(ServerLevel level, Bay bay) {
		Layout l = layout(bay);
		Rooms.Frame f = bay.frame();
		boolean dis = bay.wall() == Wall.DIS;
		boolean tower = bay.tower();
		BlockPos outside = f.at(0, doorA(bay), doorB(bay) - 1);
		int ground = ground(level, outside.getX(), outside.getZ());
		if (!level.getFluidState(outside.atY(ground)).isEmpty() || !level.getFluidState(outside.atY(ground - 1)).isEmpty()) {
			return null;
		}
		int roof = wallTop(level, bay.wall(), (int) Math.floor(f.cx()), (int) Math.floor(f.cz()));
		if (!tower && roof - ground < 4) {
			return null;
		}
		List<Integer> floors = floors(ground, roof, tower);
		int start = floors.size() > 1 && l.core ? stairStart(floors, l.open) : -1;
		if (start < 0 && !tower) {
			// no stair fits: one tall room
			floors = List.of(ground);
		}
		Map<Long, BlockState> stair = start >= 0 ? stair(l, floors, start, dis) : Map.of();
		int topFloor = floors.get(floors.size() - 1);
		Set<Integer> floorSet = new HashSet<>(floors);
		int platform = roof + 1 + STOREY;
		RandomSource random = RandomSource.create(bay.id().hashCode());
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int high = tower ? roof + 10 : roof + 1;
		for (Map.Entry<Long, Integer> e : l.cells.entrySet()) {
			int x = BlockPos.getX(e.getKey());
			int z = BlockPos.getZ(e.getKey());
			int type = e.getValue();
			double[] co = f.coords(x, z);
			boolean face = Math.abs(co[1]) > hb(bay) + 0.5;
			boolean crenel = ((x + z) & 1) == 0;
			level.getChunk(x >> 4, z >> 4);
			for (int y = ground - 1; y <= high; y++) {
				BlockState state;
				if (type == SHELL) {
					if (tower) {
						state = y <= roof + 9 ? Rooms.stone(dis, random) : crenel ? Rooms.stone(dis, random) : air;
					} else {
						// the wall's top, and on it the walk: a parapet on each face, clear between
						state = y <= roof ? Rooms.stone(dis, random) : face && crenel ? Rooms.stone(dis, random) : air;
					}
				} else if (!tower && y == roof) {
					state = Rooms.stone(dis, random);
				} else if (y > (tower ? platform - 1 : roof)) {
					state = air;
				} else if (type == NEWEL) {
					state = y <= topFloor - 1 ? Rooms.stone(dis, random) : air;
				} else if (tower && y == platform - 1 && type == ROOM) {
					state = Rooms.stone(dis, random);
				} else {
					long here = BlockPos.asLong(x, y, z);
					if (stair.containsKey(here)) {
						state = stair.get(here);
					} else if (floorSet.contains(y + 1)) {
						// a floor; over the stair, open where it climbs within four blocks below (so no head meets its edge)
						boolean climb = type == STAIR && (stair.containsKey(BlockPos.asLong(x, y - 1, z)) || stair.containsKey(BlockPos.asLong(x, y - 2, z))
								|| stair.containsKey(BlockPos.asLong(x, y - 3, z)));
						state = climb ? air : y + 1 == platform ? Rooms.stone(dis, random) : Rooms.floorBlock(dis);
					} else {
						state = air;
					}
				}
				level.setBlock(pos.set(x, y, z), state, 2);
			}
			// a footing down to solid ground under it (a tower stands out from the wall)
			for (int y = ground - 2, n = 0; n < 40; y--, n++) {
				BlockState s = level.getBlockState(pos.set(x, y, z));
				if (!(s.isAir() || s.canBeReplaced() || s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES))) {
					break;
				}
				level.setBlock(pos, Rooms.stone(dis, random), 2);
			}
		}
		// the door, on Hell's side, and the way to it cleared
		for (int d = 0; d <= 2; d++) {
			for (int y = ground; y <= ground + 2; y++) {
				level.setBlock(f.at(y, doorA(bay), doorB(bay) - d), air, 2);
			}
			BlockPos under = f.at(ground - 1, doorA(bay), doorB(bay) - d);
			if (level.getBlockState(under).canBeReplaced()) {
				level.setBlock(under, Rooms.stone(dis, random), 2);
			}
		}
		if (tower && floorSet.contains(roof + 1)) {
			// out onto the walk, wherever the top of the wall meets the tower
			double r = radius(bay.wall());
			for (Map.Entry<Long, Integer> e : l.cells.entrySet()) {
				int x = BlockPos.getX(e.getKey());
				int z = BlockPos.getZ(e.getKey());
				if (e.getValue() == SHELL && Math.abs(InfernoGeometry.effectiveRadius(x + 0.5, z + 0.5) - r) <= 2.5) {
					for (int y = roof + 1; y <= roof + 3; y++) {
						level.setBlock(pos.set(x, y, z), air, 2);
					}
				}
			}
		}
		// barred windows: out over the wall's outer side (and Hell's side, on a tower)
		BlockState bars = Blocks.IRON_BARS.defaultBlockState();
		for (int i = 0; i < floors.size(); i++) {
			int fl = floors.get(i);
			if (tower && fl == platform) {
				continue;
			}
			int ceiling = i + 1 < floors.size() ? floors.get(i + 1) - 1 : roof;
			int[] along = tower ? new int[]{-2, 0} : new int[]{-3, -1, 1, 3};
			for (int y = fl + 2; y <= fl + 3 && y < ceiling; y++) {
				for (int a : along) {
					window(level, l, f.at(y, a, hb(bay) + 1), bars);
					if (tower) {
						window(level, l, f.at(y, a, -hb(bay) - 1), bars);
					}
				}
			}
		}
		// the rooms: carpet, a lantern, and each one's furniture
		Rooms.Kind prev = null;
		for (int i = 0; i < floors.size(); i++) {
			int fl = floors.get(i);
			Rooms.Kind kind;
			if (tower && fl == platform) {
				kind = Rooms.Kind.LOOKOUT;
			} else if (tower && i == 0) {
				kind = Rooms.Kind.GUARD;
			} else if (tower && fl == roof + 1) {
				kind = Rooms.Kind.WAR;
			} else {
				kind = Rooms.pick(dis, random, prev);
			}
			prev = kind;
			if (kind != Rooms.Kind.LOOKOUT) {
				int ceiling = i + 1 < floors.size() ? floors.get(i + 1) - 1 : roof;
				Rooms.carpet(level, f, Rooms.Shape.square(ha(bay), hb(bay)), fl, dis);
				Rooms.lantern(level, l.plan, 0, 0.5, fl, ceiling);
			}
			Rooms.furnish(level, l.plan, kind, fl, dis, random, p -> l.type(p.getX(), p.getZ()) == ROOM);
		}
		return new Built(ground, roof, floors.stream().mapToInt(Integer::intValue).toArray());
	}

	private static void window(ServerLevel level, Layout l, BlockPos at, BlockState bars) {
		if (l.type(at.getX(), at.getZ()) == SHELL) {
			level.setBlock(at, bars, 2);
		}
	}

	/** True within {@code within} blocks of a built bay's middle (for Virgil's lesson about the walls). */
	public static boolean nearBuilt(ServerLevel level, double x, double z, double within) {
		HellState state = HellState.get(level.getServer());
		for (Wall w : Wall.values()) {
			if (Math.abs(Math.hypot(x, z) - radius(w)) > within + 6) {
				continue;
			}
			int n = count(w);
			int k0 = (int) Math.round(Math.atan2(z, x) / (2 * Math.PI / n));
			for (int k = k0 - 2; k <= k0 + 2; k++) {
				if (state.wallRooms.contains(id(w, Math.floorMod(k, n)))) {
					Bay b = bay(w, k);
					if (Math.hypot(x - b.frame().cx(), z - b.frame().cz()) < within) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** Builds the bays players are near (once each; a bay left out is marked as decided too). */
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
				if (p.isSpectator() || Math.abs(Math.hypot(p.getX(), p.getZ()) - r) > NEAR + 8) {
					continue;
				}
				int k0 = (int) Math.round(Math.atan2(p.getZ(), p.getX()) / step);
				for (int d = -span; d <= span; d++) {
					int k = Math.floorMod(k0 + d, n);
					String id = id(w, k);
					if (state.wallRooms.contains(id)) {
						continue;
					}
					Bay bay = bay(w, k);
					if (Math.hypot(p.getX() - bay.frame().cx(), p.getZ() - bay.frame().cz()) >= NEAR) {
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
	 * For the smoke test, from geometry alone: every bay's rooms are inside its wall; every bay and tower has its stair, and it
	 * can start so that every floor reaches it, whatever height the ground at its door is.
	 */
	public static String selfTest() {
		List<String> wrong = new ArrayList<>();
		int bays = 0;
		int towers = 0;
		int noStair = 0;
		int skipped = 0;
		for (Wall w : Wall.values()) {
			double r = radius(w);
			double half = half(w);
			for (int k = 0; k < count(w); k++) {
				if (!clear(w, k)) {
					skipped++;
					continue;
				}
				Bay bay = bay(w, k);
				Layout l = layout(bay);
				if (bay.tower()) {
					towers++;
				} else {
					bays++;
					for (Map.Entry<Long, Integer> e : l.cells.entrySet()) {
						int x = BlockPos.getX(e.getKey());
						int z = BlockPos.getZ(e.getKey());
						double off = Math.abs(InfernoGeometry.effectiveRadius(x + 0.5, z + 0.5) - r);
						if ((e.getValue() != SHELL && off > half + 1.0) || off > half + 2.5) {
							if (wrong.size() < 5) {
								wrong.add(bay.id() + " sticks out of its wall at " + x + ", " + z);
							}
						}
					}
				}
				if (!l.core) {
					noStair++;
					if (bay.tower() && wrong.size() < 5) {
						wrong.add(bay.id() + " (a tower) has no room for its stair");
					}
					continue;
				}
				// whatever the gap from the ground floor to the next (4 to 11 blocks), a start must serve every floor
				for (int gap = 4; gap <= 11; gap++) {
					List<Integer> floors = new ArrayList<>(List.of(0, gap, gap + STOREY));
					if (bay.tower()) {
						floors.add(gap + 2 * STOREY);
						floors.add(gap + 3 * STOREY);
					}
					if (stairStart(floors, l.open) < 0 && wrong.size() < 5) {
						wrong.add(bay.id() + "'s stair can't reach every floor (open " + l.open + ", gap " + gap + ")");
					}
				}
			}
		}
		String summary = bays + " bays and " + towers + " watch towers along both walls (" + skipped + " left to the gatehouses, " + noStair
				+ " single rooms with no stair); good rooms " + Math.round(100 * Rooms.goodShare(false)) + "% on the Gate, "
				+ Math.round(100 * Rooms.goodShare(true)) + "% on Dis";
		return wrong.isEmpty() ? "Wall rooms test OK: " + summary : "Wall rooms test FAILED: " + String.join("; ", wrong) + " (" + summary + ")";
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
							Built built = build(level, bay);
							HellState.get(level.getServer()).wallRooms.add(bay.id());
							ctx.getSource().sendSuccess(() -> Component.literal(built == null ? "No room could be built there (water at its door?)."
									: "Built " + (bay.tower() ? "the watch tower " : "the wall room ") + bay.id() + " at " + (int) bay.frame().cx() + ", "
											+ (int) bay.frame().cz() + ", floors " + java.util.Arrays.toString(built.floors()) + ".")
									.withStyle(ChatFormatting.GOLD), false);
							return 1;
						}))));
	}
}
