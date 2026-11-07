package net.thesmallthings.hellcraft.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
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
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import net.thesmallthings.hellcraft.HellcraftMod;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The rooms of the walls, their watch towers and the gatehouses: what each kind of room holds, and how often each comes.
 *
 * Every kind is laid out on the same small plan, 5 cells across (a, from -2 to 2) and 4 deep (b, from -1 at the front to 2
 * at the back), in a {@link Frame} that can stand at any angle: so the same rooms furnish a round gatehouse floor (on the
 * world's axes) and a room in a curved wall. The cell at the front middle (0, -1) is always left clear: it's the way in.
 *
 * The good rooms (the brewery, the armoury, the enchanting room, the treasury) are rare on the Gate of Hell's wall and make up
 * about two rooms in five on Dis, the fallen angels' city. They're worth finding: the anvil, the brewing stands and the
 * enchanting table work (the table has ten bookshelves round it), and the treasury's chests and the armoury's barrel hold loot.
 */
public final class Rooms {
	private Rooms() {
	}

	/** Floor to floor in a gatehouse, about (its rooms 7 blocks high, with room to stand on the stair beneath the next). */
	public static final double FLOOR_SPACING = 8.0;

	/** What a room is for (see {@link #furnish}). */
	public enum Kind {GUARD, LIBRARY, MESS, KITCHEN, STOREROOM, CHAPEL, PRISON, SHRINE, BREWERY, ARMOURY, ENCHANTING, TREASURY, WAR, LOOKOUT}

	/** The kinds a floor can be, and how common each is on the Gate of Hell's wall and on Dis (same order). */
	private static final Kind[] KINDS = {Kind.GUARD, Kind.LIBRARY, Kind.MESS, Kind.KITCHEN, Kind.STOREROOM, Kind.CHAPEL, Kind.PRISON,
			Kind.SHRINE, Kind.BREWERY, Kind.ARMOURY, Kind.ENCHANTING, Kind.TREASURY};
	private static final int[] GATE_WEIGHTS = {5, 3, 4, 3, 4, 2, 3, 2, 1, 1, 1, 1};
	private static final int[] DIS_WEIGHTS = {3, 3, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3};

	/**
	 * The chests of a treasury (the Gate's like a ruined altar's, Dis's like Greed's vaults) and a storeroom's barrel (a Rest's
	 * supplies). In a holder of their own, so the geometry can be worked out (and tested) without the mod loaded.
	 */
	private static final class Loot {
		static final ResourceKey<LootTable> GATE_TREASURE = ResourceKey.create(Registries.LOOT_TABLE, HellcraftMod.id("chests/altar_ruin"));
		static final ResourceKey<LootTable> DIS_TREASURE = ResourceKey.create(Registries.LOOT_TABLE, HellcraftMod.id("chests/vault"));
		static final ResourceKey<LootTable> GATE_STORES = ResourceKey.create(Registries.LOOT_TABLE, HellcraftMod.id("chests/virgils_rest_upper"));
		static final ResourceKey<LootTable> DIS_STORES = ResourceKey.create(Registries.LOOT_TABLE, HellcraftMod.id("chests/virgils_rest_middle"));
	}

	/** True for the rooms worth finding. */
	public static boolean good(Kind k) {
		return k == Kind.BREWERY || k == Kind.ARMOURY || k == Kind.ENCHANTING || k == Kind.TREASURY;
	}

	/** A kind for a floor, by its wall's weights, never the same as {@code avoid} (the floor below). */
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
		return Kind.STOREROOM;
	}

	/** The share of a wall's rooms that are good ones (for the self-test and the README). */
	public static double goodShare(boolean dis) {
		int[] weights = dis ? DIS_WEIGHTS : GATE_WEIGHTS;
		int total = 0;
		int good = 0;
		for (int i = 0; i < KINDS.length; i++) {
			total += weights[i];
			good += good(KINDS[i]) ? weights[i] : 0;
		}
		return good / (double) total;
	}

	/**
	 * A plan's frame: an origin (any point; a cell's centre for a frame on the world's axes) and two horizontal unit axes,
	 * u across and v back (away from the way in). Cells are world blocks, found by rounding.
	 */
	public record Frame(double cx, double cz, double ux, double uz, double vx, double vz) {
		/** A frame on the world's axes (u east, v south) round a block's centre. */
		public static Frame axes(double cx, double cz) {
			return new Frame(cx, cz, 1, 0, 0, 1);
		}

		/** The same frame, its origin moved (da, db) cells. */
		public Frame shifted(double da, double db) {
			return new Frame(cx + da * ux + db * vx, cz + da * uz + db * vz, ux, uz, vx, vz);
		}

		/** The block (a, b) cells from the origin, at height y. */
		public BlockPos at(int y, double a, double b) {
			return new BlockPos((int) Math.floor(cx + a * ux + b * vx), y, (int) Math.floor(cz + a * uz + b * vz));
		}

		/** Where the centre of block (x, z) lies in this frame, (a, b). */
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

		public boolean has(double a, double b) {
			return radius > 0 ? a * a + b * b <= radius * radius : Math.abs(a) <= ha + 0.5 && Math.abs(b) <= hb + 0.5;
		}

		/** The middle of the room, where the carpet is the stronger colour. */
		public boolean core(double a, double b) {
			return radius > 0 ? a * a + b * b < 2.4 * 2.4 : Math.abs(a) <= 2.5 && Math.abs(b) <= 1.5;
		}

		/** How far from the origin, in blocks, the room's cells can reach. */
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

	// ------------------------------------------------------------------ furnishing

	/** Carpet over a room's open floor: one colour in its middle, another round it. Floors under steps and holes are skipped. */
	public static void carpet(ServerLevel level, Frame f, Shape room, int floor, boolean dis) {
		int bound = room.bound();
		int cx = (int) Math.floor(f.cx());
		int cz = (int) Math.floor(f.cz());
		BlockState inner = (dis ? Blocks.CARPET.red() : Blocks.CARPET.black()).defaultBlockState();
		BlockState ring = (dis ? Blocks.CARPET.black() : Blocks.CARPET.gray()).defaultBlockState();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = cx - bound; x <= cx + bound; x++) {
			for (int z = cz - bound; z <= cz + bound; z++) {
				double[] co = f.coords(x, z);
				if (!room.has(co[0], co[1])) {
					continue;
				}
				pos.set(x, floor, z);
				BlockState below = level.getBlockState(pos.below());
				if (!level.getBlockState(pos).isAir() || !(below.is(Blocks.DARK_OAK_PLANKS) || below.is(Blocks.CRIMSON_PLANKS))) {
					continue;
				}
				level.setBlock(pos, room.core(co[0], co[1]) ? inner : ring, 2);
			}
		}
	}

	/** A chain from the ceiling (the block at {@code ceiling}) and a soul lantern on it, over cell (a, b), if there's room. */
	public static void lantern(ServerLevel level, Frame f, double a, double b, int floor, int ceiling) {
		if (ceiling - floor < 5) {
			return;
		}
		BlockPos chain = f.at(ceiling - 1, a, b);
		if (level.getBlockState(chain).isAir() && level.getBlockState(chain.below()).isAir()) {
			level.setBlock(chain, Blocks.IRON_CHAIN.defaultBlockState(), 2);
			level.setBlock(chain.below(), Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);
		}
	}

	/**
	 * Furnishes a room's plan (the 5 by 4 cells round the frame's origin, a from -2 to 2 and b from -1 to 2) with the things of
	 * its kind, standing on the floor at {@code floor}. Lay the carpet first: the furniture stands in its place.
	 */
	public static void furnish(ServerLevel level, Frame f, Kind kind, int floor, boolean dis, RandomSource random, Predicate<BlockPos> allowed) {
		Ctx c = new Ctx(f, dis, random);
		switch (kind) {
			case GUARD -> {
				table(c, 0, 0);
				chair(c, -1, 0, 1, 0);
				chair(c, 1, 0, -1, 0);
				chair(c, 0, 1, 0, -1);
				barrel(c, -2, 2);
				barrel(c, 2, 2);
				candle(c, -2, 2, 1, 2);
				c.put(2, -1, 0, Blocks.GRINDSTONE.defaultBlockState().setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR));
				barrel(c, -2, -1);
			}
			case LIBRARY -> {
				c.put(0, 2, 0, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, f.dir(0, -1)));
				for (int a : new int[]{-2, -1, 1, 2}) {
					c.put(a, 2, 0, Blocks.BOOKSHELF.defaultBlockState());
					c.put(a, 2, 1, Blocks.BOOKSHELF.defaultBlockState());
				}
				candle(c, -2, 2, 2, 3);
				candle(c, 2, 2, 2, 2);
				for (int[] s : new int[][]{{-2, 0}, {-2, -1}, {2, 0}, {2, -1}}) {
					c.put(s[0], s[1], 0, Blocks.BOOKSHELF.defaultBlockState());
				}
				candle(c, 2, 0, 1, 1);
				table(c, -1, 0);
				chair(c, 1, 0, -1, 0);
			}
			case MESS -> {
				for (int a = -1; a <= 1; a++) {
					table(c, a, 0);
					chair(c, a, 1, 0, -1);
				}
				chair(c, -1, -1, 0, 1);
				chair(c, 1, -1, 0, 1);
				candle(c, -1, 0, 2, 3);
				candle(c, 1, 0, 2, 2);
				barrel(c, -2, 2);
				barrel(c, 2, 2);
			}
			case KITCHEN -> {
				c.put(-2, 2, 0, Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f.dir(0, -1)));
				c.put(-1, 2, 0, Blocks.SMOKER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f.dir(0, -1)));
				c.put(0, 2, 0, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
				barrel(c, 1, 2);
				barrel(c, 2, 2);
				table(c, 0, 0);
				c.put(1, 0, 0, Blocks.CAKE.defaultBlockState());
				c.put(-2, -1, 0, Blocks.HAY_BLOCK.defaultBlockState());
				barrel(c, 2, -1);
			}
			case STOREROOM -> {
				for (int a : new int[]{-2, -1, 1, 2}) {
					barrel(c, a, 2);
				}
				barrel(c, -2, 2, 1);
				barrel(c, 2, 2, 1);
				c.put(-2, 0, 0, Blocks.HAY_BLOCK.defaultBlockState());
				c.put(-2, -1, 0, Blocks.HAY_BLOCK.defaultBlockState());
				c.put(-2, 0, 1, Blocks.HAY_BLOCK.defaultBlockState());
				barrel(c, 2, 0);
				if (random.nextInt(2) == 0) {
					// now and then something left behind
					c.loot(-1, 2, 0, dis ? Loot.DIS_STORES : Loot.GATE_STORES);
				}
			}
			case CHAPEL -> {
				c.put(0, 2, 0, Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
				candle(c, 0, 2, 1, 4);
				candle(c, -1, 2, 0, 3);
				candle(c, 1, 2, 0, 3);
				for (int a : new int[]{-2, -1, 1, 2}) {
					chair(c, a, 0, 0, 1);
					chair(c, a, -1, 0, 1);
				}
			}
			case PRISON -> {
				for (int a : new int[]{-2, -1, 1, 2}) {
					c.put(a, 1, 0, Blocks.IRON_BARS.defaultBlockState());
					c.put(a, 1, 1, Blocks.IRON_BARS.defaultBlockState());
				}
				c.put(-1, 2, 0, Blocks.SKELETON_SKULL.defaultBlockState());
				c.put(1, 2, 0, Blocks.BONE_BLOCK.defaultBlockState());
				c.put(2, 2, 0, Blocks.IRON_CHAIN.defaultBlockState());
				c.put(-2, 2, 0, Blocks.COBWEB.defaultBlockState());
				barrel(c, -2, -1);
				candle(c, -2, -1, 1, 1);
			}
			case SHRINE -> {
				c.put(0, 1, 0, (dis ? Blocks.CAMPFIRE : Blocks.SOUL_CAMPFIRE).defaultBlockState());
				BlockState skull = (dis ? Blocks.WITHER_SKELETON_SKULL : Blocks.SKELETON_SKULL).defaultBlockState();
				c.put(-1, 2, 0, skull);
				c.put(1, 2, 0, skull);
				c.put(0, 2, 0, Blocks.BONE_BLOCK.defaultBlockState());
				for (int[] s : new int[][]{{-2, 1}, {2, 1}, {-2, -1}, {2, -1}}) {
					candle(c, s[0], s[1], 0, 3);
				}
			}
			case BREWERY -> {
				c.put(-1, 1, 0, Blocks.BREWING_STAND.defaultBlockState());
				c.put(1, 1, 0, Blocks.BREWING_STAND.defaultBlockState());
				c.put(0, 2, 0, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
				barrel(c, -2, 2);
				barrel(c, 2, 2);
				// a bed of nether wart, ready to pick
				c.put(-2, 0, 0, Blocks.SOUL_SAND.defaultBlockState());
				c.put(-2, -1, 0, Blocks.SOUL_SAND.defaultBlockState());
				c.put(-2, 0, 1, Blocks.NETHER_WART.defaultBlockState().setValue(BlockStateProperties.AGE_3, 3));
				c.put(-2, -1, 1, Blocks.NETHER_WART.defaultBlockState().setValue(BlockStateProperties.AGE_3, 3));
				candle(c, 2, 0, 0, 2);
			}
			case ARMOURY -> {
				c.put(0, 1, 0, Blocks.ANVIL.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f.dir(1, 0)));
				c.put(-2, 1, 0, Blocks.SMITHING_TABLE.defaultBlockState());
				c.put(2, 1, 0, Blocks.GRINDSTONE.defaultBlockState().setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR));
				c.put(-2, -1, 0, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
				barrel(c, -2, 2);
				c.loot(2, 2, 0, dis ? BuiltInLootTables.VILLAGE_ARMORER : BuiltInLootTables.VILLAGE_WEAPONSMITH);
			}
			case ENCHANTING -> {
				// ten bookshelves two blocks from the table, with air between: it enchants up to about level 20
				c.put(0, 1, 0, Blocks.ENCHANTING_TABLE.defaultBlockState());
				for (int[] s : new int[][]{{-2, -1}, {-2, 0}, {-2, 1}, {-2, 2}, {2, -1}, {2, 0}, {2, 1}, {2, 2}, {-1, -1}, {1, -1}}) {
					c.put(s[0], s[1], 0, Blocks.BOOKSHELF.defaultBlockState());
				}
				candle(c, -2, 2, 1, 3);
				candle(c, 2, 2, 1, 3);
				for (int[] s : new int[][]{{-1, 0}, {-1, 1}, {0, 0}, {1, 0}, {1, 1}}) {
					// the cells between the shelves and the table carry its power only while they're empty: no carpet there
					c.put(s[0], s[1], 0, Blocks.AIR.defaultBlockState());
				}
			}
			case TREASURY -> {
				c.loot(-2, 2, 0, dis ? Loot.DIS_TREASURE : Loot.GATE_TREASURE, Blocks.CHEST.defaultBlockState()
						.setValue(BlockStateProperties.HORIZONTAL_FACING, f.dir(0, -1)));
				c.loot(2, 2, 0, dis ? Loot.DIS_TREASURE : Loot.GATE_TREASURE, Blocks.CHEST.defaultBlockState()
						.setValue(BlockStateProperties.HORIZONTAL_FACING, f.dir(0, -1)));
				c.put(0, 2, 0, Blocks.GILDED_BLACKSTONE.defaultBlockState());
				candle(c, 0, 2, 1, 4);
				candle(c, -2, 0, 0, 3);
				candle(c, 2, 0, 0, 3);
				barrel(c, -2, -1);
			}
			case WAR -> {
				c.put(0, 0, 0, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
				chair(c, -1, 0, 1, 0);
				chair(c, 1, 0, -1, 0);
				chair(c, 0, 1, 0, -1);
				barrel(c, -2, 2);
				barrel(c, 2, 2);
				candle(c, 2, -1, 0, 4);
				c.put(-2, -1, 0, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, f.dir(1, 0)));
			}
			case LOOKOUT -> {
				c.put(0, 1, 0, Blocks.SOUL_CAMPFIRE.defaultBlockState());
				for (int[] s : new int[][]{{-2, 2}, {2, 2}, {-2, -1}, {2, -1}}) {
					c.put(s[0], s[1], 0, Blocks.LANTERN.defaultBlockState());
				}
			}
		}
		c.place(level, floor, allowed);
	}

	/** The stone of a wall or a room's walls. */
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

	/**
	 * A room's plan being laid out: what stands on each of its cells (a, b) and how high (dy), then placed in the world. Each plan
	 * cell goes to the one block nearest it, so on a frame at an angle nothing is doubled (and now and then a piece is left out).
	 */
	private static final class Ctx {
		private final Frame f;
		private final boolean dis;
		private final RandomSource random;
		private final Map<Integer, BlockState> items = new LinkedHashMap<>();
		private final Map<Integer, ResourceKey<LootTable>> loot = new HashMap<>();

		Ctx(Frame f, boolean dis, RandomSource random) {
			this.f = f;
			this.dis = dis;
			this.random = random;
		}

		Frame f() {
			return f;
		}

		boolean dis() {
			return dis;
		}

		private static int key(int a, int b, int dy) {
			return ((a + 8) << 16) | ((b + 8) << 8) | (dy + 8);
		}

		/** Puts a block on the plan at (a, b), dy above the floor (never in the way in, the front middle). */
		void put(int a, int b, int dy, BlockState state) {
			if (a == 0 && b == -1) {
				return;
			}
			items.put(key(a, b, dy), state);
		}

		/** A barrel (or the given container) at (a, b), filled from a loot table when first opened. */
		void loot(int a, int b, int dy, ResourceKey<LootTable> table) {
			loot(a, b, dy, table, Blocks.BARREL.defaultBlockState());
		}

		void loot(int a, int b, int dy, ResourceKey<LootTable> table, BlockState container) {
			put(a, b, dy, container);
			loot.put(key(a, b, dy), table);
		}

		/** Places the plan: each cell at the block nearest it, where {@code allowed} lets it stand. */
		void place(ServerLevel level, int floor, Predicate<BlockPos> allowed) {
			Map<Integer, int[]> nearest = new HashMap<>();
			Map<Integer, Double> distance = new HashMap<>();
			int cx = (int) Math.floor(f.cx());
			int cz = (int) Math.floor(f.cz());
			for (int x = cx - 5; x <= cx + 5; x++) {
				for (int z = cz - 5; z <= cz + 5; z++) {
					double[] co = f.coords(x, z);
					int a = (int) Math.round(co[0]);
					int b = (int) Math.round(co[1]);
					if (a < -2 || a > 2 || b < -1 || b > 2) {
						continue;
					}
					double d = (co[0] - a) * (co[0] - a) + (co[1] - b) * (co[1] - b);
					int cell = key(a, b, 0);
					if (d < distance.getOrDefault(cell, Double.MAX_VALUE)) {
						distance.put(cell, d);
						nearest.put(cell, new int[]{x, z});
					}
				}
			}
			for (Map.Entry<Integer, BlockState> e : items.entrySet()) {
				int k = e.getKey();
				int dy = (k & 0xFF) - 8;
				int[] at = nearest.get(k - (k & 0xFF) + 8);
				if (at == null || !allowed.test(new BlockPos(at[0], floor, at[1]))) {
					continue;
				}
				BlockPos pos = new BlockPos(at[0], floor + dy, at[1]);
				level.setBlock(pos, e.getValue(), 2);
				ResourceKey<LootTable> table = loot.get(k);
				if (table != null) {
					RandomizableContainer.setBlockEntityLootTable(level, random, pos, table);
				}
			}
		}
	}

	private static void table(Ctx c, int a, int b) {
		c.put(a, b, 0, (c.dis() ? Blocks.CRIMSON_FENCE : Blocks.DARK_OAK_FENCE).defaultBlockState());
		c.put(a, b, 1, (c.dis() ? Blocks.CRIMSON_PRESSURE_PLATE : Blocks.DARK_OAK_PRESSURE_PLATE).defaultBlockState());
	}

	/** A chair at (a, b) facing (da, db): toward its table, its back to the room. */
	private static void chair(Ctx c, int a, int b, int da, int db) {
		c.put(a, b, 0, (c.dis() ? Blocks.CRIMSON_STAIRS : Blocks.DARK_OAK_STAIRS).defaultBlockState().setValue(StairBlock.FACING, c.f().dir(da, db)));
	}

	private static void barrel(Ctx c, int a, int b) {
		barrel(c, a, b, 0);
	}

	private static void barrel(Ctx c, int a, int b, int dy) {
		c.put(a, b, dy, Blocks.BARREL.defaultBlockState());
	}

	private static void candle(Ctx c, int a, int b, int dy, int n) {
		c.put(a, b, dy, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, n).setValue(CandleBlock.LIT, true));
	}
}
