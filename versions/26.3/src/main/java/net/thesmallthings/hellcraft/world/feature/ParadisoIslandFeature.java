package net.thesmallthings.hellcraft.world.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.thesmallthings.hellcraft.world.InfernoGeometry;
import net.thesmallthings.hellcraft.world.ParadisoGeometry.Island;
import net.thesmallthings.hellcraft.world.ParadisoGeometry.Kind;
import net.thesmallthings.hellcraft.world.ParadisoGeometry.Sphere;
import net.thesmallthings.hellcraft.world.ParadisoGeometry;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * All of Paradiso's islands (Phase 7), built chunk by chunk from ParadisoGeometry: each sphere's great island with its
 * centrepiece (the trial's ground), the arrival isle (a hole for the column of light from below and a canopy over it),
 * the departure isle (a glowing dais where the next column rises), bridges, stepping stones out to four small islands,
 * and the pedestals of the trials' lights. The Empyrean's great island holds the Celestial Rose, a bowl of tiers round
 * Beatrice's bell. Each sphere has its own palette.
 */
public record ParadisoIslandFeature() implements Feature {
	public static final ParadisoIslandFeature INSTANCE = new ParadisoIslandFeature();
	public static final MapCodec<ParadisoIslandFeature> CODEC = MapCodec.unit(INSTANCE);
	/** How far a sphere's islands reach from its centre. */
	private static final double REACH = 64;
	/** The Rose's bowl: its radius, and how deep its middle is sunk below the island's top. */
	public static final int ROSE_RADIUS = 28;
	public static final int ROSE_DEPTH = 4;

	@Override
	public MapCodec<ParadisoIslandFeature> codec() {
		return CODEC;
	}

	/** A sphere's blocks. */
	private record Palette(BlockState top, BlockState patch, BlockState fill, BlockState under, BlockState deck, BlockState rail,
						   BlockState glow, BlockState pillar, BlockState canopy) {
	}

	private record Layout(List<Island> islands, List<double[]> bridges, List<int[]> stones, List<double[]> lights) {
	}

	private static final Map<Sphere, Layout> LAYOUTS = new EnumMap<>(Sphere.class);

	private static synchronized Layout layout(Sphere s) {
		return LAYOUTS.computeIfAbsent(s, k -> new Layout(ParadisoGeometry.islands(k), ParadisoGeometry.bridges(k), ParadisoGeometry.stones(k),
				ParadisoGeometry.lights(k)));
	}

	private static BlockState b(net.minecraft.world.level.block.Block block) {
		return block.defaultBlockState();
	}

	private static Palette palette(Sphere s) {
		return switch (s) {
			case MOON -> new Palette(b(Blocks.CALCITE), b(Blocks.SNOW_BLOCK), b(Blocks.CALCITE), b(Blocks.DIORITE), b(Blocks.SMOOTH_QUARTZ),
					b(Blocks.DIORITE_WALL), b(Blocks.SEA_LANTERN), b(Blocks.QUARTZ_PILLAR), b(Blocks.SMOOTH_QUARTZ));
			case MERCURY -> new Palette(b(Blocks.POLISHED_DIORITE), b(Blocks.COPPER_BLOCK.waxed().pick(WeatheringCopper.WeatherState.OXIDIZED)),
					b(Blocks.DIORITE), b(Blocks.POLISHED_ANDESITE), b(Blocks.CUT_COPPER.waxed().pick(WeatheringCopper.WeatherState.EXPOSED)),
					b(Blocks.DIORITE_WALL), b(Blocks.SEA_LANTERN), b(Blocks.CUT_COPPER.waxed().pick(WeatheringCopper.WeatherState.WEATHERED)),
					b(Blocks.CUT_COPPER.waxed().pick(WeatheringCopper.WeatherState.EXPOSED)));
			case VENUS -> new Palette(b(Blocks.GRASS_BLOCK), b(Blocks.MOSS_BLOCK), b(Blocks.DIRT), b(Blocks.DYED_TERRACOTTA.pick(DyeColor.PINK)),
					b(Blocks.CHERRY_PLANKS), b(Blocks.CHERRY_FENCE), b(Blocks.PEARLESCENT_FROGLIGHT), b(Blocks.STRIPPED_CHERRY_LOG),
					b(Blocks.CHERRY_PLANKS));
			case SUN -> new Palette(b(Blocks.SMOOTH_SANDSTONE), b(Blocks.HONEYCOMB_BLOCK), b(Blocks.SANDSTONE), b(Blocks.DYED_TERRACOTTA.pick(DyeColor.ORANGE)),
					b(Blocks.CUT_SANDSTONE), b(Blocks.SANDSTONE_WALL), b(Blocks.GLOWSTONE), b(Blocks.SMOOTH_SANDSTONE), b(Blocks.CUT_SANDSTONE));
			case MARS -> new Palette(b(Blocks.DYED_TERRACOTTA.pick(DyeColor.RED)), b(Blocks.RED_SANDSTONE), b(Blocks.RED_SANDSTONE), b(Blocks.TERRACOTTA),
					b(Blocks.CUT_RED_SANDSTONE), b(Blocks.RED_SANDSTONE_WALL), b(Blocks.SHROOMLIGHT), b(Blocks.POLISHED_GRANITE),
					b(Blocks.CUT_RED_SANDSTONE));
			case JUPITER -> new Palette(b(Blocks.SMOOTH_QUARTZ), b(Blocks.QUARTZ_BRICKS), b(Blocks.QUARTZ_BLOCK), b(Blocks.DYED_TERRACOTTA.pick(DyeColor.WHITE)),
					b(Blocks.QUARTZ_BRICKS), b(Blocks.IRON_BARS), b(Blocks.SEA_LANTERN), b(Blocks.QUARTZ_PILLAR), b(Blocks.QUARTZ_BRICKS));
			case SATURN -> new Palette(b(Blocks.AMETHYST_BLOCK), b(Blocks.CALCITE), b(Blocks.CALCITE), b(Blocks.TUFF), b(Blocks.PURPUR_BLOCK),
					b(Blocks.GLASS_PANE), b(Blocks.SEA_LANTERN), b(Blocks.PURPUR_PILLAR), b(Blocks.PURPUR_BLOCK));
			case FIXED_STARS -> new Palette(b(Blocks.DEEPSLATE_TILES), b(Blocks.POLISHED_DEEPSLATE), b(Blocks.DEEPSLATE), b(Blocks.DEEPSLATE),
					b(Blocks.POLISHED_DEEPSLATE), b(Blocks.DEEPSLATE_TILE_WALL), b(Blocks.SEA_LANTERN), b(Blocks.END_STONE_BRICKS),
					b(Blocks.POLISHED_DEEPSLATE));
			case PRIMUM_MOBILE -> new Palette(b(Blocks.PRISMARINE_BRICKS), b(Blocks.DARK_PRISMARINE), b(Blocks.PRISMARINE), b(Blocks.PRISMARINE),
					b(Blocks.PRISMARINE_BRICKS), b(Blocks.PRISMARINE_WALL), b(Blocks.SEA_LANTERN), b(Blocks.STAINED_GLASS.pick(DyeColor.LIGHT_BLUE)),
					b(Blocks.PRISMARINE_BRICKS));
			case EMPYREAN -> new Palette(b(Blocks.GRASS_BLOCK), b(Blocks.QUARTZ_BRICKS), b(Blocks.QUARTZ_BLOCK), b(Blocks.CALCITE), b(Blocks.QUARTZ_BRICKS),
					b(Blocks.END_ROD), b(Blocks.SEA_LANTERN), b(Blocks.QUARTZ_PILLAR), b(Blocks.QUARTZ_BRICKS));
		};
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		int x0 = origin.getX() & ~15;
		int z0 = origin.getZ() & ~15;
		boolean placed = false;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (Sphere s : ParadisoGeometry.SPHERES) {
			double[] c = ParadisoGeometry.centre(s);
			double nx = Math.max(x0, Math.min(x0 + 16, c[0]));
			double nz = Math.max(z0, Math.min(z0 + 16, c[1]));
			if ((nx - c[0]) * (nx - c[0]) + (nz - c[1]) * (nz - c[1]) > REACH * REACH) {
				continue;
			}
			Layout layout = layout(s);
			Palette pal = palette(s);
			for (int x = x0; x < x0 + 16; x++) {
				for (int z = z0; z < z0 + 16; z++) {
					placed |= column(level, pos, s, layout, pal, x, z);
				}
			}
			if (s == Sphere.VENUS) {
				cherries(level, generator, random, c, x0, z0);
			}
		}
		return placed;
	}

	/** One column of one sphere: its islands, bridges, stones and buildings. */
	private static boolean column(WorldGenLevel level, BlockPos.MutableBlockPos pos, Sphere s, Layout layout, Palette pal, int x, int z) {
		double px = x + 0.5;
		double pz = z + 0.5;
		int top = ParadisoGeometry.topY(s);
		Island on = null;
		int depth = -1;
		for (Island isle : layout.islands()) {
			int d = isle.depth(px, pz);
			if (d >= 0 && (on == null || isle.top() > on.top() || d > depth)) {
				on = isle;
				depth = d;
			}
		}
		boolean any = false;
		if (on != null) {
			boolean hole = on.kind() == Kind.ARRIVAL && s.ordinal() > 0
					&& Math.abs(x - Math.floor(on.x())) <= 1 && Math.abs(z - Math.floor(on.z())) <= 1;
			if (!hole) {
				int t = on.top();
				for (int y = t - depth + 1; y <= t; y++) {
					BlockState state = y == t ? surface(s, pal, x, z) : y >= t - 2 ? pal.fill() : pal.under();
					level.setBlock(pos.set(x, y, z), state, 2);
				}
				any = true;
			}
		} else {
			for (double[] seg : layout.bridges()) {
				double d = segmentDistance(px, pz, seg);
				if (d < 2.0) {
					level.setBlock(pos.set(x, top, z), pal.deck(), 2);
					if (d < 1.0) {
						level.setBlock(pos.set(x, top - 1, z), pal.under(), 2);
					}
					if (d >= 1.45) {
						level.setBlock(pos.set(x, top + 1, z), pal.rail(), 2);
					}
					any = true;
				}
			}
			for (int[] st : layout.stones()) {
				if (x >= st[0] && x <= st[0] + 1 && z >= st[2] && z <= st[2] + 1) {
					level.setBlock(pos.set(x, st[1], z), pal.deck(), 2);
					any = true;
				}
			}
		}
		for (Island isle : layout.islands()) {
			int bx = (int) Math.floor(isle.x());
			int bz = (int) Math.floor(isle.z());
			int dx = x - bx;
			int dz = z - bz;
			if (Math.abs(dx) > 34 || Math.abs(dz) > 34) {
				continue;
			}
			switch (isle.kind()) {
				case ARRIVAL -> arrival(level, pos, s, pal, isle, x, z, dx, dz);
				case DEPARTURE -> departure(level, pos, pal, isle.top(), x, z, dx, dz);
				case CENTRE -> centrepiece(level, pos, s, pal, isle.top(), x, z, dx, dz);
				case SATELLITE -> {
				}
			}
		}
		if (s == Sphere.MOON || s == Sphere.MERCURY || s == Sphere.SUN || s == Sphere.JUPITER) {
			for (double[] light : layout.lights()) {
				if ((int) Math.floor(light[0]) == x && (int) Math.floor(light[2]) == z) {
					level.setBlock(pos.set(x, (int) light[1] - 1, z), pal.glow(), 2);
				}
			}
		}
		for (int[] spire : ParadisoGeometry.spires(s)) {
			spire(level, pos, pal, spire, x, z);
		}
		return any;
	}

	private static BlockState surface(Sphere s, Palette pal, int x, int z) {
		double h = InfernoGeometry.hash01(x, z, 400 + s.ordinal());
		if (s == Sphere.FIXED_STARS && h < 0.04) {
			return pal.glow();
		}
		double patch = InfernoGeometry.hash01(x >> 2, z >> 2, 420 + s.ordinal());
		return patch < 0.22 ? pal.patch() : pal.top();
	}

	private static double segmentDistance(double px, double pz, double[] seg) {
		double vx = seg[2] - seg[0];
		double vz = seg[3] - seg[1];
		double t = ((px - seg[0]) * vx + (pz - seg[1]) * vz) / (vx * vx + vz * vz);
		t = Math.max(0, Math.min(1, t));
		double dx = px - (seg[0] + vx * t);
		double dz = pz - (seg[1] + vz * t);
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** The arrival isle: four pillars and a canopy over the hole the column of light comes up through, and a gate. */
	private static void arrival(WorldGenLevel level, BlockPos.MutableBlockPos pos, Sphere s, Palette pal, Island isle, int x, int z, int dx, int dz) {
		int top = isle.top();
		if (Math.abs(dx) == 3 && Math.abs(dz) == 3) {
			for (int y = top + 1; y <= top + 5; y++) {
				level.setBlock(pos.set(x, y, z), pal.pillar(), 2);
			}
		}
		if (dx * dx + dz * dz <= 13) {
			level.setBlock(pos.set(x, top + 6, z), dx == 0 && dz == 0 ? pal.glow() : pal.canopy(), 2);
		}
		if (s == Sphere.MOON && dx == 0 && dz == 0) {
			// the way back down to Purgatory's summit (Paradiso.tick)
			level.setBlock(pos.set(x, top + 1, z), Blocks.END_GATEWAY.defaultBlockState(), 2);
		}
		if (s == Sphere.EMPYREAN) {
			int[] gate = homeGate();
			if (x == gate[0] && z == gate[2]) {
				// the Gate of Return to the Gate of Hell
				level.setBlock(pos.set(x, top + 1, z), Blocks.END_GATEWAY.defaultBlockState(), 2);
			}
		}
	}

	/** The Empyrean's Gate of Return, {x, y, z}: on its arrival isle, beyond the column, away from the Rose. */
	public static int[] homeGate() {
		double[] a = ParadisoGeometry.arrival(Sphere.EMPYREAN);
		double[] c = ParadisoGeometry.centre(Sphere.EMPYREAN);
		double dx = a[0] - c[0];
		double dz = a[1] - c[1];
		double len = Math.sqrt(dx * dx + dz * dz);
		return new int[]{(int) Math.floor(a[0] + dx / len * 5.5), ParadisoGeometry.topY(Sphere.EMPYREAN) + 1, (int) Math.floor(a[1] + dz / len * 5.5)};
	}

	/** The departure isle: a glowing dais with four posts, where the next column of light rises. */
	private static void departure(WorldGenLevel level, BlockPos.MutableBlockPos pos, Palette pal, int top, int x, int z, int dx, int dz) {
		if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
			level.setBlock(pos.set(x, top, z), pal.glow(), 2);
		}
		if (Math.abs(dx) == 2 && Math.abs(dz) == 2) {
			level.setBlock(pos.set(x, top + 1, z), pal.pillar(), 2);
			level.setBlock(pos.set(x, top + 2, z), pal.pillar(), 2);
			level.setBlock(pos.set(x, top + 3, z), pal.glow(), 2);
		}
	}

	/** Each sphere's great island has its own centrepiece, the ground of its trial. */
	private static void centrepiece(WorldGenLevel level, BlockPos.MutableBlockPos pos, Sphere s, Palette pal, int top, int x, int z, int dx, int dz) {
		double r = Math.sqrt(dx * dx + dz * dz);
		double a = Math.atan2(dz, dx);
		switch (s) {
			case MOON -> {
				// six pale pillars round a pool of light
				for (int i = 0; i < 6; i++) {
					double pa = i * Math.PI / 3;
					if (dx == (int) Math.round(Math.cos(pa) * 10) && dz == (int) Math.round(Math.sin(pa) * 10)) {
						int h = 2 + i % 3;
						for (int y = top + 1; y <= top + h; y++) {
							level.setBlock(pos.set(x, y, z), pal.pillar(), 2);
						}
						level.setBlock(pos.set(x, top + h + 1, z), pal.glow(), 2);
					}
				}
			}
			case MERCURY -> {
				// four copper gnomons, each with a rod to catch the light
				if (Math.abs(dx) == 7 && Math.abs(dz) == 7) {
					for (int y = top + 1; y <= top + 4; y++) {
						level.setBlock(pos.set(x, y, z), pal.pillar(), 2);
					}
					level.setBlock(pos.set(x, top + 5, z), Blocks.LIGHTNING_ROD.waxed().pick(WeatheringCopper.WeatherState.UNAFFECTED).defaultBlockState(), 2);
				}
			}
			case VENUS -> {
				// pink petals in the grass (the cherries are placed whole: cherries())
				if (r < 19 && r > 3 && InfernoGeometry.hash01(x, z, 433) < 0.18) {
					level.setBlock(pos.set(x, top + 1, z), Blocks.PINK_PETALS.defaultBlockState(), 2);
				}
			}
			case SUN -> {
				// the ring of the wise: twelve lights inside, twelve pillars outside, a golden band between
				if (r >= 12.4 && r < 13.6) {
					level.setBlock(pos.set(x, top, z), Blocks.GLAZED_TERRACOTTA.pick(DyeColor.YELLOW).defaultBlockState(), 2);
				}
				for (int i = 0; i < 12; i++) {
					double pa = i * Math.PI / 6 + Math.PI / 12;
					if (dx == (int) Math.round(Math.cos(pa) * 17) && dz == (int) Math.round(Math.sin(pa) * 17)) {
						for (int y = top + 1; y <= top + 5; y++) {
							level.setBlock(pos.set(x, y, z), pal.pillar(), 2);
						}
						level.setBlock(pos.set(x, top + 6, z), pal.glow(), 2);
					}
				}
			}
			case MARS -> {
				// the cross of light (Paradiso XIV), and four pillars round it
				if ((Math.abs(dx) <= 1 && Math.abs(dz) <= 13) || (Math.abs(dz) <= 1 && Math.abs(dx) <= 13)) {
					level.setBlock(pos.set(x, top, z), pal.glow(), 2);
				}
				if (Math.abs(dx) == 10 && Math.abs(dz) == 10) {
					for (int y = top + 1; y <= top + 4; y++) {
						level.setBlock(pos.set(x, y, z), pal.pillar(), 2);
					}
					level.setBlock(pos.set(x, top + 5, z), pal.glow(), 2);
				}
			}
			case JUPITER -> {
				if (eagle(dx, dz)) {
					level.setBlock(pos.set(x, top, z), dx == 0 && dz == -12 ? Blocks.LAPIS_BLOCK.defaultBlockState()
							: Blocks.CONCRETE.pick(DyeColor.YELLOW).defaultBlockState(), 2);
				}
			}
			case SATURN -> ladder(level, pos, pal, top, x, z, r, a);
			case PRIMUM_MOBILE -> {
				// the Seraph's arena: rings of light, crystal pillars round the rim
				if ((r >= 8 && r < 9) || (r >= 16 && r < 17)) {
					level.setBlock(pos.set(x, top, z), pal.glow(), 2);
				}
				for (int i = 0; i < 8; i++) {
					double pa = i * Math.PI / 4;
					if (dx == (int) Math.round(Math.cos(pa) * 22) && dz == (int) Math.round(Math.sin(pa) * 22)) {
						for (int y = top + 1; y <= top + 5; y++) {
							level.setBlock(pos.set(x, y, z), pal.pillar(), 2);
						}
						level.setBlock(pos.set(x, top + 6, z), pal.glow(), 2);
					}
				}
			}
			case EMPYREAN -> rose(level, pos, pal, top, x, z, r, dx, dz);
		}
	}

	/** Jupiter's Eagle, made of the just (Paradiso XVIII), in gold in the floor, its head to the north. */
	static boolean eagle(int dx, int dz) {
		double body = (dx * dx) / 6.0 + (dz * dz) / 49.0;
		double hx = dx;
		double hz = dz + 11;
		boolean head = hx * hx + hz * hz <= 6;
		boolean beak = dx == 0 && (dz == -14 || dz == -15);
		boolean wing = Math.abs(dx) >= 2 && Math.abs(dx) <= 14 && Math.abs(dz - (-1 - Math.abs(dx) * 0.25)) <= 2.2 - Math.abs(dx) * 0.08;
		boolean tail = dz >= 6 && dz <= 13 && Math.abs(dx) <= (dz - 5) * 0.55 + 0.5;
		return body <= 1 || head || beak || wing || tail;
	}

	/** Jacob's Ladder (Paradiso XXI): golden steps winding round a pillar to a light at the top. */
	private static void ladder(WorldGenLevel level, BlockPos.MutableBlockPos pos, Palette pal, int top, int x, int z, double r, double a) {
		int height = ParadisoGeometry.LADDER_HEIGHT;
		if (r <= 1.6) {
			for (int y = top + 1; y <= top + height + 1; y++) {
				level.setBlock(pos.set(x, y, z), y == top + height + 1 ? pal.glow() : pal.pillar(), 2);
			}
			return;
		}
		if (r < 2.2 || r > 4.6) {
			return;
		}
		double turn = ParadisoGeometry.LADDER_TURN;
		double angle = a < 0 ? a + 2 * Math.PI : a;
		for (int n = 0; n * 2 * Math.PI < height * turn + 2 * Math.PI; n++) {
			int i = (int) Math.round((angle + n * 2 * Math.PI) / turn);
			if (i >= 0 && i < height) {
				BlockState step = i % 5 == 4 ? Blocks.GLOWSTONE.defaultBlockState() : Blocks.STAINED_GLASS.pick(DyeColor.YELLOW).defaultBlockState();
				level.setBlock(pos.set(x, top + 1 + i, z), step, 2);
			}
		}
	}

	/** A star spire of the Fixed Stars: a pillar with steps winding up it and a light on top. */
	private static void spire(WorldGenLevel level, BlockPos.MutableBlockPos pos, Palette pal, int[] spire, int x, int z) {
		int base = spire[1];
		int h = spire[3];
		if (x == spire[0] && z == spire[2]) {
			for (int y = base + 1; y < base + h; y++) {
				level.setBlock(pos.set(x, y, z), pal.pillar(), 2);
			}
			level.setBlock(pos.set(x, base + h, z), pal.glow(), 2);
			return;
		}
		int[][] around = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
		for (int j = 1; j < h; j++) {
			int[] d = around[j % 4];
			if (x == spire[0] + d[0] && z == spire[2] + d[1]) {
				level.setBlock(pos.set(x, base + j, z), pal.deck(), 2);
			}
		}
	}

	/**
	 * The Celestial Rose (Paradiso XXX-XXXI): a bowl of tiers sunk into the Empyrean's island, white with bands of grass
	 * and petals, ringed with rods of light, and Beatrice's bell in the middle.
	 */
	private static void rose(WorldGenLevel level, BlockPos.MutableBlockPos pos, Palette pal, int top, int x, int z, double r, int dx, int dz) {
		if (r > ROSE_RADIUS) {
			return;
		}
		int tier = (int) (r / 3);
		int y = top - (ROSE_DEPTH - Math.min(ROSE_DEPTH, (tier + 1) / 2));
		BlockState floor = tier == 0 ? Blocks.QUARTZ_BLOCK.defaultBlockState()
				: tier % 2 == 0 ? Blocks.GRASS_BLOCK.defaultBlockState()
				: (dx + dz) % 5 == 0 ? pal.glow() : Blocks.QUARTZ_BRICKS.defaultBlockState();
		for (int up = y + 1; up <= top; up++) {
			level.setBlock(pos.set(x, up, z), Blocks.AIR.defaultBlockState(), 2);
		}
		level.setBlock(pos.set(x, y, z), floor, 2);
		if (tier % 2 == 0 && tier > 0 && InfernoGeometry.hash01(x, z, 451) < 0.6) {
			level.setBlock(pos.set(x, y + 1, z), Blocks.PINK_PETALS.defaultBlockState(), 2);
		}
		if (dx == 0 && dz == 0) {
			level.setBlock(pos.set(x, y + 1, z), Blocks.QUARTZ_PILLAR.defaultBlockState(), 2);
			level.setBlock(pos.set(x, y + 2, z), Blocks.BELL.defaultBlockState(), 2);
		}
		for (int i = 0; i < 12; i++) {
			double a = i * Math.PI / 6;
			if (dx == (int) Math.round(Math.cos(a) * (ROSE_RADIUS - 1)) && dz == (int) Math.round(Math.sin(a) * (ROSE_RADIUS - 1))) {
				for (int up = 1; up <= 5; up++) {
					level.setBlock(pos.set(x, y + up, z), Blocks.END_ROD.defaultBlockState(), 2);
				}
			}
		}
	}

	/** Beatrice's bell, in the middle of the Rose. */
	public static BlockPos bell() {
		double[] c = ParadisoGeometry.centre(Sphere.EMPYREAN);
		return new BlockPos((int) Math.floor(c[0]), ParadisoGeometry.topY(Sphere.EMPYREAN) - ROSE_DEPTH + 2, (int) Math.floor(c[1]));
	}

	/** Venus's cherry trees, each placed whole by the chunk its trunk stands in. */
	private static void cherries(WorldGenLevel level, ChunkGenerator generator, RandomSource random, double[] c, int x0, int z0) {
		var tree = level.registryAccess().lookupOrThrow(Registries.FEATURE).getValue(TreeFeatures.CHERRY);
		if (tree == null) {
			return;
		}
		int top = ParadisoGeometry.topY(Sphere.VENUS);
		for (int i = 0; i < 5; i++) {
			double a = i * 2 * Math.PI / 5 + 0.35;
			int x = (int) Math.floor(c[0] + Math.cos(a) * 8);
			int z = (int) Math.floor(c[1] + Math.sin(a) * 8);
			if (x >= x0 && x < x0 + 16 && z >= z0 && z < z0 + 16) {
				tree.place(level, generator, random, new BlockPos(x, top + 1, z));
			}
		}
	}
}
