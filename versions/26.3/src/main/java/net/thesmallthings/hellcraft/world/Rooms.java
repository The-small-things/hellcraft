package net.thesmallthings.hellcraft.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.function.BiPredicate;

/**
 * The rooms of the walls and gatehouses: what each kind of room holds, and how often each kind comes. A room is worked out in
 * its own {@link Frame}: a runs across it, b runs out from the wall, so a room can stand at any angle on a curved wall. Its
 * cells are world blocks found by rounding, so the same furnishing works in a gatehouse (on axes) and in a bay of wall.
 *
 * The good rooms (the armoury, the enchanting room, the treasury) are rare on the Gate of Hell's wall and common on Dis, the
 * fallen angels' city. Every room is furnished with carpet, a chain and soul lantern from the ceiling, and its own things.
 */
public final class Rooms {
	private Rooms() {
	}

	/** What a room is for: its furniture (see {@link #furnish}). */
	public enum Kind {GUARD, LIBRARY, MESS, KITCHEN, CHAPEL, PRISON, SHRINE, ARMOURY, ENCHANTING, TREASURY, WAR, LOOKOUT}

	/** Floors are this far apart (so the room between two floors is 6 or 7 blocks high: room to walk, no hitting your head). */
	public static final double FLOOR_SPACING = 7.5;

	/** The level of floor i, counted up from base. */
	public static int floorLevel(int base, int i) {
		return base + (int) Math.round(i * FLOOR_SPACING);
	}

	/** Kinds for a middle floor, and how common each is on the Gate of Hell's wall and on Dis (same order). */
	private static final Kind[] KINDS = {Kind.GUARD, Kind.LIBRARY, Kind.MESS, Kind.KITCHEN, Kind.CHAPEL, Kind.PRISON, Kind.SHRINE,
			Kind.ARMOURY, Kind.ENCHANTING, Kind.TREASURY};
	private static final int[] GATE_WEIGHTS = {5, 3, 4, 3, 2, 3, 2, 1, 1, 1};
	private static final int[] DIS_WEIGHTS = {3, 4, 2, 2, 1, 1, 1, 4, 4, 4};

	/** A kind for a middle floor, by weight, never the kind just below it ({@code avoid}). */
	public static Kind pick(boolean dis, RandomSource random, Kind avoid) {
		int[] weights = dis ? DIS_WEIGHTS : GATE_WEIGHTS;
		int total = 0;
		for (int w : weights) {
			total += w;
		}
		for (int tries = 0; tries < 20; tries++) {
			int roll = random.nextInt(total);
			for (int i = 0; i < KINDS.length; i++) {
				roll -= weights[i];
				if (roll < 0) {
					if (KINDS[i] != avoid) {
						return KINDS[i];
					}
					break;
				}
			}
		}
		return Kind.GUARD;
	}

	/** A room's frame: the centre of a block, and two horizontal axes: u runs across the room, v out from the wall. */
	public record Frame(double cx, double cz, double ux, double uz, double vx, double vz) {
		/** A frame on the world's axes (u east, v south). */
		public static Frame axes(double cx, double cz) {
			return new Frame(cx, cz, 1, 0, 0, 1);
		}

		/** The block (a, b) cells from the centre, at height y. */
		public BlockPos at(int y, int a, int b) {
			return new BlockPos((int) Math.floor(cx + a * ux + b * vx), y, (int) Math.floor(cz + a * uz + b * vz));
		}

		/** The (a, b) of the centre of block (x, z), in cells from the room's centre. */
		public double[] coords(int x, int z) {
			double dx = x + 0.5 - cx;
			double dz = z + 0.5 - cz;
			return new double[]{dx * ux + dz * uz, dx * vx + dz * vz};
		}

		/** The compass direction of (a, b) in this frame. */
		public Direction dir(int a, int b) {
			return cardinal(a * ux + b * vx, a * uz + b * vz);
		}
	}

	/** Which cells a room holds: a square (half-widths ha, hb, in cells) or a circle of the given radius. */
	public record Shape(double ha, double hb, double radius) {
		public static Shape square(double ha, double hb) {
			return new Shape(ha, hb, 0);
		}

		public static Shape round(double radius) {
			return new Shape(0, 0, radius);
		}

		/** In the room. */
		public boolean has(double a, double b) {
			return radius > 0 ? a * a + b * b <= radius * radius : Math.abs(a) <= ha + 0.5 && Math.abs(b) <= hb + 0.5;
		}

		/** In the room or its walls: the cells a bay rebuilds. */
		public boolean reaches(double a, double b) {
			return radius > 0 ? a * a + b * b <= (radius + 1) * (radius + 1) : Math.abs(a) <= ha + 1.5 && Math.abs(b) <= hb + 1.5;
		}

		/** The middle of the room: its carpet is the stronger colour. */
		public boolean core(double a, double b) {
			return radius > 0 ? a * a + b * b < 2.4 * 2.4 : Math.abs(a) <= 2.5 && Math.abs(b) <= 1.5;
		}

		/** How far from the centre, in blocks, the room's cells can reach. */
		public int bound() {
			return radius > 0 ? (int) Math.ceil(radius) + 2 : (int) Math.ceil(Math.max(ha, hb)) + 3;
		}
	}

	/** The compass direction of a horizontal vector. */
	public static Direction cardinal(double dx, double dz) {
		if (Math.abs(dx) > Math.abs(dz)) {
			return dx > 0 ? Direction.EAST : Direction.WEST;
		}
		return dz > 0 ? Direction.SOUTH : Direction.NORTH;
	}

	/**
	 * Furnishes one floor: the room's furniture (kind), a chain and soul lantern from the ceiling, and carpet over the floor.
	 * {@code floor} is the walking level, {@code ceiling} the level of the block overhead. Cells that {@code reserved} names
	 * (a ladder, a way through) are left clear.
	 */
	public static void furnish(ServerLevel level, Frame f, Kind kind, int floor, int ceiling, Shape room, BiPredicate<Integer, Integer> reserved,
			boolean dis, RandomSource random) {
		Ctx c = new Ctx(level, f, floor, room, reserved, dis, random);
		if (ceiling - floor >= 5) {
			c.put(0, 0, ceiling - 1 - floor, Blocks.IRON_CHAIN.defaultBlockState());
			c.put(0, 0, ceiling - 2 - floor, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		}
		switch (kind) {
			case GUARD -> {
				table(c, 0, 0);
				chairs(c, 0, 0);
				barrel(c, 3, 1);
				barrel(c, -3, -1);
				c.put(3, -1, 0, Blocks.GRINDSTONE.defaultBlockState().setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR));
				candle(c, -3, 1, 0, 2);
			}
			case LIBRARY -> {
				c.put(0, 0, 0, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, f.dir(0, -1)));
				for (int[] s : new int[][]{{2, 0}, {-2, 0}, {2, -1}, {-2, -1}}) {
					c.put(s[0], s[1], 0, Blocks.BOOKSHELF.defaultBlockState());
					c.put(s[0], s[1], 1, Blocks.BOOKSHELF.defaultBlockState());
					candle(c, s[0], s[1], 2, random.nextInt(3) + 1);
				}
			}
			case MESS -> {
				table(c, -2, 0);
				chairs(c, -2, 0);
				table(c, 2, 0);
				chairs(c, 2, 0);
				candle(c, -2, 0, 2, 3);
				candle(c, 2, 0, 2, 3);
				barrel(c, 0, 1);
			}
			case KITCHEN -> {
				c.put(-3, 0, 0, Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f.dir(1, 0)));
				c.put(-2, 0, 0, Blocks.CAULDRON.defaultBlockState());
				c.put(-3, -1, 0, Blocks.CAMPFIRE.defaultBlockState());
				c.put(-3, 1, 0, Blocks.HAY_BLOCK.defaultBlockState());
				barrel(c, 3, 1);
				barrel(c, 3, -1);
			}
			case CHAPEL -> {
				for (int a : new int[]{-3, -2, -1, 1, 2, 3}) {
					c.put(a, 1, 0, stairs(c, f.dir(0, -1)));
					c.put(a, -1, 0, stairs(c, f.dir(0, 1)));
				}
				c.put(0, 2, 0, (dis ? Blocks.CAMPFIRE : Blocks.SOUL_CAMPFIRE).defaultBlockState());
				candle(c, -4, 2, 0, 3);
				candle(c, 4, 2, 0, 3);
			}
			case PRISON -> {
				for (int b : new int[]{-2, -1, 1, 2}) {
					c.put(2, b, 0, Blocks.IRON_BARS.defaultBlockState());
					c.put(2, b, 1, Blocks.IRON_BARS.defaultBlockState());
				}
				c.put(3, -1, 0, Blocks.IRON_CHAIN.defaultBlockState());
				c.put(3, 1, 0, Blocks.SKELETON_SKULL.defaultBlockState());
				c.put(4, 0, 0, Blocks.BONE_BLOCK.defaultBlockState());
				c.put(4, -1, 0, Blocks.BONE_BLOCK.defaultBlockState());
				barrel(c, -3, 1);
				c.put(-4, 2, 0, Blocks.COBWEB.defaultBlockState());
			}
			case SHRINE -> {
				c.put(0, 0, 0, (dis ? Blocks.CAMPFIRE : Blocks.SOUL_CAMPFIRE).defaultBlockState());
				BlockState skull = (dis ? Blocks.WITHER_SKELETON_SKULL : Blocks.SKELETON_SKULL).defaultBlockState();
				c.put(2, 0, 0, skull);
				c.put(-2, 0, 0, skull);
				c.put(0, 2, 0, skull);
				for (int[] s : new int[][]{{2, 2}, {-2, 2}, {2, -1}, {-2, -1}}) {
					candle(c, s[0], s[1], 0, 3);
				}
			}
			case ARMOURY -> {
				c.put(0, 0, 0, Blocks.SMITHING_TABLE.defaultBlockState());
				c.put(2, 1, 0, Blocks.ANVIL.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f.dir(-1, 0)));
				c.put(-2, 1, 0, Blocks.GRINDSTONE.defaultBlockState().setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR));
				c.put(2, -1, 0, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
				barrel(c, -2, -1);
				barrel(c, 3, 0);
			}
			case ENCHANTING -> {
				c.put(0, 0, 0, Blocks.ENCHANTING_TABLE.defaultBlockState());
				for (int[] s : new int[][]{{2, 0}, {2, 1}, {2, -1}, {-2, 0}, {-2, 1}, {-2, -1}}) {
					c.put(s[0], s[1], 0, Blocks.BOOKSHELF.defaultBlockState());
				}
				candle(c, 2, 0, 1, 2);
				candle(c, -2, 0, 1, 3);
				c.put(1, 1, 0, Blocks.LAPIS_BLOCK.defaultBlockState());
				c.put(-1, -1, 0, Blocks.LAPIS_BLOCK.defaultBlockState());
			}
			case TREASURY -> {
				for (int[] s : new int[][]{{2, 0}, {-2, 0}, {2, 1}, {-2, -1}}) {
					c.put(s[0], s[1], 0, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f.dir(0, -1)));
				}
				c.put(0, 0, 0, Blocks.GOLD_BLOCK.defaultBlockState());
				c.put(0, -1, 0, Blocks.GILDED_BLACKSTONE.defaultBlockState());
				candle(c, -3, 1, 0, 3);
				candle(c, 3, -1, 0, 3);
			}
			case WAR -> {
				c.put(0, 0, 0, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
				chairs(c, 0, 0);
				barrel(c, 3, -1);
				barrel(c, -3, 1);
				candle(c, 3, 1, 0, 4);
			}
			case LOOKOUT -> {
				c.put(0, 0, 0, Blocks.SOUL_CAMPFIRE.defaultBlockState());
				for (int[] s : new int[][]{{3, 2}, {-3, 2}, {3, -2}, {-3, -2}}) {
					c.put(s[0], s[1], 0, Blocks.LANTERN.defaultBlockState());
				}
			}
		}
		carpet(c);
	}

	/** The stone of a wall, or a room's walls. */
	public static BlockState stone(boolean dis, RandomSource random) {
		int roll = random.nextInt(20);
		if (!dis) {
			return roll < 2 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : roll < 5 ? Blocks.DEEPSLATE_TILES.defaultBlockState()
					: roll == 5 ? Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		}
		return roll < 3 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : roll == 3 ? Blocks.RED_NETHER_BRICKS.defaultBlockState()
				: roll == 4 ? Blocks.GILDED_BLACKSTONE.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
	}

	/** The boards of a room's floor. */
	public static BlockState floorBlock(boolean dis) {
		return (dis ? Blocks.CRIMSON_PLANKS : Blocks.DARK_OAK_PLANKS).defaultBlockState();
	}

	// ------------------------------------------------------------------ the furniture

	/** A room being furnished: its level, frame, cells, which cells are reserved, and the wood of Dis or the Gate. */
	private record Ctx(ServerLevel level, Frame f, int y, Shape room, BiPredicate<Integer, Integer> reserved, boolean dis, RandomSource random) {
		/** Places a block at (a, b), dy above the floor, if that cell is in the room and not reserved. */
		void put(int a, int b, int dy, BlockState state) {
			if (room.has(a, b) && !reserved.test(a, b)) {
				level.setBlock(f.at(y + dy, a, b), state, 2);
			}
		}
	}

	private static void table(Ctx c, int a, int b) {
		c.put(a, b, 0, (c.dis() ? Blocks.CRIMSON_FENCE : Blocks.DARK_OAK_FENCE).defaultBlockState());
		c.put(a, b, 1, (c.dis() ? Blocks.CRIMSON_PRESSURE_PLATE : Blocks.DARK_OAK_PRESSURE_PLATE).defaultBlockState());
	}

	/** Four chairs round a table, each facing the table. */
	private static void chairs(Ctx c, int a, int b) {
		c.put(a + 1, b, 0, stairs(c, c.f().dir(-1, 0)));
		c.put(a - 1, b, 0, stairs(c, c.f().dir(1, 0)));
		c.put(a, b + 1, 0, stairs(c, c.f().dir(0, -1)));
		c.put(a, b - 1, 0, stairs(c, c.f().dir(0, 1)));
	}

	private static BlockState stairs(Ctx c, Direction facing) {
		return (c.dis() ? Blocks.CRIMSON_STAIRS : Blocks.DARK_OAK_STAIRS).defaultBlockState().setValue(StairBlock.FACING, facing);
	}

	private static void barrel(Ctx c, int a, int b) {
		c.put(a, b, 0, Blocks.BARREL.defaultBlockState());
	}

	private static void candle(Ctx c, int a, int b, int dy, int n) {
		c.put(a, b, dy, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, n).setValue(CandleBlock.LIT, true));
	}

	/** Carpet over the room's floor (where the floor is open and solid): one colour in the middle, another round it. */
	private static void carpet(Ctx c) {
		Frame f = c.f();
		int bound = c.room().bound();
		int cx = (int) Math.floor(f.cx());
		int cz = (int) Math.floor(f.cz());
		BlockState inner = (c.dis() ? Blocks.CARPET.red() : Blocks.CARPET.black()).defaultBlockState();
		BlockState ring = (c.dis() ? Blocks.CARPET.black() : Blocks.CARPET.gray()).defaultBlockState();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = cx - bound; x <= cx + bound; x++) {
			for (int z = cz - bound; z <= cz + bound; z++) {
				double[] co = f.coords(x, z);
				if (!c.room().has(co[0], co[1])) {
					continue;
				}
				pos.set(x, c.y(), z);
				if (!c.level().getBlockState(pos).isAir() || c.level().getBlockState(pos.below()).isAir()) {
					continue;
				}
				c.level().setBlock(pos, c.room().core(co[0], co[1]) ? inner : ring, 2);
			}
		}
	}
}
