package net.thesmallthings.hellcraft.world.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.thesmallthings.hellcraft.block.HellBlocks;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.SecretWays;
import net.thesmallthings.hellcraft.world.Zone;

/**
 * The secret ways down (SecretWays): each circle's landmark round its mouth, and a small cairn where it sets you down in
 * the circle below. Every column is worked out from the site alone, so each chunk builds only what falls in it. The
 * ground is already level there (InfernoGeometry.roughness); this makes it exactly so, then builds. Runs late, after the
 * trees and the other set-pieces, and clears what they left in the way.
 */
public record SecretWayFeature() implements Feature {
	public static final SecretWayFeature INSTANCE = new SecretWayFeature();
	public static final MapCodec<SecretWayFeature> CODEC = MapCodec.unit(INSTANCE);

	/** How far under the floor a mouth's shaft goes (the block below it is its bottom, see {@link #bottom}). */
	public static final int SHAFT = 4;
	private static final int CLEAR = 16;

	@Override
	public MapCodec<SecretWayFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		int x0 = origin.getX() & ~15;
		int z0 = origin.getZ() & ~15;
		boolean placed = false;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (SecretWays.Site site : SecretWays.all()) {
			int reach = site.kind().radius + 1;
			if (site.x() + reach >= x0 && site.x() - reach <= x0 + 15 && site.z() + reach >= z0 && site.z() - reach <= z0 + 15) {
				for (int x = Math.max(x0, site.x() - reach); x <= Math.min(x0 + 15, site.x() + reach); x++) {
					for (int z = Math.max(z0, site.z() - reach); z <= Math.min(z0 + 15, site.z() + reach); z++) {
						placed |= mouthColumn(level, pos, site, x, z);
					}
				}
			}
			int lr = SecretWays.LANDING_RADIUS;
			if (site.landX() + lr >= x0 && site.landX() - lr <= x0 + 15 && site.landZ() + lr >= z0 && site.landZ() - lr <= z0 + 15) {
				for (int x = Math.max(x0, site.landX() - lr); x <= Math.min(x0 + 15, site.landX() + lr); x++) {
					for (int z = Math.max(z0, site.landZ() - lr); z <= Math.min(z0 + 15, site.landZ() + lr); z++) {
						placed |= landingColumn(level, pos, site, x, z);
					}
				}
			}
		}
		return placed;
	}

	/** The block at the bottom of a kind's shaft (the self-test looks for it). */
	public static Block bottom(SecretWays.Kind kind) {
		return switch (kind) {
			case WELL, TOMB, HOLLOW_TREE, HEAD -> Blocks.SOUL_SOIL;
			case STONES -> Blocks.CRYING_OBSIDIAN;
			case SINKHOLE -> Blocks.MUD;
			case VAULT -> Blocks.RAW_GOLD_BLOCK;
			case WHIRLPOOL -> Blocks.MAGMA_BLOCK;
			case FUNNEL -> Blocks.SOUL_SAND;
		};
	}

	/** What the ground under a site is made of, where it has to be filled in. */
	private static Block ground(SecretWays.Kind kind) {
		return switch (kind) {
			case WELL -> Blocks.COBBLESTONE;
			case STONES -> Blocks.TUFF;
			case SINKHOLE -> Blocks.PACKED_MUD;
			case VAULT -> Blocks.BLACKSTONE;
			case WHIRLPOOL -> Blocks.MUD;
			case TOMB -> Blocks.BLACKSTONE;
			case HOLLOW_TREE -> Blocks.COARSE_DIRT;
			case FUNNEL -> Blocks.RED_SANDSTONE;
			case HEAD -> Blocks.COBBLED_DEEPSLATE;
		};
	}

	// ------------------------------------------------------------------ the mouth's landmark

	private static boolean mouthColumn(WorldGenLevel level, BlockPos.MutableBlockPos pos, SecretWays.Site site, int x, int z) {
		SecretWays.Kind kind = site.kind();
		int f = site.frameF(x, z);
		int s = site.frameS(x, z);
		double d = Math.hypot(x - site.x(), z - site.z());
		if (d > kind.radius + 0.5) {
			return false;
		}
		int y0 = site.y();
		int water = kind == SecretWays.Kind.WHIRLPOOL ? InfernoGeometry.fluidLevel(x, z) : Integer.MIN_VALUE;
		Column c = new Column(level, pos, x, z, water);
		// level ground at y0: filled under, cleared over
		for (int y = y0 - 6; y <= y0; y++) {
			if (!solid(level.getBlockState(pos.set(x, y, z)))) {
				c.set(y, ground(kind));
			}
		}
		int clear = kind == SecretWays.Kind.HOLLOW_TREE ? CLEAR + 10 : CLEAR;
		for (int y = y0 + 1; y <= y0 + clear; y++) {
			c.empty(y);
		}
		long h = hash(x, z);
		boolean mouth = kind.mouth(f, s);
		if (mouth) {
			for (int y = y0 - SHAFT + 1; y <= y0; y++) {
				c.empty(y);
			}
			c.set(y0 - SHAFT, bottom(kind));
		}
		switch (kind) {
			case WELL -> well(c, f, s, d, y0, h, mouth, site);
			case STONES -> stones(c, x, z, d, y0, h, mouth, site);
			case SINKHOLE -> sinkhole(c, d, y0, h, mouth);
			case VAULT -> vault(c, f, s, d, y0, h, mouth, site);
			case WHIRLPOOL -> whirlpool(c, d, y0, h, mouth, water);
			case TOMB -> tomb(c, f, s, y0, h, mouth, site);
			case HOLLOW_TREE -> hollowTree(c, f, s, d, y0, h, mouth, site);
			case FUNNEL -> funnel(c, d, y0, h, mouth);
			case HEAD -> head(c, f, s, y0, h, mouth);
		}
		return true;
	}

	/** Limbo: a ruined well of mossy stone, its winch beam still across it and a soul lantern far down. */
	private static void well(Column c, int f, int s, double d, int y0, long h, boolean mouth, SecretWays.Site site) {
		if (mouth) {
			if (f == 0 && s == 0) {
				c.set(y0 - SHAFT + 1, Blocks.SOUL_LANTERN.defaultBlockState());
				c.set(y0 + 3, Blocks.IRON_CHAIN);
			}
		} else if (d <= 2.6) {
			// the rim: a block high, broken here and there, higher in places; two posts carry the beam
			boolean post = f == 0 && Math.abs(s) == 2;
			c.set(y0, Blocks.STONE_BRICKS);
			if (post) {
				for (int y = y0 + 1; y <= y0 + 3; y++) {
					c.set(y, Blocks.STONE_BRICK_WALL);
				}
			} else if (pick(h, 0, 8) != 0) {
				c.set(y0 + 1, pick(h, 4, 3) == 0 ? Blocks.MOSSY_STONE_BRICKS : pick(h, 4, 3) == 1 ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS);
				if (pick(h, 8, 4) == 0) {
					c.set(y0 + 2, Blocks.MOSSY_STONE_BRICKS);
				}
			}
		} else if (d <= 3.6) {
			c.set(y0, pick(h, 0, 3) == 0 ? Blocks.GRAVEL : pick(h, 0, 3) == 1 ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE);
		} else if (pick(h, 0, 5) == 0) {
			c.set(y0, Blocks.COARSE_DIRT);
		}
		// the beam across the top, along the posts
		if (f == 0 && Math.abs(s) <= 2) {
			c.set(y0 + 4, Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, site.fx() != 0 ? Direction.Axis.Z : Direction.Axis.X));
		}
	}

	/** Lust: a ring of seven standing stones bent by the wind round a dark hole, its rim of chiselled tuff. */
	private static void stones(Column c, int x, int z, double d, int y0, long h, boolean mouth, SecretWays.Site site) {
		if (mouth) {
			return;
		}
		if (d <= 2.6) {
			c.set(y0, Blocks.CHISELED_TUFF);
			return;
		}
		if (d <= 4.2) {
			c.set(y0, pick(h, 0, 3) == 0 ? Blocks.POLISHED_TUFF : Blocks.TUFF_BRICKS);
		}
		for (int i = 0; i < 7; i++) {
			double a = i * 2 * Math.PI / 7 + site.x() * 0.01;
			int sx = site.x() + (int) Math.round(Math.cos(a) * 6.2);
			int sz = site.z() + (int) Math.round(Math.sin(a) * 6.2);
			if (sx == x && sz == z) {
				int height = 3 + i % 3;
				c.set(y0, Blocks.TUFF);
				for (int y = y0 + 1; y <= y0 + height; y++) {
					c.set(y, y == y0 + height ? Blocks.CHISELED_TUFF : i % 2 == 0 ? Blocks.TUFF : Blocks.POLISHED_TUFF);
				}
			}
		}
	}

	/** Gluttony: the mud gives way in a funnel of muck, roots and mushrooms at its lip, a black hole at its bottom. */
	private static void sinkhole(Column c, double d, int y0, long h, boolean mouth) {
		if (mouth) {
			return;
		}
		int top = d <= 3 ? y0 - 2 : d <= 4.5 ? y0 - 1 : y0;
		for (int y = top + 1; y <= y0; y++) {
			c.empty(y);
		}
		c.set(top, Blocks.MUD);
		if (d > 4.5) {
			if (pick(h, 0, 6) == 0) {
				c.set(top + 1, Blocks.MANGROVE_ROOTS);
			} else if (pick(h, 0, 6) == 1) {
				c.set(top + 1, Blocks.BROWN_MUSHROOM);
			}
		}
	}

	/** Greed: a strongroom's floor among heaps of coin, its trapdoors flung open over the dark. */
	private static void vault(Column c, int f, int s, double d, int y0, long h, boolean mouth, SecretWays.Site site) {
		if (mouth) {
			return;
		}
		int cheb = Math.max(Math.abs(f), Math.abs(s));
		if (cheb <= 3) {
			c.set(y0, cheb == 2 ? Blocks.CHISELED_POLISHED_BLACKSTONE : cheb == 3 ? Blocks.GILDED_BLACKSTONE
					: pick(h, 0, 5) == 0 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS : Blocks.POLISHED_BLACKSTONE_BRICKS);
			// two iron trapdoors, thrown open away from the hole
			if (cheb == 2 && (f == 0 || s == 0)) {
				Direction toHole = facing(site, -Integer.signum(f), -Integer.signum(s));
				c.set(y0 + 1, Blocks.IRON_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING, toHole).setValue(TrapDoorBlock.OPEN, true));
			}
			return;
		}
		// the coin heaps round it
		int roll = pick(h, 0, 10);
		if (roll < 2) {
			c.set(y0 + 1, Blocks.RAW_GOLD_BLOCK);
			c.set(y0 + 2, Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE);
		} else if (roll < 5) {
			c.set(y0 + 1, Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE);
		}
	}

	/** The Styx: a ring of dead stumps round a pool that turns; under it the water pulls you down. */
	private static void whirlpool(Column c, double d, int y0, long h, boolean mouth, int water) {
		BlockState drag = Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(BubbleColumnBlock.DRAG_DOWN, true);
		if (mouth) {
			for (int y = y0 - SHAFT + 1; y < water; y++) {
				c.set(y, drag);
			}
			return;
		}
		c.set(y0, d <= 3 ? Blocks.CLAY : Blocks.MUD);
		if (d > 5.4) {
			int roll = pick(h, 0, 7);
			if (roll == 0) {
				for (int y = y0 + 1; y <= water + pick(h, 8, 3); y++) {
					c.set(y, Blocks.MANGROVE_LOG);
				}
			} else if (roll <= 2) {
				c.set(y0 + 1, Blocks.MANGROVE_ROOTS.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
			}
		}
	}

	/** Heresy: an open tomb, its lid shoved aside, soul lanterns at its corners and steps going down into the dark. */
	private static void tomb(Column c, int f, int s, int y0, long h, boolean mouth, SecretWays.Site site) {
		int af = Math.abs(f);
		int as = Math.abs(s);
		if (af <= 4 && as <= 3 && !mouth) {
			c.set(y0, pick(h, 0, 4) == 0 ? Blocks.SOUL_SOIL : Blocks.POLISHED_BLACKSTONE);
		}
		if (mouth) {
			c.set(y0 - SHAFT + 1, Blocks.SOUL_FIRE);
			return;
		}
		if (as <= 1 && f >= 0 && f <= 2) {
			// the steps, coming down from the front
			Direction up = facing(site, 1, 0);
			int step = y0 - (2 - f);
			for (int y = step; y <= y0 + 2; y++) {
				c.empty(y);
			}
			c.set(step, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, up));
			return;
		}
		if (as == 2 && af <= 3 || af == 3 && as <= 2) {
			if (f == 3 && as <= 1) {
				return;
			}
			c.set(y0 + 1, Blocks.POLISHED_BLACKSTONE_BRICKS);
			if (as == 2 && af == 3) {
				c.set(y0 + 2, Blocks.SOUL_LANTERN.defaultBlockState());
			}
			return;
		}
		if (s == 3 && af <= 2) {
			c.set(y0 + 1, Blocks.POLISHED_BLACKSTONE_SLAB);
		}
	}

	/** The Wood of the Suicides: a dead giant of a tree, hollow, its door facing the way you came, thorns at its roots. */
	private static void hollowTree(Column c, int f, int s, double d, int y0, long h, boolean mouth, SecretWays.Site site) {
		int height = 11;
		int af = Math.abs(f);
		int as = Math.abs(s);
		int cheb = Math.max(af, as);
		if (mouth) {
			// capped above; far down, soul fire glows out of the door
			c.set(y0 + height, Blocks.DARK_OAK_LOG);
			c.set(y0 - SHAFT + 1, Blocks.SOUL_FIRE);
			return;
		}
		if (cheb == 2 && !(af == 2 && as == 2)) {
			boolean door = f == 2 && s == 0;
			for (int y = y0 + 1; y <= y0 + height; y++) {
				if (door && y <= y0 + 2) {
					continue;
				}
				c.set(y, pick(h, y, 4) == 0 ? Blocks.STRIPPED_DARK_OAK_LOG : Blocks.DARK_OAK_LOG);
			}
			if (door) {
				c.set(y0, Blocks.DARK_OAK_PLANKS);
			}
			return;
		}
		// branches reaching out and up from the crown (none over the door)
		boolean onLine = f == 0 || s == 0 || af == as;
		if (onLine && cheb >= 2 && cheb <= 5 && !(f > 0 && s == 0)) {
			c.set(y0 + height - 3 + cheb, Blocks.DARK_OAK_LOG);
			if (cheb == 5) {
				// a soul lantern hangs from the end of each long bough
				c.set(y0 + height + 1, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
			}
		}
		// roots, and thorns between them, but not on the way in
		boolean way = f >= 2 && as <= 1;
		if (!way && d > 2.6 && d <= 4.4) {
			int roll = pick(h, 0, 6);
			if (roll <= 1) {
				c.set(y0 + 1, Blocks.STRIPPED_DARK_OAK_LOG);
			} else if (roll == 2) {
				c.set(y0 + 1, HellBlocks.THORNS.defaultBlockState());
			}
		}
	}

	/** The Burning Sands: a funnel in the red sand, ribs of something it caught sticking out of its rim. */
	private static void funnel(Column c, double d, int y0, long h, boolean mouth) {
		if (mouth) {
			return;
		}
		int depth = (int) Math.round(3.5 * Math.max(0, 1 - (d - 1.5) / 5.5));
		int top = y0 - depth;
		for (int y = top + 1; y <= y0; y++) {
			c.empty(y);
		}
		c.set(top - 1, Blocks.RED_SANDSTONE);
		c.set(top, Blocks.RED_SAND);
		if (d > 5.2 && pick(h, 0, 9) == 0) {
			c.set(top + 1, Blocks.BONE_BLOCK);
			c.set(top + 2, Blocks.BONE_BLOCK);
		}
	}

	/**
	 * Malebolge: the head of the old man of Crete (Inferno XIV), of fine gold, half sunk in the ditch's floor; his eyes weep,
	 * and his mouth is open. Inside it the way goes down.
	 */
	private static void head(Column c, int f, int s, int y0, long h, boolean mouth) {
		// an upright ellipsoid round (f = -1, s = 0): 5 across, 7 high above the floor
		double r2 = ((f + 1) * (f + 1) + s * s) / 25.0;
		int top = r2 <= 1 ? y0 + (int) Math.floor(7 * Math.sqrt(1 - r2)) : y0;
		boolean gullet = Math.abs(s) <= 1 && f >= -2 && f <= 4;
		for (int y = y0 + 1; y <= top; y++) {
			if (gullet && y <= y0 + 2) {
				continue;
			}
			if (mouth && y <= y0 + 2) {
				continue;
			}
			c.set(y, pick(h, y, 9) == 0 ? Blocks.GILDED_BLACKSTONE : pick(h, y, 9) <= 2 ? Blocks.GOLD_BLOCK : Blocks.RAW_GOLD_BLOCK);
		}
		if (gullet && !mouth && f <= 3) {
			c.set(y0, Blocks.NETHER_WART_BLOCK);
		}
		// eyes of crying obsidian (he weeps), and his nose over the open mouth
		if (f == 2 && Math.abs(s) == 2) {
			c.set(y0 + 4, Blocks.CRYING_OBSIDIAN);
		}
		if (f == 4 && s == 0) {
			c.set(y0 + 3, Blocks.GOLD_BLOCK);
			c.set(y0 + 4, Blocks.RAW_GOLD_BLOCK);
		}
	}

	// ------------------------------------------------------------------ the landing

	/** Where a secret sets you down: a small floor of the circle's stone with a lantern post (a raft on the Styx). */
	private static boolean landingColumn(WorldGenLevel level, BlockPos.MutableBlockPos pos, SecretWays.Site site, int x, int z) {
		int dx = x - site.landX();
		int dz = z - site.landZ();
		if (Math.max(Math.abs(dx), Math.abs(dz)) > 2) {
			return false;
		}
		Zone to = site.to();
		boolean post = dx == 2 && dz == 0;
		if (to == Zone.STYX && InfernoGeometry.fluidAt(x, z) != InfernoGeometry.Fluid.NONE) {
			int deck = InfernoGeometry.fluidLevel(x, z) - 1;
			level.setBlock(pos.set(x, deck, z), Blocks.SPRUCE_PLANKS.defaultBlockState(), 2);
			for (int y = deck + 1; y <= deck + 4; y++) {
				level.setBlock(pos.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
			}
			for (int y = deck - 1; y > deck - 6 && !solid(level.getBlockState(pos.set(x, y, z))); y--) {
				if (Math.abs(dx) == 2 && Math.abs(dz) == 2) {
					level.setBlock(pos, Blocks.SPRUCE_LOG.defaultBlockState(), 2);
				}
			}
			if (post) {
				level.setBlock(pos.set(x, deck + 1, z), Blocks.SPRUCE_FENCE.defaultBlockState(), 2);
				level.setBlock(pos.set(x, deck + 2, z), Blocks.SPRUCE_FENCE.defaultBlockState(), 2);
				level.setBlock(pos.set(x, deck + 3, z), Blocks.SOUL_LANTERN.defaultBlockState(), 2);
			}
			return true;
		}
		int y = top(level, pos, x, z);
		if (y == Integer.MIN_VALUE) {
			return false;
		}
		long h = hash(x, z);
		Block[] stone = cairn(to);
		level.setBlock(pos.set(x, y, z), (pick(h, 0, 3) == 0 ? stone[1] : stone[0]).defaultBlockState(), 2);
		for (int up = 1; up <= 5; up++) {
			level.setBlock(pos.set(x, y + up, z), Blocks.AIR.defaultBlockState(), 2);
		}
		if (post) {
			level.setBlock(pos.set(x, y + 1, z), stone[2].defaultBlockState(), 2);
			level.setBlock(pos.set(x, y + 2, z), stone[2].defaultBlockState(), 2);
			level.setBlock(pos.set(x, y + 3, z), Blocks.SOUL_LANTERN.defaultBlockState(), 2);
		}
		return true;
	}

	/** A landing's floor, its worn stone and its post, in the circle's own stone. */
	private static Block[] cairn(Zone to) {
		return switch (to) {
			case LUST -> new Block[]{Blocks.TUFF_BRICKS, Blocks.TUFF, Blocks.TUFF_BRICK_WALL};
			case GLUTTONY -> new Block[]{Blocks.MUD_BRICKS, Blocks.PACKED_MUD, Blocks.MUD_BRICK_WALL};
			case GREED -> new Block[]{Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.GILDED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE_WALL};
			case HERESY -> new Block[]{Blocks.DEEPSLATE_TILES, Blocks.CRACKED_DEEPSLATE_TILES, Blocks.BLACKSTONE_WALL};
			case WOOD_OF_SUICIDES -> new Block[]{Blocks.BLACKSTONE, Blocks.COARSE_DIRT, Blocks.DARK_OAK_FENCE};
			case BURNING_SANDS -> new Block[]{Blocks.SMOOTH_RED_SANDSTONE, Blocks.RED_SANDSTONE, Blocks.RED_SANDSTONE_WALL};
			case COCYTUS -> new Block[]{Blocks.PACKED_ICE, Blocks.BLUE_ICE, Blocks.DEEPSLATE_BRICK_WALL};
			default -> new Block[]{Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICK_WALL};
		};
	}

	/** The top of the ground in a column (plants, snow, leaves and trees don't count). */
	private static int top(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
		int from = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
		for (int y = from; y > from - 32; y--) {
			if (solid(level.getBlockState(pos.set(x, y, z)))) {
				return y;
			}
		}
		return Integer.MIN_VALUE;
	}

	// ------------------------------------------------------------------ helpers

	/** One column of a site: blocks are set in it, and emptied to air (or to water below a marsh's surface). */
	private record Column(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int water) {
		void set(int y, Block block) {
			set(y, block.defaultBlockState());
		}

		void set(int y, BlockState state) {
			level.setBlock(pos.set(x, y, z), state, 2);
		}

		void empty(int y) {
			BlockState want = y < water ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
			if (level.getBlockState(pos.set(x, y, z)) != want) {
				level.setBlock(pos, want, 2);
			}
		}
	}

	/** Ground to stand on: not air, water, plants, leaves or logs. */
	private static boolean solid(BlockState state) {
		return !state.isAir() && state.getFluidState().isEmpty() && !state.canBeReplaced() && !state.is(BlockTags.LEAVES)
				&& !state.is(BlockTags.LOGS) && state.isSolid();
	}

	/** The world direction of a site-frame step (f forward, s to the right). */
	private static Direction facing(SecretWays.Site site, int f, int s) {
		int dx = f * site.fx() - s * site.fz();
		int dz = f * site.fz() + s * site.fx();
		return Direction.getApproximateNearest(dx, 0, dz);
	}

	/** A choice from a column's hash: bits from {@code shift} on, mod {@code n}. */
	private static int pick(long h, int shift, int n) {
		return (int) Math.floorMod(h >>> (shift & 31), (long) n);
	}

	private static long hash(int x, int z) {
		long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ 0x5EC2E7L;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return h;
	}
}
