package net.thesmallthings.hellcraft.world;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;
import net.thesmallthings.hellcraft.HellcraftMod;
import net.thesmallthings.hellcraft.blood.HellState;
import net.thesmallthings.hellcraft.mixin.ItemFrameAccess;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The white room, an operator's oubliette: a large cell high above the edge of the world whose every
 * surface is papered with blank white maps in invisible glow frames, lit from behind by sea lanterns.
 * Maps in glow frames are drawn at full brightness with no shading, so floor, walls and ceiling are the
 * same flat white and the room has no depth at all. The captive is kept in adventure mode and brought
 * back if they get out; releasing them puts them back where they were and takes the room down.
 */
public final class WhiteRoom {
	private WhiteRoom() {
	}

	/** Inside size: WIDTH x HEIGHT x WIDTH blocks. */
	public static final int WIDTH = 21;
	public static final int HEIGHT = 11;
	private static final int SPACING = 48;
	private static final String TAG = "hellcraft_white_room";

	/** The lowest corner of a cell's inside. */
	public static BlockPos origin(ServerLevel level, int cell) {
		int edge = InfernoGeometry.BORDER_RADIUS - 200;
		return new BlockPos(edge - WIDTH, level.getMaxY() - HEIGHT - 12, -edge + cell * SPACING);
	}

	private static AABB inside(ServerLevel level, int cell) {
		BlockPos o = origin(level, cell);
		return new AABB(o.getX(), o.getY(), o.getZ(), o.getX() + WIDTH, o.getY() + HEIGHT, o.getZ() + WIDTH);
	}

	/** The blank white map every frame holds (made once, locked so nothing ever draws on it). */
	private static ItemStack whiteMap(MinecraftServer server) {
		ServerLevel level = server.overworld();
		HellState state = HellState.get(server);
		MapId id = state.whiteMap >= 0 ? new MapId(state.whiteMap) : null;
		if (id == null || level.getMapData(id) == null) {
			id = level.getFreeMapId();
			MapItemSavedData data = MapItemSavedData.createFresh(0, 0, (byte) 0, false, false, level.dimension()).locked();
			Arrays.fill(data.colors, MapColor.SNOW.getPackedId(MapColor.Brightness.HIGH));
			data.setDirty();
			level.setMapData(id, data);
			state.whiteMap = id.id();
			state.setDirty();
		}
		ItemStack map = new ItemStack(Items.FILLED_MAP);
		map.set(DataComponents.MAP_ID, id);
		return map;
	}

	/** Builds a cell: a shell of sea lanterns, hollow inside, every inner face covered by a white map. Returns the frames hung. */
	public static int build(ServerLevel level, int cell) {
		BlockPos o = origin(level, cell);
		int x0 = o.getX(), y0 = o.getY(), z0 = o.getZ();
		for (int x = x0 - 1; x <= x0 + WIDTH; x++) {
			for (int y = y0 - 1; y <= y0 + HEIGHT; y++) {
				for (int z = z0 - 1; z <= z0 + WIDTH; z++) {
					boolean shell = x < x0 || x >= x0 + WIDTH || y < y0 || y >= y0 + HEIGHT || z < z0 || z >= z0 + WIDTH;
					level.setBlock(new BlockPos(x, y, z), shell ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
		clearFrames(level, cell);
		ItemStack map = whiteMap(level.getServer());
		int frames = 0;
		for (int a = 0; a < WIDTH; a++) {
			for (int b = 0; b < WIDTH; b++) {
				frames += hang(level, new BlockPos(x0 + a, y0, z0 + b), Direction.UP, map);                 // floor
				frames += hang(level, new BlockPos(x0 + a, y0 + HEIGHT - 1, z0 + b), Direction.DOWN, map);  // ceiling
			}
			for (int h = 0; h < HEIGHT; h++) {
				frames += hang(level, new BlockPos(x0, y0 + h, z0 + a), Direction.EAST, map);
				frames += hang(level, new BlockPos(x0 + WIDTH - 1, y0 + h, z0 + a), Direction.WEST, map);
				frames += hang(level, new BlockPos(x0 + a, y0 + h, z0), Direction.SOUTH, map);
				frames += hang(level, new BlockPos(x0 + a, y0 + h, z0 + WIDTH - 1), Direction.NORTH, map);
			}
		}
		HellcraftMod.LOGGER.info("White room {} at {} {} {}: {} frames", cell, x0, y0, z0, frames);
		return frames;
	}

	private static int hang(ServerLevel level, BlockPos pos, Direction facing, ItemStack map) {
		GlowItemFrame frame = new GlowItemFrame(level, pos, facing);
		frame.setItem(map.copy(), false);
		frame.setInvisible(true);
		((ItemFrameAccess) frame).hellcraft$setFixed(true);
		frame.addTag(TAG);
		return level.addFreshEntity(frame) ? 1 : 0;
	}

	private static void clearFrames(ServerLevel level, int cell) {
		for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class, inside(level, cell).inflate(1.0), f -> f.entityTags().contains(TAG))) {
			frame.discard();
		}
	}

	/** Takes a cell down: the frames go, and the shell and inside become air again. */
	public static void demolish(ServerLevel level, int cell) {
		clearFrames(level, cell);
		BlockPos o = origin(level, cell);
		for (int x = o.getX() - 1; x <= o.getX() + WIDTH; x++) {
			for (int y = o.getY() - 1; y <= o.getY() + HEIGHT; y++) {
				for (int z = o.getZ() - 1; z <= o.getZ() + WIDTH; z++) {
					level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
	}

	private static int freeCell(HellState state) {
		for (int cell = 0; ; cell++) {
			int c = cell;
			if (state.captives.values().stream().noneMatch(k -> k.cell() == c)) {
				return cell;
			}
		}
	}

	private static void putInside(ServerPlayer player, ServerLevel level, int cell) {
		BlockPos o = origin(level, cell);
		player.teleportTo(level, o.getX() + WIDTH / 2.0, o.getY(), o.getZ() + WIDTH / 2.0, Set.of(), player.getYRot(), 0.0f, true);
		player.resetFallDistance();
	}

	/** Shuts a player in a white room of their own. */
	public static String trap(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		HellState state = HellState.get(server);
		if (state.captives.containsKey(player.getUUID())) {
			return player.getGameProfile().name() + " is already in the white room.";
		}
		ServerLevel level = server.overworld();
		int cell = freeCell(state);
		build(level, cell);
		HellState.GlobalSpot from = new HellState.GlobalSpot(player.level().dimension(), player.blockPosition());
		String mode = player.gameMode.getGameModeForPlayer().name();
		state.captives.put(player.getUUID(), new HellState.Captive(cell, from, mode));
		state.setDirty();
		player.setGameMode(GameType.ADVENTURE);
		putInside(player, level, cell);
		return player.getGameProfile().name() + " is shut in the white room (cell " + cell + ").";
	}

	/** Lets a player out: back where they were, as they were, and the room taken down. */
	public static String release(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		HellState state = HellState.get(server);
		HellState.Captive captive = state.captives.remove(player.getUUID());
		if (captive == null) {
			return player.getGameProfile().name() + " is not in the white room.";
		}
		state.setDirty();
		demolish(server.overworld(), captive.cell());
		ServerLevel back = server.getLevel(captive.from().dimension());
		if (back == null) {
			back = server.overworld();
		}
		BlockPos p = captive.from().pos();
		player.teleportTo(back, p.getX() + 0.5, p.getY(), p.getZ() + 0.5, Set.of(), player.getYRot(), 0.0f, true);
		player.resetFallDistance();
		GameType mode = GameType.SURVIVAL;
		for (GameType t : GameType.values()) {
			if (t.name().equalsIgnoreCase(captive.mode())) {
				mode = t;
			}
		}
		player.setGameMode(mode);
		player.sendSystemMessage(Component.literal("You've been let out of the white room.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		return player.getGameProfile().name() + " is let out of the white room.";
	}

	public static String list(MinecraftServer server) {
		HellState state = HellState.get(server);
		if (state.captives.isEmpty()) {
			return "Nobody is in the white room.";
		}
		StringBuilder sb = new StringBuilder("In the white room:");
		for (Map.Entry<UUID, HellState.Captive> e : state.captives.entrySet()) {
			HellState.Soul soul = state.existing(e.getKey());
			sb.append(' ').append(soul != null ? soul.name : e.getKey().toString()).append(" (cell ").append(e.getValue().cell()).append(')');
		}
		return sb.toString();
	}

	/** Once a second: every captive stays in adventure mode, inside their cell. */
	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		HellState state = HellState.get(server);
		if (state.captives.isEmpty()) {
			return;
		}
		ServerLevel level = server.overworld();
		for (Map.Entry<UUID, HellState.Captive> e : state.captives.entrySet()) {
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			if (player == null) {
				continue;
			}
			if (player.gameMode.getGameModeForPlayer() != GameType.ADVENTURE && !player.isSpectator()) {
				player.setGameMode(GameType.ADVENTURE);
			}
			if (player.level() != level || !inside(level, e.getValue().cell()).inflate(0.5).contains(player.position())) {
				putInside(player, level, e.getValue().cell());
			}
		}
	}

	/** Self-test for the smoke test: builds a spare cell, counts its frames and checks the map, then takes it down. */
	public static String selfTest(MinecraftServer server) {
		ServerLevel level = server.overworld();
		int cell = 50;
		int frames = build(level, cell);
		int expected = 2 * WIDTH * WIDTH + 4 * WIDTH * HEIGHT;
		BlockPos o = origin(level, cell);
		boolean lit = level.getBlockState(o.below()).is(Blocks.SEA_LANTERN);
		MapItemSavedData map = level.getMapData(new MapId(HellState.get(server).whiteMap));
		boolean white = map != null && map.locked && map.colors[0] == MapColor.SNOW.getPackedId(MapColor.Brightness.HIGH)
				&& map.colors[map.colors.length - 1] == map.colors[0];
		demolish(level, cell);
		int left = level.getEntitiesOfClass(ItemFrame.class, inside(level, cell).inflate(1.0), f -> f.entityTags().contains(TAG)).size();
		return String.format(Locale.ROOT, "White room test: %d/%d frames, lit=%s, white map=%s, frames left after release=%d",
				frames, expected, lit, white, left);
	}
}
