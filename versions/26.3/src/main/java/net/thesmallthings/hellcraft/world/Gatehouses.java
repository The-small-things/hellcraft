package net.thesmallthings.hellcraft.world;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.HellState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The gatehouses: two great round towers beside every gate of the Gate of Hell (12) and of the Walls of Dis (4), with
 * rooms inside. A door on the ground opens off the gate's passage; a spiral stair runs up round the inside of the tower,
 * through a floor every seven or eight blocks, to a door at the top of the wall (onto the walk over the gate) and on up to
 * the roof. Each floor is a furnished, carpeted room (see {@link Rooms}): a guard room on the ground, a war room at the top,
 * and between them rooms chosen by weight. The Gate of Hell's are black and grey, of dark oak; Dis's are red and black,
 * of crimson, the fallen angels' city.
 *
 * Built once, the first time a player comes within {@link #NEAR} blocks of the gate (HellState.gatehouses), so worlds made
 * before them get them too. Everything is worked out from InfernoGeometry: where the wall crosses at each gate (it wobbles),
 * the towers either side, and the spiral, whose pitch is set so it never runs across either door.
 */
public final class Gatehouses {
	private Gatehouses() {
	}

	enum Style {GATE, DIS}

	/** Radii (blocks from the tower's centre): the rooms inside, the stair round them, the outer wall. */
	static final double ROOM = 3.6;
	static final double STAIR = 5.2;
	static final double OUTER = 6.3;
	/** How far each tower's centre lies along the wall from the middle of its gate. */
	private static final double GATE_SPACING = 11.0;
	private static final double DIS_SPACING = 12.0;
	/** The Walls of Dis stand on this base height (DisWallFeature). */
	private static final int DIS_BASE = 58;
	/** How near a player comes before a gate's towers are built. */
	private static final double NEAR = 96;
	/** A door's half-width (blocks) and its height. */
	private static final double DOOR_HALF = 1.5;
	private static final int DOOR_HEIGHT = 3;

	/** One gate: its id, style, and where the middle of its passage is. */
	record Site(String id, Style style, double x, double z, double angle, double radius) {
	}

	/**
	 * One tower: its centre (a block's centre), the ground at its door, the walk at the top of the wall, its roof, its floors
	 * (walking heights, from the ground up, the wall-walk among them), the spiral's rise per turn and starting angle, and the
	 * direction of its ground door (toward its gate). {@code walkDoor} is false where the wall's top is too near the ground
	 * for a door onto it; {@code walkDoorAngle} is which way that door faces: toward the gate too (onto the walk over it)
	 * where the stair leaves it clear, else the other way, onto the wall running away from the gate.
	 */
	public record Tower(String site, Style style, double cx, double cz, int ground, int walk, int roof, int[] floors, double pitch, double start,
			double doorAngle, boolean walkDoor, double walkDoorAngle) {
		public BlockPos centre() {
			return BlockPos.containing(cx, ground, cz);
		}

		/** True if the door onto the wall faces away from the gate. */
		public boolean farDoor() {
			return walkDoor && Math.cos(walkDoorAngle - doorAngle) < 0;
		}
	}

	private static List<Site> sites;

	static List<Site> sites() {
		if (sites == null) {
			List<Site> out = new ArrayList<>();
			for (int k = 0; k < InfernoGeometry.GATE_COUNT; k++) {
				double a = InfernoGeometry.gateAngle(k);
				double r = crossing(a, InfernoGeometry.GATE_WALL_RADIUS);
				out.add(new Site("gate_" + k, Style.GATE, Math.cos(a) * r, Math.sin(a) * r, a, InfernoGeometry.GATE_WALL_RADIUS));
			}
			for (int k = 0; k < InfernoGeometry.DIS_GATE_ANGLES.length; k++) {
				double a = InfernoGeometry.DIS_GATE_ANGLES[k];
				double r = crossing(a, InfernoGeometry.DIS_WALL_RADIUS);
				out.add(new Site("dis_" + k, Style.DIS, Math.cos(a) * r, Math.sin(a) * r, a, InfernoGeometry.DIS_WALL_RADIUS));
			}
			sites = out;
		}
		return sites;
	}

	/** How far out along angle a the wall of (effective) radius target stands: it follows the ring's wobble. */
	static double crossing(double a, double target) {
		double lo = target - 200;
		double hi = target + 200;
		for (int i = 0; i < 40; i++) {
			double mid = (lo + hi) / 2;
			if (InfernoGeometry.effectiveRadius(Math.cos(a) * mid, Math.sin(a) * mid) < target) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		return (lo + hi) / 2;
	}

	static Site site(String id) {
		for (Site s : sites()) {
			if (s.id().equals(id)) {
				return s;
			}
		}
		throw new IllegalArgumentException("no gate " + id);
	}

	/** Tower {@code side} (-1 or 1) of a gate, with its ground found in the world (the floor of the gate's passage). */
	public static Tower tower(ServerLevel level, String id, int side) {
		Site s = site(id);
		return tower(s, side, passageFloor(level, (int) Math.floor(s.x()), (int) Math.floor(s.z())));
	}

	/** Tower {@code side} of gate s, given the ground at its gate. */
	static Tower tower(Site s, int side, int ground) {
		double spacing = s.style() == Style.GATE ? GATE_SPACING : DIS_SPACING;
		double r0 = Math.sqrt(s.x() * s.x() + s.z() * s.z());
		double a = s.angle() + side * spacing / r0;
		double r = crossing(a, s.radius());
		double cx = Math.floor(Math.cos(a) * r) + 0.5;
		double cz = Math.floor(Math.sin(a) * r) + 0.5;
		int walk;
		int roof;
		if (s.style() == Style.GATE) {
			walk = ground + InfernoGeometry.GATE_WALL_HEIGHT + 1;
			roof = ground + InfernoGeometry.GATE_TOWER_HEIGHT + 1;
		} else {
			walk = DIS_BASE + InfernoGeometry.DIS_WALL_HEIGHT + 1;
			roof = DIS_BASE + InfernoGeometry.DIS_TOWER_HEIGHT + 1;
		}
		boolean walkDoor = walk - ground >= 6;
		if (!walkDoor) {
			// the ground at this gate is nearly up to the wall's top: rooms up to the roof, and no door onto the wall
			walk = ground;
			roof = Math.max(roof, ground + 13);
		}
		// floors: the ground, about seven and a half blocks apart up to the wall-walk, and the roof
		List<Integer> floors = new ArrayList<>();
		floors.add(ground);
		int rise = walk - ground;
		if (rise > 0) {
			int n = Math.max(1, (int) Math.round(rise / Rooms.FLOOR_SPACING));
			for (int i = 1; i <= n; i++) {
				floors.add(ground + (int) Math.round(i * rise / (double) n));
			}
		}
		int top = floors.get(floors.size() - 1);
		if (roof - top >= 10) {
			floors.add(top + (roof - top) / 2);
		}
		floors.add(roof);
		// the spiral's pitch (8 to 13 blocks a turn, or the stair gets too steep to walk) and where it starts: chosen so that
		// at both doors, one above the other, the stair is well above the doorway or well below the floor
		double doorAngle = Math.atan2(s.z() - cz, s.x() - cx);
		double pitch = 12.0;
		double f = -1;
		double walkDoorAngle = doorAngle;
		// first with the wall's door toward the gate, over the ground door; if the stair can't leave both clear, with
		// the wall's door on the other side of the tower, where the stair passes half a turn later
		for (int far = 0; far <= (walkDoor ? 1 : 0) && f < 0; far++) {
			for (int i = 0; i <= 100 && f < 0; i++) {
				double p = 11.0 + (i % 2 == 0 ? 1 : -1) * (i / 2) * 0.05;
				if (p < 8.0 || p > 13.0) {
					continue;
				}
				double[] window = doorWindow(p);
				for (double g = window[0]; g <= window[1] && f < 0; g += 0.05) {
					double atWalk = ((g + far * p / 2 - rise) % p + p) % p;
					if (!walkDoor || (atWalk >= window[0] && atWalk <= window[1])) {
						pitch = p;
						f = g;
						walkDoorAngle = doorAngle + far * Math.PI;
					}
				}
			}
		}
		if (f < 0) {
			// no pitch suits a door onto the wall on either side: keep the ground door clear and leave it out
			walkDoor = false;
			pitch = 12.0;
			double[] window = doorWindow(pitch);
			f = (window[0] + window[1]) / 2;
		}
		double start = doorAngle - f / pitch * 2 * Math.PI;
		int[] fl = floors.stream().mapToInt(Integer::intValue).toArray();
		return new Tower(s.id(), s.style(), cx, cz, ground, walk, roof, fl, pitch, start, doorAngle, walkDoor, walkDoorAngle);
	}

	/**
	 * Where, in blocks of rise above a door's floor, the stair may cross the door's direction for a given pitch: high enough
	 * to clear the doorway, or (that much less a turn) low enough that no hole opens in the floor in front of it.
	 */
	private static double[] doorWindow(double pitch) {
		// how much of a turn a doorway spans, seen from the stair's inner edge (where it looks widest)
		double w = (DOOR_HALF / ROOM) / (2 * Math.PI) * pitch;
		return new double[]{DOOR_HEIGHT + 0.5 + w, pitch - 4.0 - w};
	}

	/** The floor of a gate's passage: down through the wall over it, then through the opening, to the paving. */
	private static int passageFloor(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
		int floor = pos.getY() + 1;
		while (pos.getY() > level.getMinY() + 5 && !level.getBlockState(pos).isAir()) {
			pos.move(Direction.DOWN);
		}
		if (pos.getY() <= level.getMinY() + 5) {
			return floor;
		}
		while (pos.getY() > level.getMinY() + 5 && level.getBlockState(pos).isAir()) {
			pos.move(Direction.DOWN);
		}
		return pos.getY() + 1;
	}

	/**
	 * A gate's key in HellState.gatehouses: "v3" since every tower has a door onto the wall ("v2" since the stairs'
	 * holes were widened); older towers are built again.
	 */
	private static String stateKey(String id) {
		return "v3:" + id;
	}

	/** True within {@code within} blocks of a gate whose towers have been built. */
	public static boolean nearBuilt(net.minecraft.world.level.Level level, double x, double z, double within) {
		if (!(level instanceof ServerLevel server) || server.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
			return false;
		}
		HellState state = HellState.get(server.getServer());
		for (Site s : sites()) {
			double dx = x - s.x();
			double dz = z - s.z();
			if (dx * dx + dz * dz < within * within && state.gatehouses.contains(stateKey(s.id()))) {
				return true;
			}
		}
		return false;
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> commands(dispatcher));
	}

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 40 != 23) {
			return;
		}
		ServerLevel level = server.overworld();
		if (!HellWorldgen.isInferno(level)) {
			return;
		}
		HellState state = HellState.get(server);
		for (Site s : sites()) {
			if (state.gatehouses.contains(stateKey(s.id()))) {
				continue;
			}
			for (ServerPlayer p : level.players()) {
				double dx = p.getX() - s.x();
				double dz = p.getZ() - s.z();
				if (!p.isSpectator() && dx * dx + dz * dz < NEAR * NEAR) {
					build(level, s.id());
					break;
				}
			}
		}
	}

	/** Builds both towers of a gate. */
	public static void build(ServerLevel level, String id) {
		HellState state = HellState.get(level.getServer());
		state.gatehouses.add(stateKey(id));
		state.setDirty();
		for (int side = -1; side <= 1; side += 2) {
			Tower t = tower(level, id, side);
			buildTower(level, t);
			HellcraftMod.LOGGER.info("Gatehouse tower built at {} ({}, floors {}, wall door {})", t.centre().toShortString(), id,
					java.util.Arrays.toString(t.floors()), !t.walkDoor() ? "none" : t.farDoor() ? "away from the gate" : "toward the gate");
		}
	}

	// ------------------------------------------------------------------ the spiral

	/** The spiral stair: position (as a long) to block, for each block of it. */
	static Map<Long, BlockState> spiral(Tower t) {
		Map<Long, BlockState> out = new HashMap<>();
		Block slab = t.style() == Style.GATE ? Blocks.POLISHED_BLACKSTONE_BRICK_SLAB : Blocks.RED_NETHER_BRICK_SLAB;
		Block stair = t.style() == Style.GATE ? Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS : Blocks.RED_NETHER_BRICK_STAIRS;
		for (int[] c : ring(t)) {
			for (int turn = 0; ; turn++) {
				int n = steps(t, c[0], c[1], turn);
				if (n <= 0) {
					continue;
				}
				if (t.ground() + n / 2.0 > t.roof()) {
					break;
				}
				int x = c[0];
				int z = c[1];
				if (n % 2 == 1) {
					out.put(BlockPos.asLong(x, t.ground() + (n - 1) / 2, z), slab.defaultBlockState());
				} else {
					out.put(BlockPos.asLong(x, t.ground() + n / 2 - 1, z), stair.defaultBlockState().setValue(StairBlock.FACING, tangent(t, x, z)));
				}
			}
		}
		return out;
	}

	/** The stair's height over cell (x, z) on a given turn, in half blocks above the ground (0 or less: not on this turn). */
	static int steps(Tower t, int x, int z, int turn) {
		double a = Math.atan2(z + 0.5 - t.cz(), x + 0.5 - t.cx()) - t.start();
		a = ((a % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI);
		return (int) Math.ceil((a / (2 * Math.PI) + turn) * 2 * t.pitch());
	}

	/** The way up the stair at (x, z): round the tower, the way the angle grows. */
	private static Direction tangent(Tower t, int x, int z) {
		double a = Math.atan2(z + 0.5 - t.cz(), x + 0.5 - t.cx());
		return horizontal(-Math.sin(a), Math.cos(a));
	}

	private static Direction horizontal(double dx, double dz) {
		if (Math.abs(dx) > Math.abs(dz)) {
			return dx > 0 ? Direction.EAST : Direction.WEST;
		}
		return dz > 0 ? Direction.SOUTH : Direction.NORTH;
	}

	private static double dist(Tower t, int x, int z) {
		double dx = x + 0.5 - t.cx();
		double dz = z + 0.5 - t.cz();
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** The cells of the stair's ring. */
	static List<int[]> ring(Tower t) {
		List<int[]> out = new ArrayList<>();
		int x0 = (int) Math.floor(t.cx());
		int z0 = (int) Math.floor(t.cz());
		for (int dx = -7; dx <= 7; dx++) {
			for (int dz = -7; dz <= 7; dz++) {
				double d = dist(t, x0 + dx, z0 + dz);
				if (d > ROOM && d <= STAIR) {
					out.add(new int[]{x0 + dx, z0 + dz});
				}
			}
		}
		return out;
	}

	/** True in the way through a door at angle a: ahead of the centre, within the door's half-width either side. */
	static boolean inDoorway(Tower t, int x, int z, double a) {
		double dx = x + 0.5 - t.cx();
		double dz = z + 0.5 - t.cz();
		double along = dx * Math.cos(a) + dz * Math.sin(a);
		double lat = -dx * Math.sin(a) + dz * Math.cos(a);
		return along > 0 && Math.abs(lat) < DOOR_HALF;
	}

	// ------------------------------------------------------------------ building

	private static void buildTower(ServerLevel level, Tower t) {
		RandomSource random = RandomSource.create(BlockPos.asLong((int) t.cx(), t.ground(), (int) t.cz()));
		Map<Long, BlockState> spiral = spiral(t);
		Set<Integer> floorLevels = new HashSet<>();
		for (int i = 1; i < t.floors().length; i++) {
			floorLevels.add(t.floors()[i]);
		}
		BlockState planks = (t.style() == Style.GATE ? Blocks.DARK_OAK_PLANKS : Blocks.CRIMSON_PLANKS).defaultBlockState();
		int x0 = (int) Math.floor(t.cx());
		int z0 = (int) Math.floor(t.cz());
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -8; dx <= 8; dx++) {
			for (int dz = -8; dz <= 8; dz++) {
				int x = x0 + dx;
				int z = z0 + dz;
				double d = dist(t, x, z);
				if (d > OUTER) {
					continue;
				}
				level.getChunk(x >> 4, z >> 4);
				int bottom = Math.min(t.ground() - 4, level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 2);
				for (int y = bottom; y <= t.roof() + 3; y++) {
					pos.set(x, y, z);
					BlockState state;
					if (d > STAIR) {
						// the outer wall, crenellated round the roof
						state = y < t.roof() ? stone(t, random) : y == t.roof() && ((x + z) & 1) == 0 ? stone(t, random) : Blocks.AIR.defaultBlockState();
					} else if (y < t.ground() - 1) {
						state = stone(t, random);
					} else if (y == t.ground() - 1) {
						state = planks;
					} else if (y >= t.roof()) {
						state = Blocks.AIR.defaultBlockState();
					} else if (floorLevels.contains(y + 1)) {
						// a floor (the roof's too): open where the stair climbs within four blocks below it, so no head meets its edge
						long here = BlockPos.asLong(x, y, z);
						if (spiral.containsKey(here)) {
							state = spiral.get(here);
						} else if (spiral.containsKey(BlockPos.asLong(x, y - 1, z)) || spiral.containsKey(BlockPos.asLong(x, y - 2, z))
								|| spiral.containsKey(BlockPos.asLong(x, y - 3, z))) {
							state = Blocks.AIR.defaultBlockState();
						} else {
							state = y + 1 == t.roof() ? stone(t, random) : planks;
						}
					} else {
						state = spiral.getOrDefault(BlockPos.asLong(x, y, z), Blocks.AIR.defaultBlockState());
					}
					level.setBlock(pos, state, 2);
				}
			}
		}
		doors(level, t);
		slits(level, t);
		furnishAll(level, t, random);
		// a fire on the roof, as the old towers had
		level.setBlock(pos.set(x0, t.roof(), z0), (t.style() == Style.GATE ? Blocks.SOUL_CAMPFIRE : Blocks.CAMPFIRE).defaultBlockState(), 3);
	}

	/**
	 * The ground door, onto the gate's passage, and the door at the wall-walk: onto the walk over the gate, or (where the
	 * stair is in the way of that) on the far side, onto the wall itself, with a walk levelled along its top.
	 */
	private static void doors(ServerLevel level, Tower t) {
		door(level, t, t.ground(), t.doorAngle());
		if (t.walkDoor()) {
			door(level, t, t.walk(), t.walkDoorAngle());
			if (t.farDoor()) {
				wallWalk(level, t);
			}
		}
	}

	private static void door(ServerLevel level, Tower t, int floor, double angle) {
		int x0 = (int) Math.floor(t.cx());
		int z0 = (int) Math.floor(t.cz());
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockState arch = (t.style() == Style.GATE ? Blocks.CHISELED_POLISHED_BLACKSTONE : Blocks.GILDED_BLACKSTONE).defaultBlockState();
		for (int dx = -9; dx <= 9; dx++) {
			for (int dz = -9; dz <= 9; dz++) {
				int x = x0 + dx;
				int z = z0 + dz;
				double d = dist(t, x, z);
				if (d <= ROOM || d > OUTER + 1.5 || !inDoorway(t, x, z, angle)) {
					continue;
				}
				for (int h = 0; h < DOOR_HEIGHT; h++) {
					pos.set(x, floor + h, z);
					if (!level.getBlockState(pos).isAir()) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
					}
				}
				if (d > STAIR) {
					if (d <= OUTER) {
						level.setBlock(pos.set(x, floor + DOOR_HEIGHT, z), arch, 3);
					}
					pos.set(x, floor - 1, z);
					if (level.getBlockState(pos).canBeReplaced()) {
						level.setBlock(pos, Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 3);
					}
				}
			}
		}
	}

	/** How far from a tower's far door the walk along the wall's top is levelled: to where the wall rooms' own walk has begun. */
	private static final double WALK_REACH = 19;

	/**
	 * From a far door, a walk three blocks wide along the top of the wall: its battlements cleared out of the way and any
	 * dip filled, as far as the wall rooms' walk (WallRooms) or to where the wall's top rises or falls away.
	 */
	private static void wallWalk(ServerLevel level, Tower t) {
		double a = t.walkDoorAngle();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		Set<Long> done = new HashSet<>();
		for (double along = OUTER + 0.5; along <= WALK_REACH; along += 0.5) {
			List<int[]> row = new ArrayList<>();
			boolean wall = false;
			for (double lat = -1.0; lat <= 1.0; lat += 0.5) {
				int x = (int) Math.floor(t.cx() + Math.cos(a) * along - Math.sin(a) * lat);
				int z = (int) Math.floor(t.cz() + Math.sin(a) * along + Math.cos(a) * lat);
				row.add(new int[]{x, z});
				// (solid a few blocks under the walk: this is still the wall, not the air past its end or a step in the ground)
				wall |= level.getBlockState(pos.set(x, t.walk() - 3, z)).isSolid();
			}
			// the wall climbs above the walk here (the ground has risen): the walk ends
			int[] mid = row.get(row.size() / 2);
			if (!wall || level.getBlockState(pos.set(mid[0], t.walk() + 2, mid[1])).isSolid() && level.getBlockState(pos.set(mid[0], t.walk() + 3, mid[1])).isSolid()) {
				break;
			}
			for (int[] c : row) {
				if (!done.add(BlockPos.asLong(c[0], 0, c[1]))) {
					continue;
				}
				for (int y = t.walk() - 2; y < t.walk(); y++) {
					if (!level.getBlockState(pos.set(c[0], y, c[1])).isSolid()) {
						level.setBlock(pos, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 3);
					}
				}
				for (int y = t.walk(); y < t.walk() + DOOR_HEIGHT; y++) {
					if (!level.getBlockState(pos.set(c[0], y, c[1])).isAir()) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
					}
				}
			}
		}
	}

	/** Narrow barred windows in each room, out and in from the wall, never toward the doors or the wall's other side. */
	private static void slits(ServerLevel level, Tower t) {
		int x0 = (int) Math.floor(t.cx());
		int z0 = (int) Math.floor(t.cz());
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < t.floors().length - 1; i++) {
			int f = t.floors()[i];
			for (int k = 0; k < 8; k++) {
				double a = t.doorAngle() + Math.PI / 8 + k * Math.PI / 4;
				double offDoor = Math.abs(Math.atan2(Math.sin(a - t.doorAngle()), Math.cos(a - t.doorAngle())));
				if (offDoor < 0.7 || offDoor > Math.PI - 0.7) {
					continue;
				}
				for (int dx = -7; dx <= 7; dx++) {
					for (int dz = -7; dz <= 7; dz++) {
						int x = x0 + dx;
						int z = z0 + dz;
						double d = dist(t, x, z);
						double ex = x + 0.5 - t.cx();
						double ez = z + 0.5 - t.cz();
						double along = ex * Math.cos(a) + ez * Math.sin(a);
						double lat = -ex * Math.sin(a) + ez * Math.cos(a);
						if (d > STAIR && d <= OUTER && along > 0 && Math.abs(lat) < 0.5) {
							for (int h = 2; h <= 3; h++) {
								level.setBlock(pos.set(x, f + h, z), Blocks.IRON_BARS.defaultBlockState(), 3);
							}
						}
					}
				}
			}
		}
	}

	/** The rooms of the tower's floors (the ground one a guard room, the top one the war room, the rest by weight), furnished. */
	private static void furnishAll(ServerLevel level, Tower t, RandomSource random) {
		int n = t.floors().length - 1;
		boolean dis = t.style() == Style.DIS;
		Rooms.Frame room = Rooms.Frame.axes(t.cx(), t.cz());
		// each room's plan has its back to the doors, its way in toward them
		Direction back = Rooms.cardinal(-Math.cos(t.doorAngle()), -Math.sin(t.doorAngle()));
		Rooms.Frame plan = new Rooms.Frame(t.cx(), t.cz(), -back.getStepZ(), back.getStepX(), back.getStepX(), back.getStepZ());
		// (on the wall's floor, where the door is on the far side, the room is turned round to keep its way in clear)
		Rooms.Frame turned = new Rooms.Frame(t.cx(), t.cz(), back.getStepZ(), -back.getStepX(), -back.getStepX(), -back.getStepZ());
		Rooms.Kind prev = null;
		for (int i = 0; i < n; i++) {
			Rooms.Kind kind = i == 0 ? Rooms.Kind.GUARD : i == n - 1 ? Rooms.Kind.WAR : Rooms.pick(dis, random, prev);
			prev = kind;
			int floor = t.floors()[i];
			Rooms.carpet(level, room, Rooms.Shape.round(ROOM), floor, dis);
			Rooms.lantern(level, room, 0, 0, floor, t.floors()[i + 1] - 1);
			Rooms.furnish(level, t.farDoor() && floor == t.walk() ? turned : plan, kind, floor, dis, random, p -> dist(t, p.getX(), p.getZ()) <= ROOM);
		}
	}

	private static BlockState stone(Tower t, RandomSource random) {
		int roll = random.nextInt(20);
		if (t.style() == Style.GATE) {
			return roll < 2 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : roll < 5 ? Blocks.DEEPSLATE_TILES.defaultBlockState()
					: roll == 5 ? Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		}
		return roll < 3 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : roll == 3 ? Blocks.RED_NETHER_BRICKS.defaultBlockState()
				: roll == 4 ? Blocks.GILDED_BLACKSTONE.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
	}

	// ------------------------------------------------------------------ test

	/**
	 * For the smoke test: in every tower (Dis's at a spread of ground heights) the stair can be walked, half a block at a
	 * time, from the ground to the roof, and no step of it stands in a doorway.
	 */
	public static String selfTest() {
		List<String> wrong = new ArrayList<>();
		int towers = 0;
		int wallDoors = 0;
		int far = 0;
		int wanted = 0;
		for (Site s : sites()) {
			int[] grounds = s.style() == Style.GATE ? new int[]{60} : new int[]{40, 50, 58, 66, 74, 80};
			for (int g : grounds) {
				for (int side = -1; side <= 1; side += 2) {
					Tower t = tower(s, side, g);
					towers++;
					// (where the ground is nearly up to the wall's top there is no door onto it, and none is wanted)
					int walkLevel = s.style() == Style.GATE ? g + InfernoGeometry.GATE_WALL_HEIGHT + 1 : DIS_BASE + InfernoGeometry.DIS_WALL_HEIGHT + 1;
					if (walkLevel - g >= 6) {
						wanted++;
						if (!t.walkDoor()) {
							wrong.add(s.id() + (side < 0 ? "a" : "b") + "@" + g + ": no door onto the wall");
						}
					}
					wallDoors += t.walkDoor() ? 1 : 0;
					far += t.farDoor() ? 1 : 0;
					String why = check(t);
					if (why != null) {
						wrong.add(s.id() + (side < 0 ? "a" : "b") + "@" + g + ": " + why);
					}
				}
			}
		}
		return wrong.isEmpty() ? "Gatehouse test OK: " + towers + " towers, each with a stair from the ground to the roof; " + wallDoors + " of the "
				+ wanted + " whose wall stands clear of the ground have a door onto it (" + far + " on the far side from the gate)"
				: "Gatehouse test FAILED: " + String.join("; ", wrong.subList(0, Math.min(6, wrong.size())));
	}

	private static String check(Tower t) {
		Map<Long, BlockState> spiral = spiral(t);
		// steps by cell and height: walk from the lowest to the roof, moving to a side neighbour at most half a block up or down
		Map<Long, Integer> surface = new HashMap<>();
		for (int[] c : ring(t)) {
			for (int turn = 0; turn < 12; turn++) {
				int n = steps(t, c[0], c[1], turn);
				if (n > 0 && t.ground() + n / 2.0 <= t.roof()) {
					surface.put(key(c[0], c[1], n), n);
				}
			}
		}
		Deque<long[]> queue = new ArrayDeque<>();
		Set<Long> seen = new HashSet<>();
		for (int[] c : ring(t)) {
			if (surface.containsKey(key(c[0], c[1], 1))) {
				queue.add(new long[]{c[0], c[1], 1});
				seen.add(key(c[0], c[1], 1));
			}
		}
		if (queue.isEmpty()) {
			return "the stair has no first step";
		}
		int top = 2 * (t.roof() - t.ground());
		boolean reached = false;
		while (!queue.isEmpty()) {
			long[] a = queue.poll();
			if (a[2] >= top) {
				reached = true;
				break;
			}
			for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				for (int dn = 0; dn <= 1; dn++) {
					long k = key((int) a[0] + d[0], (int) a[1] + d[1], (int) a[2] + dn);
					if (surface.containsKey(k) && seen.add(k)) {
						queue.add(new long[]{a[0] + d[0], a[1] + d[1], a[2] + dn});
					}
				}
			}
		}
		if (!reached) {
			return "the stair doesn't reach the roof";
		}
		for (Map.Entry<Long, BlockState> e : spiral.entrySet()) {
			int x = BlockPos.getX(e.getKey());
			int y = BlockPos.getY(e.getKey());
			int z = BlockPos.getZ(e.getKey());
			boolean ground = inDoorway(t, x, z, t.doorAngle()) && y >= t.ground() && y < t.ground() + DOOR_HEIGHT;
			boolean walk = t.walkDoor() && inDoorway(t, x, z, t.walkDoorAngle()) && y >= t.walk() - 4 && y < t.walk() + DOOR_HEIGHT;
			if (ground || walk) {
				return "a step stands in a doorway at height " + (y - t.ground());
			}
		}
		return null;
	}

	private static long key(int x, int z, int n) {
		return ((long) (x & 0xFFFFF) << 40) | ((long) (z & 0xFFFFF) << 20) | (n & 0xFFFFF);
	}

	private static void commands(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("hellcraft")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("gatehouse")
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
							Site best = null;
							for (Site s : sites()) {
								if (best == null || Math.hypot(s.x() - px, s.z() - pz) < Math.hypot(best.x() - px, best.z() - pz)) {
									best = s;
								}
							}
							Site chosen = best;
							build(level, chosen.id());
							ctx.getSource().sendSuccess(() -> Component.literal("Built the gatehouse towers of " + chosen.id() + " at "
									+ (int) chosen.x() + ", " + (int) chosen.z() + "."), false);
							return 1;
						}))));
	}
}
