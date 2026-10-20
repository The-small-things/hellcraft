package net.thesmallthings.hellcraft.world;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.blood.Pilgrimage;
import net.thesmallthings.hellcraft.config.HellConfig;
import net.thesmallthings.hellcraft.util.Signs;

import java.util.ArrayList;
import java.util.List;

/**
 * The Pilgrims' Stone, across the road from the Hall of the Damned: a lectern where a pilgrimage begins (Pilgrimage.ask),
 * between two pillars that name the fastest pilgrims to reach Purgatory and the ones who fell deepest on the way.
 */
public final class PilgrimStone {
	private PilgrimStone() {
	}

	private static String shown = "";

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(PilgrimStone::buildOnce);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 1200 == 600) {
				refresh(server);
			}
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer sp) || level.dimension() != Level.OVERWORLD) {
				return InteractionResult.PASS;
			}
			HellState state = HellState.get(sp.level().getServer());
			if (state.pilgrimStone == null || !hit.getBlockPos().equals(state.pilgrimStone.pos()) || !level.getBlockState(hit.getBlockPos()).is(Blocks.LECTERN)) {
				return InteractionResult.PASS;
			}
			Pilgrimage.ask(sp, false);
			return InteractionResult.SUCCESS;
		});
	}

	/** At server start: raise the Stone by the spawn of an Inferno world that doesn't have it yet. */
	public static void buildOnce(MinecraftServer server) {
		HellState state = HellState.get(server);
		if (!HellConfig.get().pilgrimage || state.pilgrimStone != null || !HellWorldgen.isInferno(server.overworld())) {
			return;
		}
		build(server);
	}

	/** Builds (or rebuilds) the Stone south of the road by the spawn; returns where its lectern stands. */
	public static BlockPos build(MinecraftServer server) {
		ServerLevel level = server.overworld();
		HellState state = HellState.get(server);
		int gate = InfernoGeometry.gateX();
		int x0 = gate + 21;
		int x1 = gate + 31;
		int z = 10;
		int y = Integer.MIN_VALUE;
		for (int x = x0; x <= x1; x += 2) {
			level.getChunk(x >> 4, z >> 4);
			y = Math.max(y, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
		}
		// a floor of worn stone bricks, cleared above
		for (int x = x0; x <= x1; x++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int dy = -3; dy < 0; dy++) {
					level.setBlock(new BlockPos(x, y + dy, z + dz), Blocks.STONE_BRICKS.defaultBlockState(), 3);
				}
				level.setBlock(new BlockPos(x, y - 1, z + dz), (x * 7 + dz * 3) % 5 == 0 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
						: (x + dz) % 3 == 0 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState(), 3);
				for (int dy = 0; dy <= 5; dy++) {
					level.setBlock(new BlockPos(x, y + dy, z + dz), Blocks.AIR.defaultBlockState(), 3);
				}
			}
		}
		// two pillars on the back row, the lectern between them facing the road, lanterns at the ends
		for (int x : new int[]{x0 + 2, x1 - 2}) {
			for (int dy = 0; dy < 3; dy++) {
				level.setBlock(new BlockPos(x, y + dy, z + 1), Blocks.STONE_BRICK_WALL.defaultBlockState(), 3);
			}
			level.setBlock(new BlockPos(x, y + 2, z + 1), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 3);
			level.setBlock(new BlockPos(x, y + 3, z + 1), Blocks.LANTERN.defaultBlockState(), 3);
		}
		for (int x : new int[]{x0, x1}) {
			level.setBlock(new BlockPos(x, y, z + 1), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true), 3);
		}
		BlockPos lectern = new BlockPos((x0 + x1) / 2, y, z);
		level.setBlock(lectern, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.NORTH), 3);
		Signs.place(level, lectern.south(), 8, List.of(
				Component.literal("THE PILGRIMAGE").withStyle(ChatFormatting.BOLD),
				Component.literal("Right-click the"),
				Component.literal("lectern: reach"),
				Component.literal("Purgatory alive")));
		state.pilgrimStone = new HellState.GlobalSpot(level.dimension(), lectern);
		state.setDirty();
		shown = "";
		refresh(server);
		HellcraftMod.LOGGER.info("The Pilgrims' Stone at {} {} {}", lectern.getX(), lectern.getY(), lectern.getZ());
		return lectern;
	}

	/** Rewrites the pillars' signs when the standings have changed. */
	public static void refresh(MinecraftServer server) {
		HellState state = HellState.get(server);
		if (state.pilgrimStone == null) {
			return;
		}
		ServerLevel level = server.overworld();
		BlockPos lectern = state.pilgrimStone.pos();
		if (!level.isLoaded(lectern.offset(-3, 0, 0)) || !level.isLoaded(lectern.offset(3, 0, 0))) {
			return;
		}
		List<HellState.Pilgrim> fastest = Pilgrimage.fastest(state, 3);
		List<HellState.Pilgrim> deepest = Pilgrimage.deepest(state, 3);
		String signature = fastest + "|" + deepest;
		if (signature.equals(shown)) {
			return;
		}
		shown = signature;
		Signs.place(level, lectern.offset(-3, 0, 0), 8, lines("FASTEST PILGRIMS", ChatFormatting.GOLD, fastest, true));
		Signs.place(level, lectern.offset(3, 0, 0), 8, lines("DEEPEST FALLEN", ChatFormatting.DARK_RED, deepest, false));
	}

	private static List<Component> lines(String title, ChatFormatting colour, List<HellState.Pilgrim> top, boolean done) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal(title).withStyle(colour, ChatFormatting.BOLD));
		for (int rank = 0; rank < 3; rank++) {
			if (rank < top.size()) {
				HellState.Pilgrim p = top.get(rank);
				String name = p.name().length() > 9 ? p.name().substring(0, 9) : p.name();
				String score = done ? Pilgrimage.time(p.ticks()).replace(" ", "") : p.where().replace("the ", "");
				lines.add(Component.literal((rank + 1) + " " + name + " " + score));
			} else {
				lines.add(Component.literal(rank == 0 ? "- nobody yet -" : ""));
			}
		}
		return lines;
	}

	/** The Stone as chat lines, for /hellcraft hall. */
	public static List<Component> describe(MinecraftServer server) {
		HellState state = HellState.get(server);
		List<Component> out = new ArrayList<>();
		StringBuilder fast = new StringBuilder();
		List<HellState.Pilgrim> fastest = Pilgrimage.fastest(state, 3);
		for (int i = 0; i < fastest.size(); i++) {
			fast.append(i == 0 ? "" : ",  ").append(i + 1).append(". ").append(fastest.get(i).name()).append(" (")
					.append(Pilgrimage.time(fastest.get(i).ticks())).append(")");
		}
		StringBuilder deep = new StringBuilder();
		List<HellState.Pilgrim> deepest = Pilgrimage.deepest(state, 3);
		for (int i = 0; i < deepest.size(); i++) {
			deep.append(i == 0 ? "" : ",  ").append(i + 1).append(". ").append(deepest.get(i).name()).append(" (")
					.append(deepest.get(i).where()).append(", ").append(Pilgrimage.time(deepest.get(i).ticks())).append(")");
		}
		out.add(Component.literal("FASTEST PILGRIMS: ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal(fast.isEmpty() ? "nobody yet" : fast.toString()).withStyle(ChatFormatting.GRAY)));
		out.add(Component.literal("DEEPEST FALLEN: ").withStyle(ChatFormatting.DARK_RED)
				.append(Component.literal(deep.isEmpty() ? "nobody yet" : deep.toString()).withStyle(ChatFormatting.GRAY)));
		return out;
	}
}
