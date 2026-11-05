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
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
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
 * through a floor every six or seven blocks, to a door at the top of the wall (onto the walk over the gate) and on up to
 * the roof. Each floor is a furnished, carpeted room: a guard room on the ground, then a library, a mess hall, an armoury
 * and a shrine, and a war room at the top. The Gate of Hell's are black and grey, of dark oak; Dis's are red and black,
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
	 * direction of its doors (toward its gate). {@code walkDoor} is false where the wall's top is too near the ground for one.
	 */
	public record Tower(String site, Style style, double cx, double cz, int ground, int walk, int roof, int[] floors, double pitch, double start,
			double doorAngle, boolean walkDoor) {
		public BlockPos centre() {
			return BlockPos.containing(cx, ground, cz);
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
	private static double crossing(double a, double target) {
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
		// floors: the ground, about six and a half blocks apart up to the wall-walk, and the roof
		List<Integer> floors = new ArrayList<>();
		floors.add(ground);
		int rise = walk - ground;
		if (rise > 0) {
			int n = Math.max(1, (int) Math.round(rise / 6.5));
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
		for (int i = 0; i <= 100 && f < 0; i++) {
			double p = 11.0 + (i % 2 == 0 ? 1 : -1) * (i / 2) * 0.05;
			if (p < 8.0 || p > 13.0) {
				continue;
			}
			double[] window = doorWindow(p);
			for (double g = window[0]; g <= window[1] && f < 0; g += 0.05) {
				double atWalk = ((g - rise) % p + p) % p;
				if (!walkDoor || (atWalk >= window[0] && atWalk <= window[1])) {
					pitch = p;
					f = g;
				}
			}
		}
		if (f < 0) {
			// no pitch suits both doors: keep the ground door clear and leave out the one onto the wall
			walkDoor = false;
			pitch = 12.0;
			double[] window = doorWindow(pitch);
			f = (window[0] + window[1]) / 2;
		}
		double start = doorAngle - f / pitch * 2 * Math.PI;
		int[] fl = floors.stream().mapToInt(Integer::intValue).toArray();
		return new Tower(s.id(), s.style(), cx, cz, ground, walk, roof, fl, pitch, start, doorAngle, walkDoor);
	}

	/**
	 * Where, in blocks of rise above a door's floor, the stair may cross the door's direction for a given pitch: high enough
	 * to clear the doorway, or (that much less a turn) low enough that no hole opens in the floor in front of it.
	 */
	private static double[] doorWindow(double pitch) {
		double w = (DOOR_HALF / ((ROOM + STAIR) / 2)) / (2 * Math.PI) * pitch;
		return new double[]{DOOR_HEIGHT + 0.5 + w, pitch - 3.0 - w};
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

	/** True within {@code within} blocks of a gate whose towers have been built. */
	public static boolean nearBuilt(net.minecraft.world.level.Level level, double x, double z, double within) {
		if (!(level instanceof ServerLevel server) || server.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
			return false;
		}
		HellState state = HellState.get(server.getServer());
		for (Site s : sites()) {
			double dx = x - s.x();
			double dz = z - s.z();
			if (dx * dx + dz * dz < within * within && state.gatehouses.contains(s.id())) {
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
			if (state.gatehouses.contains(s.id())) {
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
		state.gatehouses.add(id);
		state.setDirty();
		for (int side = -1; side <= 1; side += 2) {
			Tower t = tower(level, id, side);
			buildTower(level, t);
			HellcraftMod.LOGGER.info("Gatehouse tower built at {} ({}, floors {})", t.centre().toShortString(), id, java.util.Arrays.toString(t.floors()));
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
	private static boolean inDoorway(Tower t, int x, int z, double a) {
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
						// a floor (the roof's too): holes where the stair climbs through it
						long here = BlockPos.asLong(x, y, z);
						if (spiral.containsKey(here)) {
							state = spiral.get(here);
						} else if (spiral.containsKey(BlockPos.asLong(x, y - 1, z)) || spiral.containsKey(BlockPos.asLong(x, y - 2, z))) {
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
		for (int i = 0; i < t.floors().length - 1; i++) {
			furnish(level, t, i, random);
		}
		// a fire on the roof, as the old towers had
		level.setBlock(pos.set(x0, t.roof(), z0), (t.style() == Style.GATE ? Blocks.SOUL_CAMPFIRE : Blocks.CAMPFIRE).defaultBlockState(), 3);
	}

	/** The ground door, onto the gate's passage, and the door at the wall-walk, onto the walk over the gate. */
	private static void doors(ServerLevel level, Tower t) {
		List<Integer> levels = new ArrayList<>();
		levels.add(t.ground());
		if (t.walkDoor()) {
			levels.add(t.walk());
		}
		int x0 = (int) Math.floor(t.cx());
		int z0 = (int) Math.floor(t.cz());
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockState arch = (t.style() == Style.GATE ? Blocks.CHISELED_POLISHED_BLACKSTONE : Blocks.GILDED_BLACKSTONE).defaultBlockState();
		for (int dx = -9; dx <= 9; dx++) {
			for (int dz = -9; dz <= 9; dz++) {
				int x = x0 + dx;
				int z = z0 + dz;
				double d = dist(t, x, z);
				if (d <= ROOM || d > OUTER + 1.5 || !inDoorway(t, x, z, t.doorAngle())) {
					continue;
				}
				for (int l : levels) {
					for (int h = 0; h < DOOR_HEIGHT; h++) {
						pos.set(x, l + h, z);
						if (!level.getBlockState(pos).isAir()) {
							level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
						}
					}
					if (d > STAIR) {
						if (d <= OUTER) {
							level.setBlock(pos.set(x, l + DOOR_HEIGHT, z), arch, 3);
						}
						pos.set(x, l - 1, z);
						if (level.getBlockState(pos).canBeReplaced()) {
							level.setBlock(pos, Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 3);
						}
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

	private enum Room {GUARD, LIBRARY, MESS, ARMOURY, SHRINE, WAR}

	/** Room i: a guard room on the ground, a war room at the wall-walk, the rest in turn. */
	private static Room room(Tower t, int i) {
		if (i == 0) {
			return Room.GUARD;
		}
		if (t.walkDoor() && t.floors()[i] == t.walk()) {
			return Room.WAR;
		}
		Room[] middle = {Room.LIBRARY, Room.MESS, Room.ARMOURY, Room.SHRINE};
		return middle[(i - 1) % middle.length];
	}

	/** Furnishes floor i: its room's furniture in the middle, carpet round it, a lantern hung from the ceiling. */
	private static void furnish(ServerLevel level, Tower t, int i, RandomSource random) {
		int f = t.floors()[i];
		int ceiling = t.floors()[i + 1] - 1;
		int x0 = (int) Math.floor(t.cx());
		int z0 = (int) Math.floor(t.cz());
		boolean gate = t.style() == Style.GATE;
		Direction forward = horizontal(Math.cos(t.doorAngle()), Math.sin(t.doorAngle()));
		Map<Long, BlockState> put = new HashMap<>();
		Block fence = gate ? Blocks.DARK_OAK_FENCE : Blocks.CRIMSON_FENCE;
		Block plate = gate ? Blocks.DARK_OAK_PRESSURE_PLATE : Blocks.CRIMSON_PRESSURE_PLATE;
		Block chair = gate ? Blocks.DARK_OAK_STAIRS : Blocks.CRIMSON_STAIRS;
		switch (room(t, i)) {
			case GUARD -> {
				table(put, x0, f, z0, fence, plate);
				chairs(put, x0, f, z0, chair);
				put.put(BlockPos.asLong(x0 + 2, f, z0 + 1), barrel());
				put.put(BlockPos.asLong(x0 - 2, f, z0 - 1), barrel());
				put.put(BlockPos.asLong(x0 + 1, f, z0 - 2), Blocks.GRINDSTONE.defaultBlockState().setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR));
				put.put(BlockPos.asLong(x0 - 1, f, z0 + 2), Blocks.CHIPPED_ANVIL.defaultBlockState());
			}
			case LIBRARY -> {
				put.put(BlockPos.asLong(x0, f, z0), Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, forward));
				for (int[] o : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
					put.put(BlockPos.asLong(x0 + o[0], f, z0 + o[1]), Blocks.BOOKSHELF.defaultBlockState());
					put.put(BlockPos.asLong(x0 + o[0], f + 1, z0 + o[1]), Blocks.BOOKSHELF.defaultBlockState());
					put.put(BlockPos.asLong(x0 + o[0], f + 2, z0 + o[1]), candle(1 + random.nextInt(3)));
				}
				put.put(BlockPos.asLong(x0 + 1, f, z0 + 1), chair.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
				put.put(BlockPos.asLong(x0 - 1, f, z0 - 1), chair.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
			}
			case MESS -> {
				for (int k = -1; k <= 1; k++) {
					table(put, x0 + k, f, z0, fence, plate);
					put.put(BlockPos.asLong(x0 + k, f, z0 + 1), chair.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
					put.put(BlockPos.asLong(x0 + k, f, z0 - 1), chair.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
				}
				put.put(BlockPos.asLong(x0 + 2, f, z0), barrel());
				put.put(BlockPos.asLong(x0 - 2, f, z0), barrel());
				put.put(BlockPos.asLong(x0 + 2, f + 1, z0), candle(3));
			}
			case ARMOURY -> {
				put.put(BlockPos.asLong(x0, f, z0), Blocks.SMITHING_TABLE.defaultBlockState());
				put.put(BlockPos.asLong(x0 + 2, f, z0), barrel());
				put.put(BlockPos.asLong(x0 - 2, f, z0), barrel());
				put.put(BlockPos.asLong(x0, f, z0 + 2), Blocks.GRINDSTONE.defaultBlockState().setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR));
				put.put(BlockPos.asLong(x0, f, z0 - 2), Blocks.ANVIL.defaultBlockState());
				put.put(BlockPos.asLong(x0 + 1, f, z0 + 1), Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
				put.put(BlockPos.asLong(x0 - 1, f, z0 - 1), barrel());
			}
			case SHRINE -> {
				put.put(BlockPos.asLong(x0, f, z0), (gate ? Blocks.SOUL_CAMPFIRE : Blocks.CAMPFIRE).defaultBlockState());
				int rot = 0;
				for (int[] o : new int[][]{{2, 0}, {0, 2}, {-2, 0}, {0, -2}}) {
					put.put(BlockPos.asLong(x0 + o[0], f, z0 + o[1]), (gate ? Blocks.SKELETON_SKULL : Blocks.WITHER_SKELETON_SKULL).defaultBlockState()
							.setValue(SkullBlock.ROTATION, (rot += 4) % 16));
				}
				for (int[] o : new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
					put.put(BlockPos.asLong(x0 + o[0], f, z0 + o[1]), candle(2 + random.nextInt(3)));
				}
			}
			case WAR -> {
				put.put(BlockPos.asLong(x0, f, z0), Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
				chairs(put, x0, f, z0, chair);
				put.put(BlockPos.asLong(x0 + 2, f, z0 - 1), barrel());
				put.put(BlockPos.asLong(x0 - 2, f, z0 + 1), barrel());
				put.put(BlockPos.asLong(x0 + 2, f + 1, z0 - 1), candle(4));
			}
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (Map.Entry<Long, BlockState> e : put.entrySet()) {
			level.setBlock(pos.set(BlockPos.getX(e.getKey()), BlockPos.getY(e.getKey()), BlockPos.getZ(e.getKey())), e.getValue(), 3);
		}
		// carpet over the rest of the room: one colour in the middle, another round it
		BlockState inner = (gate ? Blocks.CARPET.black() : Blocks.CARPET.red()).defaultBlockState();
		BlockState aisle = (gate ? Blocks.CARPET.gray() : Blocks.CARPET.black()).defaultBlockState();
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				double d = dist(t, x0 + dx, z0 + dz);
				pos.set(x0 + dx, f, z0 + dz);
				if (d <= ROOM && level.getBlockState(pos).isAir() && !level.getBlockState(pos.below()).isAir()) {
					level.setBlock(pos, d < 2.4 ? inner : aisle, 3);
				}
			}
		}
		// a lantern on a chain over the middle
		if (ceiling - f >= 4) {
			level.setBlock(pos.set(x0, ceiling - 1, z0), Blocks.IRON_CHAIN.defaultBlockState(), 3);
			level.setBlock(pos.set(x0, ceiling - 2, z0), (gate ? Blocks.SOUL_LANTERN : Blocks.LANTERN).defaultBlockState().setValue(LanternBlock.HANGING, true), 3);
		}
	}

	private static void table(Map<Long, BlockState> put, int x, int y, int z, Block fence, Block plate) {
		put.put(BlockPos.asLong(x, y, z), fence.defaultBlockState());
		put.put(BlockPos.asLong(x, y + 1, z), plate.defaultBlockState());
	}

	/** Four chairs round a table, their backs to the room. */
	private static void chairs(Map<Long, BlockState> put, int x, int y, int z, Block chair) {
		put.put(BlockPos.asLong(x + 1, y, z), chair.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
		put.put(BlockPos.asLong(x - 1, y, z), chair.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
		put.put(BlockPos.asLong(x, y, z + 1), chair.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
		put.put(BlockPos.asLong(x, y, z - 1), chair.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
	}

	private static BlockState barrel() {
		return Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP);
	}

	private static BlockState candle(int n) {
		return Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, n).setValue(CandleBlock.LIT, true);
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
		for (Site s : sites()) {
			int[] grounds = s.style() == Style.GATE ? new int[]{60} : new int[]{40, 50, 58, 66, 74, 80};
			for (int g : grounds) {
				for (int side = -1; side <= 1; side += 2) {
					Tower t = tower(s, side, g);
					towers++;
					String why = check(t);
					if (why != null) {
						wrong.add(s.id() + (side < 0 ? "a" : "b") + "@" + g + ": " + why);
					}
				}
			}
		}
		return wrong.isEmpty() ? "Gatehouse test OK: " + towers + " towers, each with a stair from the ground to the roof"
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
			if (inDoorway(t, x, z, t.doorAngle())) {
				boolean ground = y >= t.ground() && y < t.ground() + DOOR_HEIGHT;
				boolean walk = t.walkDoor() && y >= t.walk() - 3 && y < t.walk() + DOOR_HEIGHT;
				if (ground || walk) {
					return "a step stands in a doorway at height " + (y - t.ground());
				}
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
